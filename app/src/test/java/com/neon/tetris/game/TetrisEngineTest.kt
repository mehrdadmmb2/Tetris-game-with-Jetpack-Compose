package com.neon.tetris.game

import com.neon.tetris.domain.PowerUpRules
import com.neon.tetris.domain.PowerUpType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Collects engine events synchronously (Unconfined dispatcher) so tests can assert on them. */
private class Recorder(engine: TetrisEngine, scope: CoroutineScope) {
    val events = mutableListOf<GameEvent>()
    private val job: Job = scope.launch(Dispatchers.Unconfined) { engine.events.collect { events.add(it) } }
    fun stop() = job.cancel()
    inline fun <reified T : GameEvent> of(): List<T> = events.filterIsInstance<T>()
}

private fun newEngine(seed: Int = 1) = TetrisEngine(Random(seed)).also { it.startNewGame() }

private fun TetrisEngine.advanceUntil(maxMs: Long = 6000, step: Long = 16, cond: (GameState) -> Boolean): Boolean {
    var t = 0L
    while (t < maxMs) {
        if (cond(state.value)) return true
        update(step)
        t += step
    }
    return cond(state.value)
}

/** Runs animations until play resumes. */
private fun TetrisEngine.settle() = advanceUntil { it.phase == Phase.FALLING }

private fun GameState.filled(): Int = board.count { it != 0 }

class EngineBasicsTest {

    @Test
    fun newGameSpawnsAPieceAtTheTopCenter() {
        val s = newEngine().state.value
        assertEquals(Phase.FALLING, s.phase)
        val p = s.piece!!
        assertEquals(0, p.cells().minOf { it.y })
        assertEquals(TetrisEngine.SPAWN_X, p.x)
        assertEquals(3, s.next.size)
        assertEquals(1, s.level)
        assertEquals(0, s.score)
        assertEquals(0, s.filled())
    }

    @Test
    fun firstSevenPiecesComeFromOneBag() {
        val e = newEngine(5)
        val seen = mutableListOf<PieceType>()
        repeat(7) {
            seen.add(e.state.value.piece!!.type)
            e.hardDrop()
        }
        assertEquals(7, seen.toSet().size)
    }

    @Test
    fun piecesCannotLeaveThePlayfieldSideways() {
        val e = newEngine()
        repeat(20) { e.moveLeft() }
        assertEquals(0, e.state.value.piece!!.cells().minOf { it.x })
        assertFalse(e.moveLeft())
        repeat(20) { e.moveRight() }
        assertEquals(9, e.state.value.piece!!.cells().maxOf { it.x })
        assertFalse(e.moveRight())
    }

    @Test
    fun rotationWorksAndWallKicksPushThePieceBackInside() {
        val e = newEngine()
        e.debugSetPiece(PieceType.T, 0, 3, 5)
        repeat(4) { assertTrue(e.rotate(true)) }
        assertEquals(0, e.state.value.piece!!.rotation)
        assertTrue(e.rotate(false))
        assertEquals(3, e.state.value.piece!!.rotation)

        e.debugSetPiece(PieceType.I, 1, -2, 5) // vertical I hugging the left wall
        assertTrue(e.rotate(true))
        val p = e.state.value.piece!!
        assertEquals(2, p.rotation)
        assertEquals(0, p.x)
        assertTrue(p.cells().all { it.x in 0..9 })
    }

    @Test
    fun oPieceDoesNotRotate() {
        val e = newEngine()
        e.debugSetPiece(PieceType.O)
        assertFalse(e.rotate(true))
    }

    @Test
    fun ghostShowsWhereThePieceWillLand() {
        val e = newEngine()
        e.debugSetPiece(PieceType.O, 0, 4, 0)
        assertEquals(18, e.state.value.ghostY)
    }

