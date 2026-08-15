# Phase 04 - Playback / Streams / Other Propagation

**Strategic spec:** [`../S0618_landscape_settings_density_alignment.md`](../S0618_landscape_settings_density_alignment.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02 + owner pilot sign-off
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Apply the weighted-row density pattern (R1, R2) and confirm left-alignment (R5) on the Playback, Streams and Other-media landscape layouts. These three carry no horizontal-centering offenders (only `center_vertical`), so the work is density packing.

---

## Prerequisites

- [x] Phase 02 ✅ Done.
- [x] **Owner approved the General pilot landscape screenshots.** (owner sign-off 2026-06-23 via `/spec-all`)
- [x] Backup any touched file >500 LOC to `temp/` before editing. (none in this phase >500 LOC)

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` | Modified | ≤ 360 |
| `app_v2/src/main/res/layout-land/fragment_settings_streams.xml` | Modified | ≤ 220 |
| `app_v2/src/main/res/layout-land/fragment_settings_other.xml` | Modified | ≤ 240 |

> Landscape-only by scope; portrait variants not mirrored. `center_vertical` row alignment is intentionally kept.

---

## Steps

### Step 04.1 - Playback landscape density (R1, R2)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Pack compact toggle rows into weighted horizontal rows of up to 4, value fields 2 per row, left-packed via the house weighted-`LinearLayout` pattern. Keep `nextFocus*` for D-pad order. Do not introduce `android:gravity="center"` / `layout_gravity="center"` on content.

**Verification:**

- `Grep` - no `android:layout_gravity="center"` on `MaterialButton` in the file.
- `Grep` - at least one weighted multi-child row added.
- `/build` (`.\a.ps1 fc`) passes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - PASS. File was already dense (S0609). Packed the solo `rowAllowRename` into the existing delete/confirm row, yielding a 3-up file-op row (rename | delete | confirm) with nextFocus wired. No centering offenders (`btnResetPlaybackSection` keeps `layout_gravity="end"`). `.\a.ps1 fc` SUCCESSFUL.

---

### Step 04.2 - Streams landscape density (R1, R2)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_streams.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Spec self-correction (`/spec-all` 2026-06-23): the Streams fragment body holds a single master toggle (`rowEnableStreams`) plus the `btnStreams` shortcut button - there is nothing to pack into a multi-child weighted row, and there are no centering offenders. The original "pack compact toggle rows" instruction and its "at least one weighted multi-child row added" predicate are unsatisfiable here. No edit required; confirm the fragment is already minimal and left-aligned.

**Verification:**

- `Grep` - `fragment_settings_streams.xml` has no `android:gravity="center"` / `android:layout_gravity="center"` on content.
- `/build` (`.\a.ps1 fc`) passes (validated centrally in Step 04.1).

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - PASS (no-op). Single-toggle fragment; nothing to pack, no centering. Predicate corrected.

---

### Step 04.3 - Other-media landscape density (R1, R2)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_other.xml`
**Depends on:** Step 04.2

**Prompt for developer:**

> Pack compact toggle/field rows into weighted horizontal rows, left-packed. Keep the OCR sub-blocks' `center_vertical` row alignment. Preserve `nextFocus*`.

**Verification:**

- `Grep` - no `android:gravity="center"` (non-`center_vertical`) added on content controls.
- `/build` (`.\a.ps1 fc`) passes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - PASS (no edit). File already follows the landscape pattern: translation toggle + lens-style are a weighted 2-up row, all `gravity` uses are `center_vertical` / `end|center_vertical` (no horizontal centering). OCR sub-rows stay stacked (each is a conditionally-visible label + spinner; pairing gone-by-default rows would leave half-empty rows). Predicate (no centering added) holds.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project builds - run `/build`. (`.\a.ps1 fc` + final `.\a.ps1 d` SUCCESSFUL)
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Playback / Streams / Other dense. Operations (destinations) remains - the largest fragment, handled alone in Phase 05.

---

## Rollback Plan

Restore touched files from `temp/` backups - layout-only.
