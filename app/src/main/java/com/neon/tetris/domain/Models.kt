package com.neon.tetris.domain

data class PlayerStats(
    val bestScore: Int = 0,
    val highestLevel: Int = 1,
    val gamesPlayed: Int = 0,
    val totalLines: Int = 0,
)

/** Persisted state of one power-up: bought (owned) and how many charges are left. */
data class PowerUpInventory(
    val owned: Boolean = false,
    val quantity: Int = 0,
) {
    val usable: Boolean get() = owned && quantity > 0
}

typealias Inventory = Map<PowerUpType, PowerUpInventory>

data class GameSettings(
    val haptics: Boolean = true,
    val ghostPiece: Boolean = true,
    val swipeControls: Boolean = true,
    val screenShake: Boolean = true,
)

data class GameResult(
    val score: Int,
    val bestScore: Int,
    val level: Int,
    val lines: Int,
    val isNewBest: Boolean,
)
