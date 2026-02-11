# EXECUTABLE SPECIFICATION: Google Play Console Recommendations

**Date:** 2026-02-10  
**Target App:** FastMediaSorter (v2)  
**Target SDK:** 35 (Android 15)  
**Status:** DRAFT - Awaiting approval  

---

## EXECUTIVE SUMMARY

### Recommendations from Google Play Console

1. ⚠️ **Edge-to-edge may not display for all users** (User Experience)
2. ⚠️ **Your app uses deprecated APIs for edge-to-edge** (User Experience)
3. 💡 **Implement picture-in-picture** (Quality improvement)

### Critical Assessment: What Happens If NOT Implemented?

#### Immediate Impact (Next 3-6 months): **MINIMAL**

- ❌ **NO app rejection** - These are recommendations, not requirements
- ❌ **NO feature limitations** - App functions normally
- ❌ **NO visibility penalties** - Search ranking unaffected
- ✅ **Current behavior stable** - Opt-out still works on Android 15

#### Medium-term Impact (6-12 months): **LOW-MODERATE**

- ⚠️ Google may increase pressure (email campaigns, console warnings)
- ⚠️ Deprecated API **might be removed** in Android 16+ (estimated Q4 2026)
- ⚠️ Store listing may show "Not optimized for latest Android" badge
- ✅ No functional breakage expected

#### Long-term Impact (12+ months): **MODERATE-HIGH**

- ⚠️ Android 16+ may **force** edge-to-edge (no opt-out option)
- ⚠️ Competitors with PiP may have UX advantage
- ⚠️ Play Console may downrank apps not following recommendations
- ❌ Technical debt accumulates

### VERDICT: Safe to defer, but plan timeline

**Recommended timeline:**

- **PiP**: Implement in v2.7 (next minor release) - LOW risk, visible feature
- **Edge-to-Edge**: Plan for v3.0 (major release) - HIGH risk, needs full QA

---

---

## TASK 1: PICTURE-IN-PICTURE (PiP) - Priority: MEDIUM

### Complexity: MODERATE | Estimated Time: 2-3 days | Risk: LOW

### Prerequisites

- [ ] Video playback working in PlayerActivity
- [ ] ExoPlayer Media3 integrated
- [ ] VideoPlayerManager operational
- [ ] Feature flag system available (BuildConfig)

---

### STEP 1.1: Add Build Configuration (5 min)

**File:** `app_v2/build.gradle.kts`

**Location:** Inside `buildTypes` or `productFlavors` defaultConfig

```kotlin
android {
    defaultConfig {
        // ... existing config
        
        buildConfigField("boolean", "FEATURE_PIP", "true")
    }
    
    productFlavors {
        getByName("lite") {
            buildConfigField("boolean", "FEATURE_PIP", "false") // Disable for lite
        }
        getByName("photos") {
            buildConfigField("boolean", "FEATURE_PIP", "false") // Images don't need PiP
        }
        // standard and legacy keep true
    }
}
```

**Validation:**

```bash
./gradlew.bat assembleStandardDebug
# Check: app_v2/build/generated/source/buildConfig/.../BuildConfig.java contains FEATURE_PIP = true
```

---

### STEP 1.2: Update AndroidManifest (3 min)

**File:** `app_v2/src/main/AndroidManifest.xml`

**Action:** Add PiP support to PlayerActivity

```xml
<activity
    android:name=".ui.player.PlayerActivity"
    android:exported="false"
    android:supportsPictureInPicture="true"
    android:configChanges="orientation|screenSize|keyboardHidden|smallestScreenSize|screenLayout"
    android:theme="@style/Theme.FastMediaSorter.Player">
    <!-- ... existing intent filters -->
</activity>
```

**Changes:**

- Added: `android:supportsPictureInPicture="true"`
- Added to `configChanges`: `smallestScreenSize|screenLayout` (prevents activity recreation)

**Validation:**

- Build succeeds
- No manifest merge conflicts

---

### STEP 1.3: Add PiP State to PlayerViewModel (20 min)

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`

**Action 1:** Add state property

```kotlin
data class PlayerState(
    // ... existing properties
    val isInPipMode: Boolean = false
)
```

**Action 2:** Add state update function

```kotlin
@HiltViewModel
class PlayerViewModel @Inject constructor(
    // ... existing
) : ViewModel() {
    
    // ... existing code
    
    fun setPipMode(enabled: Boolean) {
        _state.update { it.copy(isInPipMode = enabled) }
        Timber.d("PiP mode: $enabled")
    }
}
```

**Validation:**

- Compile succeeds
- No errors in ViewModel

---

### STEP 1.4: Create PiP Manager (1 hour)

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PipManager.kt` (NEW)

