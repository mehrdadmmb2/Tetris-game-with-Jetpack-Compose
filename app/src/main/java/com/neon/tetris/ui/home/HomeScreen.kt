package com.neon.tetris.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neon.tetris.R
import com.neon.tetris.domain.Inventory
import com.neon.tetris.domain.LevelSystem
import com.neon.tetris.domain.PlayerStats
import com.neon.tetris.domain.PowerUpInventory
import com.neon.tetris.domain.PowerUpType
import com.neon.tetris.ui.common.AnimatedCounter
import com.neon.tetris.ui.common.GlassPanel
import com.neon.tetris.ui.common.IconKind
import com.neon.tetris.ui.common.NeonBackground
import com.neon.tetris.ui.common.NeonButton
import com.neon.tetris.ui.common.NeonProgressBar
import com.neon.tetris.ui.common.NeonText
import com.neon.tetris.ui.common.PowerUpIcon
import com.neon.tetris.ui.common.StaggeredEntrance
import com.neon.tetris.ui.common.plainClickable
import com.neon.tetris.ui.theme.Neon
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun HomeScreen(
    stats: PlayerStats,
    inventory: Inventory,
    hasGameInProgress: Boolean,
    onPlay: () -> Unit,
    onContinue: () -> Unit,
    onShop: () -> Unit,
    onSettings: () -> Unit,
) {
    NeonBackground(floatingBlocks = true) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            StaggeredEntrance(0) { Title() }
            Spacer(Modifier.height(28.dp))

            StaggeredEntrance(1) {
                Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassPanel(Modifier.weight(1f), accent = Neon.Yellow) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            NeonText(stringResource(R.string.home_best_score), size = 11.sp, color = Neon.TextDim, letterSpacing = 2.sp)
                            AnimatedCounter(stats.bestScore, size = 28.sp, color = Neon.Yellow, glow = Neon.Yellow, pop = false, countUpOnEnter = true, durationMs = 900)
                        }
                    }
                    GlassPanel(Modifier.weight(1f), accent = Neon.Magenta) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            NeonText(stringResource(R.string.home_highest_level), size = 11.sp, color = Neon.TextDim, letterSpacing = 2.sp)
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AnimatedCounter(stats.highestLevel, size = 28.sp, color = Neon.Magenta, glow = Neon.Magenta, pop = false, countUpOnEnter = true, durationMs = 700)
                                NeonText("/ ${LevelSystem.MAX_LEVEL}", Modifier.padding(bottom = 3.dp), size = 12.sp, color = Neon.TextDim, letterSpacing = 0.sp)
                            }
                            NeonProgressBar(stats.highestLevel / LevelSystem.MAX_LEVEL.toFloat(), Modifier.fillMaxWidth(), Neon.Magenta, 6.dp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            StaggeredEntrance(2) { PowerUpStrip(inventory, onShop) }

            Spacer(Modifier.height(32.dp))
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StaggeredEntrance(3, Modifier.fillMaxWidth()) {
                    if (hasGameInProgress) {
                        NeonButton(stringResource(R.string.home_continue), onContinue, Modifier.fillMaxWidth(), height = 64.dp, textSize = 22.sp, icon = IconKind.PLAY)
                    } else {
                        NeonButton(stringResource(R.string.home_play), onPlay, Modifier.fillMaxWidth(), height = 64.dp, textSize = 22.sp, icon = IconKind.PLAY)
                    }
                }
                if (hasGameInProgress) {
                    StaggeredEntrance(4, Modifier.fillMaxWidth()) {
                        NeonButton(stringResource(R.string.home_new_game), onPlay, Modifier.fillMaxWidth(), color = Neon.Cyan, filled = false, icon = IconKind.ROTATE_CW)
                    }
                }
                StaggeredEntrance(5, Modifier.fillMaxWidth()) {
                    NeonButton(stringResource(R.string.home_shop), onShop, Modifier.fillMaxWidth(), color = Neon.Magenta, filled = false, icon = IconKind.BOLT)
                }
                StaggeredEntrance(6, Modifier.fillMaxWidth()) {
                    NeonButton(stringResource(R.string.home_settings), onSettings, Modifier.fillMaxWidth(), color = Neon.Purple, filled = false, icon = IconKind.GEAR, height = 52.dp, textSize = 15.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Pulsing two-line logo with a wave of coloured blocks underneath. */
@Composable
private fun Title() {
    val transition = rememberInfiniteTransition(label = "title")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "titlePulse",
    )
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "titleWave",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        NeonText(
            "NEON", Modifier.graphicsLayer { alpha = pulse }, size = 60.sp, color = Neon.Cyan, glow = Neon.Cyan,
            weight = FontWeight.Black, letterSpacing = 10.sp,
        )
        NeonText(
            "TETRIS", Modifier.graphicsLayer { alpha = pulse }, size = 44.sp, color = Neon.Magenta, glow = Neon.Magenta,
            weight = FontWeight.Black, letterSpacing = 10.sp,
        )
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.size(width = 200.dp, height = 26.dp)) {
            val cell = 20.dp.toPx()
            val gap = (size.width - cell * 7) / 6f
            for (i in 0 until 7) {
                val color = Neon.pieceColor(i + 1)
                val y = (size.height - cell) / 2f + sin(wave + i * 0.8f) * 3.dp.toPx()
                val x = i * (cell + gap)
                drawRoundRect(color.copy(alpha = 0.25f), Offset(x - 3.dp.toPx(), y - 3.dp.toPx()), Size(cell + 6.dp.toPx(), cell + 6.dp.toPx()), CornerRadius(7.dp.toPx()))
                drawRoundRect(color, Offset(x, y), Size(cell, cell), CornerRadius(5.dp.toPx()))
                drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(x + 2.dp.toPx(), y + 2.dp.toPx()), Size(cell - 4.dp.toPx(), cell * 0.35f), CornerRadius(3.dp.toPx()))
            }
        }
    }
}

/** The five power-ups at a glance: bright + charge count when owned, dim when still locked. */
@Composable
private fun PowerUpStrip(inventory: Inventory, onClick: () -> Unit) {
    Row(
        Modifier.widthIn(max = 420.dp).fillMaxWidth().plainClickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (type in PowerUpType.entries) {
            val inv = inventory[type] ?: PowerUpInventory()
            val color = Neon.powerUpColor(type)
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier
                    .size(52.dp)
                    .clip(shape)
                    .border(1.5.dp, if (inv.owned) color else Neon.Stroke, shape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.graphicsLayer { alpha = if (inv.owned) 1f else 0.35f }) {
                    PowerUpIcon(type, if (inv.owned) color else Neon.TextDim, 28.dp)
                }
                if (inv.owned) {
                    NeonText(
                        inv.quantity.toString(), Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp),
                        size = 11.sp, color = color, weight = FontWeight.Black, letterSpacing = 0.sp,
                    )
                }
            }
        }
    }
}
