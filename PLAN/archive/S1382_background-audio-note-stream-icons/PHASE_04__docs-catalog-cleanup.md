# Phase 04 - Docs, inventory and catalog cleanup

**Strategic spec:** [`../S1382_background-audio-note-stream-icons.md`](../S1382_background-audio-note-stream-icons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Record the delivered capability in the developer inventory and close the ticket mechanically through the project facade.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | append one record |
| `dev/CHANGELOG.md` | Modified | via script only |
| `dev/CATALOG/app_v2.jsonl` | Modified | regenerated |

> `docs/FEATURES.md` and its `_RU` / `_UK` twins are deliberately absent: per CLAUDE.md §11 the showcase files are populated only by `/skill-release` from the `ALL_FEATURES` diff, never per-spec.

---

## Steps

### Step 04.1 - Record the capability in the developer inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Append one record through `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the background-playback bar's new indication in English: a rotating note while audio plays, the channel's own icon for a stream that has one, album art shown motionless. Set the record's `spec` field to `S1382`. Validate with `pwsh -NoProfile -File scripts/all_features/validate.ps1` and require exit 0. Do not hand-edit the JSONL.

**Why:**

Strategic §8 states the change is user-visible and names it as a FEATURES-level capability, and CLAUDE.md §11 makes `docs/ALL_FEATURES.jsonl` the per-spec home for exactly that record.

**Verification:**

- `Grep` - `S1382` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 2\2 PASS. Record `audio-player.now-playing-bar-source-indication`, flavors taken from the `ENABLE_PERSISTENT_AUDIO_PLAYBACK` row of `docs/FLAVOR_MATRIX.md` (standard, legacy, noLegal, vr). Validation: `ALL_FEATURES validation PASS: 641 record(s)`.

---

### Step 04.2 - Close through the post-change facade

**Files:** `dev/CHANGELOG.md`, `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/post-change.ps1` once for the whole ticket, naming every changed Kotlin file with `-Files` and adding `-ScopeToFile` so the gates judge this ticket's set rather than the whole dirty tree. Use `-ChangeType Kotlin -Module app_v2`. Read the printed verdict: only a bare `post-change: PASS` counts as clean, and exit 2 means the gates did not run rather than that they passed.

**Why:**

not stated in strategic spec

**Verification:**

- `pwsh -NoProfile -File scripts/post-change.ps1 ..` exits 0 and prints `post-change: PASS`.
- `Grep` - `S1382` present in `dev/CHANGELOG.md`.
- `Grep` - `NowPlayingManager` present in `dev/CATALOG/app_v2.jsonl` with a current line count.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 3\3 PASS. Ticket-level run over all five Kotlin files plus the inventory returned `PASS WITH ADVISORIES (1)` - the `document-registry` gate asked for acknowledgement of the `feature-inventory` edit. `docs/ALL_FEATURES.schema.json` was read and needs no change: the new record uses only declared properties, every `flavors` value is in the enum, `spec` matches `^S\d{4}$`. Re-run scoped to the inventory with `-RegistryAck "feature-inventory"` returned a bare `post-change: PASS`. Catalog carries `NowPlayingManager` at loc 399 with the six new functions.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - no code touched in this phase; Phase 03's `.\a.ps1 d` (exit 0) is the last build and it already contains the debug probes.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added via `scripts/post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated at the Phase 03 closure; the Phase 04 run reported it already up to date.
- [x] Phase-boundary audit - skipped, this phase is doc-only (`/spec-dev` rule).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - documentation and catalog only, no runtime surface.
