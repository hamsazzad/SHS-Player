package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

@Composable
fun AbRepeatView(
    modifier: Modifier = Modifier,
    pointA: Long?,
    pointB: Long?,
    currentPosition: Long,
    onSetA: () -> Unit,
    onSetB: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = modifier.padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Current: ${formatDuration(currentPosition)}",
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onSetA,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (pointA != null) "A: ${formatDuration(pointA)}"
                    else stringResource(coreUiR.string.ab_repeat_set_a),
                )
            }
            Button(
                onClick = onSetB,
                modifier = Modifier.weight(1f),
                enabled = pointA != null,
            ) {
                Text(
                    text = if (pointB != null) "B: ${formatDuration(pointB)}"
                    else stringResource(coreUiR.string.ab_repeat_set_b),
                )
            }
        }

        if (pointA != null || pointB != null) {
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(coreUiR.string.ab_repeat_clear))
            }
        }

        if (pointA != null && pointB != null) {
            Text(
                text = stringResource(coreUiR.string.ab_repeat_active),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
