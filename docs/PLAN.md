# Saklolo build plan

Hackathon deadline: 10:00 AM Manila, 10 October 2026. This pass favors a demo that builds and works offline over extra screens.

## Decisions

1. **Native app.** Kotlin, Jetpack Compose, minSdk 26, target/compile SDK 35. Two screens: SOS recorder and responder feed.
2. **Speech.** whisper.cpp v1.9.5, multilingual Whisper base quantized to `ggml-base-q5_1` (~57 MB). Chosen over Vosk because one MIT model covers Tagalog and English, and the Vosk Filipino model is ~320 MB under CC-BY-NC-SA. Tiny would be faster and weaker on Tagalog; base q5_1 is the size/quality compromise for a phone demo. The model is downloaded by Gradle and packaged in the APK, then copied into app storage on first launch. SHA-256 is pinned in `ModelInstaller`.
3. **Bisaya.** Whisper has no `ceb` language id. The Bisaya chip sets `language=auto`. Keyword triage still scores Bisaya words. Typed or edited text is the reliable Bisaya demo if recognition is rough.
4. **Triage.** Keyword and phrase classifier, not a neural net. It never uses the network. Critical beats help; safe requires an affirmative safe phrase and no critical or help signal; negated words (`hindi ligtas`, `no injuries`, `walang sugat`) do not count as their positive form. Summaries are short English lines, for example `3 trapped, need water, Purok 4`.
5. **Relay.** Google Nearby Connections `P2P_CLUSTER` (Bluetooth + Wi-Fi, including airplane mode with those radios on). Store-and-forward, dedupe by alert id, do not emit a copy whose hop count would exceed 5. QR (`SKL1|…`) is the fallback and does not need Play Services. Play Services is required for Nearby.
6. **LLM summary.** Not in this build. A small local model (for example a sub-1B quantized instruct model) can replace or sit behind `TriageEngine` later without changing the alert schema.

## What this version includes

- Record up to 30 seconds at 16 kHz mono, transcribe on device, show transcript, summary, and urgency, then send.
- Type or edit the transcript and retriage before send.
- Responder list sorted CRITICAL, then NEEDS HELP, then SAFE, newest first inside a tier. Time, GPS, hop count, transcript, delete, QR.
- Persistent alert log in app files so a relaunch still has alerts to forward.
- JVM unit tests for triage (Tagalog, Bisaya, English), dedupe, sort, hop limit, QR, and JSON.

## Verification

`./gradlew testDebugUnitTest assembleDebug` passed. Unit tests cover Tagalog, Bisaya, and English triage (including the Whisper wording `purokapat` / `tubing`), dedupe, sort, hop limit, QR, and JSON. The debug APK packages the model and `libsaklolo_whisper.so` (arm64-v8a and x86_64).

Host `whisper-cli` from the same v1.9.5 tag transcribed `jfk.wav` correctly and a Tagalog TTS clip into text the triage engine turns into `3 trapped, need water, Purok 4`. That TTS clip is not part of the app. No phone was available, so record → Nearby hop was not executed on hardware. See the README for the demo steps.

## Remaining TODOs

- Optional on-device instruct model for summaries, with the keyword engine kept as the fallback if the model is missing or too slow.
- Better Bisaya ASR (a Cebuano fine-tune or a dedicated small model) once something fits on a mid-range phone.
- Foreground service so relay continues while the screen is off. Today the demo asks people to keep Saklolo open.
- Stronger mesh behavior: backoff when both sides `requestConnection` at once, and a visible peer list.
- Encrypt alert payloads. Nearby is not a private channel.
- 32-bit ABI (`armeabi-v7a`) if an older demo phone shows up.
- A labeled set of real Tagalog and Bisaya SOS recordings to measure word error rate, not just keyword hits.
- Map pins for alerts that have GPS.
- Battery pass: cap whisper threads, and pause discovery when no relay toggle is on (already true) and when the phone is stationary for a long time.
- Play-feature or asset-pack delivery if the 57 MB model should not ship inside the base APK.
