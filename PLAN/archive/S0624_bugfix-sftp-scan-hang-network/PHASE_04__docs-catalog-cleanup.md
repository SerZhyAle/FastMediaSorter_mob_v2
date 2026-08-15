# Phase 04 - docs-catalog-cleanup

**Strategic spec:** [`../S0624_bugfix-sftp-scan-hang-network.md`](../S0624_bugfix-sftp-scan-hang-network.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-06-22
**Completed:** 2026-06-22

> **Note:** all three steps executed in one `close-and-log.ps1` finalization pass (catalog scan+render, ALL_FEATURES record, batched dev logs). The auto-derived ALL_FEATURES id used a spec-id prefix; corrected to `network-cloud.sftp-scan-bounded-termination` (area "Network & Cloud") and re-validated.

---

## Objective

Regenerate the class catalog for the new public symbol, record the capability, and confirm dev-log coverage. No `docs/FEATURES*.md` change (strategic §8 = "Без изменений").

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done and the project compiles.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (+ `.md`) | Regenerated | n/a |
| `docs/ALL_FEATURES.jsonl` | Appended | n/a |

> Catalog indexes are local gitignored artifacts - regenerate, do not hand-edit.

---

## Steps

### Step 04.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once so the scan picks up the new public symbol `ScanTimeoutException` (and any signature changes). If `set.ps1` reports the new class missing `role`/`status`, fill them: `ScanTimeoutException` - role "Domain network exception for a watchdog-aborted scan", status active.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "ScanTimeoutException" -Module app_v2` returns the record.

**Status:** `[ ]` not done

---

### Step 04.2 - Record the capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Append one record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the shipped reliability capability (EN-only): an SFTP folder scan now always terminates - on a network change or a dead/half-open connection it stops within a bounded time and shows a clear, retryable error instead of an endless loading spinner. Use op `FIX`, link `spec` = `S0624`. Validate with `scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - `S0624` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[ ]` not done

---

### Step 04.3 - Confirm dev-log coverage for every modified file

**Files:** `dev/CHANGELOG.md` (via script only)
**Depends on:** Step 04.1, Step 04.2

**Prompt for developer:**

> Ensure one dev-log entry exists per logical change (the SFTP scan-hang fix can be a single batched entry covering the pool/client/scanner/strings/UI edits). Add any missing entry via `.\scripts\add_to_dev_log.ps1` - never edit `dev/CHANGELOG.md` directly. No `docs/FEATURES*.md` edit (strategic §8 = "Без изменений"). No settings-doc regen (no setting added/changed).

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains an entry referencing the SFTP scan-hang fix / `S0624`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (query returns `ScanTimeoutException`).
- [x] `docs/ALL_FEATURES.jsonl` has the S0624 record (`network-cloud.sftp-scan-bounded-termination`); `validate.ps1` PASS (381 records).
- [x] `dev/CHANGELOG.md` covers the change (spec + code logical-change entries).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this, set journal status `BlockNeedUserTest` with the device-test script (strategic §6.2 stable-Wi-Fi repro, §6.4 large-catalog budget, plus the Wi-Fi→LTE handover repro and the §11 criteria); on device confirmation run `/spec-check S0624`.

---

## Rollback Plan

Catalog/docs regeneration only - no code or data impact. Re-run `catalog_sync.ps1` to restore.
