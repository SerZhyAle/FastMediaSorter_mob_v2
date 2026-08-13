# Phase 04 - Docs Catalog Cleanup

**Strategic spec:** [`../S0259_settings-toggle-row-general-destinations.md`](../S0259_settings-toggle-row-general-destinations.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Run the repo bookkeeping and validation gates after the general and destinations migration is complete.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.
- [ ] All intended file edits are already in place.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` | Modified via script | n/a |
| `dev/CATALOG/app_v2.jsonl` | Modified via script | n/a |
| `dev/CATALOG/app_v2.md` | Modified via script | n/a |
| `PLAN/S0259_settings-toggle-row-general-destinations.md` | Modified | ≤ 260 |
| `PLAN/S0259_settings-toggle-row-general-destinations/INDEX.md` | Modified | ≤ 220 |
| `PLAN/S0259_settings-toggle-row-general-destinations/PHASE_*.md` | Modified | ≤ 260 each |

---

## Steps

### Step 04.1 - Record mandatory dev-log entries

**Files:** every modified source/config/spec file from Phases 01-03
**Depends on:** Phase 03

**Prompt for developer:**

> Run `add_to_dev_log.ps1` for every modified source/config/spec file that does not already have a same-step entry.

**Verification:**

- `Script` - every required `add_to_dev_log.ps1` invocation exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. Dev-log entries recorded for all modified source and spec files in this implementation batch.

---

### Step 04.2 - Regenerate the Kotlin catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run the one-shot catalog sync wrapper for `app_v2` after the Kotlin changes.

**Verification:**

- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` → exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exited 0.

---

### Step 04.3 - Run the target build gate

**Files:** all touched app_v2 sources/resources
**Depends on:** Step 04.2

**Prompt for developer:**

> Run the target `standardDebug` build gate for this migration batch.

**Verification:**

- `/build` - target standard debug build passes.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. `pwsh -NoProfile -File scripts/builders/build-debug.PS1` exited 0; `assembleStandardDebug` succeeded and produced `FastMediaSorter_standard_debug_v2.60.5192.227-DEBUG.apk`.

---

### Step 04.4 - Mark the spec implemented and hand off to audit

**Files:** `INDEX.md`, all phase files, strategic spec
**Depends on:** Step 04.3

**Prompt for developer:**

> Update phase statuses/counters, flip the tactical index to Done, mark the strategic spec implemented, and hand the ticket to `/spec-check S0259`. Do not add feature-doc entries because strategic §8 says this is not a new user-facing capability.

**Verification:**

- `Grep` - strategic spec contains `**Implemented date:** 2026-05-19` once completed.
- `Grep` - `INDEX.md` shows `Status: Done` and `Phases: 4 / 4 done`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 1/2 PASS. Strategic spec moved to `Implemented` with date; `/spec-check S0259` remains the next repo step for `Verified`.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `scripts/catalog_sync.ps1 -Module app_v2` exited 0.
- [x] Target build passed.
- [x] No feature-doc update was needed per strategic §8.
