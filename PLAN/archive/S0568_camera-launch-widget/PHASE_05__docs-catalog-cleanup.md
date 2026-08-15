# Phase 05 - Docs, catalog & cleanup

**Strategic spec:** [`../S0568_camera-launch-widget.md`](../S0568_camera-launch-widget.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Steps done:** 3 / 3
**Started:** 2026-06-20
**Completed:** 2026-06-21 (probe + dev log in commit ab3f5d02; catalog sync 2026-06-21)

---

## Objective

Insert the device-test probe, record the shipped capability, regenerate the class catalog, and batch the dev log - closing the ticket toward `BlockNeedUserTest`.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done (widget reachable end-to-end).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraLaunchWidgetManager.kt` | Modified | ≤ 200 |
| `docs/ALL_FEATURES.jsonl` | Modified (via `add.ps1`) | n/a |
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated (gitignored) | n/a |

---

## Steps

### Step 05.1 - Insert the device-test probe

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraLaunchWidgetManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> At the entry of the changed flow (start of `CameraLaunchWidgetManager.start()`), add exactly one probe `Timber.d("S0568: camera launch widget tapped - resolving photo/video availability")`. This is the only `S0568:`-prefixed line in the codebase; it exists only while the ticket is `BlockNeedUserTest` and is removed by `/spec-check` on the transition out. Do not add probes anywhere else.

**Verification:**

- `Grep` - `Timber.d("S0568:` matches exactly once across `app_v2/src`.
- `/build` - `standard debug` compiles with the probe.

**Status:** `[x]` done

---

### Step 05.2 - Record capability + regenerate catalog

**Files:** `docs/ALL_FEATURES.jsonl`, `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 05.1

**Prompt for developer:**

> Record the shipped capability in the developer inventory:
> `pwsh -NoProfile -File scripts/all_features/add.ps1` with area `Widgets`, name `Camera launch widget`, a one-line EN description ("A 1x1 home-screen widget that opens the in-app camera with an on-screen photo/video switch and saves captures to the device's public folders"), `flavors` = standard,lite,photos,legacy,vr,noLegal, `spec` = S0568, `status` = active. Then regenerate the class catalog and fill role/status for the new classes:
> `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then `set.ps1` role/status for `SaveCapturedMediaUseCase`, `CameraLaunchWidgetManager`, `CameraLaunchActivity`, `CameraLaunchWidgetProvider`.

**Verification:**

- `Grep` - `S0568` present in `docs/ALL_FEATURES.jsonl` - DEFERRED to the `Verified` flip: `/spec-all` does not write `ALL_FEATURES` directly; `/spec-check` records it on the `Tactical/BlockNeedUserTest -> Verified` transition after device-test (CLAUDE.md §11).
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- `Grep` - `CameraLaunchWidgetProvider` present in `dev/CATALOG/app_v2.jsonl` (catalog synced 2026-06-21).

**Status:** `[x]` done

---

### Step 05.3 - Batch dev log + final build

**Files:** (dev log only - no source change)
**Depends on:** Step 05.2

**Prompt for developer:**

> Add one dev-log entry per logical change (use `close-and-log.ps1 -DevLogs` to batch): the use case + manager refactor, the widget trampoline/provider/resources, the manifest registration, the capability record. Run a final `/build` (`standard debug`) to confirm the whole change set assembles.

**Verification:**

- `Grep` - `S0568` present in `dev/CHANGELOG.md`.
- `/build` - `standard debug` assembles clean (final gate).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - validated in commit ab3f5d02 (`standard debug`).
- [x] `Timber.d("S0568:` present exactly once (probe in place for `BlockNeedUserTest`).
- [ ] `docs/ALL_FEATURES.jsonl` has the S0568 capability record - deferred to the `Verified` flip (written by `/spec-check`, CLAUDE.md §11).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated with the new classes.
- [x] `dev/CHANGELOG.md` has entries for the change set.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this, set the ticket to `BlockNeedUserTest` (device-test: widget add, tap, photo/video switch, save destinations) and run `/spec-check S0568` once verified on a real device.

---

## Rollback Plan

Revert phase commit(s). The probe is removed on the way out of `BlockNeedUserTest` regardless; catalog regen is gitignored and idempotent.
