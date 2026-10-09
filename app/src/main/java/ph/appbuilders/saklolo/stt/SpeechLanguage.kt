package ph.appbuilders.saklolo.stt

/**
 * Whisper has no Cebuano language id. Bisaya uses Tagalog (`tl`), the closest
 * multilingual id, and the keyword layer still scores Bisaya words.
 */
enum class SpeechLanguage(val whisperCode: String, val label: String) {
    TAGALOG("tl", "Tagalog"),
    BISAYA("tl", "Bisaya"),
    ENGLISH("en", "English"),
}
