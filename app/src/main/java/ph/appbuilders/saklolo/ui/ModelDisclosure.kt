package ph.appbuilders.saklolo.ui

/**
 * What this phone runs. Shown on the About screen and listed in the README.
 * No cloud model is called.
 */
object ModelDisclosure {
    const val TITLE = "On this phone"
    val LINES = listOf(
        "Whisper multilingual base (ggml-base-q5_1) transcribes speech on this phone. The language is Tagalog.",
        "A keyword rules engine decides emergencies. It is not a language model.",
        "Gemma 3 1B runs only when that file is already on this phone. Otherwise the rules engine is used.",
        "This app has no internet permission. Audio and messages stay on the phones nearby.",
    )

    fun mentions(name: String): Boolean = LINES.any { it.contains(name) }
}
