# Phase 01 — Manifest: PlayerActivity Multi-Instance

**Strategic spec:** [`../S0028_vr-multi-window-playback.md`](../S0028_vr-multi-window-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Declare `PlayerActivity` in the VR flavor manifest as resizable and multi-instance capable so HorizonOS can host multiple panel-player windows simultaneously.

---

## Prerequisites

- [ ] S0038 is `Verified` (see INDEX.md Pre-Implementation Blockers).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/AndroidManifest.xml` | Modified | ≤ 130 |

---

## Steps

### Step 01.1 — Verify PlayerActivity launchMode in main manifest

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Open `app_v2/src/main/AndroidManifest.xml`. Locate the `<activity android:name="...PlayerActivity"...>` declaration. Confirm its `android:launchMode` is `"standard"` or absent (defaulting to `standard`). If it is `singleTop` or `singleTask`, note the value — the next step must preserve the existing intent-filter behaviour while enabling multi-instance in the VR overlay. Do not modify the main manifest in this step.

**Verification:**

- `Grep` — pattern `PlayerActivity` in `app_v2/src/main/AndroidManifest.xml` matches at least once.
- `Grep` — pattern `singleTask` does **not** match on the line containing `PlayerActivity` in the main manifest.

**Status:** `[ ]` not done

---

### Step 01.2 — Add PlayerActivity declaration to VR flavor manifest

**Files:** `app_v2/src/vr/AndroidManifest.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Inside the `<application>` block of `app_v2/src/vr/AndroidManifest.xml`, add the following `<activity>` declaration immediately after the existing `<activity android:name="...VrPhoneFallbackActivity"...>` block:
>
> ```xml
> <!--
>   Panel player: declare resizeableActivity so HorizonOS can host multiple
>   PlayerActivity windows side-by-side in VR space. launchMode is omitted
>   (inherits "standard" from main manifest) — each new-window intent creates
>   a separate instance. FLAG_ACTIVITY_MULTIPLE_TASK at call site creates a
>   distinct task per window.
> -->
> <activity
>     android:name="com.sza.fastmediasorter.ui.player.PlayerActivity"
>     android:resizeableActivity="true" />
> ```
>
> Do not add an intent-filter — the main manifest already supplies it.

**Verification:**

- `Grep` — `android:name="com.sza.fastmediasorter.ui.player.PlayerActivity"` matches in `app_v2/src/vr/AndroidManifest.xml`.
- `Grep` — `android:resizeableActivity="true"` on the same or adjacent line in that file.
- `Grep` — no `<intent-filter>` block inside the new `PlayerActivity` declaration in the VR manifest.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `app_v2/src/vr/AndroidManifest.xml` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 02 can now assume `PlayerActivity` will be hosted in a separate task per window when launched with `FLAG_ACTIVITY_MULTIPLE_TASK`. The manifest side is done; no code changes yet.

---

## Rollback Plan

Revert phase commit(s). No data migration or user-facing surface changed.
