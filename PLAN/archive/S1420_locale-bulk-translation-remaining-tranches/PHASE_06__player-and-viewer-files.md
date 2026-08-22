# Phase 06 - Player and viewer files

**Strategic spec:** [`../S1420_locale-bulk-translation-remaining-tranches.md`](../S1420_locale-bulk-translation-remaining-tranches.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⏭️ Skipped - delivered by the bulk route, 2026-08-14
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** superseded - see Objective
**Started:** -
**Completed:** 2026-08-14 (by import, not by these steps)

---

## Objective

Translate the six thematic files behind the players and viewers, one whole file per step, in all ten locales.

**Superseded 2026-08-14.** All six files went out in the single flat export and came back inside each locale's own file, so there is no per-file tranche left to run. The steps below are kept as the record of what the work would have been and as the fallback recipe for redoing one file on its own; running them now would re-seed text that is already shipped. Evidence measured across all ten locales 2026-08-14: `strings_video_player.xml` 84-86 of 98 eligible, `strings_reader.xml` 84-85 of 89, `strings_image_viewer.xml` 41 of 47 in every locale. The gaps are the deliberate symbol carve-out and the S1626 rejects, not untranslated keys this phase owns.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values-*/strings_video_player.xml` | New | n/a - generated |
| `app_v2/src/main/res/values-*/strings_reader.xml` | New | n/a - generated |
| `app_v2/src/main/res/values-*/strings_image_viewer.xml` | New | n/a - generated |
| `app_v2/src/main/res/values-*/strings_audio.xml` | New | n/a - generated |
| `app_v2/src/main/res/values-*/strings_ocr.xml` | New | n/a - generated |
| `app_v2/src/main/res/values-*/strings_drawing.xml` | New | n/a - generated |

> `values-*` here means the ten target locales listed in INDEX, `values-b+zh+Hans` included.

---

## Tranche procedure

As defined in [PHASE_02 "Tranche procedure"](PHASE_02__setup-screen-completion.md#tranche-procedure). Every step in this phase runs it with no `-KeyPrefix`: strategic §6.1 resolved that each file other than `strings.xml` is already a tranche in its own right.

---

## Steps

### Step 06.1 - `strings_video_player.xml`

**Files:** `app_v2/src/main/res/values-*/strings_video_player.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Run the tranche procedure for `strings_video_player.xml`, 98 keys. Playback control names follow the platform's own vocabulary in each language rather than a literal translation of the English word.

**Why:**

Strategic §5 places the players immediately after `strings.xml` in the visibility order.

**Verification:**

- Key count per locale: expected 98, actual must equal expected in all ten.
- Seeder exit code per locale: expected 0.

**Status:** `[ ]` not done

---

### Step 06.2 - `strings_reader.xml`

**Files:** `app_v2/src/main/res/values-*/strings_reader.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> Run the tranche procedure for `strings_reader.xml`, 89 keys.

**Why:**

Strategic §5 groups the reader with the players as a primary viewing surface.

**Verification:**

- Key count per locale: expected 89, actual must equal expected in all ten.
- Seeder exit code per locale: expected 0.

**Status:** `[ ]` not done

---

### Step 06.3 - `strings_image_viewer.xml`

**Files:** `app_v2/src/main/res/values-*/strings_image_viewer.xml`
**Depends on:** Step 06.2

**Prompt for developer:**

> Run the tranche procedure for `strings_image_viewer.xml`, 47 keys.

**Why:**

Strategic §5 places the viewers with the players in the visibility order.

**Verification:**

- Key count per locale: expected 47, actual must equal expected in all ten.
- Seeder exit code per locale: expected 0.

**Status:** `[ ]` not done

---

### Step 06.4 - `strings_audio.xml`

**Files:** `app_v2/src/main/res/values-*/strings_audio.xml`
**Depends on:** Step 06.3

**Prompt for developer:**

> Run the tranche procedure for `strings_audio.xml`, 37 keys.

**Why:**

Strategic §5 places the players ahead of the service files, and the audio player is one of them.

**Verification:**

- Key count per locale: expected 37, actual must equal expected in all ten.
- Seeder exit code per locale: expected 0.

**Status:** `[ ]` not done

---

### Step 06.5 - `strings_ocr.xml`

**Files:** `app_v2/src/main/res/values-*/strings_ocr.xml`
**Depends on:** Step 06.4

**Prompt for developer:**

> Run the tranche procedure for `strings_ocr.xml`, 32 keys. Language names inside these values name the OCR recognition language, so translate them into the target locale rather than leaving them in English.

**Why:**

Strategic §7's mitigation is closing a surface completely, and OCR text sits inside the viewer screens step 06.3 translated.

**Verification:**

- Key count per locale: expected 32, actual must equal expected in all ten.
- Seeder exit code per locale: expected 0.

**Status:** `[ ]` not done

---

### Step 06.6 - `strings_drawing.xml`

**Files:** `app_v2/src/main/res/values-*/strings_drawing.xml`
**Depends on:** Step 06.5

**Prompt for developer:**

> Run the tranche procedure for `strings_drawing.xml`, 27 keys.

**Why:**

Strategic §7's mitigation is closing a surface completely, and the drawing tools open over the image viewer translated in step 06.3.

**Verification:**

- Key count per locale: expected 27, actual must equal expected in all ten.
- Seeder exit code per locale: expected 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] Six files present in each of the ten locale directories, 330 keys per locale in total.
- [ ] No file under `values/`, `values-ru/` or `values-uk/` modified.
