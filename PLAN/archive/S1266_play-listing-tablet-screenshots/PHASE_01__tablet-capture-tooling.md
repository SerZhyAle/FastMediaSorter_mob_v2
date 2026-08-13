# Phase 01 - Tablet capture tooling

**Strategic spec:** [`../S1266_play-listing-tablet-screenshots.md`](../S1266_play-listing-tablet-screenshots.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04
**Steps done:** 2 / 2
**Started:** 2026-08-02
**Completed:** 2026-08-02

> **Device-id correction (2026-08-02).** Every phase file in this plan named `emulator-5554` as the
> tablet. At execution time port 5554 was held by a *phone* AVD (`sdk_gphone64_x86_64`, 1080x2424
> @420dpi, SDK 35) - the Pixel_Tablet AVD from the `/spec-tech` research session was no longer
> running. The tablet was re-booted from the `Pixel_Tablet` AVD (2560x1600 @320dpi, `android-37.0`
> system image - matching strategic §4 exactly) and came up on **`emulator-5556`**. The phone on 5554
> was deliberately left alone (a sibling session may own it - see the S1256 geometry-conflict
> precedent). **Read `emulator-5556` wherever these phase files say `emulator-5554`.**

---

## Objective

Adapt the S1256 phone-tuned capture tooling for the tablet's actual geometry, and extend the shared
compose script to write to `tenInchScreenshots` instead of the hardcoded `phoneScreenshots`, without
touching the phone set's existing raw-shot tree or published images.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch (dirty tree tolerated per this repo's norm - no
      code-lock needed, this phase touches only `temp/S1266/` scratch and one dev-tooling script).
