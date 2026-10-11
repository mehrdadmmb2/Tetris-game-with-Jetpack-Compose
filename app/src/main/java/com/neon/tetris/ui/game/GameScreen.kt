package com.neon.tetris.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neon.tetris.domain.BoardSpec
import com.neon.tetris.game.GameEvent
import com.neon.tetris.game.Phase
import com.neon.tetris.ui.common.NeonBackground
import com.neon.tetris.util.Haptics

private val SidePanelWidth = 84.dp
private val PanelGap = 10.dp

@Composable
fun GameScreen(
    vm: GameViewModel,
    haptics: Haptics,
    onHome: () -> Unit,
    onOpenShop: () -> Unit,
    onGameOver: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val inventory by vm.inventory.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()

    val fx = remember { BoardFx() }
    val feedback = remember(haptics) { GameFeedback(haptics) }
    val currentSettings by rememberUpdatedState(settings)
    val goGameOver by rememberUpdatedState(onGameOver)
    val goShop by rememberUpdatedState(onOpenShop)

    var levelUp by remember { mutableStateOf<LevelUpInfo?>(null) }
    var combo by remember { mutableStateOf<ComboInfo?>(null) }
    var toast by remember { mutableStateOf<ToastInfo?>(null) }
    val popups = remember { mutableStateListOf<ScorePopup>() }
    var nextId by remember { mutableIntStateOf(0) }

    // engine events -> particles, haptics, popups
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            fx.onEvent(e, currentSettings.screenShake)
            feedback.onEvent(e)
            when (e) {
                is GameEvent.LevelUp -> levelUp = LevelUpInfo(++nextId, e.level)
                is GameEvent.ComboChanged -> combo = ComboInfo(++nextId, e.combo)
                is GameEvent.ScoreAdded -> popups.add(ScorePopup(++nextId, e.points, e.x, e.y))
                else -> Unit
            }
        }
    }
    // view model -> navigation and messages
    LaunchedEffect(vm) {
        vm.uiEvents.collect { e ->
            when (e) {
                is GameUiEvent.NavigateToGameOver -> goGameOver()
                is GameUiEvent.OpenShop -> goShop()
                is GameUiEvent.Toast -> toast = ToastInfo(++nextId, e.kind, e.type)
            }
        }
    }

    BackHandler {
        when (state.phase) {
            Phase.PAUSED -> vm.resume()
            Phase.TARGETING -> vm.cancelTargeting()
            Phase.READY, Phase.GAME_OVER -> onHome()
            else -> vm.pause()
        }
    }

    NeonBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GameHud(
                score = state.score,
                best = stats.bestScore,
                level = state.level,
                progress = state.levelProgress,
                onPause = vm::pause,
            )

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val cell = maxOf(minOf((maxWidth - SidePanelWidth - PanelGap) / BoardSpec.COLS, maxHeight / BoardSpec.ROWS), 0.dp)
                val cellPx = with(LocalDensity.current) { cell.toPx() }

                Row(horizontalArrangement = Arrangement.spacedBy(PanelGap), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(cell * BoardSpec.COLS, cell * BoardSpec.ROWS)) {
                        BoardCanvas(
                            state = state,
                            settings = settings,
                            fx = fx,
                            cell = cell,
                            onMove = { if (it < 0) vm.moveLeft() else vm.moveRight() },
                            onRotate = vm::rotateClockwise,
                            onSoftDropStep = vm::softDropStep,
                            onHardDrop = vm::hardDrop,
                            onTapCell = vm::onBoardTap,
                        )
                        for (popup in popups) {
                            key(popup.id) { ScorePopupText(popup, cellPx) { popups.remove(it) } }
                        }
                        combo?.let { info ->
                            key(info.id) {
                                ComboPopup(info, Modifier.align(Alignment.TopCenter).padding(top = cell * 3)) { combo = null }
                            }
                        }
                        ToastHost(
                            toast = toast,
                            onDone = { id -> if (toast?.id == id) toast = null },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = cell * 2),
                        )
                    }
                    SidePanel(
                        next = state.next,
                        lines = state.lines,
                        combo = state.combo,
                        doubleScoreMs = state.doubleScoreRemainingMs,
                        modifier = Modifier.width(SidePanelWidth).height(cell * BoardSpec.ROWS),
                    )
                }
            }

            PowerUpBar(inventory = inventory, state = state, onClick = vm::onPowerUpClick)

            Box(Modifier.fillMaxWidth().height(ControlAreaHeight), contentAlignment = Alignment.Center) {
                val targeting = state.targeting
                if (state.phase == Phase.TARGETING && targeting != null) {
                    TargetingBanner(targeting, onCancel = vm::cancelTargeting)
                } else {
                    ControlPad(
                        onLeft = vm::moveLeft,
                        onRight = vm::moveRight,
                        onRotateClockwise = vm::rotateClockwise,
                        onRotateCounterClockwise = vm::rotateCounterClockwise,
                        onSoftDrop = vm::setSoftDrop,
                        onHardDrop = vm::hardDrop,
                    )
                }
            }
        }

        levelUp?.let { info -> key(info.id) { LevelUpOverlay(info) { levelUp = null } } }

        PauseOverlay(
            visible = state.phase == Phase.PAUSED,
            onResume = vm::resume,
            onRestart = vm::startNewGame,
            onHome = onHome,
        )
    }
}
