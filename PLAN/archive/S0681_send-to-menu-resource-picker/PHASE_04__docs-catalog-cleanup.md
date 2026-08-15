# Phase 04 - Docs & catalog cleanup

**Strategic spec:** [`../S0681_send-to-menu-resource-picker.md`](../S0681_send-to-menu-resource-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-25
**Completed:** 2026-06-25

---

## Objective

Record the new capability in the feature inventory, regenerate the class catalog for the changed public surface, and journal the change.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.
- [ ] Project compiles.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | +1 record |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CHANGELOG.md` | Modified (via script) | n/a |

> `docs/FEATURES*.md` is intentionally NOT touched - it is populated only by `/skill-release` from the `ALL_FEATURES` diff (CLAUDE.md §11).
> No setting was added or moved - the settings manifest/reference are NOT regenerated.

---

## Steps

### Step 04.1 - Record the capability in `ALL_FEATURES.jsonl`

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Phases 01-03

**Prompt for developer:**

> Add one EN-only capability record via `scripts/all_features/add.ps1` describing: the «Send to..» menu now ends with a permanent «Select resource..» entry in the player and standalone players that opens the recipient picker (copy current file to a destination resource or a custom «..» folder), mirroring the file browser's «Copy to». Validate with `pwsh -NoProfile -File scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - a record mentioning `Select resource` (or the capability id used) matches in `docs/ALL_FEATURES.jsonl`.
- Run `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.

**Status:** `[x]` done

---

### Step 04.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl` (+ `.md`)
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once (scan + render) to refresh the catalog after the `SendToMenuManager` / `StandaloneFileOperationsHandler` signature changes. These indexes are gitignored - regenerate, do not commit.

**Verification:**

- Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` - exit 0.

**Status:** `[x]` done

---

### Step 04.3 - Journal the change

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 04.2

**Prompt for developer:**

> Add one dev-log entry for the S0681 change via `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/share/SendToMenuManager.kt" "S0681" "Unify Send-to menu + pinned Select-resource copy-to entry across player and standalone players"`. Do not hand-edit `dev/CHANGELOG.md`.

**Verification:**

- `Grep` - `S0681` matches in `dev/CHANGELOG.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `docs/ALL_FEATURES.jsonl` validates.
- [ ] Catalog regenerated.
- [ ] `dev/CHANGELOG.md` has the S0681 entry.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: insert the `Timber.d("S0681: ..")` device-test probes at the changed flow entries and move the ticket to `BlockNeedUserTest` (handled by `/spec-dev`), then `/spec-check S0681`.

---

## Rollback Plan

Revert the dev-log/feature-inventory edits; regenerated catalog indexes are gitignored and need no rollback.
