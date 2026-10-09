package ph.appbuilders.saklolo.relay

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import ph.appbuilders.saklolo.SakloloRuntime

class RelayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        SakloloRuntime.get(application).ensureRelay()
        return START_STICKY
    }
}
