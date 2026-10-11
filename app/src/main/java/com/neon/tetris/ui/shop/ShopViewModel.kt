package com.neon.tetris.ui.shop

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neon.tetris.billing.BillingEvent
import com.neon.tetris.billing.BillingManager
import com.neon.tetris.billing.BillingStatus
import com.neon.tetris.billing.ProductInfo
import com.neon.tetris.data.GameRepository
import com.neon.tetris.domain.Inventory
import com.neon.tetris.domain.PowerUpInventory
import com.neon.tetris.domain.PowerUpType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShopViewModel(
    private val repository: GameRepository,
    private val billing: BillingManager,
) : ViewModel() {

    val inventory: StateFlow<Inventory> = repository.inventory
        .stateIn(viewModelScope, SharingStarted.Eagerly, PowerUpType.entries.associateWith { PowerUpInventory() })

    val products: StateFlow<Map<PowerUpType, ProductInfo>> = billing.products
    val status: StateFlow<BillingStatus> = billing.status
    val events: SharedFlow<BillingEvent> = billing.events
    val isSimulated: Boolean get() = billing.isSimulated

    private val _buying = MutableStateFlow<PowerUpType?>(null)
    /** The power-up whose purchase sheet is currently open. */
    val buying: StateFlow<PowerUpType?> = _buying.asStateFlow()

    private val _restoring = MutableStateFlow(false)
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

    init {
        viewModelScope.launch {
            billing.events.collect { event ->
                if (event !is BillingEvent.Restored) _buying.value = null
            }
        }
    }

    fun onShopOpened() {
        _buying.value = null
        billing.connect()
        viewModelScope.launch { billing.refreshProducts() }
    }

    fun onResume() {
        _buying.value = null
    }

    fun buy(activity: Activity, type: PowerUpType) {
        if (_buying.value != null) return
        _buying.value = type
        billing.purchase(activity, type)
    }

    fun restore() {
        if (_restoring.value) return
        viewModelScope.launch {
            _restoring.value = true
            try {
                billing.restorePurchases()
            } finally {
                _restoring.value = false
            }
        }
    }

    /** Debug builds only: forget all simulated purchases so the flow can be tested again. */
    fun resetTestPurchases() {
        viewModelScope.launch { repository.clearPurchases() }
    }
}
