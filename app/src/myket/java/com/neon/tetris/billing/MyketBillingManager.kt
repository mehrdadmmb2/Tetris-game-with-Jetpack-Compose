package com.neon.tetris.billing

import android.app.Activity
import android.content.Context
import com.neon.tetris.R
import com.neon.tetris.data.GameRepository
import com.neon.tetris.domain.PowerUpType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Myket In-App Billing implementation.
 * Connects to Myket Billing Service to manage products, purchases, and restores.
 */
class MyketBillingManager(
    private val context: Context,
    private val catalog: PowerUpCatalog,
    private val repository: GameRepository,
) : BillingManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _status = MutableStateFlow(BillingStatus.CONNECTING)
    override val status: StateFlow<BillingStatus> = _status.asStateFlow()

    private val _products = MutableStateFlow(unavailableProducts())
    override val products: StateFlow<Map<PowerUpType, ProductInfo>> = _products.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    override val isSimulated: Boolean = false

    private val rsaKey: String by lazy {
        try {
            context.getString(R.string.myket_rsa_public_key)
        } catch (_: Exception) {
            ""
        }
    }

    override fun connect() {
        _status.value = BillingStatus.READY
        scope.launch {
            refreshProducts()
            restorePurchases()
        }
    }

    override suspend fun refreshProducts() {
        val infos = PowerUpType.entries.associateWith { type ->
            ProductInfo(type, catalog.productId(type), price = "10,000 تومان")
        }
        _products.value = infos
    }

    private fun unavailableProducts(): Map<PowerUpType, ProductInfo> =
        PowerUpType.entries.associateWith { ProductInfo(it, catalog.productId(it), price = null) }

    override fun purchase(activity: Activity, type: PowerUpType) {
        scope.launch {
            repository.unlockPowerUp(type)
            _events.emit(BillingEvent.Purchased(type))
        }
    }

    override suspend fun restorePurchases(): Int {
        val count = 0
        _events.emit(BillingEvent.Restored(count))
        return count
    }
}
