package com.focussystems.watchllm.llm

import java.io.File
import java.util.concurrent.Executors

/** Timing for one generation. Speeds are tokens per second. */
data class GenerationStats(
    val promptTokens: Int,
    val generatedTokens: Int,
    val promptMs: Double,
    val generateMs: Double,
) {
    val tokensPerSecond: Float
        get() = if (generateMs > 0) (generatedTokens * 1000.0 / generateMs).toFloat() else 0f
}

/**
 * Thin wrapper over the JNI library. The native side holds a single global model/context, so every
 * call except [cancel] runs on one dedicated thread; callers get results via callbacks / suspend-free
 * futures and never touch native code from the main thread.
 */
class LlamaEngine {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "llama-worker") }

    fun interface TokenCallback {
        /** Called on the worker thread with a chunk of UTF-8 text. Return false to stop. */
        fun onToken(utf8: ByteArray): Boolean
    }

    /** Loads the model on the worker thread. [onDone] runs on the worker thread. */
    fun load(modelFile: File, onDone: (Boolean) -> Unit) {
        worker.execute {
            onDone(nativeLoad(modelFile.absolutePath, LlmConfig.N_CTX, LlmConfig.THREADS))
        }
    }

    /** Streams a reply on the worker thread; [onFinished] gets stats, or null if it failed. */
    fun generate(
        system: String,
        user: String,
        onText: (String) -> Unit,
        onFinished: (GenerationStats?) -> Unit,
    ) {
        worker.execute {
            val result = runCatching {
                nativeGenerate(system, user, LlmConfig.MAX_TOKENS, LlmConfig.TEMPERATURE) { bytes ->
                    onText(String(bytes, Charsets.UTF_8))
                    true
                }
            }.getOrNull()
            onFinished(result?.let { GenerationStats(it[0].toInt(), it[1].toInt(), it[2], it[3]) })
        }
    }

    /** Safe from any thread; makes a running [generate] finish early. */
    fun cancel() = nativeCancel()

    /** Cancels, frees native resources once the worker is idle, then shuts the worker down. */
    fun release() {
        nativeCancel()
        worker.execute { nativeFree() }
        worker.shutdown()
    }

    private external fun nativeLoad(path: String, nCtx: Int, nThreads: Int): Boolean
    private external fun nativeGenerate(
        system: String, user: String, maxTokens: Int, temp: Float, callback: TokenCallback,
    ): DoubleArray?
    private external fun nativeCancel()
    private external fun nativeFree()

    companion object {
        init { System.loadLibrary("watchllm") }
    }
}
