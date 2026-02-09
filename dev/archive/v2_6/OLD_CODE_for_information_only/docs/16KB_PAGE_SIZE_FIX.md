# 16 KB Page Size Compatibility Fix

**Date**: December 20, 2025  
**Issue**: APK not compatible with 16 KB page size devices (Android 15+ requirement)

## Problem

Native libraries had LOAD segments not aligned at 16 KB boundaries:
- `lib/arm64-v8a/libjpeg.so`
- `lib/arm64-v8a/libleptonica.so`
- `lib/arm64-v8a/libpng.so`
- `lib/arm64-v8a/libtesseract.so`
- `lib/x86_64/libjpeg.so`
- `lib/x86_64/libleptonica.so`
- `lib/x86_64/libpng.so`
- `lib/x86_64/libtesseract.so`

## Solution Applied

### 1. gradle.properties
Added native library alignment flag:
```properties
android.bundle.enableNativeLibraryAlignment=true
```

### 2. app_v2/build.gradle.kts
Enhanced packaging configuration:
```kotlin
packaging {
    jniLibs {
        useLegacyPackaging = false  // Already present
    }
}

androidResources {
    noCompress += "so"  // Added: prevents compression of .so files
}
```

## Technical Details

### AGP Version
- Using Android Gradle Plugin 8.7.3
- Automatic 16 KB alignment support built-in (AGP 8.3+)

### Properties Impact
- `android.bundle.enableUncompressedNativeLibs=true` - stores libraries uncompressed in APK
- `android.bundle.enableNativeLibraryAlignment=true` - forces 16 KB alignment
- `useLegacyPackaging = false` - uses new packaging format with alignment support
- `noCompress += "so"` - prevents AAPT2 from compressing native libraries

## Verification Steps

1. **Build Clean APK**:
   ```powershell
   .\gradlew.bat :app_v2:clean
   .\dev\build-with-version.ps1
   ```

2. **Check Alignment**:
   ```powershell
   # Extract APK
   unzip -q FastMediaSorter_debug.apk -d apk_extracted

   # Check ELF headers (requires readelf from NDK or binutils)
   readelf -l apk_extracted/lib/arm64-v8a/libtesseract.so | grep LOAD
   ```

   **Expected Output**: All LOAD segments should show offsets aligned to 0x4000 (16 KB)
   ```
   LOAD  0x000000 0x00000000 0x00000000 0x123000 0x123000 R E 0x4000
   LOAD  0x124000 0x00124000 0x00124000 0x456000 0x456000 RW  0x4000
   ```

3. **Alternative Check with zipalign**:
   ```powershell
   # Check if APK is aligned to 16 KB (16384 bytes)
   zipalign -c -v 16384 FastMediaSorter_debug.apk
   ```

   **Expected**: All .so files report "Alignment: 16384"

4. **Upload to Play Console**:
   - Open [Google Play Console](https://play.google.com/console)
   - Upload APK to Internal Testing track
   - Check for 16 KB compatibility warnings in Pre-launch Report

## References

- [Android 16 KB Page Size Documentation](https://developer.android.com/16kb-page-size)
- [AGP Release Notes 8.3+](https://developer.android.com/studio/releases/gradle-plugin)
- Google Play requirement: Effective November 1, 2025 for apps targeting Android 15+

## Affected Libraries

Tesseract OCR native dependencies (from `cz.adaptech.tesseract4android:tesseract4android:4.7.0`):
- **libjpeg.so** - JPEG image handling for OCR input
- **libleptonica.so** - Image processing library (Leptonica 1.82.0)
- **libpng.so** - PNG image handling for OCR input  
- **libtesseract.so** - Tesseract OCR engine core (4.1.1)

These are third-party precompiled binaries included via Gradle dependency. Alignment fix ensures they load correctly on devices with 16 KB page size (Pixel 9 Pro XL, etc.).
