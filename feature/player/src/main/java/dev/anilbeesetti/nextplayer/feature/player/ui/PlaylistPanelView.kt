package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

data class PlaylistItem(
    val index: Int,
    val title: String,
    val uri: String,
    val isPlaying: Boolean,
)

@Composable
fun PlaylistPanelView(
    modifier: Modifier = Modifier,
    items: List<PlaylistItem>,
    onItemClick: (Int) -> Unit,
) {
    Column(modifier = modifier.padding(bottom = 24.dp)) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(item.index) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                if (item.isPlaying) {
                    Icon(
                        painter = painterResource(coreUiR.drawable.ic_play),
                        contentDescription = stringResource(coreUiR.string.now_playing),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = "${item.index + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.size(20.dp),
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.isPlaying) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
            }
        }
    }
}
