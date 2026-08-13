# Phase 02 - Mismatch detectors and report

**Strategic spec:** [`../S0315_rule-prompt-drift-audit.md`](../S0315_rule-prompt-drift-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 6 / 6
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Implement the executable-mismatch detectors over the Phase 01 source set, wire them behind a single `-NoProfile`-safe entrypoint that emits a `[PSCustomObject]` record stream, optional JSON, and a human verdict; style and prose drift are out of scope by construction.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] `New-RulePromptRecord`, `Get-RulePromptSources`, `Get-RulePromptScriptInventory`, and `sources.psd1` are present and dot-source clean.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/doc-drift/RulePromptDetectors.ps1` | New | ≤ 480 |
| `scripts/doc-drift/RulePromptOutput.ps1` | New | ≤ 200 |
| `scripts/doc-drift/check-rule-prompt-drift.ps1` | New | ≤ 220 |

> `RulePromptDetectors.ps1` is budgeted ≤ 480 lines; if it crosses 500 during implementation, split per detector before continuing (one detector function group per file). No `.kt`, no XML, no layout, no flavor source set - landscape-parity and flavor-isolation rules are N/A.

---

## Steps

### Step 02.1 - Detect documented-but-absent scripts

**Files:** `scripts/doc-drift/RulePromptDetectors.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a dot-sourceable module. Add `Find-MissingDocumentedScript -Sources <list> -Inventory <list>`. For each audited source file body, extract every referenced repo-relative script path matching `[\w./-]+\.ps1` (also catch `*.sh`). For each extracted path, if it is not present in the Phase 01 `Get-RulePromptScriptInventory` set and not a glob/placeholder (skip tokens containing `*`, `<`, `Sxxxx`, `${`), emit a `New-RulePromptRecord` with `mismatchKind = MissingDocumentedScript`, `severity = missing`, `sourceA` = the documenting file relPath, `sourceB = disk`, `evidence` = the offending reference, `expected` = "script present on disk", `actual` = "absent". Self-references inside the audit's own files and inside `scripts/doc-drift/README.md` examples are excluded.

**Verification:**

- `Glob` - `scripts/doc-drift/RulePromptDetectors.ps1` exists.
- `Grep` - `function Find-MissingDocumentedScript` matches exactly once.
- `Grep` - `MissingDocumentedScript` matches in the file.
- `Grep` - `\.ps1` matches (extraction pattern present).
- Manual: dot-source `RulePromptRecord.ps1` + `RulePromptSources.ps1` + `RulePromptDetectors.ps1`, then call `Find-MissingDocumentedScript` with a one-element synthetic source whose body cites `scripts/does-not-exist.ps1`; result contains exactly one record with `mismatchKind = MissingDocumentedScript`. | expected: 1 | actual: record on run.

**Status:** `[x]` done

---

### Step 02.2 - Detect missing `-NoProfile` in documented invocations

**Files:** `scripts/doc-drift/RulePromptDetectors.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `Find-MissingNoProfile -Sources <list>`. Scan each audited source body for command examples invoking PowerShell against a repo script: lines matching `pwsh\s+(?!.*-NoProfile).*-File\s+\S+\.ps1` or `pwsh\s+(?!.*-NoProfile).*-Command`. For each hit, emit a `New-RulePromptRecord` with `mismatchKind = MissingNoProfile`, `severity = stale`, `sourceA` = the file relPath, `sourceB` = the same file (self-inconsistency with the project `-NoProfile` rule), `evidence` = the trimmed offending line, `expected` = "pwsh -NoProfile", `actual` = "pwsh without -NoProfile". Lines that already contain `-NoProfile` anywhere produce no record. Fenced-code language tags and prose mentions of the word "profile" without a `pwsh` invocation are ignored.

**Verification:**

- `Grep` - `function Find-MissingNoProfile` matches exactly once.
- `Grep` - `-NoProfile` matches in the file (rule literal present).
- `Grep` - `MissingNoProfile` matches in the file.
- Manual: feed a synthetic source body containing the line `pwsh -File scripts/foo.ps1` -> exactly one `MissingNoProfile` record. | expected: 1 | actual: record on run.
- Manual: feed a synthetic source body containing `pwsh -NoProfile -File scripts/foo.ps1` -> zero records. | expected: 0 | actual: record on run.

**Status:** `[x]` done

---

### Step 02.3 - Detect abolished-artifact references and outdated validation rules

**Files:** `scripts/doc-drift/RulePromptDetectors.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add `Find-AbolishedArtifactReference -Sources <list>`. Declare a script-scope table of abolished tokens with the canonical replacement, sourced from CLAUDE.md current rules: `_spec_` segment in any `PLAN/` path (abolished - id identifies the artefact), separate audit/fix files under `PLAN/` (abolished - findings live in the ticket `## Last Audit` block), and the legacy `pwsh -File` ritual phrasing without `-NoProfile`. For each audited source body, for each abolished token present **as a prescription** (not inside a "do not" / "forbidden" / "abolished" negation on the same line), emit `New-RulePromptRecord` with `mismatchKind = AbolishedArtifactReference`, `severity = conflict`, `sourceA` = file relPath, `sourceB = CLAUDE.md`, `evidence` = trimmed line, `expected` = the canonical replacement, `actual` = the abolished token. Add `Find-OutdatedValidationRule -Sources <list> -Canonical <string>`: flag any surface that prescribes a per-step closure command which CLAUDE.md no longer lists (parameterize via the abolished table - reuse, do not hardcode a second copy). Lines that quote the token while prohibiting it are excluded.