    @Test
    fun hardDropLocksImmediatelyAndSpawnsTheQueuedPiece() = runBlocking<Unit> {
        val e = newEngine()
        val rec = Recorder(e, this)
        val queued = e.state.value.next.first()
        e.hardDrop()
        val s = e.state.value
        assertEquals(4, s.filled())
        assertEquals(queued, s.piece!!.type)
        val drop = rec.of<GameEvent.HardDropped>().single()
        assertTrue(drop.distance > 10)
        assertEquals(19, drop.to.maxOf { it.y })
        assertEquals(1, rec.of<GameEvent.Locked>().size)
        rec.stop()
    }

    @Test
    fun gravityAndSoftDrop() {
        val e = newEngine()
        val y0 = e.state.value.piece!!.y
        e.update(100)
        assertEquals(y0, e.state.value.piece!!.y) // level 1 falls one row per 800 ms
        e.setSoftDrop(true)
        repeat(4) { e.update(50) }
        assertTrue(e.state.value.piece!!.y >= y0 + 3)
        e.setSoftDrop(false)
        val y1 = e.state.value.piece!!.y
        assertTrue(e.softDropStep())
        assertEquals(y1 + 1, e.state.value.piece!!.y)
    }

    @Test
    fun pieceLocksAfterTheLockDelayAndMovingResetsIt() {
        val e = newEngine()
        e.debugSetPiece(PieceType.O, 0, 4, 18) // resting on the floor
        repeat(9) { e.update(50) }              // 450 ms
        assertEquals(0, e.boardForTest.filledCount())
        repeat(2) { e.update(50) }              // > 500 ms
        assertEquals(4, e.boardForTest.filledCount())

        val f = newEngine()
        f.debugSetPiece(PieceType.O, 0, 4, 18)
        repeat(8) { f.update(50) }              // 400 ms
        assertTrue(f.moveLeft())                // resets the timer
        repeat(8) { f.update(50) }
        assertEquals(0, f.boardForTest.filledCount())
        repeat(3) { f.update(50) }
        assertEquals(4, f.boardForTest.filledCount())
    }

    @Test
    fun endlessMovingCannotStallLockingForever() {
        val e = newEngine()
        e.debugSetPiece(PieceType.O, 0, 4, 18)
        var left = true
        repeat(40) {
            if (left) e.moveLeft() else e.moveRight()
            left = !left
            e.update(100)
        }
        assertTrue(e.boardForTest.filledCount() >= 4)
    }

    @Test
    fun pauseFreezesEverythingUntilResume() {
        val e = newEngine()
        val y0 = e.state.value.piece!!.y
        e.pause()
        assertEquals(Phase.PAUSED, e.state.value.phase)
        repeat(30) { e.update(100) }
        assertEquals(y0, e.state.value.piece!!.y)
        assertFalse(e.moveLeft())
        e.usePowerUp(PowerUpType.BOMB)
        assertEquals(Phase.PAUSED, e.state.value.phase)
        e.resume()
        repeat(9) { e.update(100) }
        assertEquals(y0 + 1, e.state.value.piece!!.y)
    }

    @Test
    fun stackingToTheTopEndsTheGame() = runBlocking<Unit> {
        val e = newEngine()
        val rec = Recorder(e, this)
        var drops = 0
        while (e.state.value.phase != Phase.GAME_OVER && drops < 200) {
            e.hardDrop()
            drops++
        }
        assertEquals(Phase.GAME_OVER, e.state.value.phase)
        assertTrue(drops < 200)
        assertEquals(1, rec.of<GameEvent.GameOver>().size)
        assertFalse(e.moveLeft())
        val rev = e.state.value.revision
        e.update(100)
        assertEquals(rev, e.state.value.revision)
        rec.stop()
    }

    @Test
    fun publishedStatesAreDistinctSnapshots() {
        val e = newEngine()
        val a = e.state.value
        e.moveLeft()
        val b = e.state.value
        assertTrue(b.revision > a.revision)
        assertTrue(a.piece!!.x != b.piece!!.x)
    }
}

class EngineScoringTest {

