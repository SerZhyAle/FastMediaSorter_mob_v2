# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep data classes used with Room
-keep class com.sza.fastmediasorter.data.local.db.** { *; }

# Keep model classes
-keep class com.sza.fastmediasorter.domain.model.** { *; }

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

# JSch (SFTP)
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# Apache Commons Net (FTP)
-keep class org.apache.commons.net.** { *; }
-dontwarn org.apache.commons.net.**

# Google Drive API
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.api.**
-dontwarn com.google.android.gms.**

# Dropbox SDK
-keep class com.dropbox.core.** { *; }
-dontwarn com.dropbox.core.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Microsoft MSAL (OneDrive)
-keep class com.microsoft.identity.** { *; }
-dontwarn com.microsoft.identity.**

# Gson (используется облачными сервисами)
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp (используется облачными сервисами)
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Retrofit (если используется в будущем)
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes EnclosingMethod
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Remove logging in release
-assumenosideeffects class timber.log.Timber* {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# Apache HTTP Client (используется транзитивными зависимостями)
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**

# Google Tink (используется MSAL/Nimbus JOSE)
-dontwarn com.google.crypto.tink.**

# OpenTelemetry (транзитивная зависимость MSAL)
-dontwarn io.opentelemetry.**

# FindBugs annotations
-dontwarn edu.umd.cs.findbugs.**

# Nimbus JOSE JWT (используется MSAL)
-dontwarn com.nimbusds.jose.**
-dontwarn com.yubico.yubikit.**
