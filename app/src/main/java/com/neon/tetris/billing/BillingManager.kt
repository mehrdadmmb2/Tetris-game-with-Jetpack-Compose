package com.neon.tetris.billing

import android.app.Activity
import com.neon.tetris.domain.PowerUpType
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class BillingStatus { CONNECTING, READY, UNAVAILABLE }

enum class BillingFailure {
    /** Play Store / network not reachable, or billing not supported on this device. */
    SERVICE_UNAVAILABLE,
    /** The product is not configured in Play Console, not published yet, or not purchasable here. */
    PRODUCT_UNAVAILABLE,
    OTHER,
}

/** Store data for one power-up. [price] is the localized, formatted price, or null while unavailable. */
data class ProductInfo(
    val type: PowerUpType,
    val productId: String,
    val price: String?,
) {
    val available: Boolean get() = price != null
}

sealed interface BillingEvent {
    data class Purchased(val type: PowerUpType) : BillingEvent
    /** Payment is still being processed (e.g. cash payment); the power-up unlocks once Play confirms it. */
    data object Pending : BillingEvent
    data object Cancelled : BillingEvent
    data class Failed(val failure: BillingFailure) : BillingEvent
    /** Result of a manual restore: how many purchased power-ups were found on this Google account. */
    data class Restored(val count: Int) : BillingEvent
}

/** What the UI needs from billing. Implemented by Google Play Billing and by a simulator for debug builds. */
interface BillingManager {
    val status: StateFlow<BillingStatus>
    val products: StateFlow<Map<PowerUpType, ProductInfo>>
    val events: SharedFlow<BillingEvent>

    /** True when purchases are simulated locally (debug builds only). */
    val isSimulated: Boolean

    fun connect()
    suspend fun refreshProducts()
    fun purchase(activity: Activity, type: PowerUpType)

    /** Re-reads the account's purchases from Google Play and re-applies them. Returns how many were found. */
    suspend fun restorePurchases(): Int
}
