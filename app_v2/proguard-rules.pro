# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Large third-party SDKs should use their own consumer rules or targeted keep contracts.
# Blanket app-side package keeps cripple R8 shrinking/obfuscation and inflate release heap/dex.

# Keep Gson-serialized persistence models that lack @SerializedName: without this R8 renames
# their fields, breaking cross-version JSON restore (Drive backup, trash metadata, game state) - S0737/S0719.
# S1157: narrowed from `-keep class .. { *; }` to the FIELD surface. Gson reflects over instance
# fields and enum constants (static fields), never over methods - so <fields> preserves the wire
# format while every method (mappers, use cases, data-class boilerplate) goes back to R8. The
# serialized roots stay referenced from code (fromJson(X::class.java)), so the classes survive.
-keepclassmembers class com.sza.fastmediasorter.domain.usecase.Backup** { <fields>; }
-keep class com.sza.fastmediasorter.data.model.TrashMetadata { *; }
-keepclassmembers class com.sza.fastmediasorter.domain.game.** { <fields>; }

# Cached file lists persist MediaFile JSON across app updates. Keep its field names stable so a
# release mapping change cannot turn an existing cache blob into an incomplete object.
-keep,allowoptimization class com.sza.fastmediasorter.domain.model.MediaFile {
    <fields>;
}

# The startup settings dump reflects every AppSettings field for support diagnostics. Keep the
# reflective fields, then retain only their names; the class name remains obfuscatable.
-keep,allowoptimization,allowobfuscation class com.sza.fastmediasorter.domain.model.AppSettings {
    <fields>;
}
-keepclassmembernames class com.sza.fastmediasorter.domain.model.AppSettings {
    <fields>;
}

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

# Google Drive API - keep only annotation-driven JSON fields instead of whole SDK packages.
-keepclassmembers class * extends com.google.api.client.util.GenericData {
    @com.google.api.client.util.Key <fields>;
}
-keepclassmembers class * extends com.google.api.client.json.GenericJson {
    @com.google.api.client.util.Key <fields>;
}
-dontwarn com.google.api.**

# Dropbox SDK - targeted keep rules (we only use files/users/auth APIs, NOT team/teamcommon).
# DO NOT use a blanket -keep class com.dropbox.core.** - it prevents R8 from stripping unused
# team management classes (GroupSummary, GroupManagementType, etc.) that cause dex2oat
# "Method processed more than once" warnings at install time.
-keep class com.dropbox.core.DbxRequestConfig { *; }
-keep class com.dropbox.core.DbxRequestConfig$Builder { *; }
-keep class com.dropbox.core.DbxException { *; }
-keep class com.dropbox.core.android.Auth { *; }
-keep class com.dropbox.core.oauth.DbxCredential { *; }
-keep class com.dropbox.core.http.OkHttp3Requestor { *; }
-keep class com.dropbox.core.v2.DbxClientV2 { *; }
-keep class com.dropbox.core.v2.files.** { *; }
-keep class com.dropbox.core.v2.users.** { *; }
-keep class com.dropbox.core.v2.auth.** { *; }
# Internal framework classes needed by SDK (request/response routing, error handling)
-keep class com.dropbox.core.v2.common.** { *; }
-keep class com.dropbox.core.json.** { *; }
-keep class com.dropbox.core.util.** { *; }
-keep class com.dropbox.core.http.** { *; }
-keep class com.dropbox.core.DbxApiException { *; }
-keep class com.dropbox.core.InvalidAccessTokenException { *; }
# team/teamcommon are intentionally NOT kept - R8 will strip them.
# This eliminates GroupSummary$Builder and related classes from the APK.
-dontwarn com.dropbox.core.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Microsoft MSAL (OneDrive)
-dontwarn com.microsoft.identity.**

# Gson (used by cloud services and Retrofit)
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
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

# OkHttp (used by cloud services)
-dontwarn okhttp3.**
-dontwarn okio.**

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

# Remove verbose/debug logging in release (v, d only).
# WARNING: Do NOT add w() or e() here - -assumenosideeffects removes the call
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

