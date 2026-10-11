package com.neon.tetris

import android.app.Application
import com.neon.tetris.billing.BillingManager
import com.neon.tetris.billing.BillingManagerFactory
import com.neon.tetris.billing.PowerUpCatalog
import com.neon.tetris.data.GameRepository
import com.neon.tetris.util.Haptics

/** Hand-rolled dependency container: small enough that a DI framework would only add noise. */
class AppContainer(application: Application) {
    val repository = GameRepository(application)
    val catalog = PowerUpCatalog.fromResources(application)
    val haptics = Haptics(application)
    val billing: BillingManager = BillingManagerFactory.create(application, catalog, repository)
}

class TetrisApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