```kotlin
package com.sza.fastmediasorter.ui.player.helpers

import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.domain.model.MediaType
import timber.log.Timber

/**
 * Manages Picture-in-Picture functionality for PlayerActivity
 */
class PipManager(
    private val context: Context
) {
    
    /**
     * Check if PiP is available on this device/flavor
     */
    fun isPipAvailable(): Boolean {
        return BuildConfig.FEATURE_PIP && 
               Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }
    
    /**
     * Build PiP params with correct aspect ratio
     * @param videoWidth Actual video width from ExoPlayer
     * @param videoHeight Actual video height from ExoPlayer
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun buildPipParams(videoWidth: Int, videoHeight: Int): PictureInPictureParams {
        val aspectRatio = if (videoWidth > 0 && videoHeight > 0) {
            Rational(videoWidth, videoHeight)
        } else {
            Rational(16, 9) // Fallback
        }
        
        Timber.d("PiP aspect ratio: $aspectRatio (${videoWidth}x${videoHeight})")
        
        return PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .build()
    }
    
    /**
     * Check if should enter PiP for this media type
     */
    fun shouldEnterPip(mediaType: MediaType?, isPlaying: Boolean, isPaused: Boolean): Boolean {
        if (!isPipAvailable()) return false
        if (mediaType != MediaType.VIDEO) return false
        if (!isPlaying || isPaused) return false
        
        return true
    }
}
```

**Validation:**

- File compiles
- Timber imports resolve
- BuildConfig.FEATURE_PIP accessible

---

### STEP 1.5: Integrate PipManager into PlayerActivity (45 min)

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`

**Action 1:** Add PipManager field

```kotlin
class PlayerActivity : AppCompatActivity() {
    // ... existing fields
    
    private lateinit var pipManager: PipManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... existing initialization
        
        pipManager = PipManager(this)
    }
}
```

**Action 2:** Override onUserLeaveHint

```kotlin
override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    
    if (!pipManager.isPipAvailable()) {
        return
    }
    
    val state = viewModel.state.value
    val shouldEnter = pipManager.shouldEnterPip(
        mediaType = state.currentFile?.type,
        isPlaying = state.isPlaying,
        isPaused = state.isPaused
    )
    
    if (shouldEnter && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val dimensions = videoPlayerManager.getVideoDimensions() // Need to implement
        val params = pipManager.buildPipParams(dimensions.width, dimensions.height)
        
        enterPictureInPictureMode(params)
    }
}
```

**Action 3:** Override onPictureInPictureModeChanged

```kotlin
override fun onPictureInPictureModeChanged(
    isInPictureInPictureMode: Boolean,
    newConfig: android.content.res.Configuration
) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    
    Timber.d("PiP mode changed: $isInPictureInPictureMode")
    viewModel.setPipMode(isInPictureInPictureMode)
    
    // Hide/show UI elements
    binding.toolbar.isVisible = !isInPictureInPictureMode
    binding.playerControlsContainer.isVisible = !isInPictureInPictureMode
    binding.touchZonesOverlay.isVisible = !isInPictureInPictureMode
    binding.commandPanel.isVisible = !isInPictureInPictureMode && viewModel.state.value.showCommandPanel
    
    // Suspend gesture handling
    if (isInPictureInPictureMode) {
        gestureManager?.suspend()
        systemBarsManager.enterFullscreenMode()
    } else {
        gestureManager?.resume()
        if (viewModel.state.value.showCommandPanel) {
            systemBarsManager.updateSystemBarsVisibility(true)
        }
    }
}
```

**Validation:**

- Compile succeeds
- No missing imports

---

### STEP 1.6: Add getVideoDimensions to VideoPlayerManager (15 min)

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerManager.kt`

**Action:** Add method to retrieve current video dimensions

```kotlin
data class VideoDimensions(val width: Int, val height: Int)

class VideoPlayerManager(/* ... */) {
    
    // ... existing code
    
    fun getVideoDimensions(): VideoDimensions {
        val format = exoPlayer.videoFormat
        return if (format != null) {
            VideoDimensions(
                width = format.width,
                height = format.height
            )
        } else {
            Timber.w("Video format not available, using default")
            VideoDimensions(1920, 1080) // Default fallback
        }
    }
}
```

**Validation:**

- Compile succeeds
- ExoPlayer videoFormat accessible

---

### STEP 1.7: Add suspend/resume to GestureManager (15 min)

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/GestureManager.kt` (or wherever gesture logic resides)

**Action:** Add suspension flag

```kotlin
class GestureManager(/* ... */) {
    
    private var isSuspended = false
    
    fun suspend() {
        isSuspended = true
        Timber.d("Gesture handling suspended")
    }
    
