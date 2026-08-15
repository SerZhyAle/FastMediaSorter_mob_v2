# Phase 04 - Docs / catalog cleanup

**Strategic spec:** [`../S0613_standalone-document-text-print-send-to.md`](../S0613_standalone-document-text-print-send-to.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-06-22
**Completed:** 2026-06-22

---

## Objective

Regenerate the class catalog for the new/renamed/changed types, record the delivered capability in the developer inventory, and confirm the closure gates - no source behavior change.

---

## Prerequisites

- [ ] Phases 01-03 ✅ Done and the project compiles.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated (gitignored local index) | - |
| `docs/ALL_FEATURES.jsonl` | Appended (1 record) | - |
| `dev/CHANGELOG.md` | Appended (via `add_to_dev_log.ps1`) | - |

> `docs/FEATURES*.md` is NOT edited - the public showcase is populated only by `/skill-release` (CLAUDE.md §11).

---

## Steps

### Step 4.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set `role` + `status` for the new `DocumentPrintHost` and renamed `PrintShareFallbackManager` via `dev/CATALOG/scripts/set.ps1` if the scan left them blank.

**Verification:**

- `Grep` (with `--no-ignore`) - `DocumentPrintHost` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` (with `--no-ignore`) - `PlayerPrintFallbackManager` absent from `dev/CATALOG/app_v2.jsonl` (renamed).

**Status:** `[x]` done

---

### Step 4.2 - Record the delivered capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Phases 01-03

**Prompt for developer:**

> Append one record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the shippable capability: printing a document (PDF/Office) or text file from the standalone viewer (opened from an external app) via the unified «Send to..» menu. EN-only, factual, no marketing. `spec` field = `S0613`. (Handled by `/spec-dev` on the `Implemented` transition if it runs closure; otherwise add here.)

**Verification:**

- `Grep` - a record with `"spec":"S0613"` (or `S0613` in the spec field) present in `docs/ALL_FEATURES.jsonl`.

**Status:** `[x]` done

---

### Step 4.3 - Dev log + closure gates

**Files:** `dev/CHANGELOG.md`
**Depends on:** Phases 01-03

**Prompt for developer:**

> Ensure `dev/CHANGELOG.md` has entries (via `add_to_dev_log.ps1` / `close-and-log.ps1 -DevLogs`) for the seam (Phase 01) and each host (Phases 02/03) - one logical entry per change, not per touched file. No `strings.xml` change occurred (print messages reused), so no `check_strings_localized.ps1` run is required; no setting changed, so no settings-doc gate.

**Verification:**

- `Grep` - `S0613` present in `dev/CHANGELOG.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `docs/ALL_FEATURES.jsonl` has the S0613 record.
- [ ] `dev/CHANGELOG.md` has S0613 entries.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-dev` inserts the `Timber.d("S0613: ..")` device-test tags at `DocumentStandaloneActivity.printMediaFile` and `TextStandaloneActivity.printMediaFile` before the final build, sets `BlockNeedUserTest`, then `/spec-check` verifies.

---

## Rollback Plan

Docs/catalog only - no rollback needed beyond reverting the inventory/changelog appends.
