package dev.anilbeesetti.nextplayer.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity

// ─── Data Models ─────────────────────────────────────────────────────────────

data class MusicItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val size: Long = 0L,
    val dateAdded: Long = 0L,
    val path: String = "",
    val uri: Uri,
    val folderName: String = "",
    val folderPath: String = "",
    val isOnSdCard: Boolean = false,
)

data class MusicFolder(
    val name: String,
    val path: String,
    val songs: List<MusicItem>,
    val isOnSdCard: Boolean = false,
)

enum class MusicViewMode { FILES, FOLDERS, TREE }
enum class MusicSortBy { TITLE, DURATION, DATE, SIZE }
enum class MusicSortOrder { ASCENDING, DESCENDING }
enum class MusicLayoutMode { LIST, GRID }

data class MusicDisplayPrefs(
    val showAlbumArt: Boolean = true,
    val showArtist: Boolean = true,
    val showDuration: Boolean = true,
    val showSize: Boolean = false,
    val showDate: Boolean = false,
    val showPath: Boolean = false,
)

// ─── Utility functions ────────────────────────────────────────────────────────

fun cleanMeta(value: String?, default: String): String {
    if (value == null) return default
    val cleaned = value.trim()
    return if (cleaned.isEmpty() || cleaned == "<unknown>") default else cleaned
}

fun formatDuration(ms: Long): String {
    val secs = ms / 1000
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}

