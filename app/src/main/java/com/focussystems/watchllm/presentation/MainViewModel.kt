package com.focussystems.watchllm.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.focussystems.watchllm.llm.GenerationStats
import com.focussystems.watchllm.llm.LlamaEngine
import android.os.SystemClock
import com.focussystems.watchllm.llm.LlmConfig
import com.focussystems.watchllm.llm.Preset
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Phase { Loading, Ready, Generating, Error }

data class UiState(
    val phase: Phase = Phase.Loading,
    val reply: String = "",
    val stats: GenerationStats? = null,
    val error: String? = null,
)

/** Model lifetime == this ViewModel's lifetime: loaded when the app opens, freed when it closes. */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = LlamaEngine()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val model = File(app.filesDir, LlmConfig.MODEL_FILE_NAME)
        if (!model.exists()) {
            _state.value = UiState(phase = Phase.Error, error = "Model not found:\n${model.path}")
        } else {
            engine.load(model) { ok ->
                _state.value = if (ok) UiState(Phase.Ready)
                else UiState(phase = Phase.Error, error = "Failed to load model")
            }
        }
    }

    // Text arrives per token on the worker thread; pushing every token to Compose steals CPU from
    // the generation threads, so chunks are buffered and flushed to the UI every UI_FLUSH_MS.
    private val pending = StringBuilder()
    private var lastFlush = 0L

    private fun flush() {
        if (pending.isEmpty()) return
        val text = pending.toString()
        pending.setLength(0)
        _state.update { it.copy(reply = it.reply + text) }
    }

    fun ask(preset: Preset, input: String) {
        if (_state.value.phase != Phase.Ready) return
        _state.value = UiState(phase = Phase.Generating)
        pending.setLength(0)
        lastFlush = SystemClock.uptimeMillis()
        engine.generate(
            system = preset.systemPrompt,
            user = preset.buildPrompt(input),
            onText = { chunk ->
                pending.append(chunk)
                val now = SystemClock.uptimeMillis()
                if (now - lastFlush >= UI_FLUSH_MS) { flush(); lastFlush = now }
            },
            onFinished = { stats ->
                flush()
                _state.update {
                    // The model is still loaded, so a failed reply is recoverable: back to Ready.
                    if (stats == null) it.copy(phase = Phase.Ready, error = "Generation failed. Try again.")
                    else it.copy(phase = Phase.Ready, stats = stats)
                }
            },
        )
    }

    fun stop() = engine.cancel()

    private companion object { const val UI_FLUSH_MS = 150L }

    override fun onCleared() = engine.release()
}
