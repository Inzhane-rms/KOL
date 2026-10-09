package ph.appbuilders.saklolo.relay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import ph.appbuilders.saklolo.R

object RelayNotifications {
    const val ID = 41
    private const val CHANNEL = "saklolo_relay"

    fun build(context: Context): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL,
                "Nearby relay",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows while KOL is relaying to nearby phones"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_saklolo)
            .setContentTitle("KOL")
            .setContentText("KOL is relaying to nearby phones")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
