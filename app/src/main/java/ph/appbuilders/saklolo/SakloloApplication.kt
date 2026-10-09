package ph.appbuilders.saklolo

import android.app.Application

class SakloloApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SakloloRuntime.get(this)
    }
}