**Verification:**

- `Grep` - `function Find-AbolishedArtifactReference` matches exactly once.
- `Grep` - `function Find-OutdatedValidationRule` matches exactly once.
- `Grep` - `_spec_` matches in the file (abolished token registered).
- `Grep` - `AbolishedArtifactReference` and `OutdatedValidationRule` both match in the file.
- Manual: synthetic source body with the prescriptive line `create PLAN/S0001_spec_foo.md` -> exactly one `AbolishedArtifactReference` record; a body with `never use a _spec_ segment` (negation) -> zero records. | expected: 1 / 0 | actual: record on run.

**Status:** `[x]` done

---

### Step 02.4 - Detect conflicting route names across skills

**Files:** `scripts/doc-drift/RulePromptDetectors.ps1`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add `Find-ConflictingRouteName -Sources <list> -Canonical <string>`. A "route" is a `/skill-name` token. Build the set of routes that physically exist as `.claude/commands/<name>.md` (kind `skill`). For every audited source body, extract `/[a-z][a-z0-9-]+` tokens that look like skill routes; for each token that is referenced as an actionable route but has no matching command file, emit `New-RulePromptRecord` with `mismatchKind = ConflictingRouteName`, `severity = conflict`, `sourceA` = file relPath, `sourceB` = `.claude/commands`, `evidence` = the route token, `expected` = "route file present", `actual` = "no .claude/commands/<route>.md". Separately, if CLAUDE.md's mandatory-skills table names a route whose command file is absent (or two table rows map the same situation to different routes), emit one record per conflict. Exclude tokens inside fenced non-shell examples, URLs, and file paths (a `/` followed by a known directory like `scripts/`, `docs/`, `dev/`, `PLAN/`).

**Verification:**

- `Grep` - `function Find-ConflictingRouteName` matches exactly once.
- `Grep` - `ConflictingRouteName` matches in the file.
- `Grep` - `.claude/commands` matches in the file.
- Manual: synthetic source citing `/totally-made-up-route` as an action -> exactly one `ConflictingRouteName` record; a body citing `/git` (which exists) -> zero records. | expected: 1 / 0 | actual: record on run.

**Status:** `[x]` done

---

### Step 02.5 - Add the record-stream output formatter

**Files:** `scripts/doc-drift/RulePromptOutput.ps1`
**Depends on:** Step 02.4

**Prompt for developer:**

> Create a dot-sourceable module mirroring the existing `scripts/doc-drift/Output.ps1` grammar style. Add `Format-RulePromptReport -Records <list> -Color:<switch>` returning an array of human-readable lines, one per record, in the grammar `<MISMATCHKIND> | <sourceA> | <sourceB> | expected: <expected> | actual: <actual>`, followed by a `SUMMARY | total: N | conflict: A | stale: B | missing: C` line counting by `severity`. Add `ConvertTo-RulePromptJson -Records <list>` returning a single compressed JSON object `{ "verdict": "...", "total": N, "byKind": {..}, "records": [..] }` where `verdict` is `DRIFT` when total > 0 else `CLEAN`. No prose-quality or style category appears in either output.

**Verification:**

