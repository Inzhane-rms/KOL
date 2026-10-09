# Saklolo

Saklolo is an offline disaster SOS app for the AppBuilders PH Hackathon 2026 (theme: Local AI).

During a typhoon, a person may have no mobile signal. They record a voice SOS in Tagalog, Bisaya, or English. The phone transcribes it **on the device**, turns it into a short alert such as `3 trapped, need water, Purok 4`, and tags it **CRITICAL**, **NEEDS HELP**, or **SAFE**. Alerts then move phone to phone with no internet. A responder screen lists what this phone has seen, critical first, with time and GPS when a fix was available.

The name is Filipino for a call for rescue.

## What runs locally

| Step | How | Network |
| --- | --- | --- |
| Speech to text | whisper.cpp v1.9.5 and the multilingual Whisper **base** model, quantized `ggml-base-q5_1` (about 57 MB) | None |
| Summary and urgency | Rule and keyword classifier for Tagalog, Bisaya, and English | None |
| Phone-to-phone relay | Google Nearby Connections, strategy `P2P_CLUSTER`, store-and-forward, deduped by alert id, hop limit 5 | None (radios only) |
| Fallback share | QR code of the alert summary | None |

The speech model is **bundled in the APK**. Gradle downloads `ggml-base-q5_1.bin` at build time into `app/src/main/assets/models/` (gitignored). On first launch the app copies that asset into private storage and checks its SHA-256. If you build without the asset, the app can download the same file once; after that, airplane mode is enough. Transcription code never opens a socket.

Whisper language codes used by the chips:

- Tagalog → `tl`
- English → `en`
- Bisaya and Auto → `auto`

Whisper's multilingual set includes Tagalog and English. It does **not** have a Cebuano/Bisaya language id, so that chip uses auto-detect. Urgency keywords still match Bisaya words (`napiit`, `tabang`, `tubig`, `pagkaon`, `luwas`, `baha`, …) when they show up in the transcript. You can also type or edit the transcript before sending. That path does not need the microphone.

Vosk was considered and not used. Its Filipino model is about 320 MB and is licensed CC-BY-NC-SA, which is a poor fit for a hackathon build. A small English Vosk model would miss Tagalog and Bisaya. One Whisper multilingual model covers the demo.

A small on-device language model for summaries was not wired in this version. The keyword triage is the reliable baseline. See `docs/PLAN.md`.

## Build the debug APK

