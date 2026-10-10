package ph.appbuilders.saklolo.stt

/**
 * Decode settings for on-device Whisper.
 *
 * Language is Tagalog. Temperature starts at 0 and steps by 0.2. Entropy is
 * 2.4 and logprob is -1.0. no_context is on.
 *
 * Greedy best_of [BEST_OF] is the only decode. Each clip aborts at four times
 * its length, and at least 15 seconds. Beam size [BEAM] is not requested.
 * whisper.cpp keeps walking the temperature step while it is below 1.0, and it
 * stops early when entropy and logprob are inside the thresholds.
 */
object WhisperPrompt {
    const val LANGUAGE = "tl"
    const val THREADS = 4
    const val BEAM = 5
    const val BEST_OF = 3
    const val USE_BEAM = false
    const val TEXT =
        "Nasaan ka? Nandito ako sa gate. Nahimatay si Ana, hindi makahinga, naipit, siksikan. " +
            "Tawagan mo ang medic. Barkada, sige, okay lang."
    const val LEXICON =
        "nahimatay, siksikan, tulong, naipit, hindi makahinga, nawawala, gate, stage, CR, medic, " +
            "first aid, entrance, exit, VIP, barricade, papunta, nandito, saan, kita tayo."

    fun text(names: List<String>): String {
        val clean = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val body = "$TEXT $LEXICON"
        if (clean.isEmpty()) return body
        return "$body ${clean.joinToString(", ")}."
    }
}
