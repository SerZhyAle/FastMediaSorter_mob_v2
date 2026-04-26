# Phase 4 — Hide Cast Button on Unsupported Flavors

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` (line ~160)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` (line ~356)
**Status:** [x] done

## Context

Cast button visibility is currently gated only on file type and WiFi. On vr flavor the button
would still appear (WiFi is present on Quest) and tapping it would show a "Cast unavailable" toast.
Instead, hide the button entirely when `SUPPORT_CAST = false`.

## Steps

### CommandPanelLayoutPlanner.kt

Change line ~160:

Before:
```kotlin
if ((isImage || isVideo) && isWifiConnected) add(PlayerCommand.CAST)
```

After:
```kotlin
if (BuildConfig.SUPPORT_CAST && (isImage || isVideo) && isWifiConnected) add(PlayerCommand.CAST)
```

### CommandPanelController.kt

Change line ~356:

Before:
```kotlin
safeViews.btnCastCmd.isVisible = (isImage || isVideo) && isWifiConnected(binding.root.context)
```

After:
```kotlin
safeViews.btnCastCmd.isVisible = BuildConfig.SUPPORT_CAST && (isImage || isVideo) && isWifiConnected(binding.root.context)
```

## Verification

- On vr flavor: cast button absent from command panel for any media type.
- On standard flavor: cast button present for image/video with WiFi connected — behavior unchanged.
- `BuildConfig.SUPPORT_CAST` evaluates to a compile-time constant — no runtime overhead.
