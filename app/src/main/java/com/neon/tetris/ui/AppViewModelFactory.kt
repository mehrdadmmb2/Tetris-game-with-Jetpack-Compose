package com.neon.tetris.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.neon.tetris.AppContainer
import com.neon.tetris.ui.game.GameViewModel
import com.neon.tetris.ui.shop.ShopViewModel

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(AppViewModel::class.java) -> AppViewModel(container.repository)
        modelClass.isAssignableFrom(GameViewModel::class.java) -> GameViewModel(container.repository)
        modelClass.isAssignableFrom(ShopViewModel::class.java) -> ShopViewModel(container.repository, container.billing)
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    } as T
}
