# Phase 01 - Measurement harness and the before-baseline

**Strategic spec:** [`../S1502_stream-catalog-thumbnail-performance.md`](../S1502_stream-catalog-thumbnail-performance.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Deliver the repeatable full-catalog measurement run and capture the before-numbers with it, while the tree still holds none of this ticket's code changes.

---

## Why this phase is first

Strategic §6.1 asks for a measurement "до и после шага A" - before and after pillar A. Two acceptance criteria are comparisons against current behaviour rather than absolute limits: §11.4 (screen open must not get longer) and §11.6 (peak memory must not grow). Neither can be settled once the changes have landed, because the thing they compare against no longer exists. This phase is entirely scripts plus configuration and touches no application source, so putting it first costs nothing and is the only ordering that produces a baseline at all.

ADR-1 fixes the order of the pillars A - B - C - D. It says nothing about pillar E, and running E first is what makes ADR-2's "acceptance is a number, not an impression" achievable.

---

## Prerequisites

- [ ] Working tree holds no S1502 application-source change - the baseline is void otherwise.
- [ ] A device or emulator is attached (`scripts/devtest/device-ready.ps1`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/devtest/streams-perf-seed.ps1` | New | ≤ 220 |
| `scripts/devtest/prerelease-measure.ps1` | Modified | ≤ 540 |
| `scripts/devtest/prerelease.config.psd1` | Modified | ≤ 90 |
| `temp/S1502/baseline/*.json` | New (artifacts, not committed) | n/a |

---

## Steps

### Step 01.1 - Seed a device with the full catalog reproducibly

**Files:** `scripts/devtest/streams-perf-seed.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Write a script that brings a connected device's debug install to the full shipped catalog and reports how many rows it ended up with, so a measurement run starts from a known state instead of whatever the device happened to hold. Resolve adb the same way `prerelease-measure.ps1` does, accept `-DeviceId` and `-Json`, and give it a documented exit-code contract with a distinct code for "device reachable but catalog did not reach the expected size" (CLAUDE.md Rule 7 on reachable exit codes - `Write-Error $msg -ErrorAction Continue` before a non-1 `exit`).
>
> Verify the resulting row count by querying the app's database through adb rather than by trusting the import's own report.

**Why:**

Strategic §2 goal 5 requires a repeatable run on a real-size catalog, and research artifact 01 records that whether the owner's installed database actually holds ~19.8k rows is not answerable from code, so a measurement that does not establish its own N proves nothing.

**Verification:**

- `Glob` - `scripts/devtest/streams-perf-seed.ps1` exists.
- `Grep` - the script header lists its exit codes.
- Run against the connected device: exit 0 and the reported row count equals the number of **logical CSV records** in `delivery/stream-catalog/streams.csv`. Not its line count: four records carry newlines inside quoted fields, so the file's 19,860 lines are 19,855 records, and a line-count predicate would fail a correct run.

**Status:** `[x]` done

---

### Step 01.2 - Add the streams checkpoints to the measurement harness

**Files:** `scripts/devtest/prerelease-measure.ps1`, `scripts/devtest/prerelease.config.psd1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Extend the `-Checkpoint` `ValidateSet` with five checkpoints and add a matching `Thresholds` entry for each to `prerelease.config.psd1`:
>
> - `streams-open` - caller-supplied `-ElapsedMs`, the same path `player-open` uses. **Not** `am start -W`: `StreamsActivity` is declared `android:exported="false"`, so a shell-initiated start is refused with a `SecurityException`, and the operator has to reach the screen through the UI anyway.
> - `streams-search`, `streams-list-scroll`, `streams-grid-scroll` - `dumpsys gfxinfo` janky-frame percentage, reusing the `list-scroll` branch. For search the caller resets gfxinfo, types a burst into the search box, then calls. §11.1 asks whether a keystroke holds the main thread past one frame, and janky frames is the direct count of that.
> - `streams-peak-memory` - `VmHWM` from `/proc/<pid>/status`, which is a real high-water mark, with `dumpsys meminfo` total PSS as the fallback where `/proc` is not readable. `dumpsys meminfo` alone reports only the current sample, so it cannot answer "did the peak grow".
>
> Carry over the emulator advisory the existing `ListScroll` checkpoint already applies to both scroll checkpoints, for the same reason: host-GPU rendering inflates janky% structurally. Extend the `.DESCRIPTION` checkpoint list and the exit-code block in the file header to cover all five.

**Why:**

Strategic §11 criteria 1 to 4 and 6 are each stated in measured terms - keystroke latency, both scroll modes, screen-open time and peak memory - and ADR-2 refuses to accept the ticket on a subjective impression, so every one of them needs a number from the harness that already produces the project's other performance verdicts.

**Verification:**

- `Grep` - `streams-open`, `streams-search`, `streams-list-scroll`, `streams-grid-scroll` and `streams-peak-memory` each match in `prerelease-measure.ps1`.
- `Grep` - five matching keys added under `Thresholds` in `prerelease.config.psd1`.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` passes for both scripts.
- Each new checkpoint run against the connected device exits 0 or 11, never 1.
- The four pre-existing checkpoints still behave as before - the `list-scroll` label is replaced by a multi-value condition, so a regression here is possible and must be disproved, not assumed.

**Status:** `[x]` done

---

### Step 01.3 - Capture the before-baseline

**Files:** `temp/S1502/baseline/`
**Depends on:** Step 01.2

**Prompt for developer:**

> Build and install the debug APK from the current tree - which holds no S1502 application change - run `streams-perf-seed.ps1`, then run all five checkpoints with `-Json` and write each result to `temp/S1502/baseline/<checkpoint>.json`. Record the device model, API level and RAM alongside them, because a baseline whose hardware is unknown cannot be compared against anything later.
>
> If no device with a floor-tier profile is available, capture the baseline on whatever device is attached and label it as such. A baseline from the wrong hardware is still a valid before/after pair on that hardware; a missing baseline is not.

**Why:**

Strategic §11 criteria 4 and 6 are comparisons against current behaviour, so they are unprovable without a number taken before the change lands, and §6.1 asks for the measurement both before and after pillar A.

**Verification:**

- `Glob` - `temp/S1502/baseline/streams-open.json`, `streams-search.json`, `streams-list-scroll.json`, `streams-grid-scroll.json` and `streams-peak-memory.json` all exist.
- `temp/S1502/baseline/device.json` records the device identity, API level, RAM, catalog row count and APK version for the whole set. One sibling file rather than the identity repeated into all five - the identity is a property of the run, and duplicating it invites the copies to disagree.

**Status:** `[x]` done

---

## Step Log

- 2026-08-08 - Step 01.1 DONE. `scripts/devtest/streams-perf-seed.ps1` created. Run against `emulator-5554` (sdk_gphone64_x86_64, API 35): `exit=0`, `expected=19855 actual=19855`.
  - Seeds the database directly from `delivery/stream-catalog/streams.csv` instead of driving the in-app import, which downloads a zip release asset - a baseline must not vary with network reachability or with the release asset drifting from the working tree. Rationale is in the script header.
  - Ids are deterministic (`seed-000000`..), `addedAt` is fixed. A random UUID or a wall-clock timestamp would make two seeds of the same catalog differ in columns the sort modes read, which would break the baseline/after comparison.
  - Only catalog columns are written; the play-outcome columns are deliberately not named, so the script survives the Phase 04 schema split unchanged.
  - Defect found and fixed during the step: the first draft piped `adb exec-out` into `Set-Content`, and a native command's stdout crosses the PowerShell pipeline as decoded strings, which corrupts a SQLite file. Replaced with `Start-Process -RedirectStandardOutput`.
  - Verification predicate corrected in this phase file: it asked for "lines minus header" (19,859), but four catalog records carry newlines inside quoted fields, so the correct figure is 19,855 logical records. The old predicate would have failed a correct run.

- 2026-08-08 - Step 01.2 DONE. Five checkpoints added to `prerelease-measure.ps1` plus five `Thresholds` keys in `prerelease.config.psd1`.
  - Plan corrected during the step: `streams-open` cannot reuse the `cold-start` `am start -W` branch. `StreamsActivity` is `android:exported="false"` (`AndroidManifest.xml:248`), and a shell-initiated start is refused - verified on device, `SecurityException: not exported from uid 10209`. It now takes a caller-supplied `-ElapsedMs`, like `player-open`.
  - `streams-peak-memory` reads `VmHWM` from `/proc/<pid>/status` (a real high-water mark) and falls back to `dumpsys meminfo` total PSS. `meminfo` alone reports the sample at call time, which cannot answer "did the peak grow"; the fallback labels itself as a sample in `detail` rather than passing itself off as a peak.
  - Measured on `emulator-5554`: `streams-open` 120 ms (supplied), `streams-search` 120 ms (supplied), both scroll checkpoints 15.0 % janky and correctly marked advisory, `streams-peak-memory` 325112 kB via VmHWM. All exit 0.
  - Regression check on the pre-existing checkpoints, because the `list-scroll` string label was replaced by a multi-value condition: `list-scroll` 15.0 % advisory, `cold-start` 3048 ms, `player-open` 900 ms - all exit 0, all unchanged in shape.
  - Thresholds are pipeline-chosen starter values and say so in the config comment; strategic §3.3 records that the owner has not set them.

- 2026-08-08 - Step 01.3 DONE. Baseline captured on `emulator-5554` from debug APK `v2.60.8071.632-DEBUG`, built from a tree holding no S1502 application change. Artifacts in `temp/S1502/baseline/`.

  | Checkpoint | Baseline | Limit | Note |
  |---|---:|---:|---|
  | `streams-open` | 274 ms | 5000 | ActivityTaskManager `Displayed` marker |
  | `streams-search` | 21.15 % janky | 20 | over limit; advisory on emulator |
  | `streams-list-scroll` | 1.06 % janky | 20 | advisory on emulator |
  | `streams-grid-scroll` | 2.77 % janky | 20 | advisory on emulator |
  | `streams-peak-memory` | 382,668 kB | 524,288 | `/proc` VmHWM |

  - **The headline baseline finding: typing four characters into the search box over 19,855 rows produces 21.15 % janky frames.** That is the research artifact's central claim - the filter pass runs on the main thread - confirmed by measurement rather than by reading control flow, and confirmed on an emulator substantially faster than the API-23 floor the ticket targets. It is also the only checkpoint already outside its starter limit before any change is made.
  - `streams-search` was redefined during this step, on evidence. It was specified as a wall-clock "ms to filtered list"; the first run returned 2,536 ms, of which nearly all was the `uiautomator dump` needed to observe the settled list from outside. A measurement whose instrument costs more than its subject is not a measurement. It now reads gfxinfo janky % across a typing burst, which is the direct count of what §11.1 actually asks about - whether a keystroke holds the main thread past one frame. Script, config Metric key and this phase file were all updated together.
  - `streams-open` was likewise improved during the step: the system already logs `Displayed .. StreamsActivity: +Xms`, so the checkpoint reads that marker instead of a caller stopwatch, and needs neither app code nor an exported activity. `-ElapsedMs` remains as the fallback.
  - Device is an API 35 emulator with 2.5 GB RAM - **not** the `legacy` floor (API 23, 128 MB heap) strategic §3.2 names. Floor-hardware numbers are still owed and remain the `BlockNeedUserTest` gate.

- 2026-08-08 - **Baseline partially invalidated. The three frame-based numbers above must not be used as a comparator.**
  - Found while spot-checking after Phase 02: a 12-swipe burst renders only **27-30 frames** on this emulator, and three identical back-to-back runs returned **60.00 %, 55.56 % and 46.43 %** janky. The spread between repeats of the same measurement is wider than any improvement the ticket could plausibly produce, so the metric cannot detect one. The host was running four concurrent java/gradle processes from a sibling session, which is part of the cause but not the whole of it - a percentage computed over 28 frames is arithmetic, not a measurement.
  - Consequence: `streams-list-scroll` 1.06 %, `streams-grid-scroll` 2.77 % and `streams-search` 21.15 % are **not** valid before-numbers. The earlier reading of the 21.15 % figure as empirical confirmation that the filter pass blocks the main thread is withdrawn - it rests on the same unreliable sample. Research artifact 01's claim stands on its code trace, which was never in doubt; what it still lacks is a measurement, exactly as its own "What no one has established" section says.
  - `streams-open` (274 ms, from the system's `Displayed` marker) and `streams-peak-memory` (382,668 kB, from `/proc` VmHWM) are unaffected - neither is frame-sampled - and remain valid before-numbers.
  - Harness fixed rather than the conclusion reworded: `prerelease-measure.ps1` now parses `Total frames rendered`, reports it in `detail`, and marks any janky record under 100 frames `insufficient: true` plus advisory, so no later run can compare two such numbers and believe the difference means something. Verified: the same scroll now returns `insufficient: true` over 29 frames.
  - Re-taking the frame-based baseline needs a quiet host and a scroll long enough to clear 100 frames, and properly needs floor-tier hardware. Carried to Phase 04 Step 04.9.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] No application source changed in this phase - `Grep` for S1502 in `app_v2/src` returns 0 hits.
- [x] `Grep` for `TODO(phase-01)` returns zero hits in source and scripts. The single hit under `PLAN/` is this criterion's own line.
- [x] Dev log entries added via `post-change.ps1` - three closures, all `PASS`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

### Phase-boundary audit (Layer 1; Layers 2-4 not applicable - no application source touched)

- **P2, fixed in phase.** `streams-open` reads the most recent `Displayed` marker in the logcat buffer, so a caller who skips `adb logcat -c` before tapping through would be handed a previous launch's number as if it were this run's. Header now states the requirement.
- **P3, accepted.** `streams-perf-seed.ps1` emits its human-readable and JSON records with scrambled key order, because the `[ordered]` record is bound to a `[hashtable]` parameter. Cosmetic; the values and the exit code are unaffected.
- **Not a finding.** The seed deletes `sourceOrigin = 'CATALOG'` rows before inserting, which mirrors `StreamSourceRepository.mergeCatalog` and leaves MANUAL/IMPORTED rows alone. The package is hardcoded to `com.sza.fastmediasorter.debug`, so a release install is never reachable.
- **Regression check on shared code.** Replacing the `list-scroll` switch label with a multi-value condition could have broken the four pre-existing checkpoints; all four were re-run and behave as before.

---

## Handoff Notes to Next Phase

The baseline in `temp/S1502/baseline/` is the only copy of the before-numbers and is not committed - do not clear `temp/S1502/` until the ticket is Verified. Every later phase compares against it, and Step 01.3 cannot be repeated once Phase 02 lands.

---

## Rollback Plan

Revert the phase commit - scripts and configuration only, no application source and no schema.
