# Phase 05 - Docs Catalog Cleanup

**Strategic spec:** [`../S0305_mid-audio-playback-support.md`](../S0305_mid-audio-playback-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04 - Tests Validation
**Blocks:** final completion gate
**Steps done:** 3 / 3
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Update user-facing feature docs, refresh generated catalogs, and prepare S0305 for `/spec-check`.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] Working tree is clean or intentionally dirty with unrelated changes documented.
- [ ] No unresolved implementation blockers remain.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 10 |
| `docs/FEATURES_RU.md` | Modified | ≤ 10 |
| `docs/FEATURES_UK.md` | Modified | ≤ 10 |
| `dev/CATALOG/app_v2.jsonl` | Generated | local index |
| `dev/CATALOG/app_v2.md` | Generated | local index |
| `PLAN/S0305_mid-audio-playback-support/INDEX.md` | Modified | ≤ 30 |
| `PLAN/S0305_mid-audio-playback-support/PHASE_05__docs-catalog-cleanup.md` | Modified | ≤ 30 |

---

## Steps

### Step 05.1 - Update Feature Docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`  
**Depends on:** start of phase

**Prompt for developer:**

> Add a concise bullet under the Audio Player section in all three feature docs. State that MID/MIDI files play as regular music from local, network, and cloud sources after the usual cache preparation. Do not mention internal class names or implementation details.

**Verification:**

- `Grep` - `MID/MIDI` exists in `docs/FEATURES.md`.
- `Grep` - `MID/MIDI` exists in `docs/FEATURES_RU.md`.
- `Grep` - `MID/MIDI` exists in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Added mirrored Audio Player bullets for MID/MIDI playback from local, network, and cloud sources after normal cache preparation. Dev log recorded.

---

### Step 05.2 - Refresh Catalog And Dev Logs

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`, `dev/CHANGELOG.md`  
**Depends on:** Step 05.1

**Prompt for developer:**

> Run the catalog wrapper after Kotlin changes and ensure dev log entries exist for every modified source, config, docs, and spec file. Use `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Do not edit generated catalog files by hand.

**Verification:**

- `Command` - `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- `Grep` - `AudioPlaybackService` exists in `dev/CATALOG/app_v2.md`.
- `Grep` - `MidiPlaybackPolicy` exists in `dev/CATALOG/app_v2.md`.
- `Grep` - `S0305` exists in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 4/4 PASS. Ran `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`; catalog contains `AudioPlaybackService` and `MidiPlaybackPolicy`; `dev/CHANGELOG.md` contains S0305 entries. Dev log recorded.

---

### Step 05.3 - Close Tactical Progress Markers

**Files:** `PLAN/S0305_mid-audio-playback-support/INDEX.md`, `PLAN/S0305_mid-audio-playback-support/PHASE_05__docs-catalog-cleanup.md`  
**Depends on:** Step 05.2

**Prompt for developer:**

> Mark completed phases and this phase's steps only after their verification predicates pass. Update `INDEX.md` phase counters and Completion Gate. Then run `/spec-check S0305`; do not manually set strategic status to `Verified`.

**Verification:**

- `Grep` - `**Status:** Done` exists in `PLAN/S0305_mid-audio-playback-support/INDEX.md` after all phases close.
- `Grep` - `05 | docs-catalog-cleanup | 04 | ✅ Done` exists in `PLAN/S0305_mid-audio-playback-support/INDEX.md` after this phase closes.
- `Command` - `/spec-check S0305` completes and writes the latest audit into the strategic spec.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Tactical `INDEX.md` is `Done`, Phase 05 row is `✅ Done`, and `/spec-check S0305` wrote the final inline audit to the strategic spec. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` or `scripts/post-change.ps1`.
- [x] `/spec-check S0305` has been run.

---

## Handoff Notes to Next Phase

Final phase - see `INDEX.md` Completion Gate.

---

## Rollback Plan

Revert phase commit(s). Documentation and generated catalog updates are reversible.