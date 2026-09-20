# WatchLLM

A Wear OS app that runs a small language model **entirely on the watch**, with no network and no
background service. It uses llama.cpp through JNI and Jetpack Compose for Wear OS.

Built for the OnePlus Watch 2R (Wear OS 4, 32-bit `armeabi-v7a` only, ~1.85 GB RAM).
Model: `SmolLM2-135M-Instruct-Q4_K_M.gguf`, about 5-6 tokens/s with 2 threads.

## What it does

- Pick a preset: **Ask anything**, **Quick fact**, **Explain simply**, **Rewrite shorter**.
- Enter your text by **typing** (the watch keyboard) or **speaking** (the system speech
  recognizer). After speaking you confirm with **Send**, **Retry** or **Cancel**.
- The reply streams onto the screen, with a **Stop** button and the tokens/s shown at the end.

Design rules: the model loads when the app opens and is freed when it closes; generation runs on a
background thread; the screen stays on only while generating; generation is cancelled when the app
leaves the foreground; replies are capped at 128 tokens.

## Layout

| Path | What it is |
| --- | --- |
| `app/src/main/cpp/` | CMake and the JNI bridge (`llm_jni.cpp`) to llama.cpp |
| `.../llm/LlmConfig.kt` | Hardcoded settings: threads, context size, max tokens, temperature, default system prompt |
| `.../llm/Presets.kt` | The preset list (system prompt + prompt template). Edit here to change presets |
| `.../llm/LlamaEngine.kt` | Kotlin wrapper: one worker thread for all native calls |
| `.../presentation/` | Compose screens, navigation, and `MainViewModel` |

## Building

llama.cpp is **not** in this repo. CMake reads it from `C:/llama.cpp` by default. Override with
`llamaCppDir` in `gradle.properties`:

```
llamaCppDir=D:/path/to/llama.cpp
```

Requirements: NDK `30.0.16248370`, CMake `4.1.2` (both via the Android SDK), and JDK 25 (the project's
Gradle daemon toolchain, e.g. Android Studio's bundled JBR).

```
gradlew.bat :app:assembleDebug
```

The native code is built as Release even for debug builds, because an unoptimised ggml is far too slow.

## Installing the model

The model is not bundled. Push it to the app's private files folder (the app must be installed first):

```
adb push SmolLM2-135M-Instruct-Q4_K_M.gguf /data/local/tmp/model.gguf
adb shell "run-as com.focussystems.watchllm sh -c 'cat /data/local/tmp/model.gguf > files/SmolLM2-135M-Instruct-Q4_K_M.gguf'"
adb shell rm /data/local/tmp/model.gguf
```

## Watch setup notes

- The in-app keyboard needs an enabled input method. On the test watch Gboard was installed but not
  enabled, so I ran `ime enable` and `ime set` for
  `com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME`.
- Wireless debugging drops when the watch sleeps off the charger, so reconnect as needed.

## Status

Works on the watch: model load, streaming, presets, typing, voice with confirm, Stop.

Not verified: that a running reply actually halts when the screen turns off (the app is stopped
by the system at that moment, which triggers the cancel), and the failed-reply recovery path.
Not built: a battery-saver toggle and any further error states.