    fun resume() {
        isSuspended = false
        Timber.d("Gesture handling resumed")
    }
    
    // In existing gesture handlers
    fun onTouch(event: MotionEvent): Boolean {
        if (isSuspended) return false
        
        // ... existing gesture handling
    }
}
```

**Validation:**

- Compile succeeds
- Touch events respect suspension

---

### STEP 1.8: Testing Checklist

**Manual Testing:**

- [ ] Start video playback in PlayerActivity
- [ ] Press Home button → App enters PiP mode
- [ ] Video continues playing in small window
- [ ] Tap PiP window → Returns to full screen
- [ ] Press Back in full screen → PiP NOT triggered
- [ ] Try with paused video → PiP NOT triggered
- [ ] Try with image file → PiP NOT triggered
- [ ] Try with audio file → PiP NOT triggered
- [ ] Rotate device in PiP → No crash
- [ ] Lock screen in PiP → Video stops (expected behavior)

**Edge Cases:**

- [ ] Very long video (2+ hours)
- [ ] Portrait video (9:16) → Correct aspect ratio
- [ ] Ultra-wide video (21:9) → Correct aspect ratio
- [ ] Switch to another app while in PiP
- [ ] Receive phone call during PiP

**Performance:**

- [ ] Check battery drain (should be same as normal playback)
- [ ] Check memory leaks with LeakCanary
- [ ] Check video decoder remains active

---

### STEP 1.9: Optional Enhancements (DEFER to v2.8)

These are NOT required for initial implementation:

- [ ] Add RemoteAction buttons (play/pause, next, previous)
- [ ] Add user preference toggle in Settings
- [ ] Add PiP for audio files (with album art)
- [ ] Handle screen lock (BroadcastReceiver for ACTION_SCREEN_OFF)
- [ ] Add telemetry to track PiP usage

---

---

## TASK 2: EDGE-TO-EDGE COMPLIANCE - Priority: HIGH (DEFERRED to v3.0)

### Complexity: HIGH | Estimated Time: 5-7 days | Risk: HIGH

### ⚠️ RECOMMENDATION: DO NOT IMPLEMENT in current release cycle

**Reasons:**

1. Affects ALL Activities (15+ screens)
2. Conflicts with existing SystemBarsManager architecture
3. Requires extensive regression testing
4. Different behavior across API 21-35
5. Device-specific bugs expected (Samsung, Xiaomi, Oppo)

**Timeline:** Plan for v3.0 (Q3 2026) with dedicated QA sprint

---

### Prerequisites (Before starting)

- [ ] Complete regression test suite for all Activities
- [ ] Access to 10+ physical devices (different OEMs)
- [ ] Dedicated QA resources (2+ people)
- [ ] Rollback plan approved
- [ ] Feature flag infrastructure ready
- [ ] 2-week buffer before next release

---

### STEP 2.1: Pre-Implementation Audit (1 day)

**Action 1:** Document all Activities and their system bar usage

Create file: `temp/edge_to_edge_audit.md`

```markdown
# Edge-to-Edge Audit

## Activities Using Theme.FastMediaSorter
- [ ] MainActivity - Status: toolbar exists
- [ ] BrowseActivity - Status: toolbar exists
- [ ] PlayerActivity - Status: custom SystemBarsManager (HIGH RISK)
- [ ] EditActivity - Status: needs review
- [ ] BatchActivity - Status: needs review
- [ ] SettingsActivity - Status: needs review
- [ ] NetworkActivity - Status: needs review
- [ ] CloudActivity - Status: needs review (if exists)

## Current SystemBarsManager behavior
- Location: PlayerActivity.kt
- Functions: enterFullscreenMode(), exitFullscreenMode(), updateSystemBarsVisibility()
- Conflicts: YES - manages bars manually
```

**Action 2:** Test current behavior on API 21, 28, 31, 33, 35

```bash
# Document screenshots of ALL screens in temp/screenshots/
```

---

### STEP 2.2: Create Feature Flag (15 min)

**File:** `app_v2/build.gradle.kts`

```kotlin
android {
    defaultConfig {
        buildConfigField("boolean", "FEATURE_EDGE_TO_EDGE", "false") // Start disabled
    }
}
```

---

### STEP 2.3: Update Theme with Flag Guard (30 min)

**File:** `app_v2/src/main/res/values/themes.xml`

**Current state:**

```xml
<style name="Theme.FastMediaSorter" parent="Theme.Material3.DayNight.NoActionBar">
    <item name="android:windowOptOutEdgeToEdgeEnforcement">true</item>
    <item name="android:statusBarColor">@color/status_bar_color</item>
    <item name="android:navigationBarColor">@color/nav_bar_color</item>
    <!-- ... -->
