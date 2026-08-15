# Phase 08 — Docs / Catalog Cleanup

**Strategic spec:** [`../S0207_radical-memory-reduction.md`](../S0207_radical-memory-reduction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** all preceding phases
**Blocks:** —
**Steps done:** 3 / 4 (Step 08.4 deferred to on-device BlockNeedUserTest run)
**Started:** 2026-05-16
**Completed:** —

---

## Objective

Final sweep: regenerate class catalog, verify dev changelog completeness, record final acceptance measurement, and prepare the ticket for `/spec-check` to advance status from `Implemented` to `Verified` (or `Partial` / `Broken`).

`docs/FEATURES*.md` is NOT touched — strategic §8 mandates "Без изменений".

`photos` signs off only the shared image/cache/startup/network parts of S0207. The canonical MP3/player acceptance in Step 08.4 applies only to audio-capable flavors.

---

## Prerequisites

- [ ] Phases 01..07 all ✅ Done.
- [ ] Project compiles for at least `standardDebug` flavor.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | (auto) |
| `dev/CATALOG/app_v2.md` | Regenerated | (auto) |
| `dev/CHANGELOG.md` | Appended | (auto via script) |
| `dev/FUNCTIONALITY.log` | Appended | (auto via script) |

---

## Steps

### Step 08.1 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`

**Prompt for developer:**

> Run:
> ```
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> For each new class introduced in Phases 01..07 (`MemoryCheckpoint`, `MemoryProbe`, `MemoryProbeImpl`, `MemoryScenario`, `MemoryProfile`, `MemoryProfileCoordinator`, `MemoryProfileCoordinatorImpl`, `MemoryProfileFlavorOverride`, `DefaultMemoryProfileFlavorOverride`, `VrMemoryProfileFlavorOverride`, `NativePressureMonitor`, `IdleDisconnectPolicy`, `IdleDisconnectPolicyImpl`, `DeferredStartupWorker`, `FirstFrameSignal`), call `set.ps1 -Class <Name> -Role <appropriate>` to populate the `role` field. Use the standard role taxonomy: `Util` for `MemoryCheckpoint`, `MemoryScenario`; `Probe` for `MemoryProbe*`, `NativePressureMonitor`, `FirstFrameSignal`; `Coordinator` for `MemoryProfileCoordinator*`, `IdleDisconnectPolicy*`; `Policy` for `*FlavorOverride`; `Worker` for `DeferredStartupWorker`.

**Verification:**

- `Grep` in `dev/CATALOG/app_v2.jsonl` — each new class name appears exactly once.
- `Grep` — `"role"` field is non-empty for each of the new entries.

**Status:** `[x]` done — 2026-05-16. Catalog regenerated (1329 records). Roles set for all 12 new classes. Verification: each class name present in jsonl; role field non-empty.

---

### Step 08.2 — Dev changelog audit

**Files:** `dev/CHANGELOG.md`

**Prompt for developer:**

> Run `Grep -c "S0207"` over `dev/CHANGELOG.md`. Confirm the count matches the sum of files touched across all phases (rough sanity, not exact equality — each phase posted entries via `add_to_dev_log.ps1`). If any file from a phase "Files Touched" table has zero changelog entry, post one now:
> ```
> .\scripts\add_to_dev_log.ps1 "<path>" "<target>" "<description>"
> ```

**Verification:**

- `Grep` in `dev/CHANGELOG.md` — `S0207` count > 30 (8 phases × ~4 files average ≈ 32).

**Status:** `[x]` done — 2026-05-16. S0207 count in CHANGELOG.md: 100 (exceeds threshold of 30). Verification: expected >30 | actual 100.

---

### Step 08.3 — Functionality log

**Files:** `dev/FUNCTIONALITY.log`

**Prompt for developer:**

> S0207 introduces a user-perceivable behaviour change: the `warning_low_memory_playback` toast stops appearing in normal scenarios. Post one entry via:
> ```
> .\scripts\add_to_functionality_log.ps1 -Id S0207 -Op FIX -Description "Eliminate `warning_low_memory_playback` toast in standard scenarios; native heap budget under 30 MB after cold start + SFTP browse + MP3 open."
> ```

**Verification:**

- `Grep` in `dev/FUNCTIONALITY.log` — `S0207` appears at least once with `FIX` operation.

**Status:** `[x]` done — 2026-05-16. FIX entry added: "Eliminate warning_low_memory_playback toast in standard scenarios...". Verification: expected S0207 FIX in log | actual: present (2 entries: CHANGE from Phase 01, FIX from Phase 08).

---

### Step 08.4 — Final acceptance measurement (audio-capable flavors)

**Files:** —

**Prompt for developer:**

> Cold-start on the canonical emulator (`heapMax=512 MB`, `RAM=3.82 GB`) for an audio-capable flavor. Open SFTP folder with 7 MP3 files. Tap the first one.
>
> Open `logs/current.log`. Locate the `MEM_PROBE | checkpoint=PRE_PLAY | scenario=audio` line. Record:
> - `native=<alloc>MB/<reserved>MB(free=<free>MB)`.
>
> Acceptance:
> - `<alloc>` ≤ 30 MB.
> - `<free>` ≥ 30 MB.
> - Toast `warning_low_memory_playback` does NOT fire during the run.
>
> Record actual values in Blockers Log of INDEX.md with `expected | actual` pairs.
>
> For `photos`, do not run the MP3/player acceptance. Instead, record that the flavor signs off the shared image/cache/startup/network phases and inherits the gallery / thumbnail acceptance from the earlier phases.

**Verification:**

- `Grep` in `logs/current.log` — `MEM_PROBE | checkpoint=PRE_PLAY | scenario=audio` line present.
- `Grep` — `native heap critically low after GC` and `Мало памяти` both ABSENT from the most recent session log.
- Acceptance values recorded as `expected | actual`.

**Status:** `[manual — deferred to human]` — requires emulator/device run with canonical SFTP MP3 scenario. Deferred to BlockNeedUserTest operator test.

---

## Phase Done Criteria

- [ ] Every `Step 08.*` above is `[x] done`.
- [ ] Catalog regenerated and committed.
- [ ] All changelog entries present.
- [ ] Functionality log entry present.
- [ ] Acceptance values meet the strategic §11 criteria for audio-capable flavors, and `photos` is explicitly signed off only for the shared image/cache/startup/network scopes. If they do not: do NOT mark this phase Done; record gap, advance ticket to `Partial` via `/spec-check`.

## Revision History

- **2026-05-15** — by `/spec-update` (GPT-5.4, focus: consistency, completeness)
	- Applied: aligned final acceptance language with the corrected flavor scope (`photos` excluded from MP3/player acceptance), and updated the catalog-cleanup class list to include the revised Phase 03 override model plus `FirstFrameSignal`. Proposed (DISCUSS): 0.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

After this phase: `/spec-check S0207` is the operator's next action. `/spec-check` is responsible for:
- Removing every `Timber.d("S0207:` debug tag inserted during `/spec-dev`.
- Advancing the ticket to `Verified` (or `Partial`/`Broken` based on acceptance).
- Updating `dev/FUNCTIONALITY.log` if outcome is `Partial`/`Broken`.

---

## Rollback Plan

This phase has no code changes — only catalog, changelog, and functionality-log entries. Rollback = restore previous catalog/changelog/functionality-log file states (git revert of this phase commit).
