package ph.appbuilders.saklolo.relay

import java.io.File
import java.io.InputStream

/**
 * Which voice clips to attach when a phone connects, and leftover temp files
 * from a previous process.
 */
object ClipRelay {
    const val RECENT_CLIP_MS = 30L * 60L * 1000L

    /** A brand-new send attaches its clip. A reconnect only resends a recent one. */
    fun includeClip(createdAtMillis: Long, now: Long, force: Boolean): Boolean {
        if (force) return true
        val age = now - createdAtMillis
        return age in 0..RECENT_CLIP_MS
    }

    fun deletePending(dir: File) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isFile && file.name.startsWith("pending-") && file.name.endsWith(".wav")) {
                file.delete()
            }
        }
    }

    fun copyStream(input: InputStream, dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            dest.outputStream().use { output -> input.copyTo(output) }
            dest.isFile && dest.length() > 0L
        } catch (_: Exception) {
            dest.delete()
            false
        }
    }
}
