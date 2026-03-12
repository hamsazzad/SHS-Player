package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

data class BookmarkItem(
    val id: Long,
    val position: Long,
    val label: String,
)

@Composable
fun BookmarkView(
    modifier: Modifier = Modifier,
    bookmarks: List<BookmarkItem>,
    onAddBookmark: () -> Unit,
    onSeekToBookmark: (Long) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
) {
    Column(modifier = modifier.padding(bottom = 24.dp)) {
        TextButton(
            onClick = onAddBookmark,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                painter = painterResource(coreUiR.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(coreUiR.string.add_bookmark))
        }

        if (bookmarks.isEmpty()) {
            Text(
                text = stringResource(coreUiR.string.no_bookmarks),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            bookmarks.forEach { bookmark ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeekToBookmark(bookmark.position) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bookmark.label,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = formatDuration(bookmark.position),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = { onDeleteBookmark(bookmark.id) }) {
                        Icon(
                            painter = painterResource(coreUiR.drawable.ic_close),
                            contentDescription = stringResource(coreUiR.string.delete),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
