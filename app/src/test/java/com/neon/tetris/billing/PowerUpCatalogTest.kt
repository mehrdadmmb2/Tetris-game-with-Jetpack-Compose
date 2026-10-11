package com.neon.tetris.billing

import com.neon.tetris.domain.PowerUpType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class PowerUpCatalogTest {

    private fun ids() = PowerUpType.entries.associateWith { "product_${it.key}" }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
        } catch (expected: IllegalArgumentException) {
            return
        }
        fail("expected IllegalArgumentException")
    }

    @Test
    fun mapsEveryPowerUpToItsProductIdAndBack() {
        val catalog = PowerUpCatalog(ids())
        for (type in PowerUpType.entries) {
            assertEquals("product_${type.key}", catalog.productId(type))
            assertEquals(type, catalog.typeFor(catalog.productId(type)))
        }
        assertEquals(PowerUpType.entries.size, catalog.allProductIds.size)
    }

    @Test
    fun productIdsAreFullyConfigurable() {
        val custom = ids().toMutableMap().apply { put(PowerUpType.BOMB, "com.example.my_custom_bomb") }
        val catalog = PowerUpCatalog(custom)
        assertEquals("com.example.my_custom_bomb", catalog.productId(PowerUpType.BOMB))
        assertEquals(PowerUpType.BOMB, catalog.typeFor("com.example.my_custom_bomb"))
        assertNull(catalog.typeFor("product_bomb")) // the old id no longer maps to anything
    }

    @Test
    fun unknownProductIdsAreIgnored() {
        assertNull(PowerUpCatalog(ids()).typeFor("something_else"))
    }

    @Test
    fun duplicateProductIdsAreRejected() {
        assertRejected { PowerUpCatalog(PowerUpType.entries.associateWith { "same_id" }) }
    }

    @Test
    fun everyPowerUpNeedsAProductId() {
        assertRejected { PowerUpCatalog(mapOf(PowerUpType.BOMB to "only_bomb")) }
    }
}
