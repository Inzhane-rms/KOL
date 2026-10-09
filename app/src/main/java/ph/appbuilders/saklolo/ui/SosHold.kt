package ph.appbuilders.saklolo.ui

/**
 * Sliding a finger off the SOS button is still a hold.
 * Recording ends on pointer-up or a real cancel.
 */
internal fun shouldEndSosHold(
    pointerPressed: Boolean,
    outOfBounds: Boolean,
    cancelled: Boolean,
): Boolean = when {
    cancelled -> true
    !pointerPressed -> true
    outOfBounds -> false
    else -> false
}
