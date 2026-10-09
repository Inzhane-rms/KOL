package ph.appbuilders.saklolo.stt

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The speech model is bundled in the APK when Gradle downloaded it at build time.
 * If that asset is missing, the app downloads the same file once into app storage.
 * Transcription itself never contacts the network.
 */
object ModelInstaller {
    const val FILE_NAME = "ggml-base-q5_1.bin"
    const val ASSET_PATH = "models/$FILE_NAME"
    const val EXPECTED_BYTES = 59_707_625L
    const val EXPECTED_SHA256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"
    const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"

    fun installedFile(context: Context): File = File(context.filesDir, FILE_NAME)

    fun isReady(context: Context): Boolean = installedFile(context).let { it.exists() && it.length() == EXPECTED_BYTES }

    suspend fun ensure(context: Context, onProgress: (String) -> Unit): File = withContext(Dispatchers.IO) {
        val dest = installedFile(context)
        if (dest.exists() && dest.length() == EXPECTED_BYTES) return@withContext dest
        val temp = File(context.filesDir, "$FILE_NAME.partial")
        if (temp.exists()) temp.delete()
        val copied = copyAsset(context, temp, onProgress)
        if (!copied) {
            download(temp, onProgress)
        }
        val hash = sha256(temp)
        if (temp.length() != EXPECTED_BYTES || hash != EXPECTED_SHA256) {
            temp.delete()
            error("Speech model failed verification. Expected the Whisper base q5_1 file.")
        }
        if (dest.exists()) dest.delete()
        if (!temp.renameTo(dest)) {
            temp.copyTo(dest, overwrite = true)
            temp.delete()
        }
        dest
    }

    private fun copyAsset(context: Context, dest: File, onProgress: (String) -> Unit): Boolean {
        return try {
            context.assets.open(ASSET_PATH).use { input ->
                onProgress("Copying speech model onto this phone…")
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun download(dest: File, onProgress: (String) -> Unit) {
        onProgress("Downloading speech model once…")
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 60_000
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Model download failed (HTTP $code)")
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var readTotal = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        readTotal += n
                        if (total > 0) {
                            onProgress("Downloading speech model… ${(100 * readTotal / total).toInt()}%")
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