    @Test
    fun clearingALineScoresRemovesItAndDropsTheBlocksAbove() = runBlocking<Unit> {
        val e = newEngine()
        val rec = Recorder(e, this)
        e.boardForTest.loadRows(listOf(".111111111"))
        e.debugSetPiece(PieceType.I, rotation = 1, x = -2, y = 0) // vertical I into the gap at column 0
        e.hardDrop()

        val s = e.state.value
        assertEquals(Phase.CLEARING, s.phase)
        assertEquals(50, s.score)
        assertEquals(1, s.lines)
        assertEquals(1, s.combo)
        assertEquals(ClearKind.LINES, s.clear!!.kind)
        assertEquals(10, s.clear!!.cells.size)

        var sawCollapse = false
        var guard = 0
        while (e.state.value.phase != Phase.FALLING && guard++ < 200) {
            e.update(16)
            val c = e.state.value.collapse
            if (c != null) {
                sawCollapse = true
                for (y in 17..19) assertEquals(1, c.offsets[y * 10])
                assertEquals(0, c.offsets[16 * 10])
            }
        }
        assertTrue(sawCollapse)
        val end = e.state.value
        assertEquals(Phase.FALLING, end.phase)
        assertNotNull(end.piece)
        assertEquals(3, end.filled())
        for (y in 17..19) assertTrue(end.cell(0, y) != 0)
        assertEquals(0, end.cell(0, 16))

        assertEquals(listOf(19), rec.of<GameEvent.LinesCleared>().single().rows)
        assertEquals(1, rec.of<GameEvent.ClearBurst>().size)
        assertEquals(50, rec.of<GameEvent.ScoreAdded>().single().points)
        rec.stop()
    }

    @Test
    fun fourLinesScoreBigAndJumpSeveralLevels() = runBlocking<Unit> {
        val e = newEngine()
        val rec = Recorder(e, this)
        e.boardForTest.loadRows(List(4) { "111111111." })
        e.debugSetPiece(PieceType.I, rotation = 1, x = 7, y = 0) // vertical I into column 9
        e.hardDrop()
        val s = e.state.value
        assertEquals(600, s.score)   // 40 cells * 5 points * 3
        assertEquals(4, s.lines)
        assertEquals(7, s.level)     // 600 / 100 + 1
        assertEquals(7, rec.of<GameEvent.LevelUp>().single().level)
        assertTrue(e.settle())
        assertEquals(0, e.state.value.filled())
        rec.stop()
    }

    @Test
    fun consecutiveClearsBuildAComboAndAMissResetsIt() = runBlocking<Unit> {
        val e = newEngine()
        val rec = Recorder(e, this)

        e.boardForTest.loadRows(listOf(".111111111"))
        e.debugSetPiece(PieceType.I, 1, -2, 0)
        e.hardDrop()
        assertEquals(50, rec.of<GameEvent.LinesCleared>().last().points)
        assertTrue(e.settle())

        e.boardForTest.loadRows(listOf(".111111111"))
        e.debugSetPiece(PieceType.I, 1, -2, 0)
        e.hardDrop()
        val second = rec.of<GameEvent.LinesCleared>().last()
        assertEquals(2, second.combo)
        assertEquals(75, second.points)
        assertEquals(listOf(2), rec.of<GameEvent.ComboChanged>().map { it.combo })
        assertEquals(125, e.state.value.score)
        assertTrue(e.settle())

        e.boardForTest.loadRows(emptyList())
        e.debugSetPiece(PieceType.O, 0, 4, 0)
        e.hardDrop() // no line -> combo broken
        assertEquals(0, e.state.value.combo)
        rec.stop()
    }
}

class EnginePowerUpTest {

