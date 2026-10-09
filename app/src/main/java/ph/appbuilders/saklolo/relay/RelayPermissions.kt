package ph.appbuilders.saklolo.relay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Runtime permissions Nearby Connections needs before advertise or discovery.
 * Microphone and camera are separate; starting the relay does not wait on them.
 */
object RelayPermissions {
    fun required(sdkInt: Int): List<String> {
        val names = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (sdkInt >= Build.VERSION_CODES.S) {
            names += Manifest.permission.BLUETOOTH_SCAN
            names += Manifest.permission.BLUETOOTH_ADVERTISE
            names += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            names += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        return names
    }

    fun granted(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        required(sdkInt).all { name ->
            ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
        }
}
