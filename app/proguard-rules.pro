# kotlinx.serialization keeps generated serializers reachable through reflection.
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
-keepclasseswithmembers class ** {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Media3 / ExoPlayer resolves renderers and extractors reflectively.
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# WorkManager workers are instantiated by name.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
