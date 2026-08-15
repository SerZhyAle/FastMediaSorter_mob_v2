# Phase 02 - Taxonomy & Baseline

**Strategic spec:** [`../S0339_strings-thematic-split.md`](../S0339_strings-thematic-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Freeze the deterministic prefix→thematic-file mapping in a machine-readable table and capture the before-migration key-union baseline per locale.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0339_strings-thematic-split/taxonomy.psd1` | New | ≤ 120 |
| `temp/s0339_baseline_en.txt` | New (artifact) | - |
| `temp/s0339_baseline_ru.txt` | New (artifact) | - |
| `temp/s0339_baseline_uk.txt` | New (artifact) | - |

---

## Steps

### Step 02.1 - Author the taxonomy table

**Files:** `PLAN/S0339_strings-thematic-split/taxonomy.psd1`
**Depends on:** - start of phase

**Prompt for developer:**

> Write a PowerShell data file mapping each target thematic file to its owned key prefixes. Apply the resolved rule (strategic §6): threshold ≥~20 keys per theme; cross-cutting and ambiguous prefixes stay in residual `strings.xml`; prefixes that collide with existing thematic files merge into that file or stay residual. Proposed mapping:
> - `strings_settings.xml` ← settings, setting, pref
> - `strings_input.xml` ← keybinding, kbm, touch
> - `strings_video_player.xml` ← player, playback, video
> - `strings_vr.xml` (existing) ← vr
> - `strings_reader.xml` ← pdf, epub, text
> - `strings_sources.xml` ← smb, sftp, cloud, dropbox
> - `strings_setup.xml` ← welcome, sysinfo
> - `strings_file_operations.xml` ← file, delete, import, export, addresource
> - `strings_scheduled.xml` ← scheduled
> - `strings_image_viewer.xml` ← image, gif
> - `strings_ocr.xml` ← ocr, translation
> - `strings_audio.xml` ← audio
> - `strings_drawing.xml` ← draw
> - `strings_widget.xml` ← widget
> - `strings_calculator.xml` ← calculator
> - `strings_game.xml` ← game
>
> Everything else (error, dialog, msg, toast, tooltip, label, hint, link, resource, activity, perm, sort, camera, wear, …) stays in residual `strings.xml`.

**Verification:**

- `Glob` - `taxonomy.psd1` exists.
- `Grep` - each target filename above appears exactly once.
- Manual: file imports clean via `Import-PowerShellDataFile`.

**Status:** `[ ]` not done

---

### Step 02.2 - Capture per-locale key-union baseline

**Files:** `temp/s0339_baseline_{en,ru,uk}.txt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Run the Phase 01 `audit` action and split its output into one sorted key-list file per locale under `temp/`. These are the immutable before-migration oracles.

**Verification:**

- `Glob` - all three baseline files exist and are non-empty.
- Record `expected | actual` line counts per locale.

**Status:** `[ ]` not done

---

### Step 02.3 - Pre-flight collision check

**Files:** (read-only)
**Depends on:** Step 02.2

**Prompt for developer:**

> For every prefix in the taxonomy, confirm no key it would move already lives in a non-residual thematic file (would indicate the move engine must skip it). Print a report; zero collisions expected for newly created files, and `vr`→existing `strings_vr.xml` is an intentional merge, not a collision.

**Verification:**

- Manual: collision report printed; only the intended `vr` merge flagged, everything else clean.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] `taxonomy.psd1` imports without error.
- [ ] Three baseline files present in `temp/`.
- [ ] Dev log entry added for `taxonomy.psd1`.

---

## Handoff Notes to Next Phase

`taxonomy.psd1` is the single source of truth for Phase 03's bulk moves. Baseline files are the comparison oracle for Phase 04.

---

## Rollback Plan

Delete `taxonomy.psd1` and the `temp/` baselines - no resource files touched.
