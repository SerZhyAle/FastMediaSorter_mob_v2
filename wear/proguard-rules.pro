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

# ===== Durable enum constant names (S2596) =====
# Each name below is a storage key: it is written with `.name` into DataStore, a preferences
# value or a serialized snapshot, and read back by name, so a rename by R8 makes the stored
# value unreadable and the reader falls silently back to a default. The rules are addressed
# rather than resting on the two package keeps above, because those cover only two of the five
# packages these enums live in - a real standardRelease mapping showed the other seven renamed.
# The list is maintained by scripts/quality/assert-enum-persistence-contract.ps1, which fails
# the build when a new durable enum appears without its rule.
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.browse.BrowseSortOrder {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.documents.DocumentFontSize {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.documents.WearDocumentFormat {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.game.GameDifficulty {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.game.GameEnemyType {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.game.GameStatus {
    <fields>;
}
# S2840: three more, on two different grounds. UnitSystem and WearGeometryMode are written with `.name`
# into DataStore by WearAppearancePreferencesImpl and read back by matching that name against the
# entries, each degrading to a default instead of throwing - so a rename silently resets the owner's
# choice. HomeSectionId names a Compose test tag (WearTestTags.homeSection), and the wear pre-release
# sweep selects by that tag on a MINIFIED build, so a rename turns the sweep into a false refusal. All
# three also fall inside the wear.domain.model package keep above; the addressed rules are here for the
# reason the 22 rules around them are, that coverage must not depend on a broad rule surviving the next
# edit of this file.
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.HomeSectionId {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.LastUsedKind {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.MediaType {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.NetworkSourceType {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.PowerSavingTrigger {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.VideoScaleMode {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.VoiceNoteDeliveryState {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendPolicy {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.WearAppId {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.WearColorScheme {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.WearContentType {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.UnitSystem {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.WearGeometryMode {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.WearDestinationId {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.WearPlaybackCommand {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.WearTileKind {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.WearViewMode {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSection {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.ui.streams.StreamFilterKind {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.ui.streams.StreamSortOrder {
    <fields>;
}
# S3283: two more that store a constant name, on two different grounds. BloodPressureSource is a Room
# column - BloodPressureHistoryEntity keeps `source` as the member name and reads it back with
# `fromStored`, degrading to MANUAL, so a rename rewrites the origin of measurements already on the
# watch. SosMode is the receiving half of the phone's Data Layer command: the payload is the member name
# and nothing else, decoded by WatchWearListenerService with `fromNameOrDefault`, so a rename on either
# side turns a siren request into the default mode instead of a failure.
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.BloodPressureSource {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.wear.domain.model.SosMode {
    <fields>;
}
