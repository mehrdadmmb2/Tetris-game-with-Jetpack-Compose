package com.neon.tetris.ui.shop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neon.tetris.R
import com.neon.tetris.billing.BillingEvent
import com.neon.tetris.billing.BillingFailure
import com.neon.tetris.billing.BillingStatus
import com.neon.tetris.billing.ProductInfo
import com.neon.tetris.domain.PowerUpInventory
import com.neon.tetris.domain.PowerUpRules
import com.neon.tetris.domain.PowerUpType
import com.neon.tetris.ui.common.GlassPanel
import com.neon.tetris.ui.common.IconKind
import com.neon.tetris.ui.common.NeonBackground
import com.neon.tetris.ui.common.NeonButton
import com.neon.tetris.ui.common.NeonIcon
import com.neon.tetris.ui.common.NeonIconButton
import com.neon.tetris.ui.common.NeonText
import com.neon.tetris.ui.common.PowerUpIcon
import com.neon.tetris.ui.common.plainClickable
import com.neon.tetris.ui.common.powerUpDescription
import com.neon.tetris.ui.common.powerUpTitle
import com.neon.tetris.ui.theme.Neon
import kotlinx.coroutines.delay

private class ShopNotice(val id: Int, val event: BillingEvent)

@Composable
fun ShopScreen(
    vm: ShopViewModel,
    onBuy: (PowerUpType) -> Unit,
    onBack: () -> Unit,
) {
    val inventory by vm.inventory.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val buying by vm.buying.collectAsStateWithLifecycle()
    val restoring by vm.restoring.collectAsStateWithLifecycle()

    var notice by remember { mutableStateOf<ShopNotice?>(null) }
    // the pill keeps its text while it animates out, so remember the last notice separately
    var shownNotice by remember { mutableStateOf<ShopNotice?>(null) }
    var noticeId by remember { mutableIntStateOf(0) }

    LaunchedEffect(vm) { vm.onShopOpened() }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            val n = ShopNotice(++noticeId, event)
            shownNotice = n
            notice = n
        }
    }
    LaunchedEffect(notice?.id) {
        if (notice != null) {
            delay(3200)
            notice = null
        }
    }

    NeonBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                NeonIconButton(IconKind.BACK, onBack, color = Neon.Cyan)
                NeonText(stringResource(R.string.shop_title), size = 26.sp, glow = Neon.Magenta, weight = FontWeight.Black, letterSpacing = 3.sp)
            }
            Spacer(Modifier.height(14.dp))

            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NeonText(
                    stringResource(R.string.shop_subtitle, PowerUpRules.CHARGES_ON_UNLOCK, PowerUpRules.RECHARGE_EVERY_LEVELS),
                    size = 13.sp, color = Neon.TextDim, weight = FontWeight.Medium, letterSpacing = 0.2.sp, maxLines = 4,
                )
                if (vm.isSimulated) Banner(stringResource(R.string.shop_test_mode), Neon.Orange)
                if (status == BillingStatus.UNAVAILABLE) Banner(stringResource(R.string.shop_billing_unavailable), Neon.Red)

                for (type in PowerUpType.entries) {
                    PowerUpCard(
                        type = type,
                        inventory = inventory[type] ?: PowerUpInventory(),
                        product = products[type],
                        isBuying = buying == type,
                        anyBuying = buying != null,
                        onBuy = { onBuy(type) },
                    )
                }

                NeonButton(
                    text = stringResource(if (restoring) R.string.shop_restoring else R.string.shop_restore),
                    onClick = vm::restore,
                    modifier = Modifier.fillMaxWidth(),
                    color = Neon.Purple,
                    filled = false,
                    enabled = !restoring,
                    height = 52.dp,
                    textSize = 15.sp,
                    icon = IconKind.ROTATE_CW,
                )
                if (vm.isSimulated) {
                    NeonText(
                        stringResource(R.string.shop_reset_test),
                        Modifier.fillMaxWidth().plainClickable(onClick = vm::resetTestPurchases).padding(vertical = 10.dp),
                        size = 12.sp, color = Neon.Orange, align = TextAlign.Center, letterSpacing = 0.5.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        AnimatedVisibility(
            visible = notice != null,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),
            enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it },
            exit = fadeOut(tween(200)),
        ) {
            shownNotice?.let { NoticePill(it.event) }
        }
    }
}

