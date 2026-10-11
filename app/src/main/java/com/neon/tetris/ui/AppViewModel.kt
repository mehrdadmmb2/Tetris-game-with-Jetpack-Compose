package com.neon.tetris.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neon.tetris.data.GameRepository
import com.neon.tetris.domain.GameSettings
import com.neon.tetris.domain.Inventory
import com.neon.tetris.domain.PlayerStats
import com.neon.tetris.domain.PowerUpInventory
import com.neon.tetris.domain.PowerUpType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Data shown on Home and Settings: persisted stats, power-up inventory and settings. */
class AppViewModel(private val repository: GameRepository) : ViewModel() {

    val stats: StateFlow<PlayerStats> = repository.stats
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerStats())

    val inventory: StateFlow<Inventory> = repository.inventory
        .stateIn(viewModelScope, SharingStarted.Eagerly, PowerUpType.entries.associateWith { PowerUpInventory() })

    val settings: StateFlow<GameSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, GameSettings())

    /** False until DataStore has delivered its first values, so screens never flash default data. */
    val loaded: StateFlow<Boolean> = combine(repository.stats, repository.inventory, repository.settings) { _, _, _ -> true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setHaptics(enabled: Boolean) = update { it.copy(haptics = enabled) }
    fun setGhostPiece(enabled: Boolean) = update { it.copy(ghostPiece = enabled) }
    fun setSwipeControls(enabled: Boolean) = update { it.copy(swipeControls = enabled) }
    fun setScreenShake(enabled: Boolean) = update { it.copy(screenShake = enabled) }

    private fun update(transform: (GameSettings) -> GameSettings) {
        viewModelScope.launch { repository.updateSettings(transform) }
    }
}
