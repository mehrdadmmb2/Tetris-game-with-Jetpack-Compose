package com.neon.tetris.domain

import kotlin.math.pow
import kotlin.math.roundToInt

/** Fixed geometry of the playfield. */
object BoardSpec {
    const val COLS = 10
    const val ROWS = 20
}

/**
 * Level progression. The formula requested by the design is `requiredScore = level × 100`:
 * level 1 is completed at 100 points, level 2 at 200 ... level 100 at 10,000 points (total score).
 * Change [POINTS_PER_LEVEL] / [requiredScore] here to re-tune the whole game.
 */
object LevelSystem {
    const val MAX_LEVEL = 100
    const val POINTS_PER_LEVEL = 100

    private const val START_INTERVAL_MS = 800.0
    private const val MIN_INTERVAL_MS = 50.0
    private const val SPEEDUP_PER_LEVEL = 0.93

    /** Total score at which [level] is completed: `level × 100`. */
    fun requiredScore(level: Int): Int = level.coerceIn(0, MAX_LEVEL) * POINTS_PER_LEVEL

    /** Level the player is on for a given total score (1..[MAX_LEVEL]). */
    fun levelForScore(score: Int): Int =
        (score.coerceAtLeast(0) / POINTS_PER_LEVEL + 1).coerceAtMost(MAX_LEVEL)

    /** Progress (0..1) through the current level. 1.0 once the final level is completed. */
    fun progress(score: Int): Float {
        val s = score.coerceAtLeast(0)
        if (s >= requiredScore(MAX_LEVEL)) return 1f
        val level = levelForScore(s)
        val start = requiredScore(level - 1)
        val span = requiredScore(level) - start
        return ((s - start).toFloat() / span).coerceIn(0f, 1f)
    }

    /** Progress (0..1) of a score towards the final level; used for the "campaign" bar on the home screen. */
    fun overallProgress(score: Int): Float =
        (score.toFloat() / requiredScore(MAX_LEVEL)).coerceIn(0f, 1f)

    /** Milliseconds a piece needs to fall one row. Gets faster with every level, asymptotically towards 50 ms. */
    fun gravityIntervalMs(level: Int): Long {
        val l = level.coerceIn(1, MAX_LEVEL)
        return (MIN_INTERVAL_MS + (START_INTERVAL_MS - MIN_INTERVAL_MS) * SPEEDUP_PER_LEVEL.pow(l - 1)).toLong()
    }

    /** Line-clear animations get slightly shorter on higher levels so the game keeps its rhythm. */
    fun animationSpeedFactor(level: Int): Float = when {
        level >= 30 -> 0.7f
        level >= 10 -> 0.85f
        else -> 1f
    }
}

/** Every cell removed by a completed line is worth points; combos and multi-line clears multiply them. */
object ScoreRules {
    const val CELL_POINTS = 5
    const val POWER_UP_CELL_POINTS = 3
    const val MAX_COMBO_STEPS = 9

    fun lineMultiplier(lines: Int): Float = when {
        lines <= 1 -> 1f
        lines == 2 -> 1.5f
        lines == 3 -> 2f
        else -> 3f
    }

    /** combo = number of consecutive piece placements that cleared lines (1 = first clear, no bonus). */
    fun comboMultiplier(combo: Int): Float = 1f + 0.5f * (combo.coerceIn(1, MAX_COMBO_STEPS) - 1)

    fun lineClearPoints(lines: Int, combo: Int, doubleScore: Boolean): Int {
        val base = lines * BoardSpec.COLS * CELL_POINTS * lineMultiplier(lines) * comboMultiplier(combo)
        return base.roundToInt() * (if (doubleScore) 2 else 1)
    }

    fun powerUpPoints(cells: Int, doubleScore: Boolean): Int =
        cells * POWER_UP_CELL_POINTS * (if (doubleScore) 2 else 1)
}
