---
description: "Use to audit a spec against the codebase and set status Verified/Partial/Broken, writing to the Last Audit section. Triggers: 'spec-check Sxxxx', 'is this spec actually implemented'."
---

# Specification Implementation Audit

Audit a spec against actual repository state. Auto-detects strategic vs tactical scope.

> **No audit file written.** Findings go in compact `## Last Audit` block at bottom of strategic spec. Block **overwritten** each run - only most recent audit kept. Journal `updated` timestamp moves on every audit. Old audit history intentionally discarded.

## Usage

```text
/spec-check <Sxxxx-or-slug>
/spec-check <Sxxxx-or-slug> --strategic
/spec-check <Sxxxx-or-slug> --tactical
/spec-check <Sxxxx-or-slug> --phase <NN>
/spec-check <Sxxxx-or-slug> --phases <01,03,05>
/spec-check <Sxxxx-or-slug> --strict        # treat WARN as FAIL
/spec-check <Sxxxx-or-slug> --quick         # skip grep-heavy invariants
```

---

## Auto-detection

| Strategic file | Tactical folder | Flag | Mode |
| :---: | :---: | --- | --- |
| exists | exists | none | **full** - strategic + every phase |
| exists | missing | none | strategic only |
| exists | exists | `--strategic` | strategic only |
| exists | exists | `--tactical` | every phase |
| exists | exists | `--phase NN` | single phase |
| missing | any | any | abort |

---

## Process

> **Out-of-scope discoveries (CLAUDE.md §3.1):** audit surfaces a real problem outside this spec's contract and non-trivial (own research + fix) → not a finding against this spec - park via `/spec-draft` (dedup via `scripts/spec_catalog/search.ps1` first), one per distinct problem, and list parked `Sxxxx` ids alongside audit verdict. Findings within this spec's contract stay in `## Last Audit` as usual.

**1 - Parse arguments, locate spec.**

Resolve `Sxxxx` and slug via `select.ps1`. Check `PLAN/Sxxxx_<slug>.md` exists - abort if not. Check `PLAN/Sxxxx_<slug>/INDEX.md` - record presence. Apply auto-detection table.

**2 - Extract verification contract.**

Strategic: parse §2 Goals, §3.2 Constraints, §6 Research items, §8 FEATURES text, §11 Criteria.
Tactical: parse INDEX Phase Overview, Blockers, Completion Gate; each phase's Files Touched, Steps Verification, Done Criteria.

**3 - Run checks. Record outcome per check:**

`PASS` / `WARN` / `FAIL` / `MANUAL` / `UNCHECKABLE` / `EXEMPT`

Verification mechanics:

| Check | How |
| --- | --- |
| File exists | `Glob` with exact path |
| Class/function declared | `Grep` for `class <Name>` / `fun <name>` - verify hit is declaration, not comment/string |
| No forbidden call | `Grep` for pattern; PASS iff zero hits |
| String resource present | `scripts/utils/set-android-string.ps1 -Action get -Key <key>` (checks EN/RU/UK at once, exit 1 if missing anywhere) or `Grep` for `name="<key>"` in all three `values/strings.xml` files |
| Room version | Read `AppDatabase.kt`, match `@Database(version = N` |
| Dev log entry | `Grep` for file path in `dev/CHANGELOG.md` |
| Catalog up-to-date | `Grep` for class name in `dev/CATALOG/<module>.jsonl` |
| Dead-weight introduced - per CLAUDE.md Rule 20 (dead-weight hygiene), obey it as written | `Grep` for remnants the change should have removed: orphaned/superseded classes, `-keep` rules naming a deleted class, a dependency added to a flavor that never references it, assets/resources no longer referenced. WARN per remnant that would still ship in target variant. Cross-check `PLAN/` before treating a zero-ref artifact as dead - may be active-ticket scaffolding |
| FEATURES trilingual | Read strategic §8 first. §8 text is "Без изменений" (or equivalent "no change") → EXEMPT. Otherwise `Grep` for keyword in all three FEATURES docs - PASS only if all three hit |
| File size vs budget | `Read` file, count lines, compare to step budget |
| Flavor gating | `Grep` for `BuildConfig.<FLAG>` if §3.2 names a flag |
| Step status consistency | Parse `[x] done` in phase file; cross-check Verification predicates |
| Phase status consistency | INDEX row status == phase `Status:` header |
| Debug-tag invariant | Current journal status `BlockNeedUserTest`: `Grep` for `Timber.d("<Sxxxx>:` across `.kt` - PASS iff ≥1 hit, FAIL if none (spec lost its device-test probe). Any other status: PASS iff zero hits - surviving tags stale (WARN, list them; verdict flip in step 6 deletes them). |

