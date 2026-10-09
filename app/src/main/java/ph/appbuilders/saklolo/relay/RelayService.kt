package ph.appbuilders.saklolo.relay

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import ph.appbuilders.saklolo.SakloloRuntime

class RelayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val runtime = SakloloRuntime.get(application)
        val started = runRelayServiceStart(
            startForeground = { promoteToForeground() },
            ensureRelay = { runtime.ensureRelay() },
            onForegroundFailure = { error ->
                Log.i(BLINK, "startForeground failure ${error.javaClass.simpleName} message=${error.message}")
            },
            onFailure = { error ->
                Log.i(BLINK, "relay start failure ${error.javaClass.simpleName} message=${error.message}")
                runtime.noteRelayStartFailed(relayStartFailureMessage(error))
                stopSelf()
            },
        )
        return if (started) START_STICKY else START_NOT_STICKY
    }

    private fun promoteToForeground() {
        val notification = RelayNotifications.build(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                RelayNotifications.ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(RelayNotifications.ID, notification)
        }
    }

    private companion object {
        const val BLINK = "BLINK"
    }
}

internal const val FOREGROUND_START_NOT_ALLOWED =
    "android.app.ForegroundServiceStartNotAllowedException"

internal fun relayStartFailureMessage(error: Throwable): String {
    val kind = when {
        error is SecurityException -> "security"
        isForegroundStartNotAllowed(error) -> "foreground-not-allowed"
        else -> "error"
    }
    val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
    return "Relay could not start ($kind): $detail"
}

internal fun isForegroundStartNotAllowed(error: Throwable): Boolean =
    error.javaClass.name == FOREGROUND_START_NOT_ALLOWED

/**
 * Promotes to the foreground, then starts Nearby. A notification failure is
 * logged and does not skip the relay. A failure inside [ensureRelay] is
 * reported and never rethrown.
 */
@SuppressLint("NewApi")
internal fun runRelayServiceStart(
    startForeground: () -> Unit,
    ensureRelay: () -> Unit,
    onForegroundFailure: (Throwable) -> Unit = {},
    onFailure: (Throwable) -> Unit,
): Boolean {
    try {
        startForeground()
    } catch (error: Exception) {
        onForegroundFailure(error)
    }
    return try {
        ensureRelay()
        true
    } catch (error: Exception) {
        onFailure(error)
        false
    }
}
