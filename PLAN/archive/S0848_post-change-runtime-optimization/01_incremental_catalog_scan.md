# Phase 01 - Incremental catalog scan

**Goal:** Stop `scan.ps1` from calling `git log -1` once per `.kt`. When the caller names the changed files, compute `lastTouched` via git only for those; reuse the value already stored in the existing JSONL for every unchanged file. Full rebuild (no changed-files signal) keeps today's behaviour exactly.

## Steps

- [x] **1. Add `-ChangedFiles [string[]]` to `dev/CATALOG/scripts/scan.ps1`.**
  - Optional param. When null/empty -> full rebuild (current behaviour: git for every file).
  - Normalize each entry to an absolute, forward-slashed, lower-cased path (Join with `$Root` if relative) into a `HashSet[string]` `changedSet`.
  - Verification: `Get-Command` parses; `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (no `-ChangedFiles`) still succeeds and behaves as before.

- [x] **2. Build an existing-lastTouched-by-path map from the loaded JSONL.**
  - While reading `$OutFile` into `$existing`, also record `$existingLastTouchedByPath[$obj.path] = $obj.lastTouched` (first non-empty wins; all class records of a file share the file's date).
  - Verification: map is populated on a re-run where `$OutFile` exists.

- [x] **2. Gate the `Get-LastTouched` call per file.**
  - Replace the unconditional `$lastTouched = Get-LastTouched ...` (line ~202) with: if no `changedSet` OR file's normalized full path is in `changedSet` -> call git; else reuse `$existingLastTouchedByPath[$rel]`; if that is missing/empty (new file absent from JSONL) -> fall back to git.
  - Verification: with `-ChangedFiles <one file>`, git is invoked only for that file (confirm via a temporary trace or by timing on a large module); the emitted `lastTouched` for a changed file matches a direct `git log -1`.

- [x] **3. Thread `-ChangedFiles` through `scripts/catalog_sync.ps1`.**
  - Add optional `-ChangedFiles [string[]]`; forward to `scan.ps1` only when provided (`render.ps1` unaffected).
  - Verification: `catalog_sync -Module app_v2` (no `-ChangedFiles`) unchanged; `catalog_sync -Module app_v2 -ChangedFiles <f>` runs and forwards.

- [x] **4. Pass `-ChangedFiles $File` from `post-change.ps1` catalog-sync step.**
  - In the `catalog-sync` `Invoke-Step` (line ~220), add `-ChangedFiles $File`.
  - Verification: `post-change` for a single `.kt` completes; catalog JSONL updated; no per-file git storm.

## Parity check (phase acceptance)

- [x] Take a clean full scan JSONL as baseline. Run an incremental post-change touching one file, then a full `catalog_sync` (no `-ChangedFiles`). The full-refresh JSONL must be byte-identical to a from-scratch full scan (semantic parity of the full path preserved).
- [x] The incremental JSONL differs from the full-refresh JSONL only in `lastTouched` of files changed outside this run (acceptable staleness), never in structure/classes/functions.
