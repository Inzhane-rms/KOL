package ph.appbuilders.saklolo.stt

enum class SpeechLanguage(val whisperCode: String, val label: String) {
    TAGALOG("tl", "Tagalog"),
    BISAYA("auto", "Bisaya"),
    ENGLISH("en", "English"),
    AUTO("auto", "Auto"),
}
