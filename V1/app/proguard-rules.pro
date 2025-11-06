# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Suppress warnings for missing annotations
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**

# Keep Room entities
-keep class com.sza.fastmediasorter.data.** { *; }

# Keep Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# Keep jCIFS-ng (SMB client)
-keep class jcifs.** { *; }
-dontwarn jcifs.**

# Keep BouncyCastle provider and MD4 algorithm for jCIFS-ng
-keep class org.bouncycastle.** { *; }
-keep class m6.** { *; }
-keepclassmembers class org.bouncycastle.jce.provider.BouncyCastleProvider {
    public *;
}
-keep class org.bouncycastle.jce.provider.** { *; }
-keep class org.bouncycastle.crypto.** { *; }
-keep class org.bouncycastle.crypto.digests.MD4Digest { *; }
-dontwarn org.bouncycastle.**

# Keep SLF4J (required by jCIFS-ng)
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }

# Keep Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep ViewBinding
-keep class com.sza.fastmediasorter.databinding.** { *; }

# Keep Google Crypto Tink (for EncryptedSharedPreferences)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}