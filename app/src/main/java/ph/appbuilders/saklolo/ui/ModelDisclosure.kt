package ph.appbuilders.saklolo.ui

/**
 * What this phone runs. Shown on the About screen and listed in the README.
 * No cloud model is called.
 */
object ModelDisclosure {
    const val TITLE = "On this phone"
    val LINES = listOf(
        "Whisper multilingual base (ggml-base-q5_1) transcribes speech on this phone. The language is Tagalog. Decoding uses greedy best_of 3, temperature 0 stepping by 0.2, entropy 2.4, and logprob -1.0. Each clip stops at about 1.5 times its length, and at least 3 seconds. Beam 5 runs only after a clip finishes faster than its own length.",
        "ggml-small-tl-q5_1.bin (whisper-small-tagalog) is never the default and is not bundled. It is selected only when a published SHA-256 matches. No digest is bundled, so this phone stays on the base model.",
        "A keyword rules engine decides emergencies. It is not a language model. After Whisper, a lexicon and phonetic match correct likely slips, spoken gate numbers become Gate N, and the line is sentence case. The higher urgency of the raw and corrected lines is kept.",
        "Gemma 3 1B runs only when that file is already on this phone and the phone has at least 3.4 GiB of RAM. It may clean non-emergency wording only, after the rules, with a 4 second limit. Emergency text is not sent to Gemma. Otherwise the rules engine is used.",
        "This app has no internet permission. Audio and messages stay on the phones nearby.",
    )

    fun mentions(name: String): Boolean = LINES.any { it.contains(name) }
}
