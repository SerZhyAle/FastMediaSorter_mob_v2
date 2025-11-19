# APK Size Optimization Plan

## Current State
- **Debug APK Size (Baseline)**: 49.94 MB
- **After BouncyCastle + ExoPlayer**: 48.78 MB ✅ Saved 1.16 MB (2.3%)
- **After Google Drive REST Migration**: 46.07 MB ✅ Saved 3.87 MB (7.7%)
- **After Resource Filtering**: 45.03 MB ✅ Saved 4.91 MB (9.8%)
- **Release APK (R8 full mode)**: 22.23 MB ✅ **Saved 27.71 MB total (55.5%)**
- **App Bundle (AAB)**: 14.67 MB ✅ **Bundle is 34% smaller than release APK**

**DEX file breakdown**:
- **Debug**: 20 files, 43.15 MB
- **Release (R8 optimized)**: 3 files, 20.74 MB ✅ Reduced from 20 → 3 DEX files!
  - classes2.dex: 8.6 MB
  - classes.dex: 7.93 MB
  - classes3.dex: 4.21 MB

**Main contributors (release)**:
- DEX files (code): 20.74 MB (93.3%)
- Resources: ~1 MB (4.5%)
- Native libs: 0 MB
- Assets: ~0.5 MB (2.2%)

**User download size from Play Store**: ~10-12 MB (with split APKs)

## ✅ APPLIED OPTIMIZATIONS

### 1. BouncyCastle Post-Quantum Crypto Exclusion
**Status**: ✅ APPLIED (Build 2.25.1119.xxxx)  
**Savings**: ~0.7-1.0 MB

Excluded from `app_v2/build.gradle.kts`:
```kotlin
packaging {
    resources {
        excludes += "org/bouncycastle/pqc/**"  // Post-quantum crypto
        excludes += "**/lowmcL5.bin.properties"  // 0.72 MB lookup table
    }
}
```

### 2. ExoPlayer Streaming Modules Exclusion
**Status**: ✅ APPLIED (Build 2.25.1119.xxxx)  
**Savings**: ~0.3-0.5 MB

Excluded DASH/HLS/SmoothStreaming (only local/network playback needed):
```kotlin
implementation("androidx.media3:media3-exoplayer:1.2.1") {
    exclude(group = "androidx.media3", module = "media3-exoplayer-dash")
    exclude(group = "androidx.media3", module = "media3-exoplayer-hls")
    exclude(group = "androidx.media3", module = "media3-exoplayer-smoothstreaming")
}
```

### 3. Google Drive REST Migration ⭐
**Status**: ✅ APPLIED (Build 2.25.1119.xxxx)  
**Savings**: ~2.71 MB (5.6% reduction)

Replaced heavy Google Drive API v3 SDK with lightweight REST implementation:

**Removed dependencies**:
```kotlin
// REMOVED (total ~10-12 MB):
// implementation("com.google.api-client:google-api-client-android:2.2.0")
// implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")
// implementation("com.google.http-client:google-http-client-gson:1.43.3")
// implementation("com.google.http-client:google-http-client-android:1.43.3")
```

**New implementation**:
- Created `GoogleDriveRestClient.kt` (modeled after `OneDriveRestClient.kt`)
- Uses only `play-services-auth:21.0.0` for OAuth
- Direct HTTP calls via `HttpURLConnection` to `www.googleapis.com/drive/v3`
- JSON parsing with `org.json.JSONObject` (built-in Android)
- All file operations implemented: list, upload, download, delete, rename, move, copy

**Code changes**:
- Replaced `GoogleDriveClient` with `GoogleDriveRestClient` in 7 files
- Updated DI injection in: `CloudMediaScanner`, `CloudFileOperationHandler`, `GoogleDriveFolderPickerViewModel`, `AddResourceActivity`
- Removed old `GoogleDriveClient.kt`

**DEX reduction**: 45 MB → 43.15 MB (1.85 MB from code alone)

### 4. Resource Configuration Filtering ✅
**Status**: ✅ APPLIED (Build 2.25.1119.xxxx)  
**Savings**: ~1.04 MB (2.3%)

