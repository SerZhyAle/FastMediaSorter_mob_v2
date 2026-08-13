# Phase 04 — perf-measurements

**Strategic spec:** [`../S0196_activity-render-priority-research.md`](../S0196_activity-render-priority-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-06-01
**Completed:** 2026-06-01

---

## Objective

Collect reproducible timing and evidence for every target surface using the Phase 01 protocol and resolve the measured portions of strategic §6.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.
- [x] Phase 03 is ✅ Done.
- [x] `temp/S0196/01_protocol.md` defines the device, modes, and raw artifacts.
- [x] The selected build is installed on the reference device or emulator.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/S0196/04_measurement_journal.md` | New | ≤ 400 |
| `temp/S0196/04_trace_inventory.md` | New | ≤ 260 |
| `temp/S0196/04_frame_notes.md` | New | ≤ 260 |

---

## Steps

### Step 04.1 — Measure player-family surfaces in cold and warm modes

**Files:** `temp/S0196/04_measurement_journal.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `temp/S0196/04_measurement_journal.md` and fill the player-family rows first: standalone image, standalone audio, standalone video, in-app player, and VR boundary if it remains in scope. Record first meaningful content time, full-draw surrogate, and visible order for cold and warm runs.

**Verification:**

- `Glob` — `temp/S0196/04_measurement_journal.md` exists.
- `Grep` — `Standalone image` present in that file.
- `Grep` — `Standalone audio` present in that file.
- `Grep` — `Standalone video` present in that file.
- `Grep` — `PlayerActivity` present in that file.
- `Grep` — `VrPlayerActivity` present in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - Verification 6/6 PASS. Files: temp/S0196/04_measurement_journal.md. Dev log recorded.

---

### Step 04.2 — Measure browse, settings, dialog, and picker hosts

**Files:** `temp/S0196/04_measurement_journal.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Extend `temp/S0196/04_measurement_journal.md` with `BrowseActivity`, `SettingsActivity`, the resource-type dialog only if Phase 03 confirmed a live caller, and all cloud picker hosts. Record time to first visible list item, first visible settings page, or first selectable option, and note any empty or loading shell shown before useful content.

**Verification:**

- `Grep` — `BrowseActivity` present in `temp/S0196/04_measurement_journal.md`.
- `Grep` — `SettingsActivity` present in `temp/S0196/04_measurement_journal.md`.
- `Grep` — `GoogleDriveFolderPickerActivity` present in `temp/S0196/04_measurement_journal.md`.
- `Grep` — `DropboxFolderPickerActivity` present in `temp/S0196/04_measurement_journal.md`.
- `Grep` — `OneDriveFolderPickerActivity` present in `temp/S0196/04_measurement_journal.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - Verification 5/5 PASS. Files: temp/S0196/04_measurement_journal.md. Dev log recorded.

---

### Step 04.3 — Preserve raw evidence index and frame notes

**Files:** `temp/S0196/04_trace_inventory.md`, `temp/S0196/04_frame_notes.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Create `temp/S0196/04_trace_inventory.md` and `temp/S0196/04_frame_notes.md`. For every measured surface, record the trace id, log id, screen recording id, and the first meaningful frame note used to support the verdict.

**Verification:**

- `Glob` — `temp/S0196/04_trace_inventory.md` exists.
- `Glob` — `temp/S0196/04_frame_notes.md` exists.
- `Grep` — `screen recording` present in `temp/S0196/04_trace_inventory.md`.
- `Grep` — `first meaningful frame` present in `temp/S0196/04_frame_notes.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - Verification 4/4 PASS. Files: temp/S0196/04_trace_inventory.md, temp/S0196/04_frame_notes.md. Dev log recorded.

---

### Step 04.4 — Classify severity and perceived gain

**Files:** `temp/S0196/04_measurement_journal.md`
**Depends on:** Step 04.3

**Prompt for developer:**

> Update `temp/S0196/04_measurement_journal.md` with one severity label per surface: `acceptable`, `borderline`, or `needs child spec`. Separate obvious user-visible order issues from synthetic-only findings so Phase 05 can answer strategic §6.10 without re-reading raw traces.

**Verification:**

- `Grep` — `acceptable` present in `temp/S0196/04_measurement_journal.md`.
- `Grep` — `borderline` present in `temp/S0196/04_measurement_journal.md`.
- `Grep` — `needs child spec` present in `temp/S0196/04_measurement_journal.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - Verification 3/3 PASS. Files: temp/S0196/04_measurement_journal.md. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `temp/S0196/04_measurement_journal.md` resolves the measured portions of strategic §6.1–§6.6 and §6.10.
- [x] Every measured surface has a matching entry in `04_trace_inventory.md` and `04_frame_notes.md`.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 05 consumes only the summarized journal and the evidence index. Do not restate raw trace details in the recommendation; link or cite the evidence ids instead.

---

## Rollback Plan

Delete `temp/S0196/04_*` files — no production code or persisted app data changed.