**4 - Score.**

- `Verified` - every check is PASS, MANUAL, or EXEMPT. Zero WARN and FAIL.
- `Partial` - zero FAIL, ≥1 WARN. Collapses to `Broken` under `--strict`.
- `Broken` - ≥1 FAIL.

**5 - Write `## Last Audit` block** at bottom of `PLAN/Sxxxx_<slug>.md` (overwrite if present). Use compact template below - keep under ~40 lines, PASS counts + FAIL/WARN action items only. No verbose tables. Action items are input `/spec-fix` consumes.

**6 - Update `Status:` fields.**

Full/strategic mode: flip strategic spec `Status:` to score (`Verified` / `Partial` / `Broken`). Always touch journal status via `update.ps1`.

Whenever verdict flips journal/strategic status (full or strategic mode), enforce debug-tag invariant: spec no longer `BlockNeedUserTest`, so must carry zero `Timber.d("<Sxxxx>:` tags. `Grep` all `.kt` for `Timber.d("<Sxxxx>:` and delete every matching line (idempotent no-op if none). Run dev log line per `.kt` file that lost a tag. See CLAUDE.md "Debug Verification Tags". Only `.kt` mutation `/spec-check` performs.

Tactical-only mode: update INDEX `Status:` + audited phase rows + phase headers. Do not touch strategic `Status:`. Do not touch debug tags.

**7 - Run dev log + finalize (batched).**

Use `close-and-log.ps1` for Step 6 (status flip) + this step + Step 7a (functionality log) + post-change catalog scan in single pwsh invocation:

```powershell
pwsh -NoProfile -File scripts/spec_catalog/close-and-log.ps1 `
    -Id <Sxxxx> `
    -Status <Verified|Partial|Broken> `
    -DevLogs '[{"file":"PLAN/Sxxxx_<slug>.md","target":"spec-check","desc":"Audit <Sxxxx> -> <score>; PASS/WARN/FAIL N/N/N"}]' `
    # -DevLogs is ONE JSON-array string - append one {file,target,desc} object per modified
    # phase / INDEX / .kt file that lost a debug tag. Never a PowerShell array literal @('{..}','{..}'):
    # pwsh -File binds only its first element and close-and-log.ps1 rejects the leftovers (S1063).
    -FuncOp <ADD|CHANGE|""> -FuncDesc "<english summary or omit>" `
    -FeatArea "<inventory area, e.g. Video Player>" `
    -FeatName "<short capability name>" `
    -FeatFlavors "<exact builds that ship it, e.g. standard,legacy>" `
    -CatalogModule app_v2
```

Pass `-SkipFuncLog` (or omit `-FuncOp`/`-FuncDesc`) when spec purely internal or verdict `Partial`/`Broken`. Pass `-SkipCatalogSync` when no `.kt` file touched by debug-tag removal step.

`-FuncOp` requires `-FeatArea`/`-FeatName`/`-FeatFlavors` (S1072): the record asserts which area owns the capability and which builds ship it, and the script used to invent all three (`General` / an 80-char cut of `-FuncDesc` / `standard`) - a record that reads plausibly and is false. Derive `-FeatFlavors` from the actual gate (`BuildConfig` flag in `app_v2/build.gradle.kts`, or the source set), never from a sibling record.

Wrapped sequence still applies same rules:

- Feature inventory: record into `docs/ALL_FEATURES.jsonl` (via `scripts/all_features/add.ps1`) only on `Verified` flip, only when no record for this capability exists yet, only for user-visible changes (apply §2 heuristics). `docs/FEATURES*` is curated public showcase, populated only by `/skill-release` from inventory diff - never edited per-spec here.
- For tactical-only audits or `Partial`/`Broken` verdicts inventory block silently skipped.

