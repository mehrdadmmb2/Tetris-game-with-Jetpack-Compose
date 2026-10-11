package com.neon.tetris.ui.game

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neon.tetris.R
import com.neon.tetris.domain.Inventory
import com.neon.tetris.domain.LevelSystem
import com.neon.tetris.domain.PowerUpInventory
import com.neon.tetris.domain.PowerUpRules
import com.neon.tetris.domain.PowerUpType
import com.neon.tetris.game.GameState
import com.neon.tetris.game.PieceType
import com.neon.tetris.game.Phase
import com.neon.tetris.game.Tetrominoes
import com.neon.tetris.ui.common.AnimatedCounter
import com.neon.tetris.ui.common.GlassPanel
import com.neon.tetris.ui.common.IconKind
import com.neon.tetris.ui.common.NeonIcon
import com.neon.tetris.ui.common.NeonIconButton
import com.neon.tetris.ui.common.NeonProgressBar
import com.neon.tetris.ui.common.NeonText
import com.neon.tetris.ui.common.PowerUpIcon
import com.neon.tetris.ui.common.glowBox
import com.neon.tetris.ui.theme.Neon
import com.neon.tetris.ui.theme.formatNumber
import kotlin.math.max

@Composable
private fun Label(text: String, modifier: Modifier = Modifier, color: Color = Neon.TextDim) {
    NeonText(text, modifier, size = 10.sp, color = color, weight = FontWeight.Bold, letterSpacing = 2.sp)
}

/** Pause button, score, best score, level badge and level progress. */
@Composable
fun GameHud(
    score: Int,
    best: Int,
    level: Int,
    progress: Float,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeonIconButton(IconKind.PAUSE, onPause, color = Neon.Cyan, size = 52.dp)
            GlassPanel(Modifier.weight(1f), accent = Neon.Cyan) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Label(stringResource(R.string.hud_score))
                        AnimatedCounter(score, size = 26.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Label(stringResource(R.string.hud_best))
                        NeonText(formatNumber(max(best, score)), size = 18.sp, color = Neon.Yellow, glow = Neon.Yellow.copy(alpha = 0.5f))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LevelBadge(level)
            NeonProgressBar(progress, Modifier.weight(1f), Neon.Magenta, 12.dp)
            val done = level >= LevelSystem.MAX_LEVEL && progress >= 1f
            NeonText(
                if (done) stringResource(R.string.hud_max) else "${(progress * 100).toInt()}%",
                Modifier.size(width = 40.dp, height = 16.dp),
                size = 12.sp, color = Neon.TextDim, align = TextAlign.End,
            )
        }
    }
}

@Composable
private fun LevelBadge(level: Int) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Neon.Magenta.copy(alpha = 0.55f), Neon.Purple.copy(alpha = 0.35f))))
            .border(1.5.dp, Neon.Magenta, shape)
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NeonText(stringResource(R.string.hud_level), size = 8.sp, color = Color.White.copy(alpha = 0.8f), letterSpacing = 2.sp)
        NeonText(level.toString(), size = 18.sp, weight = FontWeight.Black, glow = Neon.Magenta, letterSpacing = 0.sp)
    }
}

/** NEXT previews, lines counter, combo and double-score chips. */
@Composable
fun SidePanel(
    next: List<PieceType>,
    lines: Int,
    combo: Int,
    doubleScoreMs: Long,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier, accent = Neon.Purple) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Label(stringResource(R.string.hud_next))
            next.forEachIndexed { i, type ->
                PiecePreview(type, Modifier.fillMaxWidth().height(42.dp), alpha = if (i == 0) 1f else 0.55f)
            }
            Spacer(Modifier.weight(1f))
            if (doubleScoreMs > 0L) {
                Chip(stringResource(R.string.hud_double_score_chip, ((doubleScoreMs + 999) / 1000).toInt()), Neon.Green)
            }
            if (combo >= 2) {
                Chip(stringResource(R.string.hud_combo_chip, combo), Neon.comboColor(combo))
            }
            Label(stringResource(R.string.hud_lines))
            NeonText(lines.toString(), size = 20.sp, weight = FontWeight.Black, letterSpacing = 0.sp)
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color, shape)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        NeonText(text, size = 11.sp, color = color, weight = FontWeight.Black, letterSpacing = 0.5.sp, align = TextAlign.Center)
    }
}

