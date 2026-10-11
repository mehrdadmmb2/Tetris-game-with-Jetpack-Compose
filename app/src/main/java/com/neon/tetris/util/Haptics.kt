package com.neon.tetris.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Thin wrapper around the system vibrator with a named vocabulary of game feedback. */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** Mirrors the "Haptic feedback" setting. */
    @Volatile
    var enabled: Boolean = true

    /** Light tick: movement, rotation, button presses. */
    fun tick() = oneShot(9, 45)

    fun click() = oneShot(14, 95)

    /** Piece locked into the stack. */
    fun thud() = oneShot(20, 150)

    /** Hard drop impact; stronger the further the piece fell. */
    fun impact(distance: Int) = oneShot((18 + distance).toLong().coerceAtMost(40), (130 + distance * 6).coerceAtMost(255))

    fun lineClear(lines: Int) = when {
        lines >= 4 -> waveform(longArrayOf(0, 25, 40, 25, 40, 70), intArrayOf(0, 180, 0, 220, 0, 255))
        lines >= 2 -> waveform(longArrayOf(0, 22, 40, 45), intArrayOf(0, 170, 0, 230))
        else -> waveform(longArrayOf(0, 28, 30, 18), intArrayOf(0, 150, 0, 110))
    }

    /** Escalates with the combo counter. */
    fun combo(combo: Int) {
        val pulses = combo.coerceIn(2, 5)
        val timings = LongArray(pulses * 2) { if (it % 2 == 0) 35L else 22L }
        val amps = IntArray(pulses * 2) { if (it % 2 == 0) 0 else (140 + it * 20).coerceAtMost(255) }
        // waveform layout: [pause, vibrate, pause, vibrate, ...]
        waveform(timings, amps)
    }

    fun levelUp() = waveform(longArrayOf(0, 30, 50, 30, 50, 80), intArrayOf(0, 160, 0, 210, 0, 255))

    fun explosion() = waveform(longArrayOf(0, 120, 40, 60, 30, 40), intArrayOf(0, 255, 0, 170, 0, 90))

    fun lightning() = waveform(longArrayOf(0, 18, 28, 18, 28, 50), intArrayOf(0, 220, 0, 255, 0, 180))

    fun powerUp() = waveform(longArrayOf(0, 20, 30, 40), intArrayOf(0, 140, 0, 200))

    fun gameOver() = waveform(longArrayOf(0, 90, 60, 70, 60, 50, 60, 140), intArrayOf(0, 255, 0, 190, 0, 130, 0, 70))

    private fun oneShot(ms: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!enabled || !v.hasVibrator()) return
        val amp = if (v.hasAmplitudeControl()) amplitude.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
        v.vibrate(VibrationEffect.createOneShot(ms, amp))
    }

    private fun waveform(timings: LongArray, amplitudes: IntArray) {
        val v = vibrator ?: return
        if (!enabled || !v.hasVibrator()) return
        val effect = if (v.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        v.vibrate(effect)
    }
}
