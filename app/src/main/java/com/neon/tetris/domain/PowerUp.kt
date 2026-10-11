package com.neon.tetris.domain

/** How a power-up picks what it affects. */
enum class TargetMode { NONE, COLUMN, AREA, BLOCK_COLOR }

enum class PowerUpType(val key: String, val targetMode: TargetMode) {
    /** Clears the bottom 5 rows. */
    BOMB("bomb", TargetMode.NONE),
    /** Clears one column (tap a column). */
    LIGHTNING("lightning", TargetMode.COLUMN),
    /** Clears a 3×3 area (tap the centre). */
    HAMMER("hammer", TargetMode.AREA),
    /** Clears every block of one colour (tap a block). */
    COLOR_BOMB("color_bomb", TargetMode.BLOCK_COLOR),
    /** Doubles all points for a limited time. */
    DOUBLE_SCORE("double_score", TargetMode.NONE);

    val needsTarget: Boolean get() = targetMode != TargetMode.NONE
}

object PowerUpRules {
    /** Charges granted the first time a power-up is unlocked through a purchase. */
    const val CHARGES_ON_UNLOCK = 3
    const val MAX_CHARGES = 9
    /** Every N levels reached during play, each owned power-up recharges by [RECHARGE_AMOUNT]. */
    const val RECHARGE_EVERY_LEVELS = 5
    const val RECHARGE_AMOUNT = 1

    const val BOMB_ROWS = 5
    const val HAMMER_RADIUS = 1 // 1 -> 3×3
    const val DOUBLE_SCORE_DURATION_MS = 30_000L
}
