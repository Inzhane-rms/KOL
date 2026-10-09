package ph.appbuilders.saklolo.relay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Runtime permissions Nearby Connections needs before advertise or discovery.
 * Location is still requested on every version. On API 33+ approximate
 * (coarse) location is enough to start the relay. On API 31–32 precise
 * location is still required. Microphone and camera are separate.
 */
object RelayPermissions {
    fun required(sdkInt: Int): List<String> {
        val names = mutableListOf<String>()
        if (sdkInt >= Build.VERSION_CODES.S) {
            names += Manifest.permission.BLUETOOTH_SCAN
            names += Manifest.permission.BLUETOOTH_ADVERTISE
            names += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            names += Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            names += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return names
    }

    /** API 33+ accepts coarse or fine. Older versions accept only fine. */
    fun locationSatisfied(sdkInt: Int, fine: Boolean, coarse: Boolean): Boolean {
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) return fine || coarse
        return fine
    }

    /** Android 12 granted Approximate and not Precise. The relay cannot start. */
    fun needsPreciseChoice(sdkInt: Int, fine: Boolean, coarse: Boolean): Boolean =
        sdkInt in Build.VERSION_CODES.S until Build.VERSION_CODES.TIRAMISU && coarse && !fine

    /**
     * Copy for the settings card when location is why the relay cannot start.
     * API 33+ with both fine and coarse denied, or API 31–32 with only coarse.
     */
    fun locationWarning(sdkInt: Int, fine: Boolean, coarse: Boolean): String? = when {
        sdkInt >= Build.VERSION_CODES.TIRAMISU && !fine && !coarse ->
            "Allow location so nearby phones can find this one."
        needsPreciseChoice(sdkInt, fine, coarse) ->
            "Choose Precise so nearby phones can find this one."
        else -> null
    }

    fun granted(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        if (required(sdkInt).any { !isGranted(context, it) }) return false
        return locationSatisfied(sdkInt, fine(context), coarse(context))
    }

    fun needsPreciseChoice(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        needsPreciseChoice(sdkInt, fine(context), coarse(context))

    fun locationWarning(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): String? =
        locationWarning(sdkInt, fine(context), coarse(context))

    private fun fine(context: Context) = isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    private fun coarse(context: Context) = isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
