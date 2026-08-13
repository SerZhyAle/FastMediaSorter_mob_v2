# Phase 01 - Scoped detekt runner

**Strategic spec:** [`../S1595_detekt-preflight-coverage-gap.md`](../S1595_detekt-preflight-coverage-gap.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 5 / 5
**Started:** 2026-08-12
**Completed:** 2026-08-12

---

## Objective

Produce a maintained script that runs the real detekt over a named file list, outside gradle,
with the project config and the correct per-module baseline, and reports three distinct outcomes.
No caller is rewired in this phase.

---

## Prerequisites

- [ ] Strategic §6 items 1, 2 and 4 are Resolved.
- [ ] `config/detekt/detekt.yml`, `config/detekt/baseline-app_v2.xml`, `config/detekt/baseline-wear.xml` present.
- [ ] A JDK is on PATH (`java -version` succeeds).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/detekt-scoped.ps1` | New | ≤ 260 |
| `scripts/quality/detekt-scoped.tests/run-tests.ps1` | New | ≤ 160 |

> No Kotlin, no resources, no flavor source sets involved - this phase touches repository tooling only.

---

## Steps

### Step 01.1 - Resolve the analyser classpath from the dependency cache

**Files:** `scripts/quality/detekt-scoped.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/quality/detekt-scoped.ps1`. Implement classpath resolution: glob the gradle
> module cache for the detekt CLI jars, the Kotlin compiler-embeddable, the ktlint jars and the
> detekt-formatting plugin jar. Resolve every coordinate by wildcard on the version segment, never
> by a version literal. Exclude `-sources.jar` and keep the formatting plugin out of the `-cp`
> list, because it is passed through `--plugins` instead. If the plugin jar is missing or fewer
> than eight jars resolve, return the "cannot verify" outcome and say which coordinate came up
> empty - never continue with a partial classpath.

**Why:**

Strategic §5.1 pillar 2 requires the runner to distinguish "could not check" from "checked and
found nothing", and `research/05` names the classpath as the fragile part precisely because it
resolves from a cache whose contents follow version pins that move. The archived precedent this
mechanism comes from pinned versions as literals, which is why it was never reusable.

**Verification:**

- `Glob` - `scripts/quality/detekt-scoped.ps1` exists.
- `Grep` - `-sources.jar` appears in an exclusion filter.
- `Grep` - no four-part version literal (`1.23.8`, `2.0.21`, `0.50.0`) appears outside a comment.

**Status:** `[x]` done

---

### Step 01.2 - Invoke detekt over the named file list with the per-module baseline

**Files:** `scripts/quality/detekt-scoped.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Accept `-ChangedFiles` as one comma-joined argument and split it, matching the convention every
> other consumer in this repository uses. Keep only `.kt` files. Group them by owning module
> (`app_v2` / `wear`) from the repo-relative path prefix, and invoke detekt once per module with
> that module's own `config/detekt/baseline-<module>.xml`. Pass `--config config/detekt/detekt.yml`
> and `--build-upon-default-config`, and pass the formatting jar through `--plugins`.

**Why:**

Strategic §3.2 makes per-module baseline selection a hard constraint: a `wear` file judged against
the `app_v2` baseline would report the whole of `wear`'s known debt as new findings. The
comma-joined argument convention exists because `pwsh -File` binds a `[string[]]` parameter to its
first element only, which is recorded on the existing preflight as S1184.

**Verification:**

- `Grep` - `--build-upon-default-config` present.
- `Grep` - `baseline-` is built from a module variable, not written twice as two literals.
- Run on one `app_v2` file and one `wear` file in one call; exit code is 0 and the output names two modules.

**Status:** `[x]` done

---

### Step 01.3 - Write the report outside the gradle report directory

**Files:** `scripts/quality/detekt-scoped.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Write the XML report under `temp/detekt-scoped/<module>.xml`. Never write to
> `<module>/build/reports/detekt/detekt.xml`. Parse the report to produce the finding list rather
> than scraping stdout.

**Why:**

`assert-detekt.ps1` narrows a project-wide failure against `<module>/build/reports/detekt/detekt.xml`
and judges that report's staleness by mtime (its S1189 logic). Writing there would make an
unrelated gate narrow a real failure against a report this script produced, and blame the wrong
ticket - the risk named in strategic §7.

**Verification:**

- `Grep` - `build/reports/detekt` does not appear as a write target in this file.
- `Glob` - after a run, `temp/detekt-scoped/app_v2.xml` exists.
- `Glob` - `app_v2/build/reports/detekt/detekt.xml` mtime is unchanged across a run of this script.

**Status:** `[x]` done

---

### Step 01.4 - Implement the three-outcome exit contract

**Files:** `scripts/quality/detekt-scoped.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Exit 0 when the analyser ran and found nothing in the named files. Exit 1 when it ran and found
> something, printing each finding as `<file>:<line>:<col> - <RuleId> - <message>`. Exit 2 when it
> could not run at all - classpath incomplete, `java` absent, config missing, a named file absent,
> or the analyser process failing without producing a report. Never map an inability to run onto
> either of the other two. Document the codes in the script header, per the repo's exit-contract
> rule, and write `Write-Error <msg> -ErrorAction Continue` before any non-1 exit so the code is
> actually reachable.

**Why:**

Strategic ADR-3 makes "could not verify" a first-class outcome: collapsing it into "clean" would
certify unchecked work, which is the failure this repository has already paid for, and collapsing
it into "found" would block closure whenever the tooling breaks. The `Write-Error` form is
required because under `$ErrorActionPreference = 'Stop'` a bare `Write-Error` throws and the
following `exit N` never runs, which is enforced by `scripts/quality/assert-exit-contract.ps1`.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate` - exit 0.
- Run against a file containing a fresh violation - exit 1, and the printed line carries a rule id.
- Run with `-ChangedFiles` naming a non-existent file - exit 2.

**Status:** `[x]` done

---

### Step 01.5 - Add contract tests

**Files:** `scripts/quality/detekt-scoped.tests/run-tests.ps1`
**Depends on:** Step 01.4

**Prompt for developer:**

> Add a test script covering the three outcomes: a fixture file with a known violation returns 1
> and names the rule; a clean fixture returns 0; a deliberately broken classpath root returns 2.
> Keep fixtures inside the tests directory. Follow the shape of the existing
> `scripts/quality/assert-exit-contract.tests/` and `assert-detekt.tests/` suites.

**Why:**

Strategic §7 lists silent degradation - the runner stops checking and reports clean - as the
highest-probability risk, and a test that pins the exit-2 path is the only thing that catches it
after a future version bump moves the cache layout.

**Verification:**

- `Glob` - `scripts/quality/detekt-scoped.tests/run-tests.ps1` exists.
- Run it - exit 0, and its output names three covered outcomes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] No app build required - this phase touches no Kotlin, no resources and no build files.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate` exits 0.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

**Deviation from plan, recorded rather than hidden.** Step 01.1 planned a single classpath
resolution. The real cache holds two Kotlin versions - the one detekt is compiled against and the
one this project builds with - and picking either by rule fails: the wrong one crashes inside
detekt's own rule-set wiring, not at class load, so no static inspection can tell them apart. The
runner therefore probes the candidates and caches the combination that ran, keyed on the detekt
pin. This satisfies strategic §5.3 ("the way the analyser environment is assembled must be
replaceable, because versions move") more directly than the planned form did.

**Second deviation.** `scripts/quality/lib/detekt-report.ps1` gained
`Get-DetektFindingsFromReports`, and `Get-DetektFindings` now delegates to it. The parser could
only read the gradle task's own output path, and duplicating it would have duplicated its
fail-closed contract and its StrictMode handling of a clean report - both bought by past defects.
Existing suite re-run after the change: 14 passed.

---

## Handoff Notes to Next Phase

A callable runner with a stable three-outcome contract exists and is not wired to anything.
Phase 02 may assume: comma-joined `-ChangedFiles`, per-module baseline selection, report under
`temp/detekt-scoped/`, exit 0/1/2 as above, and no `BUILD.LOCK` acquisition anywhere in it.

---

## Rollback Plan

Delete the two new files. Nothing else references them at the end of this phase, so removal
restores the previous behaviour exactly.
