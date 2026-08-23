# Phase 04 - Screenshots of the watch screens

**Strategic spec:** [`../S1801_wear-documentation-site-pages.md`](../S1801_wear-documentation-site-pages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-08-19
**Completed:** 2026-08-19

---

## Objective

Specify, capture and place every screenshot the two Wear guides need, so no step that is ambiguous in words alone is left without a picture, and no placeholder survives in any locale.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] A Wear OS surface is listed by `adb devices` - the emulator `sdk_gwear_x86_64` is sufficient per `research/03__wear-screenshots.md`.
- [x] Test media is reachable from that surface, so a populated screen can be captured rather than an empty state.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/howto/SCREENSHOTS.md` | Modified | ≤ 30 added table rows |
| `docs/howto/screenshots/screenshot-wear-*.png` | New | n/a - binary assets |
| `docs/howto/scenario-watch-music*.md` | Modified | placeholder replacement only |
| `docs/howto/scenario-watch-network*.md` | Modified | placeholder replacement only |

---

## Steps

### Step 04.1 - Specify every required frame in the master table

**Files:** `docs/howto/SCREENSHOTS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one row per placeholder left by Phases 02 and 03 to the master table, in the existing column shape: file name, scenario, step, what must be visible on screen, source. Name the file `screenshot-wear-<scenario>-step<N>.png`. The "what must be visible" column states the concrete control or text that proves the step, not a description of the screen in general.

**Why:**

Strategic §7 names unreadable frames as a risk whose mitigation is that the requirement for each frame is fixed in the master table in advance, including what must be visible; a frame captured without that specification cannot be re-shot identically after the interface moves, which is the whole reason the table exists for the nine phone guides.

**Verification:**

- `Grep` - the count of rows matching `screenshot-wear-` in `docs/howto/SCREENSHOTS.md` equals the placeholder count in the English pages of Phases 02 and 03 combined.
- Every added row has a non-empty "what must be visible" cell.

**Status:** `[x]` done

---

### Step 04.2 - Capture the frames from the watch surface

**Files:** `docs/howto/screenshots/screenshot-wear-*.png`
**Depends on:** Step 04.1

**Prompt for developer:**

> Drive the watch to each state Step 04.1 specified, capture the frame at the watch's native resolution via `adb exec-out screencap -p`, crop/pad if the surface is round and the site expects square presentation, and save it under the file name from Step 04.1. Capture from the same language edition as the guide will present, or from the English build if the UI is icon-driven.

**Why:**

Strategic §7 names missing media as a risk whose consequence is an empty slot on the published site; placing actual images rather than leaving placeholders is what closes the risk.

**Verification:**

- `Glob` - every file named in Step 04.1 exists under `docs/howto/screenshots/`.
- Every image has non-zero size and is a valid PNG.

**Status:** `[x]` done

---

### Step 04.3 - Replace placeholders in all six pages

**Files:** `docs/howto/scenario-watch-music*.md`, `docs/howto/scenario-watch-network*.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Replace every `<!-- TODO screenshot: <name> -->` comment in all six files with the Markdown image tag `![<alt text>](screenshots/<name>)`. The alt text translates naturally into each file's language; the file name is identical across the three locales.

**Why:**

Strategic §2.3 requires identical media presentation across locales, and §7 lists stale placeholders on the site as a release defect.

**Verification:**

- `Grep` - `<!-- TODO screenshot:` returns zero hits across the entire `docs/howto/` directory.
- `Grep` - every `![...](screenshots/screenshot-wear-*.png)` points at a file that exists on disk.

**Status:** `[x]` done

---

### Step 04.4 - Confirm no step depends on its picture alone

**Files:** `docs/howto/scenario-watch-music*.md`, `docs/howto/scenario-watch-network*.md`
**Depends on:** Step 04.3

**Prompt for developer:**

> Read each step in all six pages with the image tag mentally hidden. Confirm that the text names the control, the gesture, the target name and the expected result clearly enough that a reader who cannot see the picture can still finish the step.

**Why:**

Strategic §3.2 binds all how-to copy to the principle that an image illustrates a step, it never replaces the explanation; a guide that says "tap the button shown below" fails accessibility and fails when images are blocked.

**Verification:**

- Every step contains a verb naming the action and a noun naming the target control.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - not applicable: no source file is touched in this phase.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable - no Kotlin change.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Both guides are complete pages with real frames and no placeholders, and the master table records how each frame can be re-shot after the interface moves. Phase 05 can point the landing at finished pages rather than at drafts.

---

## Rollback Plan

Revert the phase commit; the placeholders return and the image files are removed. No generated artifact and no source file is involved.
