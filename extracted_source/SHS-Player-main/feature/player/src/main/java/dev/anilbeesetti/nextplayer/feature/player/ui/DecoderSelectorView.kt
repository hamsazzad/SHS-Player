package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

@Composable
fun DecoderSelectorView(
    modifier: Modifier = Modifier,
    isHardware: Boolean,
    onSelectHardware: () -> Unit,
    onSelectSoftware: () -> Unit,
) {
    Column(modifier = modifier.padding(bottom = 24.dp)) {
        RadioButtonRow(
            text = stringResource(coreUiR.string.hardware_decoder),
            selected = isHardware,
            onClick = onSelectHardware,
        )
        RadioButtonRow(
            text = stringResource(coreUiR.string.software_decoder),
            selected = !isHardware,
            onClick = onSelectSoftware,
        )
    }
}
