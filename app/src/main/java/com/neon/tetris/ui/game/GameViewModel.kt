package com.neon.tetris.ui.game

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neon.tetris.data.GameRepository
import com.neon.tetris.domain.GameResult
import com.neon.tetris.domain.GameSettings
import com.neon.tetris.domain.Inventory
import com.neon.tetris.domain.PlayerStats
import com.neon.tetris.domain.PowerUpInventory
import com.neon.tetris.domain.PowerUpRules
import com.neon.tetris.domain.PowerUpType
import com.neon.tetris.game.GameEvent
import com.neon.tetris.game.GameState
import com.neon.tetris.game.Phase
import com.neon.tetris.game.RejectReason
import com.neon.tetris.game.TetrisEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ToastKind { OUT_OF_CHARGES, NOTHING_TO_CLEAR, ALREADY_ACTIVE, RECHARGED }

/** Things the screen has to react to that are not part of the engine state. */
sealed interface GameUiEvent {
    data object NavigateToGameOver : GameUiEvent
    data object OpenShop : GameUiEvent
    data class Toast(val kind: ToastKind, val type: PowerUpType?) : GameUiEvent
}

/**
 * Owns the [TetrisEngine], runs its game loop, applies persistence rules (best score, power-up charges) and
 * exposes everything the game screens need. Scoped to the Activity so a running game survives navigating
 * to the shop or the home screen.
 */
class GameViewModel(private val repository: GameRepository) : ViewModel() {

    private val engine = TetrisEngine()

    /** Engine snapshot (board, piece, score, level, animations ...). */
    val state: StateFlow<GameState> = engine.state
    /** One-shot engine events for effects (particles, haptics, popups). */
    val events: SharedFlow<GameEvent> = engine.events

    val inventory: StateFlow<Inventory> = repository.inventory
        .stateIn(viewModelScope, SharingStarted.Eagerly, PowerUpType.entries.associateWith { PowerUpInventory() })
    val settings: StateFlow<GameSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, GameSettings())
    val stats: StateFlow<PlayerStats> = repository.stats
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerStats())

    /** Whether the Home screen should offer "Continue". */
    val hasGameInProgress: StateFlow<Boolean> = engine.state
        .map { it.phase != Phase.READY && it.phase != Phase.GAME_OVER }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _result = MutableStateFlow<GameResult?>(null)
    /** Set once a game has ended; shown by the Game Over screen. */
    val result: StateFlow<GameResult?> = _result.asStateFlow()

    private val _uiEvents = MutableSharedFlow<GameUiEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val uiEvents: SharedFlow<GameUiEvent> = _uiEvents.asSharedFlow()

    private var loopJob: Job? = null
    private var runRecorded = true
    private var lastLevel = 1

    init {
        viewModelScope.launch { engine.events.collect { onEngineEvent(it) } }
    }

    // ------------------------------------------------------------------ session control

    fun startNewGame() {
        recordAbandonedRun()
        _result.value = null
        runRecorded = false
        lastLevel = 1
        engine.startNewGame()
        startLoop()
    }

    fun pause() {
        engine.pause()
        stopLoop()
    }

    fun resume() {
        engine.resume()
        if (state.value.phase != Phase.PAUSED) startLoop()
    }

    /** Called when the app goes to the background. */
    fun autoPause() {
        val phase = state.value.phase
        if (phase != Phase.READY && phase != Phase.GAME_OVER && phase != Phase.PAUSED) pause()
    }

    // ------------------------------------------------------------------ input

    fun moveLeft() { engine.moveLeft() }
    fun moveRight() { engine.moveRight() }
    fun rotateClockwise() { engine.rotate(true) }
    fun rotateCounterClockwise() { engine.rotate(false) }
    fun softDropStep() { engine.softDropStep() }
    fun setSoftDrop(active: Boolean) = engine.setSoftDrop(active)
    fun hardDrop() { engine.hardDrop() }

    fun onPowerUpClick(type: PowerUpType) {
        val s = state.value
        if (s.phase != Phase.FALLING && s.phase != Phase.TARGETING) return
        val inv = inventory.value[type] ?: PowerUpInventory()
        when {
            !inv.owned -> {
                pause()
                _uiEvents.tryEmit(GameUiEvent.OpenShop)
            }
            inv.quantity <= 0 -> _uiEvents.tryEmit(GameUiEvent.Toast(ToastKind.OUT_OF_CHARGES, type))
            s.phase == Phase.TARGETING && s.targeting == type -> engine.cancelTargeting()
            else -> engine.usePowerUp(type)
        }
    }

    fun onBoardTap(x: Int, y: Int) = engine.targetCell(x, y)

    fun cancelTargeting() = engine.cancelTargeting()

    // ------------------------------------------------------------------ engine events

    private fun onEngineEvent(e: GameEvent) {
        when (e) {
            is GameEvent.PowerUpApplied -> viewModelScope.launch { repository.consumePowerUp(e.type) }
            is GameEvent.PowerUpRejected -> {
                val kind = if (e.reason == RejectReason.ALREADY_ACTIVE) ToastKind.ALREADY_ACTIVE else ToastKind.NOTHING_TO_CLEAR
                _uiEvents.tryEmit(GameUiEvent.Toast(kind, e.type))
            }
            is GameEvent.LevelUp -> onLevelUp(e.level)
            is GameEvent.GameOver -> onGameOver()
            else -> Unit
        }
    }

    /** Owned power-ups recharge every [PowerUpRules.RECHARGE_EVERY_LEVELS] levels reached during play. */
    private fun onLevelUp(level: Int) {
        val every = PowerUpRules.RECHARGE_EVERY_LEVELS
        val milestones = level / every - lastLevel / every
        lastLevel = level
        if (milestones > 0 && inventory.value.values.any { it.owned }) {
            viewModelScope.launch { repository.rechargePowerUps(milestones * PowerUpRules.RECHARGE_AMOUNT) }
            _uiEvents.tryEmit(GameUiEvent.Toast(ToastKind.RECHARGED, null))
        }
    }

    private fun onGameOver() {
        stopLoop()
        val s = state.value
        val alreadyRecorded = runRecorded
        runRecorded = true
        viewModelScope.launch {
            val recorded = if (!alreadyRecorded && s.score > 0) repository.recordRun(s.score, s.level, s.lines) else null
            _result.value = recorded
                ?: GameResult(s.score, maxOf(stats.value.bestScore, s.score), s.level, s.lines, isNewBest = false)
            delay(GAME_OVER_DELAY_MS) // let the board play its game-over animation first
            _uiEvents.emit(GameUiEvent.NavigateToGameOver)
        }
    }

    /** A game that is replaced by a new one still counts towards best score / highest level. */
    private fun recordAbandonedRun() {
        if (runRecorded) return
        runRecorded = true
        val s = state.value
        if (s.score > 0) viewModelScope.launch { repository.recordRun(s.score, s.level, s.lines) }
    }

    // ------------------------------------------------------------------ game loop

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(FRAME_MS)
                val now = SystemClock.elapsedRealtime()
                engine.update(now - last)
                last = now
            }
        }
    }

    private fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
    }

    override fun onCleared() {
        stopLoop()
        super.onCleared()
    }

    private companion object {
        const val FRAME_MS = 8L
        const val GAME_OVER_DELAY_MS = 1500L
    }
}
