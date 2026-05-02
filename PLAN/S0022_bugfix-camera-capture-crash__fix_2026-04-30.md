# Fix Log — S0022 bugfix-camera-capture-crash

**Date:** 2026-04-30
**Audit source:** `PLAN/S0022_bugfix-camera-capture-crash__audit_2026-04-30.md`
**Auto-applied fixes:** 0
**Manual follow-ups:** 3

## Plan Preview

| # | Origin | Classification | Planned action | Files |
|---|--------|:--------------:|----------------|-------|
| 1 | Top Action Item 1 | manual | follow-up: missing updated FEATURES bullets in all three mirrors requires wording/content decision, not a mechanical mirror sync | `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` |
| 2 | Top Action Item 2 | manual | follow-up: `IOException` -> dedicated localized Snackbar is application logic / control-flow, not a trivial mechanical fix | `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt` |
| 3 | Tactical WARN: stale INDEX status | manual | follow-up: tactical `Status:` movement is reserved for `/spec-check`; no safe auto-fix applies here | `PLAN/S0022_bugfix-camera-capture-crash/INDEX.md` |

## Auto Fixes Applied

None.

## Manual Follow-ups

| Origin | Why auto-fix is unsafe | Suggested next action | Files |
|--------|-------------------------|-----------------------|-------|
| Top Action Item 1 | The audit reports that all three `docs/FEATURES*.md` files are missing the new S0022 behavior. The workflow can only mirror an already-existing EN bullet into RU/UK placeholders; it must not invent a new canonical EN bullet. | Add the canonical EN bullet for S0022, then mirror it to RU/UK via the documentation workflow. | `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` |
| Top Action Item 2 | Routing `IOException` to `camera_capture_error_io` changes application behavior in `save()` and requires a logic edit in user-facing control flow. `/spec-fix` must not patch Kotlin logic. | Update `BrowseCameraCaptureManager.save()` so the IOException path surfaces `camera_capture_error_io`, then rerun the audit. | `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt` |
| Tactical WARN: stale INDEX status | Tactical and strategic `Status:` fields are moved by `/spec-check`, not `/spec-fix`. Updating them here would violate the workflow contract. | After the manual code/doc fixes land, rerun `/spec-check S0022` to let the workflow recompute the tactical status. | `PLAN/S0022_bugfix-camera-capture-crash/INDEX.md` |

## Result

No repository code or documentation files were modified by this fix-up run. Only fix-tracking artefacts were updated.
