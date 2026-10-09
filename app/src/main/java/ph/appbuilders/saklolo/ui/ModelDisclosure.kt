package ph.appbuilders.saklolo.ui

/**
 * What this phone runs. Shown on the About screen and listed in the README.
 * No cloud model is called.
 */
object ModelDisclosure {
    const val TITLE = "On this phone"
    val LINES = listOf(
        "Whisper multilingual base (ggml-base-q5_1) transcribes speech on this phone. The language is Tagalog. Decoding uses beam 5, temperature 0 stepping by 0.2, entropy 2.4, and logprob -1.0. If a clip runs longer than its own length times 1.5, later clips use greedy best_of 3.",
        "If ggml-small-tl-q5_1.bin is already in this phone's files, that model is used instead. It is LWobole/whisper-small-tagalog (Apache-2.0), converted with whisper.cpp and quantized q5_1. It is not bundled.",
        "A keyword rules engine decides emergencies. It is not a language model. After Whisper, a lexicon and phonetic match correct likely slips, spoken gate numbers become Gate N, and the line is sentence case. The higher urgency of the raw and corrected lines is kept.",
        "Gemma 3 1B runs only when that file is already on this phone and the phone has at least 3.4 GiB of RAM. It may clean non-emergency wording only, after the rules, with a 4 second limit. Emergency text is not sent to Gemma. Otherwise the rules engine is used.",
        "This app has no internet permission. Audio and messages stay on the phones nearby.",
    )

    fun mentions(name: String): Boolean = LINES.any { it.contains(name) }
}