fun scanMusicFiles(context: Context): List<MusicItem> {
    val musicList = mutableListOf<MusicItem>()
    val internalPath = Environment.getExternalStorageDirectory().absolutePath
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
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.DATA,
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    try {
        context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val path = cursor.getString(pathCol) ?: continue
                val folderPath = path.substringBeforeLast("/")
                val folderName = folderPath.substringAfterLast("/").ifEmpty { "/" }
                musicList.add(MusicItem(
                    id = id,
                    title = cleanMeta(cursor.getString(titleCol), "Unknown Title"),
                    artist = cleanMeta(cursor.getString(artistCol), "Unknown Artist"),
                    album = cleanMeta(cursor.getString(albumCol), "Unknown Album"),
                    duration = cursor.getLong(durCol),
                    size = cursor.getLong(sizeCol),
                    dateAdded = cursor.getLong(dateCol) * 1000L,
                    path = path,
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    folderName = folderName,
                    folderPath = folderPath,
                    isOnSdCard = !path.startsWith(internalPath),
                ))
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return musicList
}

fun groupByFolder(songs: List<MusicItem>): List<MusicFolder> =
    songs.groupBy { it.folderPath }.map { (path, items) ->
        MusicFolder(
            name = path.substringAfterLast("/").ifEmpty { "/" },
            path = path,
            songs = items,
            isOnSdCard = items.firstOrNull()?.isOnSdCard ?: false,
        )
    }.sortedBy { it.name.lowercase() }

fun sortSongs(songs: List<MusicItem>, by: MusicSortBy, order: MusicSortOrder): List<MusicItem> {
    val sorted = when (by) {
        MusicSortBy.TITLE -> songs.sortedBy { it.title.lowercase() }
        MusicSortBy.DURATION -> songs.sortedBy { it.duration }
        MusicSortBy.DATE -> songs.sortedBy { it.dateAdded }
        MusicSortBy.SIZE -> songs.sortedBy { it.size }
    }
    return if (order == MusicSortOrder.DESCENDING) sorted.reversed() else sorted
}

// ─── Favorites / Recent / Playlist helpers ────────────────────────────────────

private const val PREFS = "music_prefs"

fun getMusicPrefs(context: Context): SharedPreferences =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

fun getMusicFavorites(context: Context): Set<Long> =
    getMusicPrefs(context).getStringSet("favorites", emptySet())
        ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()

fun toggleMusicFavorite(context: Context, id: Long): Boolean {
    val prefs = getMusicPrefs(context)
    val favs = getMusicFavorites(context).toMutableSet()
    val isFav = favs.contains(id)
    if (isFav) favs.remove(id) else favs.add(id)
    prefs.edit().putStringSet("favorites", favs.map { it.toString() }.toSet()).apply()
    return !isFav
}

fun getRecentMusicIds(context: Context): List<Long> =
    getMusicPrefs(context).getString("recents", "")
        ?.split(",")?.filter { it.isNotEmpty() }?.mapNotNull { it.toLongOrNull() } ?: emptyList()

fun addRecentMusic(context: Context, id: Long) {
    val prefs = getMusicPrefs(context)
    val recents = getRecentMusicIds(context).toMutableList()
    recents.remove(id); recents.add(0, id)
    prefs.edit().putString("recents", recents.take(50).joinToString(",")).apply()
}

fun getCustomPlaylists(context: Context): List<String> =
    getMusicPrefs(context).getString("custom_playlists", "")
        ?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()

fun saveCustomPlaylists(context: Context, playlists: List<String>) =
    getMusicPrefs(context).edit().putString("custom_playlists", playlists.joinToString("|")).apply()

fun getPlaylistSongs(context: Context, playlistName: String, allSongs: List<MusicItem>): List<MusicItem> {
    val idsStr = getMusicPrefs(context).getString("playlist_$playlistName", "") ?: ""
    val ids = idsStr.split(",").filter { it.isNotEmpty() }.mapNotNull { it.toLongOrNull() }.toSet()
    return allSongs.filter { it.id in ids }
}

fun addSongToPlaylist(context: Context, playlistName: String, id: Long) {
    val prefs = getMusicPrefs(context)
    val key = "playlist_$playlistName"
    val ids = (prefs.getString(key, "") ?: "").split(",").filter { it.isNotEmpty() }.toMutableList()
    if (!ids.contains(id.toString())) ids.add(id.toString())
    prefs.edit().putString(key, ids.joinToString(",")).apply()
}

fun playAudio(context: Context, item: MusicItem) {
    addRecentMusic(context, item.id)
    val intent = Intent(context, PlayerActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        setDataAndType(item.uri, "audio/*")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

// ─── Main MusicScreen ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MusicScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var allSongs by remember { mutableStateOf<List<MusicItem>>(emptyList()) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var favoriteIds by remember { mutableStateOf(getMusicFavorites(context)) }
    var recentIds by remember { mutableStateOf(getRecentMusicIds(context)) }
    var customPlaylists by remember { mutableStateOf(getCustomPlaylists(context)) }

    var viewMode by rememberSaveable { mutableStateOf(MusicViewMode.FILES) }
    var layoutMode by rememberSaveable { mutableStateOf(MusicLayoutMode.LIST) }
    var sortBy by rememberSaveable { mutableStateOf(MusicSortBy.TITLE) }
    var sortOrder by rememberSaveable { mutableStateOf(MusicSortOrder.ASCENDING) }
    var displayPrefs by remember { mutableStateOf(MusicDisplayPrefs()) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var showQuickSettings by rememberSaveable { mutableStateOf(false) }
    var showCreatePlaylist by rememberSaveable { mutableStateOf(false) }
    var newPlaylistName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        allSongs = scanMusicFiles(context)
        favoriteIds = getMusicFavorites(context)
        recentIds = getRecentMusicIds(context)
        customPlaylists = getCustomPlaylists(context)
    }

    val tabs = listOf("Files", "Folders", "Favourites", "Recent", "Playlists")

    val sortedSongs = remember(allSongs, sortBy, sortOrder) { sortSongs(allSongs, sortBy, sortOrder) }

    val filteredSongs = remember(sortedSongs, searchQuery) {
        if (searchQuery.isBlank()) sortedSongs
        else sortedSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    val favoriteSongs = remember(filteredSongs, favoriteIds) { filteredSongs.filter { it.id in favoriteIds } }

    val recentSongs = remember(allSongs, recentIds, searchQuery) {
        val map = allSongs.associateBy { it.id }
        recentIds.mapNotNull { map[it] }.let { songs ->
            if (searchQuery.isBlank()) songs
            else songs.filter { it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true) }
        }
    }

    val folders = remember(filteredSongs) { groupByFolder(filteredSongs) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Music") },
            actions = {
                IconButton(onClick = { isSearchActive = !isSearchActive; if (!isSearchActive) searchQuery = "" }) {
                    Icon(imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = if (isSearchActive) "Close Search" else "Search")
                }
                IconButton(onClick = { showQuickSettings = true }) {
                    Icon(imageVector = Icons.Rounded.Settings, contentDescription = "Quick Settings")
                }
            }
        )

        AnimatedVisibility(visible = isSearchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search songs, artists, albums...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
            )
        }

        // Info bar with count + layout toggle + sort chip (Files/Folders tabs only)
        if (selectedTab <= 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val count = if (selectedTab == 0) filteredSongs.size else folders.size
                Text("$count items", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                FilterChip(
                    selected = false,
                    onClick = { layoutMode = if (layoutMode == MusicLayoutMode.LIST) MusicLayoutMode.GRID else MusicLayoutMode.LIST },
                    label = { Text(if (layoutMode == MusicLayoutMode.LIST) "List" else "Grid") },
                    leadingIcon = {
                        Icon(imageVector = if (layoutMode == MusicLayoutMode.LIST) Icons.AutoMirrored.Rounded.List else Icons.Rounded.GridView,
                            contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                FilterChip(
                    selected = false,
                    onClick = { showQuickSettings = true },
                    label = {
                        Text(when (sortBy) {
                            MusicSortBy.TITLE -> if (sortOrder == MusicSortOrder.ASCENDING) "A-Z" else "Z-A"
                            MusicSortBy.DURATION -> if (sortOrder == MusicSortOrder.ASCENDING) "Shortest" else "Longest"
                            MusicSortBy.DATE -> if (sortOrder == MusicSortOrder.ASCENDING) "Oldest" else "Newest"
                            MusicSortBy.SIZE -> if (sortOrder == MusicSortOrder.ASCENDING) "Smallest" else "Largest"
                        })
                    }
                )
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { idx, label ->
                Tab(selected = selectedTab == idx, onClick = { selectedTab = idx },
                    text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
            }
        }

        when (selectedTab) {
            0 -> MusicFilesList(filteredSongs, layoutMode, displayPrefs, favoriteIds, customPlaylists, context,
                onFavoriteToggle = { favoriteIds = getMusicFavorites(context) },
                onAddToPlaylist = { pl, id -> addSongToPlaylist(context, pl, id) })
            1 -> MusicFoldersList(folders, layoutMode, displayPrefs, favoriteIds, customPlaylists, context,
                onFavoriteToggle = { favoriteIds = getMusicFavorites(context) },
                onAddToPlaylist = { pl, id -> addSongToPlaylist(context, pl, id) })
            2 -> MusicFilesList(favoriteSongs, MusicLayoutMode.LIST, displayPrefs, favoriteIds, customPlaylists, context,
                emptyMessage = "No favourite songs yet.\nLong-press any song to add to favourites.",
                onFavoriteToggle = { favoriteIds = getMusicFavorites(context) },
                onAddToPlaylist = { pl, id -> addSongToPlaylist(context, pl, id) })
            3 -> MusicFilesList(recentSongs, MusicLayoutMode.LIST, displayPrefs, favoriteIds, customPlaylists, context,
                emptyMessage = "No recently played songs.\nPlay some music to see them here.",
                onFavoriteToggle = { favoriteIds = getMusicFavorites(context) },
                onAddToPlaylist = { pl, id -> addSongToPlaylist(context, pl, id) })
            4 -> PlaylistsTab(allSongs, customPlaylists, favoriteIds, displayPrefs, context,
                onCreatePlaylist = { showCreatePlaylist = true },
                onFavoriteToggle = { favoriteIds = getMusicFavorites(context) },
                onAddToPlaylist = { pl, id -> addSongToPlaylist(context, pl, id) },
                onPlaylistsChanged = { customPlaylists = getCustomPlaylists(context) })
        }
    }

    if (showQuickSettings) {
        MusicQuickSettingsDialog(viewMode, layoutMode, sortBy, sortOrder, displayPrefs,
            onViewModeChange = { viewMode = it; selectedTab = when (it) { MusicViewMode.FILES -> 0; else -> 1 } },
            onLayoutModeChange = { layoutMode = it },
            onSortByChange = { sortBy = it },
            onSortOrderChange = { sortOrder = it },
            onDisplayPrefsChange = { displayPrefs = it },
            onDismiss = { showQuickSettings = false })
    }

    if (showCreatePlaylist) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false; newPlaylistName = "" },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(value = newPlaylistName, onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        val updated = (customPlaylists + newPlaylistName.trim()).distinct()
                        saveCustomPlaylists(context, updated)
                        customPlaylists = updated
                    }
                    showCreatePlaylist = false; newPlaylistName = ""
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylist = false; newPlaylistName = "" }) { Text("Cancel") }
            }
        )
    }
}