</style>
```

**Target state (ONLY when feature flag enabled):**

```xml
<style name="Theme.FastMediaSorter" parent="Theme.Material3.DayNight.NoActionBar">
    <!-- REMOVE: android:windowOptOutEdgeToEdgeEnforcement -->
    <item name="android:statusBarColor">@android:color/transparent</item>
    <item name="android:navigationBarColor">@android:color/transparent</item>
    <item name="android:windowLightStatusBar">true</item> <!-- Adjust per theme -->
    <item name="android:windowLightNavigationBar">true</item>
    <!-- ... -->
</style>
```

**⚠️ DO NOT CHANGE YET** - Just document the plan

---

### STEP 2.4: Create WindowInsets Helper (2 hours)

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/util/WindowInsetsHelper.kt` (NEW)

```kotlin
package com.sza.fastmediasorter.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import timber.log.Timber

/**
 * Helper to apply window insets for Edge-to-Edge compatibility
 */
object WindowInsetsHelper {
    
    /**
     * Apply insets to a root view, adding padding to avoid system bars
     * @param root Root view of the Activity
     * @param applyTop Whether to apply top inset (status bar)
     * @param applyBottom Whether to apply bottom inset (navigation bar)
     */
    fun applyInsets(
        root: View,
        applyTop: Boolean = true,
        applyBottom: Boolean = true
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )
            
            Timber.d("Applying insets - top: ${insets.top}, bottom: ${insets.bottom}")
            
            view.updatePadding(
                top = if (applyTop) insets.top else 0,
                bottom = if (applyBottom) insets.bottom else 0
            )
            
            WindowInsetsCompat.CONSUMED
        }
    }
    
    /**
     * For Activities with Toolbar - apply only to content container
     */
    fun applyInsetsWithToolbar(
        contentContainer: View,
        toolbar: View
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(contentContainer) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Toolbar handles top inset as margin
            toolbar.updatePadding(top = insets.top)
            
            // Content only gets bottom inset
            view.updatePadding(bottom = insets.bottom)
            
            WindowInsetsCompat.CONSUMED
        }
    }
}
```

---

### STEP 2.5: Apply Insets to Each Activity (3-4 days)

**⚠️ EACH Activity requires individual testing**

#### Example: MainActivity

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(binding.root)
    
    // Apply insets if feature enabled
    if (BuildConfig.FEATURE_EDGE_TO_EDGE) {
        WindowInsetsHelper.applyInsetsWithToolbar(
            contentContainer = binding.contentContainer,
            toolbar = binding.toolbar
        )
    }
    
    // ... rest of onCreate
}
```

**Testing for MainActivity:**

- [ ] Toolbar not hidden by status bar
- [ ] RecyclerView items visible (not under nav bar)
- [ ] FAB positioned correctly
- [ ] Portrait mode works
- [ ] Landscape mode works
- [ ] Test on notch device
- [ ] Test on gesture navigation
- [ ] Test on 3-button navigation

**Repeat for ALL Activities** (15+ screens × 2 hours each = 30+ hours)

---

### STEP 2.6: Resolve SystemBarsManager Conflict (4 hours)

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SystemBarsManager.kt`

**Problem:** PlayerActivity has custom system bar management that conflicts with edge-to-edge

**Solution:** Refactor to support both modes

```kotlin
class SystemBarsManager(
    private val activity: Activity,
    private val window: Window
) {
    private val isEdgeToEdgeEnabled = BuildConfig.FEATURE_EDGE_TO_EDGE
    
    fun enterFullscreenMode() {
        if (isEdgeToEdgeEnabled) {
            // New edge-to-edge approach
            WindowCompat.getInsetsController(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Legacy approach (current implementation)
            window.decorView.systemUiVisibility = // ... existing code
        }
    }
    
    // Similar refactor for exitFullscreenMode(), updateSystemBarsVisibility()
}
```

**Testing:**

- [ ] PlayerActivity fullscreen still works
- [ ] System bars hide/show correctly
- [ ] Gesture controls work
- [ ] No conflicts with WindowInsets

---

### STEP 2.7: Device-Specific Testing (2-3 days)

**Required test matrix:**

| Device            | Android | Navigation | Screen    | Status |
| ----------------- | ------- | ---------- | --------- | ------ |
| Pixel 6           | 14      | Gesture    | Standard  | [ ]    |
| Pixel 8           | 15      | Gesture    | Standard  | [ ]    |
| Samsung S21       | 13      | 3-button   | Rounded   | [ ]    |
| Samsung S24       | 14      | Gesture    | Rounded   | [ ]    |
| Xiaomi 13         | 13      | Gesture    | Notch     | [ ]    |
| OnePlus 11        | 14      | Gesture    | Punch     | [ ]    |
| Old device (low)  | 7.1     | 3-button   | Standard  | [ ]    |
| Tablet (medium)   | 12      | Gesture    | Landscape | [ ]    |
| Foldable          | 14      | Gesture    | Fold      | [ ]    |
| Android TV (opt)  | 11      | D-pad      | TV        | [ ]    |

