# Whisper JNI class name is part of the native symbol.
-keep class ph.appbuilders.saklolo.stt.WhisperNative { *; }

-keep class com.google.android.gms.nearby.** { *; }
-dontwarn com.google.android.gms.nearby.**

-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**

-keep class ph.appbuilders.saklolo.triage.Urgency { *; }
-keepclassmembers enum ph.appbuilders.saklolo.triage.Urgency {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

-keep class ph.appbuilders.saklolo.model.** { *; }
-keep class ph.appbuilders.saklolo.data.** { *; }
