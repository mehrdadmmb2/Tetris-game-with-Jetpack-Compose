package com.neon.tetris.game

import com.neon.tetris.domain.BoardSpec
import com.neon.tetris.domain.PowerUpType

enum class Phase { READY, FALLING, TARGETING, CLEARING, COLLAPSING, PAUSED, GAME_OVER }

/** [burstAt] = animation progress at which the particle burst fires. */
enum class ClearKind(val burstAt: Float) {
    LINES(0.72f), BOMB(0.30f), LIGHTNING(0.35f), HAMMER(0.25f), COLOR_BOMB(0.60f)
}

enum class RejectReason { NOTHING_TO_CLEAR, ALREADY_ACTIVE }

data class AnimCell(val x: Int, val y: Int, val color: Int)

/** A removal in progress (line clear or power-up). [progress] runs 0..1 over the whole timeline. */
class ClearAnimation(
    val id: Int,
    val kind: ClearKind,
    val cells: List<AnimCell>,
    val progress: Float,
    val focusX: Float,
    val focusY: Float,
    /** Struck column for lightning, otherwise -1. */
    val column: Int,
)

/** Blocks above a removal falling into the gap. [offsets] = rows fallen, per cell index of the *new* board. */
class CollapseAnimation(val offsets: IntArray, val progress: Float)

/** Immutable snapshot published by the engine through a StateFlow. */
class GameState(
    val revision: Long,
    val phase: Phase,
    val board: IntArray,
    val piece: ActivePiece?,
    val ghostY: Int,
    val next: List<PieceType>,
    val score: Int,
    val level: Int,
    val lines: Int,
    val combo: Int,
    val levelProgress: Float,
    val doubleScoreRemainingMs: Long,
    val targeting: PowerUpType?,
    val clear: ClearAnimation?,
    val collapse: CollapseAnimation?,
) {
    fun cell(x: Int, y: Int): Int = board[y * BoardSpec.COLS + x]

    val isPlayable: Boolean get() = phase == Phase.FALLING
    val isActive: Boolean
        get() = phase != Phase.READY && phase != Phase.GAME_OVER
}

/** One-shot happenings, consumed by the UI for haptics/particles/popups and by the view model for persistence. */
sealed interface GameEvent {
    data object Started : GameEvent
    data object Moved : GameEvent
    data class Rotated(val clockwise: Boolean) : GameEvent
    data object SoftDropped : GameEvent
    data class HardDropped(
        val type: PieceType,
        val from: List<Cell>,
        val to: List<Cell>,
        val distance: Int,
    ) : GameEvent
    data class Locked(val type: PieceType, val cells: List<Cell>) : GameEvent
    data class LinesCleared(val rows: List<Int>, val lines: Int, val points: Int, val combo: Int) : GameEvent
    data class ClearBurst(
        val kind: ClearKind,
        val cells: List<AnimCell>,
        val focusX: Float,
        val focusY: Float,
    ) : GameEvent
    data class ComboChanged(val combo: Int) : GameEvent
    /** [x]/[y] are board coordinates (in cells) where the score popup should appear. */
    data class ScoreAdded(val points: Int, val x: Float, val y: Float) : GameEvent
    data class LevelUp(val level: Int) : GameEvent
    data class TargetingStarted(val type: PowerUpType) : GameEvent
    data class TargetingCancelled(val type: PowerUpType) : GameEvent
    /** A charge was spent. The view model decrements the persisted quantity when it sees this. */
    data class PowerUpApplied(val type: PowerUpType) : GameEvent
    data class PowerUpRejected(val type: PowerUpType, val reason: RejectReason) : GameEvent
    data object DoubleScoreEnded : GameEvent
    data object Paused : GameEvent
    data object Resumed : GameEvent
    data object GameOver : GameEvent
}
