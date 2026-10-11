package com.neon.tetris.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.neon.tetris.ui.common.IconKind
import com.neon.tetris.ui.common.NeonIcon
import com.neon.tetris.ui.common.glowBox
import com.neon.tetris.ui.theme.Neon
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val INITIAL_REPEAT_DELAY_MS = 170L
private const val REPEAT_INTERVAL_MS = 55L

/** Height of the control area; the targeting banner replaces the pad at the same size. */
val ControlAreaHeight = 124.dp

/**
 * A touch button that reports press and release. With [repeatMs] > 0 it auto-repeats while held
 * (delayed auto shift), which is what makes sliding a piece across the board feel right.
 */
@Composable
fun GameButton(
    icon: IconKind,
    color: Color,
    modifier: Modifier = Modifier,
    onPress: () -> Unit,
    onRelease: () -> Unit = {},
    repeatMs: Long = 0L,
) {
    var pressed by remember { mutableStateOf(false) }
    val pressCb by rememberUpdatedState(onPress)
    val releaseCb by rememberUpdatedState(onRelease)
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "gameButton")
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind { if (pressed) glowBox(color, 1.2f, 18.dp.toPx(), layers = 3) }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (pressed) listOf(color.copy(alpha = 0.45f), Neon.Panel) else listOf(Neon.PanelHigh, Neon.Panel),
                ),
            )
            .border(1.5.dp, color.copy(alpha = if (pressed) 1f else 0.6f), shape)
            .pointerInput(repeatMs) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        pressCb()
                        if (repeatMs > 0L) {
                            coroutineScope {
                                val repeater = launch {
                                    delay(INITIAL_REPEAT_DELAY_MS)
                                    while (true) {
                                        pressCb()
                                        delay(repeatMs)
                                    }
                                }
                                tryAwaitRelease()
                                repeater.cancel()
                            }
                        } else {
                            tryAwaitRelease()
                        }
                        pressed = false
                        releaseCb()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        NeonIcon(icon, color, 30.dp, glow = pressed)
    }
}

@Composable
fun ControlPad(
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onRotateClockwise: () -> Unit,
    onRotateCounterClockwise: () -> Unit,
    onSoftDrop: (Boolean) -> Unit,
    onHardDrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameButton(IconKind.LEFT, Neon.Cyan, Modifier.weight(1f).height(64.dp), onPress = onLeft, repeatMs = REPEAT_INTERVAL_MS)
                GameButton(IconKind.RIGHT, Neon.Cyan, Modifier.weight(1f).height(64.dp), onPress = onRight, repeatMs = REPEAT_INTERVAL_MS)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameButton(IconKind.ROTATE_CCW, Neon.Magenta, Modifier.weight(1f).height(64.dp), onPress = onRotateCounterClockwise)
                GameButton(IconKind.ROTATE_CW, Neon.Magenta, Modifier.weight(1f).height(64.dp), onPress = onRotateClockwise)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GameButton(
                IconKind.DOWN, Neon.Blue, Modifier.weight(1f).height(52.dp),
                onPress = { onSoftDrop(true) }, onRelease = { onSoftDrop(false) },
            )
            GameButton(IconKind.DROP, Neon.Orange, Modifier.weight(1f).height(52.dp), onPress = onHardDrop)
        }
    }
}
