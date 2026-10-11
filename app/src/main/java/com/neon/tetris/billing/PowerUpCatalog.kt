package com.neon.tetris.billing

import android.content.Context
import com.neon.tetris.R
import com.neon.tetris.domain.PowerUpType

/**
 * Maps every power-up to its Google Play product ID. IDs live in res/values/product_ids.xml, so they can be
 * changed (or overridden per build type / flavor) without touching code. Tests can pass any map.
 */
class PowerUpCatalog(private val productIds: Map<PowerUpType, String>) {

    private val byProductId: Map<String, PowerUpType> = productIds.entries.associate { (type, id) -> id to type }

    init {
        require(productIds.keys.containsAll(PowerUpType.entries)) { "Every power-up needs a product id" }
        require(byProductId.size == productIds.size) { "Product ids must be unique" }
    }

    val allProductIds: List<String> get() = PowerUpType.entries.map { productIds.getValue(it) }

    fun productId(type: PowerUpType): String = productIds.getValue(type)

    fun typeFor(productId: String): PowerUpType? = byProductId[productId]

    companion object {
        fun fromResources(context: Context): PowerUpCatalog = PowerUpCatalog(
            mapOf(
                PowerUpType.BOMB to context.getString(R.string.product_id_bomb),
                PowerUpType.LIGHTNING to context.getString(R.string.product_id_lightning),
                PowerUpType.HAMMER to context.getString(R.string.product_id_hammer),
                PowerUpType.COLOR_BOMB to context.getString(R.string.product_id_color_bomb),
                PowerUpType.DOUBLE_SCORE to context.getString(R.string.product_id_double_score),
            ),
        )
    }
}