    @Test
    fun bombClearsTheBottomFiveRowsAndDropsTheRest() = runBlocking<Unit> {
        val e = newEngine()
        val rec = Recorder(e, this)
        e.boardForTest.loadRows(
            listOf(
                "2.........", // y13
                "2.........", // y14
                "3333.33333", // y15 \
                "4.4.4.4.4.", // y16  |
                "5555555.5.", // y17  | bomb zone: 36 blocks
                "6.6.6.6.6.", // y18  |
                "7777777.77", // y19 /
            ),
        )
        val pieceY = e.state.value.piece!!.y
        e.usePowerUp(PowerUpType.BOMB)

        val s = e.state.value
        assertEquals(Phase.CLEARING, s.phase)
        assertEquals(ClearKind.BOMB, s.clear!!.kind)
        assertEquals(36, s.clear!!.cells.size)
        assertEquals(108, s.score)        // 36 * 3
        assertEquals(2, s.level)
        assertEquals(1, rec.of<GameEvent.PowerUpApplied>().size)

        assertTrue(e.settle())
        val end = e.state.value
        assertEquals(2, end.filled())
        assertEquals(2, end.cell(0, 18))
        assertEquals(2, end.cell(0, 19))
        assertEquals(pieceY, end.piece!!.y) // the falling piece waited for the explosion
        assertEquals(1, rec.of<GameEvent.ClearBurst>().size)
        rec.stop()
    }

    @Test
    fun bombOnAnEmptyBoardIsRejectedAndKeepsTheCharge() = runBlocking<Unit> {
        val e = newEngine()
        val rec = Recorder(e, this)
        e.usePowerUp(PowerUpType.BOMB)
        assertEquals(Phase.FALLING, e.state.value.phase)
        assertEquals(RejectReason.NOTHING_TO_CLEAR, rec.of<GameEvent.PowerUpRejected>().single().reason)
        assertTrue(rec.of<GameEvent.PowerUpApplied>().isEmpty())
        rec.stop()
    }

    @Test
    fun lightningClearsExactlyTheChosenColumn() = runBlocking<Unit> {
        val e = newEngine()
        val rec = Recorder(e, this)
        e.boardForTest.loadRows(
            listOf(
                "...1......",
                "..21......",
                "..21.3....",
                "1.21.3....",
            ),
        )
        e.usePowerUp(PowerUpType.LIGHTNING)
        assertEquals(Phase.TARGETING, e.state.value.phase)
        assertEquals(PowerUpType.LIGHTNING, e.state.value.targeting)

        e.targetCell(8, 10) // empty column: nothing happens, still choosing
        assertEquals(Phase.TARGETING, e.state.value.phase)
        assertEquals(RejectReason.NOTHING_TO_CLEAR, rec.of<GameEvent.PowerUpRejected>().single().reason)

        e.targetCell(3, 5)
        assertEquals(Phase.CLEARING, e.state.value.phase)
        assertEquals(3, e.state.value.clear!!.column)
        assertEquals(12, e.state.value.score) // 4 blocks * 3
        assertTrue(e.settle())
        val s = e.state.value
        for (y in 0..19) assertEquals(0, s.cell(3, y))
        assertEquals(6, s.filled())
        assertEquals(2, s.cell(2, 17))
        assertEquals(3, s.cell(5, 18))
        assertEquals(1, rec.of<GameEvent.PowerUpApplied>().size)
        rec.stop()
    }

    @Test
    fun hammerClearsA3x3AreaAndBlocksAboveFallIntoIt() {
        val e = newEngine()
        e.boardForTest.loadRows(
            listOf(
                "....5.....", // y15: above the hammer area
                "..........", // y16
                "...111....", // y17
                "..11111...", // y18
                "111111111.", // y19
            ),
        )
        e.usePowerUp(PowerUpType.HAMMER)
        e.targetCell(4, 18) // area x 3..5, y 17..19
        assertEquals(27, e.state.value.score) // 9 blocks * 3
        assertTrue(e.settle())
        val s = e.state.value
        assertEquals(5, s.cell(4, 18))        // fell 3 rows into the gap (15 -> 18)
        assertEquals(0, s.cell(4, 15))
        assertEquals(0, s.cell(3, 18))
        assertEquals(0, s.cell(5, 18))
        assertEquals(1, s.cell(2, 18))        // neighbours untouched
        assertEquals(1, s.cell(6, 19))
        assertEquals(0, s.cell(4, 19))
        assertEquals(9, s.filled())
    }

