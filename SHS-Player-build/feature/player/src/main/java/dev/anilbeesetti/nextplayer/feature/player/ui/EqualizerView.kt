package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.feature.player.state.AudioEqualizerState

val LocalAudioEqualizerState = compositionLocalOf<AudioEqualizerState?> { null }

@Composable
fun EqualizerView(
    modifier: Modifier = Modifier,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    val audioEqualizerState = LocalAudioEqualizerState.current
    Column(modifier = modifier.padding(bottom = 24.dp)) {

        if (audioEqualizerState != null && audioEqualizerState.isReady && audioEqualizerState.bandCount > 0) {
            Text(
                text = "Audio Equalizer",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            audioEqualizerState.bandLevels.forEachIndexed { index, level ->
                val freq = audioEqualizerState.bandFrequencies.getOrElse(index) { "" }
                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = freq, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${if (level >= 0) "+" else ""}${level / 100} dB",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Slider(
                        value = level.toFloat(),
                        onValueChange = { audioEqualizerState.setBandLevel(index, it.toInt()) },
                        valueRange = audioEqualizerState.minLevel.toFloat()..audioEqualizerState.maxLevel.toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            TextButton(
                onClick = { audioEqualizerState.resetBands() },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Reset EQ")
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        Text(
            text = "Video Adjustments",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        EqualizerSlider(
            label = stringResource(coreUiR.string.brightness_label),
            value = brightness,
            onValueChange = onBrightnessChange,
        )
        EqualizerSlider(
            label = stringResource(coreUiR.string.contrast),
            value = contrast,
            onValueChange = onContrastChange,
        )
        EqualizerSlider(
            label = stringResource(coreUiR.string.saturation),
            value = saturation,
            onValueChange = onSaturationChange,
        )
        TextButton(
            onClick = onReset,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(coreUiR.string.reset))
        }
    }
}

@Composable
private fun EqualizerSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..2f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
