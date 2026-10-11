package com.neon.tetris.game

import com.neon.tetris.domain.BoardSpec
import kotlin.math.abs

/**
 * A simple one-piece-lookahead player (aggregate height, lines, holes, bumpiness) that only uses the public engine API.
 * Tests use it to produce realistic long games with many line clears.
 */
internal object GreedyBot {
    private const val C = BoardSpec.COLS
    private const val R = BoardSpec.ROWS

    private fun fits(b: IntArray, type: PieceType, rotation: Int, bx: Int, by: Int): Boolean {
        val o = Tetrominoes.offsets(type, rotation)
        for (i in 0 until 4) {
            val x = bx + o[i * 2]
            val y = by + o[i * 2 + 1]
            if (x !in 0 until C || y >= R) return false
            if (y >= 0 && b[y * C + x] != 0) return false
        }
        return true
    }

    private fun evaluate(b: IntArray): Double {
        val grid = Array(R) { y -> IntArray(C) { x -> b[y * C + x] } }
        val kept = grid.filter { row -> row.any { it == 0 } }
        val cleared = R - kept.size
        val padded = List(cleared) { IntArray(C) } + kept
        val heights = IntArray(C) { x ->
            val top = padded.indexOfFirst { it[x] != 0 }
            if (top < 0) 0 else R - top
        }
        var holes = 0
        for (x in 0 until C) {
            var seen = false
            for (y in 0 until R) if (padded[y][x] != 0) seen = true else if (seen) holes++
        }
        var bumpiness = 0
        for (x in 0 until C - 1) bumpiness += abs(heights[x] - heights[x + 1])
        return -0.51 * heights.sum() + 0.76 * cleared - 0.36 * holes - 0.18 * bumpiness
    }

    /** Chooses the best placement for the current piece, steers the piece there and hard-drops it. */
    fun playPiece(engine: TetrisEngine) {
        val s = engine.state.value
        val piece = s.piece ?: return
        if (s.phase != Phase.FALLING) return

        var bestScore = Double.NEGATIVE_INFINITY
        var bestRotation = 0
        var bestX = piece.x
        for (rotation in 0..3) {
            if (piece.type == PieceType.O && rotation > 0) break
            for (bx in -3..9) {
                if (!fits(s.board, piece.type, rotation, bx, -3)) continue
                var y = -3
                while (fits(s.board, piece.type, rotation, bx, y + 1)) y++
                val sim = s.board.copyOf()
                val o = Tetrominoes.offsets(piece.type, rotation)
                for (i in 0 until 4) {
                    val cy = y + o[i * 2 + 1]
                    if (cy >= 0) sim[cy * C + bx + o[i * 2]] = piece.type.id
                }
                val score = evaluate(sim)
                if (score > bestScore) {
                    bestScore = score
                    bestRotation = rotation
                    bestX = bx
                }
            }
        }

        repeat(bestRotation) { engine.rotate(true) }
        var guard = 0
        while (guard++ < 14) {
            val x = engine.state.value.piece?.x ?: break
            if (x < bestX) engine.moveRight() else if (x > bestX) engine.moveLeft() else break
        }
        engine.hardDrop()
    }
}
