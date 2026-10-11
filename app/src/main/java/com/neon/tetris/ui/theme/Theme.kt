package com.neon.tetris.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.neon.tetris.domain.PowerUpType
import com.neon.tetris.game.PieceType
import com.neon.tetris.util.Haptics
import java.text.NumberFormat
import java.util.Locale

/** Dark / neon palette. */
object Neon {
    val Bg0 = Color(0xFF05020F)
    val Bg1 = Color(0xFF0B0526)
    val Bg2 = Color(0xFF1A0B47)
    val Bg3 = Color(0xFF2B1068)

    val Cyan = Color(0xFF00E5FF)
    val Magenta = Color(0xFFFF2BD6)
    val Yellow = Color(0xFFFFE600)
    val Green = Color(0xFF39FF88)
    val Red = Color(0xFFFF3D6E)
    val Blue = Color(0xFF4D7CFF)
    val Orange = Color(0xFFFF9A1F)
    val Purple = Color(0xFFB84DFF)

    val Panel = Color(0xFF140A33)
    val PanelHigh = Color(0xFF1E1048)
    val TextDim = Color(0xFF9A94C8)
    val Stroke = Color(0x33FFFFFF)

    /** Colour for a board cell id (1..7 = I, O, T, S, Z, J, L). */
    fun pieceColor(id: Int): Color = when (id) {
        1 -> Cyan
        2 -> Yellow
        3 -> Purple
        4 -> Green
        5 -> Red
        6 -> Blue
        7 -> Orange
        else -> Color.Transparent
    }

    fun pieceColor(type: PieceType): Color = pieceColor(type.id)

    fun powerUpColor(type: PowerUpType): Color = when (type) {
        PowerUpType.BOMB -> Orange
        PowerUpType.LIGHTNING -> Cyan
        PowerUpType.HAMMER -> Yellow
        PowerUpType.COLOR_BOMB -> Magenta
        PowerUpType.DOUBLE_SCORE -> Green
    }

    /** Combo colour ramps up from cyan to hot magenta. */
    fun comboColor(combo: Int): Color = when {
        combo >= 6 -> Magenta
        combo >= 4 -> Orange
        combo >= 3 -> Yellow
        else -> Cyan
    }
}

fun Color.lighten(fraction: Float): Color = lerp(this, Color.White, fraction)
fun Color.darken(fraction: Float): Color = lerp(this, Color.Black, fraction)

private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)

/** 12,345 with Latin digits regardless of the device language. */
fun formatNumber(value: Int): String = numberFormat.format(value.toLong())

/** Haptics are provided once at the root so any button can give feedback. */
val LocalHaptics = staticCompositionLocalOf<Haptics> { error("Haptics not provided") }
