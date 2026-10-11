package com.neon.tetris.game

import com.neon.tetris.domain.BoardSpec
import com.neon.tetris.domain.LevelSystem
import com.neon.tetris.domain.PowerUpType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Plays thousands of random inputs (bursts of sideways moves along the stack, soft-drop holds, rotations,
 * every power-up with targeting, pausing, restarts) against many seeds and checks the engine's invariants
 * after every single action.
 */
class EngineFuzzTest {

    private fun check(s: GameState, prevScore: Int, prevLines: Int, context: String) {
        assertTrue("$context: board values", s.board.all { it in 0..7 })
        assertEquals("$context: level matches score", LevelSystem.levelForScore(s.score), s.level)
        assertTrue("$context: score never decreases", s.score >= prevScore)
        assertTrue("$context: lines never decrease", s.lines >= prevLines)
        assertTrue("$context: progress in range", s.levelProgress in 0f..1f)
        if (s.phase == Phase.CLEARING) assertNotNull("$context: clearing needs an animation", s.clear)
        if (s.phase == Phase.COLLAPSING) assertNotNull("$context: collapsing needs an animation", s.collapse)

        val p = s.piece
        val live = s.phase == Phase.FALLING || s.phase == Phase.TARGETING || s.phase == Phase.CLEARING ||
            s.phase == Phase.COLLAPSING || s.phase == Phase.PAUSED
        if (p != null && live) {
            for (c in p.cells()) {
                assertTrue("$context: piece inside walls $c", c.x in 0 until BoardSpec.COLS && c.y < BoardSpec.ROWS)
                if (c.y >= 0) assertEquals("$context: piece overlaps a block at $c", 0, s.cell(c.x, c.y))
            }
        }
    }

    @Test
    fun randomPlayNeverBreaksInvariants() {
        var gamesEnded = 0
        var powerUpsUsed = 0
        var maxLevel = 1
        var linesCleared = 0
        for (seed in 1..60) {
            val rnd = Random(seed)
            val engine = TetrisEngine(Random(seed * 7919))
            engine.startNewGame()
            var prevScore = 0
            var prevLines = 0

            fun verify(label: String) {
                val s = engine.state.value
                check(s, prevScore, prevLines, "seed=$seed after $label (phase=${s.phase})")
                prevScore = s.score
                prevLines = s.lines
                maxLevel = maxOf(maxLevel, s.level)
                linesCleared = maxOf(linesCleared, s.lines)
                if (s.phase == Phase.GAME_OVER) {
                    gamesEnded++
                    engine.startNewGame()
                    prevScore = 0
                    prevLines = 0
                }
            }

            repeat(4000) {
                when (rnd.nextInt(100)) {
                    in 0..29 -> { engine.update(rnd.nextLong(1, 101)); verify("update") }
                    in 30..43 -> repeat(rnd.nextInt(1, 7)) { engine.moveLeft(); verify("moveLeft") }
                    in 44..57 -> repeat(rnd.nextInt(1, 7)) { engine.moveRight(); verify("moveRight") }
                    in 58..65 -> { engine.rotate(rnd.nextBoolean()); verify("rotate") }
                    in 66..69 -> { engine.softDropStep(); verify("softDropStep") }
                    in 70..73 -> { engine.setSoftDrop(rnd.nextBoolean()); verify("setSoftDrop") }
                    in 74..83 -> { engine.hardDrop(); verify("hardDrop") }
                    in 84..87 -> {
                        val before = engine.state.value.phase
                        engine.usePowerUp(PowerUpType.entries[rnd.nextInt(PowerUpType.entries.size)])
                        if (before == Phase.FALLING && engine.state.value.phase != Phase.FALLING) powerUpsUsed++
                        verify("usePowerUp")
                    }
                    in 88..93 -> {
                        engine.targetCell(rnd.nextInt(-1, BoardSpec.COLS + 1), rnd.nextInt(-1, BoardSpec.ROWS + 1))
                        verify("targetCell")
                    }
                    94 -> { engine.cancelTargeting(); verify("cancelTargeting") }
                    95 -> { engine.pause(); verify("pause") }
                    else -> { engine.resume(); verify("resume") }
                }
            }
        }
        val stats = "games=$gamesEnded powerUps=$powerUpsUsed maxLevel=$maxLevel maxLines=$linesCleared"
        println("fuzz coverage: $stats")
        assertTrue("coverage too low: $stats", gamesEnded > 20 && powerUpsUsed > 50 && maxLevel > 3)
    }

