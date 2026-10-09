# Debug builds are not minified. Keep Nearby and whisper JNI if release minify is turned on later.
-keep class com.google.android.gms.nearby.** { *; }
-keep class ph.appbuilders.saklolo.stt.WhisperNative { *; }
