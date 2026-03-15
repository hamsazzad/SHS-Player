package dev.anilbeesetti.nextplayer.feature.player.state

import android.media.audiofx.Equalizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.session.MediaController
import dev.anilbeesetti.nextplayer.feature.player.service.getAudioSessionId

@Composable
fun rememberAudioEqualizerState(player: Player?): AudioEqualizerState {
    val state = remember { AudioEqualizerState() }

    LaunchedEffect(player) {
        if (player is MediaController) {
            val sessionId = player.getAudioSessionId()
            if (sessionId != 0) state.initialize(sessionId)
            player.listen { events ->
                if (events.contains(Player.EVENT_AUDIO_SESSION_ID)) {
                    val newId = player.getAudioSessionId()
                    if (newId != 0) state.initialize(newId)
                }
            }
        }
    }

    DisposableEffect(Unit) { onDispose { state.release() } }
    return state
}

@Stable
class AudioEqualizerState {
    private var equalizer: Equalizer? = null

    var isReady by mutableStateOf(false)
        private set
    var bandCount by mutableIntStateOf(0)
        private set
    var bandLevels by mutableStateOf<List<Int>>(emptyList())
        private set
    var bandFrequencies by mutableStateOf<List<String>>(emptyList())
        private set
    var minLevel: Int = -1500
        private set
    var maxLevel: Int = 1500
        private set

    fun initialize(audioSessionId: Int) {
        release()
        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            eq.enabled = true
            val count = eq.numberOfBands.toInt()
            bandCount = count
            val range = eq.bandLevelRange
            minLevel = range[0].toInt()
            maxLevel = range[1].toInt()
            bandLevels = (0 until count).map { eq.getBandLevel(it.toShort()).toInt() }
            bandFrequencies = (0 until count).map { i ->
                val freqHz = eq.getCenterFreq(i.toShort()) / 1000
                if (freqHz >= 1000) "${freqHz / 1000}kHz" else "${freqHz}Hz"
            }
            isReady = true
        } catch (e: Exception) {
            e.printStackTrace()
            isReady = false
        }
    }

    fun setBandLevel(bandIndex: Int, level: Int) {
        val eq = equalizer ?: return
        try {
            eq.setBandLevel(bandIndex.toShort(), level.toShort())
            bandLevels = bandLevels.toMutableList().also { it[bandIndex] = level }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetBands() {
        val eq = equalizer ?: return
        try {
            repeat(bandCount) { i -> eq.setBandLevel(i.toShort(), 0) }
            bandLevels = List(bandCount) { 0 }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
        isReady = false
        bandCount = 0
        bandLevels = emptyList()
        bandFrequencies = emptyList()
    }
}
