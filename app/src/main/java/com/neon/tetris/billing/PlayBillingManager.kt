package com.neon.tetris.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google Play Billing (library 8.x) implementation. Power-ups are one-time, non-consumable products:
 *  - a purchased product is acknowledged (required within 3 days, or Google refunds it),
 *  - ownership is written to DataStore, so it survives restarts,
 *  - [restorePurchases] and the silent restore on every connect re-apply whatever the Google account owns,
 *    which also completes purchases that were pending or interrupted.
 *
 * Note: entitlements are granted on the client. For a production game with real money at stake, verify purchase
 * tokens on a server with the Google Play Developer API before granting.
 */
class PlayBillingManager(
    context: Context,
    private val catalog: PowerUpCatalog,
    private val repository: GameRepository,
) : BillingManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val detailsByType = HashMap<PowerUpType, ProductDetails>()

    private val _status = MutableStateFlow(BillingStatus.CONNECTING)
    override val status: StateFlow<BillingStatus> = _status.asStateFlow()

    private val _products = MutableStateFlow(unavailableProducts())
    override val products: StateFlow<Map<PowerUpType, ProductInfo>> = _products.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    override val isSimulated: Boolean = false

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(PurchasesUpdatedListener { result, purchases -> onPurchasesUpdated(result, purchases) })
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    // ------------------------------------------------------------------ connection

    override fun connect() {
        if (client.isReady) {
            scope.launch {
                refreshProducts()
                restore(silent = true)
            }
            return
        }
        if (client.connectionState == BillingClient.ConnectionState.CONNECTING) return
        _status.value = BillingStatus.CONNECTING
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _status.value = BillingStatus.READY
                    scope.launch {
                        refreshProducts()
                        restore(silent = true)
                    }
                } else {
                    _status.value = BillingStatus.UNAVAILABLE
                }
            }

            override fun onBillingServiceDisconnected() {
                _status.value = BillingStatus.CONNECTING
            }
        })
    }

    // ------------------------------------------------------------------ products

    override suspend fun refreshProducts() {
        if (!client.isReady) return
        val productList = catalog.allProductIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()

        val details: List<ProductDetails> = suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { result, queryResult ->
                val list = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryResult.productDetailsList
                } else {
                    emptyList()
                }
                if (cont.isActive) cont.resume(list)
            }
        }

        detailsByType.clear()
        val infos = unavailableProducts().toMutableMap()
        for (d in details) {
            val type = catalog.typeFor(d.productId) ?: continue
            detailsByType[type] = d
            infos[type] = ProductInfo(type, d.productId, d.oneTimePurchaseOfferDetails?.formattedPrice)
        }
        _products.value = infos
    }

    private fun unavailableProducts(): Map<PowerUpType, ProductInfo> =
        PowerUpType.entries.associateWith { ProductInfo(it, catalog.productId(it), price = null) }

    // ------------------------------------------------------------------ buying

    override fun purchase(activity: Activity, type: PowerUpType) {
        val details = detailsByType[type]
        if (!client.isReady || details == null) {
            _events.tryEmit(BillingEvent.Failed(if (client.isReady) BillingFailure.PRODUCT_UNAVAILABLE else BillingFailure.SERVICE_UNAVAILABLE))
            if (client.isReady) scope.launch { refreshProducts() } else connect()
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        val offerToken = details.oneTimePurchaseOfferDetails?.offerToken
        if (!offerToken.isNullOrEmpty()) productParams.setOfferToken(offerToken)

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams.build()))
            .build()
        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _events.tryEmit(BillingEvent.Failed(failureFor(result.responseCode)))
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch {
                var pending = false
                for (purchase in purchases.orEmpty()) {
                    val granted = grant(purchase)
                    granted.forEach { _events.emit(BillingEvent.Purchased(it)) }
                    if (granted.isEmpty() && purchase.purchaseState == Purchase.PurchaseState.PENDING) pending = true
                }
                if (pending) _events.emit(BillingEvent.Pending)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> _events.tryEmit(BillingEvent.Cancelled)
            // Already bought (e.g. on another device or before a reinstall): just re-apply what the account owns.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { restore(silent = false) }
            else -> _events.tryEmit(BillingEvent.Failed(failureFor(result.responseCode)))
        }
    }

    // ------------------------------------------------------------------ restoring

    override suspend fun restorePurchases(): Int = restore(silent = false)

    private suspend fun restore(silent: Boolean): Int {
        if (!client.isReady) {
            if (!silent) _events.emit(BillingEvent.Failed(BillingFailure.SERVICE_UNAVAILABLE))
            return 0
        }
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        val purchases: List<Purchase>? = suspendCancellableCoroutine { cont ->
            client.queryPurchasesAsync(params) { result, list ->
                if (cont.isActive) {
                    cont.resume(if (result.responseCode == BillingClient.BillingResponseCode.OK) list else null)
                }
            }
        }
        if (purchases == null) {
            if (!silent) _events.emit(BillingEvent.Failed(BillingFailure.SERVICE_UNAVAILABLE))
            return 0
        }
        var found = 0
        for (purchase in purchases) found += grant(purchase).size
        if (!silent) _events.emit(BillingEvent.Restored(found))
        return found
    }

    // ------------------------------------------------------------------ entitlement

    /** Unlocks the power-ups of a PURCHASED purchase and acknowledges it. Idempotent. Returns the unlocked types. */
    private suspend fun grant(purchase: Purchase): List<PowerUpType> {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return emptyList()
        val types = purchase.products.mapNotNull { catalog.typeFor(it) }
        if (types.isEmpty()) return emptyList()
        for (type in types) repository.unlockPowerUp(type)
        if (!purchase.isAcknowledged) acknowledge(purchase.purchaseToken)
        return types
    }

    private suspend fun acknowledge(token: String) = suspendCancellableCoroutine<Unit> { cont ->
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        client.acknowledgePurchase(params) {
            // On failure the purchase simply stays unacknowledged and is retried by the next restore,
            // long before Google's 3 day deadline.
            if (cont.isActive) cont.resume(Unit)
        }
    }

    private fun failureFor(code: Int): BillingFailure = when (code) {
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.NETWORK_ERROR -> BillingFailure.SERVICE_UNAVAILABLE

        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
        BillingClient.BillingResponseCode.DEVELOPER_ERROR,
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> BillingFailure.PRODUCT_UNAVAILABLE

        else -> BillingFailure.OTHER
    }
}
