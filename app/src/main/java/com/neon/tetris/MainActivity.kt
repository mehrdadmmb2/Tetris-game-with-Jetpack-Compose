package com.neon.tetris

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.neon.tetris.ui.AppViewModel
import com.neon.tetris.ui.AppViewModelFactory
import com.neon.tetris.ui.game.GameViewModel
import com.neon.tetris.ui.navigation.NeonTetrisApp
import com.neon.tetris.ui.shop.ShopViewModel

class MainActivity : ComponentActivity() {

    private val container: AppContainer by lazy { (application as TetrisApplication).container }
    private val factory: AppViewModelFactory by lazy { AppViewModelFactory(container) }

    private val appVm: AppViewModel by viewModels { factory }
    private val gameVm: GameViewModel by viewModels { factory }
    private val shopVm: ShopViewModel by viewModels { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        container.billing.connect()

        setContent {
            NeonTetrisApp(
                appVm = appVm,
                gameVm = gameVm,
                shopVm = shopVm,
                haptics = container.haptics,
                onBuy = { type -> shopVm.buy(this@MainActivity, type) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-reads the account's purchases: completes pending ones and picks up purchases made elsewhere.
        container.billing.connect()
    }

    override fun onPause() {
        super.onPause()
        gameVm.autoPause()
    }
}
