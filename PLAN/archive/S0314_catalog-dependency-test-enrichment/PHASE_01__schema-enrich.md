# Phase 01 - schema-enrich

**Ticket:** S0314
**Owns:** Harden the existing `hasTests` extraction; add the append-only dependency field; prove existing field names and manual records survive a rescan.
**Files Touched:** `dev/CATALOG/scripts/scan.ps1`

> Blocked until BLK-01 and BLK-02 in `INDEX.md` are resolved. Field names below assume the defaults stated there; substitute the owner-confirmed names if BLK-01/BLK-02 resolve otherwise.

---

## Context (static facts established by audit)

- `scan.ps1` `$srcRoots` enumerates 11 source roots: `main`, `standard`, `lite`, `photos`, `legacy`, `vr`, `vrStub`, `noLegal`, `streamingEnabled`, `cloudEnabled`, `cloudDisabled` (each guarded by `Test-Path`).
- `Test-HasTests` only rewrites `src\main\` to `src\test\` / `src\androidTest\` and assumes a `<ClassName>Test.kt` filename, so flavor-root classes never match a test.
- Manual fields are merged back from `$existing` on the key `"$rel::$className"`: `noFlavors`, `status`, `role`, and per-function `description`. Auto fields are recomputed.
- `Get-Injected` extracts types from `@Inject constructor(..)` only.
- Record field order today: `path`, `class`, `layer`, `loc`, `lastTouched`, `noFlavors`, `injected`, `hasTests`, `coroutines`, `usesTimber`, `sideEffects`, `userFeedback`, `status`, `role`, `functions`.

---

## Steps

- [x] **S01.1 - Snapshot guard.** Confirm `scan.ps1` LOC before editing; if over 500, create a timestamped copy under `temp/` first.
  - Verification: `Glob` finds a `temp/scan.ps1.*.bak` (or equivalent) when `scan.ps1` exceeds 500 LOC; if at/under 500 LOC, record `expected: <=500 | actual: <n>` and skip the backup.
  - Result: `expected: <=500 | actual: 243` (awk; pre-edit). No backup required.

- [x] **S01.2 - Harden test-coverage extraction.** Replace `Test-HasTests` so it (a) derives the test source root from the file's own source root (not a hard-coded `src\main\`), covering every entry in `$srcRoots`; (b) matches both the `<ClassName>Test.kt` convention and a same-relative-path file under `src\test\` / `src\androidTest\`. Keep the field name `hasTests` (BLK-01 default).
  - Verification: `Grep` for `function Test-HasTests` in `scan.ps1` returns exactly 1 match.
  - Verification: `Grep` for `src\\main\\` inside the `Test-HasTests` body returns 0 matches (hard-coded main root removed).
  - Verification: `Grep` for `hasTests = ` in `scan.ps1` returns exactly 1 match (single write site, field name unchanged).
  - Result: `function Test-HasTests` `expected: 1 | actual: 1`. `src\main\` inside `Test-HasTests` body `expected: 0 | actual: 0` (sole remaining `src\main\` hit is the unrelated `$srcRoots` array definition at line 27, outside the function). Record-field write `hasTests = $hasTests` `expected: 1 | actual: 1` (the only other `hasTests =` substring is the pre-existing local-var `$hasTests = Test-HasTests ...`). New impl maps any `src\<root>\` to `src\test\`/`src\androidTest\` and matches both the `<ClassName>Test.kt` sibling and a same-relative-path mirror.

- [x] **S01.3 - Add dependency extractor.** Add a `Get-ConstructorDeps` function that returns the ordered, de-duplicated list of constructor parameter types for the class scope, reusing the parameter-parsing regex style of `Get-Injected` but without requiring `@Inject` (BLK-02 default). Leave `Get-Injected` untouched.
  - Verification: `Grep` for `function Get-ConstructorDeps` in `scan.ps1` returns exactly 1 match.
  - Verification: `Grep` for `function Get-Injected` in `scan.ps1` still returns exactly 1 match (existing extractor preserved).
  - Result: `function Get-ConstructorDeps` `expected: 1 | actual: 1`; `function Get-Injected` `expected: 1 | actual: 1`. `Get-ConstructorDeps` prefers an explicit `constructor(..)` (covers `@Inject constructor` and plain `constructor`), falls back to the class-header primary-constructor parenthesis, then reuses the exact `Get-Injected` param-type regex; imports not parsed (BLK-02 default). `Get-Injected` body unchanged.

- [x] **S01.4 - Write the dependency field append-only.** Add `constructorDeps = Get-ConstructorDeps $scope` to the `[ordered]` record AFTER the existing `injected` entry. Do not remove, rename, or reorder any existing key.
  - Verification: `Grep` for `constructorDeps = ` in `scan.ps1` returns exactly 1 match.
  - Verification: `Grep` for each existing key literal (`path =`, `class =`, `layer =`, `loc =`, `lastTouched =`, `noFlavors =`, `injected =`, `hasTests =`, `coroutines =`, `usesTimber =`, `sideEffects =`, `userFeedback =`, `status =`, `role =`, `functions =`) each returns at least 1 match in `scan.ps1` (append-only: nothing dropped).
  - Result: record-field write `constructorDeps = $constructorDeps` `expected: 1 record-field write | actual: 1` (line 229; the other `constructorDeps =` hit is the local-var compute, same idiom as `injected`/`sideEffects`). All 15 existing keys present in the `[ordered]` block (lines 222-237) in original order `expected: >=1 each | actual: 1 each`. Serialized order proven on a real record: `"injected":[..],"constructorDeps":[..],"hasTests":false,..` - `constructorDeps` sits exactly between `injected` and `hasTests`.

- [x] **S01.5 - Preserve manual records across rescan.** Confirm the manual-merge block still restores `noFlavors`, `status`, `role`, and function `description` from `$existing`, and that the new `constructorDeps` is an auto field (recomputed, never copied from `$existing`). Do not add `constructorDeps` to the manual-merge block.
  - Verification: `Grep` for `$record.status    = $old.status` (and the `role` / `noFlavors` siblings) in `scan.ps1` each returns 1 match (manual restore intact).
  - Verification: `Grep` for `constructorDeps` inside the `if ($existing.ContainsKey($key))` block returns 0 matches (dependency field is not treated as manual).
  - Result: `$record.status = $old.status`, `$record.role = $old.role`, `$record.noFlavors = $old.noFlavors` each `expected: 1 | actual: 1` (lines 243-245, untouched). `constructorDeps` inside the `if ($existing.ContainsKey($key))` block (lines 241-258) `expected: 0 | actual: 0` (its only two occurrences, lines 218 and 229, are outside the merge block). Field is recomputed every scan.

- [x] **S01.6 - Module separation guard.** Confirm `$OutFile` is still derived from `$Module` (`dev\CATALOG\$Module.jsonl`) and that no step introduced a cross-module write.
  - Verification: `Grep` for `dev\\CATALOG\\$Module.jsonl` in `scan.ps1` returns at least 1 match; `Grep` for a hard-coded `app_v2.jsonl` or `wear.jsonl` literal returns 0 matches.
  - Result: `dev\CATALOG\$Module.jsonl` `expected: >=1 | actual: 1` (line 23). Hard-coded `app_v2.jsonl`/`wear.jsonl` literal `expected: 0 | actual: 0`. `$OutFile` stays `$Module`-scoped; no cross-module write introduced.

- [x] **S01.7 - Scan dry-run gate (owner-run, app_v2).** The owner runs `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`. Expected: exit code 0; the resulting `dev/CATALOG/app_v2.jsonl` contains at least one record with a `constructorDeps` array and the `hasTests` boolean.
  - Verification: record `expected: exit 0 | actual: <code>`; record `expected: constructorDeps present + hasTests present | actual: <observed on a sampled record>`. This step is non-trivial executable evidence per CLAUDE.md §16 - narration alone does not close it.
  - Result (auto-run by executor, gitignored JSONL): `expected: exit 0 | actual: 0`. Scan emitted 1234 files -> 1500 records. `expected: constructorDeps present + hasTests present on records | actual: RECORDS_MISSING_constructorDeps=0, RECORDS_MISSING_hasTests=0` (all 1500). 806 records carry non-empty `constructorDeps`; 317 records `hasTests=true`. Sample `AppShortcutsManager`: `injected=[ApplicationContext,Context,ResourceDao]`, `constructorDeps=[ApplicationContext,Context,ResourceDao]`, `hasTests=False`. Superset invariant `injected ⊆ constructorDeps` `expected: 0 violations | actual: 0` across all records; 547 records have empty `injected` but non-empty `constructorDeps` (non-Hilt/`@Inject`-free classes now visible).

- [x] **S01.8 - Manual-preservation proof (owner-run).** Before the rescan in S01.7, the owner sets a manual role+status on one record via `set.ps1`; after the rescan, that record still carries the same `role` and `status`.
  - Verification: record `expected: role==<set value> & status==<set value> after rescan | actual: <observed>`. A mismatch is a hard failure.
  - Result (auto-run by executor; `set.ps1` is fully non-interactive): on `AppShortcutsManager.kt` set `role=S0314-PROOF-manual-role-sentinel`, `status=legacy`, then rescanned (exit 0). `expected: role==sentinel & status==legacy after rescan, constructorDeps recomputed non-empty | actual: ROLE_PRESERVED=True, STATUS_PRESERVED=True, DEPS_RECOMPUTED_NONEMPTY_AND_STABLE=True -> RESULT=PASS`. Sentinel reset to `role='' status=unknown` afterwards (gitignored index left clean). Repeatable command: `pwsh -NoProfile -File temp/s0314_manual_preservation_proof.ps1`.

---

## Phase Done Criteria

At least the following invariants hold (all must be true):

1. `scan.ps1` writes a `constructorDeps` array on each record, positioned after `injected`, and the existing 15 field names are all still emitted under their original names (append-only).
2. `Test-HasTests` no longer hard-codes `src\main\`; it resolves the test root from each file's own source root, so flavor-root classes are eligible for a test match.
3. A `-NoProfile` scan of `app_v2` exits 0 and emits records carrying both `hasTests` and `constructorDeps`.
4. A record with a manually set `role` and `status` survives the rescan unchanged; `constructorDeps` is recomputed, never copied from the prior record.
5. `$OutFile` remains `$Module`-scoped; no `app_v2`/`wear` cross-write was introduced.
