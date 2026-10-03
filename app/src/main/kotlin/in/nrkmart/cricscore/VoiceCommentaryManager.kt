package `in`.nrkmart.cricscore

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

object VoiceCommentaryManager : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isVoiceEnabled = MutableStateFlow(true)
    val isVoiceEnabled: StateFlow<Boolean> = _isVoiceEnabled.asStateFlow()

    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            isInitialized = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    fun toggleVoice() {
        _isVoiceEnabled.value = !_isVoiceEnabled.value
    }

    fun setVoiceEnabled(enabled: Boolean) {
        _isVoiceEnabled.value = enabled
    }

    fun speak(text: String) {
        if (!_isVoiceEnabled.value || !isInitialized) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cric_voice_${System.currentTimeMillis()}")
        } catch (_: Exception) {}
    }

    fun speakBall(runs: Int, isWicket: Boolean = false, extraType: ExtrasType = ExtrasType.NONE, extraRuns: Int = 0) {
        if (!_isVoiceEnabled.value || !isInitialized) return
        val comment = when {
            isWicket -> "Wicket! That is out!"
            extraType == ExtrasType.WIDE -> if (extraRuns > 0) "Wide ball plus $extraRuns runs" else "Wide ball"
            extraType == ExtrasType.NO_BALL -> if (extraRuns > 0) "No ball, $extraRuns runs" else "No ball"
            extraType == ExtrasType.BYE -> "$extraRuns Bye"
            extraType == ExtrasType.LEG_BYE -> "$extraRuns Leg bye"
            runs == 0 -> "Dot ball"
            runs == 1 -> "1 run"
            runs == 2 -> "2 runs"
            runs == 3 -> "3 runs"
            runs == 4 -> "4 runs! Great boundary!"
            runs == 6 -> "Sixer! Huge hit!"
            else -> "$runs runs"
        }
        speak(comment)
    }

    fun speakMilestone(text: String) {
        if (!_isVoiceEnabled.value || !isInitialized) return
        speak(text)
    }

    fun speakOverSummary(overNumber: Int, runsInOver: Int, teamName: String, totalRuns: Int, totalWickets: Int) {
        if (!_isVoiceEnabled.value || !isInitialized) return
        val comment = "End of over $overNumber. $runsInOver runs from the over. $teamName are $totalRuns for $totalWickets."
        speak(comment)
    }

    fun processVoiceCommand(command: String, viewModel: ScoringViewModel, context: Context): Boolean {
        val clean = command.trim().lowercase(Locale.US)
        return when {
            clean.contains("six") || clean.contains("6") || clean.contains("maximum") -> {
                viewModel.handleRuns(6, true)
                true
            }
            clean.contains("four") || clean.contains("4") || clean.contains("boundary") -> {
                viewModel.handleRuns(4, true)
                true
            }
            clean.contains("three") || clean.contains("3") -> {
                viewModel.handleRuns(3, true)
                true
            }
            clean.contains("two") || clean.contains("2") || clean.contains("double") -> {
                viewModel.handleRuns(2, true)
                true
            }
            clean.contains("one") || clean.contains("1") || clean.contains("single") -> {
                viewModel.handleRuns(1, true)
                true
            }
            clean.contains("dot") || clean.contains("zero") || clean.contains("0") || clean.contains("no run") -> {
                viewModel.handleRuns(0, true)
                true
            }
            clean.contains("wide") -> {
                viewModel.handleExtra(ExtrasType.WIDE, 0)
                true
            }
            clean.contains("no ball") -> {
                viewModel.handleExtra(ExtrasType.NO_BALL, 0)
                true
            }
            clean.contains("bye") -> {
                viewModel.handleExtra(ExtrasType.BYE, 1)
                true
            }
            clean.contains("leg bye") -> {
                viewModel.handleExtra(ExtrasType.LEG_BYE, 1)
                true
            }
            clean.contains("wicket") || clean.contains("out") || clean.contains("bowled") || clean.contains("caught") -> {
                viewModel.handleWicket(WicketType.BOWLED, null)
                true
            }
            clean.contains("undo") || clean.contains("cancel") -> {
                viewModel.undo(context)
                true
            }
            else -> false
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (_: Exception) {}
    }
}
