package com.neon.tetris.ui.game

import android.os.SystemClock
import com.neon.tetris.domain.PowerUpType
import com.neon.tetris.game.ClearKind
import com.neon.tetris.game.GameEvent
import com.neon.tetris.util.Haptics

/** Maps engine events to haptic feedback. */
class GameFeedback(private val haptics: Haptics) {

    private var lastTick = 0L
    private var suppressThudUntil = 0L

    private fun tick() {
        val now = SystemClock.uptimeMillis()
        if (now - lastTick >= 40L) {
            lastTick = now
            haptics.tick()
        }
    }

    fun onEvent(e: GameEvent) {
        when (e) {
            is GameEvent.Moved, is GameEvent.SoftDropped, is GameEvent.Rotated -> tick()
            is GameEvent.HardDropped -> {
                suppressThudUntil = SystemClock.uptimeMillis() + 60L // the impact replaces the plain lock thud
                haptics.impact(e.distance)
            }
            is GameEvent.Locked -> if (SystemClock.uptimeMillis() > suppressThudUntil) haptics.thud()
            is GameEvent.LinesCleared -> if (e.combo >= 2) haptics.combo(e.combo) else haptics.lineClear(e.lines)
            is GameEvent.LevelUp -> haptics.levelUp()
            is GameEvent.ClearBurst -> when (e.kind) {
                ClearKind.BOMB -> haptics.explosion()
                ClearKind.LIGHTNING -> haptics.lightning()
                ClearKind.HAMMER -> haptics.impact(14)
                ClearKind.COLOR_BOMB -> haptics.powerUp()
                ClearKind.LINES -> Unit
            }
            is GameEvent.TargetingStarted -> haptics.click()
            is GameEvent.PowerUpApplied -> if (e.type == PowerUpType.DOUBLE_SCORE) haptics.powerUp()
            is GameEvent.GameOver -> haptics.gameOver()
            else -> Unit
        }
    }
}