@Composable
private fun Banner(text: String, color: Color) {
    val shape = RoundedCornerShape(12.dp)
    NeonText(
        text,
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.8f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        size = 12.sp, color = color, weight = FontWeight.Bold, letterSpacing = 0.3.sp, maxLines = 3,
    )
}

@Composable
private fun PowerUpCard(
    type: PowerUpType,
    inventory: PowerUpInventory,
    product: ProductInfo?,
    isBuying: Boolean,
    anyBuying: Boolean,
    onBuy: () -> Unit,
) {
    val color = Neon.powerUpColor(type)
    GlassPanel(Modifier.fillMaxWidth(), accent = color, glow = inventory.owned) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val iconShape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .size(64.dp)
                    .clip(iconShape)
                    .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.08f))))
                    .border(1.5.dp, color.copy(alpha = 0.8f), iconShape),
                contentAlignment = Alignment.Center,
            ) { PowerUpIcon(type, color, 38.dp) }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                NeonText(powerUpTitle(type), size = 18.sp, weight = FontWeight.Black, letterSpacing = 0.5.sp)
                NeonText(powerUpDescription(type), size = 12.sp, color = Neon.TextDim, weight = FontWeight.Medium, letterSpacing = 0.2.sp, maxLines = 3)
                if (inventory.owned) {
                    NeonText(
                        stringResource(R.string.shop_charges, inventory.quantity),
                        size = 13.sp, color = color, weight = FontWeight.Black, letterSpacing = 1.sp,
                    )
                }
            }

            if (inventory.owned) {
                OwnedBadge()
            } else {
                val available = product?.available == true
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NeonText(
                        product?.price ?: "—", size = 17.sp, color = color, weight = FontWeight.Black,
                        glow = color.copy(alpha = 0.5f), letterSpacing = 0.5.sp,
                    )
                    NeonButton(
                        text = when {
                            isBuying -> "…"
                            available -> stringResource(R.string.shop_buy)
                            else -> stringResource(R.string.shop_unavailable)
                        },
                        onClick = onBuy,
                        modifier = Modifier.width(100.dp),
                        color = color,
                        enabled = available && !anyBuying,
                        height = 42.dp,
                        textSize = if (available || isBuying) 14.sp else 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun OwnedBadge() {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .clip(shape)
            .background(Neon.Green.copy(alpha = 0.15f))
            .border(1.5.dp, Neon.Green, shape)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        NeonIcon(IconKind.CHECK, Neon.Green, 16.dp, glow = false)
        NeonText(stringResource(R.string.shop_owned), size = 12.sp, color = Neon.Green, weight = FontWeight.Black, letterSpacing = 1.sp)
    }
}

@Composable
private fun NoticePill(event: BillingEvent) {
    val (text, color) = when (event) {
        is BillingEvent.Purchased -> stringResource(R.string.msg_purchased, powerUpTitle(event.type)) to Neon.Green
        is BillingEvent.Pending -> stringResource(R.string.msg_pending) to Neon.Yellow
        is BillingEvent.Cancelled -> stringResource(R.string.msg_cancelled) to Neon.TextDim
        is BillingEvent.Restored ->
            if (event.count > 0) stringResource(R.string.msg_restored, event.count) to Neon.Green
            else stringResource(R.string.msg_restored_none) to Neon.TextDim
        is BillingEvent.Failed -> stringResource(
            when (event.failure) {
                BillingFailure.SERVICE_UNAVAILABLE -> R.string.msg_failed_service
                BillingFailure.PRODUCT_UNAVAILABLE -> R.string.msg_failed_product
                BillingFailure.OTHER -> R.string.msg_failed_other
            },
        ) to Neon.Red
    }
    GlassPanel(Modifier.widthIn(max = 480.dp), accent = color, corner = 16.dp, glow = true) {
        NeonText(
            text, Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            size = 13.sp, color = Color.White, weight = FontWeight.SemiBold, align = TextAlign.Center, maxLines = 3, letterSpacing = 0.2.sp,
        )
    }
}
