# Specification: VR-HYBRID-1 — Migrate VR flavor to Meta Hybrid App Model

**Status:** Draft
**Date:** 2026-04-24
**Tier:** 3 — Moderate (medium risk)
**Roadmap entry:** Ad-hoc — not listed in `PLAN/IMPROVEMENT_ROADMAP.md`. Diagnosed 2026-04-24 from VRSHELL_DEBUG log evidence (log file `logs/fastmediasorter_20260424_012755.log`, since pruned): OpenXR session never reaches `XR_SESSION_STATE_FOCUSED` on Quest 3; user sees a black 2D panel instead of exclusive immersive VR regardless of stereo-mode choice. The diagnostic scaffolding used during investigation (VRSHELL_DEBUG logging, `com.oculus.vrshell.launch_id` reads, `getLaunchId()` stub) has already been removed from the working tree — this spec plans only the corrective manifest/runtime changes that remain.

---

## 1. Problem Statement

On Quest 3 (HorizonOS) the OpenXR session launched by `VrPlayerActivity` stops at `XR_SESSION_STATE_VISIBLE` and never transitions to `FOCUSED`, so the runtime keeps the app as a 2D panel with a black XR layer rendered behind the panel — no exclusive headset takeover. The earlier working hypothesis ("HorizonOS puts a `com.oculus.vrshell.launch_id` String extra into the launch Intent") was disproved in the diagnostic session: the key never appears in any Intent HorizonOS delivers. The real mechanism Meta documents for panel↔immersive handoff is the [Hybrid App Model](https://developers.meta.com/horizon/documentation/spatial-sdk/hybrid-apps-overview/): two separate Activities with distinct `com.oculus.intent.category.2D` / `com.oculus.intent.category.VR` intent-filter categories, launched via an explicit task-swap pattern. Today the VR-flavor manifest declares the VR category on nothing, and the app never performs the task swap — so `FOCUSED` is unreachable. In addition, the existing manifest comment at `app_v2/src/vr/AndroidManifest.xml:23-28` warns that simply adding `com.oculus.intent.category.VR` to `VrPlayerActivity` previously produced an instant black screen (passthrough disabled before the XR session was ready) — evidence that the category on its own is insufficient; the task split is the co-requisite that makes the category safe.

---

## 2. Goals

1. `VrPlayerActivity` reaches `XR_SESSION_STATE_FOCUSED` when launched from a panel Activity in the same app (verified via `adb logcat OpenXR` showing `... -> XR_SESSION_STATE_FOCUSED`).
2. `VrPlayerActivity` carries an `intent-filter` with `com.oculus.intent.category.VR` in the VR-flavor `AndroidManifest.xml`.
3. `VrPlayerActivity` runs in a **separate Android task** from the panel activities (distinct `taskAffinity`), so panel and immersive never co-exist as the compositor's front window.
4. Panel→immersive transition uses `Intent.ACTION_MAIN` + `FLAG_ACTIVITY_NEW_TASK` and the source panel task is ended via `finishAndRemoveTask()` (per Meta's hybrid-app code sample).
5. Immersive→panel exit (`finish()` in `VrPlayerActivity`, BACK/X/B controller buttons) routes back to the panel surface via the HorizonOS home-intent + `PendingIntent` pattern so the user lands on a fresh `MainActivity` panel instead of a dead task.
6. `docs/DEV_OPS.md` "QUEST DEBUGGING (VR flavor)" section is rewritten to describe the hybrid-app-model mechanism instead of the now-discarded `launch_id` theory.
7. `docs/FEATURES*.md` are **not** touched (no user-facing feature change — this is an infra correction).

Non-goals for this spec:
- Redesigning the VR toggle button behaviour (`switchToPanelPreservingPosition` / `switchToImmersivePreservingPosition`) — those stay as intra-VR-task relaunches.
- Changing which files route to `VrPlayerActivity` vs standard `PlayerActivity` — `BuildConfig.PLAYER_ACTIVITY_CLASS` resolution stays untouched.
- Reconstructing destroyed panel back-stack state (for example restoring `BrowseActivity` with folder position after immersive exit).
- Wear OS.
- Re-introducing `launch_id` / VRSHELL_DEBUG diagnostic code (already removed).
- Non-VR flavors (`standard`, `lite`, `photos`, `legacy`). `vrUnlicensed` shares sources with `vr` and therefore inherits the fix automatically.

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ❌ | `BuildConfig.SUPPORT_VR_PLAYER=false`, `PLAYER_ACTIVITY_CLASS=com.sza.fastmediasorter.ui.player.PlayerActivity` (build.gradle.kts:126-127). |
| `lite`     | ❌ | Same as standard (build.gradle.kts:152-153). |
| `photos`   | ❌ | Same as standard (build.gradle.kts:176-177). |
| `legacy`   | ❌ | Same as standard (build.gradle.kts:203-204). |
| `vr`       | ✅ | Primary target. `BuildConfig.SUPPORT_VR_PLAYER=true` (build.gradle.kts:253), `PLAYER_ACTIVITY_CLASS="com.sza.fastmediasorter.vr.VrPlayerActivity"` (build.gradle.kts:255). Manifest under `app_v2/src/vr/` is modified. |
| `vrUnlicensed` | ✅ | `SUPPORT_VR_PLAYER=true` (build.gradle.kts:300), same `PLAYER_ACTIVITY_CLASS` (build.gradle.kts:301). Shares `src/vr/` sources via `sourceSets` merge — gets the fix automatically. |

Gate for the runtime branch: `BuildConfig.SUPPORT_VR_PLAYER && intent.component.className == BuildConfig.PLAYER_ACTIVITY_CLASS`. No new `BuildConfig` flags are introduced.

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 26 (standard minSdk, inherited by the `vr` flavor) | `PendingIntent.FLAG_IMMUTABLE` used in the exit-to-panel flow is supported since API 23 — always on. |
| 31+ (Android 12 / Horizon OS) | `PendingIntent.FLAG_IMMUTABLE` became mandatory when the PendingIntent is fired by another process. The home-intent handoff crosses into `com.oculus.vrshell`, so we MUST pass `FLAG_IMMUTABLE`. |
| 34+ (Android 14 / Horizon OS v67+) | `foregroundServiceType` on `AudioPlaybackService` is unaffected — the immersive activity's lifecycle change does not alter service declarations. |

All Quest 2/3/Pro devices on recent HorizonOS run Android 12L or Android 14 (SDK 32 / 34). Meta's hybrid-app model is officially supported from `horizonOSVersionCode 69` (~v69); Quest 3 on v77+ meets this threshold. No compat branching required.

### 3.3 Wear OS Impact

No Wear OS changes required. The `wear/` module has no VR / OpenXR code.

---

## 4. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `MainActivity` (889 LOC) | [app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt) | Library / resource browser. VR-flavor manifest gives it `com.oculus.intent.category.2D` + `LAUNCHER` (lines 68-72) — HorizonOS treats it as a 2D panel window. Four `PlayerActivity.createIntent(...)` call sites (144, 260, 623, 636). |
| `VR AndroidManifest` | [app_v2/src/vr/AndroidManifest.xml](../app_v2/src/vr/AndroidManifest.xml) | Declares `VrPlayerActivity` (29-45) with `exported=false`, `launchMode=singleTask`, `meta-data com.oculus.vr.focusaware=true`, but **no intent-filter** and **no `com.oculus.intent.category.VR` category**. Comment at lines 23-28 documents a prior black-screen failure when the VR category was added without a task split. |
| `VrPlayerActivity` (1089 LOC) | [app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt) | Extends `PlayerActivity`; hosts OpenXR via `OpenXrSessionManager`. `exitVrAndStopPlayback` (705-708) calls `finish()`; `launchVrFailureRecovery` (710-729) calls `finish()`; `launchStandardPlayerFallback` (818-838) and `launchUnsupportedImmersiveFallback` (840-870) re-target `PlayerActivity` directly; BACK/X/B/≡ handlers in `dispatchKeyEvent` (630-668); `onNewIntent` recreate path (614-623); intra-VR-task toggle in `switchToPanelPreservingPosition` (1045-1054) and `switchToImmersivePreservingPosition` (1060-1068). |
| `PlayerActivity.createIntent` | [app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt:767-794](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt#L767-L794) | Single factory used by all entry points. Routes via `Class.forName(BuildConfig.PLAYER_ACTIVITY_CLASS)` — in VR flavor that resolves to `VrPlayerActivity`. |
| `OpenXrSessionManager` | [app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt) | Owns XR session lifecycle. Receives state transitions from the Meta runtime. Does NOT need code changes; FOCUSED is an external transition driven by the OS. |
| `VrToggleButtonManager` | [app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrToggleButtonManager.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrToggleButtonManager.kt) | Panel↔immersive toggle within `VrPlayerActivity`. Out of scope — intra-task. |
| `MainResumePlaybackHelper` | [app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResumePlaybackHelper.kt:133-139](../app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResumePlaybackHelper.kt#L133-L139) | Already early-returns on `SUPPORT_VR_PLAYER` for `ScreenType.PLAYER` — the `createIntent(...)` at line 145 is dead code on the VR flavor today. No-op for this spec, but listed for visibility. |
| `BrowseEventHandler` | [app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt:57-87](../app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt#L57-L87) | User tap → builds player intent (standard at 71-72 via `createStandardPlayerIntent`, or immersive at 74-80 via `PlayerActivity.createIntent`) → `playerActivityLauncher.launch(...)` at line 83 expects a result (`EXTRA_MODIFIED_FILES`). |
| `BrowseManagerInitializer` | [app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt:724-730](../app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt#L724-L730) | Browse-initiated slideshow startup. Plain `activity.startActivity(intent)` (no result contract). |
| `FavoritesWidgetProvider` | [app_v2/src/main/java/com/sza/fastmediasorter/widget/FavoritesWidgetProvider.kt](../app_v2/src/main/java/com/sza/fastmediasorter/widget/FavoritesWidgetProvider.kt) | Launches `PlayerActivity` directly via a widget `PendingIntent`. Explicitly out of scope for this iteration. |
| `docs/DEV_OPS.md` (Quest Debugging) | [docs/DEV_OPS.md](../docs/DEV_OPS.md) | Still contains the now-discarded `launch_id` workflow explanation. Must be rewritten to describe the hybrid-app model. |

**Key limitation:** both `MainActivity` and `VrPlayerActivity` currently live in the **same Android task** (default `taskAffinity` inherited from `<application>`). When `MainActivity` calls `startActivity(vrIntent)`, Android stacks `VrPlayerActivity` on top of the same task. HorizonOS compositor keeps rendering the task root (panel) as the active window because the task contains a 2D-category Activity; `VrPlayerActivity`'s OpenXR session therefore never wins focus. The earlier attempt to fix this by adding `com.oculus.intent.category.VR` to `VrPlayerActivity` without splitting tasks (see manifest comment lines 23-28) produced an immediate black screen — passthrough was disabled before the XR session was ready.

---

## 5. Proposed Architecture

### 5.1 Two tasks: panel and VR

Split the two personas of the app into separate tasks via `taskAffinity`:

- **Panel task** — default affinity (`${applicationId}`). Contains every non-VR Activity: `MainActivity`, `BrowseActivity`, `SettingsActivity`, `AddResourceActivity`, `ResourceEditorActivity`, `DuplicatesActivity`, `WelcomeActivity`, cloud folder pickers, widget config. Stays as-is with `com.oculus.intent.category.2D` on `MainActivity`, no explicit `taskAffinity` (inherits `${applicationId}`).
- **VR task** — explicit `taskAffinity="${applicationId}.vr"` on `VrPlayerActivity` only. Declares an `intent-filter` with `com.oculus.intent.category.VR`, so HorizonOS treats this Activity as an immersive peer rather than a panel child.

Transitions:

```
Panel (MainActivity/Browse/…) ──startActivity(vrIntent, NEW_TASK)──▶ VR task (VrPlayerActivity)
                              └──finishAndRemoveTask()──────────────▶ panel task destroyed

VR (VrPlayerActivity.finish/exit) ──home-intent + PendingIntent(panel)──▶ panel task recreated from home
```

### 5.2 New classes / files

One new Kotlin file is introduced. It lives in `main` so both panel activities and `VrPlayerActivity` can share the same task-transition contract without source-set tricks.

| Class / File | Location | Lines budget |
|--------------|----------|--------------|
| `VrTaskTransition.kt` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/` | ≤ 140 |

`VrTaskTransition` is a stateless `object` exposing three functions:

```kotlin
object VrTaskTransition {
    /** True only for intents whose resolved target component is the VR immersive player. */
    fun shouldEnterImmersiveTask(intent: Intent): Boolean

    /** Launch VrPlayerActivity in its own task and tear down the caller's panel task. */
    fun enterImmersive(source: Activity, vrIntent: Intent)

    /** Leave the VR task and return to a fresh MainActivity panel via HorizonOS home-intent. */
    fun exitImmersiveToPanel(source: Activity)
}
```

### 5.3 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ | Transition logic extracted to `VrTaskTransition`. |
| New classes follow naming (`VerbNounUseCase`, `NounRepository`, `NounViewModel`, `NounVerbManager`) | ⚠️ | `VrTaskTransition` is a stateless `object` — no mutable state, no lifecycle. `NounVerb` matches the existing pattern used by `VrRouteDecisionHelper`. |
| Data flow strictly `UI → ViewModel → UseCase → Repository → DataSource` | ✅ | Task transitions are UI-chrome concerns; no ViewModel/UseCase involved. |
| No `Log.d()` — Timber only | ✅ | `VrTaskTransition` uses Timber. Existing `Log.e("VR_BOOT", …)` calls in `VrPlayerActivity` (kept intentionally per the in-file comment at line 156-157 — they survive logcat flooding) stay as-is. |
| Room schema version incremented (if DB changes) | N/A | No DB changes. |
| `StateFlow` for state, `SharedFlow` for one-shot events | N/A | No flows added. |
| Hilt DI: new bindings declared in module file | N/A | Stateless `object` — no binding. |

### 5.4 AndroidManifest changes (VR flavor)

Current [app_v2/src/vr/AndroidManifest.xml:29-45](../app_v2/src/vr/AndroidManifest.xml#L29-L45):

```xml
<activity
    android:name="com.sza.fastmediasorter.vr.VrPlayerActivity"
    android:configChanges="density|fontScale|keyboard|keyboardHidden|navigation|orientation|screenLayout|screenSize|smallestScreenSize|uiMode"
    android:exported="false"
    android:launchMode="singleTask"
    android:resizeableActivity="true"
    android:screenOrientation="landscape">

    <meta-data
        android:name="com.oculus.vr.focusaware"
        android:value="true" />

</activity>
```

Target:

```xml
<activity
    android:name="com.sza.fastmediasorter.vr.VrPlayerActivity"
    android:configChanges="density|fontScale|keyboard|keyboardHidden|navigation|orientation|screenLayout|screenSize|smallestScreenSize|uiMode"
    android:exported="false"
    android:launchMode="singleTask"
    android:resizeableActivity="true"
    android:screenOrientation="landscape"
    android:taskAffinity="${applicationId}.vr"
    android:excludeFromRecents="false">

    <!-- Meta Hybrid App Model: immersive Activity opts in to exclusive XR window.
         Required for HorizonOS to transition XR session to FOCUSED state.
         Prior attempt (see git history of this file) added this category without a
         task split and produced an instant black screen: passthrough was disabled
         before XR session was ready. Category is now safe because VrPlayerActivity
         runs in a dedicated task (taskAffinity above) so no 2D panel window shares
         the compositor slot during the transition. -->
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="com.oculus.intent.category.VR" />
    </intent-filter>

    <meta-data
        android:name="com.oculus.vr.focusaware"
        android:value="true" />

</activity>
```

Additional manifest edits:
- `VrPhoneFallbackActivity` (currently lines 75-77, no `taskAffinity` attribute) gains `android:taskAffinity=""` so on a phone (no XR runtime) it joins the caller's task and does not end up isolated.
- The comment block at lines 23-28 is rewritten to document the hybrid-app rationale instead of the old "no VR intent-filter here" warning.
- `MainActivity`'s intent-filter (lines 68-72) stays untouched — panel Activity remains a panel.

Notes:
- `exported="false"` is kept — the `intent-filter` is used by HorizonOS to interpret the Activity category; it does NOT make the Activity launchable by other apps (explicit component launch still required because `exported=false` wins).
- `taskAffinity="${applicationId}.vr"` — AGP resolves `${applicationId}` per build variant; for `com.sza.fastmediasorter.vr.debug` the affinity becomes `com.sza.fastmediasorter.vr.debug.vr`. Unique per flavor/build type.

### 5.5 VrTaskTransition implementation sketch

```kotlin
package com.sza.fastmediasorter.ui.player.entry

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.ui.main.MainActivity
import timber.log.Timber

object VrTaskTransition {

    fun shouldEnterImmersiveTask(intent: Intent): Boolean {
        if (!BuildConfig.SUPPORT_VR_PLAYER) return false
        val targetClassName = intent.component?.className ?: return false
        return targetClassName == BuildConfig.PLAYER_ACTIVITY_CLASS
    }

    /** Launch VrPlayerActivity in its own task; remove the caller's panel task. */
    fun enterImmersive(source: Activity, vrIntent: Intent) {
        vrIntent.action = Intent.ACTION_MAIN
        vrIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Timber.i("VrTaskTransition.enterImmersive: source=%s target=%s",
            source.javaClass.simpleName, vrIntent.component?.className)
        source.startActivity(vrIntent)
        // finishAndRemoveTask tears down the panel task so the compositor no longer
        // has a 2D window competing with the VR task for foreground.
        source.finishAndRemoveTask()
    }

    /** Return from VR task to panel root via home-intent + PendingIntent. */
    fun exitImmersiveToPanel(source: Activity) {
        val ctx: Context = source.applicationContext
        val panelIntent = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = PendingIntent.getActivity(
            ctx, 0, panelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("extra_launch_in_home_pending_intent", pending)
        Timber.i("VrTaskTransition.exitImmersiveToPanel: routing via home-intent")
        source.startActivity(home)
        source.finishAndRemoveTask()
    }
}
```

### 5.6 Call-site changes

Every caller of `PlayerActivity.createIntent(...)` must discriminate on the **resolved target component**, not merely on flavor. The branch is:

```kotlin
val intent = PlayerActivity.createIntent(…)
if (VrTaskTransition.shouldEnterImmersiveTask(intent)) {
    VrTaskTransition.enterImmersive(activity, intent)
} else {
    startActivity(intent)
}
```

This matters because the VR flavor still contains explicit standard-player paths. In particular, `BrowseEventHandler.createStandardPlayerIntent(...)` constructs `Intent(activity, PlayerActivity::class.java)` directly for MONO / non-immersive media (see usage site at `BrowseEventHandler.kt:71-72`); those launches must remain standard panel launches and keep the existing `ActivityResultLauncher` contract.

Callers requiring the wrap:

| Call site | Context | Notes |
|-----------|---------|-------|
| [MainActivity.kt:144](../app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt#L144) | Service-active fresh-launch reopen | Currently also adds `FLAG_ACTIVITY_REORDER_TO_FRONT`. When immersive, the reorder flag is irrelevant — `enterImmersive` re-runs `ACTION_MAIN` + `NEW_TASK` and destroys the panel. Keep the original behaviour for non-VR flavors. |
| [MainActivity.kt:260](../app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt#L260) | `openAudioPlayerFromNotification` | Notification-tap reopen for audio resources. Audio is typically standard-player on VR (see `shouldLaunchStandardPlayer` logic) so most real calls will stay on the else-branch. |
| [MainActivity.kt:623](../app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt#L623) | `NavigateToPlayerSlideshow` event | Slideshow start from library. |
| [MainActivity.kt:636](../app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt#L636) | `NavigateToPlayerRandomMusic` event | Random-music playback start. |
| [BrowseEventHandler.kt:74-83](../app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt#L74-L83) | User tap inside browser | **Special:** keep `playerActivityLauncher.launch(...)` for explicit standard-player intents (lines 71-72 branch). For VR-target intents bypass the launcher and call `VrTaskTransition.enterImmersive(...)` directly — the source browse task will be destroyed and no activity result can be delivered. |
| [BrowseManagerInitializer.kt:727](../app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt#L727) | Browse-initiated slideshow startup | Plain `startActivity` today; keep semantics for non-VR, branch to `enterImmersive` for VR-target intents. |

Call sites **not** updated in this iteration:
- [MainResumePlaybackHelper.kt:145](../app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResumePlaybackHelper.kt#L145). The call site is already gated by an early return at lines 133-139 that skips PLAYER resume on `SUPPORT_VR_PLAYER=true`. Adding the task-swap wrap is a no-op today because the code path is dead on VR; leave as-is and revisit if/when that early return is lifted.
- [FavoritesWidgetProvider.kt](../app_v2/src/main/java/com/sza/fastmediasorter/widget/FavoritesWidgetProvider.kt) — widget `PendingIntent` remains a standard-player entry path. Migrating the widget to immersive handoff is out of scope (see section 14).

### 5.7 switchToPanelPreservingPosition / switchToImmersivePreservingPosition

Current behaviour at [VrPlayerActivity.kt:1045-1068](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt#L1045-L1068) restarts the same `VrPlayerActivity` with `EXTRA_FORCE_IMMERSIVE` flipped. Both restarts stay inside the VR task. No change required — they remain intra-task; no cross-task panel↔VR handoff. Leave as-is.

### 5.8 Exit paths that must use `VrTaskTransition.exitImmersiveToPanel`

These methods currently call `finish()` expecting Android's back-stack to surface `MainActivity`. In the split-task world there is no panel in the VR task's back stack — `finish()` would drop the user at the system shell. Reroute:

- `VrPlayerActivity.exitVrAndStopPlayback(reason)` at [VrPlayerActivity.kt:705-708](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt#L705-L708). Change the trailing `finish()` to `VrTaskTransition.exitImmersiveToPanel(this)`.
- BACK / X / B controller handlers in `dispatchKeyEvent` at [VrPlayerActivity.kt:630-668](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt#L630-L668) — all delegate to `exitVrAndStopPlayback`, so the single-function change above covers all of them.
- `launchVrFailureRecovery(shouldFinish=true)` at [VrPlayerActivity.kt:710-729](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt#L710-L729) — replace the `finish()` inside the `shouldFinish` branch.
- `launchStandardPlayerFallback` at [VrPlayerActivity.kt:818-838](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt#L818-L838) and `launchUnsupportedImmersiveFallback` at [VrPlayerActivity.kt:840-870](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt#L840-L870) stay on their existing direct `startActivity(Intent(intent).setClass(..., PlayerActivity::class.java)); finish()` recovery path in this iteration. They execute after `VrPlayerActivity` is already alive and are post-entry recovery rather than panel→immersive handoff; they do not participate in the FOCUSED-entry failure this spec is fixing. Add a short WHY comment at each call site noting the intentional exemption.

---

## 6. Data Flow

```
 ┌────────────────────────────────────────────────────┐
 │  Panel task  (taskAffinity=${applicationId})        │
 │  ┌───────────────┐  ┌───────────────┐               │
 │  │  MainActivity │─▶│ BrowseActivity│── user tap    │
 │  │ (.2D LAUNCHER)│  │   (.2D)       │    file ──┐   │
 │  └───────────────┘  └───────────────┘           │   │
 └─────────────────────────────────────────────────┼───┘
                                                   │
                         PlayerActivity.createIntent(…)
                                                   │
                                                   ▼
                VrTaskTransition.enterImmersive(source, intent)
                  ├─ intent.action = ACTION_MAIN
                  ├─ intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
                  ├─ source.startActivity(intent)
                  └─ source.finishAndRemoveTask()    [panel task destroyed]
                                                   │
                                                   ▼
 ┌─────────────────────────────────────────────────────┐
 │  VR task  (taskAffinity=${applicationId}.vr)         │
 │  ┌───────────────────────────┐                       │
 │  │      VrPlayerActivity     │                       │
 │  │   (.VR intent-filter,     │                       │
 │  │    com.oculus.vr.focusaware=true)                 │
 │  │   OpenXR session ──▶ FOCUSED  (reached because    │
 │  │   no 2D panel competes for compositor window)     │
 │  └───────────────────────────┘                       │
 └─────────────────────────────────────────────────────┘
       │
       │  exit: BACK / X / B / playback end / delete-last-file / fatal XR error
       ▼
 VrTaskTransition.exitImmersiveToPanel(source)
   ├─ build PendingIntent(MainActivity, NEW_TASK, FLAG_IMMUTABLE)
   ├─ build home intent (ACTION_MAIN + CATEGORY_HOME, extra_launch_in_home_pending_intent=pending)
   ├─ source.startActivity(home)      [HorizonOS fires PendingIntent → restarts panel task]
   └─ source.finishAndRemoveTask()    [VR task destroyed]
                                                   │
                                                   ▼
                              Back to Panel task (MainActivity)
```

Unsupported-media and standard-player recovery paths are intentionally excluded from this diagram — see section 5.8.

---

## 7. Files to Modify

| File | Current LOC | Change | Est. size after |
|------|:-----------:|--------|-----------------|
| [app_v2/src/vr/AndroidManifest.xml](../app_v2/src/vr/AndroidManifest.xml) | 92 | Add `taskAffinity` + `excludeFromRecents` + `<intent-filter>` with `com.oculus.intent.category.VR` on `VrPlayerActivity`; add `taskAffinity=""` on `VrPhoneFallbackActivity`; rewrite comment at 23-28. | ~105 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt` | — | **New file.** Centralises target-intent discrimination, immersive entry, and VR exit-to-panel routing. | ≤ 140 |
| [app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt) | 1089 | Reroute `exitVrAndStopPlayback` (705-708) and `launchVrFailureRecovery` (710-729) through `VrTaskTransition.exitImmersiveToPanel`; keep `launchStandardPlayerFallback` / `launchUnsupportedImmersiveFallback` on their existing direct recovery path with an added WHY comment. | ~1090 |
| [app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt) | 889 | Wrap the four `PlayerActivity.createIntent(...)` call sites at lines 144, 260, 623, 636 with `VrTaskTransition.shouldEnterImmersiveTask` / `enterImmersive`. | ~900 |
| [app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt) | 245 | Keep `playerActivityLauncher.launch(...)` for explicit standard-player intents (branch at lines 71-72); for the immersive branch at 74-80 call `VrTaskTransition.enterImmersive(activity, playerIntent)` instead of `playerActivityLauncher.launch`, and document that no activity result is expected. | ~255 |
| [app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt) | 795 | Wrap the single slideshow-startup call site at line 727-730. | ~800 |
| [docs/DEV_OPS.md](../docs/DEV_OPS.md) | unchanged overall | Rewrite "QUEST DEBUGGING (VR flavor)" section — remove the `launch_id` explanation (scaffolding already deleted from source); document the hybrid-app-model manifest change + task split. Keep the "Correct workflow" steps (install without launch, tap from Library). Update the "Verifying FOCUSED is reached" subsection. | unchanged |

Files that require a timestamped backup to `temp/` before edit (>500 LOC rule): `VrPlayerActivity.kt` (1089), `MainActivity.kt` (889), `BrowseManagerInitializer.kt` (795), `PlayerActivity.kt` (796 — touched indirectly by nothing in this spec, no backup needed unless edit actually lands there).

`PlayerActivity.kt`, `MainResumePlaybackHelper.kt`, and `FavoritesWidgetProvider.kt` are NOT modified in this iteration.

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| 1. HorizonOS still doesn't transition to FOCUSED after the manifest + task split (root cause is deeper). | Low–Med | Ship Step 3 (manifest) + Step 4 (VrPlayerActivity exit reroute) + Step 2 (`VrTaskTransition`) first on a scratch branch; install via `install-vr-debug-to-device.ps1`; launch from Library; check `adb logcat OpenXR` for the `VISIBLE → FOCUSED` transition. Only proceed to call-site wraps (Steps 5-7) if FOCUSED is observably reached. Rollback cost is three commits. |
| 2. Adding `com.oculus.intent.category.VR` reproduces the prior black-screen failure (documented in manifest comment lines 23-28) even with the task split. | Med | The task split is the proven co-requisite per Meta's hybrid-app docs. If black screen still appears, fall back to splitting `VrPlayerActivity` into `VrImmersiveActivity` (with the VR category) and a kept `VrPlayerActivity` without the category (flagged in section 14). Do NOT ship category-only without task split under any circumstances. |
| 3. `finishAndRemoveTask()` kills the panel task, so immersive exit always returns to `MainActivity` rather than the destroyed `BrowseActivity` stack. | Med | Accept as an iteration-1 constraint and document in Goals and manual test cases. Rich panel-state restoration is deferred (section 14). |
| 4. `AudioPlaybackService` is torn down because the panel Activity that started it is `finishAndRemoveTask()`-ed. | Med | The service runs via `startForegroundService` bound to application context, so it survives panel teardown. However `MainActivity.onDestroy` (lines 324-332) runs `unifiedCache.clearAll()` under `isFinishing && !isChangingConfigurations` — which `finishAndRemoveTask` satisfies. The cache could clear mid-playback. Manual test #9 verifies; if caught, gate the cache-clear on an explicit `EXTRA_EXITING_APP` flag rather than lifecycle. |
| 5. `BrowseEventHandler` cannot rely on `StartActivityForResult` for immersive launches because the caller task is deliberately destroyed. | Med | Preserve `playerActivityLauncher.launch(...)` only for explicit standard-player intents. For immersive launches, enter the VR task directly and accept that any later browse view is a fresh panel session rather than a result callback. ADR-3 captures this explicitly. |
| 6. `onNewIntent` flow (next/prev file while VR is already open) breaks because the new intent arrives on the panel's side via `singleTask`. | Low | `VrPlayerActivity` has `launchMode=singleTask` + its own task affinity — a new intent with matching component but different file hits `onNewIntent` on the VR-task instance (FLAG_ACTIVITY_NEW_TASK + matching affinity reuses the task). `onNewIntent → setIntent + recreate()` at lines 614-623 continues to work. Verify via manual test #6. |
| 7. `VrPhoneFallbackActivity` (currently inherits default task affinity) gets isolated in its own task on a phone, breaking navigation. | Low | Manifest patch sets `android:taskAffinity=""` explicitly on `VrPhoneFallbackActivity` so it joins the caller's task. Manual test #10 verifies. |
| 8. `com.oculus.vr.focusaware=true` + `com.oculus.intent.category.VR` disable passthrough during `VrPlayerActivity` post-toggle panel-mode (the `switchToPanelPreservingPosition` state). Panel-mode flat-stereo content inside `VrPlayerActivity` loses its environment. | Med | The toggle remains intra-VR-task; flat content still runs inside `VrPlayerActivity` in the VR task. If passthrough loss is visible and disruptive, revisit by splitting `VrPlayerActivity` into `VrImmersiveActivity` (with `.VR` category) and a kept `VrPlayerActivity` without. Tracked as out-of-scope (section 14) pending manual observation. |

---

## 9. Testing Plan

### 9.1 Unit Tests

Add `VrTaskTransitionTest` (JVM unit test) covering `shouldEnterImmersiveTask(intent)`:
1. VR flavor + intent target class equals `BuildConfig.PLAYER_ACTIVITY_CLASS` → `true`.
2. Explicit `Intent(ctx, PlayerActivity::class.java)` (standard-player override used by `BrowseEventHandler.createStandardPlayerIntent`) → `false`.
3. Intent with null component → `false`.
4. Non-VR flavor (`SUPPORT_VR_PLAYER=false`) → `false`.

`enterImmersive(...)` and `exitImmersiveToPanel(...)` are thin wrappers over framework APIs (`Activity.startActivity`, `finishAndRemoveTask`, `PendingIntent`); no unit tests required for them.

### 9.2 Manual Test Cases

Run **on Quest 3 from HorizonOS Library** (not ADB), each scenario from a fresh install:

1. **Golden path — FOCUSED reached.** Install via `install-vr-debug-to-device.ps1`; open `adb logcat -s OpenXR VrRuntimeClient VrPlayerActivity`; launch from Library; tap a VR180 file → `VrPlayerActivity` opens → logcat MUST contain `PostSessionStateChange: XR_SESSION_STATE_VISIBLE -> XR_SESSION_STATE_FOCUSED`. User sees exclusive immersive view (no panel, no black-framed overlay).
2. **Panel task is destroyed on entry.** Before tapping: `adb shell dumpsys activity activities | grep -E "TaskRecord|Hist"` → observe one panel task. After tapping → observe exactly one VR task with affinity `...vr`; the panel task is gone.
3. **Exit to panel via BACK / X / B.** From immersive VR, press each of controller BACK, X, B → app returns to `MainActivity` with resource list visible; VR task is gone. The panel is a fresh `MainActivity` instance (recreated via home-intent), not a surfaced back-stack.
4. **Browse-origin immersive launch returns to panel root.** Open browse; tap a VR file → immersive opens and browse task is destroyed. Press BACK from VR → app lands on a fresh `MainActivity`, not the old `BrowseActivity` stack. Re-open the same resource manually and confirm the file list reflects current storage state after a fresh load.
5. **Browse-origin standard-player launch still delivers result.** From `BrowseActivity`, open a MONO / non-immersive file that stays on explicit `PlayerActivity::class.java`. Delete or rename inside the player; return → browse list updates via the existing `EXTRA_MODIFIED_FILES` result path.
6. **Next/previous file inside VR (`onNewIntent`).** Open a VR video; while in immersive, trigger next file via controller or overlay. `VrPlayerActivity.onNewIntent` fires; `recreate()` runs; new file loads. Session transitions `FOCUSED → VISIBLE → FOCUSED` or stays FOCUSED throughout.
7. **Panel↔immersive toggle inside VR task.** With VR playing, tap the 3D/panel toggle button → `switchToPanelPreservingPosition` runs → activity restarts in panel-mode inside the VR task (`dumpsys` shows still `...vr` affinity). Tap again → immersive restarts. Session state logs well-ordered.
8. **Fallback to standard PlayerActivity.** Play a 2D (MONO) file while `forceImmersiveThisLaunch=false` and auto-detect returns non-immersive → `launchStandardPlayerFallback` fires → existing direct fallback path opens `PlayerActivity` cleanly; user is not stranded on a dead shell surface.
9. **AudioPlaybackService survives transition.** Start an audio track via a notification action that routes to VR audio playback; verify the service's media-session is still active after VR task teardown → panel task reopen. Service notification remains visible through the task swap. Also verify that `unifiedCache.clearAll()` did NOT fire mid-session (check Timber log for "Cleared UnifiedFileCache").
10. **Phone fallback (no XR runtime).** Install `vrDebug` APK on a non-Quest device; tap a file → `VrPlayerActivity.onCreate` detects no XR runtime → redirects to `VrPhoneFallbackActivity` → fallback Activity opens in the caller's task (no isolated VR task). User sees the phone-fallback UI, not a black screen.
11. **Error-state — XR init fails on Quest.** Simulate by modifying `isXrRuntimeAvailable` to return false on a dev build → `VrPlayerActivity.onCreate` redirects to `VrPhoneFallbackActivity` on-device; verify task-swap does not occur (no `finishAndRemoveTask` of the caller) and user can return to the library cleanly.
12. **ADB sanity regression.** `adb shell am start -n com.sza.fastmediasorter.vr.debug/com.sza.fastmediasorter.ui.main.MainActivity` → app launches; tap a VR file → XR init succeeds; session reaches `VISIBLE` **but does NOT reach FOCUSED** (expected behaviour for ADB launches — documented in DEV_OPS.md). No crash, no dead task.
13. **Widget regression — favorites widget stays panel-only.** Trigger [FavoritesWidgetProvider.kt](../app_v2/src/main/java/com/sza/fastmediasorter/widget/FavoritesWidgetProvider.kt) from the home screen and verify it still opens the standard `PlayerActivity` path rather than attempting immersive task handoff.

### 9.3 Maestro E2E

No Maestro tests needed. Quest-only behaviour; Maestro executes on emulator/phone devices where the VR flavor falls back to `VrPhoneFallbackActivity`, so no immersive-specific flow can be automated.

---

## 10. Accessibility

No accessibility changes. All manifest edits and task-routing changes are invisible to accessibility services. `VrTaskTransition` surfaces no UI. TalkBack traversal order inside `MainActivity` / `BrowseActivity` / `VrPlayerActivity` is unaffected; touch targets unchanged; no colour-only affordances introduced.

---

## 11. User-Facing Feature Update

No FEATURES doc update required. This is an infra correction — the user-visible outcome is "VR immersive now actually works on Quest 3", which was the advertised feature in `FEATURES*.md` all along. No new feature to announce; no removed capability.

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: Shared `VrTaskTransition` helper in `main`, not inline branches or source-set overrides.**
- **Decision:** Add `VrTaskTransition.kt` under `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/` and centralise `shouldEnterImmersiveTask(intent)`, `enterImmersive(...)`, and `exitImmersiveToPanel(...)` there.
- **Alternatives considered:** (a) inline `BuildConfig.SUPPORT_VR_PLAYER` branches at every call site; (b) a dual-file `PlayerEntryLauncher` / `PlayerActivityTaskPolicy` arrangement split across `main` and `vr` source sets.
- **Reason:** A shared helper is compile-safe in all flavors, keeps the "only when the resolved target component is the VR player" rule in one place, and avoids fragile source-set shadowing assumptions.

**ADR-2: Immersive exit returns to fresh `MainActivity` only in this iteration.**
- **Decision:** `VrTaskTransition.exitImmersiveToPanel(source)` always recreates `MainActivity`.
- **Alternatives considered:** (a) restore `BrowseActivity` or another origin panel; (b) persist origin-class / folder state across the task swap.
- **Reason:** Generic VR exit sites do not have enough information to reconstruct `BrowseActivity.createIntent(...)` correctly (`resourceId`, folder path, file path, scroll state). Returning to panel root is explicit, testable, and matches the actual information available.

**ADR-3: Browse immersive launches do not preserve the `ActivityResultLauncher` contract.**
- **Decision:** `BrowseEventHandler` keeps `playerActivityLauncher.launch(...)` only for explicit standard-player intents; VR-target intents bypass that launcher and enter the immersive task directly.
- **Alternatives considered:** (a) keep `StartActivityForResult` even when the caller task is removed; (b) invent a synthetic result bridge after shell handoff.
- **Reason:** Once `finishAndRemoveTask()` destroys the browse task, no result callback target remains. The spec must model that honestly instead of promising a callback that cannot be delivered.

**ADR-4: `VrPlayerActivity` stays a single class even though it serves both immersive and intra-task panel-mode (post-toggle) roles.**
- **Decision:** The hybrid-app model's intent-filter category is applied to `VrPlayerActivity` even though the same Activity instance also handles the post-toggle panel-preserving-position mode.
- **Alternatives considered:** Split into `VrImmersiveActivity` (with `.VR` intent-filter) and a kept `VrPlayerActivity` as a flat-mode bridge without the category.
- **Reason:** The current toggle path has been working for flat-mode presentation; splitting the class doubles the OpenXR session / ExoPlayer wiring code and doubles the test surface. The manifest comment at 23-28 warns of a prior black-screen failure from adding the category without a task split — so adding the task split is the decisive change, and splitting the Activity is a second-order mitigation only if Risk #8 (passthrough loss during panel-mode toggle) surfaces in manual testing.

**ADR-5: `MainResumePlaybackHelper.kt:145` is NOT wrapped in this spec.**
- **Decision:** Leave `MainResumePlaybackHelper` untouched.
- **Alternatives considered:** Wrap the call site with `VrTaskTransition.shouldEnterImmersiveTask` / `enterImmersive` for consistency with the other four wrapped sites.
- **Reason:** The call site is already gated by an early return at lines 133-139 that skips PLAYER resume on `SUPPORT_VR_PLAYER=true`. Wrapping would be a no-op today and risks confusing future readers into thinking the resume path is reachable on VR when it is not. Revisit when the early return is lifted.

---

## 13. Implementation Steps

Backups required before editing files > 500 LOC — create once upfront. Run `add_to_dev_log.ps1` once per modified file (per-step requirement below).

1. **Backup heavily edited files** to `temp/backup_<YYYYMMDD_HHmm>_*.kt`:
   - `VrPlayerActivity.kt` (1089 LOC)
   - `MainActivity.kt` (889 LOC)
   - `BrowseManagerInitializer.kt` (795 LOC)

2. **Create `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt`** with `shouldEnterImmersiveTask(intent)`, `enterImmersive(source, vrIntent)`, and `exitImmersiveToPanel(source)` per the sketch in section 5.5. `shouldEnterImmersiveTask(intent)` MUST gate on `BuildConfig.SUPPORT_VR_PLAYER` AND compare `intent.component?.className` against `BuildConfig.PLAYER_ACTIVITY_CLASS` so explicit standard-player intents in the VR flavor remain panel launches. Add KDoc covering the hybrid-app contract and why each Intent flag is necessary. Run dev log.

3. **Patch `app_v2/src/vr/AndroidManifest.xml`:**
   - Add `android:taskAffinity="${applicationId}.vr"` and `android:excludeFromRecents="false"` to `<activity android:name="…VrPlayerActivity">` (lines 29-45).
   - Add the `<intent-filter>` with `ACTION_MAIN` + `CATEGORY_DEFAULT` + `com.oculus.intent.category.VR` inside that activity.
   - Add `android:taskAffinity=""` on `<activity android:name="…VrPhoneFallbackActivity">` (lines 75-77) so it joins the caller's task.
   - Replace the comment block at lines 23-28 with an updated note citing the Meta Hybrid App Model and explaining why the category is now safe (task split is the co-requisite).
   - Run dev log.

4. **Edit `VrPlayerActivity.kt`:**
   - In `exitVrAndStopPlayback` at lines 705-708, replace `finish()` with `VrTaskTransition.exitImmersiveToPanel(this)`.
   - In `launchVrFailureRecovery` at lines 710-729, replace `finish()` in the `shouldFinish` branch with the same helper.
   - In `launchStandardPlayerFallback` (818-838) and `launchUnsupportedImmersiveFallback` (840-870), keep the current direct `PlayerActivity` recovery launch and add a short WHY comment that this path intentionally stays outside the hybrid-app task swap because it is post-entry recovery, not panel→immersive handoff.
   - Add `import com.sza.fastmediasorter.ui.player.entry.VrTaskTransition`.
   - Run dev log.

5. **Edit `MainActivity.kt`:**
   - At each of the four `PlayerActivity.createIntent(...)` call sites (lines 144, 260, 623, 636), wrap with:
     ```kotlin
     val playerIntent = PlayerActivity.createIntent(…)
     if (VrTaskTransition.shouldEnterImmersiveTask(playerIntent)) {
         VrTaskTransition.enterImmersive(this, playerIntent)
     } else {
         startActivity(playerIntent)
     }
     ```
     For the line-144 site, preserve `FLAG_ACTIVITY_REORDER_TO_FRONT` only in the non-VR branch — it is meaningless once `enterImmersive` adds `NEW_TASK`.
   - Add `import com.sza.fastmediasorter.ui.player.entry.VrTaskTransition`.
   - Run dev log.

6. **Edit `BrowseEventHandler.kt`:**
   - Keep `playerActivityLauncher.launch(playerIntent)` at line 83 for explicit standard-player intents (the branch at lines 71-72 via `createStandardPlayerIntent`).
   - For `PlayerActivity.createIntent(...)` results (lines 74-80) that resolve to the immersive target, call `VrTaskTransition.enterImmersive(activity, playerIntent)` directly instead of using `playerActivityLauncher`.
   - Add a WHY comment noting that the activity-result contract is intentionally unavailable for immersive launches because the browse task is removed.
   - Add the `VrTaskTransition` import.
   - Run dev log.

7. **Edit `BrowseManagerInitializer.kt`:**
   - At line 727-730 wrap the single slideshow-startup call site with the same `shouldEnterImmersiveTask` / `enterImmersive` branch as MainActivity.
   - Add the `VrTaskTransition` import.
   - Run dev log.

8. **Rewrite `docs/DEV_OPS.md` "QUEST DEBUGGING (VR flavor)" section:**
   - Remove any remaining "Why `launch_id` matters" subsection text.
   - Replace with a "Why FOCUSED requires the hybrid-app task split" subsection citing the manifest change + `VrTaskTransition` pattern.
   - Keep the "Correct workflow" steps (install without launch, tap from Library, attach debugger, live logcat).
   - Update the "Verifying FOCUSED is reached" subsection to the post-fix expected log sequence (`OpenXR: XR_SESSION_STATE_IDLE → READY → SYNCHRONIZED → VISIBLE → FOCUSED`).
   - Run dev log.

9. **Add unit test `VrTaskTransitionTest`** with the four cases from section 9.1 under `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/entry/`. Run dev log.

10. **Catalogue refresh** (mandatory — `VrTaskTransition.kt` is a new public class):
    ```powershell
    pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
    ```
    Fill `role` + `status` for the new class via `set.ps1` per `dev/CATALOG/README.md`. Commit updated `dev/CATALOG/app_v2.jsonl` + `app_v2.md` together with the code change.

11. **Build verification** (use `/build` skill, not direct gradle): produce `vrDebug` APK, install via `install-vr-debug-to-device.ps1`, proceed to manual test plan section 9.2. Do NOT mark the spec complete until at least test cases #1, #2, #3, #6, and #9 pass on real Quest 3 hardware.

Mandatory step checklist:
- [ ] String resources: **N/A** (no new user-facing strings).
- [ ] `docs/FEATURES.md` + `_RU` + `_UK`: **N/A** (no feature delta).
- [ ] Room DB migration: **N/A**.
- [ ] `.\scripts\add_to_dev_log.ps1` run for every modified file (per-step requirement above).

---

## 14. Out of Scope (future items)

- **Split `VrPlayerActivity` into `VrImmersiveActivity` + panel-mode sibling** if passthrough loss on the post-toggle flat-mode proves disruptive (Risk #8).
- **Restore panel-origin state after immersive exit.** Future work may reconstruct `BrowseActivity` with `resourceId`, folder path, file path, and scroll position instead of returning only to `MainActivity`.
- **Widget → immersive direct handoff.** At present [FavoritesWidgetProvider.kt](../app_v2/src/main/java/com/sza/fastmediasorter/widget/FavoritesWidgetProvider.kt) intentionally remains a standard-player path, and `MainActivity` widget actions still route through panel-first flow.
- **Re-enable VR-flavor PLAYER resume** by lifting the early return in [MainResumePlaybackHelper.kt:133-139](../app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResumePlaybackHelper.kt#L133-L139) and wrapping the call site at line 145 with `VrTaskTransition` (see ADR-5).
- **`intent_cmd` extra investigation.** The debug log showed HorizonOS delivers `intent_cmd=""` to panel activities. Its purpose (if any, beyond a shell marker) is not documented by Meta — worth a follow-up prototype if FOCUSED still fails after this spec lands.
- **Per-file `StereoFormatOverrideDao` UX improvement.** The separate bug surfaced in this diagnosis session (persistent `SBS_FULL` override leaking into auto-detect) should be tracked as its own spec.
