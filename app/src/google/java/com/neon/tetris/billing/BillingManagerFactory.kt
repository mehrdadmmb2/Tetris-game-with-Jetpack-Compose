package com.neon.tetris.billing

import android.app.Application
import com.neon.tetris.BuildConfig
import com.neon.tetris.data.GameRepository

object BillingManagerFactory {
    fun create(
        application: Application,
        catalog: PowerUpCatalog,
        repository: GameRepository,
    ): BillingManager {
        return if (BuildConfig.SIMULATE_PURCHASES) {
            SimulatedBillingManager(catalog, repository)
        } else {
            PlayBillingManager(application, catalog, repository)
        }
    }
}
