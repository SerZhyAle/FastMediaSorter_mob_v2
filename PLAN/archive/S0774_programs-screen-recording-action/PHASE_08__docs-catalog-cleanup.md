# Phase 08 - docs-catalog-cleanup

**Strategic spec:** [`../S0774_programs-screen-recording-action.md`](../S0774_programs-screen-recording-action.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** -
**Steps done:** 4 / 4
**Started:** 2026-06-29
**Completed:** 2026-06-29

> **Phase Step Log (2026-06-29):** ALL_FEATURES record added (Capture, standard+noLegal; validate PASS 446 records); 2 `Timber.d("S0774:` tags present (inserted Phase 07); catalog scanned + role/status/noFlavors set for 10 new classes; `close-and-log.ps1` flipped status In Progress -> BlockNeedUserTest with device-test note + 6 dev logs. `assert-no-ticket-logs` now exit 0 (tags allowed under BlockNeedUserTest).

---

## Objective

Finalize the inventory, catalog, and debug-verification state so the feature is ready for on-device testing. No feature behaviour changes.

---

## Prerequisites

- [ ] Phases 01-07 ✅ Done; standard debug builds green.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via `add.ps1`) | +1 record |
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated | - |
| `app_v2/src/screenCapture/.../ScreenVideoRecordingConsentActivity.kt` (debug tag) | Modified | +1 line |
| `app_v2/src/main/.../helpers/MainScreenRecordingManager.kt` (debug tag) | Modified | +1 line |

> `dev/CATALOG/*.jsonl` + `.md` are gitignored local indexes - regenerate, do not commit.

---

## Steps

### Step 08.1 - Record the capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only capability record via `scripts/all_features/add.ps1` describing: screen video recording from the programs block (whole screen + microphone audio via MediaProjection foreground service, stop from notification or in-app card with timer, save to chosen resource or Downloads; standard `fms.screenCapture=on` + noLegal). Do NOT edit `docs/FEATURES*.md` (strategic §8 defers the showcase sentence to `/skill-release`).

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` → exit 0.
- `Grep` - a screen-recording record present in `docs/ALL_FEATURES.jsonl`.

**Status:** `[x] done`

---

### Step 08.2 - Insert BlockNeedUserTest debug tags

**Files:** `ScreenVideoRecordingConsentActivity.kt`, `MainScreenRecordingManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Per CLAUDE.md §2, before entering `BlockNeedUserTest` insert exactly one `Timber.d("S0774: <entry-point>")` per changed flow entry: one at the consent/start entry (`ScreenVideoRecordingConsentActivity` start, or the service `ACTION_START`), one at the in-app start (`MainScreenRecordingManager.start()`). Do not add a tag per modified line. These are removed when the ticket leaves `BlockNeedUserTest`.

**Verification:**

- `Grep` - exactly two `Timber.d("S0774:` occurrences across `.kt` files.
- `pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.ps1` → no `S0774` in any `Timber.i/w/e`.

**Status:** `[x] done`

---

### Step 08.3 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl` + `.md`
**Depends on:** Steps in prior phases (all new classes exist)

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set `role`+`status` for the new public classes (`ScreenRecordingStateController`, `ScreenVideoRecordingController`, `ScreenVideoRecordingService`, `ScreenVideoRecordingConsentActivity`, `ScreenVideoRecordingControllerImpl`, `MainScreenRecordingMenuManager`, `MainScreenRecordingManager`, `MainProgramsMenuCoordinator`) via `set.ps1`. For the `src/screenCapture` classes add `-NoFlavors "lite,photos,legacy"`.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*ScreenVideoRecording*"` lists the new classes.

**Status:** `[x] done`

---

### Step 08.4 - Dev log + status transition

**Files:** dev log, spec catalog
**Depends on:** Steps 08.1-08.3

**Prompt for developer:**

> Add the per-ticket dev-log entry (batch via `close-and-log.ps1 -DevLogs` if multiple). Advance the ticket to `BlockNeedUserTest` with a `-StatusNote` describing the device test: enable the toggle, pick a destination (and leave it empty once), start recording from the panel and the menu, background the app and record another app, stop from the notification and from the in-app card, verify the MP4 lands in the chosen resource / Downloads with microphone audio, and confirm the scenario is absent in lite/photos/legacy.

**Verification:**

- `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0774 -Status BlockNeedUserTest -StatusNote '<device test plan>'` → spec header shows `BlockNeedUserTest` + note.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0774 -Format json` → status `BlockNeedUserTest`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] All four steps `[x]`.
- [ ] `ALL_FEATURES` validates; catalog regenerated; two debug tags present.
- [ ] Ticket status = `BlockNeedUserTest` with a device-test note.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

Final phase. On-device verification follows via `/spec-test-device S0774`; `/spec-check` flips to `Verified` (and removes the two `S0774` debug tags) once confirmed.

---

## Rollback Plan

Revert the phase commit - inventory/catalog/log only; debug tags removed on status change.
