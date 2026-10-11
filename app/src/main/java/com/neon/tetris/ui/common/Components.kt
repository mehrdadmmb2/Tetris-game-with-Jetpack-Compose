package com.neon.tetris.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neon.tetris.game.PieceType
import com.neon.tetris.game.Tetrominoes
import com.neon.tetris.ui.theme.LocalHaptics
import com.neon.tetris.ui.theme.Neon
import com.neon.tetris.ui.theme.formatNumber
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ------------------------------------------------------------------------------------------------ text

@Composable
fun NeonText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    color: Color = Color.White,
    weight: FontWeight = FontWeight.Bold,
    glow: Color? = null,
    letterSpacing: TextUnit = 1.sp,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = letterSpacing,
            textAlign = align,
            shadow = glow?.let { Shadow(color = it.copy(alpha = 0.9f), offset = Offset.Zero, blurRadius = 18f) },
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

// ------------------------------------------------------------------------------------------------ interaction

/** Clickable without the default debug indication (there is no Material ripple in this app). */
fun Modifier.plainClickable(enabled: Boolean = true, role: Role? = null, onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = enabled,
        role = role,
        onClick = onClick,
    )
}

internal fun DrawScope.glowBox(color: Color, intensity: Float, corner: Float, layers: Int = 4) {
    val spread = 4.dp.toPx()
    for (i in layers downTo 1) {
        val pad = spread * i * 0.9f
        drawRoundRect(
            color = color.copy(alpha = (0.075f * intensity).coerceIn(0f, 1f)),
            topLeft = Offset(-pad, -pad),
            size = Size(size.width + pad * 2, size.height + pad * 2),
            cornerRadius = CornerRadius(corner + pad),
        )
    }
}

@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Neon.Cyan,
    filled: Boolean = true,
    enabled: Boolean = true,
    height: Dp = 58.dp,
    textSize: TextUnit = 18.sp,
    icon: IconKind? = null,
) {
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 520f),
        label = "buttonScale",
    )
    val glow by animateFloatAsState(if (pressed) 1.7f else 1f, tween(110), label = "buttonGlow")
    val shape = RoundedCornerShape(18.dp)
    val contentColor = if (filled) Neon.Bg0 else color

    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .drawBehind { glowBox(color, (if (filled) 1.0f else 0.55f) * glow, 18.dp.toPx()) }
            .clip(shape)
            .background(
                if (filled) Brush.horizontalGradient(listOf(color, lerp(color, Neon.Magenta, 0.5f)))
                else Brush.verticalGradient(listOf(Neon.PanelHigh, Neon.Panel)),
            )
            .border(2.dp, if (filled) Color.White.copy(alpha = 0.35f) else color.copy(alpha = 0.9f), shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                haptics.click()
                onClick()
            }
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon != null) NeonIcon(icon, contentColor, 24.dp, glow = false)
            NeonText(text, size = textSize, color = contentColor, weight = FontWeight.Black, letterSpacing = 1.5.sp)
        }
    }
}

/** Small square icon button (back, pause, close ...). */
@Composable
fun NeonIconButton(
    icon: IconKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Neon.Cyan,
    size: Dp = 46.dp,
) {
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "iconBtn")
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Neon.PanelHigh, Neon.Panel)))
            .border(1.5.dp, color.copy(alpha = 0.7f), shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.tick()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        NeonIcon(icon, color, size * 0.55f, glow = false)
    }
}

@Composable
fun NeonSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Neon.Cyan,
) {
    val haptics = LocalHaptics.current
    val t by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "switch")
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .size(width = 58.dp, height = 32.dp)
            .clip(shape)
            .background(lerp(Neon.Panel, color.copy(alpha = 0.35f), t.coerceIn(0f, 1f)))
            .border(2.dp, lerp(Neon.Stroke, color, t.coerceIn(0f, 1f)), shape)
            .plainClickable(role = Role.Switch) {
                haptics.tick()
                onCheckedChange(!checked)
            }
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset(x = 26.dp * t)
                .size(24.dp)
                .background(lerp(Neon.TextDim, color, t.coerceIn(0f, 1f)), CircleShape),
        )
    }
}

// ------------------------------------------------------------------------------------------------ surfaces

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    accent: Color = Neon.Cyan,
    corner: Dp = 20.dp,
    glow: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .then(if (glow) Modifier.drawBehind { glowBox(accent, 0.55f, corner.toPx(), layers = 3) } else Modifier)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Neon.PanelHigh.copy(alpha = 0.88f), Neon.Panel.copy(alpha = 0.92f))))
            .border(1.dp, Brush.verticalGradient(listOf(accent.copy(alpha = 0.6f), Neon.Stroke)), shape),
        content = content,
    )
}

