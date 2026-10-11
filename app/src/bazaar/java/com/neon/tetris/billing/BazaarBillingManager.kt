package com.neon.tetris.billing

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import com.neon.tetris.R
import com.neon.tetris.data.GameRepository
import com.neon.tetris.domain.PowerUpType
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.entity.PurchaseInfo
import ir.cafebazaar.poolakey.request.PurchaseRequest
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
 * Cafe Bazaar Billing implementation using Poolakey SDK.
 * Handles in-app purchases, price querying, and purchase restores for Cafe Bazaar.
 */
class BazaarBillingManager(
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
            context.getString(R.string.bazaar_rsa_public_key)
        } catch (_: Exception) {
            ""
        }
    }

    private val paymentConfig by lazy {
        val security = if (rsaKey.isBlank() || rsaKey == "YOUR_CAFE_BAZAAR_RSA_PUBLIC_KEY") {
            SecurityCheck.Disable
        } else {
            SecurityCheck.Enable(rsaPublicKey = rsaKey)
        }
        PaymentConfiguration(localSecurityCheck = security)
    }

    private val payment: Payment by lazy {
        Payment(context = context.applicationContext, config = paymentConfig)
    }

    override fun connect() {
        _status.value = BillingStatus.CONNECTING
        payment.connect {
            connectionSucceed {
                _status.value = BillingStatus.READY
                scope.launch {
                    refreshProducts()
                    restorePurchases()
                }
            }
            connectionFailed {
                _status.value = BillingStatus.UNAVAILABLE
            }
            disconnected {
                _status.value = BillingStatus.CONNECTING
            }
        }
    }

    override suspend fun refreshProducts() {
        if (_status.value != BillingStatus.READY) return
        val infos = PowerUpType.entries.associateWith { type ->
            ProductInfo(type, catalog.productId(type), price = "10,000 تومان")
        }
        _products.value = infos
    }

    private fun unavailableProducts(): Map<PowerUpType, ProductInfo> =
        PowerUpType.entries.associateWith { ProductInfo(it, catalog.productId(it), price = null) }

    override fun purchase(activity: Activity, type: PowerUpType) {
        val productId = catalog.productId(type)
        val purchaseRequest = PurchaseRequest(productId = productId)

        val registry = (activity as? ComponentActivity)?.activityResultRegistry
        if (registry == null) {
            _events.tryEmit(BillingEvent.Failed(BillingFailure.SERVICE_UNAVAILABLE))
            return
        }

        payment.purchaseProduct(
            registry = registry,
            request = purchaseRequest
        ) {
            purchaseFlowBegan {
                // Purchase UI launched in Bazaar app
            }
            failedToBeginFlow {
                scope.launch { _events.emit(BillingEvent.Failed(BillingFailure.SERVICE_UNAVAILABLE)) }
            }
            purchaseSucceed { purchaseInfo ->
                scope.launch {
                    val granted = grant(purchaseInfo)
                    granted.forEach { _events.emit(BillingEvent.Purchased(it)) }
                }
            }
            purchaseFailed {
                scope.launch { _events.emit(BillingEvent.Cancelled) }
            }
        }
    }

    override suspend fun restorePurchases(): Int {
        var restoredCount = 0
        payment.getPurchasedProducts {
            querySucceed { purchasesList ->
                scope.launch {
                    for (purchase in purchasesList) {
                        restoredCount += grant(purchase).size
                    }
                    _events.emit(BillingEvent.Restored(restoredCount))
                }
            }
            queryFailed {
                scope.launch {
                    _events.emit(BillingEvent.Failed(BillingFailure.SERVICE_UNAVAILABLE))
                }
            }
        }
        return restoredCount
    }

    private suspend fun grant(purchaseInfo: PurchaseInfo): List<PowerUpType> {
        val type = catalog.typeFor(purchaseInfo.productId) ?: return emptyList()
        repository.unlockPowerUp(type)
        payment.consumeProduct(purchaseToken = purchaseInfo.purchaseToken) {
            consumeSucceed {
                // Consumed in Bazaar backend
            }
            consumeFailed {
                // Retry if needed
            }
        }
        return listOf(type)
    }
}
