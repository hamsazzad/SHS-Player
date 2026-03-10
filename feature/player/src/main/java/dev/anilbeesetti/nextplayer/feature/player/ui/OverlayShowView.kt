package dev.anilbeesetti.nextplayer.feature.player.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.feature.player.extensions.noRippleClickable

@Composable
fun BoxScope.OverlayShowView(
    modifier: Modifier = Modifier,
    player: Player,
    overlayView: OverlayView?,
    videoContentScale: VideoContentScale,
    isMirrored: Boolean = false,
    isFavorite: Boolean = false,
    isHardwareDecoder: Boolean = true,
    abPointA: Long? = null,
    abPointB: Long? = null,
    currentPosition: Long = 0L,
    voiceEffect: VoiceEffect = VoiceEffect.NORMAL,
    eqBrightness: Float = 1f,
    eqContrast: Float = 1f,
    eqSaturation: Float = 1f,
    bookmarks: List<BookmarkItem> = emptyList(),
    playlistItems: List<PlaylistItem> = emptyList(),
    onDismiss: () -> Unit = {},
    onSelectSubtitleClick: () -> Unit = {},
    onVideoContentScaleChanged: (VideoContentScale) -> Unit = {},
    onMirrorClick: () -> Unit = {},
    onAbRepeatClick: () -> Unit = {},
    onDecoderClick: () -> Unit = {},
    onEqualizerClick: () -> Unit = {},
    onVoiceChangerClick: () -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    onBookmarkClick: () -> Unit = {},
    onTrimClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    onPlaylistClick: () -> Unit = {},
    onVideoToAudioClick: () -> Unit = {},
    onReversePlayClick: () -> Unit = {},
    onAbSetA: () -> Unit = {},
    onAbSetB: () -> Unit = {},
    onAbClear: () -> Unit = {},
    onSelectHardwareDecoder: () -> Unit = {},
    onSelectSoftwareDecoder: () -> Unit = {},
    onVoiceEffectSelect: (VoiceEffect) -> Unit = {},
    onSleepTimerSelect: (Int) -> Unit = {},
    onSleepTimerCancel: () -> Unit = {},
    onEqBrightnessChange: (Float) -> Unit = {},
    onEqContrastChange: (Float) -> Unit = {},
    onEqSaturationChange: (Float) -> Unit = {},
    onEqReset: () -> Unit = {},
    onAddBookmark: () -> Unit = {},
    onSeekToBookmark: (Long) -> Unit = {},
    onDeleteBookmark: (Long) -> Unit = {},
    onPlaylistItemClick: (Int) -> Unit = {},
) {
    Box(
        modifier = modifier
            .matchParentSize()
            .then(
                if (overlayView != null) {
                    Modifier.noRippleClickable(onClick = onDismiss)
                } else {
                    Modifier
                },
            ),
    )

    AudioTrackSelectorView(
        show = overlayView == OverlayView.AUDIO_SELECTOR,
        player = player,
        onDismiss = onDismiss,
    )

    SubtitleSelectorView(
        show = overlayView == OverlayView.SUBTITLE_SELECTOR,
        player = player,
        onSelectSubtitleClick = onSelectSubtitleClick,
        onDismiss = onDismiss,
    )

    PlaybackSpeedSelectorView(
        show = overlayView == OverlayView.PLAYBACK_SPEED,
        player = player,
    )

    VideoContentScaleSelectorView(
        show = overlayView == OverlayView.VIDEO_CONTENT_SCALE,
        videoContentScale = videoContentScale,
        onVideoContentScaleChanged = onVideoContentScaleChanged,
        onDismiss = onDismiss,
    )

    OverlayView(
        show = overlayView == OverlayView.PLAYER_MENU,
        title = stringResource(coreUiR.string.more_options),
    ) {
        PlayerMenuView(
            isMirrored = isMirrored,
            isFavorite = isFavorite,
            onMirrorClick = { onMirrorClick(); onDismiss() },
            onAbRepeatClick = { onDismiss(); onAbRepeatClick() },
            onDecoderClick = { onDismiss(); onDecoderClick() },
            onEqualizerClick = { onDismiss(); onEqualizerClick() },
            onVoiceChangerClick = { onDismiss(); onVoiceChangerClick() },
            onSleepTimerClick = { onDismiss(); onSleepTimerClick() },
            onBookmarkClick = { onDismiss(); onBookmarkClick() },
            onTrimClick = { onTrimClick(); onDismiss() },
            onShareClick = { onShareClick(); onDismiss() },
            onFavoriteClick = { onFavoriteClick(); onDismiss() },
            onPlaylistClick = { onDismiss(); onPlaylistClick() },
            onVideoToAudioClick = { onVideoToAudioClick(); onDismiss() },
            onReversePlayClick = { onReversePlayClick(); onDismiss() },
        )
    }

    OverlayView(
        show = overlayView == OverlayView.AB_REPEAT,
        title = stringResource(coreUiR.string.ab_repeat),
    ) {
        AbRepeatView(
            pointA = abPointA,
            pointB = abPointB,
            currentPosition = currentPosition,
            onSetA = onAbSetA,
            onSetB = onAbSetB,
            onClear = onAbClear,
        )
    }

    OverlayView(
        show = overlayView == OverlayView.DECODER_SELECTOR,
        title = stringResource(coreUiR.string.decoder_switch),
    ) {
        DecoderSelectorView(
            isHardware = isHardwareDecoder,
            onSelectHardware = { onSelectHardwareDecoder(); onDismiss() },
            onSelectSoftware = { onSelectSoftwareDecoder(); onDismiss() },
        )
    }

    OverlayView(
        show = overlayView == OverlayView.VOICE_CHANGER,
        title = stringResource(coreUiR.string.voice_changer),
    ) {
        VoiceChangerView(
            currentEffect = voiceEffect,
            onSelectEffect = { onVoiceEffectSelect(it); onDismiss() },
        )
    }

    OverlayView(
        show = overlayView == OverlayView.SLEEP_TIMER,
        title = stringResource(coreUiR.string.sleep_timer),
    ) {
        SleepTimerView(
            onSelectMinutes = { onSleepTimerSelect(it); onDismiss() },
            onCancel = { onSleepTimerCancel(); onDismiss() },
        )
    }

    OverlayView(
        show = overlayView == OverlayView.EQUALIZER,
        title = stringResource(coreUiR.string.equalizer),
    ) {
        EqualizerView(
            brightness = eqBrightness,
            contrast = eqContrast,
            saturation = eqSaturation,
            onBrightnessChange = onEqBrightnessChange,
            onContrastChange = onEqContrastChange,
            onSaturationChange = onEqSaturationChange,
            onReset = onEqReset,
        )
    }

    OverlayView(
        show = overlayView == OverlayView.BOOKMARKS,
        title = stringResource(coreUiR.string.bookmarks),
    ) {
        BookmarkView(
            bookmarks = bookmarks,
            onAddBookmark = onAddBookmark,
            onSeekToBookmark = { onSeekToBookmark(it); onDismiss() },
            onDeleteBookmark = onDeleteBookmark,
        )
    }

    OverlayView(
        show = overlayView == OverlayView.PLAYLIST_PANEL,
        title = stringResource(coreUiR.string.playlist),
    ) {
        PlaylistPanelView(
            items = playlistItems,
            onItemClick = { onPlaylistItemClick(it); onDismiss() },
        )
    }
}

val Configuration.isPortrait: Boolean
    get() = orientation == Configuration.ORIENTATION_PORTRAIT

enum class OverlayView {
    AUDIO_SELECTOR,
    SUBTITLE_SELECTOR,
    PLAYBACK_SPEED,
    VIDEO_CONTENT_SCALE,
    PLAYER_MENU,
    AB_REPEAT,
    DECODER_SELECTOR,
    VOICE_CHANGER,
    SLEEP_TIMER,
    EQUALIZER,
    BOOKMARKS,
    PLAYLIST_PANEL,
}
