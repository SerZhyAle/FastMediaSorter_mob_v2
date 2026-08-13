# Phase 03 - Docs & catalog cleanup

**Strategic spec:** [`../S0478_send-to-menu-icons.md`](../S0478_send-to-menu-icons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Regenerate the class catalog for the changed share module and record the dev log for every touched file.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CATALOG/app_v2.md` | Regenerated | n/a |
| `dev/CHANGELOG.md` | Appended (via script) | n/a |

> `docs/FEATURES*.md` are NOT touched - strategic §8 is "Без изменений".

---

## Steps

### Step 03.1 - Regenerate the app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`

**Depends on:** Phase 02

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to rescan and re-render the catalog after the share-module declaration changes.

**Verification:**

- `Bash` - `catalog_sync.ps1 -Module app_v2` exits 0 and prints `OK (app_v2)`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Done via `close-and-log.ps1 -CatalogModule app_v2` (scan + render) at spec-dev finalization. Catalog regenerated.

---

### Step 03.2 - Record dev log for all touched files

**Files:** `dev/CHANGELOG.md`

**Depends on:** Step 03.1

**Prompt for developer:**

> Add one `scripts/add_to_dev_log.ps1` entry per file changed across Phases 01-02: the six new drawables, `ShareTargetModule.kt`, `SendToMenuManager.kt`, `ShareTargetIconResolver.kt`. Never hand-edit `dev/CHANGELOG.md`.

**Verification:**

- `Grep` - `ic_send_camera` present in `dev/CHANGELOG.md`.
- `Grep` - `SendToMenuManager.kt` entry dated today present in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Done via `close-and-log.ps1 -DevLogs @(..)` at spec-dev finalization: 10 entries (6 drawables + ShareTargetModule + SendToMenuManager + ShareTargetIconResolver + spec).

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: device-test the two menu presentations, then `/spec-check S0478`.

---

## Rollback Plan

Catalog files are gitignored local indexes - re-run `catalog_sync.ps1` to restore. Dev log is append-only; no rollback needed.
