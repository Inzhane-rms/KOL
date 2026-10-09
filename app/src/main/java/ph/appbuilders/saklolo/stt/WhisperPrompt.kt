package ph.appbuilders.saklolo.stt

/**
 * Concert speech is forced to Tagalog. The prompt lists the words people shout
 * in Tagalog and English so the base model keeps them.
 *
 * Beam search at size 5 was not turned on. On multilingual base it runs the
 * decoder several times, and this build was not timed on a Camon 40. Greedy
 * sampling with best_of 5 is the whisper.cpp greedy default and is what the
 * native layer uses.
 */
object WhisperPrompt {
    const val LANGUAGE = "tl"
    const val TEXT =
        "Concert, emergency, tulong, nahimatay, siksikan, Gate, naipit, hindi makahinga, " +
            "nawawala, medic, ambulansya, dugo, nasaktan, exit, crowd, help, fainted, " +
            "can't breathe, trapped, lost."

    val WORDS = listOf(
        "nahimatay",
        "siksikan",
        "tulong",
        "Gate",
        "naipit",
        "hindi makahinga",
        "nawawala",
        "medic",
    )
}
