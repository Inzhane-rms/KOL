package ph.appbuilders.saklolo.ui

/**
 * Lifting the finger on the SOS button releases the clip for transcription.
 * Sliding off the button cancels. A system cancel also cancels.
 */
internal enum class SosHoldEnd { Continue, Release, Cancel }

internal fun sosHoldEnd(
    pointerPressed: Boolean,
    outOfBounds: Boolean,
    cancelled: Boolean,
): SosHoldEnd = when {
    cancelled -> SosHoldEnd.Cancel
    outOfBounds -> SosHoldEnd.Cancel
    !pointerPressed -> SosHoldEnd.Release
    else -> SosHoldEnd.Continue
}
