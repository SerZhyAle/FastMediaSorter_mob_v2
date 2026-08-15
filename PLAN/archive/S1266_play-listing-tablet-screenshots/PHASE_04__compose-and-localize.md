# Phase 04 - Locale replication and composition

**Strategic spec:** [`../S1266_play-listing-tablet-screenshots.md`](../S1266_play-listing-tablet-screenshots.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03 (validated en-US raw set + reusable capture script)
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-08-02
**Completed:** 2026-08-02

> **Device id:** `emulator-5556` - see the Phase 01 header note.

---

## Objective

Replicate the validated capture across `ru-RU` and `uk-UA`, then compose all three locales' raw shots
into caption-bearing, Play-bounds-valid images under `tenInchScreenshots`.

---

## Prerequisites

- [ ] Phase 03 done - 8/8 en-US frames QA-passed, `capture-locale-set-tablet.ps1` proven working.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/play-shots-tablet/ru-RU/*.png` (8 files) | New | - |
| `temp/play-shots-tablet/uk-UA/*.png` (8 files) | New | - |
| `play/listing/en-US/images/tenInchScreenshots/*.png` | New | - |
| `play/listing/ru-RU/images/tenInchScreenshots/*.png` | New | - |
| `play/listing/uk-UA/images/tenInchScreenshots/*.png` | New | - |

---

## Steps

### Step 04.1 - Capture ru-RU and uk-UA raw sets

**Files:** none (device-driven output only)
**Depends on:** - start of phase

**Prompt for developer:**

> Run `temp/S1266/capture-locale-set-tablet.ps1 -Locale ru-RU -DeviceId emulator-5554`, then the same
> with `-Locale uk-UA`. Per S1256's own documented trap, the name filter used for the `reader` slot
> does not survive a locale switch and must be re-applied by the script itself (already built into
> Step 03.1's script per its prompt) - confirm this actually happened by checking the `reader` frame
> for each locale shows the book content, not a stray file. Spot-check at least the `reader` and
> `browse` frames per locale (the two slots with locale-sensitive UI text/filtering) for correctness
> before moving to composition - do not trust a clean exit code alone.

**Verification:**

- `Glob` - `temp/play-shots-tablet/ru-RU/*.png` and `temp/play-shots-tablet/uk-UA/*.png` each return
  exactly 8 files.
- Spot-check confirms `reader` slot shows real book content (not an XML/wrong-file frame) in both
  locales.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 2/2 PASS, after the spot-check caught a real defect and the sets were
  re-shot. `ru-RU` and `uk-UA` each hold 8 PNGs, all 2560x1600.
- **The spot-check this step mandates is what caught it, and a clean exit code would not have.**
  First pass: the `ru-RU` browse frame had correctly translated chrome but its resource tiles still
  read **English** (`All Files`, `Recent Media`, ..), and the `uk-UA` browse frame had Ukrainian
  chrome over **Russian** tiles (`Все файлы`, `Загрузки`). The built-in resources carry stored names
  and the app rewrites them to the current locale only on the launch *after* the switch lands, so
  every frame was showing the previous locale's names. Russian text inside a Ukrainian store listing
  is not shippable, and `cmd locale get-app-locales` reported success throughout.
- Fix: `capture-locale-set-tablet.ps1` now performs a discarded warm-up `stop` + `launch` immediately
  after the locale switch, before any capture. Verified by probe - `tvResourceName` values came back
  `Усі файли`, `Нещодавні медіа`, `Вся музика`, `Усі відео`, `Фото з камери`, `Усі зображення`.
- All three locales were then re-shot with the corrected script (en-US too, so the whole set is the
  output of one script version). Re-checked frames: `ru-RU/browse` fully Russian, `uk-UA/browse` fully
  Ukrainian with no Russian left, `ru-RU/reader` real book text at 17/26, `uk-UA/reader` real book
  text at 21/26 - neither an XML or wrong-file frame.
- The four folders added in Phase 02 keep their as-created English names (`Photos`, `Videos`,
  `Music`, `Books`) in all three locales. That is correct, not a miss: they are user-chosen resource
  names, not localized strings, and a real user's own folder names do not translate either.

---

### Step 04.2 - Compose all three locales into tenInchScreenshots

**Files:** `play/listing/<locale>/images/tenInchScreenshots/*.png`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `python scripts/release/compose-play-screenshots.py --tablet` (the flag Phase 01 Step 01.2
> added). Confirm it reports 8 composed images per locale × 3 locales = 24 total, zero "missing slot"
> warnings (a missing warning means a raw shot from Phase 03/04.1 did not land where the script looks
> for it - fix the path mismatch, do not proceed with a partial set per strategic §2 goal 2's "publish
> the whole slot" requirement). Confirm the phone set's own `phoneScreenshots` folders are unchanged
> (byte-identical file count/mtimes to before this step) - this script's `--tablet` branch must never
> touch the phone path.

**Verification:**

- `python scripts/release/compose-play-screenshots.py --tablet` exits 0, output reports 24 composed
  images (8 × 3 locales), 0 missing.
- `Glob` - `play/listing/en-US/images/tenInchScreenshots/*.png`,
  `play/listing/ru-RU/images/tenInchScreenshots/*.png`,
  `play/listing/uk-UA/images/tenInchScreenshots/*.png` each return exactly 8 files.
- `play/listing/*/images/phoneScreenshots/` file counts match their pre-Phase-04 state (regression
  check - the tablet run must not have touched the phone set).

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 3/3 PASS. `python scripts/release/compose-play-screenshots.py --tablet`
  exit 0, printed all 24 lines (`<locale>/NN (slot) -> ..tenInchScreenshots\NN.png (2560x1600)`),
  `DONE: composed 24 screenshot(s) across 3 locale(s)`, and **no** `WARNING: n slot(s) without a raw
  shot` line - so no slot was silently skipped.
- `Glob` - `en-US`, `ru-RU`, `uk-UA` each hold exactly 8 files under `images/tenInchScreenshots/`.
- **Phone set proven untouched, not merely counted.** File counts stayed 8/8/8 *and* the aggregate
  md5-of-md5s over all 24 `phoneScreenshots` PNGs is `dabc277b7a06b116c6bf0f08ca63bb55`, byte-identical
  to the baseline recorded before Phase 01 began. The `--tablet` branch reads
  `temp/play-shots-tablet/` and writes `tenInchScreenshots`, so it cannot reach the phone path.
- Play bounds re-checked independently of the script's own `validate_bounds`: all 24 are 2560x1600,
  aspect 1.600, inside `[320,3840]` and under the 2:1 cap. No padding was needed - `fit_to_aspect`
  leaves a 1.6:1 source alone.
- Composed output inspected, not just counted: the caption band renders in brand blue with correct
  Cyrillic ("Вся медиатека - в пару касаний") and occupies roughly a quarter of the frame height.
  That is the Phase 01 short-edge font fix doing its job; the original width-based sizing would have
  buried ~38% of a landscape frame under the band.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] 24/24 composed tablet images present across 3 locales, all within Play's `[320,3840]` /
      `<=2:1` bounds (enforced by the compose script's own `validate_bounds` - a non-zero exit means
      this criterion already failed).
- [ ] Phone set untouched.

---

## Handoff Notes to Next Phase

Phase 05 runs `publish-play-listing.ps1 -Mode validate` against the now-populated
`tenInchScreenshots` folders, then the real publish, then closes the ticket.

---

## Rollback Plan

Low-risk: `play/listing/*/images/tenInchScreenshots/` is new content, not a modification of any
existing published slot - deleting the three new folders fully reverts this phase with zero risk to
the live phone set.
