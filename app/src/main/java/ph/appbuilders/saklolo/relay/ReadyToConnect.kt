package ph.appbuilders.saklolo.relay

/**
 * What must be true before Nearby advertise/discovery runs.
 * The name prompt and notification permission do not gate the relay.
 * Wi-Fi is required because P2P_CLUSTER uses the Wi-Fi radio even with no network.
 */
enum class SetupKey {
    BLUETOOTH,
    LOCATION,
    NEARBY,
    WIFI,
    MICROPHONE,
    CAMERA,
    NOTIFICATIONS,
    BATTERY,
}

data class SetupFacts(
    val bluetoothOn: Boolean,
    val locationOn: Boolean,
    val nearbyPermission: Boolean,
    val wifiOn: Boolean,
    val microphone: Boolean,
    val camera: Boolean,
    val notifications: Boolean,
    val batteryUnrestricted: Boolean,
    val nearbyDenied: Boolean = false,
    val microphoneDenied: Boolean = false,
    val cameraDenied: Boolean = false,
    val notificationsDenied: Boolean = false,
)

data class SetupRow(
    val key: SetupKey,
    val title: String,
    val required: Boolean,
    val ok: Boolean,
    val permanentlyDenied: Boolean = false,
)

object ReadyToConnect {
    const val TITLE = "Ready to connect"

    fun rows(facts: SetupFacts): List<SetupRow> = listOf(
        row(SetupKey.BLUETOOTH, "Bluetooth on", required = true, ok = facts.bluetoothOn),
        row(SetupKey.LOCATION, "Location on", required = true, ok = facts.locationOn),
        row(
            SetupKey.NEARBY,
            "Nearby devices permission",
            required = true,
            ok = facts.nearbyPermission,
            permanentlyDenied = facts.nearbyDenied,
        ),
        row(SetupKey.WIFI, "Wi-Fi on", required = true, ok = facts.wifiOn),
        row(
            SetupKey.MICROPHONE,
            "Microphone",
            required = false,
            ok = facts.microphone,
            permanentlyDenied = facts.microphoneDenied,
        ),
        row(
            SetupKey.CAMERA,
            "Camera",
            required = false,
            ok = facts.camera,
            permanentlyDenied = facts.cameraDenied,
        ),
        row(
            SetupKey.NOTIFICATIONS,
            "Notifications",
            required = false,
            ok = facts.notifications,
            permanentlyDenied = facts.notificationsDenied,
        ),
        row(SetupKey.BATTERY, "Battery optimization off", required = false, ok = facts.batteryUnrestricted),
    )

    fun requiredMissing(facts: SetupFacts): Int = rows(facts).count { it.required && !it.ok }

    /** Radios and Nearby permission only. Name and notifications are ignored. */
    fun shouldStart(facts: SetupFacts): Boolean = requiredMissing(facts) == 0

    fun show(seen: Boolean, requiredMissing: Int, pillTapped: Boolean): Boolean =
        pillTapped || !seen || requiredMissing > 0

    fun statusPill(requiredMissing: Int, inRange: Int): String =
        if (requiredMissing > 0) "Setup needed ($requiredMissing)" else "Ready · $inRange in range"

    fun permissionLine(facts: SetupFacts): String =
        "bt=${bit(facts.bluetoothOn)} location=${bit(facts.locationOn)} nearby=${bit(facts.nearbyPermission)} " +
            "wifi=${bit(facts.wifiOn)} mic=${bit(facts.microphone)} camera=${bit(facts.camera)} " +
            "notifications=${bit(facts.notifications)} battery=${bit(facts.batteryUnrestricted)}"

    private fun bit(on: Boolean): String = if (on) "1" else "0"

    private fun row(
        key: SetupKey,
        title: String,
        required: Boolean,
        ok: Boolean,
        permanentlyDenied: Boolean = false,
    ) = SetupRow(key, title, required, ok, permanentlyDenied && !ok)
}
