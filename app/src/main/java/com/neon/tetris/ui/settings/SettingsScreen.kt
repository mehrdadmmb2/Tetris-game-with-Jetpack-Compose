package com.neon.tetris.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neon.tetris.BuildConfig
import com.neon.tetris.R
import com.neon.tetris.domain.GameSettings
import com.neon.tetris.ui.common.GlassPanel
import com.neon.tetris.ui.common.IconKind
import com.neon.tetris.ui.common.NeonBackground
import com.neon.tetris.ui.common.NeonIconButton
import com.neon.tetris.ui.common.NeonSwitch
import com.neon.tetris.ui.common.NeonText
import com.neon.tetris.ui.theme.Neon

@Composable
fun SettingsScreen(
    settings: GameSettings,
    onHaptics: (Boolean) -> Unit,
    onGhostPiece: (Boolean) -> Unit,
    onSwipeControls: (Boolean) -> Unit,
    onScreenShake: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    NeonBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NeonIconButton(IconKind.BACK, onBack, color = Neon.Cyan)
                NeonText(stringResource(R.string.settings_title), size = 26.sp, glow = Neon.Cyan, weight = FontWeight.Black, letterSpacing = 3.sp)
            }
            Spacer(Modifier.height(20.dp))
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingRow(stringResource(R.string.settings_haptics), stringResource(R.string.settings_haptics_desc), settings.haptics, onHaptics)
                SettingRow(stringResource(R.string.settings_ghost), stringResource(R.string.settings_ghost_desc), settings.ghostPiece, onGhostPiece)
                SettingRow(stringResource(R.string.settings_swipe), stringResource(R.string.settings_swipe_desc), settings.swipeControls, onSwipeControls)
                SettingRow(stringResource(R.string.settings_shake), stringResource(R.string.settings_shake_desc), settings.screenShake, onScreenShake)
                NeonText(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    size = 12.sp, color = Neon.TextDim, letterSpacing = 1.sp,
                    align = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), accent = Neon.Purple) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                NeonText(title, size = 17.sp, weight = FontWeight.Bold, letterSpacing = 0.3.sp)
                NeonText(description, size = 12.sp, color = Neon.TextDim, weight = FontWeight.Medium, letterSpacing = 0.2.sp, maxLines = 2)
            }
            NeonSwitch(checked, onChange)
        }
    }
}