# ===== Android 15 edge-to-edge: strip deprecated system-bar color setters =====
# Google Play's Android-15 edge-to-edge check STATICALLY flags every invoke of the deprecated
# android.view.Window.setStatusBarColor / setNavigationBarColor, even when the caller guards it
# with if(SDK_INT<35). Confirmed: warning #2 is present on release 6222.158 (Material 1.14.0),
# attributed to the R8-inlined Material bottom-sheet helpers (o43.b/q43.b). The runtime guard is
# NOT enough for the static scanner, so the calls must be removed from release bytecode.
# Framework-level (not EdgeToEdgeUtils-level) so the strip survives R8 inlining of the Material
# wrappers and also covers androidx.activity.enableEdgeToEdge()'s own pre-29 setters.
# The app never calls these setters itself (grep-verified); bars stay transparent via
# android:statusBarColor/navigationBarColor in the app theme (pre-35 buckets).
-assumenosideeffects class android.view.Window {
    public void setStatusBarColor(int);
    public void setNavigationBarColor(int);
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

# ===== Gson Serialization =====
# S0385: removed the force-keep for data.repository.TestCredential** - those models are
# debug-only (consumed only under BuildConfig.DEBUG), so R8 now strips them in release
# instead of shipping a credentials-shaped model to production users.

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

# Glide's generated facade (GlideApp / GlideRequest / GlideRequests) no longer exists: the KSP
# processor generates only GeneratedAppGlideModuleImpl, and nothing in this app referenced the
# facade anyway. The rules that named it were also pointing at the wrong package - kapt had emitted
# it under .di - so they matched nothing even before the migration (S1338 phase 10).

# ===== ML Kit (Translation & OCR) =====
-dontwarn com.google.mlkit.**

# ===== Tesseract OCR =====
# Keep native interface classes and JNI methods
-keep class com.googlecode.tesseract.android.** { *; }
-keep class cz.adaptech.tesseract4android.** { *; }

# Keep OCR data model classes
-keepclassmembers class cz.adaptech.tesseract4android.** {
    <init>(...);
    <methods>;
}

# ===== WebView JS Bridge =====
# Keep methods annotated with @JavascriptInterface so R8 doesn't rename them.
# Without this rule, EpubSelectionBridge.onSelectionChanged() is obfuscated in release
# builds and the JS bridge silently stops working.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
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

# Keep drawable resources referenced in layouts (prevent resource shrinking)
-keep class **.R$drawable { *; }
-keepclassmembers class **.R$drawable {
    public static <fields>;
}

# Keep SharedPreferences for language storage
-keepclassmembers class android.content.SharedPreferences {
    public <methods>;
}

# ===== OpenXR native bridge (VR flavor) =====
# Native C++ looks up XrRenderCallback.onRenderEye(IIII)V by string name via
# GetMethodID - R8 must not rename the interface or its method.
# Also keep OpenXrNative: its `external` methods are resolved by JNI class name.
-keep class com.sza.fastmediasorter.vr.openxr.XrRenderCallback { *; }
-keep interface com.sza.fastmediasorter.vr.openxr.XrRenderCallback { *; }
-keep class com.sza.fastmediasorter.vr.openxr.OpenXrNative { *; }
-keepclassmembers class com.sza.fastmediasorter.vr.openxr.OpenXrNative {
    native <methods>;
}
# S1674: these enum member names are persisted by Room, DataStore, or SharedPreferences and
# must remain readable after an update built with a different R8 mapping.
-keepclassmembernames enum com.sza.fastmediasorter.data.cloud.CloudProvider {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.domain.model.DisplayMode {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.domain.model.MediaType {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.domain.model.PrefetchCacheMultiplier {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.domain.model.ResourceGridCellSize {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.domain.model.ResourceType {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.domain.model.ScreenType {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.domain.model.SortMode {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.domain.model.StreamingCacheCleanupMode {
    <fields>;
}
# S2364: both of the rules below named a class that does not exist. The enums are nested, so their
# real R8 names carry `$`, and R8 ignores a rule matching nothing in silence - each protected
# nothing from the day it was written while the gate reported it as a satisfied contract.
# SearchableLanguagePickerDialog.Mode round-trips through a Bundle, kept for the lexical reason
# spelled out on the welcome explainer below; StreamsViewModel.SortMode decodes a name persisted in
# the "streams_session" DataStore, so its round trip really does cross an update.
-keepclassmembernames enum com.sza.fastmediasorter.ui.dialog.SearchableLanguagePickerDialog$Mode {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.ui.streams.StreamsViewModel$SortMode {
    <fields>;
}
# S2363-adjacent, found by assert-enum-persistence-contract during the release-35 sweep.
# DeviceProfileType.name is written into the Room column DeviceProfileEntity.type and read back
# with valueOf in DeviceProfileLocalDataSource.toDomain(), which carries no runCatching - so a
# row written under one R8 mapping and read under the next would throw rather than degrade.
-keepclassmembernames enum com.sza.fastmediasorter.data.model.DeviceProfileType {
    <fields>;
}
# The welcome explainer passes its mode as mode.name through a Bundle and resolves it with
# valueOf. That round trip stays inside one build, so it cannot break across an update the way
# the Room column can - the rule is here because the contract is lexical: an enum resolved by
# name carries the rule, and arguing the exception per call site is how the Room case was missed.
-keepclassmembernames enum com.sza.fastmediasorter.ui.welcome.WelcomeEnableAllExplainerDialogFragment$Mode {
    <fields>;
}
# S2364: seven enums the gate could not see until it resolved a `.name` receiver by its declared
# type instead of by its identifier spelling. Every one of them writes its constant name into a
# DataStore key or value, so an R8 remap orphans what the user already stored.
# DeliverableSet names the DataStore keys "delivery_installed_<name>" and "delivery_stamp_<name>"
# in InstalledSetMarkerStore, and also the on-disk marker directory entry.
-keepclassmembernames enum com.sza.fastmediasorter.domain.delivery.DeliverableSet {
    <fields>;
}
# GameMode.name is the value under "embedded_game_mode"; GameMode.fromStorageName degrades to
# CLASSIC on an unknown name, so a remap silently resets the chosen game skin instead of throwing.
-keepclassmembernames enum com.sza.fastmediasorter.domain.game.GameMode {
    <fields>;
}
# CommandGroup.name builds the preference key "keybinding__<name>" in KeybindingRemapViewModel,
# so a remap orphans the user's remapped key bindings.
-keepclassmembernames enum com.sza.fastmediasorter.domain.input.CommandGroup {
    <fields>;
}
# LauncherOrientation.name is interpolated into the launcher visibility key prefix
# "launcher_desktop__<name>__".
-keepclassmembernames enum com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation {
    <fields>;
}
# StatsKey.name and StatsMediaType.name are lowercased into every counter key that
# StatsAggregateDataStore writes, so a remap orphans the whole accumulated statistics set.
-keepclassmembernames enum com.sza.fastmediasorter.domain.stats.StatsKey {
    <fields>;
}
-keepclassmembernames enum com.sza.fastmediasorter.domain.stats.StatsMediaType {
    <fields>;
}
# ControlSection.name is written to SharedPreferences as the last opened playback section and read
# back by matching `it.name`. Nested, which is the shape S2364 made expressible at all.
-keepclassmembernames enum com.sza.fastmediasorter.ui.player.PlaybackControlDialogFragment$ControlSection {
    <fields>;
}
