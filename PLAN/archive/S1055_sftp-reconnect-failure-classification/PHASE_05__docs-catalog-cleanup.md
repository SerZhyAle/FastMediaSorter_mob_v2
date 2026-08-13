# Phase 05 - Docs / catalog cleanup

**Strategic spec:** [`../S1055_sftp-reconnect-failure-classification.md`](../S1055_sftp-reconnect-failure-classification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** -
**Completed:** 2026-07-15

---

## Objective

Regenerate the class catalog for the new exception subtype and close out mechanical bookkeeping.

---

## Prerequisites

- [ ] Phases 01-04 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (regenerated, gitignored) | Modified | n/a |
| `dev/CHANGELOG.md` (via `add_to_dev_log.ps1`) | Modified | n/a |

---

## Steps

### Step 05.1 - Regenerate catalog and set role/status for the new class

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set the role/status for the new `NetworkHostKeyChangedException` via `dev/CATALOG/scripts/set.ps1` (role: data-layer network exception type; status: active). No flavor isolation needed - the class lives in `src/main`.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "NetworkHostKeyChangedException"` returns the class.

**Status:** `[x]` done

---

### Step 05.2 - String audit and dev-log closure

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "error_"` (exit 0 required). Ensure a `dev/CHANGELOG.md` entry exists for every modified source file (batch via `close-and-log.ps1 -DevLogs`). `docs/FEATURES*` is NOT touched (strategic §8 = "Без изменений"). This is a security/correctness hardening, not a new user capability, so no `docs/ALL_FEATURES.jsonl` capability record unless `/spec-dev` judges the improved messaging a shippable capability.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "error_"` exits 0.
- `Grep` - `dev/CHANGELOG.md` contains entries for `NetworkExceptions.kt`, `NetworkErrorClassifier.kt`, `NetworkErrorMessageMapper.kt`, `SftpDataSource.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Both steps `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] String audit exits 0.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S1055`.

---

## Rollback Plan

No production code in this phase; catalog is regenerable. Nothing to roll back beyond the prior phases.
