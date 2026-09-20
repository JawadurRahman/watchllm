package com.focussystems.watchllm.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.focussystems.watchllm.llm.GenerationStats
import com.focussystems.watchllm.llm.LlamaEngine
import com.focussystems.watchllm.llm.LlmConfig
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

    fun ask(prompt: String, system: String = LlmConfig.SYSTEM_PROMPT) {
        if (_state.value.phase != Phase.Ready) return
        _state.value = UiState(phase = Phase.Generating)
        engine.generate(
            system = system,
            user = prompt,
            onText = { chunk -> _state.update { it.copy(reply = it.reply + chunk) } },
            onFinished = { stats ->
                _state.update {
                    if (stats == null) it.copy(phase = Phase.Error, error = "Generation failed")
                    else it.copy(phase = Phase.Ready, stats = stats)
                }
            },
        )
    }

    fun stop() = engine.cancel()

    override fun onCleared() = engine.release()
}
