# Phase 02 - Menu Launcher Contract

**Strategic spec:** [`../S0559_split-screencapture-menu-standard.md`](../S0559_split-screencapture-menu-standard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Introduce a flavor-gated `MenuScreenshotLauncher` seam: an interface in `src/main` with an empty-set Hilt default (so flavors without capture see no launcher), and a real implementation in `src/screenCapture` that starts `ScreenCaptureConsentActivity`. No UI yet. Mirrors the existing `Set<ScreenGestureOverlayController>` gating idiom.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (standard compiles with the shared engine reachable).
- [ ] `ScreenCaptureConsentActivity` is on the `standard` + `noLegal` classpath.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/MenuScreenshotLauncher.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/MenuScreenshotLauncherModule.kt` | New | ≤ 20 |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/MenuScreenshotLauncherImpl.kt` | New | ≤ 40 |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/di/ScreenCaptureLauncherModule.kt` | New | ≤ 25 |

> **Flavor placement.** The contract interface + empty-set default live in `src/main` (compiled by every flavor). The real impl + its `@IntoSet` binding live in `src/screenCapture/java`, which is mounted only into `standard` + `noLegal` (Phase 01). Flavors without `screenCapture` (photos/lite/legacy/vr) resolve the empty set - launcher absent.

---

## Steps

### Step 02.1 - Declare the launcher contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/MenuScreenshotLauncher.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create interface `MenuScreenshotLauncher` in package `com.sza.fastmediasorter.core.screencapture`. Single member: `fun launch(activity: android.app.Activity)`. KDoc one line: store-safe, user-initiated confirmable screenshot trigger; implementations start the MediaProjection consent flow. No Android framework work in the interface itself.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/MenuScreenshotLauncher.kt` exists.
- `Grep` - `interface MenuScreenshotLauncher` matches exactly once.
- `Grep` - `fun launch(activity: android.app.Activity)` (or `fun launch(activity: Activity)`) present.

**Status:** `[ ]` not done

---

### Step 02.2 - Bind the empty-set default in main

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/MenuScreenshotLauncherModule.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create Hilt module `MenuScreenshotLauncherModule` in package `com.sza.fastmediasorter.di`: `@Module @InstallIn(SingletonComponent::class) abstract class MenuScreenshotLauncherModule { @dagger.multibindings.Multibinds abstract fun launchers(): Set<MenuScreenshotLauncher> }`. This provides an empty `Set<MenuScreenshotLauncher>` for every flavor; the real binding is contributed only by the screenCapture source set. Mirror `di/ScreenGestureOverlayModule.kt` exactly in shape.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/di/MenuScreenshotLauncherModule.kt` exists.
- `Grep` - `@Multibinds` and `Set<MenuScreenshotLauncher>` both present.
- `Grep` - `@InstallIn(SingletonComponent::class)` present.

**Status:** `[ ]` not done

---

### Step 02.3 - Implement the launcher in screenCapture

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/MenuScreenshotLauncherImpl.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `MenuScreenshotLauncherImpl @Inject constructor()` in package `com.sza.fastmediasorter.screencapture`, implementing `MenuScreenshotLauncher`. `launch(activity)` starts `ScreenCaptureConsentActivity` via `activity.startActivity(Intent(activity, ScreenCaptureConsentActivity::class.java))`. The consent activity already owns the MediaProjection consent dialog and post-consent service start, so the impl is a thin trigger - no capture or save logic here. Do not pass a gesture direction (this is the menu path; the service already treats a null/absent direction as a plain silent-save-to-destination, matching the gesture default).

**Verification:**

- `Glob` - `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/MenuScreenshotLauncherImpl.kt` exists.
- `Grep` - `class MenuScreenshotLauncherImpl` matches once and `: MenuScreenshotLauncher` present.
- `Grep` - `ScreenCaptureConsentActivity::class.java` present.

**Status:** `[ ]` not done

---

### Step 02.4 - Contribute the impl to the set

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/di/ScreenCaptureLauncherModule.kt`
**Depends on:** Step 02.2, Step 02.3

**Prompt for developer:**

> Create Hilt module `ScreenCaptureLauncherModule` in package `com.sza.fastmediasorter.di`: `@Module @InstallIn(SingletonComponent::class) abstract class ScreenCaptureLauncherModule { @Binds @IntoSet abstract fun bindMenuScreenshotLauncher(impl: MenuScreenshotLauncherImpl): MenuScreenshotLauncher }`. This lives only in the screenCapture source set, so only `standard` + `noLegal` contribute a non-empty set. Mirror `src/noLegal/java/.../di/ScreenCaptureModule.kt` (`@Binds @IntoSet`) in shape.

**Verification:**

- `Glob` - `app_v2/src/screenCapture/java/com/sza/fastmediasorter/di/ScreenCaptureLauncherModule.kt` exists.
- `Grep` - `@Binds` and `@IntoSet` both present.
- `Grep` - `bindMenuScreenshotLauncher(impl: MenuScreenshotLauncherImpl): MenuScreenshotLauncher` present.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - `assembleStandardDebug` succeeds (Hilt graph resolves the non-empty set in standard). Run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regen deferred to Phase 04 batch.

---

## Handoff Notes to Next Phase

- `Set<MenuScreenshotLauncher>` is injectable: non-empty in `standard` + `noLegal`, empty elsewhere. Phase 03 injects it into `OperationsSettingsFragment` and shows the action only when non-empty.

---

## Rollback Plan

Revert the phase commit(s): delete the four new files. No data migration or user-facing surface changed (no UI references the launcher yet).