    @Test
    fun botPlayMixedWithRandomPowerUpsKeepsInvariants() {
        var maxLines = 0
        var maxLevel = 1
        var chains = 0
        var gamesEnded = 0
        for (seed in 1..16) {
            val rnd = Random(seed + 1000)
            val engine = TetrisEngine(Random(seed))
            engine.startNewGame()
            var prevScore = 0
            var prevLines = 0
            var prevCombo = 0
            repeat(1500) { step ->
                val s = engine.state.value
                when {
                    s.phase == Phase.GAME_OVER -> {
                        gamesEnded++
                        engine.startNewGame()
                        prevScore = 0; prevLines = 0; prevCombo = 0
                    }
                    s.phase == Phase.TARGETING ->
                        engine.targetCell(rnd.nextInt(0, BoardSpec.COLS), rnd.nextInt(BoardSpec.ROWS - 8, BoardSpec.ROWS))
                    s.phase == Phase.FALLING && rnd.nextInt(100) < 10 ->
                        engine.usePowerUp(PowerUpType.entries[rnd.nextInt(PowerUpType.entries.size)])
                    s.phase == Phase.FALLING && rnd.nextInt(100) < 3 -> { engine.pause(); engine.resume() }
                    s.phase == Phase.FALLING -> GreedyBot.playPiece(engine)
                    else -> engine.update(rnd.nextLong(1, 101))
                }
                val now = engine.state.value
                if (now.combo > prevCombo + 1) chains++
                prevCombo = now.combo
                check(now, prevScore, prevLines, "bot seed=$seed step=$step phase=${now.phase}")
                prevScore = now.score
                prevLines = now.lines
                maxLines = maxOf(maxLines, now.lines)
                maxLevel = maxOf(maxLevel, now.level)
            }
        }
        val stats = "maxLines=$maxLines maxLevel=$maxLevel gamesEnded=$gamesEnded"
        println("bot fuzz coverage: $stats")
        assertTrue("coverage too low: $stats", maxLines > 40 && maxLevel > 10)
    }

    /** Independent reference for classic line clearing: drop the full rows, pad the top with empty rows. */
    private fun withoutFullRows(board: IntArray): IntArray {
        val cols = BoardSpec.COLS
        val rows = BoardSpec.ROWS
        val kept = (0 until rows).filter { y -> (0 until cols).any { x -> board[y * cols + x] == 0 } }
        val result = IntArray(board.size)
        val offset = rows - kept.size
        kept.forEachIndexed { i, y -> for (x in 0 until cols) result[(offset + i) * cols + x] = board[y * cols + x] }
        return result
    }

    @Test
    fun theBotCanPlayAVeryLongGameUpToTheMaximumLevel() {
        val engine = TetrisEngine(Random(42))
        engine.startNewGame()
        var pieces = 0
        var guard = 0
        var prevScore = 0
        var prevLines = 0
        var expectedAfterClear: IntArray? = null
        var lastClearId = -1
        var clearsVerified = 0
        while (pieces < 2500 && guard++ < 200_000) {
            val before = engine.state.value
            // oracle: remember what the board must look like once this line clear has finished
            val clear = before.clear
            if (before.phase == Phase.CLEARING && clear != null && clear.kind == ClearKind.LINES && clear.id != lastClearId) {
                lastClearId = clear.id
                expectedAfterClear = withoutFullRows(before.board)
            }
            if ((before.phase == Phase.FALLING || before.phase == Phase.GAME_OVER) && expectedAfterClear != null) {
                assertTrue("board after a line clear must equal the reference result", before.board.contentEquals(expectedAfterClear))
                expectedAfterClear = null
                clearsVerified++
            }
            when (before.phase) {
                Phase.FALLING -> { GreedyBot.playPiece(engine); pieces++ }
                Phase.GAME_OVER -> break
                else -> engine.update(50)
            }
            val s = engine.state.value
            check(s, prevScore, prevLines, "long game piece=$pieces")
            prevScore = s.score
            prevLines = s.lines
        }
        val s = engine.state.value
        println("long game: pieces=$pieces lines=${s.lines} score=${s.score} level=${s.level} phase=${s.phase} clearsVerifiedAgainstModel=$clearsVerified")
        assertTrue("the bot should clear many lines (${s.lines})", s.lines > 150)
        assertTrue("every line clear should be checked against the model ($clearsVerified)", clearsVerified > 100)
        assertTrue("level progression should work all the way up (${s.level})", s.level >= 25)
        assertEquals(LevelSystem.levelForScore(s.score), s.level)
    }
}
