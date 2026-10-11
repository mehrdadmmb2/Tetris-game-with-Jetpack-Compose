package com.neon.tetris.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neon.tetris.domain.PowerUpType
import com.neon.tetris.ui.theme.Neon
import com.neon.tetris.ui.theme.darken
import com.neon.tetris.ui.theme.lighten
import kotlin.math.cos
import kotlin.math.sin

enum class IconKind {
    LEFT, RIGHT, DOWN, DROP, ROTATE_CW, ROTATE_CCW, PAUSE, PLAY, BACK, LOCK, CHECK, CLOSE, GEAR, HOME,
    BOLT, BOMB, HAMMER, COLOR_BOMB,
}

/** A glowing icon drawn with canvas primitives (no image assets). */
@Composable
fun NeonIcon(kind: IconKind, color: Color, size: Dp = 28.dp, modifier: Modifier = Modifier, glow: Boolean = true) {
    Canvas(modifier.size(size)) {
        if (glow) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.26f), Color.Transparent),
                    center = center,
                    radius = this.size.minDimension * 0.85f,
                ),
                radius = this.size.minDimension * 0.85f,
            )
        }
        drawNeonIcon(kind, color)
    }
}

@Composable
fun PowerUpIcon(type: PowerUpType, color: Color, size: Dp = 30.dp, modifier: Modifier = Modifier) {
    if (type == PowerUpType.DOUBLE_SCORE) {
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            NeonText("×2", size = (size.value * 0.62f).sp, color = color, weight = FontWeight.Black, glow = color, letterSpacing = 0.sp)
        }
    } else {
        val kind = when (type) {
            PowerUpType.BOMB -> IconKind.BOMB
            PowerUpType.LIGHTNING -> IconKind.BOLT
            PowerUpType.HAMMER -> IconKind.HAMMER
            else -> IconKind.COLOR_BOMB
        }
        NeonIcon(kind, color, size, modifier)
    }
}

