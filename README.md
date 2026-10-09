# B-LINK

B-LINK is an offline disaster SOS app for the AppBuilders PH Hackathon 2026 (theme: Local AI).

During a typhoon, a person may have no mobile signal. They hold the SOS button and speak in Tagalog, Bisaya, or English. The phone transcribes the clip **on the device**, turns it into a short alert such as `3 trapped, need water, Purok 4`, and tags it **CRITICAL**, **NEEDS HELP**, or **SAFE**. Alerts and the original voice clip then move phone to phone with no internet. A responder feed lists what this phone has seen, critical first.

## What runs locally

| Step | How | Network |
| --- | --- | --- |
| Speech to text | whisper.cpp v1.9.5 and the multilingual Whisper **base** model, quantized `ggml-base-q5_1` (about 57 MB) | None |
| Urgency | Rule and keyword classifier for Tagalog, Bisaya, and English | None |
| One-line summary | Gemma 3 1B int4 through MediaPipe **only if** the sideloaded `.task` file is present and the phone reports at least about 3.4 GiB of RAM. Otherwise the same keyword line. Urgency never comes from the model. | None |
| Phone-to-phone relay | Google Nearby Connections, strategy `P2P_CLUSTER`, store-and-forward, deduped by alert id, hop limit 5. The voice clip is a Nearby FILE payload linked to the alert id. | None (radios only) |
| Fallback share | QR code of the alert summary | None |
| Alert log | Room database on the phone, so the feed survives relaunch | None |
| Ask B-LINK | Bundled Tagalog and English safety answers. A medical phrase opens the SOS recorder instead of a tip. | None |

The speech model is **bundled in the APK**. Gradle downloads `ggml-base-q5_1.bin` at build time into `app/src/main/assets/models/` (gitignored). On first launch the app copies that asset into private storage and checks its SHA-256. Transcription code never opens a socket.

The Gemma file is **not** in the APK. Push it after install (see below). If it is missing, the phone is short on RAM, the model errors, or the call takes longer than 15 seconds, the keyword summary is what gets stored and relayed.

Whisper language codes:

- Tagalog → `tl`
- Bisaya → `tl` (Whisper has no Cebuano id; the keyword layer still scores Bisaya words)
- English → `en`

You can also type or edit the transcript before sending. That path does not need the microphone. A typed alert has no voice clip, so the feed does not show a play button for it.

Vosk was considered and not used. Its Filipino model is about 320 MB and is licensed CC-BY-NC-SA.

## Build the release APK

