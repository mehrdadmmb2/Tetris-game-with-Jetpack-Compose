package com.neon.tetris.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import com.neon.tetris.ui.theme.Neon
import com.neon.tetris.ui.theme.darken
import com.neon.tetris.ui.theme.lighten

/**
 * Pre-builds the gradients for one cell size so the board can draw hundreds of glowing blocks per frame
 * without allocating brushes. Gradients are defined in cell-local coordinates and moved with translate().
 */
class BlockPainter(val cell: Float) {
    private val bodies: Array<Brush> = Array(8) { id ->
        val c = if (id == 0) Color.Gray else Neon.pieceColor(id)
        Brush.linearGradient(
            colors = listOf(c.lighten(0.45f), c, c.darken(0.38f)),
            start = Offset.Zero,
            end = Offset(cell, cell),
        )
    }
    val sheen: Brush = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
        startY = 0f,
        endY = cell * 0.55f,
    )

    fun body(id: Int): Brush = bodies[id.coerceIn(0, 7)]
}

/**
 * Draws one neon block with its top-left corner at ([left], [top]).
 * [glow] scales the halo, [scale] shrinks/grows around the cell centre, [whiten] blends towards white.
 */
fun DrawScope.drawBlock(
    painter: BlockPainter,
    colorId: Int,
    left: Float,
    top: Float,
    alpha: Float = 1f,
    glow: Float = 1f,
    scale: Float = 1f,
    whiten: Float = 0f,
) {
    if (alpha <= 0f || scale <= 0f) return
    val cell = painter.cell
    val color = Neon.pieceColor(colorId)
    val s = cell * scale
    val inset = (cell - s) / 2f
    val gap = cell * 0.04f
    val bs = s - gap * 2f
    val corner = CornerRadius(s * 0.2f)
    translate(left + inset + gap, top + inset + gap) {
        // soft drop shadow so blocks lift off the board
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.35f * alpha),
            topLeft = Offset(cell * 0.03f, cell * 0.07f),
            size = Size(bs, bs),
            cornerRadius = corner,
        )
        if (glow > 0.05f) {
            for (i in 2 downTo 1) {
                val g = cell * 0.10f * i * glow.coerceAtMost(3f)
                drawRoundRect(
                    color = color.copy(alpha = (0.09f * alpha * glow.coerceAtMost(2.2f)).coerceAtMost(0.6f)),
                    topLeft = Offset(-g, -g),
                    size = Size(bs + g * 2f, bs + g * 2f),
                    cornerRadius = CornerRadius(corner.x + g),
                )
            }
        }
        drawRoundRect(brush = painter.body(colorId), size = Size(bs, bs), cornerRadius = corner, alpha = alpha)
        drawRoundRect(brush = painter.sheen, size = Size(bs, bs * 0.55f), cornerRadius = corner, alpha = alpha * 0.8f)
        drawRoundRect(
            color = color.lighten(0.55f).copy(alpha = 0.85f * alpha),
            size = Size(bs, bs),
            cornerRadius = corner,
            style = Stroke(width = cell * 0.045f),
        )
        if (whiten > 0f) {
            drawRoundRect(
                color = Color.White.copy(alpha = (whiten * alpha).coerceIn(0f, 1f)),
                size = Size(bs, bs),
                cornerRadius = corner,
            )
        }
    }
}
