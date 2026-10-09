package ph.appbuilders.saklolo.relay

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object BatteryLevel {
    /** Unknown readings stay at 100 so a missing sticky intent does not darken the radios. */
    fun percent(level: Int, scale: Int): Int {
        if (level < 0 || scale <= 0) return 100
        return ((level * 100f) / scale).toInt().coerceIn(0, 100)
    }

    fun read(context: Context): Int = try {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        percent(level, scale)
    } catch (_: Exception) {
        100
    }
}
