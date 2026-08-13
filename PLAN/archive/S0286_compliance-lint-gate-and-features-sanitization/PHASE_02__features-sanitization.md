# Phase 02 - features-sanitization

**Strategic spec:** [../S0286_compliance-lint-gate-and-features-sanitization.md](../S0286_compliance-lint-gate-and-features-sanitization.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Rewrite the public FEATURES bullets in neutral language and remove their temporary baseline suppressions.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] The compliance task is wired into `preBuild`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | <= 520 |
| `docs/FEATURES_RU.md` | Modified | <= 520 |
| `docs/FEATURES_UK.md` | Modified | <= 520 |
| `app_v2/compliance/platform-name-baseline.txt` | Modified | <= 240 |

---

## Steps

### Step 02.1 - Rewrite the public FEATURES bullets in neutral capability language

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Phase 01

**Prompt for developer:**

> Rewrite the four public feature bullets identified by strategic §1 / §8 so they describe capabilities, not named platforms. Keep the content aligned across EN / RU / UK and re-check the wording against `docs/COMMUNICATION_POLICY.md` §6.

**Verification:**

- `Grep` - `Instagram|TikTok|Threads|threads\.com|threads\.net` returns zero hits across `docs/FEATURES.md`, `docs/FEATURES_RU.md`, and `docs/FEATURES_UK.md`.
- `Grep` - the EN file still contains `Auto-download incoming links` and `Multiple accounts per host`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification PASS. Files: `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`. The four public bullets were rewritten in neutral capability language and the deny-list grep returns zero hits.

---

### Step 02.2 - Remove the public FEATURES suppressions from the baseline

**Files:** `app_v2/compliance/platform-name-baseline.txt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Delete the baseline lines that referenced `docs/FEATURES.md`, `docs/FEATURES_RU.md`, and `docs/FEATURES_UK.md`. After this step, the public FEATURES files must stay green without any baseline help.

**Verification:**

- `Grep` - `docs/FEATURES` returns zero hits in `app_v2/compliance/platform-name-baseline.txt`.
- `Grep` - `docs/FEATURES_RU` returns zero hits in `app_v2/compliance/platform-name-baseline.txt`.
- `Grep` - `docs/FEATURES_UK` returns zero hits in `app_v2/compliance/platform-name-baseline.txt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification PASS. File: `app_v2/compliance/platform-name-baseline.txt`. No public FEATURES suppressions remained after the implementation patch, so the baseline stayed source-only.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Public `docs/FEATURES*.md` contain zero deny-list matches.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The public feature inventory is now clean; the final phase only needs build validation, a negative probe, and spec metadata closure.

---

## Rollback Plan

Revert the three FEATURES edits and restore the deleted baseline lines.