**Test ALL Activities on EACH device**

---

### STEP 2.8: Rollback Plan

**If issues found in production:**

1. **Immediate (same day):**

   ```kotlin
   // In build.gradle.kts
   buildConfigField("boolean", "FEATURE_EDGE_TO_EDGE", "false")
   ```

   Build and push emergency release

2. **Medium-term (next release):**
   - Revert theme changes
   - Remove WindowInsets code
   - Return to opt-out model

3. **Monitoring:**
   - Crashlytics: Monitor crashes in affected Activities
   - Firebase Analytics: Track Activity launches
   - User reports: Check for UI layout complaints

---

### STEP 2.9: Staged Rollout Strategy

**Week 1:**

- 10% rollout
- Monitor crash rate (must be < 0.1%)
- Monitor ANR rate

**Week 2:**

- If stable: 50% rollout
- Continue monitoring

**Week 3:**

- If stable: 100% rollout

**Rollback triggers:**

- Crash rate increase > 0.5%
- ANR increase > 1%
- Critical UI bug reports > 10

---

### STEP 2.10: Success Criteria

- [ ] Zero crashes related to window insets
- [ ] All Activities render correctly on 10+ devices
- [ ] No content hidden behind system bars
- [ ] Performance metrics unchanged
- [ ] User satisfaction score stable
- [ ] Google Play Console warning cleared

---

## IMPLEMENTATION TIMELINE & DECISION MATRIX

### Recommended Approach

| Task           | Version | Start Date | Duration | Resources            | Priority |
| -------------- | ------- | ---------- | -------- | -------------------- | -------- |
| **PiP**        | v2.7    | 2026-02-15 | 3 days   | 1 developer          | MEDIUM   |
| **Edge-to-Edge** | v3.0  | 2026-Q3    | 7 days   | 1 dev + 2 QA testers | HIGH     |

### Decision Tree

```
Google Play Recommendations Received
│
├─ PiP Implementation?
│  ├─ YES → Start TASK 1 (3 days)
│  │       → Include feature flag
│  │       → Restrict to VIDEO only
│  │       → Add to v2.7 milestone
│  │
│  └─ NO  → Safe to defer
│          → No negative impact
│          → Revisit in 6 months
│
└─ Edge-to-Edge Implementation?
   ├─ YES → DO NOT START NOW
   │       → Wait for v3.0 planning
   │       → Allocate QA resources first
   │       → Requires 2-week testing buffer
   │
   └─ NO  → **RECOMMENDED**
           → Deprecated API still works
           → Android 16 not released yet
           → Defer to major version update
```

---

## RISK MITIGATION SUMMARY

### If You Implement Now (NOT RECOMMENDED for Edge-to-Edge)

**High-Risk Scenarios:**

1. UI regression on> 15 screens requiring emergency hotfix
2. Device-specific crashes (Samsung, Xiaomi custom ROMs)
3. User complaints about hidden content
4. Increased support burden
5. Delayed release timeline

**Mitigation:**

- Feature flag MANDATORY
- Staged rollout (10% → 50% → 100%)
- Rollback plan tested in advance
- Extra QA resources allocated

### If You Defer (RECOMMENDED)

**Consequences:**

- Google Play Console warnings persist (cosmetic only)
- No functional impact on users
- App remains fully operational
- More time to plan proper migration

**Benefits:**

- Zero risk to current stability
- Can bundle with v3.0 overhaul
- More time for architecture improvements
- Competitive analysis of other apps' implementations

---

## FILES MODIFIED SUMMARY (When Implemented)

### TASK 1: PiP (8 files)

1. ✏️ `app_v2/build.gradle.kts` - Feature flag
2. ✏️ `app_v2/src/main/AndroidManifest.xml` - PiP support
3. ✏️ `PlayerViewModel.kt` - State management
4. ➕ `PipManager.kt` - NEW
5. ✏️ `PlayerActivity.kt` - Lifecycle hooks
6. ✏️ `VideoPlayerManager.kt` - Dimensions API
7. ✏️ `GestureManager.kt` - Suspend/resume
8. ➕ Unit tests - NEW

### TASK 2: Edge-to-Edge (20+ files)

