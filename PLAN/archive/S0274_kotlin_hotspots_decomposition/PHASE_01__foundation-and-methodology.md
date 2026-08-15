# Phase 01 - Foundation and Methodology

**Strategic spec:** [`../S0274_kotlin_hotspots_decomposition.md`](../S0274_kotlin_hotspots_decomposition.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Capture the baseline LOC snapshot of all 16 backlog files and the current Kotlin-daemon JVM-heap reminder text in `gradle.properties`. Establish a one-place reference table that subsequent waves use to measure their before/after delta. No production-code change.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch (`DEBUG-v00N`).
- [ ] `dev/CATALOG/app_v2.jsonl` is fresh - run `scripts/catalog_sync.ps1 -Module app_v2` if last touched > 24 h ago.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0274_kotlin_hotspots_decomposition/INDEX.md` | Modified (snapshot table refresh only) | n/a |
| `temp/S0274_baseline_2026-05-20.md` | New (volatile snapshot artifact) | ≤ 100 |

> No production code is touched in this phase. The `temp/` artifact is gitignored - its purpose is to give later waves a stable reference to compare against.

---

## Steps

### Step 01.1 - Refresh catalog snapshot

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Regenerate the catalogue so backlog LOC numbers in INDEX reflect the current branch. Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (this chains `scan.ps1` + `render.ps1` in one process). No file edits beyond what the script writes.

**Verification:**

- `Glob` - `dev/CATALOG/app_v2.jsonl` exists.
- `Bash` - `git log -1 --format=%ct -- dev/CATALOG/app_v2.jsonl` is **not required** (file is gitignored); use `Get-Item dev/CATALOG/app_v2.jsonl | Select-Object LastWriteTime` and confirm it sits within the current minute.
- expected: catalog regeneration writes both `app_v2.jsonl` and `app_v2.md` | actual: PASS if both LastWriteTime values are within 60 s of the script execution.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 17:38 - Verification 1/1 PASS. catalog_sync.ps1 wrote app_v2.jsonl (17:38:28) + app_v2.md (17:38:29). No dev-log (catalogue is gitignored).

---

### Step 01.2 - Write baseline LOC snapshot

**Files:** `temp/S0274_baseline_2026-05-20.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `temp/S0274_baseline_2026-05-20.md` (UTF-8, English). For each of the 16 backlog files in INDEX `Wave Backlog`, record: file path, current LOC from the freshly regenerated catalogue, current top-level public-class count, today's date. Use this exact markdown table format:
>
> ```
> # S0274 Baseline Snapshot - 2026-05-20
>
> | Wave | File | LOC | Public classes |
> |-----:|------|----:|---------------:|
> | 01 | ui/player/VideoPlayerManager.kt | <n> | <n> |
> | .. | ..                              | .. | .. |
> ```
>
> Source LOC from the freshly regenerated `dev/CATALOG/app_v2.jsonl`. Source public-class count from a `Grep` on the source file for `^class ` and `^object ` (top-level only - do not include nested classes).

**Verification:**

- `Glob` - `temp/S0274_baseline_2026-05-20.md` exists.
- `Grep` - `^| 01 |` matches exactly once in the file.
- `Grep` - `^| 16 |` matches exactly once in the file.
- `Grep` - `# S0274 Baseline Snapshot - 2026-05-20` matches exactly once.
- expected: 16 wave rows | actual: count matches.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 17:39 - Verification 4/4 PASS. temp/S0274_baseline_2026-05-20.md written with 16-row LOC + public-class table.

---

### Step 01.3 - Record current gradle.properties reminder verbatim

**Files:** `temp/S0274_baseline_2026-05-20.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Append a `## Kotlin daemon reminder (gradle.properties)` section to `temp/S0274_baseline_2026-05-20.md`. Copy the exact block of lines that today references `VideoPlayerManager.kt` and `kotlin.daemon.jvm.options` from `gradle.properties` (currently lines 43-46) into a fenced ` ```properties ` code block. This is the "before" snapshot that strategic §6 #3 will be compared against after Wave 01 verifies on device. Do not modify `gradle.properties` itself in this phase - measurement is data-driven inside Wave 01.

**Verification:**

- `Grep` - `## Kotlin daemon reminder (gradle.properties)` matches once in the file.
- `Grep` - `kotlin.daemon.jvm.options` matches at least once inside the fenced block.
- `Grep` - the `VideoPlayerManager.kt is 1700+ lines` reminder line (or its current variant) is present verbatim.
- expected: complete verbatim copy of the reminder block | actual: the three matching greps all hit.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 17:39 - Verification 3/3 PASS. Reminder block (gradle.properties lines 43-46) appended verbatim to temp/S0274_baseline_2026-05-20.md.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `temp/S0274_baseline_2026-05-20.md` exists and contains the 16-row snapshot plus the reminder block.
- [ ] `dev/CATALOG/app_v2.jsonl` and `.md` are fresh (regenerated in step 01.1).
- [ ] `dev/CHANGELOG.md` has one entry for `PLAN/S0274_kotlin_hotspots_decomposition/INDEX.md` (snapshot refresh) and one entry for `temp/S0274_baseline_2026-05-20.md` (baseline creation) via `.\scripts\add_to_dev_log.ps1`. The `temp/` entry is informational - the file is gitignored.
- [ ] No production-code change committed in this phase.

---

## Handoff Notes to Next Phase

- Wave 01 (Phase 02) compares its before/after LOC against the `01` row in `temp/S0274_baseline_2026-05-20.md`.
- The reminder block is consulted at the very end of Wave 01: if the kotlin-daemon heap budget can be reduced, `gradle.properties` lines 43-46 are rewritten and the reminder is dropped.

---

## Rollback Plan

This phase touches only `temp/` (gitignored) and regenerates the local catalog index. There is nothing to roll back. If something looks off in the snapshot, rerun step 01.1 and step 01.2.
