package dev.anilbeesetti.nextplayer.feature.player

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.database.dao.BookmarkDao
import dev.anilbeesetti.nextplayer.core.database.dao.FavoriteDao
import dev.anilbeesetti.nextplayer.core.database.entities.BookmarkEntity
import dev.anilbeesetti.nextplayer.core.database.entities.FavoriteEntity
import dev.anilbeesetti.nextplayer.core.domain.GetSortedPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.model.LoopMode
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.feature.player.state.VideoZoomEvent
import dev.anilbeesetti.nextplayer.feature.player.ui.BookmarkItem
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    private val getSortedPlaylistUseCase: GetSortedPlaylistUseCase,
    private val bookmarkDao: BookmarkDao,
    private val favoriteDao: FavoriteDao,
) : ViewModel() {

    var playWhenReady: Boolean = true

    private val internalUiState = MutableStateFlow(
        PlayerUiState(
            playerPreferences = preferencesRepository.playerPreferences.value,
        ),
    )
    val uiState = internalUiState.asStateFlow()

    private var currentVideoUri = MutableStateFlow("")
    private var sleepTimerJob: Job? = null

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val bookmarks: Flow<List<BookmarkItem>> = currentVideoUri
        .flatMapLatest { uri ->
            if (uri.isEmpty()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                bookmarkDao.getBookmarksForVideo(uri).map { list ->
                    list.map { BookmarkItem(id = it.id, position = it.position, label = it.label) }
                }
            }
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val isFavorite: Flow<Boolean> = currentVideoUri
        .flatMapLatest { uri ->
            if (uri.isEmpty()) {
                kotlinx.coroutines.flow.flowOf(false)
            } else {
                favoriteDao.isFavorite(uri)
            }
        }

    init {
        viewModelScope.launch {
            preferencesRepository.playerPreferences.collect { prefs ->
                internalUiState.update { it.copy(playerPreferences = prefs) }
            }
        }
    }

    fun setCurrentVideoUri(uri: String) {
        currentVideoUri.value = uri
    }

    suspend fun getPlaylistFromUri(uri: Uri): List<Video> {
        return getSortedPlaylistUseCase.invoke(uri)
    }

    fun updateVideoZoom(uri: String, zoom: Float) {
        viewModelScope.launch {
            mediaRepository.updateMediumZoom(uri, zoom)
        }
    }

    fun updatePlayerBrightness(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(playerBrightness = value) }
        }
    }

    fun updateVideoContentScale(contentScale: VideoContentScale) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(playerVideoZoom = contentScale) }
        }
    }

    fun setLoopMode(loopMode: LoopMode) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(loopMode = loopMode) }
        }
    }

    fun onVideoZoomEvent(event: VideoZoomEvent) {
        when (event) {
            is VideoZoomEvent.ContentScaleChanged -> {
                updateVideoContentScale(event.contentScale)
            }
            is VideoZoomEvent.ZoomChanged -> {
                updateVideoZoom(event.mediaItem.mediaId, event.zoom)
            }
        }
    }

    fun addBookmark(position: Long) {
        viewModelScope.launch {
            val label = "Bookmark at ${formatMs(position)}"
            bookmarkDao.insertBookmark(
                BookmarkEntity(
                    videoUri = currentVideoUri.value,
                    position = position,
                    label = label,
                ),
            )
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            bookmarkDao.deleteBookmark(id)
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val uri = currentVideoUri.value
            if (uri.isNotEmpty()) {
                val isFav = favoriteDao.isFavorite(uri).first()
                if (isFav) {
                    favoriteDao.removeFavorite(uri)
                } else {
                    favoriteDao.addFavorite(FavoriteEntity(videoUri = uri))
                }
            }
        }
    }

    fun startSleepTimer(minutes: Int, player: Player) {
        sleepTimerJob?.cancel()
        sleepTimerJob = viewModelScope.launch {
            delay(minutes * 60 * 1000L)
            player.pause()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
    }

    private fun formatMs(ms: Long): String {
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
}

@Stable
data class PlayerUiState(
    val playerPreferences: PlayerPreferences? = null,
)

sealed interface PlayerEvent
