package ph.appbuilders.saklolo.stt

internal object WhisperNative {
    init {
        System.loadLibrary("saklolo_whisper")
    }

    @JvmStatic
    external fun initContext(modelPath: String): Long

    @JvmStatic
    external fun freeContext(contextPtr: Long)

    @JvmStatic
    external fun transcribe(contextPtr: Long, audio: FloatArray, threads: Int, language: String): String?
}