    @Test
    fun colorBombRemovesEveryBlockOfTheTappedColor() = runBlocking<Unit> {
        val e = newEngine()
        val rec = Recorder(e, this)
        e.boardForTest.loadRows(
            listOf(
                "2.1.......", // y17
                "1.2.2.....", // y18
                "2.1.1.2...", // y19
            ),
        )
        e.usePowerUp(PowerUpType.COLOR_BOMB)
        e.targetCell(1, 18) // empty cell: rejected
        assertEquals(Phase.TARGETING, e.state.value.phase)
        assertEquals(1, rec.of<GameEvent.PowerUpRejected>().size)

        e.targetCell(2, 18) // a colour-2 block
        assertEquals(15, e.state.value.score) // 5 blocks * 3
        assertTrue(e.settle())
        val s = e.state.value
        assertEquals(4, s.filled())
        assertTrue(s.board.all { it == 0 || it == 1 })
        assertEquals(1, s.cell(0, 19)) // the block that sat above a removed one dropped
        assertEquals(1, s.cell(2, 18))
        assertEquals(1, s.cell(2, 19))
        assertEquals(1, s.cell(4, 19))
        rec.stop()
    }

    @Test
    fun targetingPausesGravityUntilConfirmedOrCancelled() = runBlocking<Unit> {
        val e = newEngine()
        val rec = Recorder(e, this)
        val y0 = e.state.value.piece!!.y
        e.usePowerUp(PowerUpType.HAMMER)
        repeat(50) { e.update(100) }
        assertEquals(y0, e.state.value.piece!!.y)
        assertFalse(e.moveLeft())
        e.cancelTargeting()
        assertEquals(Phase.FALLING, e.state.value.phase)
        assertEquals(1, rec.of<GameEvent.TargetingCancelled>().size)
        assertTrue(rec.of<GameEvent.PowerUpApplied>().isEmpty())
        rec.stop()
    }

    @Test
    fun doubleScoreDoublesPointsThenExpires() = runBlocking<Unit> {
        val e = newEngine()
        val rec = Recorder(e, this)
        e.usePowerUp(PowerUpType.DOUBLE_SCORE)
        assertEquals(PowerUpRules.DOUBLE_SCORE_DURATION_MS, e.state.value.doubleScoreRemainingMs)
        assertEquals(1, rec.of<GameEvent.PowerUpApplied>().size)

        e.usePowerUp(PowerUpType.DOUBLE_SCORE) // already running
        assertEquals(RejectReason.ALREADY_ACTIVE, rec.of<GameEvent.PowerUpRejected>().single().reason)
        assertEquals(1, rec.of<GameEvent.PowerUpApplied>().size)

        e.boardForTest.loadRows(listOf(".111111111"))
        e.debugSetPiece(PieceType.I, 1, -2, 0)
        e.hardDrop()
        assertEquals(100, e.state.value.score)

        assertTrue(e.advanceUntil(maxMs = 40_000) { it.doubleScoreRemainingMs == 0L })
        assertEquals(1, rec.of<GameEvent.DoubleScoreEnded>().size)
        rec.stop()
    }

    @Test
    fun aPowerUpThatCompletesARowChainsIntoALineClear() = runBlocking<Unit> {
        val e = newEngine()
        val rec = Recorder(e, this)
        e.boardForTest.loadRows(
            listOf(
                "...111....", // y15 falls 3 rows when the hammer removes y16..y18 in columns 3..5
                "...111....", // y16 (smashed)
                "..........", // y17
                "111...1111", // y18 holes under the hammer area
                "..........", // y19
            ),
        )
        e.usePowerUp(PowerUpType.HAMMER)
        e.targetCell(4, 17)
        assertTrue(e.settle())
        val clears = rec.of<GameEvent.LinesCleared>()
        assertEquals(listOf(18), clears.single().rows)
        assertTrue(e.advanceUntil { it.phase == Phase.FALLING && it.clear == null && it.collapse == null })
        assertEquals(0, e.state.value.filled())
        rec.stop()
    }
}
