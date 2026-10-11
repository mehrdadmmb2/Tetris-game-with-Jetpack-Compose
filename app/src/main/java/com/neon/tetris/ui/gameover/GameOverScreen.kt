package com.neon.tetris.ui.gameover

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neon.tetris.R
import com.neon.tetris.domain.GameResult
import com.neon.tetris.ui.common.AnimatedCounter
import com.neon.tetris.ui.common.GlassPanel
import com.neon.tetris.ui.common.IconKind
import com.neon.tetris.ui.common.NeonBackground
import com.neon.tetris.ui.common.NeonButton
import com.neon.tetris.ui.common.NeonText
import com.neon.tetris.ui.common.StaggeredEntrance
import com.neon.tetris.ui.theme.Neon

@Composable
fun GameOverScreen(
    result: GameResult?,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
) {
    val r = result ?: GameResult(score = 0, bestScore = 0, level = 1, lines = 0, isNewBest = false)

    val transition = rememberInfiniteTransition(label = "gameOver")
    // the title flickers like a failing neon tube
    val flicker by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2600
                1f at 0
                1f at 1500
                0.35f at 1560
                1f at 1620
                0.6f at 1700
                1f at 1760
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "flicker",
    )
    val badgePulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "badgePulse",
    )

    NeonBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            StaggeredEntrance(0) {
                NeonText(
                    stringResource(R.string.gameover_title),
                    Modifier.graphicsLayer { alpha = flicker },
                    size = 44.sp, color = Neon.Red, glow = Neon.Red, weight = FontWeight.Black, letterSpacing = 4.sp,
                    align = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(10.dp))

            if (r.isNewBest) {
                StaggeredEntrance(1) {
                    val shape = RoundedCornerShape(50)
                    NeonText(
                        stringResource(R.string.gameover_new_best),
                        Modifier
                            .graphicsLayer { scaleX = badgePulse; scaleY = badgePulse }
                            .clip(shape)
                            .background(Neon.Yellow.copy(alpha = 0.16f))
                            .border(1.5.dp, Neon.Yellow, shape)
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        size = 15.sp, color = Neon.Yellow, glow = Neon.Yellow, weight = FontWeight.Black, letterSpacing = 3.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(8.dp))
            StaggeredEntrance(2) {
                GlassPanel(Modifier.widthIn(max = 420.dp).fillMaxWidth(), accent = Neon.Cyan, glow = true) {
                    Column(
                        Modifier.fillMaxWidth().padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        NeonText(stringResource(R.string.gameover_score), size = 12.sp, color = Neon.TextDim, letterSpacing = 3.sp)
                        AnimatedCounter(r.score, size = 48.sp, color = Color.White, glow = Neon.Cyan, pop = false, countUpOnEnter = true, durationMs = 1100)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Stat(stringResource(R.string.gameover_best), r.bestScore, Neon.Yellow)
                            Stat(stringResource(R.string.gameover_level), r.level, Neon.Magenta)
                            Stat(stringResource(R.string.gameover_lines), r.lines, Neon.Green)
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                StaggeredEntrance(3, Modifier.fillMaxWidth()) {
                    NeonButton(stringResource(R.string.gameover_play_again), onPlayAgain, Modifier.fillMaxWidth(), height = 62.dp, textSize = 20.sp, icon = IconKind.ROTATE_CW)
                }
                StaggeredEntrance(4, Modifier.fillMaxWidth()) {
                    NeonButton(stringResource(R.string.gameover_home), onHome, Modifier.fillMaxWidth(), color = Neon.Purple, filled = false, icon = IconKind.HOME)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NeonText(label, size = 10.sp, color = Neon.TextDim, letterSpacing = 2.sp)
        AnimatedCounter(value, size = 20.sp, color = color, glow = color, pop = false, countUpOnEnter = true, durationMs = 900)
    }
}
