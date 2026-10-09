package ph.appbuilders.saklolo.contact

/** Mute blocks a new hold-to-talk capture. A capture already running is dropped, not sent. */
object HoldMute {
    fun allowStart(muted: Boolean): Boolean = !muted

    fun dropInFlight(muted: Boolean): Boolean = muted

    /** Placing or accepting a call leaves mute as it is. */
    fun keep(muted: Boolean): Boolean = muted

    /** Mute clears only when the call is over. */
    fun afterEnd(): Boolean = false
}
