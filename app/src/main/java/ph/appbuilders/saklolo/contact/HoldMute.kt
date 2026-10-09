package ph.appbuilders.saklolo.contact

/** Mute blocks a new hold-to-talk capture. A capture already running is dropped, not sent. */
object HoldMute {
    fun allowStart(muted: Boolean): Boolean = !muted

    fun dropInFlight(muted: Boolean): Boolean = muted
}
