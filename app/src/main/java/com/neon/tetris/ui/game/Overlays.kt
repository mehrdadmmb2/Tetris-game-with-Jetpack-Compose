package com.neon.tetris.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neon.tetris.R
import com.neon.tetris.domain.PowerUpType
import com.neon.tetris.domain.TargetMode
import com.neon.tetris.ui.common.GlassPanel
import com.neon.tetris.ui.common.IconKind
import com.neon.tetris.ui.common.NeonButton
import com.neon.tetris.ui.common.NeonIconButton
import com.neon.tetris.ui.common.NeonText
import com.neon.tetris.ui.common.PowerUpIcon
import com.neon.tetris.ui.common.plainClickable
import com.neon.tetris.ui.common.powerUpTitle
import com.neon.tetris.ui.theme.Neon
import com.neon.tetris.ui.theme.formatNumber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class LevelUpInfo(val id: Int, val level: Int)
class ComboInfo(val id: Int, val combo: Int)
class ScorePopup(val id: Int, val points: Int, val x: Float, val y: Float)
class ToastInfo(val id: Int, val kind: ToastKind, val type: PowerUpType?)

@Composable
fun PauseOverlay(
    visible: Boolean,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onHome: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + scaleIn(tween(250), initialScale = 0.92f),
        exit = fadeOut(tween(150)),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.68f)).plainClickable { },
            contentAlignment = Alignment.Center,
        ) {
            GlassPanel(Modifier.padding(32.dp).fillMaxWidth().widthIn(max = 380.dp), glow = true) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    NeonText(stringResource(R.string.pause_title), size = 34.sp, glow = Neon.Cyan, weight = FontWeight.Black, letterSpacing = 4.sp)
                    NeonButton(stringResource(R.string.pause_resume), onResume, Modifier.fillMaxWidth(), icon = IconKind.PLAY)
                    NeonButton(stringResource(R.string.pause_restart), onRestart, Modifier.fillMaxWidth(), color = Neon.Magenta, filled = false, icon = IconKind.ROTATE_CW)
                    NeonButton(stringResource(R.string.pause_home), onHome, Modifier.fillMaxWidth(), color = Neon.Purple, filled = false, icon = IconKind.HOME)
                }
            }
        }
    }
}

/** Big "LEVEL UP!" banner with an expanding ring; removes itself when finished. */
@Composable
fun LevelUpOverlay(info: LevelUpInfo, onDone: () -> Unit) {
    val scale = remember(info.id) { Animatable(0.3f) }
    val fade = remember(info.id) { Animatable(0f) }
    val burst = remember(info.id) { Animatable(0f) }

    LaunchedEffect(info.id) {
        launch { fade.animateTo(1f, tween(180)) }
        launch { scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 220f)) }
        launch { burst.animateTo(1f, tween(950, easing = FastOutSlowInEasing)) }
        delay(1350)
        fade.animateTo(0f, tween(350))
        onDone()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val t = burst.value
            val r = size.minDimension * 0.75f * t
            if (r > 1f) {
                drawCircle(Neon.Magenta.copy(alpha = 0.55f * (1f - t)), r, center, style = Stroke(width = 10.dp.toPx() * (1f - t) + 1f))
                drawCircle(Neon.Cyan.copy(alpha = 0.4f * (1f - t)), r * 0.7f, center, style = Stroke(width = 6.dp.toPx() * (1f - t) + 1f))
                drawCircle(
                    brush = Brush.radialGradient(listOf(Neon.Yellow.copy(alpha = 0.35f * (1f - t)), Color.Transparent), center = center, radius = r),
                    radius = r, center = center,
                )
            }
        }
        Column(
            Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; alpha = fade.value },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NeonText(
                stringResource(R.string.level_up), size = 46.sp, color = Neon.Yellow, glow = Neon.Orange,
                weight = FontWeight.Black, letterSpacing = 3.sp, align = TextAlign.Center,
            )
            NeonText(stringResource(R.string.level_up_sub, info.level), size = 26.sp, glow = Neon.Cyan, weight = FontWeight.Black, letterSpacing = 4.sp)
        }
    }
}

