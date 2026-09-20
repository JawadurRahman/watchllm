# WatchLLM

**A language model that runs entirely on your wrist.** No phone, no cloud, no account, no
network. Ask a question, dictate a sentence, or rewrite a message, and the answer is generated on the
watch itself.

WatchLLM is a Wear OS app built with [llama.cpp](https://github.com/ggml-org/llama.cpp) (via JNI)
and Jetpack Compose for Wear OS. It was made for a 32-bit, 1.85 GB smartwatch, the OnePlus Watch 2R,
and streams about **5-6 tokens per second** from a 135M-parameter model on just 2 threads.

## Features

- **Fully on-device.** The model runs on the watch CPU with NEON. Nothing leaves the device.
- **Streaming replies.** Words appear as they are generated, with a Stop button and a tokens/s readout.
- **Presets.** One tap picks a persona: *Ask anything*, *Quick fact*, *Explain simply*, *Rewrite shorter*.
  Each is just a system prompt plus a prompt template, kept in one easy-to-edit list.
- **Type or speak.** Enter text with the watch keyboard, or dictate with the system speech recognizer
  and confirm what it heard (**Send**, **Retry**, **Cancel**) before anything reaches the model.
- **Built for a tiny battery and tiny RAM.** No background or foreground service. The model loads when
  the app opens and is freed when it closes. The screen stays on only while a reply is being
  generated, generation is cancelled when the app leaves the foreground, and replies are capped at
  128 tokens.

## How it works

A slim JNI layer (`app/src/main/cpp/llm_jni.cpp`) talks to llama.cpp's C API directly. It formats each
request with the model's own chat template, decodes the prompt, and streams tokens back to Kotlin
through a callback. Generation runs on a dedicated worker thread, and UI text updates are batched to
about every 150 ms so the screen never steals time from the inference threads.

| Path | What it is |
| --- | --- |
| `app/src/main/cpp/` | CMake build and the JNI bridge to llama.cpp |
| `.../llm/LlmConfig.kt` | Settings: threads, context size, max tokens, temperature, default prompt |
| `.../llm/Presets.kt` | The preset list. Edit here to add or change presets |
| `.../llm/LlamaEngine.kt` | Kotlin wrapper around the native library |
| `.../presentation/` | Compose screens, navigation, and the view model |

## Build it

llama.cpp is not bundled. Point the build at a checkout; it defaults to `C:/llama.cpp`, or set
`llamaCppDir` in `gradle.properties`:

```
llamaCppDir=D:/path/to/llama.cpp
```

You need NDK `30.0.16248370` and CMake `4.1.2` (both from the Android SDK manager) and JDK 25, the
toolchain the Gradle daemon expects (Android Studio's bundled JBR works).

```
gradlew.bat :app:assembleDebug
```

The native code is always built optimised, even for debug builds, since an unoptimised ggml is far
too slow on a watch.

## Add a model

The model is not bundled in the APK. Push a GGUF file into the app's private files folder. This
project uses `SmolLM2-135M-Instruct-Q4_K_M.gguf`:

```
adb push SmolLM2-135M-Instruct-Q4_K_M.gguf /data/local/tmp/model.gguf
adb shell "run-as com.focussystems.watchllm sh -c 'cat /data/local/tmp/model.gguf > files/SmolLM2-135M-Instruct-Q4_K_M.gguf'"
adb shell rm /data/local/tmp/model.gguf
```

## Notes

- Text entry uses the watch's system keyboard, so an input method such as Gboard must be enabled on
  the watch.
- The build targets `armeabi-v7a` only. Adding other ABIs means extending `abiFilters` and the CMake
  arguments in `app/build.gradle.kts`.