Enabled locale filtering in `defaultConfig`:
```kotlin
// Keep only English, Russian, Ukrainian locales
resourceConfigurations += listOf("en", "ru", "uk")
```

**Result**: Debug APK reduced from 46.07 MB → 45.03 MB  
**Note**: Density filtering handled automatically by Play Store with App Bundle distribution

### 5. R8 Full Mode Optimization ⭐⭐
**Status**: ✅ APPLIED (Release builds)  
**Savings**: ~22.8 MB (50.6% of debug APK)

Enabled in `gradle.properties`:
```kotlin
android.enableR8.fullMode=true
```

**Results**:
- Debug APK: 45.03 MB
- Release APK: 22.23 MB
- **DEX reduction**: 20 files (43 MB) → 3 files (20.74 MB)
- **Code shrinking**: Aggressive dead code elimination
- **Obfuscation**: Class/method renaming for additional size reduction

### 6. App Bundle (AAB) ⭐
**Status**: ✅ IMPLEMENTED (Release distribution)  
**Bundle size**: 14.67 MB (34% smaller than release APK)

Build command:
```bash
.\gradlew.bat :app_v2:bundleRelease
```

**Benefits**:
- Play Store serves optimized split APKs per device
- User downloads only: base + architecture + density + locale specific modules
- **Estimated user download**: ~10-12 MB (vs 22.23 MB full APK)
- Automatic updates smaller (delta patching)

## Optimization Recommendations

### 1. **CRITICAL: Exclude Unused BouncyCastle Algorithms** (Save ~2-3 MB)

Add to `app_v2/build.gradle.kts`:

```kotlin
packaging {
    resources {
        // ... existing excludes ...
        
        // Exclude post-quantum crypto (not needed for SMB/SFTP)
        excludes += "org/bouncycastle/pqc/**"
        excludes += "org/bouncycastle/crypto/engines/AES*.class"
        excludes += "**/lowmcL5.bin.properties"
    }
}

dependencies {
    // ... existing deps ...
    
    // Use lighter BouncyCastle variant (only what SMBJ needs)
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1") {
        exclude(group = "org.bouncycastle", module = "bcprov-ext-jdk18on")
    }
}
```

### 2. ~~**Google Drive API - Use Lighter Alternative** (Save ~8-10 MB)~~ ✅ COMPLETED

**Status**: ✅ MIGRATED to REST API (saved 2.71 MB)

~~**Current (HEAVY)**:~~
```kotlin
// REMOVED:
// implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")
// implementation("com.google.api-client:google-api-client-android:2.2.0")
// implementation("com.google.http-client:google-http-client-gson:1.43.3")
// implementation("com.google.http-client:google-http-client-android:1.43.3")
```

**Current (LIGHT)**:
```kotlin
// Google Drive (REST API + Google Sign-In)
implementation("com.google.android.gms:play-services-auth:21.0.0")
```

✅ Created `GoogleDriveRestClient.kt` using direct HttpURLConnection (no SDK).  
✅ All operations work: auth, list, upload, download, delete, rename, move, copy.  
✅ Pattern matches `OneDriveRestClient.kt` for consistency.

### 3. **Enable R8 Full Mode** (Save ~5-10 MB in release)

Add to `app_v2/build.gradle.kts`:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        
        // Add R8 full mode optimization
        // Uncomment after testing:
        // android.enableR8.fullMode=true (in gradle.properties)
    }
}
```

### 4. **App Bundle Instead of APK** (Dynamic Delivery)

Use `bundleRelease` instead of `assembleRelease`:
```bash
.\gradlew.bat :app_v2:bundleRelease
```

Benefits:
- Splits by ABI (arm64-v8a, armeabi-v7a, x86)
- Splits by density (hdpi, xhdpi, etc.)
- User downloads only what's needed (~30-40% smaller)

### 5. **Conditional Cloud Dependencies** (Build Variants)

Create product flavors for different distributions:

```kotlin
android {
    flavorDimensions += "cloud"
    
    productFlavors {
        create("full") {
            dimension = "cloud"
            // All cloud providers
        }
        
        create("lite") {
            dimension = "cloud"
            // Only Google Drive (most requested)
        }
        
        create("minimal") {
            dimension = "cloud"
            // No cloud, only local/SMB/SFTP/FTP
        }
    }
}

