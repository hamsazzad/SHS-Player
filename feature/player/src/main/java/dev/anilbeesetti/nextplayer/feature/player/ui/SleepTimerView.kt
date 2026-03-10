package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

@Composable
fun SleepTimerView(
    modifier: Modifier = Modifier,
    onSelectMinutes: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = modifier.padding(bottom = 24.dp)) {
        RadioButtonRow(
            text = stringResource(coreUiR.string.sleep_timer_off),
            selected = false,
            onClick = onCancel,
        )
        RadioButtonRow(
            text = stringResource(coreUiR.string.sleep_timer_5),
            selected = false,
            onClick = { onSelectMinutes(5) },
        )
        RadioButtonRow(
            text = stringResource(coreUiR.string.sleep_timer_10),
            selected = false,
            onClick = { onSelectMinutes(10) },
        )
        RadioButtonRow(
            text = stringResource(coreUiR.string.sleep_timer_15),
            selected = false,
            onClick = { onSelectMinutes(15) },
        )
        RadioButtonRow(
            text = stringResource(coreUiR.string.sleep_timer_30),
            selected = false,
            onClick = { onSelectMinutes(30) },
        )
        RadioButtonRow(
            text = stringResource(coreUiR.string.sleep_timer_60),
            selected = false,
            onClick = { onSelectMinutes(60) },
        )
    }
}
