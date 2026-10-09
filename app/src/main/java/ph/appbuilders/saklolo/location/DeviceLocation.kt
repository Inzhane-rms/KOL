package ph.appbuilders.saklolo.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat

object DeviceLocation {
    @Volatile
    private var latest: Pair<Double, Double>? = null

    private var listening = false

    fun lastKnown(context: Context): Pair<Double, Double>? {
        latest?.let { return it }
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        val fix = providers.mapNotNull { provider ->
            try {
                if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
            } catch (_: Exception) {
                null
            }
        }.maxByOrNull { it.time }
        return fix?.let { store(it) }
    }

    fun start(context: Context) {
        if (listening || !hasPermission(context)) return
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { location -> store(location) }
        try {
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2_000L,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
                listening = true
            }
        } catch (_: Exception) {
        }
        lastKnown(context)
    }

    private fun store(location: Location): Pair<Double, Double> {
        val pair = location.latitude to location.longitude
        latest = pair
        return pair
    }

    private fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }
}
