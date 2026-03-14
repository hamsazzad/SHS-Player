package dev.anilbeesetti.nextplayer.ui

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
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
                val title = cleanMeta(cursor.getString(titleCol), "Unknown Title")
                val artist = cleanMeta(cursor.getString(artistCol), "Unknown Artist")
                val album = cleanMeta(cursor.getString(albumCol), "Unknown Album")
                val duration = cursor.getLong(durCol)
                val size = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(dateCol) * 1000L
                val path = cursor.getString(pathCol) ?: ""
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val file = java.io.File(path)
                val folderPath = file.parent ?: ""
                val folderName = file.parentFile?.name ?: ""
                val isOnSdCard = path.isNotEmpty() && !path.startsWith(internalPath)
                musicList.add(MusicItem(id, title, artist, album, duration, size, dateAdded, path, uri, folderName, folderPath, isOnSdCard))
            }
        }
    } catch (_: Exception) {}
    return musicList
}

fun groupByFolder(songs: List<MusicItem>): List<MusicFolder> =
    songs.groupBy { it.folderPath }
        .map { (path, items) -> MusicFolder(items.first().folderName.ifEmpty { path }, path, items, items.first().isOnSdCard) }
        .sortedBy { it.name.lowercase() }

fun sortSongs(songs: List<MusicItem>, by: MusicSortBy, order: MusicSortOrder): List<MusicItem> {
    val sorted = when (by) {
        MusicSortBy.TITLE -> songs.sortedBy { it.title.lowercase() }
        MusicSortBy.DURATION -> songs.sortedBy { it.duration }
        MusicSortBy.DATE -> songs.sortedBy { it.dateAdded }
        MusicSortBy.SIZE -> songs.sortedBy { it.size }
    }
    return if (order == MusicSortOrder.DESCENDING) sorted.reversed() else sorted
}

fun getMusicPrefs(context: Context): SharedPreferences =
    context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)

