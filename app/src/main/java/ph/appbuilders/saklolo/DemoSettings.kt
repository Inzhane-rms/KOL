package ph.appbuilders.saklolo

import android.content.Context
import android.os.Build
import ph.appbuilders.saklolo.relay.PeerFilter
import ph.appbuilders.saklolo.relay.parseAllowlist
import ph.appbuilders.saklolo.stt.SpeechLanguage

class DemoSettings(context: Context) {
    private val prefs = context.getSharedPreferences("saklolo_demo", Context.MODE_PRIVATE)

    var deviceName: String
        get() = prefs.getString(KEY_NAME, null) ?: Build.MODEL ?: "B-LINK"
        set(value) {
            prefs.edit().putString(KEY_NAME, value.trim().ifEmpty { "B-LINK" }).apply()
        }

    var displayName: String
        get() = prefs.getString(KEY_DISPLAY, null)?.trim()?.takeIf { it.isNotEmpty() } ?: "Me"
        set(value) {
            prefs.edit().putString(KEY_DISPLAY, value.trim().ifEmpty { "Me" }).apply()
        }

    var lastChatReadMillis: Long
        get() = prefs.getLong(KEY_CHAT_READ, 0L)
        set(value) {
            prefs.edit().putLong(KEY_CHAT_READ, value).apply()
        }

    var restrictPeers: Boolean
        get() = prefs.getBoolean(KEY_RESTRICT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_RESTRICT, value).apply()
        }

    var allowlistRaw: String
        get() = prefs.getString(KEY_ALLOW, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_ALLOW, value).apply()
        }

    var language: SpeechLanguage
        get() = prefs.getString(KEY_LANG, SpeechLanguage.TAGALOG.name)
            ?.let { runCatching { SpeechLanguage.valueOf(it) }.getOrNull() }
            ?: SpeechLanguage.TAGALOG
        set(value) {
            prefs.edit().putString(KEY_LANG, value.name).apply()
        }

    fun peerFilter(): PeerFilter = PeerFilter(restrictPeers, parseAllowlist(allowlistRaw))

    companion object {
        private const val KEY_NAME = "name"
        private const val KEY_DISPLAY = "display_name"
        private const val KEY_CHAT_READ = "chat_read"
        private const val KEY_RESTRICT = "restrict"
        private const val KEY_ALLOW = "allow"
        private const val KEY_LANG = "lang"
    }
}
