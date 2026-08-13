# Phase 02 - Quick Settings tile fallback

**Strategic spec:** [`../S0672_standard-edge-gesture-play-compliance.md`](../S0672_standard-edge-gesture-play-compliance.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** 2026-06-26
**Completed:** 2026-06-26

---

## Objective

Build the contingency trigger: a Quick Settings tile (`ScreenshotGestureTileService`) that launches the same MediaProjection capture path as the strip, WITHOUT a persistent overlay or any `specialUse` / `SYSTEM_ALERT_WINDOW` declaration. Ships behind its own independent gate `fms.edgeGestureTile` (default `off`), so it can be enabled in a build that drops the strip if Play rejects the `specialUse` declaration. `standard`-only, flavor-isolated source set.

> **Tile tap behaviour (decided in plan, owner-revisable):** one tap launches `ScreenCaptureConsentActivity` to capture the current screen, reusing the user's configured DOWN-direction gesture action for post-capture routing (save / player / draw / OCR / share). This delivers the headline capability (screenshot capture, which Play users lack) through a single tap and reuses the existing per-direction settings + `ScreenshotGestureActionDispatcher`. Per-direction / open-app / panel access stays on the strip primary path. If the owner wants the tile to open the app-launch panel instead, change Step 02.4 only.

---

## Prerequisites

- [x] Phase nothing - independent. (S0671 capture engine dependency tracked in INDEX.)
- [x] `AudioToggleTileService` reviewed as the QS-tile pattern reference (`src/main/.../core/AudioToggleTileService.kt`).
- [x] Working tree is clean or on a feature branch (DEBUG-v019).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `gradle.properties` | Modified | ≤ 25 |
| `app_v2/build.gradle.kts` | Modified | ≤ 1200 |
| `app_v2/src/standardEdgeTile/java/com/sza/fastmediasorter/screencapture/ScreenshotGestureTileService.kt` | New | ≤ 120 |
| `app_v2/src/standardEdgeTile/AndroidManifest.xml` | New | ≤ 30 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ +2 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ +2 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ +2 |

> **Flavor placement (Rule 14 / `dev/FLAVOR_DEVELOPMENT_RULES.md`).** The tile is `standard`-only and lives in a NEW source set `src/standardEdgeTile/` - never in `src/main`. It needs no `src/main` contract interface (it is a concrete launcher that calls the flavor-neutral `ScreenCaptureConsentActivity` directly). Tile label strings live in `src/main/res` (consistent with the existing edge-gesture strings, which are all in `src/main`; the string is referenced by the gated tile class). The tile icon reuses the existing `@drawable/ic_notification_screen_capture` (monochrome) - no new drawable, so the new source set needs no `res/` directory.

---

## Steps

### Step 02.1 - Add the independent `fms.edgeGestureTile` build gate

**Files:** `gradle.properties`, `app_v2/build.gradle.kts`
**Depends on:** - start of phase

**Prompt for developer:**

> In `gradle.properties`, add a new property `fms.edgeGestureTile=off` next to `fms.edgeGestureOverlay=off`, with a one-line comment: independent QS-tile fallback trigger (no specialUse / SYSTEM_ALERT_WINDOW); enabled instead of the strip if Play rejects the specialUse declaration. In `app_v2/build.gradle.kts`, next to the `edgeGestureOverlayStandardEnabled` reader (~line 171), add `edgeGestureTileStandardEnabled` resolving `fms.edgeGestureTile` (default `off`).

**Verification:**

- `Grep` - `^fms\.edgeGestureTile=off$` matches once in `gradle.properties`.
- `Grep` - `edgeGestureTileStandardEnabled` matches in `app_v2/build.gradle.kts`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-26 - Verification 2/2 PASS. `^fms.edgeGestureTile=off$` x1 in `gradle.properties`; reader `edgeGestureTileStandardEnabled` at `build.gradle.kts:175`. Dev log batched.

---

### Step 02.2 - Mount the `standardEdgeTile` source set and manifest behind the gate

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 02.1

**Prompt for developer:**

> In the `getByName("standard")` source-set block (~588), mount `src/standardEdgeTile/java` under `if (screenCaptureStandardEnabled && edgeGestureTileStandardEnabled)` - BOTH flags, because the tile references `ScreenCaptureConsentActivity` from `src/screenCapture` (present only when `screenCaptureStandardEnabled`). In the `androidComponents.onVariants` manifest-injection block (~969), inject `src/standardEdgeTile/AndroidManifest.xml` for `flavorName == "standard" && edgeGestureTileStandardEnabled`. Do not touch the `edgeGestureOverlayStandardEnabled` branches (the strip stays independently gated).

**Verification:**

- `Grep` - `src/standardEdgeTile/java` appears in `app_v2/build.gradle.kts` under a condition referencing `edgeGestureTileStandardEnabled`.
- `Grep` - `standardEdgeTile/AndroidManifest.xml` injection is guarded by `edgeGestureTileStandardEnabled`.
- `/build` - `assembleStandardDebug` (no `-P`) still configures (tile source set absent by default).

**Status:** `[x]` done

**Step Log:**

- 2026-06-26 - Verification PASS. Source-set mount `src/standardEdgeTile/java` gated on `screenCaptureStandardEnabled && edgeGestureTileStandardEnabled` (build.gradle.kts); manifest injection gated on `flavorName == "standard" && edgeGestureTileStandardEnabled`. Proof: compile with `-Pfms.edgeGestureTile=on` mounts + compiles the class (EXIT 0); default-flags manifest task configures clean (build.gradle.kts syntactically valid). Dev log batched.

---

### Step 02.3 - Declare the tile service manifest (no specialUse, no SYSTEM_ALERT_WINDOW)

**Files:** `app_v2/src/standardEdgeTile/AndroidManifest.xml` (New)
**Depends on:** Step 02.2

**Prompt for developer:**

> Create `src/standardEdgeTile/AndroidManifest.xml` declaring `ScreenshotGestureTileService` as a `<service>` with `android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"`, `android:exported="true"`, `android:icon="@drawable/ic_notification_screen_capture"`, `android:label="@string/tile_screenshot_label"`, an `<intent-filter>` for `android.service.quicksettings.action.QS_TILE`, and `<meta-data android:name="android.service.quicksettings.ACTIVE_TILE" android:value="false" />`. Mirror the structure of the existing `AudioToggleTileService` declaration in `src/main/AndroidManifest.xml`. Declare NO `SYSTEM_ALERT_WINDOW`, NO `FOREGROUND_SERVICE_SPECIAL_USE`, NO foreground service - the tile launches an activity, it does not host an overlay.

**Verification:**

- `Glob` - `app_v2/src/standardEdgeTile/AndroidManifest.xml` exists.
- `Grep` - `android.service.quicksettings.action.QS_TILE` present; `BIND_QUICK_SETTINGS_TILE` present.
- `Grep` - `SPECIAL_USE` and `SYSTEM_ALERT_WINDOW` do NOT appear in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-26 - Verification 3/3 PASS. Manifest exists; `QS_TILE` action + `BIND_QUICK_SETTINGS_TILE` present; no `SPECIAL_USE` / `SYSTEM_ALERT_WINDOW`. Merged-manifest proof (`--rerun-tasks` required - the manifest task does not re-detect a `-P` config delta on incremental runs): tile=on -> `ScreenshotGestureTileService` x1 + `QS_TILE`, specialUse 0; default -> absent. Dev log batched.

---

### Step 02.4 - Implement `ScreenshotGestureTileService`

**Files:** `app_v2/src/standardEdgeTile/java/com/sza/fastmediasorter/screencapture/ScreenshotGestureTileService.kt` (New)
**Depends on:** Step 02.3

**Prompt for developer:**

> Create `ScreenshotGestureTileService : android.service.quicksettings.TileService` (no Hilt - `TileService`'s lifecycle does not fit standard Hilt scopes; match `AudioToggleTileService`, which is plain). On `onClick()`, launch `ScreenCaptureConsentActivity` with `EXTRA_GESTURE_DIRECTION = ScreenshotGestureDirection.DOWN.name` and flags `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS`, using `startActivityAndCollapse` with the API-34 `PendingIntent` overload and the deprecated `Intent` overload below it (copy the exact `Build.VERSION` branch from `AudioToggleTileService.onClick`). In `onStartListening()`, set the tile label to `@string/tile_screenshot_label`, icon to `@drawable/ic_notification_screen_capture`, state `Tile.STATE_INACTIVE`, and call `qsTile?.updateTile()`. No persistent overlay, no foreground service, no MediaProjection logic here - that all lives in `ScreenCaptureConsentActivity` (verified self-contained). Comment only the non-obvious bit (why no Hilt / why DOWN action is the tile's default).

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class ScreenshotGestureTileService : TileService` matches exactly once.
- `Grep` - `ScreenCaptureConsentActivity` and `startActivityAndCollapse` both referenced.
- `Grep -n "Log\.d\("` returns zero hits (Timber only).
- `/build` - `assembleStandardDebug -P fms.edgeGestureTile=on` compiles (tile mounted, references resolve).

**Status:** `[x]` done

**Step Log:**

- 2026-06-26 - Verification 4/4 PASS. File exists; `class ScreenshotGestureTileService : TileService` x1; references `ScreenCaptureConsentActivity` + `startActivityAndCollapse`; zero `Log.d`. `:app_v2:compileStandardDebugKotlin -Pfms.edgeGestureTile=on` EXIT 0 - `ScreenshotGestureDirection.DOWN` + `EXTRA_GESTURE_DIRECTION` resolve, compiled class present. Dev log batched.

---

### Step 02.5 - Add the tile label string (EN/RU/UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - parallel with 02.4

**Prompt for developer:**

> Add one string key `tile_screenshot_label` across all three locales in a single lockstep call: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key tile_screenshot_label -En "Screenshot" -Ru "Снимок экрана" -Uk "Знімок екрана"`. This is a Quick Settings tile label (short noun, like the existing `tile_audio_play` keys). Check the wording against `docs/COMMUNICATION_POLICY.md` §6 tone checklist (a tile label is a plain noun - no punctuation, no sentence). Then audit: `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "tile_screenshot"`.

**Verification:**

- `Grep` - `tile_screenshot_label` present in all three `strings.xml` files.
- Strings pass COMMUNICATION_POLICY §6 checklist.
- `scripts/check_strings_localized.ps1 -KeyPrefix "tile_screenshot"` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-26 - Verification 3/3 PASS. `tile_screenshot_label` present in EN/RU/UK (Screenshot / Снимок экрана / Знімок екрана); `check_strings_localized.ps1 -KeyPrefix tile_screenshot` EXIT 0 (parity OK); plain noun, no punctuation - COMMUNICATION_POLICY section 6 OK. Dev log batched.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `:app_v2:compileStandardDebugKotlin -Pfms.edgeGestureTile=on` EXIT 0 (tile mounted, references resolve).
- [x] Default build still excludes the tile (gate off): default-flags merged manifest -> `ScreenshotGestureTileService` count 0; default config parses build.gradle.kts clean. The tile source set is isolated (no `src/main` reference), so default compile is unaffected.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added (2026-06-26 14:24, one entry per logical change per CLAUDE.md section 12).
- [x] `dev/CATALOG/app_v2.jsonl` regeneration deferred to Phase 03 Step 03.2 - the single per-ticket catalog run that also sets the tile class role + `NoFlavors`. The `ScreenshotGestureTileService` class is compiled and ready to index.

---

## Handoff Notes to Next Phase

A `standard`-only QS tile fallback exists behind `fms.edgeGestureTile=off`, launching the S0671 consent/capture path with the user's DOWN action and no persistent overlay. It carries no `specialUse` / `SYSTEM_ALERT_WINDOW`. Phase 03 records the new class in the catalog (sub-step `set.ps1 -NoFlavors "lite,photos,legacy,noLegal,vr"`) and the capability in `ALL_FEATURES`. One `Timber.d("S0672: ..")` probe at `onClick` is inserted at the BlockNeedUserTest transition, not here.

---

## Rollback Plan

Revert the phase commit: delete `src/standardEdgeTile/`, drop `fms.edgeGestureTile` + its reader + the source-set/manifest mounts, remove the `tile_screenshot_label` keys. No data migration; the tile is gated off by default so default builds are unaffected either way.