1. ✏️ `app_v2/build.gradle.kts` - Feature flag
2. ✏️ `app_v2/src/main/res/values/themes.xml` - Theme updates
3. ➕ `WindowInsetsHelper.kt` - NEW
4. ✏️ `MainActivity.kt` - Insets
5. ✏️ `BrowseActivity.kt` - Insets
6. ✏️ `PlayerActivity.kt` - SystemBarsManager refactor
7. ✏️ `SettingsActivity.kt` - Insets
8. ✏️ `EditActivity.kt` - Insets
9. ✏️ `BatchActivity.kt` - Insets
10. ✏️ `NetworkActivity.kt` - Insets
11. ✏️ `SystemBarsManager.kt` - Mode detection
12. ✏️ 10+ layout XML files - Padding adjustments
13. ➕ Integration tests - NEW

---

## FINAL RECOMMENDATION

### ✅ TASK 1 (PiP): **APPROVED FOR v2.7**

- Low-Medium complexity
- Low risk (isolated to PlayerActivity)
- Visible feature users may appreciate
- Clean implementation path available
- 3 days development + testing

### ⛔ TASK 2 (Edge-to-Edge): **DEFERRED TO v3.0**

- High complexity (affects entire app)
- High risk (15+ screens, device variations)
- Not required by Google (only recommended)
- Requires dedicated QA sprint
- Better suited for major version update

### 📋 NEXT STEPS

1. **Approve this specification** (or request revisions)
2. **Create GitHub issues:**
   - Issue #1: "Implement PiP for PlayerActivity" (milestone: v2.7)
   - Issue #2: "Plan Edge-to-Edge migration" (milestone: v3.0)
3. **Schedule PiP implementation:**
   - Sprint allocation: 3 days
   - Developer assignment: [TBD]
   - Test devices prepared: [TBD]
4. **Monitor Google Play Console** for policy changes

---

**Document Status:** READY FOR REVIEW  
**Last Updated:** 2026-02-10  
**Prepared By:** GitHub Copilot (Claude Sonnet 4.5)  
**Review Required From:** Project Lead / Senior Developer  

---

### QUESTIONS FOR DECISION MAKER

Before proceeding, please answer:

1. **PiP Implementation Decision:**
   - [ ] YES - Approve for v2.7 (3 days work)
   - [ ] NO - Defer to future version
   - [ ] DISCUSS - Need more information

2. **Edge-to-Edge Decision:**
   - [ ] AGREE - Defer to v3.0 as recommended
   - [ ] DISAGREE - Want to implement now (explain why)
   - [ ] ALTERNATIVE - Propose different timeline

3. **Resource Availability:**
   - Developer hours available for v2.7: _____ days
   - QA resource availability: _____ testers
   - Device test pool size: _____ devices

4. **Risk Tolerance:**
   - [ ] LOW - Only implement if zero risk
   - [ ] MEDIUM - Accept minor bugs if quick to fix
   - [ ] HIGH - Willing to deal with edge cases

5. **Timeline Pressure:**
   - Next planned release date: ___________
   - Buffer time before release: _____ days
   - Can delay release if needed: YES / NO

---

**END OF SPECIFICATION**

### A. Remove Edge-to-Edge Opt-Out

In `app_v2/src/main/res/values/themes.xml`:

1. **Remove** the following line from `Theme.FastMediaSorter` (Base Theme):

    ```xml
    <item name="android:windowOptOutEdgeToEdgeEnforcement">true</item>
    ```

    *Reasoning:* This parameter is deprecated and prevents the app from being future-proof.

2. **Update System Bar Colors**:
    For Android 15 compliance, status and navigation bars should be transparent.
    Update `Theme.FastMediaSorter`:

    ```xml
    <!-- Change to transparent for proper Edge-to-Edge -->
    <item name="android:statusBarColor">@android:color/transparent</item>
    <item name="android:navigationBarColor">@android:color/transparent</item>
    ```

    *Note:* You may need to ensure `android:windowLightStatusBar` and `android:windowLightNavigationBar` are set correctly depending on whether the background is light or dark, to ensure icons are visible.

### B. Handle Window Insets (Padding)

Since the app will now draw behind system bars on all screens (not just Player), you must ensure content doesn't get obscured.

1. **Review Activities** inheriting from `Theme.FastMediaSorter` (e.g., `MainActivity`, `BrowseActivity`).
2. **Apply Insets:** Use `ViewCompat.setOnApplyWindowInsetsListener` to add padding to the root views of these activities, verifying that list items or headers are not hidden behind the status/nav bars.
    - *Note:* `PlayerActivity` already handles this manually via `SystemBarsManager` and its own listener, so it might be safe, but verify.

---

## 2. Picture-in-Picture (PiP) Implementation

**Issue:**
Google Play Console recommends "Implement picture-in-picture to improve your app quality". The current `PlayerActivity` does not support PiP.

**Required Changes:**

### A. Manifest Configuration

In `app_v2/src/main/AndroidManifest.xml`:

