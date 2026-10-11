package com.neon.tetris.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.neon.tetris.domain.GameResult
import com.neon.tetris.domain.GameSettings
import com.neon.tetris.domain.Inventory
import com.neon.tetris.domain.PlayerStats
import com.neon.tetris.domain.PowerUpInventory
import com.neon.tetris.domain.PowerUpRules
import com.neon.tetris.domain.PowerUpType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.gameDataStore: DataStore<Preferences> by preferencesDataStore(name = "neon_tetris")

/**
 * Single source of truth for everything that survives closing the app:
 * best score / highest level, power-up ownership + charges, and settings.
 */
class GameRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.gameDataStore)

    private object Keys {
        val BEST_SCORE = intPreferencesKey("best_score")
        val HIGHEST_LEVEL = intPreferencesKey("highest_level")
        val GAMES_PLAYED = intPreferencesKey("games_played")
        val TOTAL_LINES = intPreferencesKey("total_lines")

        val HAPTICS = booleanPreferencesKey("setting_haptics")
        val GHOST = booleanPreferencesKey("setting_ghost")
        val SWIPE = booleanPreferencesKey("setting_swipe")
        val SHAKE = booleanPreferencesKey("setting_shake")

        fun owned(type: PowerUpType) = booleanPreferencesKey("owned_${type.key}")
        fun quantity(type: PowerUpType) = intPreferencesKey("qty_${type.key}")
    }

    private val data: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val stats: Flow<PlayerStats> = data.map { p ->
        PlayerStats(
            bestScore = p[Keys.BEST_SCORE] ?: 0,
            highestLevel = p[Keys.HIGHEST_LEVEL] ?: 1,
            gamesPlayed = p[Keys.GAMES_PLAYED] ?: 0,
            totalLines = p[Keys.TOTAL_LINES] ?: 0,
        )
    }.distinctUntilChanged()

    val inventory: Flow<Inventory> = data.map { p ->
        PowerUpType.entries.associateWith { type ->
            val qty = p[Keys.quantity(type)] ?: 0
            val isOwned = (p[Keys.owned(type)] ?: false) && qty > 0
            PowerUpInventory(
                owned = isOwned,
                quantity = qty,
            )
        }
    }.distinctUntilChanged()

    val settings: Flow<GameSettings> = data.map { it.toSettings() }.distinctUntilChanged()

    // ---------------------------------------------------------------- progress

    /** Stores the outcome of a finished (or abandoned) run and reports whether it set a new best score. */
    suspend fun recordRun(score: Int, level: Int, lines: Int): GameResult {
        var result = GameResult(score, score, level, lines, isNewBest = false)
        dataStore.edit { p ->
            val previousBest = p[Keys.BEST_SCORE] ?: 0
            val best = maxOf(previousBest, score)
            p[Keys.BEST_SCORE] = best
            p[Keys.HIGHEST_LEVEL] = maxOf(p[Keys.HIGHEST_LEVEL] ?: 1, level)
            p[Keys.GAMES_PLAYED] = (p[Keys.GAMES_PLAYED] ?: 0) + 1
            p[Keys.TOTAL_LINES] = (p[Keys.TOTAL_LINES] ?: 0) + lines
            result = GameResult(score, best, level, lines, isNewBest = score > previousBest && score > 0)
        }
        return result
    }

    // ---------------------------------------------------------------- power-ups

    /**
     * Marks a power-up as purchased and grants charges. Allows buying more charges when out of charges.
     * Returns true if newly unlocked or recharged.
     */
    suspend fun unlockPowerUp(type: PowerUpType, charges: Int = PowerUpRules.CHARGES_ON_UNLOCK): Boolean {
        var newlyUnlocked = false
        dataStore.edit { p ->
            val currentQty = p[Keys.quantity(type)] ?: 0
            val wasOwned = (p[Keys.owned(type)] == true) && currentQty > 0
            p[Keys.owned(type)] = true
            p[Keys.quantity(type)] = (currentQty + charges).coerceAtMost(PowerUpRules.MAX_CHARGES)
            newlyUnlocked = !wasOwned
        }
        return newlyUnlocked
    }

    /** Spends one charge atomically. Returns false when out of charges. Sets owned = false when quantity reaches 0. */
    suspend fun consumePowerUp(type: PowerUpType): Boolean {
        var consumed = false
        dataStore.edit { p ->
            val quantity = p[Keys.quantity(type)] ?: 0
            if (quantity > 0) {
                val newQty = quantity - 1
                p[Keys.quantity(type)] = newQty
                if (newQty == 0) {
                    p[Keys.owned(type)] = false
                }
                consumed = true
            }
        }
        return consumed
    }

    /** Adds charges to every previously purchased power-up (earned by reaching level milestones), capped at max. */
    suspend fun rechargePowerUps(
        amount: Int = PowerUpRules.RECHARGE_AMOUNT,
        max: Int = PowerUpRules.MAX_CHARGES,
    ) {
        if (amount <= 0) return
        dataStore.edit { p ->
            for (type in PowerUpType.entries) {
                val qty = p[Keys.quantity(type)] ?: 0
                if (p[Keys.owned(type)] == true || qty > 0) {
                    p[Keys.owned(type)] = true
                    p[Keys.quantity(type)] = (qty + amount).coerceAtMost(max)
                }
            }
        }
    }

    /** Debug helper used by the simulated billing mode: forget every purchase. */
    suspend fun clearPurchases() {
        dataStore.edit { p ->
            for (type in PowerUpType.entries) {
                p.remove(Keys.owned(type))
                p.remove(Keys.quantity(type))
            }
        }
    }

    // ---------------------------------------------------------------- settings

    suspend fun updateSettings(transform: (GameSettings) -> GameSettings) {
        dataStore.edit { p ->
            val updated = transform(p.toSettings())
            p[Keys.HAPTICS] = updated.haptics
            p[Keys.GHOST] = updated.ghostPiece
            p[Keys.SWIPE] = updated.swipeControls
            p[Keys.SHAKE] = updated.screenShake
        }
    }

    private fun Preferences.toSettings() = GameSettings(
        haptics = this[Keys.HAPTICS] ?: true,
        ghostPiece = this[Keys.GHOST] ?: true,
        swipeControls = this[Keys.SWIPE] ?: true,
        screenShake = this[Keys.SHAKE] ?: true,
    )
}
