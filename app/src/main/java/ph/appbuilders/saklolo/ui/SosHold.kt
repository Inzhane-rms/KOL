package ph.appbuilders.saklolo.ui

import kotlin.math.sqrt

/** Finger must travel this far past the SOS button before a short hold cancels. */
internal const val CANCEL_PAST_EDGE_DP = 64

/** A release at or after this duration sends, even if the finger has left the button. */
internal const val SEND_AFTER_RECORDING_MS = 3_000L

/** A cancelled clip can be restored until this window ends. */
internal const val CANCEL_UNDO_MS = 5_000L

/**
 * Lifting the finger releases the clip for transcription.
 * A short hold cancels only once the finger is at least [CANCEL_PAST_EDGE_DP] past the button.
 * After [SEND_AFTER_RECORDING_MS], a release sends no matter where the finger is.
 */
internal enum class SosHoldEnd { Continue, Release, Cancel }

internal fun distancePastButtonEdge(x: Float, y: Float, sizePx: Float): Float {
    val center = sizePx / 2f
    val dx = x - center
    val dy = y - center
    return (sqrt(dx * dx + dy * dy) - center).coerceAtLeast(0f)
}

internal fun sosHoldEnd(
    pointerPressed: Boolean,
    distancePastEdgePx: Float,
    cancelSlopPx: Float,
    elapsedMs: Long,
    cancelled: Boolean,
): SosHoldEnd {
    if (cancelled) return SosHoldEnd.Cancel
    val longEnough = elapsedMs >= SEND_AFTER_RECORDING_MS
    if (!pointerPressed && longEnough) return SosHoldEnd.Release
    if (longEnough) return SosHoldEnd.Continue
    if (distancePastEdgePx >= cancelSlopPx) return SosHoldEnd.Cancel
    if (!pointerPressed) return SosHoldEnd.Release
    return SosHoldEnd.Continue
}

internal fun undoExpired(elapsedSinceCancelMs: Long, windowMs: Long = CANCEL_UNDO_MS): Boolean =
    elapsedSinceCancelMs >= windowMs