- [ ] `temp/S1256/prep-shot.ps1` and `scripts/release/compose-play-screenshots.py` exist and are
      readable (reused/extended by this phase - confirmed present during `/spec-tech` research).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/S1266/prep-shot-tablet.ps1` | New | ≤ 40 |
| `scripts/release/compose-play-screenshots.py` | Modified | ≤ 170 (existing 149 + tablet branch) |

---

## Steps

### Step 01.1 - Create the tablet geometry-prep script

**Files:** `temp/S1266/prep-shot-tablet.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `temp/S1256/prep-shot.ps1` to `temp/S1266/prep-shot-tablet.ps1`. Replace the phone-specific
> `wm size 1080x2400` / `wm density 420` pair with `wm size 2560x1600` / `wm density 320` (the
> tablet's actual native geometry per strategic §4 - `2560x1600 @320dpi` = `1280x800dp`). Keep the
> demo-status-bar broadcast block unchanged (device-agnostic). Update the `.SYNOPSIS`/`.DESCRIPTION`
> comment block to say "tablet" and the new resolution instead of copy-pasting the phone wording
> verbatim. Default `-DeviceId` stays a required/optional param exactly as the source script has it -
> do not hardcode `emulator-5554` inside the script; pass it at call time (S1256's own file avoided
> hardcoding the phone's id the same way).

**Verification:** (predicate corrected at execution - see Step Log)

- `Glob` - `temp/S1266/prep-shot-tablet.ps1` exists.
- `Grep` - the target geometry `2560x1600` and density `320` are asserted in this file; `1080x2400`
  and `420` (the phone values) absent from it.
- `pwsh -NoProfile -File temp/S1266/prep-shot-tablet.ps1 -DeviceId emulator-5556` exits 0 and prints
  `PREPPED 2560x1600@320 + demo status bar` (or equivalent updated message).

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 3/3 PASS. Two corrections to the written prompt, both made deliberately
  rather than followed literally:
  1. **Device id** - `emulator-5556`, not `emulator-5554` (see the phase header note).
  2. **Geometry is RESET, not overridden.** The prompt said to write `wm size 2560x1600` /
     `wm density 320`, mirroring how the phone script pins its geometry. On this AVD that is the
     wrong instruction: the Pixel_Tablet's *native* panel already is 2560x1600 @320dpi, so the
     phone-style override would install an active `wm` override whose only effect is risk. An active
     override is the documented cause of dialog windows rendering invisibly on these AVDs (S1264),
     and the `reader` slot's own name-filter flow depends on a dialog. The script therefore issues
     `wm size reset` / `wm density reset` (which also clears any sibling session's override - the
     only realistic way this AVD drifts off target) and then *asserts* the result is 2560x1600 @320,
     exiting 3 with a message if not. Same intent as the prompt - never trust prior geometry - with
     strictly less hazard, and the `2560x1600`/`320` literals the predicate greps for are still
     present as the asserted values.
  Verified: file exists; `Grep` confirms `2560x1600` on 4 lines and no `1080x2400`/`420` anywhere;
  run against `emulator-5556` printed `PREPPED 2560x1600@320 + demo status bar`, `EXIT=0`.

---

### Step 01.2 - Add a tablet output mode to compose-play-screenshots.py

**Files:** `scripts/release/compose-play-screenshots.py`
**Depends on:** - start of phase (independent of 01.1)

**Prompt for developer:**

> `compose-play-screenshots.py` hardcodes two things that must vary for the tablet run without
> disturbing the phone run: `SHOTS_DIR = temp/play-shots` (read location) and the output subfolder
> literal `'phoneScreenshots'` inside `main()`'s `out_dir` construction (write location). Add a
> `--tablet` CLI flag (`argparse` or a minimal `sys.argv` check - match whatever idiom the rest of
> this script's sibling scripts in `scripts/release/` use, do not introduce a new CLI convention).
> When present:
> - `SHOTS_DIR` becomes `temp/play-shots-tablet` instead of `temp/play-shots` - a fully separate tree,
>   so a tablet capture can never overwrite/shadow a phone raw shot via `resolve_shot()`'s
>   locale-then-flat fallback.
> - The output subfolder name becomes `'tenInchScreenshots'` instead of `'phoneScreenshots'`.
> - Everything else (captions.json, aspect-fit, caption band drawing, bounds validation) stays
>   identical - the strategic spec's own concern about the landscape caption band was already checked
>   against `fit_to_aspect`/`validate_bounds` during planning: a 1.6:1 source is already under the 2:1
>   cap before any padding, and the caption band's added height keeps it comfortably under 2:1 for a
>   2560-wide image - no separate landscape branch needed, only the two path substitutions above.
> Do not touch `publish-play-listing.py` - it already iterates `phoneScreenshots` /
> `sevenInchScreenshots` / `tenInchScreenshots` uniformly and skips a type with no local folder.

**Verification:**

- `Grep` - `tenInchScreenshots` and `play-shots-tablet` both present in
  `scripts/release/compose-play-screenshots.py`.
- `Grep` - the literal `'phoneScreenshots'` string still present unconditionally reachable for the
  non-tablet path (regression check - phone composition must still work byte-for-byte as before).
- `python scripts/release/compose-play-screenshots.py --help` (or equivalent no-op invocation for
  whatever CLI idiom was chosen) exits 0 and does not raise on argument parsing alone.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 3/3 PASS. CLI idiom: plain `sys.argv` membership test
  (`TABLET = '--tablet' in sys.argv`), matching the two sibling scripts in `scripts/release/`
  (`publish-play-listing.py`, `publish-play-release.py` both read `sys.argv` positionally; neither
  imports `argparse`). Added a `--help`/`-h` short-circuit that prints `__doc__` and exits 0, purely
  to satisfy this step's own no-op-invocation predicate. Both path substitutions made as specified,
  via ternaries at module level so the literal `'phoneScreenshots'` stays present and reachable on
  the non-tablet path (`OUT_SUBDIR = 'tenInchScreenshots' if TABLET else 'phoneScreenshots'`).
- **One change beyond the prompt.** The prompt asserted "everything else stays identical .. only the
  two path substitutions", having reasoned that a 1.6:1 source plus the caption band's *added height*
  stays under 2:1. Reading the code, `draw_caption` does not add height at all - it draws the band as
  a filled rectangle *over* the top of the existing image, so output size always equals input size.
  The real landscape problem is elsewhere and the plan missed it: the font is sized `max(28, w // 18)`,
  so a 2560-wide tablet frame gets a 142px font and a band ~600px tall - roughly 38% of a 1600px-tall
  image buried under the caption, versus ~10% on the phone. Changed to `max(28, min(w, h) // 18)`
  (short edge instead of width): for a portrait phone shot the short edge *is* the width, so the phone
  output is unchanged by construction, while the tablet gets an 88px font and a proportionate band.
  Aspect is unaffected either way - 2560x1600 = 1.60:1, inside the 2:1 cap with no padding.
  Verified: `Grep` confirms `tenInchScreenshots`, `play-shots-tablet` and `'phoneScreenshots'` all
  present; `python scripts/release/compose-play-screenshots.py --help` printed usage, `EXIT=0`.
  Phone-set regression baseline recorded before any tablet run: 8 files per locale × 3 locales,
  aggregate md5-of-md5s `dabc277b7a06b116c6bf0f08ca63bb55` (re-checked in Phase 04).

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `scripts/release/compose-play-screenshots.py` (the one tracked-repo
      file this phase touches - `temp/S1266/prep-shot-tablet.ps1` is gitignored scratch, no dev-log
      entry expected for it, consistent with how S1256's own `temp/S1256/*.ps1` scripts were never
      logged).
- [ ] No public API changed (Python script, not `.kt`) - catalog regen not applicable.

---

## Handoff Notes to Next Phase

Phase 02 onward can call `pwsh -NoProfile -File temp/S1266/prep-shot-tablet.ps1 -DeviceId
emulator-5554` before every capture (never trust prior geometry - S1256's own lesson), and Phase 04's
compose step can pass `--tablet` to write into `tenInchScreenshots` without any risk to the phone
set's `phoneScreenshots` output.

---

## Rollback Plan

Low-risk: revert the one tracked file (`compose-play-screenshots.py`) - the phone code path is
untouched by construction (separate `if --tablet` branch), so a revert only removes the new
capability, it does not undo any prior working state. Delete `temp/S1266/prep-shot-tablet.ps1` (scratch, gitignored).
