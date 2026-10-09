# B-LINK build plan

Hackathon deadline: 10:00 AM Manila, 10 October 2026. This pass favors a demo that builds and works offline over extra screens.

## Decisions

1. **Native app.** Kotlin, Jetpack Compose, minSdk 26, target/compile SDK 35. Two screens: SOS recorder and responder feed.
2. **Speech.** whisper.cpp v1.9.5, multilingual Whisper base quantized to `ggml-base-q5_1` (~57 MB). Chosen over Vosk because one MIT model covers Tagalog and English, and the Vosk Filipino model is ~320 MB under CC-BY-NC-SA. Tiny would be faster and weaker on Tagalog; base q5_1 is the size/quality compromise for a phone demo. The model is downloaded by Gradle and packaged in the APK, then copied into app storage on first launch. SHA-256 is pinned in `ModelInstaller`.
3. **Bisaya.** Whisper has no `ceb` language id. Bisaya uses `tl`, the same code as Tagalog. Keyword triage still scores Bisaya words. Typed or edited text is the reliable Bisaya demo if recognition is rough.
4. **Triage.** Keyword and phrase classifier, not a neural net. It never uses the network. Critical beats help; safe requires an affirmative safe phrase and no critical or help signal; negated words (`hindi ligtas`, `no injuries`, `walang sugat`) do not count as their positive form. Summaries are short English lines, for example `3 trapped, need water, Purok 4`.
5. **Relay.** Google Nearby Connections `P2P_CLUSTER` in a foreground service (`connectedDevice`) with a persistent notification. Store-and-forward, dedupe by alert id. The sender stores hops 0 and transmits 0. Each receive stores hops + 1, so a direct receipt shows 1 hop. Forwarding does not increment. Nothing past 5 hops is stored or forwarded. The original clip is a 16 kHz mono WAV, capped at 30 seconds, sent as a Nearby FILE payload linked to the alert id. The play button is shown only when that file is on the phone. QR (`SKL1|…`) is the fallback and does not need Play Services. A demo allowlist can restrict peers by endpoint name.
6. **LLM summary.** MediaPipe LLM Inference starts loading a sideloaded Gemma 3 1B int4 `.task` in the background at app start when the file exists and `MemoryInfo.totalMem` is at least 3.4 GiB. It may replace only the one-line summary. Urgency stays on `TriageEngine`. Missing file, low RAM, or two failed attempts keeps the keyword line. One failure is retried on the next alert. The 15 second timeout wraps inference only, so an alert is not stuck behind model load. LiteRT-LM was not used: it wants a newer Kotlin than 2.0.21 and a `.litertlm` file, not the `.task` this demo pushes.

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

- Better Bisaya ASR (a Cebuano fine-tune or a dedicated small model) once something fits on a mid-range phone.
- Stronger mesh behavior: backoff when both sides `requestConnection` at once. The nearby card lists connected phones.
- Encrypt alert payloads. Nearby is not a private channel.
- 32-bit ABI (`armeabi-v7a`) if an older demo phone shows up.
- A labeled set of real Tagalog and Bisaya SOS recordings to measure word error rate, not just keyword hits.
- Map pins for alerts that have GPS.
- Battery pass: cap whisper threads, and pause discovery when no relay toggle is on (already true) and when the phone is stationary for a long time.
- Play-feature or asset-pack delivery if the 57 MB model should not ship inside the base APK.
