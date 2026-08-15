# Phase 04 - docs-catalog-cleanup

**Strategic spec:** [`../S1152_resume-stream-on-launch.md`](../S1152_resume-stream-on-launch.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-07-22
**Completed:** 2026-07-22

---

## Objective

Regenerate the class catalog for the new public classes and record the change in the dev log / feature inventory. No source behavior changes.

---

## Prerequisites

- [ ] Phases 01-03 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CHANGELOG.md` | Appended (via script) | - |

---

## Steps

### Step 04.1 - Regenerate the class catalog and set roles

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set `role`+`status` for the three new classes (`StreamResumeState`, `StreamResumeStateRepository`, `StreamResumeStateRepositoryImpl`) via `dev/CATALOG/scripts/set.ps1` if the scan left them unclassified. These are `standard`-and-derivatives (streams) classes; no `-NoFlavors` restriction needed (they live in `src/main`).

**Verification:**

- `Grep` - `StreamResumeStateRepositoryImpl` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

---

### Step 04.2 - Dev log + feature inventory

**Files:** `dev/CHANGELOG.md` (via script), `docs/ALL_FEATURES.jsonl` (via script)
**Depends on:** Step 04.1

**Prompt for developer:**

> Add a dev-log entry per modified/new source file via `.\scripts\add_to_dev_log.ps1` (batch through `close-and-log.ps1 -DevLogs` at ticket close). Record the shippable capability in `docs/ALL_FEATURES.jsonl` via `scripts/all_features/add.ps1` (EN-only) - area "Streams", capability "Resume last stream on launch", flavors = the builds that ship streams (read the gate; standard and derivatives with stream support). `docs/FEATURES*.md` is NOT edited here (release-owned). This record is normally written by `/spec-dev` on the `Implemented` flip; if it already exists for S1152, skip.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains `MainResumePlaybackHelper.kt` (or the batched ticket entry).
- `Grep` - `docs/ALL_FEATURES.jsonl` contains `S1152` in the `spec` field.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `dev/CHANGELOG.md` has entries for every modified file.
- [ ] Feature inventory recorded (or confirmed present).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S1152` (or the pipeline's device-test gate, since Phase 03 leaves the ticket in `BlockNeedUserTest`).

---

## Rollback Plan

Catalog/dev-log only - regenerate from source; nothing to revert in app behavior.