fun getMusicFavorites(context: Context): Set<Long> =
    getMusicPrefs(context).getStringSet("favorites", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

fun toggleMusicFavorite(context: Context, id: Long): Boolean {
    val prefs = getMusicPrefs(context)
    val current = getMusicFavorites(context).toMutableSet()
    val isNowFav = if (id in current) { current.remove(id); false } else { current.add(id); true }
    prefs.edit().putStringSet("favorites", current.map { it.toString() }.toSet()).apply()
    return isNowFav
}

fun addBatchMusicFavorites(context: Context, ids: Set<Long>) {
    val prefs = getMusicPrefs(context)
    val current = getMusicFavorites(context).toMutableSet()
    current.addAll(ids)
    prefs.edit().putStringSet("favorites", current.map { it.toString() }.toSet()).apply()
}

fun removeBatchMusicFavorites(context: Context, ids: Set<Long>) {
    val prefs = getMusicPrefs(context)
    val current = getMusicFavorites(context).toMutableSet()
    current.removeAll(ids)
    prefs.edit().putStringSet("favorites", current.map { it.toString() }.toSet()).apply()
}

fun getRecentMusicIds(context: Context): List<Long> =
    getMusicPrefs(context).getString("recent", "")!!
        .split(",").mapNotNull { it.toLongOrNull() }

fun addRecentMusic(context: Context, id: Long) {
    val prefs = getMusicPrefs(context)
    val recent = getRecentMusicIds(context).toMutableList()
    recent.remove(id); recent.add(0, id)
    prefs.edit().putString("recent", recent.take(50).joinToString(",")).apply()
}

fun getCustomPlaylists(context: Context): List<String> =
    getMusicPrefs(context).getStringSet("playlists", emptySet())!!.sorted()

fun saveCustomPlaylists(context: Context, playlists: List<String>) =
    getMusicPrefs(context).edit().putStringSet("playlists", playlists.toSet()).apply()

fun getPlaylistSongs(context: Context, playlistName: String, allSongs: List<MusicItem>): List<MusicItem> {
    val ids = getMusicPrefs(context).getStringSet("playlist_$playlistName", emptySet())!!
        .mapNotNull { it.toLongOrNull() }.toSet()
    return allSongs.filter { it.id in ids }
}

fun addSongToPlaylist(context: Context, playlistName: String, id: Long) {
    val prefs = getMusicPrefs(context)
    val key = "playlist_$playlistName"
    val current = prefs.getStringSet(key, emptySet())!!.toMutableSet()
    current.add(id.toString())
    prefs.edit().putStringSet(key, current).apply()
}

fun addBatchSongsToPlaylist(context: Context, playlistName: String, ids: Set<Long>) {
    val prefs = getMusicPrefs(context)
    val key = "playlist_$playlistName"
    val current = prefs.getStringSet(key, emptySet())!!.toMutableSet()
    ids.forEach { current.add(it.toString()) }
    prefs.edit().putStringSet(key, current).apply()
}

// ─── Playback ─────────────────────────────────────────────────────────────────

fun playAudio(context: Context, item: MusicItem, queue: List<MusicItem> = emptyList()) {
    addRecentMusic(context, item.id)
    val intent = Intent(context, PlayerActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        setDataAndType(item.uri, "audio/*")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (queue.size > 1) {
            putParcelableArrayListExtra("video_list", ArrayList(queue.map { it.uri }))
        }
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
                    Icon(imageVector = if (isSearchActive) NextIcons.Close else NextIcons.Search,
                        contentDescription = if (isSearchActive) "Close Search" else "Search")
                }
                IconButton(onClick = { showQuickSettings = true }) {
                    Icon(imageVector = NextIcons.Settings, contentDescription = "Quick Settings")
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
                leadingIcon = { Icon(NextIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(NextIcons.Close, contentDescription = "Clear")
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
                        Icon(imageVector = if (layoutMode == MusicLayoutMode.LIST) NextIcons.List else NextIcons.GridView,
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
                onAddToPlaylist = { pl, id -> addSongToPlaylist(context, pl, id) },
                onBulkFavoriteAdd = { ids -> addBatchMusicFavorites(context, ids); favoriteIds = getMusicFavorites(context) },
                onBulkFavoriteRemove = { ids -> removeBatchMusicFavorites(context, ids); favoriteIds = getMusicFavorites(context) },
                onBulkAddToPlaylist = { pl, ids -> addBatchSongsToPlaylist(context, pl, ids) })
            1 -> MusicFoldersList(folders, layoutMode, displayPrefs, favoriteIds, customPlaylists, context,
                onFavoriteToggle = { favoriteIds = getMusicFavorites(context) },
                onAddToPlaylist = { pl, id -> addSongToPlaylist(context, pl, id) },
                onBulkFavoriteAdd = { ids -> addBatchMusicFavorites(context, ids); favoriteIds = getMusicFavorites(context) },
                onBulkFavoriteRemove = { ids -> removeBatchMusicFavorites(context, ids); favoriteIds = getMusicFavorites(context) },
                onBulkAddToPlaylist = { pl, ids -> addBatchSongsToPlaylist(context, pl, ids) })
            2 -> MusicFilesList(favoriteSongs, MusicLayoutMode.LIST, displayPrefs, favoriteIds, customPlaylists, context,
                emptyMessage = "No favourite songs yet.\nLong-press any song to add to favourites.",
                onFavoriteToggle = { favoriteIds = getMusicFavorites(context) },
                onAddToPlaylist = { pl, id -> addSongToPlaylist(context, pl, id) },
                onBulkFavoriteAdd = { ids -> addBatchMusicFavorites(context, ids); favoriteIds = getMusicFavorites(context) },
                onBulkFavoriteRemove = { ids -> removeBatchMusicFavorites(context, ids); favoriteIds = getMusicFavorites(context) },
                onBulkAddToPlaylist = { pl, ids -> addBatchSongsToPlaylist(context, pl, ids) })
            3 -> MusicFilesList(recentSongs, MusicLayoutMode.LIST, displayPrefs, favoriteIds, customPlaylists, context,
                emptyMessage = "No recently played songs.\nPlay some music to see them here.",
                onFavoriteToggle = { favoriteIds = getMusicFavorites(context) },
                onAddToPlaylist = { pl, id -> addSongToPlaylist(context, pl, id) },
                onBulkFavoriteAdd = { ids -> addBatchMusicFavorites(context, ids); favoriteIds = getMusicFavorites(context) },
                onBulkFavoriteRemove = { ids -> removeBatchMusicFavorites(context, ids); favoriteIds = getMusicFavorites(context) },
                onBulkAddToPlaylist = { pl, ids -> addBatchSongsToPlaylist(context, pl, ids) })
            4 -> PlaylistsTab(allSongs, customPlaylists, favoriteIds, displayPrefs, context,
                onCreatePlaylist = { showCreatePlaylist = true },
                onFavoriteToggle = { favoriteIds = getMusicFavorites(context) },
                onAddToPlaylist = { pl, id -> addSongToPlaylist(context, pl, id) },
                onBulkFavoriteAdd = { ids -> addBatchMusicFavorites(context, ids); favoriteIds = getMusicFavorites(context) },
                onBulkFavoriteRemove = { ids -> removeBatchMusicFavorites(context, ids); favoriteIds = getMusicFavorites(context) },
                onBulkAddToPlaylist = { pl, ids -> addBatchSongsToPlaylist(context, pl, ids) },
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

// ─── Bulk Action Bar ──────────────────────────────────────────────────────────

@Composable
fun BulkActionBar(
    selectedCount: Int,
    totalCount: Int,
    favoriteIds: Set<Long>,
    selectedIds: Set<Long>,
    customPlaylists: List<String>,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onAddToFavorites: () -> Unit,
    onRemoveFromFavorites: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onDelete: () -> Unit = {},
    onMoveToPrivacyFolder: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val allSelectedAreFav = selectedIds.isNotEmpty() && selectedIds.all { it in favoriteIds }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Songs") },
            text = { Text("Delete $selectedCount selected song(s)? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onClear) {
                    Icon(imageVector = NextIcons.Close, contentDescription = "Clear selection")
                }
                Text(
                    text = "$selectedCount of $totalCount selected",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSelectAll) { Text("All") }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Favorites action
                if (allSelectedAreFav) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(onClick = onRemoveFromFavorites).padding(8.dp)) {
                        Icon(painter = painterResource(coreUiR.drawable.ic_favorite),
                            contentDescription = "Remove from Favourites",
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                        Text("Unfav", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(onClick = onAddToFavorites).padding(8.dp)) {
                        Icon(painter = painterResource(coreUiR.drawable.ic_favorite_border),
                            contentDescription = "Add to Favourites",
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Text("Favourite", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Privacy Folder action
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(onClick = onMoveToPrivacyFolder).padding(8.dp)) {
                    Icon(imageVector = NextIcons.Lock,
                        contentDescription = "Move to Privacy Folder",
                        tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                    Text("Privacy", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary)
                }

                // Delete action
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(onClick = { showDeleteConfirm = true }).padding(8.dp)) {
                    Icon(imageVector = NextIcons.Delete,
                        contentDescription = "Delete selected",
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                    Text("Delete", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
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
    onBulkFavoriteAdd: (Set<Long>) -> Unit = {},
    onBulkFavoriteRemove: (Set<Long>) -> Unit = {},
    onBulkAddToPlaylist: (String, Set<Long>) -> Unit = { _, _ -> },
) {
    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp))
        }
        return
    }

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    BackHandler(enabled = isSelectionMode) { selectedIds = emptySet() }

    Box(modifier = Modifier.fillMaxSize()) {
        val listBottomPad = if (isSelectionMode) 140.dp else 80.dp

        if (layoutMode == MusicLayoutMode.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = listBottomPad),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(songs, key = { it.id }) { song ->
                    MusicGridItem(
                        song = song,
                        isFavorite = song.id in favoriteIds,
                        isSelected = song.id in selectedIds,
                        isSelectionMode = isSelectionMode,
                        displayPrefs = displayPrefs,
                        customPlaylists = customPlaylists,
                        context = context,
                        queue = songs,
                        onFavoriteToggle = onFavoriteToggle,
                        onAddToPlaylist = onAddToPlaylist,
                        onSelectionToggle = {
                            selectedIds = if (song.id in selectedIds) selectedIds - song.id else selectedIds + song.id
                        },
                        onLongPress = { selectedIds = selectedIds + song.id },
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = listBottomPad),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(songs, key = { it.id }) { song ->
                    MusicListItem(
                        song = song,
                        isFavorite = song.id in favoriteIds,
                        isSelected = song.id in selectedIds,
                        isSelectionMode = isSelectionMode,
                        displayPrefs = displayPrefs,
                        customPlaylists = customPlaylists,
                        context = context,
                        queue = songs,
                        onFavoriteToggle = onFavoriteToggle,
                        onAddToPlaylist = onAddToPlaylist,
                        onSelectionToggle = {
                            selectedIds = if (song.id in selectedIds) selectedIds - song.id else selectedIds + song.id
                        },
                        onLongPress = { selectedIds = selectedIds + song.id },
                    )
                }
            }
        }

        // Bulk action bar
        AnimatedVisibility(
            visible = isSelectionMode,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BulkActionBar(
                selectedCount = selectedIds.size,
                totalCount = songs.size,
                favoriteIds = favoriteIds,
                selectedIds = selectedIds,
                customPlaylists = customPlaylists,
                onSelectAll = { selectedIds = songs.map { it.id }.toSet() },
                onClear = { selectedIds = emptySet() },
                onAddToFavorites = { onBulkFavoriteAdd(selectedIds); selectedIds = emptySet() },
                onRemoveFromFavorites = { onBulkFavoriteRemove(selectedIds); selectedIds = emptySet() },
                onAddToPlaylist = { pl -> onBulkAddToPlaylist(pl, selectedIds); selectedIds = emptySet() },
                onDelete = {
                    val toDelete = songs.filter { it.id in selectedIds }
                    toDelete.forEach { s ->
                        runCatching {
                            val uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, s.id)
                            context.contentResolver.delete(uri, null, null)
                        }
                    }
                    selectedIds = emptySet()
                },
                onMoveToPrivacyFolder = {
                    val urisToMove = songs.filter { it.id in selectedIds }.map {
                        android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it.id)
                    }
                    selectedIds = emptySet()
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        urisToMove.forEach { uri -> moveToVault(context, uri, "music") }
                    }
                },
            )
        }
    }
}

// ─── Folders List (navigates into folder) ─────────────────────────────────────

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
    onBulkFavoriteAdd: (Set<Long>) -> Unit = {},
    onBulkFavoriteRemove: (Set<Long>) -> Unit = {},
    onBulkAddToPlaylist: (String, Set<Long>) -> Unit = { _, _ -> },
) {
    if (folders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No folders found.", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var currentFolder by remember { mutableStateOf<MusicFolder?>(null) }

    // Folder detail view (navigated into)
    if (currentFolder != null) {
        val folder = currentFolder!!
        BackHandler { currentFolder = null }
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { currentFolder = null }) {
                    Icon(imageVector = NextIcons.ArrowBack, contentDescription = "Back to Folders")
                }
                Icon(
                    imageVector = NextIcons.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(folder.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${folder.songs.size} songs · ${if (folder.isOnSdCard) "SD Card" else "Internal Storage"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
            MusicFilesList(
                songs = folder.songs,
                layoutMode = layoutMode,
                displayPrefs = displayPrefs,
                favoriteIds = favoriteIds,
                customPlaylists = customPlaylists,
                context = context,
                emptyMessage = "No songs in this folder.",
                onFavoriteToggle = onFavoriteToggle,
                onAddToPlaylist = onAddToPlaylist,
                onBulkFavoriteAdd = onBulkFavoriteAdd,
                onBulkFavoriteRemove = onBulkFavoriteRemove,
                onBulkAddToPlaylist = onBulkAddToPlaylist,
            )
        }
        return
    }

    // Folder list view
    LazyColumn(contentPadding = PaddingValues(bottom = 80.dp), modifier = Modifier.fillMaxSize()) {
        items(folders, key = { it.path }) { folder ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clickable { currentFolder = folder },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = NextIcons.Folder, contentDescription = null,
                        modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(folder.name, style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${folder.songs.size} songs · ${if (folder.isOnSdCard) "SD Card" else "Internal Storage"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = NextIcons.ChevronRight,
                        contentDescription = "Open folder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
    onBulkFavoriteAdd: (Set<Long>) -> Unit = {},
    onBulkFavoriteRemove: (Set<Long>) -> Unit = {},
    onBulkAddToPlaylist: (String, Set<Long>) -> Unit = { _, _ -> },
    onPlaylistsChanged: () -> Unit,
) {
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }
    var showAddSongsDialog by remember { mutableStateOf(false) }

    if (selectedPlaylist != null) {
        val playlist = selectedPlaylist!!
        val playlistSongs = getPlaylistSongs(context, playlist, allSongs)

        BackHandler { selectedPlaylist = null }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { selectedPlaylist = null }) {
                        Icon(imageVector = NextIcons.ArrowBack, contentDescription = "Back")
                    }
                    Text(playlist, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Add Music button
                    IconButton(onClick = { showAddSongsDialog = true }) {
                        Icon(painter = painterResource(coreUiR.drawable.ic_add),
                            contentDescription = "Add Songs to Playlist",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
                HorizontalDivider()
                MusicFilesList(
                    songs = playlistSongs,
                    layoutMode = MusicLayoutMode.LIST,
                    displayPrefs = displayPrefs,
                    favoriteIds = favoriteIds,
                    customPlaylists = emptyList(),
                    context = context,
                    emptyMessage = "No songs in this playlist.\nTap + to add songs.",
                    onFavoriteToggle = onFavoriteToggle,
                    onAddToPlaylist = { _, _ -> },
                    onBulkFavoriteAdd = onBulkFavoriteAdd,
                    onBulkFavoriteRemove = onBulkFavoriteRemove,
                    onBulkAddToPlaylist = { _, _ -> },
                )
            }

            // FAB to add songs
            FloatingActionButton(
                onClick = { showAddSongsDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(painter = painterResource(coreUiR.drawable.ic_add),
                    contentDescription = "Add Music",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        if (showAddSongsDialog) {
            AddSongsToPlaylistDialog(
                allSongs = allSongs,
                alreadyInPlaylist = playlistSongs.map { it.id }.toSet(),
                onDismiss = { showAddSongsDialog = false },
                onAdd = { ids ->
                    addBatchSongsToPlaylist(context, playlist, ids)
                    onPlaylistsChanged()
                    showAddSongsDialog = false
                },
            )
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
                    val songCount = getPlaylistSongs(context, playlist, allSongs).size
                    Row(modifier = Modifier.fillMaxWidth()
                        .combinedClickable(onClick = { selectedPlaylist = playlist }, onLongClick = { showMenu = true })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(coreUiR.drawable.ic_playlist), contentDescription = null,
                            modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(playlist, style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$songCount songs", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(imageVector = NextIcons.ChevronRight, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box {
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Delete Playlist") }, onClick = {
                                    showMenu = false
                                    val updated = customPlaylists.filter { it != playlist }
                                    saveCustomPlaylists(context, updated)
                                    getMusicPrefs(context).edit().remove("playlist_$playlist").apply()
                                    onPlaylistsChanged()
                                })
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                }
            }
        }
    }
}

// ─── Add Songs to Playlist Dialog ─────────────────────────────────────────────

@Composable
fun AddSongsToPlaylistDialog(
    allSongs: List<MusicItem>,
    alreadyInPlaylist: Set<Long>,
    onDismiss: () -> Unit,
    onAdd: (Set<Long>) -> Unit,
) {
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val availableSongs = remember(allSongs, alreadyInPlaylist) { allSongs.filter { it.id !in alreadyInPlaylist } }
    var searchQuery by remember { mutableStateOf("") }
    val filteredSongs = remember(availableSongs, searchQuery) {
        if (searchQuery.isBlank()) availableSongs
        else availableSongs.filter { it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add Songs", modifier = Modifier.weight(1f))
                    if (selectedIds.isNotEmpty()) {
                        Text("${selectedIds.size} selected",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search songs...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(NextIcons.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(NextIcons.Close, contentDescription = null)
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        text = {
            if (filteredSongs.isEmpty()) {
                Text(
                    if (availableSongs.isEmpty()) "All songs are already in this playlist."
                    else "No matching songs found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedIds = if (selectedIds.size == filteredSongs.size) emptySet()
                                else filteredSongs.map { it.id }.toSet()
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selectedIds.size == filteredSongs.size && filteredSongs.isNotEmpty(),
                                onCheckedChange = {
                                    selectedIds = if (it) filteredSongs.map { s -> s.id }.toSet() else emptySet()
                                },
                            )
                            Text("Select All", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider()
                    }
                    items(filteredSongs, key = { it.id }) { song ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedIds = if (song.id in selectedIds) selectedIds - song.id else selectedIds + song.id
                            }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = song.id in selectedIds,
                                onCheckedChange = {
                                    selectedIds = if (it) selectedIds + song.id else selectedIds - song.id
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(formatDuration(song.duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(selectedIds) },
                enabled = selectedIds.isNotEmpty(),
            ) { Text("Add ${if (selectedIds.isNotEmpty()) "(${selectedIds.size})" else ""}") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ─── List Item ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicListItem(
    song: MusicItem,
    isFavorite: Boolean,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    displayPrefs: MusicDisplayPrefs,
    customPlaylists: List<String>,
    context: Context,
    queue: List<MusicItem> = emptyList(),
    modifier: Modifier = Modifier,
    onFavoriteToggle: () -> Unit,
    onAddToPlaylist: (String, Long) -> Unit,
    onSelectionToggle: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(song.title) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val songUri = ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
    val listItemScope = androidx.compose.runtime.rememberCoroutineScope()
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onSelectionToggle()
                    else playAudio(context, song, queue.ifEmpty { listOf(song) })
                },
                onLongClick = {
                    if (isSelectionMode) onSelectionToggle()
                    else showMenu = true
                },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectionToggle() },
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
        } else if (displayPrefs.showAlbumArt) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
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
                    Text(
                        java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                            .format(java.util.Date(song.dateAdded)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (displayPrefs.showPath && song.path.isNotEmpty()) {
                Text(song.path, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!isSelectionMode) {
            IconButton(onClick = { toggleMusicFavorite(context, song.id); onFavoriteToggle() },
                modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(if (isFavorite) coreUiR.drawable.ic_favorite else coreUiR.drawable.ic_favorite_border),
                    contentDescription = null,
                    tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box {
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Select") }, onClick = {
                        showMenu = false; onLongPress()
                    })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Play") }, onClick = {
                        showMenu = false; playAudio(context, song, queue.ifEmpty { listOf(song) })
                    })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = {
                        showMenu = false; renameText = song.title; showRenameDialog = true
                    })
                    DropdownMenuItem(text = { Text("Share") }, onClick = {
                        showMenu = false
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "audio/*"
                            putExtra(android.content.Intent.EXTRA_STREAM, songUri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share Music"))
                    })
                    DropdownMenuItem(text = { Text("Info") }, onClick = {
                        showMenu = false; showInfoDialog = true
                    })
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = {
                        showMenu = false; showDeleteDialog = true
                    })
                    DropdownMenuItem(
                        text = { Text(if (isFavorite) "Remove from Favourites" else "Add to Favourites") },
                        onClick = { showMenu = false; toggleMusicFavorite(context, song.id); onFavoriteToggle() },
                    )
                    DropdownMenuItem(
                        text = { Text("Move to Privacy Folder") },
                        onClick = {
                            showMenu = false
                            listItemScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                moveToVault(context, songUri, "music")
                            }
                        },
                    )
                    if (customPlaylists.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Add to Playlist:", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        customPlaylists.forEach { pl ->
                            DropdownMenuItem(text = { Text(pl) },
                                onClick = { showMenu = false; onAddToPlaylist(pl, song.id) })
                        }
                    }
                }
            }
        }
    }
// Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Song") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val values = ContentValues().apply {
                        put(android.provider.MediaStore.Audio.Media.TITLE, renameText)
                        put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, renameText)
                    }
                    context.contentResolver.update(songUri, values, null, null)
                    showRenameDialog = false
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } },
        )
    }
    // Info Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Song Info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Title: ${song.title}", style = MaterialTheme.typography.bodyMedium)
                    Text("Artist: ${song.artist}", style = MaterialTheme.typography.bodyMedium)
                    if (song.album.isNotEmpty()) Text("Album: ${song.album}", style = MaterialTheme.typography.bodyMedium)
                    Text("Duration: ${formatDuration(song.duration)}", style = MaterialTheme.typography.bodyMedium)
                    if (song.size > 0) Text("Size: ${formatFileSize(song.size)}", style = MaterialTheme.typography.bodyMedium)
                    if (song.path.isNotEmpty()) Text("Path: ${song.path}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("OK") } },
        )
    }
    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Song") },
            text = { Text("Are you sure you want to delete \"${song.title}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    context.contentResolver.delete(songUri, null, null)
                    showDeleteDialog = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
}

// ─── Song Grid Item ───────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicGridItem(
    song: MusicItem,
    isFavorite: Boolean,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    displayPrefs: MusicDisplayPrefs,
    customPlaylists: List<String>,
    context: Context,
    queue: List<MusicItem> = emptyList(),
    onFavoriteToggle: () -> Unit,
    onAddToPlaylist: (String, Long) -> Unit,
    onSelectionToggle: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(song.title) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val songUri = ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
    val gridItemScope = androidx.compose.runtime.rememberCoroutineScope()
    val borderMod = if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
    else Modifier

    Card(
        modifier = Modifier.fillMaxWidth().then(borderMod)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onSelectionToggle()
                    else playAudio(context, song, queue.ifEmpty { listOf(song) })
                },
                onLongClick = {
                    if (isSelectionMode) onSelectionToggle()
                    else showMenu = true
                },
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painter = painterResource(coreUiR.drawable.ic_headset), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
                if (isSelected) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        Box(
                            modifier = Modifier.padding(4.dp).size(20.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(imageVector = NextIcons.Check, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
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
                if (!isSelectionMode) {
                    IconButton(onClick = { toggleMusicFavorite(context, song.id); onFavoriteToggle() },
                        modifier = Modifier.size(28.dp)) {
                        Icon(
                            painter = painterResource(if (isFavorite) coreUiR.drawable.ic_favorite else coreUiR.drawable.ic_favorite_border),
                            contentDescription = null,
                            tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        if (!isSelectionMode) {
            Box {
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Select") }, onClick = {
                        showMenu = false; onLongPress()
                    })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Play") }, onClick = {
                        showMenu = false; playAudio(context, song, queue.ifEmpty { listOf(song) })
                    })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = {
                        showMenu = false; renameText = song.title; showRenameDialog = true
                    })
                    DropdownMenuItem(text = { Text("Share") }, onClick = {
                        showMenu = false
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "audio/*"
                            putExtra(android.content.Intent.EXTRA_STREAM, songUri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share Music"))
                    })
                    DropdownMenuItem(text = { Text("Info") }, onClick = {
                        showMenu = false; showInfoDialog = true
                    })
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = {
                        showMenu = false; showDeleteDialog = true
                    })
                    DropdownMenuItem(
                        text = { Text(if (isFavorite) "Remove from Favourites" else "Add to Favourites") },
                        onClick = { showMenu = false; toggleMusicFavorite(context, song.id); onFavoriteToggle() },
                    )
                    DropdownMenuItem(
                        text = { Text("Move to Privacy Folder") },
                        onClick = {
                            showMenu = false
                            gridItemScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                moveToVault(context, songUri, "music")
                            }
                        },
                    )
                    if (customPlaylists.isNotEmpty()) {
                        customPlaylists.forEach { pl ->
                            DropdownMenuItem(text = { Text("Add to: $pl") },
                                onClick = { showMenu = false; onAddToPlaylist(pl, song.id) })
                        }
                    }
                }
            }
        }
    }
// Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Song") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val values = ContentValues().apply {
                        put(android.provider.MediaStore.Audio.Media.TITLE, renameText)
                        put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, renameText)
                    }
                    context.contentResolver.update(songUri, values, null, null)
                    showRenameDialog = false
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } },
        )
    }
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Song Info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Title: ${song.title}", style = MaterialTheme.typography.bodyMedium)
                    Text("Artist: ${song.artist}", style = MaterialTheme.typography.bodyMedium)
                    if (song.album.isNotEmpty()) Text("Album: ${song.album}", style = MaterialTheme.typography.bodyMedium)
                    Text("Duration: ${formatDuration(song.duration)}", style = MaterialTheme.typography.bodyMedium)
                    if (song.size > 0) Text("Size: ${formatFileSize(song.size)}", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("OK") } },
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Song") },
            text = { Text("Are you sure you want to delete \"${song.title}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    context.contentResolver.delete(songUri, null, null)
                    showDeleteDialog = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
        )
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
        title = { Text("Music Settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Sort By
                Text("Sort By", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp))
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MusicSortBy.entries.forEach { by ->
                        FilterChip(
                            selected = sortBy == by,
                            onClick = {
                                if (sortBy == by) onSortOrderChange(if (sortOrder == MusicSortOrder.ASCENDING) MusicSortOrder.DESCENDING else MusicSortOrder.ASCENDING)
                                else { onSortByChange(by); onSortOrderChange(MusicSortOrder.ASCENDING) }
                            },
                            label = {
                                Text(when (by) {
                                    MusicSortBy.TITLE -> "Title"; MusicSortBy.DURATION -> "Duration"
                                    MusicSortBy.DATE -> "Date"; MusicSortBy.SIZE -> "Size"
                                })
                            },
                            trailingIcon = if (sortBy == by) ({
                                Icon(
                                    imageVector = if (sortOrder == MusicSortOrder.ASCENDING) NextIcons.ArrowUpward else NextIcons.ArrowDownward,
                                    contentDescription = null, modifier = Modifier.size(16.dp),
                                )
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
                        SegmentedButton(
                            selected = sortOrder == order,
                            onClick = { onSortOrderChange(order) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        ) { Text(label) }
                    }
                }

                // Display Fields
                Text("Display Fields", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = displayPrefs.showAlbumArt,
                        onClick = { onDisplayPrefsChange(displayPrefs.copy(showAlbumArt = !displayPrefs.showAlbumArt)) },
                        label = { Text("Album Art") })
                    FilterChip(selected = displayPrefs.showArtist,
                        onClick = { onDisplayPrefsChange(displayPrefs.copy(showArtist = !displayPrefs.showArtist)) },
                        label = { Text("Artist") })
                    FilterChip(selected = displayPrefs.showDuration,
                        onClick = { onDisplayPrefsChange(displayPrefs.copy(showDuration = !displayPrefs.showDuration)) },
                        label = { Text("Duration") })
                    FilterChip(selected = displayPrefs.showSize,
                        onClick = { onDisplayPrefsChange(displayPrefs.copy(showSize = !displayPrefs.showSize)) },
                        label = { Text("File Size") })
                    FilterChip(selected = displayPrefs.showDate,
                        onClick = { onDisplayPrefsChange(displayPrefs.copy(showDate = !displayPrefs.showDate)) },
                        label = { Text("Date Added") })
                    FilterChip(selected = displayPrefs.showPath,
                        onClick = { onDisplayPrefsChange(displayPrefs.copy(showPath = !displayPrefs.showPath)) },
                        label = { Text("File Path") })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
