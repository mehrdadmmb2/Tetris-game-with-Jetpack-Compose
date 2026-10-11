package com.neon.tetris.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.neon.tetris.R
import com.neon.tetris.domain.PowerUpRules
import com.neon.tetris.domain.PowerUpType

@Composable
fun powerUpTitle(type: PowerUpType): String = stringResource(
    when (type) {
        PowerUpType.BOMB -> R.string.powerup_bomb
        PowerUpType.LIGHTNING -> R.string.powerup_lightning
        PowerUpType.HAMMER -> R.string.powerup_hammer
        PowerUpType.COLOR_BOMB -> R.string.powerup_color_bomb
        PowerUpType.DOUBLE_SCORE -> R.string.powerup_double_score
    },
)

@Composable
fun powerUpDescription(type: PowerUpType): String = when (type) {
    PowerUpType.BOMB -> stringResource(R.string.powerup_bomb_desc, PowerUpRules.BOMB_ROWS)
    PowerUpType.LIGHTNING -> stringResource(R.string.powerup_lightning_desc)
    PowerUpType.HAMMER -> stringResource(R.string.powerup_hammer_desc)
    PowerUpType.COLOR_BOMB -> stringResource(R.string.powerup_color_bomb_desc)
    PowerUpType.DOUBLE_SCORE ->
        stringResource(R.string.powerup_double_score_desc, (PowerUpRules.DOUBLE_SCORE_DURATION_MS / 1000).toInt())
}
