# Incremental Test Implementation Plan
**Created**: January 17, 2026  
**Status**: 🚧 ACTIVE  
**Priority**: CRITICAL

## Problem Statement
Three consecutive failures trying to add all test methods at once to IntegrationTestRunner.kt.
Cost: ~10 EUR wasted on compilation errors.

## Root Cause
- Attempting to add 800+ lines of code in single push
- Not checking UseCase API signatures before implementation
- Not testing incrementally
- Complex dependencies between new code

## Solution: Incremental Approach
**ONE METHOD → BUILD → COMMIT**

---

## Phase 1: Preparation & Helpers

### Step 1.1: Revert Failed Changes ✅ NEXT
**Goal**: Clean slate  
**Actions**:
- Revert IntegrationTestRunner.kt to last working state
- Keep TestCredentialsLoader.kt changes (those compiled)
- Keep constructor changes (those compiled)
- Remove all failed test method implementations

**Commands**:
```powershell
git diff app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/IntegrationTestRunner.kt
# Manually revert only the broken methods (lines 1455-2269)
.\gradlew :app_v2:compileDebugKotlin
```

**Commit**: "revert: remove broken integration test methods"

---

### Step 1.2: Add createLocalTestFile() Helper
**Goal**: Reusable test file creator  
**File**: IntegrationTestRunner.kt  
**Location**: After line 2385 (in helper section)

**Code to Add**:
```kotlin
/**
 * Create a local test file in cache directory
 * @param fileName name of file to create
 * @return File object or null on failure
 */
private fun createLocalTestFile(fileName: String): File? {
    return try {
        val dir = File(context.cacheDir, "integration_tests")
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText("Test content: ${System.currentTimeMillis()}")
        file
    } catch (e: Exception) {
        Timber.e(e, "Failed to create local test file: $fileName")
        null
    }
}
```

**Build**: `.\gradlew :app_v2:compileDebugKotlin`  
**Test**: Verify compilation succeeds  
**Commit**: "test: add createLocalTestFile helper"

---

## Phase 2: Image Tests (ONE AT A TIME)

### Step 2.1: Add testImageRotate() Method
**Goal**: Test RotateImageUseCase  
**File**: IntegrationTestRunner.kt  
**Location**: Before "// ========== HELPER METHODS =========="

**Check API First**:
```kotlin
// From RotateImageUseCase.kt:
suspend fun execute(imagePath: String, angle: Float): Result<Unit>
```

**Code to Add**:
```kotlin
/**
 * Test image rotation
 */
private suspend fun testImageRotate(angle: Float) {
    val testName = "Image Rotate: ${angle}°"
    val startTime = System.currentTimeMillis()
    
    log("[$testName] Starting...")
    
    try {
        val testFile = createLocalTestFile("test_rotate_${System.currentTimeMillis()}.jpg")
        if (testFile == null) {
            recordResult(testName, "ImageRotate", "LOCAL", "LOCAL", false, 0,
                error = "Failed to create test file")
            return
        }
        
        // Create a simple test image
        val bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
        testFile.outputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        val result = rotateImageUseCase.execute(testFile.absolutePath, angle)
        val duration = System.currentTimeMillis() - startTime
        
        result.fold(
            onSuccess = {
                recordResult(testName, "ImageRotate", "LOCAL", "LOCAL", true, duration)
            },
            onFailure = { error ->
                recordResult(testName, "ImageRotate", "LOCAL", "LOCAL", false, duration,
                    error = error.message)
            }
        )
        
        testFile.delete()
        
    } catch (e: Exception) {
        recordResult(testName, "ImageRotate", "LOCAL", "LOCAL", false,
            System.currentTimeMillis() - startTime, error = e.message)
        Timber.e(e, "Image rotate test failed")
    }
}
```

**Build**: `.\gradlew :app_v2:compileDebugKotlin`  
**Commit**: "test: add testImageRotate method"

---

### Step 2.2: Register testImageRotate() in buildTestList()
**File**: IntegrationTestRunner.kt  
**Location**: In buildTestList(), after Matrix Delete tests, before "return tests"

**Code to Add**:
```kotlin
        // Image rotation tests
        tests.add(Test("Image: Rotate 90°") { testImageRotate(90f) })
        tests.add(Test("Image: Rotate 180°") { testImageRotate(180f) })
        tests.add(Test("Image: Rotate -90°") { testImageRotate(-90f) })
```

**Build**: `.\gradlew :app_v2:compileDebugKotlin`  
**Commit**: "test: register image rotation tests"

---

### Step 2.3: Add testImageFlip() Method
**Check API**: FlipImageUseCase.execute(imagePath: String, direction: FlipDirection)  
**Follow same pattern as Step 2.1**  
**Commit**: "test: add testImageFlip method"

### Step 2.4: Register testImageFlip()
**Follow same pattern as Step 2.2**  
**Commit**: "test: register image flip tests"

---

## Phase 3: GIF Tests (Same Pattern)
- Step 3.1: Add testGifExtractFrames()
- Step 3.2: Register testGifExtractFrames()
- Step 3.3: Add testGifSaveFirstFrame()
- Step 3.4: Register testGifSaveFirstFrame()
- Step 3.5: Add testGifChangeSpeed()
- Step 3.6: Register testGifChangeSpeed()

**API Checks**:
- ExtractGifFramesUseCase: `execute(gifPath: String): Result<Int>`
- SaveGifFirstFrameUseCase: `execute(gifPath: String): Result<String>`
- ChangeGifSpeedUseCase: `execute(gifPath: String, speedMultiplier: Float, saveToDownloads: Boolean = false): Result<String>`

---

## Phase 4: Settings Tests
- Step 4.1: Add testSettingsExport()
- Step 4.2: Register testSettingsExport()
- Step 4.3: Add testSettingsImport()
- Step 4.4: Register testSettingsImport()

**API Check**: Need to read ExportSettingsUseCase and ImportSettingsUseCase

---

## Phase 5: Favorites Tests
- Step 5.1: Add testFavoritesToggle()
- Step 5.2: Register testFavoritesToggle()
- Step 5.3: Add testFavoritesList()
- Step 5.4: Register testFavoritesList()

**API Check**: 
- FavoritesUseCase: `toggleFavorite(mediaFile: MediaFile, resourceId: Long)`
- FavoritesUseCase: `getAllFavorites(): Flow<List<FavoritesEntity>>`

---

## Phase 6: Cloud Provider Tests (LATER)
Only after Phases 1-5 complete successfully.

---

## Execution Rules
1. ✅ **ONE STEP AT A TIME** - no exceptions
2. ✅ **BUILD AFTER EACH** - `.\gradlew :app_v2:compileDebugKotlin`
3. ✅ **COMMIT IMMEDIATELY** - don't accumulate
4. ✅ **READ API FIRST** - check UseCase signatures before coding
5. ✅ **COPY PATTERNS** - use existing test methods as templates
6. ✅ **MAX 50 LINES** - keep methods small
7. ✅ **NO MULTI-FILE CHANGES** - one file per step

---

## Current Status
**Next Step**: Step 1.1 - Revert failed changes
**Estimated Time**: 2-3 hours for all phases (at 5 min per step)
**Estimated Cost**: ~5 EUR (vs 10 EUR wasted on failed approach)
