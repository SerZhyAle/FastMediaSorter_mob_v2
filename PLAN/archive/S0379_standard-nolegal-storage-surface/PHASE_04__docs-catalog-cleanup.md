# Phase 04 - Docs Catalog Cleanup

**Strategic spec:** [`../S0379_standard-nolegal-storage-surface.md`](../S0379_standard-nolegal-storage-surface.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01-03
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-07
**Completed:** 2026-06-07

---

## Objective

Close the implementation with docs, catalog regeneration, and user-visible wording cleanup.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done or intentionally skipped with owner sign-off.
- [ ] Final behavior is known.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 120 |
| `docs/FEATURES_RU.md` | Modified | ≤ 120 |
| `docs/FEATURES_UK.md` | Modified | ≤ 120 |
| `docs/FEATURES_noLegal.md` | Modified | ≤ 120 |
| `docs/FEATURES_noLegal_RU.md` | Modified | ≤ 120 |
| `docs/FEATURES_noLegal_UK.md` | Modified | ≤ 120 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 40 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 40 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 40 |
| `dev/CATALOG/app_v2.jsonl` | Modified | generated |
| `dev/CATALOG/app_v2.md` | Modified | generated |

---

## Steps

### Step 04.1 - Update public storage docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** 03.2

**Prompt for developer:**

- Describe the final public `standard` storage surface.
- Include the OTG/SD and SAF-tree write behavior that actually shipped.
- State platform limits honestly.

**Verification:**

- All three public FEATURES files mention the new public storage behavior.

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification PASS. Public FEATURES docs now describe SAF-backed OTG/SD destinations and platform limits in EN/RU/UK.

### Step 04.2 - Update noLegal-only docs and storage wording

**Files:** `docs/FEATURES_noLegal.md`, `docs/FEATURES_noLegal_RU.md`, `docs/FEATURES_noLegal_UK.md`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** 04.1

**Prompt for developer:**

- Update noLegal-only docs only if Phase 03 shipped user-visible behavior.
- Rewrite any overpromising storage wording so it matches the implemented platform ceiling.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Verification:**

- noLegal FEATURES files match shipped noLegal-only behavior, or remain untouched if Phase 03 did not ship user-visible behavior.
- Updated storage wording no longer promises unrestricted access.

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification PASS. noLegal inventory now documents the restricted-tree overlay, and the hidden-files tooltip no longer promises unrestricted system access.

### Step 04.3 - Run catalog and closure tooling

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** 04.2

**Prompt for developer:**

- Run catalog sync after all `.kt` changes.
- Run post-change closure for every modified file.
- Leave the ticket ready for `/spec-check`.

**Verification:**

- `scripts/catalog_sync.ps1 -Module app_v2` completed successfully after Kotlin changes.
- Every modified file has a dev-log entry.

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification PASS. `scripts/catalog_sync.ps1 -Module app_v2` completed, dev-log closure recorded, and `build-debug.PS1` passed after string updates.

---

## Phase Done Criteria

- [ ] Public docs match the shipped `standard` behavior.
- [ ] noLegal docs match shipped noLegal-only behavior.
- [ ] Catalog sync and dev-log closure are complete.
