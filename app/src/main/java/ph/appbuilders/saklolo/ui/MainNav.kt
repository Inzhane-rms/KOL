package ph.appbuilders.saklolo.ui

/** Tabs on the floating bar. Home is the start. Chat and Call are pushed screens. There is no SOS destination. */
object MainNav {
    const val HOME = "home"
    const val CONTACTS = "contacts"
    const val MESSAGES = "messages"
    const val ADD = "add"
    const val THREAD = "thread"
    const val CALL = "call"

    val tabs = listOf(HOME, CONTACTS, MESSAGES, ADD)

    fun barRoute(route: String): String = when (route) {
        CONTACTS -> CONTACTS
        MESSAGES, THREAD -> MESSAGES
        ADD -> ADD
        else -> HOME
    }
}
