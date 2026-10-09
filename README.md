<img src="docs/kol_logo.svg" width="120">

# KOL

Call and message friends with no signal, phone to phone over Bluetooth and Wi-Fi, with on-device AI captions and emergency detection.

This page describes [v0.12.7-test](https://github.com/Inzhane-rms/KOL/releases/tag/v0.12.7-test).

## What works

- The Ready-to-connect checklist.
- Adding a contact by QR.
- 1:1 texts and voice notes. An outgoing message shows Waiting until this phone has handed it to Nearby, then Sent. Sent is not a delivery receipt, and a message that was sent does not go back to Waiting when the friend leaves range.
- Push-to-talk calls with live on-device captions. The control is Hold to talk.
- Tagalog transcription. Speech uses the Whisper multilingual base model. Decoding starts greedy, with best_of 3, and each clip stops at about 1.5 times its length, and at least 3 seconds. Beam 5 runs only after a greedy clip finishes faster than the clip itself. A fixed prompt and SpeechLexicon auto-correction run on the phone. Tapping a caption or a voice-note transcript shows the original text when it differs from the corrected line.
- The "Possible emergency" card, from on-device keyword rules, and the Urgent message sent to that contact.
- Quick-reply chips.
- Legal screens, the first-launch agreement, and Delete all data in Settings.

## Known limits

- No SOS broadcast or medic feed. The app's screens are Home, Contacts, Messages, Add, a chat, and a call.
- Messages are not end-to-end encrypted. A 1:1 message may hop through other nearby KOL phones on the way to the person it is addressed to. Nothing is forwarded past 5 hops.
- No delivery receipts.
- Not tested on many devices.
- Range not measured.
- Gemma is optional and not used in the demo.

## Local AI and no cloud

The manifest removes the INTERNET permission. The app does not call a cloud model.

- Speech-to-text: Whisper through whisper.cpp, Tagalog forced, with a fixed prompt and lexicon auto-correction (SpeechLexicon) on the phone (Whisper multilingual base, ggml-base-q5_1, whisper.cpp v1.9.5).
- OpenAI Whisper multilingual base, ggml-base-q5_1.bin, converted and published by the whisper.cpp project (ggerganov/whisper.cpp), MIT license.
- whisper.cpp library (MIT), compiled in unchanged; the JNI bridge is adapted from whisper.cpp's Android example.
- Google Gemma 3 1B IT int4 (Gemma Terms of Use): optional and side-loaded, not shipped in the APK, and not used in the demo video.
- Technologies: Kotlin 2.0.21, Jetpack Compose (BOM 2024.10.01), Android Room 2.6.1, Google Nearby Connections 19.3.0 (Google Play services), ZXing embedded 4.3.0, whisper.cpp v1.9.5 (NDK 27.2), MediaPipe LLM Inference tasks-genai 0.10.27 (for the optional Gemma), kotlinx-coroutines 1.9.0, kotlinx-serialization 1.7.3, Poppins font (SIL OFL 1.1). AGP 8.7.3, minSdk 26, targetSdk 35.
- APIs and cloud services: none in the app. Google Nearby Connections is a local, offline API that runs through Google Play services on the phone.
- Privacy: voice clips and transcripts never go to a server or the internet. They stay on KOL phones: the sender's, the receiver's, and any nearby KOL phone that relays them along the way. They are not end-to-end encrypted.

## Install

Download the APK from the [v0.12.7-test release](https://github.com/Inzhane-rms/KOL/releases/tag/v0.12.7-test) and sideload it.

## Build

```bash
./gradlew assembleRelease
```

The speech model downloads at build time.

## Disclosure

Built during the hackathon with AI-assisted coding (Cursor cloud agents and a team of AI assistants).

## Package ID

The package ID `ph.appbuilders.saklolo` is an internal ID from an early prototype name.
