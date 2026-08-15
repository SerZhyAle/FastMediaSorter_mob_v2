# Phase 01 - Foundations

**Strategic spec:** [`../S0313_flavor-isolation-diff-guard.md`](../S0313_flavor-isolation-diff-guard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Establish the detector primitives: the forbidden-token pattern set, the change-source resolver (git diff of `src/main` Kotlin or explicit `-Path` list), and the violation-record contract. No classification, gating, or report output yet.

---

## Prerequisites

- [ ] INDEX.md Pre-Implementation Blocker (strategic §6.1 baseline) is checked.
- [ ] `pwsh` 7 and `git` are callable from the repo shell.
- [ ] `scripts/guard/` is writable (created by this phase).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/guard/FlavorTokens.ps1` | New | ≤ 120 |
| `scripts/guard/ChangeSource.ps1` | New | ≤ 160 |
| `scripts/guard/ViolationRecord.ps1` | New | ≤ 120 |

> This tool reads Kotlin only; it writes no `.kt`. Timber / `Log.d` / landscape-parity / flavor-source-set step rules are N/A. Build gate per step is "library dot-sources under `-NoProfile` and exposes its function", not gradle.

---

## Steps

### Step 01.1 - Define forbidden-token pattern set

**Files:** `scripts/guard/FlavorTokens.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/guard/FlavorTokens.ps1` exposing a function `Get-FlavorTokenPatterns` that returns the ordered set of forbidden flavor-gate token patterns enforced by CLAUDE.md Rule 15: `BuildConfig.IS_*`, `BuildConfig.SUPPORT_*`, `BuildConfig.ENABLE_*`. Encode them as a single anchored regex `BuildConfig\.(IS_|SUPPORT_|ENABLE_)[A-Z0-9_]+` plus, for each match, a `remediationCategory` string drawn from `dev/FLAVOR_DEVELOPMENT_RULES.md` (`source-set-impl`, `interface-boundary`, `noop-default`, `hilt-flavor-module`, `res-override`). Return objects with fields `pattern`, `tokenFamily`, `remediationCategory`. Do not scan files in this step - this module is data only.

**Verification:**

- `Glob` - `scripts/guard/FlavorTokens.ps1` exists.
- `Grep` - `function Get-FlavorTokenPatterns` matches exactly once.
- `Grep` - `BuildConfig\.(IS_|SUPPORT_|ENABLE_)` is present.
- `Grep` - `remediationCategory` is present.
- `pwsh -NoProfile -Command ". ./scripts/guard/FlavorTokens.ps1; if ((Get-FlavorTokenPatterns).Count -ge 1) { exit 0 } else { exit 1 }"` - expected exit: 0 | actual: 0.

**Status:** `[x]` done

---

### Step 01.2 - Resolve change source

**Files:** `scripts/guard/ChangeSource.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `scripts/guard/ChangeSource.ps1` exposing `Get-ChangedMainKotlin` that resolves the set of `src/main` Kotlin files to scan. Default source: `git diff --name-only` (and `--cached`) filtered to `app_v2/src/main/java/**/*.kt`. Override: an explicit `-Path` string array, also filtered to the same `src/main/java` Kotlin root. Add a `-Ref` parameter to diff against a given base ref (default unstaged + staged). Return objects with fields `file`, `source` (`diff` or `explicit`). A file outside `app_v2/src/main/java` is excluded - flavor source sets (`src/<flavor>/java`) are never scanned because isolation there is correct by construction. Do not read file contents in this step.

**Verification:**

- `Glob` - `scripts/guard/ChangeSource.ps1` exists.
- `Grep` - `function Get-ChangedMainKotlin` matches exactly once.
- `Grep` - `git diff` is present.
- `Grep` - `src/main/java` is present.
- `Grep` - `param` block contains `[string[]]$Path` and `[string]$Ref`.
- `pwsh -NoProfile -Command ". ./scripts/guard/ChangeSource.ps1; \$r = Get-ChangedMainKotlin -Path @('app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt'); if (\$r.Count -eq 1 -and \$r[0].source -eq 'explicit') { exit 0 } else { exit 1 }"` - expected exit: 0 | actual: 0.

**Status:** `[x]` done

---

### Step 01.3 - Define violation-record contract

**Files:** `scripts/guard/ViolationRecord.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `scripts/guard/ViolationRecord.ps1` exposing `New-FlavorViolation` that builds one violation record with exactly these fields: `file` (repo-relative path), `line` (1-based int), `matchedToken` (the literal `BuildConfig.*` substring), `tokenFamily` (`IS` / `SUPPORT` / `ENABLE`), `remediationCategory` (from `FlavorTokens.ps1`), `classification` (placeholder string set later by Phase 02 - default `unset`). The function takes raw inputs and returns the ordered record object only - it performs no scanning and no classification.

**Verification:**

- `Glob` - `scripts/guard/ViolationRecord.ps1` exists.
- `Grep` - `function New-FlavorViolation` matches exactly once.
- `Grep` - `matchedToken` is present.
- `Grep` - `remediationCategory` is present.
- `Grep` - `classification` is present.
- `pwsh -NoProfile -Command ". ./scripts/guard/ViolationRecord.ps1; \$v = New-FlavorViolation -File 'a/b.kt' -Line 7 -MatchedToken 'BuildConfig.IS_NO_LEGAL_FLAVOR' -TokenFamily 'IS' -RemediationCategory 'interface-boundary'; if (\$v.line -eq 7 -and \$v.classification -eq 'unset') { exit 0 } else { exit 1 }"` - expected exit: 0 | actual: 0.

**Status:** `[x]` done

---

### Step 01.4 - Smoke-wire the three modules together

**Files:** `scripts/guard/FlavorTokens.ps1`, `scripts/guard/ChangeSource.ps1`, `scripts/guard/ViolationRecord.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Confirm the three library modules dot-source into one PowerShell session with no name collisions and no side effects (no scanning, no writes) on import. Do not add a driver entrypoint yet - that is Phase 02. Add a one-line header comment to each module naming its single exported function and the S-ticket (`S0313`) as plain text (not a `Timber` tag, not a log probe).

**Verification:**

- `Grep` - `S0313` is present in `scripts/guard/FlavorTokens.ps1`.
- `Grep` - `S0313` is present in `scripts/guard/ChangeSource.ps1`.
- `Grep` - `S0313` is present in `scripts/guard/ViolationRecord.ps1`.
- `pwsh -NoProfile -Command ". ./scripts/guard/FlavorTokens.ps1; . ./scripts/guard/ChangeSource.ps1; . ./scripts/guard/ViolationRecord.ps1; if ((Get-Command Get-FlavorTokenPatterns,Get-ChangedMainKotlin,New-FlavorViolation).Count -eq 3) { exit 0 } else { exit 1 }"` - expected exit: 0 | actual: 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] All three library modules dot-source together under `-NoProfile` with exit 0 (Step 01.4 predicate). expected exit: 0 | actual: 0.
- [x] `Glob` - the three files under `scripts/guard/` all exist.
- [x] No file was written to the project root and no `.kt` file was created or modified.
- [x] Dev log entry added for every file in "Files Touched" via `scripts/post-change.ps1` (ChangeType `Script`).

---

## Handoff Notes to Next Phase

Phase 02 consumes `Get-ChangedMainKotlin` for the file set, `Get-FlavorTokenPatterns` for matching, and `New-FlavorViolation` for records. The `classification` field is the seam Phase 02 fills with `new-or-touched` vs `legacy`.

---

## Rollback Plan

Delete `scripts/guard/FlavorTokens.ps1`, `scripts/guard/ChangeSource.ps1`, `scripts/guard/ViolationRecord.ps1`. No source, catalog, or user-facing surface changed.
