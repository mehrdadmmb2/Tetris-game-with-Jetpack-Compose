package com.neon.tetris.billing

import android.app.Activity
import com.neon.tetris.data.GameRepository
import com.neon.tetris.domain.PowerUpType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Used only when BuildConfig.SIMULATE_PURCHASES is true (debug builds by default) so the whole shop / owned /
 * persistence flow can be tried without a Play Console setup. It goes through the same repository calls as the
 * real implementation. Release builds always use [PlayBillingManager].
 */
class SimulatedBillingManager(
    private val catalog: PowerUpCatalog,
    private val repository: GameRepository,
) : BillingManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val isSimulated: Boolean = true

    private val _status = MutableStateFlow(BillingStatus.READY)
    override val status: StateFlow<BillingStatus> = _status.asStateFlow()

    private val _products = MutableStateFlow(
        PowerUpType.entries.associateWith { ProductInfo(it, catalog.productId(it), PRICES.getValue(it)) },
    )
    override val products: StateFlow<Map<PowerUpType, ProductInfo>> = _products.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    override fun connect() = Unit

    override suspend fun refreshProducts() = Unit

    override fun purchase(activity: Activity, type: PowerUpType) {
        scope.launch {
            delay(700) // pretend the Play sheet is open
            repository.unlockPowerUp(type)
            _events.emit(BillingEvent.Purchased(type))
        }
    }

    override suspend fun restorePurchases(): Int {
        delay(400)
        val owned = repository.inventory.first().count { it.value.owned }
        _events.emit(BillingEvent.Restored(owned))
        return owned
    }

    private companion object {
        val PRICES = mapOf(
            PowerUpType.BOMB to "$0.99",
            PowerUpType.LIGHTNING to "$0.99",
            PowerUpType.HAMMER to "$0.99",
            PowerUpType.COLOR_BOMB to "$1.49",
            PowerUpType.DOUBLE_SCORE to "$0.99",
        )
    }
}
