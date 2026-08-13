# Phase 01 - Sources and record contract

**Strategic spec:** [`../S0315_rule-prompt-drift-audit.md`](../S0315_rule-prompt-drift-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Define the canonical-source set as a data manifest and the executable-mismatch record contract (`sourceA`, `sourceB`, `mismatchKind`, `evidence`, `expected`, `actual`); add a source-discovery library module that enumerates the audited files. No detection logic and no report emission yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done (none - foundation phase).
- [ ] Strategic §6.1 (rule drift baseline) Pre-Implementation Blocker is resolved and checked in INDEX.md. Phase 01 must not start otherwise.
- [ ] Working tree is clean or on a feature branch.
- [ ] `scripts/doc-drift/` exists and its `Output.ps1` record-grammar pattern has been read as the reuse reference.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/doc-drift/sources.psd1` | New | ≤ 120 |
| `scripts/doc-drift/RulePromptSources.ps1` | New | ≤ 220 |
| `scripts/doc-drift/RulePromptRecord.ps1` | New | ≤ 160 |

> No file projected >500 lines. No `.kt`, no XML, no layout, no flavor source set - landscape-parity and flavor-isolation rules are N/A for this phase.

---

## Steps

### Step 01.1 - Declare the canonical-source manifest

**Files:** `scripts/doc-drift/sources.psd1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a PowerShell data file (`Import-PowerShellDataFile`-loadable) that declares the audited source set as data, so the set is extended without code change. Include a top-level `Canonical` key whose value is the repo-relative path `CLAUDE.md`, and a `Surfaces` key: an array of entries, each with `path` (repo-relative file or glob), `kind` (one of `skill`, `agent`, `agentsmd`, `copilot`, `workflow`), and `audited` (`$true`). Cover at minimum: `.claude/commands/*.md` (kind `skill`), `.claude/agents/*.md` (kind `agent`), `AGENTS.md` (kind `agentsmd`), `.github/copilot-instructions.md` (kind `copilot`), `dev/AGENT_WORKFLOW.md` and `dev/PROJECT_OPERATIONS_INDEX.md` (kind `workflow`). Add a `ScriptRoots` key listing the directories whose `*.ps1` files form the on-disk script reality (`scripts`, `dev/CATALOG/scripts`, `dev/ACTIVITY_CATALOG/scripts`). Do not add any key describing prose, wording, tone, or style.

**Verification:**

- `Glob` - `scripts/doc-drift/sources.psd1` exists.
- `Grep` - `Canonical` matches in the file.
- `Grep` - `'CLAUDE.md'` matches in the file. | expected: present | actual: record on run.
- `Grep` - `.claude/commands` matches in the file.
- `Grep` - `.claude/agents` matches in the file.
- `Grep` - `ScriptRoots` matches in the file.
- `Grep` - `(?i)\b(style|tone|wording|prose)\b` returns zero hits in the file. | expected: 0 | actual: 0.
- Manual: `pwsh -NoProfile -Command "Import-PowerShellDataFile -LiteralPath scripts/doc-drift/sources.psd1 | Out-Null; if ($LASTEXITCODE) { exit 1 }"` exits 0. | expected: 0 | actual: 0.

**Status:** `[x]` done

---

### Step 01.2 - Define the executable-mismatch record contract

**Files:** `scripts/doc-drift/RulePromptRecord.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a dot-sourceable library module exposing a single factory function `New-RulePromptRecord` that returns a `[PSCustomObject]` with exactly these fields: `mismatchKind` (string), `sourceA` (string - the repo-relative path or token that asserts something), `sourceB` (string - the repo-relative path or on-disk reality it conflicts with, or `disk` when the conflict is "documented but absent"), `evidence` (string - the offending line or token, trimmed), `expected` (string), `actual` (string), `severity` (one of `conflict`, `stale`, `missing`). Validate `mismatchKind` against an allowed set declared as a script-scope constant array `$script:RulePromptMismatchKinds` = `MissingDocumentedScript`, `ConflictingRouteName`, `MissingNoProfile`, `AbolishedArtifactReference`, `StaleCommandExample`, `OutdatedValidationRule`; throw on an unknown kind. The module must not emit output on dot-source (functions and the constant only).

**Verification:**

- `Glob` - `scripts/doc-drift/RulePromptRecord.ps1` exists.
- `Grep` - `function New-RulePromptRecord` matches exactly once.
- `Grep` - `\$script:RulePromptMismatchKinds` matches in the file.
- `Grep` - `MissingDocumentedScript` matches in the file.
- `Grep` - `ConflictingRouteName` matches in the file.
- `Grep` - `MissingNoProfile` matches in the file.
- `Grep` - `AbolishedArtifactReference` matches in the file.
- `Grep` - `StaleCommandExample` matches in the file.
- `Grep` - `OutdatedValidationRule` matches in the file.
- `Grep` - `expected` and `actual` both match in the file.
- Manual: `pwsh -NoProfile -Command ". ./scripts/doc-drift/RulePromptRecord.ps1; (Get-Command New-RulePromptRecord) | Out-Null"` exits 0 and prints nothing. | expected: 0 / empty | actual: 0 / empty (DOTSOURCE_CLEAN; throw-on-unknown True; 7 fields).

**Status:** `[x]` done

---

### Step 01.3 - Add the source-discovery library module

**Files:** `scripts/doc-drift/RulePromptSources.ps1`
**Depends on:** Step 01.1, Step 01.2

**Prompt for developer:**

> Create a dot-sourceable library module exposing two functions. `Get-RulePromptSources -Manifest <hashtable> -RepoRoot <string>` resolves the manifest into a flat list of audited file objects, each with `path` (absolute), `relPath` (repo-relative), and `kind`; globs in `Surfaces[].path` are expanded; the canonical `CLAUDE.md` is included with kind `canonical`. `Get-RulePromptScriptInventory -Manifest <hashtable> -RepoRoot <string>` returns the set of on-disk `*.ps1` files under `ScriptRoots` as objects with `relPath` (repo-relative, forward-slash normalized) so a later detector can ask "is this documented script present on disk". Both functions skip paths under `temp/`, `DOWNLOADS/`, `.venv/`, `logs/`, `.kotlin/`, and `node_modules`. Neither function reads file bodies for content - discovery only; body parsing belongs to Phase 02.

**Verification:**

- `Glob` - `scripts/doc-drift/RulePromptSources.ps1` exists.
- `Grep` - `function Get-RulePromptSources` matches exactly once.
- `Grep` - `function Get-RulePromptScriptInventory` matches exactly once.
- `Grep` - `RepoRoot` matches in the file.
- `Grep` - `temp/` and `node_modules` both match (exclusion list present).
- Manual: `pwsh -NoProfile -Command ". ./scripts/doc-drift/RulePromptRecord.ps1; . ./scripts/doc-drift/RulePromptSources.ps1; $m = Import-PowerShellDataFile scripts/doc-drift/sources.psd1; $s = Get-RulePromptSources -Manifest $m -RepoRoot (Resolve-Path .).Path; if ($s.Count -lt 5) { exit 1 }"` exits 0. | expected: 0 (≥5 audited files discovered) | actual: record on run.
- Manual: the same invocation, then `($s | Where-Object { $_.kind -eq 'canonical' }).relPath` equals `CLAUDE.md`. | expected: CLAUDE.md | actual: CLAUDE.md (SOURCES_COUNT=35; INV_COUNT=137).

**Status:** `[x]` done

---

### Step 01.4 - Confirm reuse boundary against existing drift scripts

**Files:** `scripts/doc-drift/RulePromptSources.ps1`, `scripts/doc-drift/RulePromptRecord.ps1`
**Depends on:** Step 01.2, Step 01.3

**Prompt for developer:**

> Confirm the new modules do not re-implement existing drift detectors and do not fork the S0278/S0279 doc-vs-gradle scope. Verify by inspection that neither new module parses Gradle sources, version pins, `pins.psd1`, nor spec-id markers - those belong to `check-doc-vs-gradle.ps1` and `spec_catalog/drift-check.ps1` respectively. Add a one-line comment header in `RulePromptSources.ps1` stating the reuse boundary: "Rule/prompt executable drift only - version-pin drift -> check-doc-vs-gradle.ps1; spec-id-marker drift -> spec_catalog/drift-check.ps1 (S0278/S0279 own doc-vs-gradle wear/PR-gate scope)."

**Verification:**

- `Grep` - `check-doc-vs-gradle.ps1` matches inside `scripts/doc-drift/RulePromptSources.ps1` (boundary comment present).
- `Grep` - `spec_catalog/drift-check.ps1` matches inside `scripts/doc-drift/RulePromptSources.ps1`.
- `Grep` - `pins.psd1` returns zero hits across `scripts/doc-drift/RulePromptSources.ps1` and `scripts/doc-drift/RulePromptRecord.ps1`. | expected: 0 | actual: 0.
- `Grep` - `Get-GradlePins` returns zero hits across the two new modules. | expected: 0 | actual: 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `sources.psd1`, `RulePromptSources.ps1`, and `RulePromptRecord.ps1` all dot-source / import `-NoProfile` with exit 0 and zero stdout noise.
- [x] `New-RulePromptRecord` throws on an unknown `mismatchKind` and returns the six-field-plus-severity object on a known one (exercise once by hand). | expected: throw / object | actual: throw True / object with 7 fields.
- [x] `Get-RulePromptSources` discovers ≥5 audited files and tags `CLAUDE.md` as `canonical`. | expected: ≥5 | actual: 35.
- [x] `Grep` for `TODO(phase-01)` across `scripts/doc-drift/` returns zero hits. | expected: 0 | actual: 0.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`. (deferred to Phase 03.3 per plan)
- [x] `dev/CATALOG/app_v2.jsonl` unchanged - no Kotlin file touched.

---

## Handoff Notes to Next Phase

Phase 02 consumes `New-RulePromptRecord` for every emitted finding, iterates the audited file list from `Get-RulePromptSources`, and checks documented-script presence against `Get-RulePromptScriptInventory`. The mismatch-kind constant is the closed taxonomy Phase 02 detectors must map into - no detector may invent a kind outside `$script:RulePromptMismatchKinds`.

---

## Rollback Plan

Revert the phase commit(s) - three new standalone library files, no entrypoint wired yet, no data migration or user-facing surface changed.
