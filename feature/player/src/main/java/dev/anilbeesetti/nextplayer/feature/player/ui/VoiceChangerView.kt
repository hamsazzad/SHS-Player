package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

enum class VoiceEffect(val pitch: Float, val speed: Float) {
    NORMAL(1.0f, 1.0f),
    DEEP(0.6f, 1.0f),
    ROBOT(0.8f, 0.9f),
    CHILD(1.6f, 1.0f),
    ECHO(1.0f, 0.85f),
}

@Composable
fun VoiceChangerView(
    modifier: Modifier = Modifier,
    currentEffect: VoiceEffect,
    onSelectEffect: (VoiceEffect) -> Unit,
) {
    Column(modifier = modifier.padding(bottom = 24.dp)) {
        RadioButtonRow(
            text = stringResource(coreUiR.string.voice_normal),
            selected = currentEffect == VoiceEffect.NORMAL,
            onClick = { onSelectEffect(VoiceEffect.NORMAL) },
        )
        RadioButtonRow(
            text = stringResource(coreUiR.string.voice_deep),
            selected = currentEffect == VoiceEffect.DEEP,
            onClick = { onSelectEffect(VoiceEffect.DEEP) },
        )
        RadioButtonRow(
            text = stringResource(coreUiR.string.voice_robot),
            selected = currentEffect == VoiceEffect.ROBOT,
            onClick = { onSelectEffect(VoiceEffect.ROBOT) },
        )
        RadioButtonRow(
            text = stringResource(coreUiR.string.voice_child),
            selected = currentEffect == VoiceEffect.CHILD,
            onClick = { onSelectEffect(VoiceEffect.CHILD) },
        )
        RadioButtonRow(
            text = stringResource(coreUiR.string.voice_echo),
            selected = currentEffect == VoiceEffect.ECHO,
            onClick = { onSelectEffect(VoiceEffect.ECHO) },
        )
    }
}