1. Add `android:supportsPictureInPicture="true"` to the `PlayerActivity` tag.

    ```xml
    <activity
        android:name=".ui.player.PlayerActivity"
        ...
        android:supportsPictureInPicture="true"
        android:configChanges="orientation|screenSize|keyboardHidden|smallestScreenSize|screenLayout" />
    ```

    *Note:* Added `smallestScreenSize|screenLayout` to `configChanges` to prevent activity restart on PiP transition.

### B. PlayerActivity Implementation

In `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`:

1. **Handle User Leaving:**
    Override `onUserLeaveHint()` to trigger PiP when the user presses Home (if video is playing).

    ```kotlin
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val state = viewModel.state.value
        // Only enter PiP if playing video and not paused
        if (state.currentFile?.type == MediaType.VIDEO && !state.isPaused) {
            enterPictureInPictureMode(
                android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(16, 9)) // Calculate actual aspect ratio of video
                    .build()
            )
        }
    }
    ```

    *Refinement:* Calculate the `Rational` based on the actual video dimensions from `videoPlayerManager`.

2. **Handle PiP Mode Changes:**
    Override `onPictureInPictureModeChanged` to update UI.

    ```kotlin
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        
        // Toggle visibility of UI elements
        binding.toolbar.isVisible = !isInPictureInPictureMode
        binding.playerControlsContainer.isVisible = !isInPictureInPictureMode
        binding.touchZonesOverlay.isVisible = !isInPictureInPictureMode
        
        // Notify ViewModel/Managers if needed
        viewModel.setPipMode(isInPictureInPictureMode)
        
        if (isInPictureInPictureMode) {
            // Hide system bars completely
            systemBarsManager.enterFullscreenMode()
        } else {
            // Restore UI state
            if (viewModel.state.value.showCommandPanel) {
                systemBarsManager.updateSystemBarsVisibility(true)
            }
        }
    }
    ```

### C. Exclude Non-Video Content

Ensure PiP is NOT triggered for Images, Text or Audio (unless desired, but usually PiP is for video). The `onUserLeaveHint` check above handles this.

---

## Summary of Files to Touch

1. `app_v2/src/main/res/values/themes.xml` (Edge-to-Edge colors and opt-out removal)
2. `app_v2/src/main/AndroidManifest.xml` (PiP support)
3. `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` (PiP Logic)
4. *(Optional)* Layout files for other activities if padding issues arise from Edge-to-Edge enforcement.

---

## 3. Implementation Risk Analysis & Recommendations

### 3.1 Edge-to-Edge Implementation (HIGH RISK)

#### Stability Risks

- **Full UI regression testing required** for ALL screens (MainActivity, BrowseActivity, SettingsActivity, EditActivity, BatchActivity, NetworkActivity, etc.).

* **Architecture conflict**: `PlayerActivity` already has `SystemBarsManager` managing system bars. Removing opt-out will create competing mechanisms.
- **API fragmentation**: Different behavior on API 21-34 vs 35+. Current opt-out guarantees consistency across SDK versions.

#### Known Issues

- Content bleeding under status bar (lists, headers, buttons).

* Notch/cutout overlap on devices with display cutouts.
- Navigation gestures overlapping bottom UI elements.
- Different inset heights in landscape/portrait modes.
- Device-specific behavior variations (Samsung, Xiaomi, Oppo custom ROMs).

#### Time Estimate

- Implementation: 2-3 hours

* Testing + fixes: **1-2 days** (minimum 10 screens × 2 orientations × 3 API levels × 3 device types)
- **Total: 3-4 work days**

#### Recommendation

**DO NOT IMPLEMENT** immediately if:
- Release planned within next 2 weeks
- No resources for comprehensive UI regression testing
- No access to diverse device test pool

**Alternative Approach**:
- Create separate branch for edge-to-edge migration
- Test on >10 physical devices (different OEMs)
- Merge only after full validation cycle
- Consider phased rollout (10% → 50% → 100%)

---

### 3.2 Picture-in-Picture Implementation (MEDIUM RISK)

#### Missing Considerations

**1. Lifecycle Management**
- Activity remains in memory during PiP mode
- Need proper resource management (ExoPlayer, connections)
- Potential memory leaks if not handled correctly

**2. Gesture System Conflict**
- `touchZonesOverlay` may continue processing touch events
- Spec only hides overlay but doesn't disable gesture handling
- Need explicit gesture system suspension in PiP mode

**3. ViewModel State**
- `viewModel.setPipMode()` **DOES NOT EXIST**
- Need to add PiP state to PlayerViewModel
- Need state propagation to all relevant managers
- Need state restoration on PiP exit

**4. Audio Behavior**
- Should audio continue playing in PiP mode?
- If yes: **Foreground service MANDATORY** (Android 11+)
- Need notification management
- Need audio focus handling

