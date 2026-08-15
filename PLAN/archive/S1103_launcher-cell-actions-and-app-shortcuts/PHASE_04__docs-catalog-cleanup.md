# Phase 04 - Docs & Catalog Cleanup

**Strategic spec:** [`../S1103_launcher-cell-actions-and-app-shortcuts.md`](../S1103_launcher-cell-actions-and-app-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-07-22
**Completed:** 2026-07-22

**Step Log:**

- 2026-07-22 - Recorded via close-and-log: FuncOp ADD, area Launcher, flavors standard,noLegal; catalog scan+render done (203s).

---

## Objective

Record the delivered Part-1 capability and refresh the class catalog. `docs/FEATURES*.md` is release-owned (not edited here).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 04.1 - Record the capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only capability record via the close-and-log feature block (FuncOp ADD): launcher desktop cells can now open the quick-access panel and trigger a saved scheduled operation (confirm, then background run with a result toast). Note in the description that third-party app-shortcut variants are out of scope (S0427). Flavors from the gate: standard + noLegal (launcherEnabled). Read the record back to confirm flavors.

**Verification:**

- `Grep` - the new record present in `docs/ALL_FEATURES.jsonl` with `flavors` = standard + noLegal.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.

**Status:** `[x]` done

---

### Step 04.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once to index the new command variant + picker. Do not hand-edit the generated index.

**Verification:**

- `catalog_sync.ps1` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `dev/CHANGELOG.md` has entries for the ticket (via `add_to_dev_log.ps1`).
- [ ] `docs/FEATURES*.md` untouched (release-owned).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Criterion 3 (app-shortcuts) stays open for S0427.

---

## Rollback Plan

Revert catalog/dev-log entries; no runtime impact.