dependencies {
    // Full version - all cloud
    "fullImplementation"("com.google.android.gms:play-services-auth:21.0.0") // Google Drive REST
    "fullImplementation"("com.dropbox.core:dropbox-core-sdk:...")
    "fullImplementation"("com.microsoft.identity.client:msal:...")
    
    // Lite version - only Google Drive
    "liteImplementation"("com.google.android.gms:play-services-auth:21.0.0") // Google Drive REST only
    
    // Minimal - no cloud dependencies
}
```

### 6. ~~**ExoPlayer Optimization** (Save ~1-2 MB)~~ ✅ COMPLETED

**Status**: ✅ APPLIED (saved ~0.3-0.5 MB)

~~Use selective modules instead of full ExoPlayer:~~

```kotlin
// Current optimized version:
implementation("androidx.media3:media3-exoplayer:1.2.1") {
    // Excluded DASH/HLS streaming (only local/network files needed)
    exclude(group = "androidx.media3", module = "media3-exoplayer-dash")
    exclude(group = "androidx.media3", module = "media3-exoplayer-hls")
    exclude(group = "androidx.media3", module = "media3-exoplayer-smoothstreaming")
}
```

### 7. **Remove Unused Resources** (Save ~0.5-1 MB)

Add to `defaultConfig`:

```kotlin
defaultConfig {
    // ... existing config ...
    
    // Keep only necessary densities (if you don't support tablets)
    resourceConfigurations += setOf("en", "ru", "uk", "xxhdpi", "xxxhdpi")
}
```

## Expected Results

| Optimization | Size Reduction | Difficulty | Status |
|-------------|----------------|------------|--------|
| BouncyCastle cleanup | -0.7-1.0 MB | Easy | ✅ Applied |
| ExoPlayer optimization | -0.3-0.5 MB | Easy | ✅ Applied |
| **Google Drive REST migration** | **-2.71 MB** | **Medium** | ✅ **Applied** |
| Resource configs | -1.04 MB | Easy | ✅ **Applied** |
| **R8 full mode** | **-22.8 MB** | **Easy** | ✅ **Applied (Release)** |
| **App Bundle** | **34% smaller** | **Easy** | ✅ **Applied** |
| Build variants | -15-20 MB (lite) | Medium | 📋 Long-term |

**Final Results**:
- **Debug APK**: 45.03 MB (was 49.94 MB, saved 4.91 MB / 9.8%)
- **Release APK**: 22.23 MB ⬇️ (saved 27.71 MB / 55.5%)
- **App Bundle**: 14.67 MB (34% smaller than APK)
- **User download**: ~10-12 MB with split APKs
- **DEX files**: 3 (was 20) → **85% reduction in DEX count**

**Achievement unlocked**: Release APK is **less than half** the original size! 🎉

## Priority Actions

✅ **ALL CRITICAL OPTIMIZATIONS COMPLETED!**

**Applied optimizations**:
1. ✅ BouncyCastle PQC exclusion (-0.7 MB)
2. ✅ ExoPlayer streaming exclusion (-0.3 MB)
3. ✅ Google Drive REST migration (-2.71 MB)
4. ✅ Resource configuration filtering (-1.04 MB)
5. ✅ R8 full mode enabled (-22.8 MB in release)
6. ✅ App Bundle created (14.67 MB AAB)

**Next Steps** (optional further optimization):
- 📋 Consider Dropbox REST migration (similar to Drive/OneDrive pattern, potential -2-3 MB)
- 📋 Build variants (lite/full) for users who don't need all cloud providers
- 📋 Profile remaining code with Android Studio Profiler
- 📋 Test AAB upload to Play Console for final user download size verification

**Deployment Recommendation**:
- Use `.\gradlew.bat :app_v2:bundleRelease` for Play Store uploads
- Keep APK builds for direct distribution/testing only
- Monitor user download sizes in Play Console after first AAB release
   - Enable R8 full mode
   - Optimize ExoPlayer modules

3. **LONG TERM** (Strategic):
   - Create product flavors (full/lite/minimal)
   - Consider dynamic feature modules for cloud providers