@Composable
fun PiecePreview(type: PieceType, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val cellPx = with(LocalDensity.current) { 11.dp.toPx() }
    val painter = remember(cellPx) { BlockPainter(cellPx) }
    Canvas(modifier) {
        val o = Tetrominoes.offsets(type, 0)
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        for (i in 0 until 4) {
            minX = minOf(minX, o[i * 2]); maxX = maxOf(maxX, o[i * 2])
            minY = minOf(minY, o[i * 2 + 1]); maxY = maxOf(maxY, o[i * 2 + 1])
        }
        val left = (size.width - (maxX - minX + 1) * cellPx) / 2f
        val top = (size.height - (maxY - minY + 1) * cellPx) / 2f
        for (i in 0 until 4) {
            drawBlock(painter, type.id, left + (o[i * 2] - minX) * cellPx, top + (o[i * 2 + 1] - minY) * cellPx, alpha = alpha, glow = 0.5f)
        }
    }
}

// ------------------------------------------------------------------------------------------------ power-ups

@Composable
fun PowerUpBar(
    inventory: Inventory,
    state: GameState,
    onClick: (PowerUpType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        for (type in PowerUpType.entries) {
            val doubleActive = type == PowerUpType.DOUBLE_SCORE && state.doubleScoreRemainingMs > 0L
            PowerUpButton(
                type = type,
                inventory = inventory[type] ?: PowerUpInventory(),
                armed = state.targeting == type || doubleActive,
                countdown = if (doubleActive) state.doubleScoreRemainingMs / PowerUpRules.DOUBLE_SCORE_DURATION_MS.toFloat() else null,
                onClick = { if (state.phase == Phase.FALLING || state.phase == Phase.TARGETING) onClick(type) },
            )
        }
    }
}

@Composable
private fun PowerUpButton(
    type: PowerUpType,
    inventory: PowerUpInventory,
    armed: Boolean,
    countdown: Float?,
    onClick: () -> Unit,
) {
    val color = Neon.powerUpColor(type)
    val usable = inventory.usable
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "powerUpScale")
    val pulse by rememberInfiniteTransition(label = "powerUp").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "powerUpPulse",
    )
    val shape = RoundedCornerShape(16.dp)

    Box(
        Modifier
            .size(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind { if (usable) glowBox(color, if (armed) 1.7f * pulse else 0.5f, 16.dp.toPx(), layers = 3) }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(if (usable) color.copy(alpha = 0.28f) else Neon.PanelHigh, Neon.Panel)))
            .border(if (armed) 2.5.dp else 1.5.dp, if (usable) color else Neon.Stroke, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { alpha = if (usable) 1f else 0.38f }) {
            PowerUpIcon(type, if (usable) color else Neon.TextDim, 30.dp)
        }
        if (countdown != null) {
            Canvas(Modifier.fillMaxSize()) {
                val inset = 3.dp.toPx()
                drawArc(
                    color = color, startAngle = -90f, sweepAngle = 360f * countdown.coerceIn(0f, 1f), useCenter = false,
                    topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        if (!inventory.owned) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(3.dp).size(19.dp).background(Neon.Bg0, CircleShape).border(1.dp, color, CircleShape),
                contentAlignment = Alignment.Center,
            ) { NeonIcon(IconKind.LOCK, color, 11.dp, glow = false) }
        } else {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .background(if (inventory.quantity > 0) color else Neon.Red, RoundedCornerShape(8.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                NeonText(inventory.quantity.toString(), size = 11.sp, color = Neon.Bg0, weight = FontWeight.Black, letterSpacing = 0.sp)
            }
        }
    }
}
