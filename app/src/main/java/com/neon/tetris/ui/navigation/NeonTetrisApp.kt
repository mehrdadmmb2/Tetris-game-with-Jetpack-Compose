package com.neon.tetris.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neon.tetris.domain.PowerUpType
import com.neon.tetris.ui.AppViewModel
import com.neon.tetris.ui.game.GameScreen
import com.neon.tetris.ui.game.GameViewModel
import com.neon.tetris.ui.gameover.GameOverScreen
import com.neon.tetris.ui.home.HomeScreen
import com.neon.tetris.ui.settings.SettingsScreen
import com.neon.tetris.ui.shop.ShopScreen
import com.neon.tetris.ui.shop.ShopViewModel
import com.neon.tetris.ui.theme.LocalHaptics
import com.neon.tetris.ui.theme.Neon
import com.neon.tetris.util.Haptics

sealed interface Screen {
    data object Home : Screen
    data object Game : Screen
    data object GameOver : Screen
    data object Shop : Screen
    data object Settings : Screen
}

/**
 * Root composable. A tiny hand-written back stack is all this app needs: Home -> Game -> (Shop) / GameOver,
 * and Home -> Shop / Settings. The view models are owned by the Activity, so a running game survives
 * a visit to the shop.
 */
@Composable
fun NeonTetrisApp(
    appVm: AppViewModel,
    gameVm: GameViewModel,
    shopVm: ShopViewModel,
    haptics: Haptics,
    onBuy: (PowerUpType) -> Unit,
) {
    val loaded by appVm.loaded.collectAsStateWithLifecycle()
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val stats by appVm.stats.collectAsStateWithLifecycle()
    val inventory by appVm.inventory.collectAsStateWithLifecycle()
    val inProgress by gameVm.hasGameInProgress.collectAsStateWithLifecycle()
    val result by gameVm.result.collectAsStateWithLifecycle()

    LaunchedEffect(settings.haptics) { haptics.enabled = settings.haptics }

    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val current = backStack.last()

    fun push(screen: Screen) { backStack.add(screen) }
    fun pop() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    fun replaceTop(screen: Screen) { backStack[backStack.lastIndex] = screen }
    fun goHome() {
        backStack.clear()
        backStack.add(Screen.Home)
    }

    // The neon layout is always left-to-right (controls keep their positions in RTL languages too).
    CompositionLocalProvider(
        LocalHaptics provides haptics,
        LocalLayoutDirection provides LayoutDirection.Ltr,
    ) {
        Box(Modifier.fillMaxSize().background(Neon.Bg0)) {
            AnimatedVisibility(visible = loaded, enter = fadeIn(tween(350))) {
                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f)) togetherWith fadeOut(tween(160))
                    },
                    label = "screen",
                ) { screen ->
                    when (screen) {
                        Screen.Home -> HomeScreen(
                            stats = stats,
                            inventory = inventory,
                            hasGameInProgress = inProgress,
                            onPlay = {
                                gameVm.startNewGame()
                                push(Screen.Game)
                            },
                            onContinue = { push(Screen.Game) },
                            onShop = { push(Screen.Shop) },
                            onSettings = { push(Screen.Settings) },
                        )
                        Screen.Game -> GameScreen(
                            vm = gameVm,
                            haptics = haptics,
                            onHome = { pop() },
                            onOpenShop = { push(Screen.Shop) },
                            onGameOver = { replaceTop(Screen.GameOver) },
                        )
                        Screen.GameOver -> {
                            BackHandler { goHome() }
                            GameOverScreen(
                                result = result,
                                onPlayAgain = {
                                    gameVm.startNewGame()
                                    replaceTop(Screen.Game)
                                },
                                onHome = { goHome() },
                            )
                        }
                        Screen.Shop -> {
                            BackHandler { pop() }
                            ShopScreen(vm = shopVm, onBuy = onBuy, onBack = { pop() })
                        }
                        Screen.Settings -> {
                            BackHandler { pop() }
                            SettingsScreen(
                                settings = settings,
                                onHaptics = appVm::setHaptics,
                                onGhostPiece = appVm::setGhostPiece,
                                onSwipeControls = appVm::setSwipeControls,
                                onScreenShake = appVm::setScreenShake,
                                onBack = { pop() },
                            )
                        }
                    }
                }
            }
        }
    }
}