- `Glob` - `scripts/doc-drift/RulePromptOutput.ps1` exists.
- `Grep` - `function Format-RulePromptReport` matches exactly once.
- `Grep` - `function ConvertTo-RulePromptJson` matches exactly once.
- `Grep` - `SUMMARY` matches in the file.
- `Grep` - `verdict` matches in the file.
- `Grep` - `(?i)\b(style|tone|wording|prose)\b` returns zero hits in the file. | expected: 0 | actual: record on run.
- Manual: feed two synthetic records (one `conflict`, one `missing`) to `ConvertTo-RulePromptJson`; output parses as JSON and `verdict` equals `DRIFT`, `total` equals 2. | expected: DRIFT / 2 | actual: record on run.

**Status:** `[x]` done

---

### Step 02.6 - Wire the single entrypoint

**Files:** `scripts/doc-drift/check-rule-prompt-drift.ps1`
**Depends on:** Step 02.5

**Prompt for developer:**

> Create the entrypoint script. `[CmdletBinding()]` with `param([switch]$Json, [switch]$DryRun, [switch]$Color)`. Set `$ErrorActionPreference = 'Stop'`. Resolve `$repoRoot` from `$PSScriptRoot/..`. Dot-source the four library modules (`RulePromptRecord.ps1`, `RulePromptSources.ps1`, `RulePromptDetectors.ps1`, `RulePromptOutput.ps1`) into the script scope. Load `sources.psd1` via `Import-PowerShellDataFile`. In `-DryRun` mode: print the resolved canonical source, the count and relPaths of audited surfaces, and the closed mismatch-kind category list, then `exit 0` writing nothing under `temp/`. Otherwise: build the source list and script inventory, run all five detectors, concatenate records, and either emit `ConvertTo-RulePromptJson` to stdout (`-Json`) or print `Format-RulePromptReport`. Write the JSON report to `temp/rule-prompt-drift_<yyyyMMdd-HHmmss>.json` on a non-dry run. Document the header per the shared contract. Exit codes: `0` no records (CLEAN); `1` one or more records (DRIFT); `2` usage / load error. The `.DESCRIPTION` header must enumerate the six mismatch kinds and state "executable mismatch only - no prose/style drift".

**Verification:**

- `Glob` - `scripts/doc-drift/check-rule-prompt-drift.ps1` exists.
- `Grep` - `param(` with `\$DryRun` and `\$Json` both present in the file.
- `Grep` - `Import-PowerShellDataFile` matches in the file.
- `Grep` - `temp/rule-prompt-drift` matches (artifact path under `temp/`). | expected: present | actual: record on run.
- `Grep` - `executable mismatch only` matches in the `.DESCRIPTION` header.
- `Grep` - `(?i)\b(style|tone|wording|prose)\b` returns zero prescriptive hits (only the "no prose/style drift" disclaimer is allowed). | expected: disclaimer only | actual: record on run.
- Manual: `pwsh -NoProfile -File scripts/doc-drift/check-rule-prompt-drift.ps1 -DryRun` exits 0, prints the six category names, and creates no file under `temp/`. | expected: exit 0 / 6 categories / no temp file | actual: record on run.
- Manual: `pwsh -NoProfile -File scripts/doc-drift/check-rule-prompt-drift.ps1 -Json` emits one parseable JSON object with a `verdict` field. | expected: valid JSON | actual: record on run.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/doc-drift/check-rule-prompt-drift.ps1 -DryRun` exits 0 and prints the six mismatch-kind categories. | expected: exit 0 | actual: record on run.
- [x] A full non-dry `-Json` run emits exactly one parseable JSON object and writes one file under `temp/`. | expected: 1 JSON / 1 temp file | actual: record on run.
- [x] Exit code is 1 when at least one record exists and 0 when none. | expected: 1 / 0 | actual: record on run.
- [x] `Grep` for `(?i)\b(style|tone|wording|prose)\b` across the four library modules and the entrypoint returns only the single "no prose/style drift" disclaimer. | expected: disclaimer only | actual: record on run.
- [x] `Grep` for `TODO(phase-02)` across `scripts/doc-drift/` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` unchanged - no Kotlin file touched.

---

## Handoff Notes to Next Phase

The entrypoint and its grammar are stable. Phase 03 documents the six categories and the record grammar next to the owning script, links them from `scripts/doc-drift/README.md`, and records the dev-log placeholder. No detection logic changes in Phase 03.

---

## Rollback Plan

Revert the phase commit(s) - three new standalone files plus detector functions; the entrypoint is not wired into any commit/push helper or post-change step, so removing it has no caller fallout. No data migration or user-facing surface changed.
