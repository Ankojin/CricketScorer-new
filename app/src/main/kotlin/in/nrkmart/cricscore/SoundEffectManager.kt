package `in`.nrkmart.cricscore

import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object SoundEffectManager {
    private var toneGenerator: ToneGenerator? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        } catch (_: Exception) {
            toneGenerator = null
        }
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
    }

    fun playRunSound(runs: Int) {
        if (_isMuted.value) return
        scope.launch {
            try {
                val tg = toneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, 80).also { toneGenerator = it }
                when (runs) {
                    0 -> tg.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
                    1 -> tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 60)
                    2 -> {
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 50)
                        delay(70)
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 50)
                    }
                    3 -> {
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 45)
                        delay(60)
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 45)
                        delay(60)
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 45)
                    }
                    4 -> {
                        // Boundary chime
                        tg.startTone(ToneGenerator.TONE_DTMF_A, 80)
                        delay(90)
                        tg.startTone(ToneGenerator.TONE_DTMF_D, 120)
                    }
                    6 -> {
                        // Sixer fanfare
                        tg.startTone(ToneGenerator.TONE_DTMF_0, 60)
                        delay(70)
                        tg.startTone(ToneGenerator.TONE_DTMF_5, 60)
                        delay(70)
                        tg.startTone(ToneGenerator.TONE_DTMF_A, 120)
                    }
                    else -> tg.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                }
            } catch (_: Exception) {}
        }
    }

    fun playExtraSound() {
        if (_isMuted.value) return
        scope.launch {
            try {
                val tg = toneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, 80).also { toneGenerator = it }
                tg.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
            } catch (_: Exception) {}
        }
    }

    fun playWicketSound() {
        if (_isMuted.value) return
        scope.launch {
            try {
                val tg = toneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, 80).also { toneGenerator = it }
                tg.startTone(ToneGenerator.TONE_PROP_NACK, 150)
            } catch (_: Exception) {}
        }
    }

    fun playUndoSound() {
        if (_isMuted.value) return
        scope.launch {
            try {
                val tg = toneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, 80).also { toneGenerator = it }
                tg.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
            } catch (_: Exception) {}
        }
    }
}
