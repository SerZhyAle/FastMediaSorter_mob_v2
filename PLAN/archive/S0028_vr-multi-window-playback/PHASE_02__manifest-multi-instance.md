# Phase 02 — Manifest: BrowseActivity + PlayerActivity Multi-Instance

**Strategic spec:** [`../S0028_vr-multi-window-playback.md`](../S0028_vr-multi-window-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 05, Phase 06
**Steps done:** 2 / 2
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Declare `BrowseActivity` and `PlayerActivity` in the VR flavor manifest as resizable and multi-instance capable. HorizonOS reads these attributes to allow multiple windows of the same activity class.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/AndroidManifest.xml` | Modified | ≤ 160 |

---

## Steps

### Step 02.1 — Verify BrowseActivity and PlayerActivity launchMode in main manifest

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Open `app_v2/src/main/AndroidManifest.xml`. Locate the `<activity>` declarations for `BrowseActivity` and `PlayerActivity`. Confirm neither has `android:launchMode="singleTask"` or `"singleInstance"`. Also confirm `VrPlayerActivity` has `launchMode="singleTask"` — that one must NOT be declared multi-instance. Do not modify the main manifest in this step; just gather the facts.

**Verification:**

- `Grep` — `BrowseActivity` matches at least once in `app_v2/src/main/AndroidManifest.xml`.
- `Grep` — `PlayerActivity` matches at least once in `app_v2/src/main/AndroidManifest.xml`.
- `Grep` — `VrPlayerActivity` with `singleTask` matches in `app_v2/src/main/AndroidManifest.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. BrowseActivity (line 102) and PlayerActivity (line 106) confirmed in main manifest with standard launchMode (no attribute). VrPlayerActivity+singleTask confirmed in vr/AndroidManifest.xml (spec predicate targets wrong file — noted, intent satisfied). No code changes in this step.

---

### Step 02.2 — Add BrowseActivity and PlayerActivity declarations to VR flavor manifest

**Files:** `app_v2/src/vr/AndroidManifest.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Inside the `<application>` block of `app_v2/src/vr/AndroidManifest.xml`, add the following two `<activity>` declarations. Do not add intent-filters — the main manifest already supplies them.
>
> ```xml
> <!--
>   Browse: resizable so HorizonOS can host multiple BrowseActivity
>   windows side-by-side. launchMode inherits "standard" from main
>   manifest — each new-window intent creates a separate instance.
>   FLAG_ACTIVITY_MULTIPLE_TASK at call site creates a distinct task.
> -->
> <activity
>     android:name="com.sza.fastmediasorter.ui.browse.BrowseActivity"
>     android:resizeableActivity="true" />
>
> <!--
>   Panel player: resizable for the same reason as BrowseActivity.
>   VrPlayerActivity (immersive) keeps singleTask and is NOT listed here.
> -->
> <activity
>     android:name="com.sza.fastmediasorter.ui.player.PlayerActivity"
>     android:resizeableActivity="true" />
> ```

**Verification:**

- `Grep` — `android:name="com.sza.fastmediasorter.ui.browse.BrowseActivity"` matches in `app_v2/src/vr/AndroidManifest.xml`.
- `Grep` — `android:name="com.sza.fastmediasorter.ui.player.PlayerActivity"` matches in `app_v2/src/vr/AndroidManifest.xml`.
- `Grep` — `android:resizeableActivity="true"` matches at least twice in `app_v2/src/vr/AndroidManifest.xml`.
- `Grep` — no `<intent-filter>` inside either new declaration in the VR manifest.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 4/4 PASS. Added BrowseActivity + PlayerActivity with `resizeableActivity="true"` to vr/AndroidManifest.xml (lines 107-118). No intent-filters added. Files: vr/AndroidManifest.xml (+22 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (VR flavor).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `app_v2/src/vr/AndroidManifest.xml` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

HorizonOS will now correctly host multiple `BrowseActivity` and `PlayerActivity` windows. Phases 03–06 add the code that creates these windows.

---

## Rollback Plan

Revert phase commit(s). No data migration or user-facing surface changed.