Requirements: JDK 17+, Android SDK (compile SDK 35, build-tools 35.0.0, CMake 3.22.1, NDK 27.2.12479018), `git`, and `curl`.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"   # or your SDK path
export ANDROID_SDK_ROOT="$ANDROID_HOME"
./gradlew assembleDebug
```

The first build clones whisper.cpp **v1.9.5** into `third_party/whisper.cpp` (gitignored) and downloads the model. Later builds reuse both.

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install on a phone (USB debugging):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Unit tests for triage, dedupe, hop limit, and QR:

```bash
./gradlew testDebugUnitTest
```

The APK includes `arm64-v8a` (phones) and `x86_64` (emulator). 32-bit-only phones are not in this build.

## What was verified in this repo

`./gradlew testDebugUnitTest assembleDebug` succeeds. The debug APK is about 81 MB because it contains `ggml-base-q5_1.bin`. `libsaklolo_whisper.so` is in the APK for `arm64-v8a` and `x86_64`, exports the JNI entry points, links `whisper_full`, and is aligned to 16 KB pages.

There was no handset and no Android emulator here, so the mic, GPS, and Nearby hop were not run on a phone. The offline speech path was checked on the same model file with whisper.cpp's `whisper-cli` (v1.9.5) on this Linux machine:

- English reference `jfk.wav`: "And so my fellow Americans, ask not what your country can do for you, ask what you can do for your country."
- Tagalog clip (synthesized only for this check, not used by the app), language `tl` and `auto`: "Tatlong tao ang naipit sa purokapat, kailangan ng tubing." The on-device triage maps that wording to **CRITICAL** / `3 trapped, need water, Purok 4`. `tubing` and the glued `purokapat` are Whisper errors the keyword layer treats as water and Purok 4.
- A Cebuano sentence spoken by a Tagalog TTS voice (a weak stand-in, not a Bisaya speaker) still produced `napiit` and was triaged **CRITICAL**. Real Bisaya from a phone mic will be rougher. Typing the SOS still triages it with no network.

The keyword tests cover the clean Tagalog, Bisaya, and English sentences directly, including the demo line `3 trapped, need water, Purok 4`.

## Airplane-mode demo (2 or 3 phones)

1. Install the same debug APK on each phone. Open Saklolo once and wait until it says **Speech model ready on this phone**. Allow microphone, location, nearby devices / Bluetooth, and camera.
2. Turn **airplane mode on**.
3. Turn **Bluetooth** and **Wi-Fi** back on. They do not need to join a network. Leave location on so GPS can attach to an alert.
4. On every phone, open **Responders** and switch **Relay** on. Keep Saklolo in the foreground.
5. On phone A, choose Tagalog, Bisaya, or English, tap **RECORD**, and say a short SOS. Example: "Tatlong tao ang naipit sa Purok 4, kailangan ng tubig."
6. Stop, check the transcript, summary, and urgency, then tap **Send alert**.
7. Phones B and C should list that alert, **CRITICAL** above **NEEDS HELP** above **SAFE**, with time and GPS if phone A had a fix. Each phone rebroadcasts alerts it had not seen, so a third phone can hear it through the middle one. The same alert id is stored once. Forwarding stops after about 5 hops.
8. If Nearby does not connect, on phone A open the alert and tap **QR**. On phone B tap **Scan alert QR**.

Nearby Connections uses Google Play Services. A phone without Play Services can still record, triage, and exchange QR codes. Relay status text reports advertise/discover failures.

## Project layout

- `app/src/main/java/ph/appbuilders/saklolo/ui` — SOS recorder and responder feed (Jetpack Compose)
- `stt` — whisper.cpp JNI, 16 kHz recorder, model install
- `triage` — on-device summary and urgency
- `relay` — Nearby store-and-forward and QR codec
- `docs/PLAN.md` — decisions and remaining work

## DISCLOSURE

Everything below is part of how Saklolo was built or how it runs. The on-device path does not send audio, transcripts, or alerts to a cloud model.

**Models**

- OpenAI Whisper multilingual **base**, file `ggml-base-q5_1.bin` (SHA-256 `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898`), converted to ggml by the whisper.cpp project and published at `ggerganov/whisper.cpp` on Hugging Face. Whisper weights are MIT.
- No second neural model. Summary and urgency are a local keyword and phrase classifier in `TriageEngine.kt`, not a hosted LLM.

**Speech runtime**

- [whisper.cpp](https://github.com/ggml-org/whisper.cpp) **v1.9.5** (ggml-org / Georgi Gerganov), MIT, compiled into the app with the Android NDK.
- ggml (bundled inside that whisper.cpp tag), MIT.

**Android app**

- Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Gradle 8.11.1
- Jetpack Compose (BOM 2024.10.01), Material 3, Navigation Compose, AndroidX Activity, Lifecycle, coroutines
- kotlinx.serialization 1.7.3
- Google Play Services Nearby Connections 19.3.0 (`P2P_CLUSTER`)
- ZXing (`com.google.zxing:core` via `com.journeyapps:zxing-android-embedded:4.3.0`), Apache-2.0, for QR draw and scan
- JUnit 4.13.2 for unit tests
- Android SDK platform 35, build-tools 35.0.0, NDK 27.2.12479018, CMake 3.22.1

**Tools used to write this repository**

- Cursor cloud agents (Grok 4.7) helped write the application code, tests, and these docs. They are not in the APK and are not called at runtime.

**Evaluated and not shipped**

- Vosk Android and `vosk-model-tl-ph-generic-0.6` (size and CC-BY-NC-SA).
- An on-device instruction LLM for summaries (weight and integration cost for this deadline). Listed as a follow-up in `docs/PLAN.md`.

## License of this app code

The Saklolo Kotlin, JNI glue, and docs in this repository are released under the MIT License. Third-party components keep their own licenses, listed above.
