package ph.appbuilders.saklolo.stt

/**
 * Decode settings for on-device Whisper.
 *
 * Language is Tagalog. Temperature stays at 0 and the whisper.cpp fallback
 * (temperature_inc) is left at its default. no_context is on.
 *
 * Beam 5 was not turned on. It was not timed on a phone, and a hold-to-talk
 * clip has to stay short enough for a live caption. Greedy sampling with
 * best_of 3 is the fallback.
 */
object WhisperPrompt {
    const val LANGUAGE = "tl"
    const val THREADS = 4
    const val BEST_OF = 3
    const val USE_BEAM = false
    const val TEXT =
        "Nasaan ka? Nandito ako sa gate. Nahimatay si Ana, hindi makahinga, naipit, siksikan. " +
            "Tawagan mo ang medic. Barkada, sige, okay lang."

    fun text(names: List<String>): String {
        val clean = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) return TEXT
        return "$TEXT ${clean.joinToString(", ")}."
    }
}
