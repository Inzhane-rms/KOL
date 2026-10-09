package ph.appbuilders.saklolo.relay

/**
 * Demo control so a three-phone hop can be forced. Names match exactly,
 * ignoring case. When [restrict] is off, every B-LINK phone is allowed.
 */
data class PeerFilter(
    val restrict: Boolean = false,
    val allowedNames: Set<String> = emptySet(),
) {
    fun allows(name: String): Boolean {
        if (!restrict) return true
        return allowedNames.any { it.equals(name.trim(), ignoreCase = true) }
    }
}

fun parseAllowlist(raw: String): Set<String> =
    raw.split(',', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