@Composable
fun ComboPopup(info: ComboInfo, modifier: Modifier = Modifier, onDone: () -> Unit) {
    val scale = remember(info.id) { Animatable(0.4f) }
    val rise = remember(info.id) { Animatable(0f) }
    val fade = remember(info.id) { Animatable(1f) }
    LaunchedEffect(info.id) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 300f)) }
        launch { rise.animateTo(1f, tween(1250, easing = LinearEasing)) }
        delay(850)
        fade.animateTo(0f, tween(350))
        onDone()
    }
    val color = Neon.comboColor(info.combo)
    NeonText(
        text = stringResource(R.string.combo_popup, info.combo),
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            translationY = -rise.value * 28.dp.toPx()
            alpha = fade.value
        },
        size = (24 + info.combo * 3).coerceAtMost(46).sp,
        color = color,
        glow = color,
        weight = FontWeight.Black,
        letterSpacing = 2.sp,
    )
}

/** "+points" that floats up from where the cells were cleared. [x]/[y] of the popup are in cell units. */
@Composable
fun ScorePopupText(popup: ScorePopup, cellPx: Float, onDone: (ScorePopup) -> Unit) {
    val progress = remember(popup.id) { Animatable(0f) }
    LaunchedEffect(popup.id) {
        progress.animateTo(1f, tween(1000, easing = LinearEasing))
        onDone(popup)
    }
    val halfWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 60.dp.roundToPx() }
    Box(
        Modifier
            .offset { IntOffset((popup.x * cellPx).roundToInt() - halfWidthPx, (popup.y * cellPx).roundToInt()) }
            .widthIn(min = 120.dp, max = 120.dp),
        contentAlignment = Alignment.Center,
    ) {
        NeonText(
            "+" + formatNumber(popup.points),
            Modifier.graphicsLayer {
                val t = progress.value
                translationY = -t * 70.dp.toPx()
                alpha = (1f - t * t).coerceIn(0f, 1f)
                val s = 1f + 0.35f * (1f - t)
                scaleX = s
                scaleY = s
            },
            size = 22.sp, color = Neon.Yellow, glow = Neon.Orange, weight = FontWeight.Black, letterSpacing = 1.sp,
        )
    }
}

@Composable
fun ToastHost(toast: ToastInfo?, onDone: (Int) -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<ToastInfo?>(null) }
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            shown = toast
            delay(1900)
            onDone(toast.id)
        }
    }
    AnimatedVisibility(
        visible = toast != null,
        modifier = modifier,
        enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 2 },
        exit = fadeOut(tween(220)),
    ) {
        val info = shown
        if (info != null) {
            val text = when (info.kind) {
                ToastKind.OUT_OF_CHARGES -> stringResource(R.string.toast_out_of_charges, info.type?.let { powerUpTitle(it) } ?: "")
                ToastKind.NOTHING_TO_CLEAR -> stringResource(R.string.toast_nothing_to_clear)
                ToastKind.ALREADY_ACTIVE -> stringResource(R.string.toast_already_active)
                ToastKind.RECHARGED -> stringResource(R.string.toast_recharged)
            }
            GlassPanel(accent = Neon.Cyan, corner = 16.dp, glow = true) {
                NeonText(
                    text, Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    size = 13.sp, align = TextAlign.Center, maxLines = 2, weight = FontWeight.SemiBold, letterSpacing = 0.3.sp,
                )
            }
        }
    }
}

/** Replaces the control pad while a Lightning / Hammer / Color Bomb waits for its target. */
@Composable
fun TargetingBanner(type: PowerUpType, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val color = Neon.powerUpColor(type)
    val hint = when (type.targetMode) {
        TargetMode.COLUMN -> stringResource(R.string.target_lightning)
        TargetMode.AREA -> stringResource(R.string.target_hammer)
        TargetMode.BLOCK_COLOR -> stringResource(R.string.target_color_bomb)
        TargetMode.NONE -> ""
    }
    GlassPanel(modifier.fillMaxWidth(), accent = color, glow = true) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PowerUpIcon(type, color, 34.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NeonText(powerUpTitle(type), size = 16.sp, color = color, weight = FontWeight.Black, glow = color)
                NeonText(hint, size = 13.sp, color = Color.White, maxLines = 2, weight = FontWeight.Medium, letterSpacing = 0.2.sp)
            }
            NeonIconButton(IconKind.CLOSE, onCancel, color = Neon.Red, size = 44.dp)
        }
    }
}
