package dev.anilbeesetti.nextplayer.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    musicList.add(MusicItem(id, title, artist, album, duration, contentUri))
                }
            }
    } catch (_: Exception) {}
    return musicList
}

private const val PREFS_MUSIC = "music_prefs"
private const val KEY_FAVORITES = "favorites"
private const val KEY_RECENT = "recent"
private const val KEY_PLAYLISTS = "playlists"
private const val KEY_PLAYLIST_PREFIX = "playlist_"
private const val MAX_RECENT = 50

fun getMusicFavorites(context: Context): Set<Long> {
    val prefs = context.getSharedPreferences(PREFS_MUSIC, Context.MODE_PRIVATE)
    return prefs.getStringSet(KEY_FAVORITES, emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
}

fun toggleMusicFavorite(context: Context, id: Long): Boolean {
    val prefs = context.getSharedPreferences(PREFS_MUSIC, Context.MODE_PRIVATE)
    val favs = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toMutableSet() ?: mutableSetOf()
    val idStr = id.toString()
    val isFav = if (favs.contains(idStr)) {
        favs.remove(idStr); false
    } else {
        favs.add(idStr); true
    }
    prefs.edit().putStringSet(KEY_FAVORITES, favs).apply()
    return isFav
}

fun isMusicFavorite(context: Context, id: Long): Boolean {
    val prefs = context.getSharedPreferences(PREFS_MUSIC, Context.MODE_PRIVATE)
    return prefs.getStringSet(KEY_FAVORITES, emptySet())?.contains(id.toString()) == true
}

fun addToRecentMusic(context: Context, id: Long) {
    val prefs = context.getSharedPreferences(PREFS_MUSIC, Context.MODE_PRIVATE)
    val recentStr = prefs.getString(KEY_RECENT, "") ?: ""
    val recent = if (recentStr.isEmpty()) mutableListOf() else recentStr.split(",").toMutableList()
    recent.remove(id.toString())
    recent.add(0, id.toString())
    if (recent.size > MAX_RECENT) recent.subList(MAX_RECENT, recent.size).clear()
    prefs.edit().putString(KEY_RECENT, recent.joinToString(",")).apply()
}

fun getRecentMusicIds(context: Context): List<Long> {
    val prefs = context.getSharedPreferences(PREFS_MUSIC, Context.MODE_PRIVATE)
    val recentStr = prefs.getString(KEY_RECENT, "") ?: ""
    return if (recentStr.isEmpty()) emptyList() else recentStr.split(",").mapNotNull { it.toLongOrNull() }
}

fun getCustomPlaylists(context: Context): List<String> {
    val prefs = context.getSharedPreferences(PREFS_MUSIC, Context.MODE_PRIVATE)
    val playlistsStr = prefs.getString(KEY_PLAYLISTS, "") ?: ""
    return if (playlistsStr.isEmpty()) emptyList() else playlistsStr.split("|||").filter { it.isNotEmpty() }
}

fun createPlaylist(context: Context, name: String) {
    val prefs = context.getSharedPreferences(PREFS_MUSIC, Context.MODE_PRIVATE)
    val existing = getCustomPlaylists(context).toMutableList()
    if (!existing.contains(name)) {
        existing.add(name)
        prefs.edit().putString(KEY_PLAYLISTS, existing.joinToString("|||")).apply()
    }
}

fun deletePlaylist(context: Context, name: String) {
    val prefs = context.getSharedPreferences(PREFS_MUSIC, Context.MODE_PRIVATE)
    val existing = getCustomPlaylists(context).toMutableList()
    existing.remove(name)
    prefs.edit().putString(KEY_PLAYLISTS, existing.joinToString("|||")).apply()
    prefs.edit().remove("$KEY_PLAYLIST_PREFIX$name").apply()
}

fun addToPlaylist(context: Context, playlistName: String, songId: Long) {
    val prefs = context.getSharedPreferences(PREFS_MUSIC, Context.MODE_PRIVATE)
    val key = "$KEY_PLAYLIST_PREFIX$playlistName"
    val current = prefs.getString(key, "") ?: ""
    val ids = if (current.isEmpty()) mutableListOf() else current.split(",").toMutableList()
    if (!ids.contains(songId.toString())) ids.add(songId.toString())
    prefs.edit().putString(key, ids.joinToString(",")).apply()
}

fun removeFromPlaylist(context: Context, playlistName: String, songId: Long) {
    val prefs = context.getSharedPreferences(PREFS_MUSIC, Context.MODE_PRIVATE)
    val key = "$KEY_PLAYLIST_PREFIX$playlistName"
    val current = prefs.getString(key, "") ?: ""
    val ids = if (current.isEmpty()) mutableListOf() else current.split(",").toMutableList()
    ids.remove(songId.toString())
    prefs.edit().putString(key, ids.joinToString(",")).apply()
}

fun getPlaylistSongs(context: Context, playlistName: String, allSongs: List<MusicItem>): List<MusicItem> {
    val prefs = context.getSharedPreferences(PREFS_MUSIC, Context.MODE_PRIVATE)
    val key = "$KEY_PLAYLIST_PREFIX$playlistName"
    val current = prefs.getString(key, "") ?: ""
    if (current.isEmpty()) return emptyList()
    val ids = current.split(",").mapNotNull { it.toLongOrNull() }
    val songMap = allSongs.associateBy { it.id }
    return ids.mapNotNull { songMap[it] }
}

fun formatMusicDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun playMusic(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(context, PlayerActivity::class.java).apply {
            setDataAndType(uri, "audio/*")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen() {
    val context = LocalContext.current
    var allSongs by remember { mutableStateOf<List<MusicItem>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var favoriteIds by remember { mutableStateOf(getMusicFavorites(context)) }
    var recentIds by remember { mutableStateOf(getRecentMusicIds(context)) }
    var customPlaylists by remember { mutableStateOf(getCustomPlaylists(context)) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var selectedPlaylistTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        allSongs = scanMusicFiles(context)
        favoriteIds = getMusicFavorites(context)
        recentIds = getRecentMusicIds(context)
        customPlaylists = getCustomPlaylists(context)
    }

    val favoriteSongs = allSongs.filter { favoriteIds.contains(it.id) }
    val recentSongs = run {
        val songMap = allSongs.associateBy { it.id }
        recentIds.mapNotNull { songMap[it] }
    }

    val mainTabs = listOf("All", "Favourites", "Recent", "Playlists")

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Music") })

        TabRow(selectedTabIndex = selectedTab) {
            mainTabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(label) }
                )
            }
        }

        when (selectedTab) {
            0 -> MusicList(
                songs = allSongs,
                context = context,
                customPlaylists = customPlaylists,
                onPlay = { song ->
                    addToRecentMusic(context, song.id)
                    recentIds = getRecentMusicIds(context)
                    playMusic(context, song.uri)
                },
                onFavoriteToggle = { favoriteIds = getMusicFavorites(context) },
                onAddToPlaylist = { song, playlist ->
                    addToPlaylist(context, playlist, song.id)
                }
            )
            1 -> MusicList(
                songs = favoriteSongs,
                context = context,
                customPlaylists = customPlaylists,
                emptyMessage = "No favourite songs yet.\nTap the heart icon on any song.",
                onPlay = { song ->
                    addToRecentMusic(context, song.id)
                    recentIds = getRecentMusicIds(context)
                    playMusic(context, song.uri)
                },
                onFavoriteToggle = { favoriteIds = getMusicFavorites(context) },
                onAddToPlaylist = { song, playlist ->
                    addToPlaylist(context, playlist, song.id)
                }
            )
            2 -> MusicList(
                songs = recentSongs,
                context = context,
                customPlaylists = customPlaylists,
                emptyMessage = "No recently played songs.",
                onPlay = { song ->
                    addToRecentMusic(context, song.id)
                    recentIds = getRecentMusicIds(context)
                    playMusic(context, song.uri)
                },
                onFavoriteToggle = { favoriteIds = getMusicFavorites(context) },
                onAddToPlaylist = { song, playlist ->
                    addToPlaylist(context, playlist, song.id)
                }
            )
            3 -> PlaylistsSection(
                context = context,
                customPlaylists = customPlaylists,
                allSongs = allSongs,
                onCreatePlaylist = { showCreatePlaylist = true },
                onDeletePlaylist = { name ->
                    deletePlaylist(context, name)
                    customPlaylists = getCustomPlaylists(context)
                },
                onPlay = { song ->
                    addToRecentMusic(context, song.id)
                    recentIds = getRecentMusicIds(context)
                    playMusic(context, song.uri)
                },
                onFavoriteToggle = { favoriteIds = getMusicFavorites(context) },
            )
        }
    }

    if (showCreatePlaylist) {
        AlertDialog(
            onDismissRequest = {
                showCreatePlaylist = false
                newPlaylistName = ""
            },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        createPlaylist(context, newPlaylistName.trim())
                        customPlaylists = getCustomPlaylists(context)
                        newPlaylistName = ""
                        showCreatePlaylist = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreatePlaylist = false
                    newPlaylistName = ""
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun PlaylistsSection(
    context: Context,
    customPlaylists: List<String>,
    allSongs: List<MusicItem>,
    onCreatePlaylist: () -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onPlay: (MusicItem) -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    if (selectedPlaylist != null) {
        val playlistSongs = getPlaylistSongs(context, selectedPlaylist!!, allSongs)
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selectedPlaylist = null }) {
                    Icon(
                        painter = painterResource(coreUiR.drawable.ic_arrow_left),
                        contentDescription = "Back",
                    )
                }
                Text(
                    text = selectedPlaylist!!,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
            }
            MusicList(
                songs = playlistSongs,
                context = context,
                customPlaylists = emptyList(),
                emptyMessage = "No songs in this playlist.\nGo to All Songs and long-press to add.",
                onPlay = onPlay,
                onFavoriteToggle = onFavoriteToggle,
                onAddToPlaylist = { _, _ -> },
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "My Playlists",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCreatePlaylist) {
                Icon(
                    painter = painterResource(coreUiR.drawable.ic_add),
                    contentDescription = "Create Playlist",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (customPlaylists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(coreUiR.drawable.ic_playlist),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No playlists yet.\nTap + to create a playlist.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                items(customPlaylists) { playlist ->
                    var showMenu by remember { mutableStateOf(false) }
                    val songCount = getPlaylistSongs(context, playlist, customPlaylists.map { MusicItem(0L,"","","",0L, Uri.EMPTY) }).size
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPlaylist = playlist }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(coreUiR.drawable.ic_playlist),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${getPlaylistSongs(context, playlist, customPlaylists.map { MusicItem(0L, it,"","",0L,Uri.EMPTY) }).size} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    painter = painterResource(coreUiR.drawable.ic_more_vert),
                                    contentDescription = "More options",
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Delete Playlist") },
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = playlist
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Playlist") },
            text = { Text("Delete \"${showDeleteDialog}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePlaylist(showDeleteDialog!!)
                    showDeleteDialog = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicList(
    songs: List<MusicItem>,
    context: Context,
    customPlaylists: List<String>,
    emptyMessage: String = "No music found.",
    onPlay: (MusicItem) -> Unit,
    onFavoriteToggle: () -> Unit,
    onAddToPlaylist: (MusicItem, String) -> Unit,
) {
    if (songs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
        items(songs, key = { it.id }) { music ->
            MusicItemRow(
                music = music,
                context = context,
                customPlaylists = customPlaylists,
                onPlay = { onPlay(music) },
                onFavoriteToggle = onFavoriteToggle,
                onAddToPlaylist = { playlist -> onAddToPlaylist(music, playlist) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicItemRow(
    music: MusicItem,
    context: Context,
    customPlaylists: List<String>,
    onPlay: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
) {
    var isFav by remember(music.id) { mutableStateOf(isMusicFavorite(context, music.id)) }
    var showContextMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onPlay,
                onLongClick = { showContextMenu = true },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(coreUiR.drawable.ic_music_note),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = music.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = music.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = music.album,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatMusicDuration(music.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = {
            isFav = toggleMusicFavorite(context, music.id)
            onFavoriteToggle()
        }) {
            Icon(
                painter = painterResource(if (isFav) coreUiR.drawable.ic_favorite else coreUiR.drawable.ic_favorite_border),
                contentDescription = if (isFav) "Remove from favourites" else "Add to favourites",
                tint = if (isFav) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        Box {
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(if (isFav) "Remove from Favourites" else "Add to Favourites") },
                    onClick = {
                        showContextMenu = false
                        isFav = toggleMusicFavorite(context, music.id)
                        onFavoriteToggle()
                    }
                )
                if (customPlaylists.isNotEmpty()) {
                    customPlaylists.forEach { playlist ->
                        DropdownMenuItem(
                            text = { Text("Add to: $playlist") },
                            onClick = {
                                showContextMenu = false
                                onAddToPlaylist(playlist)
                            }
                        )
                    }
                }
                DropdownMenuItem(
                    text = { Text("Play") },
                    onClick = {
                        showContextMenu = false
                        onPlay()
                    }
                )
            }
        }
    }
}