Falling back to individual scripts (`close.ps1` + `add_to_dev_log.ps1` × N + `scripts/all_features/add.ps1` + `scan.ps1` + `render.ps1`) allowed when `close-and-log.ps1` unavailable, but each call is a separate pwsh process and lifecycle invariants must be reproduced manually.

**8 - Auto-chain to `/spec-fix`.**

Outcome `Partial` or `Broken` - immediately invoke `/spec-fix <Sxxxx>` to apply all mechanical fixes. If `Verified` - no further action.

**Chat output:** `<Sxxxx>: <score>. PASS/WARN/FAIL: N/N/N. Debug tags removed: N. Top issues: [list]. → Running /spec-fix…` (or `→ All checks passed. Debug tags removed: N.` on Verified)

---

## `## Last Audit` block - compact template

```markdown
## Last Audit

**Date:** <YYYY-MM-DD>
**Mode:** full | strategic | tactical | phase-<NN>
**Flags:** strict | quick | -
**Outcome:** Verified | Partial | Broken
**Counts:** PASS N · WARN N · FAIL N · MANUAL N · EXEMPT N

### Action items

1. **[FAIL §3.2.2 - Step 02.3]** <one line> - <concrete fix>.
2. **[FAIL §3.2.3 - Phase 02 Done]** <one line> - <concrete fix>.
3. **[WARN §2.3]** <one line> - <concrete fix>.

### Manual / on-device

- [ ] <signal from §11>
- [ ] <build / device check>

<If `Verified`: drop the "Action items" section, keep "Manual / on-device" only.>
```

Block replaces previous `## Last Audit` block in full. Rest of strategic spec (§1..§12) untouched.

---

## Constraints

- Never mutate spec content beyond `Status:`, `**Priority:**` (only when explicitly recomputed), and `## Last Audit` block.
- **Debug-tag removal exception:** when verdict flips journal/strategic status, delete every `Timber.d("<Sxxxx>:` line from `.kt` (CLAUDE.md "Debug Verification Tags"). Only `.kt` mutation this skill performs. Never delete tags in tactical-only / `--quick`-without-status-flip runs. Never delete a tag while journal status staying at `BlockNeedUserTest`.
- **Never write `PLAN/Sxxxx_<slug>__audit_*.md` or any other file in `PLAN/` to record audit findings.** All findings live inline in spec.
- Strategic audit qualitative (keyword overlap for goal coverage). Tactical audit strict (static predicates).
- Grep miss is FAIL. Hit-count mismatch (expected 1, found 3) is WARN with all hits listed.
- Never run `./gradlew` or build commands - static analysis only.
- Read-only zones ignored. Per CLAUDE.md Rule 4 (read-only zones) - obey it as written.
- `--quick` skips grep-heavy invariants (forbidden-call checks, trilingual greps) - annotates block.
- Never approve `Verified` if any tactical phase Broken.
- Grep hits on declaration lines only - not comments or string literals.

---

## Spec Catalog hooks

- **Argument resolution.** First positional arg is `Sxxxx` (preferred) or a slug. If slug, resolve via `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Name "<slug>" -Format json`.
- **Status transition** (after audit verdict final):
  - Verdict `Verified` → `pwsh -NoProfile -File scripts/spec_catalog/close.ps1 -Id <Sxxxx> -Status Verified`. (`close.ps1` also stamps `closed_at` on record.)
  - Verdict `Partial`  → `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status Partial`.
  - Verdict `Broken`   → `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <Sxxxx> -Status Broken`.
- **Debug tags.** Every verdict that flips journal status away from `BlockNeedUserTest` (Verified / Partial / Broken - and trivially also from `Implemented`, where there are none) requires grep-and-delete of `Timber.d("<Sxxxx>:` lines from `.kt` (Process step 6). Holds in `--quick` mode too.
- **Read-only mode (`--quick`):** still emits status transition and debug-tag removal above; difference is in scope of audit checks, not journal effect.
- **Forbidden:** per CLAUDE.md Rule 12 (spec catalog is script-owned) - obey it as written. Additionally, never demote a `Verified` ticket back to `Implemented` - only `/spec-fix` followed by `/spec-check` can change verdict. Never create audit files in `PLAN/`.
