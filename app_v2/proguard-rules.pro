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

# SMBJ and event bus system
-keep class com.hierynomus.** { *; }
-dontwarn com.hierynomus.**

# MBassador event bus (used by SMBJ)
-keep class net.engio.mbassy.** { *; }
-dontwarn net.engio.mbassy.**

# Keep event handler methods
-keepclassmembers class * {
    @net.engio.mbassy.listener.Handler <methods>;
}

# Keep constructors needed for event subscription (critical for SMBJ)
-keepclassmembers class * {
    public <init>(net.engio.mbassy.subscription.SubscriptionContext);
}

# Keep all inner classes used in SMBJ
-keepattributes InnerClasses,Signature

# BouncyCastle (требуется для SMBJ)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

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

# JSch (SFTP) - uses reflection for crypto algorithms
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# Keep algorithm implementations loaded via reflection
-keep class * implements com.jcraft.jsch.Cipher { *; }
-keep class * implements com.jcraft.jsch.MAC { *; }
-keep class * implements com.jcraft.jsch.KeyExchange { *; }
-keep class * implements com.jcraft.jsch.Compression { *; }

# Apache Commons Net (FTP)
-keep class org.apache.commons.net.** { *; }
-dontwarn org.apache.commons.net.**

# Google Drive API - uses reflection and annotations
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.api.**
-dontwarn com.google.android.gms.**

# Keep OAuth and authentication classes
-keep class * extends com.google.api.client.json.GenericJson { *; }
-keep class * extends com.google.api.client.http.HttpTransport { *; }

# Dropbox SDK - uses reflection for API calls
-keep class com.dropbox.core.** { *; }
-dontwarn com.dropbox.core.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Dropbox model classes (used via reflection)
-keepclassmembers class com.dropbox.core.v2.** { *; }

# Microsoft MSAL (OneDrive) - uses reflection heavily
-keep class com.microsoft.identity.** { *; }
-dontwarn com.microsoft.identity.**

# Keep MSAL broker components
-keep class com.microsoft.identity.client.** { *; }
-keep class com.microsoft.identity.common.** { *; }

# Gson (used by cloud services and Retrofit)
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

# Keep all Kotlin data class component functions and field names
# This prevents obfuscation of constructor parameter names used by Gson
-keepclassmembers class * {
    public <init>(...);
}
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# OkHttp (используется облачными сервисами)
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Retrofit
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes EnclosingMethod
-keepattributes Signature
-keepattributes Exceptions

# Keep Retrofit interfaces and annotations
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Keep all data classes used by Retrofit (iTunes API models)
-keep class com.sza.fastmediasorter.data.remote.** { *; }

# Remove verbose/debug logging in release (v, d only).
# WARNING: Do NOT add w() or e() here — -assumenosideeffects removes the call
# entirely from bytecode, which would suppress error/warning reporting
# (e.g. Crashlytics Timber tree, exception chaining side-effects).
-assumenosideeffects class timber.log.Timber* {
    public static *** v(...);
    public static *** d(...);
}
-assumenosideeffects class timber.log.Timber$Tree {
    public *** v(...);
    public *** d(...);
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

# ===== Gson Serialization (Test Credentials & Config) =====
# Keep test credentials model (used in integration tests)
-keep class com.sza.fastmediasorter.data.repository.TestCredential** { *; }

# Keep all classes with @SerializedName annotations
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ===== UI Reflection (CommandPanelController) =====
# Keep Android framework popup menu fields (accessed via reflection for icons)
-keep class androidx.appcompat.view.menu.** { *; }
-keepclassmembers class androidx.appcompat.widget.PopupMenu {
    <fields>;
    <methods>;
}

# ===== Glide Custom Loaders & Models =====
# Keep custom Glide model loaders and data classes
-keep class com.sza.fastmediasorter.data.network.glide.** { *; }
-keep class com.sza.fastmediasorter.domain.model.NetworkFileData { *; }
-keep class com.sza.fastmediasorter.domain.model.GoogleDriveThumbnailData { *; }
-keep class com.sza.fastmediasorter.data.cloud.models.CloudThumbnailData { *; }

# Glide uses reflection to find @GlideModule classes
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# Keep Glide generated API
-keep class com.sza.fastmediasorter.GlideApp { *; }
-keep class com.sza.fastmediasorter.GlideRequest { *; }
-keep class com.sza.fastmediasorter.GlideRequests { *; }

# ===== ML Kit (Translation & OCR) =====
# ML Kit uses reflection for model loading and language detection
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep ML Kit model classes
-keep class com.google.mlkit.nl.translate.** { *; }
-keep class com.google.mlkit.vision.text.** { *; }
-keep class com.google.mlkit.nl.languageid.** { *; }

# ===== Tesseract OCR =====
# Keep native interface classes and JNI methods
-keep class com.googlecode.tesseract.android.** { *; }
-keep class cz.adaptech.tesseract4android.** { *; }

# Keep OCR data model classes
-keepclassmembers class cz.adaptech.tesseract4android.** {
    <init>(...);
    <methods>;
}

# ===== LocaleHelper & Localization Resources =====
# CRITICAL: Keep LocaleHelper for language switching (Android 13+)
-keep class com.sza.fastmediasorter.core.util.LocaleHelper { *; }
-keepclassmembers class com.sza.fastmediasorter.core.util.LocaleHelper {
    public <methods>;
}

# Keep all string resources for all locales (prevent R8 from removing translations)
-keep class **.R$string { *; }
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep SharedPreferences for language storage
-keepclassmembers class android.content.SharedPreferences {
    public <methods>;
}
