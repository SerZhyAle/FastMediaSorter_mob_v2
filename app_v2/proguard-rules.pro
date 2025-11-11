# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep data classes used with Room
-keep class com.sza.fastmediasorter_v2.data.local.db.** { *; }

# Keep model classes
-keep class com.sza.fastmediasorter_v2.domain.model.** { *; }

# ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# SMBJ
-keep class com.hierynomus.** { *; }
-dontwarn com.hierynomus.**

# BouncyCastle (требуется для SMBJ)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keepattributes Signature
-keepattributes InnerClasses

# SSHJ
-keep class net.schmizz.** { *; }
-dontwarn net.schmizz.**
-dontwarn sun.security.x509.**
-dontwarn javax.el.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Hilt
-keepclassmembers,allowobfuscation class * {
    @javax.inject.* *;
    @dagger.* *;
    <init>();
}
