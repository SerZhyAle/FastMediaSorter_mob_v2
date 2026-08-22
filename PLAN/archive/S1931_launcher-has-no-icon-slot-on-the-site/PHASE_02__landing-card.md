# Phase 02 - Landing card for launcher mode

**Strategic spec:** [`../S1931_launcher-has-no-icon-slot-on-the-site.md`](../S1931_launcher-has-no-icon-slot-on-the-site.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-22
**Completed:** 2026-08-22

---

## Objective

Give launcher mode a card in the "Usage Scenarios" section of all three landing pages, with the `ic_launcher_mode` icon exported into the generated doc-asset tree and applied by the existing generator.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - Phase 01 created the guide pages this card links to.
- [ ] Strategic §6 research items blocking this phase are Resolved - §6.1 is Resolved.
- [ ] `app_v2/src/main/res/drawable/ic_launcher_mode.xml` exists (source for the export).
- [ ] `.venv/Scripts/python.exe` exists - `export-doc-icon-pngs.ps1` rasterizes through it.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/icons/doc-icon-map.json` | Modified | ≤ 5 |
| `index.html` | Modified | ≤ 25 |
| `index-ru.html` | Modified | ≤ 25 |
| `index-uk.html` | Modified | ≤ 25 |
| `docs/icons/doc/ic_launcher_mode.svg` | New (generated) | generated |

---

## Steps

### Step 02.1 - Insert the `landing` map entry at index 19

**Files:** `docs/icons/doc-icon-map.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one object `{"title": "Home Screen Replacement (Launcher)", "drawable": "ic_launcher_mode"}` to the `landing` array, positioned immediately after the entry titled `Home-Screen Smart Widgets`, taking the array from 24 entries to 25. Leave `howto`, `docsMap` and `settingsSections` untouched.

**Why:**

`apply-doc-icons.ps1` replaces the content of each `card-icon` span by array position, so the map entry's index and the card's position on the page must be the same number, and strategic §5 fixes the map and the three pages as one change because the generator throws when the two counts disagree.

**Verification:**

- `Grep` - `ic_launcher_mode` matches exactly once in `docs/icons/doc-icon-map.json`.
- Value equality - `(Get-Content docs/icons/doc-icon-map.json -Raw | ConvertFrom-Json).landing.Count` equals `25`.
- Value equality - `.landing[19].drawable` equals `ic_launcher_mode` and `.landing[18].title` equals `Home-Screen Smart Widgets`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-22 - doc-icon-map.json landing 24 -> 25: 'Home Screen Replacement (Launcher)' / ic_launcher_mode inserted at index 19, directly after Home-Screen Smart Widgets at index 18. ic_launcher_mode appears exactly once in the map.

---

### Step 02.2 - Add the card to all three landing pages

**Files:** `index.html`, `index-ru.html`, `index-uk.html`
**Depends on:** Step 02.1

**Prompt for developer:**

> Insert one `<article class="card scenario-card">` immediately after the `Home-Screen Smart Widgets` card in each of `index.html`, `index-ru.html` and `index-uk.html`, copying the surrounding cards' structure: a `card-title-row` holding an empty `<span class="card-icon"></span>` and an `<h3>`, a `card-badges` div carrying `<span class="scenario-badge standard">Standard Only</span>`, a `card-summary` paragraph, and a `details` block with three `detail-list` items and a `card-footer` link to `docs/howto/scenario-launcher-mode.html`. Localize the title, summary, detail items and footer text into Russian in `index-ru.html` and Ukrainian in `index-uk.html`, and point their footers at `scenario-launcher-mode-ru.html` and `scenario-launcher-mode-uk.html`. Check the new copy against `docs/COMMUNICATION_POLICY.md` §2 message formula and §6 tone checklist. Do not add launcher text to the `Home-Screen Smart Widgets` card.

**Why:**

Strategic §3.1 rules the card cannot be folded into `Home-Screen Smart Widgets` because that card is about Android home-screen widgets and merging would hide one function behind the other, and ADR-3 fixes the badge as `Standard Only` because no landing badge names noLegal - the sideload build has its own landing pages.

**Verification:**

- `Grep` - the launcher card's `<h3>` title matches exactly once in each of the three files.
- Value equality - `<span class="card-icon">` count equals `25` in each of the three files.
- `Grep` - `scenario-badge standard">Standard Only` is present inside the new card in each file.
- `Grep` - `scenario-launcher-mode.html` matches in `index.html`, `scenario-launcher-mode-ru.html` in `index-ru.html`, `scenario-launcher-mode-uk.html` in `index-uk.html`.
- `Grep` - `Home-Screen Smart Widgets` card body is unchanged (its `card-summary` still names voice notes, documents and background sync).
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-08-22 - Launcher card inserted after Home-Screen Smart Widgets in all three landing pages; card-icon count 24 -> 25 in each. Badge 'Standard Only', footer links to the locale's own guide page. Widgets card body untouched. Each locale reuses its own neighbouring card's summary and footer wording.

---

### Step 02.3 - Export the generated doc assets for `ic_launcher_mode`

**Files:** `docs/icons/doc/ic_launcher_mode.svg`
**Depends on:** Step 02.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/docs/export-doc-icon-pngs.ps1` and confirm the colour-neutral SVG for `ic_launcher_mode` was written. Do not hand-author the asset - it is generated from `app_v2/src/main/res/drawable/ic_launcher_mode.xml`. The script's PNG half is the guide-index consumer and is verified in Phase 03, not here.

**Why:**

Strategic §4 records that `ic_launcher_mode` reached `icon-inventory.json` through S1919 but was never exported into `docs/icons/doc/`, and `apply-doc-icons.ps1` throws `missing generated svg` for any mapped drawable whose landing asset is absent. The SVG is the only asset the landing pages consume - they inline it so it themes with light and dark mode - so the landing card is provable without the raster.

**Verification:**

- `Glob` - `docs/icons/doc/ic_launcher_mode.svg` exists.
- `Grep` - that SVG carries `currentColor` (the colour-neutral form the landing inlines).

**Status:** `[x]` done

**Step Log:**

- 2026-08-22 - docs/icons/doc/ic_launcher_mode.svg generated (469 B, 5 currentColor paints). Step scope corrected mid-run: the PNG half of export-doc-icon-pngs.ps1 fails on this machine (cairosvg cannot dlopen native libcairo) and is consumed only by the guide indexes, so its verification moved to Phase 03 where it is used.

---

### Step 02.4 - Apply the icons to the landing pages

**Files:** `index.html`, `index-ru.html`, `index-uk.html`
**Depends on:** Step 02.2, Step 02.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/docs/apply-doc-icons.ps1` and confirm it exits 0 and reports `25 cards inlined` for each of the three landing pages.

**Why:**

Strategic §11 criterion 2 requires the generator to run without an exception on all three pages, which is the mechanical proof that the card count and the map agree.

**Verification:**

- Exit code - `apply-doc-icons.ps1` returned 0.
- `Grep` - the new `card-icon` span in each landing page now contains an inline `<svg` with `fill="currentColor"`, not an empty span.
- Value equality - the script printed `25 cards inlined` for `index.html`, `index-ru.html` and `index-uk.html`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-22 - apply-doc-icons.ps1 exit 0, reported '25 cards inlined' for index.html, index-ru.html and index-uk.html. The launcher card's card-icon span now holds an inline currentColor SVG in all three locales.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - not applicable, no source, resource or build file touched.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for the phase's file set via `scripts/post-change.ps1`.
- [x] `dev/CATALOG/<module>.jsonl` regeneration not required - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`docs/icons/doc/ic_launcher_mode.png` now exists, which is what Phase 03's guide-index rows embed. The card's footer already resolves to the Phase 01 guide pages, so no link on the landing pages dangles.

---

## Rollback Plan

Revert the phase's edits to the three landing pages and `doc-icon-map.json`, then re-run `export-doc-icon-pngs.ps1` (it prunes assets no longer named in the map) and `apply-doc-icons.ps1`. No data migration and no app surface is involved.
