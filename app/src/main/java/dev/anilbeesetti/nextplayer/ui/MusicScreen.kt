package dev.anilbeesetti.nextplayer.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity

data class MusicItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
)

fun cleanMeta(value: String?, default: String): String {
    if (value == null) return default
    val cleaned = value.trim()
    return if (cleaned.isEmpty() || cleaned == "<unknown>") default else cleaned
}

fun scanMusicFiles(context: Context): List<MusicItem> {
    val musicList = mutableListOf<MusicItem>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
    try {
        context.contentResolver.query(collection, projection, selection, null, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cleanMeta(cursor.getString(titleCol), "Unknown Title")
                    val artist = cleanMeta(cursor.getString(artistCol), "Unknown Artist")
                    val album = cleanMeta(cursor.getString(albumCol), "Unknown Album")
                    val duration = cursor.getLong(durationCol)
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    if (duration > 0) {
                        musicList.add(MusicItem(id, title, artist, album, duration, contentUri))
                    }
                }
            }
    } catch (_: Exception) {}
    return musicList
}

fun formatMusicDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%d:%02d", minutes, seconds)
}

fun isMusicFavorite(context: Context, id: Long): Boolean =
    context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE).getBoolean("fav_$id", false)

fun toggleMusicFavorite(context: Context, id: Long): Boolean {
    val prefs = context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)
    val newVal = !prefs.getBoolean("fav_$id", false)
    prefs.edit().putBoolean("fav_$id", newVal).apply()
    return newVal
}

fun getRecentMusicIds(context: Context): List<Long> {
    val raw = context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)
        .getString("recents", "") ?: ""
    return if (raw.isEmpty()) emptyList() else raw.split(",").mapNotNull { it.toLongOrNull() }
}

fun addToRecentMusic(context: Context, id: Long) {
    val prefs = context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)
    val current = getRecentMusicIds(context).toMutableList()
    current.remove(id)
    current.add(0, id)
    prefs.edit().putString("recents", current.take(50).joinToString(",")).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var allMusic by remember { mutableStateOf<List<MusicItem>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { allMusic = scanMusicFiles(context) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Music") })
        TabRow(selectedTabIndex = selectedTab) {
            listOf("All Songs", "Favourite Musics", "Recent").forEachIndexed { i, title ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
            }
        }
        if (allMusic.isEmpty()) {
            EmptyMusicView(message = "No music found", modifier = Modifier.weight(1f))
        } else {
            when (selectedTab) {
                0 -> MusicListView(
                    items = allMusic, context = context, modifier = Modifier.weight(1f),
                    onPlay = { m -> addToRecentMusic(context, m.id); refreshKey++; playMusic(context, m.uri) },
                    onFavoriteToggle = { refreshKey++ },
                )
                1 -> {
                    val favs = remember(refreshKey, allMusic) { allMusic.filter { isMusicFavorite(context, it.id) } }
                    if (favs.isEmpty()) {
                        EmptyMusicView(message = "No favourite songs yet. Tap the heart icon on any song.", modifier = Modifier.weight(1f))
                    } else {
                        MusicListView(items = favs, context = context, modifier = Modifier.weight(1f),
                            onPlay = { m -> addToRecentMusic(context, m.id); refreshKey++; playMusic(context, m.uri) },
                            onFavoriteToggle = { refreshKey++ })
                    }
                }
                2 -> {
                    val recentIds = remember(refreshKey) { getRecentMusicIds(context) }
                    val recentMusic = remember(refreshKey, allMusic) {
                        val byId = allMusic.associateBy { it.id }
                        recentIds.mapNotNull { byId[it] }
                    }
                    if (recentMusic.isEmpty()) {
                        EmptyMusicView(message = "No recently played songs", modifier = Modifier.weight(1f))
                    } else {
                        MusicListView(items = recentMusic, context = context, modifier = Modifier.weight(1f),
                            onPlay = { m -> addToRecentMusic(context, m.id); refreshKey++; playMusic(context, m.uri) },
                            onFavoriteToggle = { refreshKey++ })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMusicView(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(coreUiR.drawable.ic_music_note),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MusicListView(
    items: List<MusicItem>,
    context: Context,
    modifier: Modifier = Modifier,
    onPlay: (MusicItem) -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(items, key = { it.id }) { music ->
            MusicRow(music = music, context = context, onClick = { onPlay(music) }, onFavoriteToggle = onFavoriteToggle)
        }
    }
}

@Composable
private fun MusicRow(music: MusicItem, context: Context, onClick: () -> Unit, onFavoriteToggle: () -> Unit) {
    var isFav by remember { mutableStateOf(isMusicFavorite(context, music.id)) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(coreUiR.drawable.ic_music_note), contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = music.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "${music.artist} — ${music.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(text = formatMusicDuration(music.duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = { isFav = toggleMusicFavorite(context, music.id); onFavoriteToggle() }) {
            Icon(
                imageVector = if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isFav) "Remove from favourites" else "Add to favourites",
                tint = if (isFav) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun playMusic(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(context, PlayerActivity::class.java).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "audio/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {}
    }
}
