# Phase 03 - Screenshots with localized captions

**Strategic spec:** [`../S0497_play-store-listing-aso-rewrite.md`](../S0497_play-store-listing-aso-rewrite.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Capture a fixed set of app screens and compose localized caption overlays (EN/RU/UK) into
`play/listing/<locale>/images/phoneScreenshots/`, sized to Play requirements.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] An emulator/device is reachable via `scripts/devtest/adb.ps1` (a connected AVD or device).
- [ ] Python `Pillow` available in `.venv` (install if missing).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `play/listing/captions.json` | New | ≤ 80 |
| `scripts/release/compose-play-screenshots.py` | New | ≤ 200 |
| `scripts/release/capture-play-screenshots.ps1` | New | ≤ 90 |

---

## Steps

### Step 03.1 - Define the screen set and localized captions

**Files:** `play/listing/captions.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `play/listing/captions.json`: an ordered list of screenshot slots, each with a stable `id`
> (e.g. `browse`, `image-viewer`, `video-player`, `slideshow`, `cloud-connect`, `reader`, `widgets`)
> and a localized caption string for `en-US`, `ru-RU`, `uk-UA`. Captions are short marketing lines
> (<=2 short lines). RU/UK use Ё/ё and `..`. Strings pass COMMUNICATION_POLICY §6 checklist. Author as
> UTF-8 via the Write tool (no Cyrillic through bash->pwsh args).

**Verification:**

- `Glob` - `play/listing/captions.json` exists and parses as JSON.
- `Grep` - all three locale keys (`en-US`, `ru-RU`, `uk-UA`) present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification 2/2 PASS. Authored `play/listing/captions.json`: 7 slots (browse, image-viewer, video-player, slideshow, cloud-connect, reader, widgets), each with en-US/ru-RU/uk-UA captions (locale parity OK, JSON parses). Screen set + auto-draft confirmed by owner. Dev log recorded.

---

### Step 03.2 - Capture raw screenshots from a device

**Files:** `scripts/release/capture-play-screenshots.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `capture-play-screenshots.ps1` that drives the standard-debug app via `scripts/devtest/adb.ps1`
> (launch, navigate to each slot, `adb shot`) and saves raw PNGs to `temp/play-shots/<id>.png`. Where a
> slot cannot be auto-navigated, capture manually and drop the PNG under `temp/play-shots/` with the
> matching `<id>.png` name. Record the device id and exit code per CLAUDE.md §15.

**Verification:**

- `Glob` - `scripts/release/capture-play-screenshots.ps1` exists.
- `Grep` - `adb.ps1` referenced.
- `Glob` - at least one `temp/play-shots/*.png` produced after a run.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification 3/3 PASS. Authored `scripts/release/capture-play-screenshots.ps1` (wraps `adb.ps1 shot` into named slot files, validates id against captions.json, `-Launch`/`-List`/`-Slot`). Device emulator-5556. Captured 3 slots: `browse` (image grid), `image-viewer`, `video-player` (PNGs in `temp/play-shots/`). Remaining 4 slots manual-pending: `slideshow` (player init path hits parked S0550 crash), `cloud-connect` (needs a cloud account), `reader` (file:// intent rejected by PdfViewerManager - in-app browse supplies content://; capture via UI nav), `widgets` (needs home-screen placement). Captured during this run; `parked: S0550 player-slideshow-init crash`. Dev log recorded.

---

### Step 03.3 - Compose localized caption overlays

**Files:** `scripts/release/compose-play-screenshots.py`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add `compose-play-screenshots.py` (Pillow): for each locale in `captions.json` and each slot id,
> load `temp/play-shots/<id>.png`, draw the localized caption as an overlay band (readable font,
> `?attr`-equivalent N/A here - use a fixed brand color), and write to
> `play/listing/<locale>/images/phoneScreenshots/<NN>.png`. Enforce Play constraints: PNG/JPEG, min
> edge >=320, max edge <=3840, 2:1 aspect bound. No bare `except:` - fail loud per missing input.

**Verification:**

- `Glob` - `scripts/release/compose-play-screenshots.py` exists.
- `Grep` - `play/listing` output path present in the script.
- `Glob` - `play/listing/en-US/images/phoneScreenshots/*.png` produced after a run.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification 3/3 PASS. Authored `scripts/release/compose-play-screenshots.py` (Pillow); installed Pillow 12.2.0 into `.venv`. Pads each raw shot onto a brand-color 2:1 canvas, draws the localized caption band (Segoe UI Bold, full Cyrillic/Ukrainian), enforces Play bounds (min 320, max 3840, <=2:1). Produced 9 PNGs (1200x2400): 3 slots x 3 locales under `play/listing/<locale>/images/phoneScreenshots/01..03.png`. 4 slots skipped with warning (manual-pending: slideshow, cloud-connect, reader, widgets). uk-UA caption render visually confirmed. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Each locale has >=2 composed screenshots under `images/phoneScreenshots/` (3 each).
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Composed screenshots live under `play/listing/<locale>/images/phoneScreenshots/`; the Phase 02 uploader
picks them up automatically on the next run.

---

## Rollback Plan

Delete `play/listing/<locale>/images/`, the two scripts, and `temp/play-shots/` - no build dependency.
