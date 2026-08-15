# Phase 04 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0313_flavor-isolation-diff-guard.md`](../S0313_flavor-isolation-diff-guard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** final audit
**Steps done:** 3 / 3
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Document the guard's exit codes, token list, and usage next to the owning script; close the dev log; confirm no `docs/FEATURES*` or class-catalog change is owed.

---

## Prerequisites

- [x] Phases 01, 02, 03 are ✅ Done.
- [x] `scripts/guard/flavor-isolation-guard.ps1` exposes documented exit codes (Phase 02).
- [x] No `.kt` file was added or modified by this plan (class catalog unaffected).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/guard/README.md` | New | ≤ 160 |
| `scripts/guard/flavor-isolation-guard.ps1` | Modified | ≤ 320 |

> Doc-only and header-only edits. This tool reads Kotlin only; it writes no `.kt`. No `docs/FEATURES*` change - strategic §8 records "Без изменений".

---

## Steps

### Step 04.1 - Document exit codes and token list in the script header

**Files:** `scripts/guard/flavor-isolation-guard.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Ensure the script header block fully documents: synopsis; the forbidden token list (`BuildConfig.IS_*`, `BuildConfig.SUPPORT_*`, `BuildConfig.ENABLE_*`) sourced from CLAUDE.md Rule 15; the change-source model (diff default, `-Path` override, `-Ref`); every exit code (`0` OK, `1` blocking new/touched, `2` bad usage, `3` internal error); the `-LegacyAudit`, `-Json`, `-DryRun` switches; the `temp/flavor-guard/report.json` artifact path. Mirror the documentation style of `scripts/check-doc-vs-gradle.ps1`. Do not embed `S0313` inside any `Timber`/log call - it is a static script; reference the ticket in plain header text only.

**Verification:**

- `Grep` - the header lists `BuildConfig.IS_`, `BuildConfig.SUPPORT_`, `BuildConfig.ENABLE_`.
- `Grep` - the header documents exit codes `0`, `1`, `2`, `3`.
- `Grep` - the header names `-LegacyAudit`, `-DryRun`, `-Json`, and `temp/flavor-guard/report.json`.
- `Grep` - no `Timber` or `Log.d` token appears anywhere in the script (it is PowerShell, not Kotlin).

**Status:** `[x]` done

---

### Step 04.2 - Write the guard README

**Files:** `scripts/guard/README.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create `scripts/guard/README.md` describing: purpose (diff-aware flavor-isolation guard, CLAUDE.md Rule 15 enforcement); the diff-only blocking default and why legacy debt is non-blocking (S0311 §6.2 - no untracked permanent baseline to rot); the shared S0311 §5.1 contract fields it honours (`-DryRun`, stable exit codes, optional JSON, human summary, `temp/` artifacts, no profile dependency, expected-vs-actual); how to run it manually and against an explicit path set; how to run the fixture self-test (`scripts/guard.tests/Run-Tests.ps1`); and an explicit "not yet wired into post-change/commit-helper" note (strategic §6 / S0311 §6.3 integration point stays manual until a follow-up decides).

**Verification:**

- `Glob` - `scripts/guard/README.md` exists.
- `Grep` - `Rule 15` is referenced.
- `Grep` - `diff-only` (or `diff-aware`) blocking model is described.
- `Grep` - `Run-Tests.ps1` is referenced.
- `Grep` - `post-change` manual-integration note is present.

**Status:** `[x]` done

---

### Step 04.3 - Close dev log and confirm no FEATURES/catalog debt

**Files:** dev log (`dev/CHANGELOG.md` via wrapper) - no direct edit
**Depends on:** Step 04.2

**Prompt for developer:**

> Run `scripts/post-change.ps1` with ChangeType `Script` for every file written across Phases 01-04 that is not yet logged, so `dev/CHANGELOG.md` carries one entry per file. Confirm `docs/FEATURES.md` / `_RU` / `_UK` are untouched (strategic §8 = "Без изменений"). Confirm no `dev/CATALOG/<module>.jsonl` regen is owed because no `.kt` was added or modified. Do not run `scan.ps1` / `render.ps1` / `catalog_sync.ps1` - there is nothing for them to pick up.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains an entry mentioning `flavor-isolation-guard` (the owning script).
- `Grep` - `docs/FEATURES.md` contains zero new flavor-guard entry (expected: 0 | actual: <fill>).
- `pwsh -NoProfile -File scripts/guard/flavor-isolation-guard.ps1 -DryRun -Path @()` - final smoke, expected exit: 0 | actual: <fill>.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `Glob` - `scripts/guard/README.md` exists.
- [x] Script header documents all four exit codes and the full token list (Step 04.1 predicates).
- [x] `dev/CHANGELOG.md` has one entry per file written by this plan.
- [x] No `docs/FEATURES*` change and no class-catalog regen were performed (none owed).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The guard is standalone and manually invoked; wiring it into `post-change.ps1` or a commit/push helper is the deferred S0311 §6.3 integration decision, intentionally out of S0313 scope.

---

## Rollback Plan

Delete `scripts/guard/README.md` and revert the header edit on `scripts/guard/flavor-isolation-guard.ps1`. No source, catalog, or user-facing surface changed.
