package ph.appbuilders.saklolo.ui

/** Tabs on the main bar. Call is the center button. There is no SOS destination. */
object MainNav {
    const val CONTACTS = "contacts"
    const val MESSAGES = "messages"
    const val ADD = "add"
    const val THREAD = "thread"
    const val CALL = "call"

    val tabs = listOf(CONTACTS, MESSAGES, CALL, ADD)

    fun barRoute(route: String): String = when (route) {
        MESSAGES -> MESSAGES
        ADD -> ADD
        else -> CONTACTS
    }
}
