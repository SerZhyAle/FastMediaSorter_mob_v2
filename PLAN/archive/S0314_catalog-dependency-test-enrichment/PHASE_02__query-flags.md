# Phase 02 - query-flags

**Ticket:** S0314
**Owns:** Extend `query.ps1` so the catalogue answers the untested-domain-class question and the dependency question without a global grep, AND'd with existing filters.
**Files Touched:** `dev/CATALOG/scripts/query.ps1`

> Depends on Phase 01: the `constructorDeps` field must exist in the JSONL before a `-DependsOn` filter is meaningful. Field/flag names assume the BLK-02 default (`constructorDeps` / `-DependsOn`); substitute the owner-confirmed names if BLK-02 resolved otherwise.

---

## Context (static facts established by audit)

- `query.ps1` already AND's every supplied filter and already supports `-Tests` / `-NoTests` on `hasTests`, plus `-Layer`, `-Injected`, `-Missing`, `-Json`, etc.
- The untested-domain question is already answerable by combining existing flags (`-Layer domain -NoTests`); Phase 02 must verify this composition works and add a dependency filter that does **not** yet exist.
- `-Injected <Type>` filters on the `injected` array only; it cannot see non-`@Inject` constructor collaborators.

---

## Steps

- [x] **S02.1 - Add the dependency filter parameter.** Add a `[string]$DependsOn` parameter to the `param(..)` block. Do not remove or rename any existing parameter.
  - Verification: `Grep` for `[string]$DependsOn` in `query.ps1` returns exactly 1 match.
  - Verification: `Grep` for `[string]$Injected` in `query.ps1` still returns exactly 1 match (existing parameter preserved).
  - Result: `[string]$DependsOn` `expected: 1 | actual: 1` (inserted right after `[string]$Injected`); `[string]$Injected` `expected: 1 | actual: 1` (preserved).

- [x] **S02.2 - Implement the dependency filter body.** After the existing `-Injected` filter line, add a filter that keeps records whose `constructorDeps` array contains `$DependsOn`. Keep it AND-composable with all other filters (same `$result = @($result | Where-Object { .. })` pattern).
  - Verification: `Grep` for `$_.constructorDeps -contains $DependsOn` in `query.ps1` returns exactly 1 match.
  - Verification: `Grep` for `if ($DependsOn)` in `query.ps1` returns exactly 1 match.
  - Result: `$_.constructorDeps -contains $DependsOn` `expected: 1 | actual: 1`; `if ($DependsOn)` `expected: 1 | actual: 1`. Added on the line immediately after the `-Injected` filter using the identical `$result = @($result | Where-Object { .. })` pattern, so it AND-composes with every other filter.

- [x] **S02.3 - Preserve existing filter semantics.** Confirm the `-Injected` filter line and the `-Tests` / `-NoTests` lines are unchanged.
  - Verification: `Grep` for `$_.injected -contains $Injected` in `query.ps1` returns exactly 1 match.
  - Verification: `Grep` for `$_.hasTests` in `query.ps1` returns at least 2 matches (`-Tests` and `-NoTests` branches intact).
  - Result: `$_.injected -contains $Injected` `expected: 1 | actual: 1` (unchanged); `$_.hasTests` `expected: >=2 | actual: 2` (`-Tests` and `-NoTests` branches byte-for-byte intact). Cross-check at runtime: `-Injected ResourceDao` returns the same 7 rows and is fully covered by `-DependsOn ResourceDao`.

- [x] **S02.4 - Update the usage header.** Add a commented usage example for `-DependsOn` near the existing `-Injected` example at the top of `query.ps1`, and add `-DependsOn` to any inline filter list comment if present.
  - Verification: `Grep` for `-DependsOn` in the header comment region of `query.ps1` returns at least 1 match.
  - Result: `-DependsOn` usage example added directly under the `-Injected` example (line 18), described as a superset of `-Injected` that also catches non-Hilt collaborators. `expected: >=1 in header | actual: 1`. No inline filter-list comment exists in `query.ps1` (that list lives in README, handled in Phase 03), so nothing else to amend here.

- [x] **S02.5 - Untested-domain query gate (owner-run, app_v2).** The owner runs `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Layer domain -NoTests -Json`. Expected: exit code 0; output is zero or more JSON records, each with `layer` == `domain` and a falsey `hasTests`. No `rg` / `grep` / `Select-String` involved.
  - Verification: record `expected: exit 0 & every row layer=domain & hasTests falsey | actual: <observed>`. Non-trivial executable evidence per CLAUDE.md §16.
  - Result (auto-run by executor): `expected: exit 0 & every row layer=domain & hasTests falsey | actual: clean ($?=True; child-process exit 0), 165 rows, 0 violations`. No grep/rg/Select-String used (pure `query.ps1 -Json`). Note: an in-process `& query.ps1` leaves `$LASTEXITCODE` unset because the script ends with `return` and calls no external exe; clean completion asserted via `$?` + a separate `pwsh -File` child process returning exit 0.

- [x] **S02.6 - Dependency query gate (owner-run, app_v2).** The owner picks a type known to appear in some constructor (e.g. a repository interface) and runs `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -DependsOn <Type> -Json`. Expected: exit code 0; each returned record's `constructorDeps` contains `<Type>`.
  - Verification: record `expected: exit 0 & every row constructorDeps contains <Type> | actual: <observed>`. Non-trivial executable evidence per CLAUDE.md §16.
  - Result (auto-run, `<Type>`=`ResourceDao`): `expected: exit 0 & every row constructorDeps contains ResourceDao | actual: clean, 7 rows, 0 violations`. Sample row `AppShortcutsManager` constructorDeps=`[ApplicationContext,Context,ResourceDao]`.

- [x] **S02.7 - Combined-filter sanity.** The owner runs the dependency filter AND'd with `-Layer` (e.g. `-DependsOn <Type> -Layer domain`). Expected: result is a subset of S02.6's result, every row `layer` == `domain`.
  - Verification: record `expected: subset & layer=domain | actual: <observed>`.
  - Result (auto-run, `-DependsOn ResourceDao -Layer domain`): `expected: subset & layer=domain | actual: 2 rows, 0 layer violations, 0 not-subset (proper subset of S02.6's 7 rows)`. Confirms `-DependsOn` AND-composes with `-Layer`.

---

## Phase Done Criteria

At least the following invariants hold (all must be true):

1. `query.ps1` exposes a `-DependsOn` parameter that filters on `constructorDeps` and AND-composes with every existing filter; no existing parameter was removed or renamed.
2. The untested-domain question is answerable purely through `query.ps1` flags (`-Layer domain -NoTests`) with exit 0 and no external grep.
3. The dependency question is answerable through `-DependsOn <Type>` with exit 0, and every returned record genuinely contains `<Type>` in `constructorDeps`.
4. `-Injected`, `-Tests`, and `-NoTests` behaviour is byte-for-byte unchanged.
5. The `query.ps1` usage header documents `-DependsOn`.