// ─── Files List ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicFilesList(
    songs: List<MusicItem>,
    layoutMode: MusicLayoutMode,
    displayPrefs: MusicDisplayPrefs,
    favoriteIds: Set<Long>,
    customPlaylists: List<String>,
    context: Context,
    emptyMessage: String = "No songs found.",
    onFavoriteToggle: () -> Unit,
    onAddToPlaylist: (String, Long) -> Unit,
) {
    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp))
        }
        return
    }
    if (layoutMode == MusicLayoutMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(songs, key = { it.id }) { song ->
                MusicGridItem(song, song.id in favoriteIds, displayPrefs, customPlaylists, context, onFavoriteToggle, onAddToPlaylist)
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp), modifier = Modifier.fillMaxSize()) {
            items(songs, key = { it.id }) { song ->
                MusicListItem(song, song.id in favoriteIds, displayPrefs, customPlaylists, context, onFavoriteToggle = onFavoriteToggle, onAddToPlaylist = onAddToPlaylist)
            }
        }
    }
}

// ─── Folders List ─────────────────────────────────────────────────────────────

@Composable
fun MusicFoldersList(
    folders: List<MusicFolder>,
    layoutMode: MusicLayoutMode,
    displayPrefs: MusicDisplayPrefs,
    favoriteIds: Set<Long>,
    customPlaylists: List<String>,
    context: Context,
    onFavoriteToggle: () -> Unit,
    onAddToPlaylist: (String, Long) -> Unit,
) {
    if (folders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No folders found.", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    var expandedFolder by remember { mutableStateOf<String?>(null) }

    LazyColumn(contentPadding = PaddingValues(bottom = 80.dp), modifier = Modifier.fillMaxSize()) {
        folders.forEach { folder ->
            item(key = folder.path) {
                val isExpanded = expandedFolder == folder.path
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                        .clickable { expandedFolder = if (isExpanded) null else folder.path },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isExpanded) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Rounded.Folder, contentDescription = null,
                            modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(folder.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${folder.songs.size} songs · ${if (folder.isOnSdCard) "SD Card" else "Internal Storage"}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(imageVector = if (isExpanded) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                            contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (expandedFolder == folder.path) {
                items(folder.songs, key = { "${folder.path}_${it.id}" }) { song ->
                    MusicListItem(song, song.id in favoriteIds, displayPrefs, customPlaylists, context,
                        modifier = Modifier.padding(start = 16.dp), onFavoriteToggle = onFavoriteToggle, onAddToPlaylist = onAddToPlaylist)
                }
            }
        }
    }
}

// ─── Playlists Tab ────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsTab(
    allSongs: List<MusicItem>,
    customPlaylists: List<String>,
    favoriteIds: Set<Long>,
    displayPrefs: MusicDisplayPrefs,
    context: Context,
    onCreatePlaylist: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onAddToPlaylist: (String, Long) -> Unit,
    onPlaylistsChanged: () -> Unit,
) {
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }

    if (selectedPlaylist != null) {
        val playlist = selectedPlaylist!!
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedPlaylist = null }) {
                    Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(playlist, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            MusicFilesList(getPlaylistSongs(context, playlist, allSongs), MusicLayoutMode.LIST, displayPrefs,
                favoriteIds, emptyList(), context,
                emptyMessage = "No songs in this playlist.\nGo to Files and long-press to add.",
                onFavoriteToggle = onFavoriteToggle, onAddToPlaylist = { _, _ -> })
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("My Playlists", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onCreatePlaylist) {
                Icon(painter = painterResource(coreUiR.drawable.ic_add), contentDescription = "Create Playlist",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (customPlaylists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painter = painterResource(coreUiR.drawable.ic_playlist), contentDescription = null,
                        modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No playlists yet.\nTap + to create a playlist.", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                items(customPlaylists, key = { it }) { playlist ->
                    var showMenu by remember { mutableStateOf(false) }
                    Row(modifier = Modifier.fillMaxWidth()
                        .combinedClickable(onClick = { selectedPlaylist = playlist }, onLongClick = { showMenu = true })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(coreUiR.drawable.ic_playlist), contentDescription = null,
                            modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(playlist, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${getPlaylistSongs(context, playlist, allSongs).size} songs",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Delete Playlist") }, onClick = {
                                    showMenu = false
                                    saveCustomPlaylists(context, customPlaylists.filter { it != playlist })
                                    onPlaylistsChanged()
                                })
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

// ─── Song List Item ───────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicListItem(
    song: MusicItem,
    isFavorite: Boolean,
    displayPrefs: MusicDisplayPrefs,
    customPlaylists: List<String>,
    context: Context,
    modifier: Modifier = Modifier,
    onFavoriteToggle: () -> Unit,
    onAddToPlaylist: (String, Long) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(modifier = modifier.fillMaxWidth()
        .combinedClickable(onClick = { playAudio(context, song) }, onLongClick = { showMenu = true })
        .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (displayPrefs.showAlbumArt) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(coreUiR.drawable.ic_headset), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (displayPrefs.showArtist) {
                Text(song.artist, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (displayPrefs.showDuration) {
                    Text(formatDuration(song.duration), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (displayPrefs.showSize && song.size > 0) {
                    Text(formatFileSize(song.size), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (displayPrefs.showDate && song.dateAdded > 0) {
                    Text(java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                        .format(java.util.Date(song.dateAdded)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (displayPrefs.showPath && song.path.isNotEmpty()) {
                Text(song.path, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = { toggleMusicFavorite(context, song.id); onFavoriteToggle() },
            modifier = Modifier.size(36.dp)) {
            Icon(painter = painterResource(if (isFavorite) coreUiR.drawable.ic_favorite else coreUiR.drawable.ic_favorite_border),
                contentDescription = null,
                tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
        }
        Box {
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Play") }, onClick = { showMenu = false; playAudio(context, song) })
                DropdownMenuItem(
                    text = { Text(if (isFavorite) "Remove from Favourites" else "Add to Favourites") },
                    onClick = { showMenu = false; toggleMusicFavorite(context, song.id); onFavoriteToggle() })
                if (customPlaylists.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Add to Playlist:", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    customPlaylists.forEach { pl ->
                        DropdownMenuItem(text = { Text(pl) }, onClick = { showMenu = false; onAddToPlaylist(pl, song.id) })
                    }
                }
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
}

// ─── Song Grid Item ───────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicGridItem(
    song: MusicItem,
    isFavorite: Boolean,
    displayPrefs: MusicDisplayPrefs,
    customPlaylists: List<String>,
    context: Context,
    onFavoriteToggle: () -> Unit,
    onAddToPlaylist: (String, Long) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()
        .combinedClickable(onClick = { playAudio(context, song) }, onLongClick = { showMenu = true }),
        shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(coreUiR.drawable.ic_headset), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (displayPrefs.showArtist) {
                Text(song.artist, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                if (displayPrefs.showDuration) {
                    Text(formatDuration(song.duration), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { toggleMusicFavorite(context, song.id); onFavoriteToggle() },
                    modifier = Modifier.size(28.dp)) {
                    Icon(painter = painterResource(if (isFavorite) coreUiR.drawable.ic_favorite else coreUiR.drawable.ic_favorite_border),
                        contentDescription = null,
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp))
                }
            }
        }
        Box {
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Play") }, onClick = { showMenu = false; playAudio(context, song) })
                DropdownMenuItem(text = { Text(if (isFavorite) "Remove from Favourites" else "Add to Favourites") },
                    onClick = { showMenu = false; toggleMusicFavorite(context, song.id); onFavoriteToggle() })
                customPlaylists.forEach { pl ->
                    DropdownMenuItem(text = { Text("Add to: $pl") }, onClick = { showMenu = false; onAddToPlaylist(pl, song.id) })
                }
            }
        }
    }
}

// ─── Quick Settings Dialog ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MusicQuickSettingsDialog(
    viewMode: MusicViewMode,
    layoutMode: MusicLayoutMode,
    sortBy: MusicSortBy,
    sortOrder: MusicSortOrder,
    displayPrefs: MusicDisplayPrefs,
    onViewModeChange: (MusicViewMode) -> Unit,
    onLayoutModeChange: (MusicLayoutMode) -> Unit,
    onSortByChange: (MusicSortBy) -> Unit,
    onSortOrderChange: (MusicSortOrder) -> Unit,
    onDisplayPrefsChange: (MusicDisplayPrefs) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick Settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                HorizontalDivider()

                // View Mode
                Text("View Mode", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    MusicViewMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(selected = viewMode == mode, onClick = { onViewModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = MusicViewMode.entries.size)) {
                            Text(when (mode) { MusicViewMode.FILES -> "Files"; MusicViewMode.FOLDERS -> "Folders"; MusicViewMode.TREE -> "Tree" }, maxLines = 1)
                        }
                    }
                }

                // Layout
                Text("Layout", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    MusicLayoutMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(selected = layoutMode == mode, onClick = { onLayoutModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = MusicLayoutMode.entries.size)) {
                            Text(when (mode) { MusicLayoutMode.LIST -> "List"; MusicLayoutMode.GRID -> "Grid" })
                        }
                    }
                }

                // Sort By
                Text("Sort By", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MusicSortBy.entries.forEach { by ->
                        FilterChip(
                            selected = sortBy == by,
                            onClick = {
                                if (sortBy == by) onSortOrderChange(if (sortOrder == MusicSortOrder.ASCENDING) MusicSortOrder.DESCENDING else MusicSortOrder.ASCENDING)
                                else { onSortByChange(by); onSortOrderChange(MusicSortOrder.ASCENDING) }
                            },
                            label = { Text(when (by) { MusicSortBy.TITLE -> "Title"; MusicSortBy.DURATION -> "Duration"; MusicSortBy.DATE -> "Date"; MusicSortBy.SIZE -> "Size" }) },
                            trailingIcon = if (sortBy == by) ({
                                Icon(imageVector = if (sortOrder == MusicSortOrder.ASCENDING) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                    contentDescription = null, modifier = Modifier.size(16.dp))
                            }) else null,
                        )
                    }
                }

                // Sort Order
                Text("Sort Order", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val labels = when (sortBy) {
                        MusicSortBy.TITLE -> listOf("A→Z", "Z→A")
                        MusicSortBy.DURATION -> listOf("Shortest", "Longest")
                        MusicSortBy.DATE -> listOf("Oldest", "Newest")
                        MusicSortBy.SIZE -> listOf("Smallest", "Largest")
                    }
                    labels.forEachIndexed { index, label ->
                        val order = if (index == 0) MusicSortOrder.ASCENDING else MusicSortOrder.DESCENDING
                        SegmentedButton(selected = sortOrder == order, onClick = { onSortOrderChange(order) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)) {
                            Text(label)
                        }
                    }
                }

                // Display Fields
                Text("Display Fields", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = displayPrefs.showAlbumArt, onClick = { onDisplayPrefsChange(displayPrefs.copy(showAlbumArt = !displayPrefs.showAlbumArt)) }, label = { Text("Album Art") })
                    FilterChip(selected = displayPrefs.showArtist, onClick = { onDisplayPrefsChange(displayPrefs.copy(showArtist = !displayPrefs.showArtist)) }, label = { Text("Artist") })
                    FilterChip(selected = displayPrefs.showDuration, onClick = { onDisplayPrefsChange(displayPrefs.copy(showDuration = !displayPrefs.showDuration)) }, label = { Text("Duration") })
                    FilterChip(selected = displayPrefs.showSize, onClick = { onDisplayPrefsChange(displayPrefs.copy(showSize = !displayPrefs.showSize)) }, label = { Text("File Size") })
                    FilterChip(selected = displayPrefs.showDate, onClick = { onDisplayPrefsChange(displayPrefs.copy(showDate = !displayPrefs.showDate)) }, label = { Text("Date Added") })
                    FilterChip(selected = displayPrefs.showPath, onClick = { onDisplayPrefsChange(displayPrefs.copy(showPath = !displayPrefs.showPath)) }, label = { Text("File Path") })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
