package ph.appbuilders.saklolo.relay

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Reads the live radios and permissions the checklist and the relay gate share. */
object SetupProbe {
    fun read(context: Context): SetupFacts {
        val sdk = Build.VERSION.SDK_INT
        return SetupFacts(
            bluetoothOn = bluetoothOn(context),
            locationOn = locationOn(context),
            nearbyPermission = RelayPermissions.granted(context, sdk),
            wifiOn = wifiOn(context),
            microphone = granted(context, Manifest.permission.RECORD_AUDIO),
            camera = granted(context, Manifest.permission.CAMERA),
            notifications = sdk < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS),
            batteryUnrestricted = batteryUnrestricted(context),
        )
    }

    fun missingNearby(context: Context, sdk: Int = Build.VERSION.SDK_INT): List<String> {
        val missing = RelayPermissions.required(sdk).filter { !granted(context, it) }.toMutableList()
        val fine = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!RelayPermissions.locationSatisfied(sdk, fine, coarse)) {
            if (!fine) missing += Manifest.permission.ACCESS_FINE_LOCATION
            if (!coarse) missing += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        return missing.distinct()
    }

    fun permanentlyDenied(context: Context, permissions: List<String>, asked: Boolean): Boolean {
        if (!asked || permissions.isEmpty()) return false
        val activity = context as? Activity ?: return false
        val missing = permissions.filter { !granted(context, it) }
        return missing.isNotEmpty() && missing.all {
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
    }

    private fun bluetoothOn(context: Context): Boolean = try {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter?.isEnabled == true
    } catch (_: SecurityException) {
        false
    }

    private fun locationOn(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun wifiOn(context: Context): Boolean = try {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifi?.isWifiEnabled == true
    } catch (_: Exception) {
        false
    }

    private fun batteryUnrestricted(context: Context): Boolean = try {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        power.isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: Exception) {
        false
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
