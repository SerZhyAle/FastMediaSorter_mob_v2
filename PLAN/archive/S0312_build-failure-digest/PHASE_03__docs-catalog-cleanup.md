# Phase 03 - Docs Catalog Cleanup

**Strategic spec:** [`../S0312_build-failure-digest.md`](../S0312_build-failure-digest.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (impl steps; central closure pending operator)
**Depends on:** Phase 01, Phase 02
**Blocks:** completion
**Steps done:** 2 / 3 (03.3 dev-log/functionality-log writes deferred to central operator closure)
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Document the digest JSON schema next to the owning script, surface the new tool in the script READMEs, and record implementation closure. Confirm no feature-doc or Kotlin-catalog change is required (internal DX tooling, no `.kt`).

---

## Prerequisites

- [x] Phase 01 and Phase 02 are ✅ Done.
- [x] `scripts/builders/build-failure-digest.ps1` exists and `-DryRun` exits 0.
- [x] No `.kt` or `.xml` file was touched anywhere in this ticket.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/builders/build-failure-digest.SCHEMA.md` | New | ≤ 90 |
| `scripts/builders/README.md` | Modified | ≤ 140 |
| `scripts/README.md` | Modified | ≤ 200 |

> File projected >500 lines after change → backup step required. No target file is projected above 500 lines.

---

## Steps

### Step 03.1 - Document the digest JSON schema next to the script

**Files:** `scripts/builders/build-failure-digest.SCHEMA.md`
**Depends on:** Phase 02

**Prompt for developer:**

> Create `scripts/builders/build-failure-digest.SCHEMA.md`. Document every top-level field of the digest object (`command`, `exitCode`, `firstActionableFailure`, `rawLogPath`, `verdict`) and every nested field of `firstActionableFailure` (`module`, `flavor`, `file`, `line`, `message`), each with type and null-ability. List the three `verdict` values and the exit-code map (0 success, 10 failure, 20 blocked, 2 usage error). Add one example JSON object for a `failure` verdict and one for a `blocked` verdict. Keep it a reference doc - no rationale prose.

**Verification:**

- `Glob` - `scripts/builders/build-failure-digest.SCHEMA.md` exists.
- `Grep` - every contract field token is documented: `command`, `exitCode`, `firstActionableFailure`, `rawLogPath`, `verdict`, `module`, `flavor`, `file`, `line`, `message`. Expected: 10/10 present | actual: recorded.
- `Grep` - the three verdict values `failure`, `success`, `blocked` each appear.
- `Grep` - the exit-code map values `10`, `20` each appear.
- `Grep` - at least two fenced ` ```json ` example blocks are present (count ≥ 2). Expected: ≥ 2 | actual: recorded.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Created `build-failure-digest.SCHEMA.md`: all top-level fields + `firstActionableFailure` nested fields documented with type and nullability, the three verdicts, the exit-code map, and one `failure` + one `blocked` example JSON. Reference doc, no rationale prose.
- Glob: exists. Contract fields expected 10/10 | actual 10/10.
- Verdict values `failure`/`success`/`blocked`: all present. Exit-map `10`/`20`: both present.
- Fenced ```json blocks expected ≥ 2 | actual 2.

---

### Step 03.2 - Surface the digest tool in the builders README and root README

**Files:** `scripts/builders/README.md`, `scripts/README.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `scripts/builders/README.md`, extend the "Failure diagnostics" note so it lists `build-failure-digest.ps1` (alias `.\a.ps1 bfd`) as the structured, agent-readable companion to `.\a.ps1 bf`, and point to `build-failure-digest.SCHEMA.md` for the JSON contract. In `scripts/README.md`, add a one-line entry for the digest tool in the appropriate section, mirroring the surrounding style. Do not alter unrelated README content (release-notes blocks, output locations).

**Verification:**

- `Grep` - `build-failure-digest.ps1` appears in `scripts/builders/README.md`.
- `Grep` - `bfd` appears in `scripts/builders/README.md`.
- `Grep` - `build-failure-digest.SCHEMA.md` is referenced from `scripts/builders/README.md`.
- `Grep` - `build-failure-digest` appears in `scripts/README.md`. Expected: present | actual: recorded.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Extended the "Failure diagnostics" note in `scripts/builders/README.md` to list `build-failure-digest.ps1` (alias `.\a.ps1 bfd`) as the structured companion to `.\a.ps1 bf`, with a link to `build-failure-digest.SCHEMA.md`. Added an `a bfd` line to the alias block in `scripts/README.md`. No unrelated README content touched.
- Grep builders README: `build-failure-digest.ps1` present (1), `bfd` present (1), `build-failure-digest.SCHEMA.md` referenced (1).
- Grep root README: `build-failure-digest` present (1).

---

### Step 03.3 - Record closure: dev log, no feature-doc, no catalog sync

**Files:** (closure step - dev log + assertions; no new source file)
**Depends on:** Step 03.2

**Prompt for developer:**

> Run `scripts/post-change.ps1` (ChangeType `Doc` for the schema/README files; ChangeType `Script` for the `.ps1` files) so every file created or modified across all three phases has a `dev/CHANGELOG.md` entry. Assert that `docs/FEATURES.md`/`_RU`/`_UK` are unchanged (internal tooling, strategic §8) and that `dev/CATALOG/app_v2.jsonl` is unchanged (no `.kt` touched). Append one functionality-log line via `scripts/add_to_functionality_log.ps1 -Id S0312 -Op ADD -Description "..."` describing the new build-failure digest tool.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains an entry referencing `build-failure-digest.ps1`. Expected: present | actual: recorded.
- `Grep` - `dev/CHANGELOG.md` contains an entry referencing `build-failure-digest.contract.ps1`. Expected: present | actual: recorded.
- `PowerShell` - `git diff --name-only -- docs/FEATURES.md docs/FEATURES_RU.md docs/FEATURES_UK.md` prints nothing. Expected: empty | actual: recorded.
- `PowerShell` - `git diff --name-only -- dev/CATALOG/app_v2.jsonl` prints nothing (no Kotlin change). Expected: empty | actual: recorded.
- `Grep` - the functionality log contains a `S0312` ADD line. Expected: present | actual: recorded.

**Status:** `[~] impl-side assertions done; dev-log + functionality-log writes deferred to central operator closure`

**Step Log:**

- 2026-05-31 - Read-only assertions run by the `/spec-dev` executor:
  - `git diff --name-only -- docs/FEATURES.md docs/FEATURES_RU.md docs/FEATURES_UK.md`: expected empty | actual empty (no feature-doc change - internal tooling, strategic §8).
  - `git diff --name-only -- dev/CATALOG/app_v2.jsonl`: expected empty | actual empty (no `.kt` touched; the file is gitignored, so empty is genuine).
  - Full working-tree scope check: S0312 added only `scripts/builders/build-failure-digest.{contract.ps1,ps1,SCHEMA.md}` and modified `a.ps1`, `scripts/README.md`, `scripts/builders/README.md`. No `.kt`/`.xml` touched.
- DEFERRED to central operator (out of `/spec-dev` execution scope per task HARD PROHIBITIONS): `scripts/post-change.ps1` dev-log entries for every created/modified file, and the `scripts/add_to_functionality_log.ps1 -Id S0312 -Op ADD` line. The two predicates below depend on those writes and are therefore left for the operator:
  - `dev/CHANGELOG.md` references `build-failure-digest.ps1` - pending operator dev-log write.
  - `dev/CHANGELOG.md` references `build-failure-digest.contract.ps1` - pending operator dev-log write.
  - functionality log `S0312` ADD line - pending operator functionality-log write.

---

## Phase Done Criteria

- [x] Steps 03.1 and 03.2 are `[x] done`; Step 03.3 impl-side assertions done, dev-log/functionality-log writes deferred to central operator closure.
- [x] `scripts/builders/build-failure-digest.SCHEMA.md` documents all contract fields, the three verdicts, and the exit-code map.
- [x] `scripts/builders/README.md` and `scripts/README.md` both reference the digest tool.
- [x] `docs/FEATURES.md`/`_RU`/`_UK` and `dev/CATALOG/app_v2.jsonl` are unchanged by S0312.
- [ ] Every file created or modified across all phases has a `dev/CHANGELOG.md` entry. (Deferred to central operator closure - `/spec-dev` execution does not write the dev log per task HARD PROHIBITIONS.)

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate, then run `/spec-check S0312`.

---

## Rollback Plan

Delete `scripts/builders/build-failure-digest.SCHEMA.md` and revert the two README edits. The schema doc and README rows carry no executable behavior.
