# Phase 06 - Docs, catalog, cleanup

**Strategic spec:** [`../S0405_always-on-top-overlay-screenshot.md`](../S0405_always-on-top-overlay-screenshot.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-11
**Completed:** 2026-06-11
**Completed:** -

---

## Objective

Finalise developer/user documentation for the `noLegal`-only capability, regenerate the class catalog with flavor hints, record the functionality-log entry, and confirm the debug verification tag is in place for device testing.

---

## Prerequisites

- [ ] Phases 01–05 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES_noLegal.md` (+ `_RU`/`_UK` if present) | Modified | - |
| `dev/CATALOG/app_v2.jsonl` (regenerated, gitignored) | Regenerated | - |
| `dev/FUNCTIONALITY.log` (via script) | Appended | - |

> `docs/FEATURES.md` (Play builds) is intentionally NOT touched - this capability ships only on `noLegal` (sideload). The Play-facing FEATURES entry is part of the future Play rollout, not this plan.

---

## Steps

### Step 06.1 - Document the capability in FEATURES_noLegal

**Files:** `docs/FEATURES_noLegal.md` (+ `_RU`/`_UK` if those exist)
**Depends on:** - start of phase

**Prompt for developer:**

> Add one user-facing sentence describing the capability to `docs/FEATURES_noLegal.md` (and its `_RU`/`_UK` siblings if present), e.g. "Снимок любого экрана краевым жестом с сохранением в выбранный ресурс или папку скриншотов." Do NOT edit `docs/FEATURES.md` / `_RU` / `_UK` (Play builds). If `docs/FEATURES_noLegal.md` does not exist, create it following the standard FEATURES structure.

**Verification:**

- `Grep` - the new sentence present in `docs/FEATURES_noLegal.md`.
- `Grep` - `docs/FEATURES.md` does NOT contain the new screenshot sentence.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 2/2 PASS. Added "§10 Screen-gesture screenshot overlay" entry + changelog row to `docs/FEATURES_noLegal.md` and its `_RU`/`_UK` siblings (EN/RU/UK); `docs/FEATURES.md` (+`_RU`/`_UK`, Play builds) untouched. Dev log recorded for all three files.

---

### Step 06.2 - Regenerate catalog with flavor hints for noLegal-only classes

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then mark the noLegal-only classes (`ScreenCaptureService`, `ScreenGestureOverlayManager`, `ScreenCaptureConsentActivity`, `OverlayHostService`, `ScreenGestureOverlayControllerImpl`, `ScreenCaptureModule`) with `set.ps1 -NoFlavors "standard,lite,photos,legacy,vr"` so the catalog records their isolation. Fill `role`/`status` for each new class. Wrap multi-entry `set.ps1` calls in try/catch (it aborts the batch on a missing path).

**Verification:**

- `Grep` - `ScreenCaptureService` present in `dev/CATALOG/app_v2.jsonl` with a `NoFlavors`/flavor-isolation marker.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*ScreenGestureOverlay*"` returns the new classes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 2/2 PASS. Marked 6 noLegal-only classes (`ScreenCaptureService`, `ScreenGestureOverlayManager`, `ScreenCaptureConsentActivity`, `OverlayHostService`, `ScreenGestureOverlayControllerImpl`, `ScreenCaptureModule`) with `-NoFlavors "standard,lite,photos,legacy,vr"` + role + `status=new`; catalog re-rendered (1773 records). Dev log recorded.

---

### Step 06.3 - Record the functionality-log entry

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 06.1

**Prompt for developer:**

> Append an ADD entry via `pwsh -NoProfile -File scripts/add_to_functionality_log.ps1` describing the new user-visible capability (edge-gesture screenshot, noLegal). Run this script standalone/last - it succeeds but leaves a non-zero `$LASTEXITCODE`; re-verify the journal/entry afterwards.

**Verification:**

- `Grep` - the new capability entry present in `dev/FUNCTIONALITY.log`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 1/1 PASS. Appended `[S0405] [ADD]` entry for the screen-gesture screenshot overlay. (Script leaves a non-zero `$LASTEXITCODE` by design; entry confirmed present.)

---

### Step 06.4 - Confirm the debug verification tag and advance status

**Files:** (no source edit - verification + status transition)
**Depends on:** Step 06.1, Step 06.2, Step 06.3

**Prompt for developer:**

> Confirm exactly one `Timber.d("S0405:` probe exists (added in Phase 03) and no permanent log embeds `S0405`. Then advance the ticket to device testing: `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0405 -Status BlockNeedUserTest -StatusNote 'noLegal device test: enable "Оверлей жестов", grant draw-over-apps, swipe down-right ~45° from the left-edge strip, confirm MediaProjection consent → screenshot saved to chosen destination (default Screenshots→Downloads) with visible indicator; verify system Back still usable; verify full disable removes the strip.'`

**Verification:**

- `Grep` - `Timber.d("S0405:` matches exactly once across `.kt` sources.
- `Grep` - no `Timber.i`/`Timber.w`/`Timber.e` line contains `S0405`.
- `select.ps1 -Id S0405 -Format json` shows `BlockNeedUserTest`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 3/3 PASS. Exactly one `Timber.d("S0405:` probe (ScreenCaptureService.onImageAvailable); zero `Timber.i/w/e` lines contain `S0405`; status flipped In Progress -> BlockNeedUserTest with the device-test note. (JSONL `statusNote` stores valid UTF-8 Cyrillic - the mojibake seen in piped `select.ps1` console output is a code-page display artifact, not file corruption; spec-file `**Status note:**` header confirmed correct via Grep.)

---

## Phase Done Criteria

- [x] Every `Step 06.*` is `[x] done`.
- [x] `docs/FEATURES_noLegal.md` updated; `docs/FEATURES.md` untouched.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated with flavor hints.
- [x] Ticket status is `BlockNeedUserTest` with a device-test note.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After device test passes, run `/spec-check S0405` to advance toward `Verified` (which removes the `S0405:` probe).

---

## Rollback Plan

Revert phase commit(s) - documentation/catalog only. The catalog is gitignored and regenerable.
