# Phase 02 — Б3: Fix AUTO mode staying selected

**Strategic spec:** [`../S0030_bugfix-panel-stereo-dialog-ui.md`](../S0030_bugfix-panel-stereo-dialog-ui.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — independent fix
**Blocks:** Phase 04
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

When user picks "Auto" in the 3D stereo radio group, `handleStereoModeSelection` reads back `host().stereoMode.value` (= `effectiveStereoMode`). `PlayerStereoModeCoordinator.setStereoMode(AUTO)` immediately resolves AUTO → detected mode and stores it in `effectiveStereoMode`. So `bindStereoMode(effectiveMode)` receives e.g. SBS and the radio jumps. Fix: bind UI to the user's selected `mode`, not the effective rendering mode. Additionally enhance `updateStereoDetectedLabel` to show the detected mode as secondary info when user is on AUTO (per strategic completion criteria §11.4).

## Root cause (ADR-1 from strategic spec)

`stereoMode` in PlayerViewModel is `effectiveStereoMode` (renderer value), not `requestedStereoMode` (user intent). The dialog should read user intent for display. Fix is entirely within `PlaybackControlDialogFragment.kt`.

## Files Touched

| File | Change |
|------|--------|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt` | `handleStereoModeSelection` + `updateStereoDetectedLabel` |

---

## Steps

### Step 2.1 — Fix handleStereoModeSelection

**Status:** `[ ] not done`
**Depends on:** —

**Prompt for developer:**
In `PlaybackControlDialogFragment.kt`, locate `private fun handleStereoModeSelection(mode: StereoMode)`.

Current code:

```kotlin
host().rememberStereoModeIfEnabled(mode)
host().setStereoMode(mode)

val effectiveMode = host().stereoMode.value
bindStereoMode(effectiveMode)

if (mode != StereoMode.AUTO) {
    host().showMessage(
        getString(R.string.playback_settings_3d_manual_toast, stereoModeLabel(effectiveMode))
    )
}
```

Replace with:

```kotlin
host().rememberStereoModeIfEnabled(mode)
host().setStereoMode(mode)

// Bind UI to the user's choice, not the resolved effective renderer mode.
// When AUTO is selected, effectiveStereoMode is already resolved to the detected
// value — using it here would jump the radio off AUTO immediately. ADR-1.
bindStereoMode(mode)

if (mode != StereoMode.AUTO) {
    val effectiveMode = host().stereoMode.value
    host().showMessage(
        getString(R.string.playback_settings_3d_manual_toast, stereoModeLabel(effectiveMode))
    )
}
```

**Verification:** `grep -A12 "fun handleStereoModeSelection" PlaybackControlDialogFragment.kt` shows `bindStereoMode(mode)` — NOT `bindStereoMode(effectiveMode)`.

---

### Step 2.2 — Enhance updateStereoDetectedLabel for AUTO state

**Status:** `[ ] not done`
**Depends on:** —

**Prompt for developer:**
In `PlaybackControlDialogFragment.kt`, locate `private fun updateStereoDetectedLabel(mode: StereoMode)`.

Current code:

```kotlin
private fun updateStereoDetectedLabel(mode: StereoMode) {
    if (mode == StereoMode.AUTO) {
        binding.tvStereoDetected.isVisible = false
        return
    }
    binding.tvStereoDetected.isVisible = true
    binding.tvStereoDetected.text = getString(R.string.playback_settings_3d_current, stereoModeLabel(mode))
}
```

Replace the AUTO branch:

```kotlin
private fun updateStereoDetectedLabel(mode: StereoMode) {
    if (mode == StereoMode.AUTO) {
        // When user is on AUTO, show detected mode as secondary info if available.
        // This satisfies strategic completion criteria §11.4 without changing the
        // radio button state — "Auto" stays selected while info is visible.
        val detected = host().detectedStereoMode.value
        val hasInfo = detected != StereoMode.UNKNOWN && detected != StereoMode.AUTO
        binding.tvStereoDetected.isVisible = hasInfo
        if (hasInfo) {
            binding.tvStereoDetected.text = getString(
                R.string.playback_settings_3d_current, stereoModeLabel(detected)
            )
        }
        return
    }
    binding.tvStereoDetected.isVisible = true
    binding.tvStereoDetected.text = getString(R.string.playback_settings_3d_current, stereoModeLabel(mode))
}
```

**Verification:** `grep -A14 "fun updateStereoDetectedLabel" PlaybackControlDialogFragment.kt` shows the `host().detectedStereoMode.value` read. No new string resources required (reuses `playback_settings_3d_current`).

---

### Step 2.3 — Dev log + lint

**Status:** `[ ] not done`
**Depends on:** 2.1, 2.2

**Prompt for developer:**

1. Run `.\gradlew.bat lintStandardDebug` — verify no new errors in `PlaybackControlDialogFragment.kt`.
2. Run `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt" "handleStereoModeSelection" "B3: Bind UI to user's mode (not effectiveStereoMode) so AUTO radio stays selected; show detected mode in secondary label when AUTO"`

**Verification:** Dev-log command exits 0. Lint clean.
