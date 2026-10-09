package ph.appbuilders.saklolo

/**
 * Settings > Delete all data. Stop the relay, then clear the phone.
 * The Whisper model file stays. It is copied from assets and is not user data.
 */
object UserDataErase {
    const val MODEL_FILE = "ggml-base-q5_1.bin"
    const val UNDO = "This can't be undone."
    const val STAYS = "Messages you already sent stay on the other person's phone."

    fun keep(name: String): Boolean =
        name == MODEL_FILE || name == "ggml-small-tl-q5_1.bin" || name.endsWith(".task")

    fun run(
        stopRelay: () -> Unit,
        clearRoom: () -> Unit,
        clearClips: () -> Unit,
        clearPrefs: () -> Unit,
        clearReplies: () -> Unit,
        newDeviceId: () -> String,
    ): String {
        stopRelay()
        clearRoom()
        clearClips()
        clearPrefs()
        clearReplies()
        return newDeviceId()
    }
}
