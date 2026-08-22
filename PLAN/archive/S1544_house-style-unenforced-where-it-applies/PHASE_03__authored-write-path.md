# Phase 03 - Authored write path

**Strategic spec:** [`../S1544_house-style-unenforced-where-it-applies.md`](../S1544_house-style-unenforced-where-it-applies.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Apply the same normalizer on the mandatory authored write path, so a hand-written string key cannot enter the tree carrying a style divergence.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/set-android-string.ps1` | Modified | ≤ 790 |
| `scripts/quality.tests/set-android-string-remove.Tests.ps1` | Modified | ≤ 5 delta |

> The file is over 500 LOC, so step 03.1 carries an explicit backup sub-step per CLAUDE.md Rule 5.
>
> The test file was added to this phase during execution (2026-08-14): it builds a throwaway sandbox and copies the tool's library dependencies by name, so a new dot-source has to be added to that manifest or all 16 cases fail at load. Measured 16/0 before the change, 6/10 after the dot-source alone, 16/0 again once the manifest was updated.

---

## Steps

### Step 03.1 - Normalize the value beside the existing format assertion

**Files:** `scripts/utils/set-android-string.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Take a timestamped backup first, per CLAUDE.md Rule 5 - the file is over 500 LOC. Then dot-source `scripts/quality/lib/house-text-style.ps1` and normalize each incoming value with `Convert-HouseStyleText -Area ResourceValue` at the point where `Assert-FormatValue` is already called, so every action that writes a value - `set`, `add`, and the per-locale values behind `-En` / `-Ru` / `-Uk` / `-Translations` - passes through it. Apply the `ё` rule only to the `ru` locale; apply the ellipsis and long-dash rules to every locale. Leave `get`, `list`, `remove` and `rename` untouched: they write no value.

**Why:**

Strategic §5.3 requires the rule to be applied at the control point that already exists and is already mandatory, rather than by a new gate that S1340 §5 forbids.

**Verification:**

- A timestamped backup was taken before the edit (CLAUDE.md Rule 5).
- `Grep` - `house-text-style.ps1` dot-sourced exactly once.
- `Grep` - `Convert-HouseStyleText` reachable from `Invoke-Set` and from the multi-locale add path.
- `Grep` - no `Convert-HouseStyleText` call inside the `get` / `list` / `remove` / `rename` branches.
- Setting a value containing a long dash writes a plain hyphen; re-reading the key with `-Action get` returns the normalized text.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Normalization applied once on the locale value set and on -Value, before the format assertions; report names locale, key, before, after and rules. Dry-run proved normalization plus unchanged mtime. Pester 16/0 after adding the new library to the sandbox manifest (6/10 without it).

---

### Step 03.2 - Name the normalization in the tool's output

**Files:** `scripts/utils/set-android-string.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> When a value is changed by normalization, print one line naming the key, the locale and the rules that fired, before the write is reported. Keep `-DryRun` honest: it reports the normalization it would apply and writes nothing. Do not fail the call - normalization corrects, it does not refuse.

**Why:**

Strategic ADR-2 makes normalization silent for the build but loud for the reader, so the author sees that the text written differs from the text passed.

**Verification:**

- `Grep` - a normalization report line exists and names key, locale and rule.
- A `-DryRun` call with a long-dash value reports the normalization and leaves the file mtime unchanged.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` - exit 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Normalization applied once on the locale value set and on -Value, before the format assertions; report names locale, key, before, after and rules. Dry-run proved normalization plus unchanged mtime. Pester 16/0 after adding the new library to the sandbox manifest (6/10 without it).

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] No build required - this phase touches no compiled source.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Existing tests `scripts/quality.tests/set-android-string-remove.Tests.ps1` still pass.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Both write paths - authored and translated - now normalize. Everything still dirty in the tree is accumulated debt, which Phase 05 clears.

---

## Rollback Plan

Restore the timestamped Rule 5 backup - no resource file is written by this phase.
