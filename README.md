# StageClix

A low-latency Android metronome app built with Jetpack Compose and the [Oboe](https://github.com/google/oboe) audio library.

## What it does

- Generates a **880 Hz sine wave click** at a user-defined BPM
- Each click is 50 ms long, with silence between beats
- BPM is adjustable from **60 to 200** via an on-screen slider
- Play and stop controls start/pause the click track without closing the audio stream

## How it works

| Layer | Technology |
|---|---|
| UI | Jetpack Compose |
| JNI bridge | `AudioEngineJni.kt` → `JniBridge.cpp` |
| Audio engine | `AudioEngine.cpp` using Oboe 1.9.3 |
| Stream config | Low-latency exclusive output, Float mono, `c++_shared` STL |

The native library (`libclickcue_engine.so`) opens an Oboe output stream on `start()`. The audio callback runs on a dedicated real-time thread, counting samples to determine beat timing and computing sine wave samples during the 50 ms click window.

## Project structure

```
app/src/main/
├── cpp/
│   ├── AudioEngine.h / .cpp   # Oboe stream + audio callback
│   ├── JniBridge.cpp          # JNI functions
│   ├── CMakeLists.txt         # Native build config
│   └── native-lib.cpp         # Placeholder
├── java/
│   ├── com/clickcue/audio/
│   │   └── AudioEngineJni.kt  # Kotlin JNI declarations
│   └── com/example/stageclix/
│       └── MainActivity.kt    # Compose UI
```

## Requirements

- Android Studio Meerkat or newer
- NDK 28+
- Min SDK 26 (Android 8.0)
- Physical device or x86_64 / arm64 emulator (x86 is excluded — Oboe 1.9.x dropped x86 prebuilts)
