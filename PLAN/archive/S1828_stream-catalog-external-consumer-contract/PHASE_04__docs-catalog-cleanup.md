# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S1828_stream-catalog-external-consumer-contract.md`](../S1828_stream-catalog-external-consumer-contract.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-20
**Completed:** 2026-08-20

---

## Objective

Re-render the script cheatsheet for the new gate and close the ticket through the mechanical facade.

---

## Prerequisites

- [ ] Phases 01, 02 and 03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/SCRIPT_CHEATSHEET.md` | Regenerated | - |
| `dev/CHANGELOG.md` | Appended via script | - |

> Never edit `dev/CHANGELOG.md` by hand - it is written by `add_to_dev_log.ps1` through the closure facade.

---

## Steps

### Step 04.1 - Re-render the script cheatsheet

**Files:** `docs/SCRIPT_CHEATSHEET.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Phase 02 added a repository script, so `assert-script-cheatsheet-sync.ps1` will report `docs/SCRIPT_CHEATSHEET.md` as stale. Regenerate it through `scripts/utils/help.ps1`, which that gate delegates its rebuild and byte-compare to, rather than editing the file.

**Why:**

Strategic §3.3 sets the validation level for this ticket, and CLAUDE.md's `script-cheatsheet-sync-gate` fails a closure whose changed set adds a repository script without re-rendering the cheatsheet.

**Verification:**

- Run `pwsh -NoProfile -File ./a.ps1 fg` - exit 0, no cheatsheet advisory.
- `Grep` - `assert-stream-asset-revisions` present in the regenerated cheatsheet.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Cheatsheet regenerated via help.ps1 (355 scripts); script-cheatsheet-sync OK. Final closure over all 7 changed files: post-change PASS (Tooling), exit 0, no advisories after -RegistryAck stream-catalog-consumers. No ALL_FEATURES record - strategic section 8 reads no FEATURES change.

---

### Step 04.2 - Close the ticket through the facade

**Files:** all files touched by Phases 01 to 03
**Depends on:** Step 04.1

**Prompt for developer:**

> Close through `scripts/post-change.ps1`, naming the whole changed set with `-Files` and adding `-ScopeToFile` so the scoped gates judge exactly this set on the always-dirty tree. Use `-ChangeType Tooling`: the set spans repository scripts and documentation and touches no Kotlin.
>
> Do not add a `docs/ALL_FEATURES.jsonl` record - strategic §8 reads "Без изменений в docs/FEATURES" and the ticket ships no user-visible capability.

**Why:**

Strategic §11 criterion 5 requires `validate.ps1` to pass after the records are added, and CLAUDE.md routes mechanical closure through the facade so the dev-log row, the gates and the catalog sync happen once and in order.

**Verification:**

- `post-change.ps1` prints `post-change: PASS` and exits 0.
- `Grep` - `dev/CHANGELOG.md` carries exactly one new row for this change.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Cheatsheet regenerated via help.ps1 (355 scripts); script-cheatsheet-sync OK. Final closure over all 7 changed files: post-change PASS (Tooling), exit 0, no advisories after -RegistryAck stream-catalog-consumers. No ALL_FEATURES record - strategic section 8 reads no FEATURES change.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `docs/FEATURES*.md` untouched - strategic §8 reads "Без изменений".
- [x] `dev/CHANGELOG.md` has an entry for this change.
- [x] `dev/CATALOG/` not regenerated - no Kotlin touched.
- [ ] `/spec-check S1828` returns `Verified`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commits. No data migration or user-facing surface changed.