**5. Aspect Ratio Calculation**
- Spec hardcodes 16:9 ratio
- Real videos may be: 4:3, 21:9, 1:1, 9:16 (vertical)
- **Must integrate with `VideoPlayerManager`** to get actual dimensions
- Need fallback for edge cases

**6. PiP Controls**
- Spec provides NO RemoteAction buttons
- Baseline PiP without controls = poor UX
- **Minimum required**: play/pause, next/prev file
- Optional: skip 10s back/forward

**7. Screen Lock Behavior**
- PiP should close when device locks
- Need `BroadcastReceiver` for `Intent.ACTION_SCREEN_OFF`

**8. Rotation Handling**
- `configChanges` added to manifest
- **NOT handled in code** – will cause configuration issues
- Need proper rotation handling in PiP mode

**9. MediaType Restrictions**
- What about audio files? Images? Documents?
- Spec only mentions video
- Need explicit exclusion logic for other types

#### Time Estimate

- Basic implementation (as per spec): 2 hours

* Proper implementation (addressing above): **1 day**
- Edge case testing: **4-6 hours**
- **Total: 1.5-2 work days**

#### Recommendation

**CAN IMPLEMENT** with following requirements:

1. **Add feature flag**: `BuildConfig.FEATURE_PIP` (standard/legacy flavors only)
2. **Add user preference**: Settings toggle to enable/disable PiP
3. **Restrict to VIDEO only**: Exclude audio/images/documents
4. **Implement RemoteAction controls**: Minimum play/pause + navigation
5. **Proper aspect ratio**: Calculate from video dimensions
6. **Lifecycle safety**: Proper resource management
7. **Gesture system integration**: Suspend touch handling in PiP mode

**Priority**: **LOW**  
- Google Play "recommends", does NOT require
- UX benefit questionable for local file viewer
- Few users actively use PiP for media browsing
- Higher-priority features exist (performance, stability)

---

### 3.3 Critical Gaps in Specification

1. **No rollback plan**: If edge-to-edge breaks production UI, how to quickly revert?
2. **No feature flags**: Both changes should be toggleable (compile-time and runtime)
3. **No unit tests**: PiP state transitions need test coverage
4. **No telemetry**: How to measure actual PiP usage after deployment?
5. **Architecture conflict not addressed**: Existing `SystemBarsManager` vs new edge-to-edge model
6. **No migration strategy**: Should this be gradual or all-at-once?
7. **No device compatibility matrix**: Which devices/Android versions need special handling?

---

### 3.4 Overall Verdict

**Specification Quality**: Technically correct but **INCOMPLETE**

**Implementation Risk**:
- Edge-to-Edge: **HIGH** (affects entire app UI)
- PiP: **MEDIUM** (isolated to PlayerActivity)

**Recommendation**:
- **Edge-to-Edge**: Defer until major version update (v3.0). Create dedicated sprint with full QA cycle.
- **PiP**: Can implement in current cycle IF properly scoped with feature flag and user preference.

**Technical Debt Risk**: HIGH if implemented as-written. Specification needs revision to account for:
- Existing architecture patterns
- Resource constraints
- Testing requirements
- Rollback mechanisms

---

## 4. Revised Implementation Checklist

### Phase 1: Research & Planning (1 day)

- [ ] Document current `SystemBarsManager` behavior

* [ ] Audit all Activities for system bar usage
- [ ] Create device test matrix (minimum 10 devices)
- [ ] Design feature flag architecture
- [ ] Create rollback plan

### Phase 2: PiP Implementation (2 days)

- [ ] Add `FEATURE_PIP` build config

* [ ] Add user preference UI
- [ ] Implement PiP entry/exit logic
- [ ] Add RemoteAction controls
- [ ] Implement aspect ratio calculation
- [ ] Add gesture system suspension
- [ ] Add lifecycle safety checks
- [ ] Write unit tests

### Phase 3: Edge-to-Edge (Deferred to v3.0)

- [ ] Remove opt-out from themes.xml

* [ ] Update system bar colors
- [ ] Implement WindowInsets handling for all Activities
- [ ] Full regression testing
- [ ] Device-specific fixes

### Phase 4: Testing & Validation (2 days)

- [ ] PiP functionality testing (all scenarios)

* [ ] Memory leak testing (LeakCanary)
- [ ] Battery drain testing
- [ ] Multi-window mode compatibility
- [ ] Device-specific issues

### Phase 5: Rollout (1 week)

- [ ] Internal testing (alpha)

* [ ] Staged rollout (10% → 50%)
- [ ] Monitor crash rates
- [ ] Collect telemetry data
- [ ] Full rollout if metrics stable