fun DrawScope.drawNeonIcon(kind: IconKind, color: Color) {
    val s = size.minDimension
    val w = s * 0.13f
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (kind) {
        IconKind.LEFT -> drawPath(
            Path().apply { moveTo(.62f * s, .16f * s); lineTo(.30f * s, .5f * s); lineTo(.62f * s, .84f * s) },
            color, style = stroke,
        )
        IconKind.RIGHT -> drawPath(
            Path().apply { moveTo(.38f * s, .16f * s); lineTo(.70f * s, .5f * s); lineTo(.38f * s, .84f * s) },
            color, style = stroke,
        )
        IconKind.DOWN -> drawPath(
            Path().apply { moveTo(.16f * s, .36f * s); lineTo(.5f * s, .70f * s); lineTo(.84f * s, .36f * s) },
            color, style = stroke,
        )
        IconKind.DROP -> {
            drawPath(
                Path().apply {
                    moveTo(.2f * s, .1f * s); lineTo(.5f * s, .38f * s); lineTo(.8f * s, .1f * s)
                    moveTo(.2f * s, .38f * s); lineTo(.5f * s, .66f * s); lineTo(.8f * s, .38f * s)
                },
                color, style = stroke,
            )
            drawLine(color, Offset(.2f * s, .88f * s), Offset(.8f * s, .88f * s), strokeWidth = w, cap = StrokeCap.Round)
        }
        IconKind.ROTATE_CW -> rotateArrow(color, w)
        IconKind.ROTATE_CCW -> scale(-1f, 1f, pivot = center) { rotateArrow(color, w) }
        IconKind.PAUSE -> {
            drawRoundRect(color, Offset(.26f * s, .2f * s), Size(.17f * s, .6f * s), CornerRadius(.05f * s))
            drawRoundRect(color, Offset(.57f * s, .2f * s), Size(.17f * s, .6f * s), CornerRadius(.05f * s))
        }
        IconKind.PLAY -> drawPath(
            Path().apply { moveTo(.3f * s, .16f * s); lineTo(.82f * s, .5f * s); lineTo(.3f * s, .84f * s); close() },
            color,
        )
        IconKind.BACK -> {
            drawLine(color, Offset(.82f * s, .5f * s), Offset(.2f * s, .5f * s), strokeWidth = w, cap = StrokeCap.Round)
            drawPath(
                Path().apply { moveTo(.46f * s, .24f * s); lineTo(.2f * s, .5f * s); lineTo(.46f * s, .76f * s) },
                color, style = stroke,
            )
        }
        IconKind.LOCK -> {
            drawArc(
                color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(.3f * s, .14f * s), size = Size(.4f * s, .5f * s),
                style = Stroke(width = w * 0.8f, cap = StrokeCap.Round),
            )
            drawRoundRect(color, Offset(.2f * s, .44f * s), Size(.6f * s, .46f * s), CornerRadius(.09f * s))
            drawCircle(Neon.Bg0, .06f * s, Offset(.5f * s, .64f * s))
            drawLine(Neon.Bg0, Offset(.5f * s, .64f * s), Offset(.5f * s, .76f * s), strokeWidth = .05f * s, cap = StrokeCap.Round)
        }
        IconKind.CHECK -> drawPath(
            Path().apply { moveTo(.2f * s, .55f * s); lineTo(.43f * s, .77f * s); lineTo(.8f * s, .27f * s) },
            color, style = Stroke(width = w * 1.15f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        IconKind.CLOSE -> {
            drawLine(color, Offset(.22f * s, .22f * s), Offset(.78f * s, .78f * s), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(color, Offset(.78f * s, .22f * s), Offset(.22f * s, .78f * s), strokeWidth = w, cap = StrokeCap.Round)
        }
        IconKind.GEAR -> {
            drawCircle(color, .25f * s, center, style = Stroke(width = w * 1.1f))
            drawCircle(color, .07f * s, center)
            for (i in 0 until 8) {
                rotate(i * 45f, pivot = center) {
                    drawRoundRect(color, Offset(.435f * s, .07f * s), Size(.13f * s, .2f * s), CornerRadius(.03f * s))
                }
            }
        }
        IconKind.HOME -> {
            drawPath(
                Path().apply { moveTo(.1f * s, .5f * s); lineTo(.5f * s, .14f * s); lineTo(.9f * s, .5f * s) },
                color, style = stroke,
            )
            drawPath(
                Path().apply {
                    moveTo(.22f * s, .46f * s); lineTo(.22f * s, .84f * s); lineTo(.42f * s, .84f * s)
                    lineTo(.42f * s, .6f * s); lineTo(.58f * s, .6f * s); lineTo(.58f * s, .84f * s)
                    lineTo(.78f * s, .84f * s); lineTo(.78f * s, .46f * s)
                },
                color, style = Stroke(width = w * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        IconKind.BOLT -> drawPath(
            Path().apply {
                moveTo(.60f * s, .04f * s); lineTo(.22f * s, .56f * s); lineTo(.46f * s, .56f * s)
                lineTo(.36f * s, .96f * s); lineTo(.80f * s, .38f * s); lineTo(.54f * s, .38f * s); close()
            },
            color,
        )
        IconKind.BOMB -> drawBomb(Brush.linearGradient(listOf(color.lighten(.35f), color, color.darken(.35f)), Offset(.2f * s, .3f * s), Offset(.7f * s, .9f * s)), color)
        IconKind.COLOR_BOMB -> drawBomb(
            Brush.sweepGradient(
                listOf(Neon.Cyan, Neon.Green, Neon.Yellow, Neon.Orange, Neon.Magenta, Neon.Purple, Neon.Cyan),
                center = Offset(.46f * s, .58f * s),
            ),
            color,
        )
        IconKind.HAMMER -> rotate(-40f, pivot = center) {
            drawRoundRect(color.darken(.25f), Offset(.455f * s, .26f * s), Size(.1f * s, .68f * s), CornerRadius(.04f * s))
            drawRoundRect(color, Offset(.2f * s, .08f * s), Size(.6f * s, .3f * s), CornerRadius(.07f * s))
            drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(.24f * s, .11f * s), Size(.52f * s, .08f * s), CornerRadius(.04f * s))
        }
    }
}

private fun DrawScope.rotateArrow(color: Color, w: Float) {
    val s = size.minDimension
    val inset = s * 0.2f
    val r = s / 2f - inset
    drawArc(
        color, startAngle = -60f, sweepAngle = 270f, useCenter = false,
        topLeft = Offset(inset, inset), size = Size(s - 2 * inset, s - 2 * inset),
        style = Stroke(width = w, cap = StrokeCap.Round),
    )
    // arrow head at the end of the arc (angle 210°), pointing along the clockwise tangent
    val a = Math.toRadians(210.0)
    val end = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
    val tangent = Offset(-sin(a).toFloat(), cos(a).toFloat())
    val radial = Offset(cos(a).toFloat(), sin(a).toFloat())
    val tip = end + tangent * (s * 0.17f)
    val b1 = end + radial * (s * 0.16f)
    val b2 = end - radial * (s * 0.16f)
    drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(b1.x, b1.y); lineTo(b2.x, b2.y); close() }, color)
}

private fun DrawScope.drawBomb(body: Brush, accent: Color) {
    val s = size.minDimension
    val c = Offset(.46f * s, .58f * s)
    drawCircle(body, radius = .3f * s, center = c)
    drawCircle(Color.White.copy(alpha = 0.55f), .07f * s, Offset(.36f * s, .47f * s))
    drawRoundRect(accent.darken(.45f), Offset(.37f * s, .2f * s), Size(.18f * s, .12f * s), CornerRadius(.03f * s))
    val fuse = Path().apply {
        moveTo(.5f * s, .2f * s)
        quadraticBezierTo(.62f * s, .08f * s, .74f * s, .12f * s)
    }
    drawPath(fuse, Color(0xFFFFD27A), style = Stroke(width = .05f * s, cap = StrokeCap.Round))
    val spark = Offset(.78f * s, .1f * s)
    drawCircle(Neon.Yellow, .055f * s, spark)
    for (i in 0 until 4) {
        val a = Math.toRadians(i * 90.0 + 45.0)
        drawLine(
            Neon.Yellow,
            spark + Offset(cos(a).toFloat(), sin(a).toFloat()) * (.07f * s),
            spark + Offset(cos(a).toFloat(), sin(a).toFloat()) * (.13f * s),
            strokeWidth = .025f * s, cap = StrokeCap.Round,
        )
    }
}
