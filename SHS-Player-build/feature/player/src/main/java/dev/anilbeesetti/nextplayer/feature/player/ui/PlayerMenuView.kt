package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

@Composable
fun PlayerMenuView(
    modifier: Modifier = Modifier,
    isMirrored: Boolean,
    isFavorite: Boolean,
    onMirrorClick: () -> Unit,
    onAbRepeatClick: () -> Unit,
    onDecoderClick: () -> Unit,
    onEqualizerClick: () -> Unit,
    onVoiceChangerClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onTrimClick: () -> Unit,
    onShareClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onPlaylistClick: () -> Unit,
    onVideoToAudioClick: () -> Unit = {},
    onReversePlayClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .padding(bottom = 24.dp),
    ) {
        MenuItem(
            icon = coreUiR.drawable.ic_mirror,
            title = if (isMirrored) stringResource(coreUiR.string.mirror_on) else stringResource(coreUiR.string.mirror_mode),
            onClick = onMirrorClick,
        )
        MenuItem(
            icon = coreUiR.drawable.ic_ab_repeat,
            title = stringResource(coreUiR.string.ab_repeat),
            onClick = onAbRepeatClick,
        )
        MenuItem(
            icon = coreUiR.drawable.ic_decoder,
            title = stringResource(coreUiR.string.decoder_switch),
            onClick = onDecoderClick,
        )
        MenuItem(
            icon = coreUiR.drawable.ic_equalizer,
            title = stringResource(coreUiR.string.equalizer),
            onClick = onEqualizerClick,
        )
        MenuItem(
            icon = coreUiR.drawable.ic_voice,
            title = stringResource(coreUiR.string.voice_changer),
            onClick = onVoiceChangerClick,
        )
        MenuItem(
            icon = coreUiR.drawable.ic_sleep_timer,
            title = stringResource(coreUiR.string.sleep_timer),
            onClick = onSleepTimerClick,
        )
        MenuItem(
            icon = coreUiR.drawable.ic_bookmark_border,
            title = stringResource(coreUiR.string.bookmarks),
            onClick = onBookmarkClick,
        )
        MenuItem(
            icon = coreUiR.drawable.ic_trim,
            title = stringResource(coreUiR.string.trim_video),
            onClick = onTrimClick,
        )
        MenuItem(
            icon = coreUiR.drawable.ic_headset,
            title = "Convert to Audio",
            onClick = onVideoToAudioClick,
        )
        MenuItem(
            icon = coreUiR.drawable.ic_fast,
            title = "Reverse Play",
            onClick = onReversePlayClick,
        )
        MenuItem(
            icon = coreUiR.drawable.ic_share,
            title = stringResource(coreUiR.string.share_video),
            onClick = onShareClick,
        )
        MenuItem(
            icon = if (isFavorite) coreUiR.drawable.ic_favorite else coreUiR.drawable.ic_favorite_border,
            title = stringResource(coreUiR.string.favorite),
            onClick = onFavoriteClick,
        )
        MenuItem(
            icon = coreUiR.drawable.ic_playlist,
            title = stringResource(coreUiR.string.playlist),
            onClick = onPlaylistClick,
        )
    }
}

@Composable
private fun MenuItem(
    icon: Int,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = title,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