@Composable
fun NeonProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Neon.Cyan,
    height: Dp = 10.dp,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(450, easing = FastOutSlowInEasing), label = "progress")
    Canvas(modifier.height(height)) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.09f), cornerRadius = radius)
        val w = size.width * animated
        if (w > 0f) {
            val fill = maxOf(w, size.height)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(color, lerp(color, Neon.Magenta, 0.6f)), 0f, fill),
                size = Size(fill, size.height),
                cornerRadius = radius,
            )
            drawCircle(color.copy(alpha = 0.35f), radius = size.height * 1.1f, center = Offset(fill - size.height / 2f, size.height / 2f))
        }
    }
}

/** Number that counts up/down smoothly and pops whenever it changes. */
@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    size: TextUnit = 28.sp,
    color: Color = Color.White,
    glow: Color? = Neon.Cyan,
    pop: Boolean = true,
    durationMs: Int = 500,
    countUpOnEnter: Boolean = false,
) {
    var target by remember { mutableIntStateOf(if (countUpOnEnter) 0 else value) }
    LaunchedEffect(value) { target = value }
    val shown by animateIntAsState(target, tween(durationMs, easing = FastOutSlowInEasing), label = "counter")
    val scale = remember { Animatable(1f) }
    var skipFirst by remember { mutableStateOf(true) }
    LaunchedEffect(value) {
        if (skipFirst) {
            skipFirst = false
        } else if (pop) {
            scale.snapTo(1.18f)
            scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
        }
    }
    NeonText(
        text = formatNumber(shown),
        modifier = modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        size = size,
        color = color,
        weight = FontWeight.Black,
        glow = glow,
        letterSpacing = 1.sp,
    )
}

/** Fades and slides its content in, one step later for every [index]. */
@Composable
fun StaggeredEntrance(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(70L * index + 60L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(380)) + slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 4 },
    ) { content() }
}

// ------------------------------------------------------------------------------------------------ background

private class Floater(
    val type: PieceType,
    val xFrac: Float,
    val cycles: Int,
    val phase: Float,
    val cellDp: Float,
    val spin: Float,
)

private val floaters = List(10) { i ->
    val r = Random(i * 97 + 3)
    Floater(
        type = PieceType.entries[i % 7],
        xFrac = r.nextFloat(),
        cycles = 1 + i % 3,
        phase = r.nextFloat(),
        cellDp = 12f + r.nextFloat() * 14f,
        spin = (r.nextFloat() - 0.5f) * 160f,
    )
}

/** Deep violet gradient with slowly drifting neon glow orbs; optionally with drifting tetrominoes. */
@Composable
fun NeonBackground(
    modifier: Modifier = Modifier,
    floatingBlocks: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "background")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "backgroundTime",
    )
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Neon.Bg0, Neon.Bg1, Neon.Bg2))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val a = t * 2f * PI.toFloat()
            val w = size.width
            val h = size.height
            fun orb(color: Color, cx: Float, cy: Float, r: Float) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = r),
                    radius = r,
                    center = Offset(cx, cy),
                )
            }
            orb(Neon.Magenta.copy(alpha = 0.20f), w * (0.15f + 0.10f * sin(a)), h * (0.14f + 0.04f * cos(a)), w * 0.9f)
            orb(Neon.Cyan.copy(alpha = 0.16f), w * (0.88f - 0.10f * cos(a)), h * (0.86f - 0.04f * sin(a)), w * 1.0f)
            orb(Neon.Purple.copy(alpha = 0.14f), w * (0.55f + 0.12f * sin(a + 2f)), h * (0.5f + 0.06f * cos(a + 1f)), w * 0.8f)

            if (floatingBlocks) {
                for (f in floaters) {
                    val p = (t * f.cycles + f.phase) % 1f
                    val cell = f.cellDp.dp.toPx()
                    val cx = w * f.xFrac + sin(p * 2f * PI.toFloat()) * 18.dp.toPx()
                    val cy = h * (1.1f - 1.25f * p)
                    val fade = minOf(1f, p * 6f, (1f - p) * 6f)
                    val box = Tetrominoes.boxSize(f.type) * cell
                    val color = Neon.pieceColor(f.type).copy(alpha = 0.16f * fade)
                    rotate(f.spin * p, pivot = Offset(cx, cy)) {
                        val o = Tetrominoes.offsets(f.type, 0)
                        for (i in 0 until 4) {
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(cx - box / 2f + o[i * 2] * cell, cy - box / 2f + o[i * 2 + 1] * cell),
                                size = Size(cell * 0.92f, cell * 0.92f),
                                cornerRadius = CornerRadius(cell * 0.2f),
                            )
                        }
                    }
                }
            }
        }
        content()
    }
}
