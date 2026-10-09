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
        val started = runRelayServiceStart(
            startForeground = { promoteToForeground() },
            ensureRelay = { SakloloRuntime.get(application).ensureRelay() },
            onFailure = { error ->
                Log.e(TAG, "Could not start the relay", error)
                SakloloRuntime.get(application).noteRelayStartFailed(relayStartFailureMessage(error))
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
        const val TAG = "RelayService"
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
 * Runs foreground promotion and then the relay. [SecurityException] and
 * [android.app.ForegroundServiceStartNotAllowedException] are caught here, and
 * so is every other [Exception]. The failure is reported and never rethrown.
 */
@SuppressLint("NewApi")
internal fun runRelayServiceStart(
    startForeground: () -> Unit,
    ensureRelay: () -> Unit,
    onFailure: (Throwable) -> Unit,
): Boolean {
    return try {
        startForeground()
        ensureRelay()
        true
    } catch (error: SecurityException) {
        onFailure(error)
        false
    } catch (error: android.app.ForegroundServiceStartNotAllowedException) {
        onFailure(error)
        false
    } catch (error: Exception) {
        onFailure(error)
        false
    }
}
