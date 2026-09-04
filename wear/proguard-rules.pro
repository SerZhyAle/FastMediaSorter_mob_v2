# Add any ProGuard configurations for Wear OS here
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
# ===== SMBJ (SMB Client) =====
# Keep SMBJ classes
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

# Keep all inner classes used in SMBJ and annotations/signatures
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# ===== Gson Serialization & Reflection =====
-dontwarn sun.misc.**
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep all Kotlin data class constructor parameter names used by Gson
-keepclassmembers class * {
    public <init>(...);
}
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}


# ===== Missing Runtime Classes (not available on Android) =====
# javax.el - Expression Language (used by mbassy but not on Android)
-dontwarn javax.el.**

# JGSS - Kerberos/GSS-API (used by SMBJ for Kerberos auth, not available on Android)
-dontwarn org.ietf.jgss.**

# ===== BouncyCastle (required for SMBJ encryption) =====
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ===== Hilt Dependency Injection =====
-keepclassmembers,allowobfuscation class * {
    @javax.inject.* *;
    @dagger.* *;
    <init>();
}

# ===== Kotlin Coroutines =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ===== Wear OS Compose =====
-keep class androidx.wear.compose.** { *; }
-dontwarn androidx.wear.compose.**

# ===== Data Classes =====
-keep class com.sza.fastmediasorter.wear.data.** { *; }
-keep class com.sza.fastmediasorter.wear.domain.model.** { *; }

# ===== Glide =====
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}

# ===== OkHttp (transitive dependency) =====
-dontwarn okhttp3.**
-dontwarn okio.**

# ===== Remove debug logging in release =====
-assumenosideeffects class timber.log.Timber* {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}