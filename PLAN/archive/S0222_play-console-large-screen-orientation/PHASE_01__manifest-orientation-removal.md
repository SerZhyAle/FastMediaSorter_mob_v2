# Phase 01 — Manifest orientation removal

**Strategic spec:** [`../S0222_play-console-large-screen-orientation.md`](../S0222_play-console-large-screen-orientation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Drop `android:screenOrientation="sensor"` from every `<activity>` entry inside `app_v2/src/main/AndroidManifest.xml`. After this phase, all main-flavor activities default to the system-managed orientation policy. The VR flavor manifest (`app_v2/src/vr/AndroidManifest.xml`) keeps its `screenOrientation="landscape"` on `VrPlayerActivity` — VR head tracking demands a fixed orientation. `configChanges` attributes are preserved unchanged.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (none — foundation phase)
- [ ] Strategic §6 research items blocking this phase are Resolved. (all three resolved, see INDEX.md "Pre-Implementation Blockers")
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ 400 |

---

## Steps

### Step 01.1 — Remove `android:screenOrientation="sensor"` from all `src/main` activities

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In `app_v2/src/main/AndroidManifest.xml`, delete the `android:screenOrientation="sensor"` attribute from every `<activity>` element. Leave the `<activity>` element itself, its `android:configChanges`, and every other attribute (`exported`, `theme`, `label`, `parentActivityName`, `supportsPictureInPicture`, `windowSoftInputMode`, `taskAffinity`) intact. The deletion is mechanical — `android:screenOrientation="sensor"` and the single ASCII space that separates it from the neighbouring attribute. There are 16 occurrences as of 2026-05-16 (MainActivity, BrowseActivity, DuplicatesActivity, PlayerActivity, SettingsActivity, KeybindingRemapActivity, AuthSessionsActivity, AddResourceActivity, ResourceEditorActivity, WelcomeActivity, GoogleDriveFolderPickerActivity, DropboxFolderPickerActivity, OneDriveFolderPickerActivity, StandalonePlayerActivity, ReceiveShareActivity, and the player launchers if any duplicates appear). Do not touch the VR flavor manifest (`app_v2/src/vr/AndroidManifest.xml`) — its `screenOrientation="landscape"` is intentional for VR head tracking.

**Verification:**

- `Grep` — pattern `android:screenOrientation="sensor"` in `app_v2/src/main/AndroidManifest.xml` matches zero times. expected: 0 | actual: 0
- `Grep` — pattern `android:screenOrientation=` in `app_v2/src/main/AndroidManifest.xml` matches zero times. expected: 0 | actual: 0
- `Grep` — pattern `android:screenOrientation="landscape"` in `app_v2/src/vr/AndroidManifest.xml` still matches exactly once. expected: 1 | actual: 1
- `Grep` — pattern `android:configChanges="orientation` across `app_v2/src/main/AndroidManifest.xml` still matches at least 15 times (counter — `configChanges` must survive). expected: ≥15 | actual: 17

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 13:23 — 15 edits to `src/main/AndroidManifest.xml`, one per activity. Initial count was 15 (not 16 — strategic §1 was off-by-one). VR manifest untouched. Verification 4/4 PASS.

---

### Step 01.2 — Dev log entry

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 01.1

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/AndroidManifest.xml" "S0222" "Drop android:screenOrientation=sensor from all 16 src/main activities; configChanges preserved; VR manifest untouched."`. The script appends to `dev/CHANGELOG.md` with branch context and timestamp.

**Verification:**

- Script exits with code 0. expected: 0 | actual: 0
- `Grep` — pattern `S0222` in latest entries of `dev/CHANGELOG.md` matches at least once. expected: ≥1 | actual: ≥1

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 13:23 — `add_to_dev_log.ps1` emitted entry on branch `DEBUG-v003`.

---

## Phase Done Criteria

- [ ] Step 01.1 and Step 01.2 are `[x] done`.
- [ ] No `android:screenOrientation` attribute remains in `app_v2/src/main/AndroidManifest.xml`.
- [ ] VR manifest still carries `screenOrientation="landscape"`.
- [ ] `dev/CHANGELOG.md` entry recorded.

---

## Handoff Notes to Next Phase

After this phase the manifest stops constraining orientation in `src/main`. The actual user-visible behaviour is exposed only when the APK runs on a device: rotation becomes free in landscape, reverse-portrait, and (on Android 16+) tablet/foldable arbitrary aspect ratios. Phase 02 handles the build + on-device audit.

---

## Rollback Plan

Revert the single-file commit. Manifest is the only artifact touched; no migrations, no code, no resources.
