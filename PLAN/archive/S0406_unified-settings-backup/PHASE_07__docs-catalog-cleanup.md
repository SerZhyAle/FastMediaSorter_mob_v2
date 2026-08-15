# Phase 07 - Docs, catalog, cleanup

**Strategic spec:** [`../S0406_unified-settings-backup.md`](../S0406_unified-settings-backup.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all
**Blocks:** none
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Finalize trilingual user-facing docs, regenerate the class catalog, and record the dev changelog.

---

## Prerequisites

- [ ] Phases 01–06 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 5 |
| `docs/FEATURES_RU.md` | Modified | ≤ 5 |
| `docs/FEATURES_UK.md` | Modified | ≤ 5 |

---

## Steps

### Step 07.1 - FEATURES trilingual sentence

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Update the existing settings-backup feature line in all three FEATURES files to state that backup now carries sources, favorites, schedules, network passwords and saved site authorizations in one format, to a local file or Google Drive, restorable after reinstall. Match the strategic §8 wording. Keep tone per `docs/COMMUNICATION_POLICY.md`. Do not duplicate the existing line - edit it.

**Verification:**

- `Grep` - the updated phrase present in `FEATURES.md`, `FEATURES_RU.md`, `FEATURES_UK.md`.

**Status:** `[ ]` not done

---

### Step 07.2 - Regenerate class catalog

**Files:** (generated index, gitignored)
**Depends on:** Step 07.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Set role/status for new classes (`BuildBackupPayloadUseCase`, `ApplyBackupPayloadUseCase`) via `set.ps1` if not auto-classified.

**Verification:**

- `Grep` - `BuildBackupPayloadUseCase` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `ApplyBackupPayloadUseCase` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

### Step 07.3 - Dev changelog for all modified files

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 07.2

**Prompt for developer:**

> Add a `dev/CHANGELOG.md` entry via `scripts/add_to_dev_log.ps1` for every source/doc file touched across phases 01–07 not already logged.

**Verification:**

- `Grep` - `S0406` present in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] String locale audit clean for any new keys (none expected; existing strings reused).
- [ ] Dev log entries present for all modified files.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Ticket moves to `BlockNeedUserTest` after a green build with debug verification tags inserted.

---

## Rollback Plan

Revert phase commit - docs/catalog/changelog only.