Requirements: JDK 17+, Android SDK (compile SDK 35, build-tools 35.0.0, CMake 3.22.1, NDK 27.2.12479018), `git`, and `curl`.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"   # or your SDK path
export ANDROID_SDK_ROOT="$ANDROID_HOME"
./gradlew assembleRelease
```

The first build clones whisper.cpp **v1.9.5** into `third_party/whisper.cpp` (gitignored) and downloads the Whisper model. Later builds reuse both.

Release APK (minified, signed with the committed demo keystore `keystore/debug.keystore`, alias `androiddebugkey`, store and key password `android`):

```text
app/build/outputs/apk/release/app-release.apk
```

`minSdk` is 26. `targetSdk` and `compileSdk` are 35. ABIs are `arm64-v8a` (phones) and `x86_64` (emulator).

Install on a phone (USB debugging):

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Open B-LINK once so Android creates the app files directory, then push Gemma only if you want on-device summary refinement. The file is not downloaded by the app.

```bash
adb shell mkdir -p /sdcard/Android/data/ph.appbuilders.saklolo/files
adb push gemma3-1b-it-int4.task /sdcard/Android/data/ph.appbuilders.saklolo/files/gemma3-1b-it-int4.task
```

Restart the app after the push. On the next spoken alert, a phone that reports at least 3.4 GiB of total RAM (advertised 4 GB phones often report less than 4 GiB) may replace the keyword line with Gemma's line. The urgency chip stays on the keyword decision. The card shows **On-device AI** or **Keyword rules**.

Unit tests:

```bash
./gradlew testDebugUnitTest
```

Debug APK, if you want the unminified build:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## What was verified in this repo

`./gradlew testDebugUnitTest` and `./gradlew assembleRelease` are the checks for this tree. There was no handset and no Android emulator here, so the microphone, GPS, Nearby hop, foreground notification, Room restore after process death, Gemma load, and voice-clip playback were **not** run on a phone.

Unit tests cover Tagalog, Bisaya, and English triage, direct receipt stored as **1 hop**, hop limit, dedupe, QR, JSON (audio path stays off the wire), Bisaya whisper code `tl`, the peer-name allowlist, the endpoint lock bookkeeping, the summary chooser, and the WAV header.

The offline speech path was checked earlier on this model file with whisper.cpp's `whisper-cli` (v1.9.5) on this Linux machine. See `docs/PLAN.md`. That check is not re-run on every build.

## Airplane-mode demo (2 or 3 phones)

Target phones for the hackathon demo are a TECNO Camon 40 (recorder) and a TECNO Spark 30 (responder), one hop, airplane mode.

1. Install the same release APK on each phone. Open B-LINK once. Allow microphone, location, nearby devices / Bluetooth, notifications, and camera. Accept the battery-optimization prompt so TECNO HiOS is less likely to kill the relay. HiOS autostart cannot be granted from the app; turn that on in system settings if the relay dies.
2. A persistent notification says **B-LINK is relaying SOS alerts nearby**. The relay is a foreground service. You do not have to leave the screen on the feed, but the process still has to stay alive.
3. Turn **airplane mode on**. Turn **Bluetooth** and **Wi-Fi** back on. They do not need to join a network. Leave location on so GPS can attach to an alert.
4. Optional: open the gear and set each phone's name (`Camon 40`, `Spark 30`). To force a path, turn on **Only these phone names** and list the other phone. Leave the switch off to talk to every B-LINK phone.
5. The status pill reads **Offline · N phones nearby** from the live connection count. The nearby card names each connected phone.
6. On the recorder phone, hold the red button and say a short SOS. Example: "Tatlong tao ang naipit sa Purok 4, kailangan ng tubig." Release, check the transcript, summary, and urgency, then tap **Send alert**.
7. The other phone should list that alert as **1 hop**, critical above needs-help above safe, with time and GPS or **No GPS**. A play button appears only when that phone has the voice clip. Tap a card to read the transcript.
8. A third phone can hear the alert through the middle one. The same alert id is stored once. The sender keeps hops at 0 (`recorded here`). Each receiver adds one hop. Forwarding stops once the stored hop count reaches 5.
9. If Nearby does not connect, open the alert, tap **QR**, and on the other phone tap **Scan QR**. Scanning also counts as a receive, so the stored hop count goes up by one.

Nearby Connections uses Google Play Services. A phone without Play Services can still record, triage, and exchange QR codes. It will not relay voice clips, because those travel as Nearby FILE payloads.

## Project layout

- `app/src/main/java/ph/appbuilders/saklolo/ui` — recorder, Ask B-LINK, responder feed, green theme
- `ask` — offline safety Q&A. Answers are the bundled sentences, not generated text
- `stt` — whisper.cpp JNI, 16 kHz recorder, model install
- `triage` — keyword summary and urgency
- `summary` — optional Gemma refinement, and an optional Ask pair-id pick
- `relay` — foreground service, Nearby store-and-forward, voice-clip files, QR codec
- `data` — Room log
- `docs/PLAN.md` — decisions

The rotating tips are short lines based on public guidance from the Philippine government Disaster Preparedness & First Aid Handbook and UNICEF Philippines. They are reminders on the recorder screen, not a substitute for official warnings.

## DISCLOSURE

Everything below is part of how B-LINK was built or how it runs. The on-device path does not send audio, transcripts, or alerts to a cloud model.

**Models**

- OpenAI Whisper multilingual **base**, file `ggml-base-q5_1.bin` (SHA-256 `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898`), converted to ggml by the whisper.cpp project and published at `ggerganov/whisper.cpp` on Hugging Face. Whisper weights are MIT.
- Google **Gemma 3 1B** int4 (`.task`), sideloaded by the person installing the demo, not bundled. Used only through MediaPipe LLM Inference. On the recorder it may refine the one-line summary. On Ask B-LINK, if the keyword match misses, it may return a stored pair id or `NONE`. That id is accepted only when it is one of the bundled pairs. Gemma's own words are never shown. Gemma is used under Google's Gemma Terms of Use. If the file is absent, this model does not run.
- Keyword and phrase classifier in `TriageEngine.kt` for urgency and for the fallback summary.
- Ask B-LINK bank, `app/src/main/assets/ask/ask_blink_qa.json`, bundled in the APK. Eighteen Tagalog and English answers drawn from the Philippine government Disaster Preparedness & First Aid Handbook (climate.gov.ph) and UNICEF Philippines emergency preparedness tips. Thirty-two medical phrases skip the tip and open the SOS recorder. The no-match line is the stored fallback, not a generated sentence.

**Speech runtime**

- [whisper.cpp](https://github.com/ggml-org/whisper.cpp) **v1.9.5** (ggml-org / Georgi Gerganov), MIT, compiled into the app with the Android NDK.
- ggml (bundled inside that whisper.cpp tag), MIT.

**Android app**

- Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Gradle 8.11.1
- Jetpack Compose (BOM 2024.10.01), Material 3, AndroidX Activity, Lifecycle, core-ktx, coroutines
- AndroidX Room 2.6.1 (KSP 2.0.21-1.0.28) for the alert log
- kotlinx.serialization 1.7.3
- Google Play Services Nearby Connections 19.3.0 (`P2P_CLUSTER`). Nearby sends Google usage analytics through Play Services.
- MediaPipe Tasks GenAI `com.google.mediapipe:tasks-genai:0.10.27` for the optional Gemma summary
- ZXing (`com.google.zxing:core` via `com.journeyapps:zxing-android-embedded:4.3.0`), Apache-2.0, for QR draw and scan
- JUnit 4.13.2 for unit tests
- Android SDK platform 35, build-tools 35.0.0, NDK 27.2.12479018, CMake 3.22.1

**Tools used to write this repository**

- Cursor cloud agents (Grok 4.7) helped write the application code, tests, and these docs. They are not in the APK and are not called at runtime.

**Evaluated and not shipped**

- Vosk Android and `vosk-model-tl-ph-generic-0.6` (size and CC-BY-NC-SA).
- LiteRT-LM (`litertlm-android`). Current releases want a newer Kotlin toolchain than this project, and they load `.litertlm` bundles rather than the MediaPipe `.task` file named for this demo.

## License of this app code

The B-LINK Kotlin, JNI glue, and docs in this repository are released under the MIT License. Third-party components keep their own licenses, listed above.
