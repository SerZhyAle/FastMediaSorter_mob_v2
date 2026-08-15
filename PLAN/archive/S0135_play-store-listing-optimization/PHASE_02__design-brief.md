# Phase 02 — Design Brief

**Strategic spec:** [`../S0135_play-store-listing-optimization.md`](../S0135_play-store-listing-optimization.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase (can run in parallel with Phases 01, 03)
**Blocks:** Phase 05
**Steps done:** 1 / 1
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Create `store_assets/design_brief.md` — a self-contained brief for the designer (external freelancer, owner, or AI-assisted) covering icon, feature graphic, and six screenshots with overlay text in EN/RU/UK.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `store_assets/design_brief.md` | New | ≤ 250 |

---

## Steps

### Step 02.1 — Create design brief document

**Files:** `store_assets/design_brief.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `store_assets/design_brief.md` with the content below verbatim.
> This document is the deliverable for the designer; its content is prescriptive, not aspirational.

```markdown
# Design Brief: Google Play Store Assets — Fast Media Sorter

**Version:** 1.0 (S0135)
**Date:** 2026-05-13
**Scope:** App icon, feature graphic, 6 phone screenshots × 3 locales (EN, RU, UK)

---

## 1. App Icon — 512 × 512 px

**Format:** PNG, 32-bit RGBA, transparent background
**Style:** Symbol-only — no text, no wordmark inside the icon
**Core motif:** Rising arrows (upward/diagonal, 2–3 arrows) in a blue gradient
  - Base colour: #1565C0 (deep blue)
  - Highlight colour: #42A5F5 (light blue)
  - Arrow direction: bottom-left → top-right (growth, sorting, upward movement)
**Background:** Solid rounded-rect or circle, dark navy (#0D1B2A) or deep blue (#0A2463)
**Accessibility test:** Icon must be recognisable in greyscale at 48 dp (≈ 192 × 192 device px)
**Do not include:** text "Fast", "Media", or any wordmark; camera lens; film strip; unrelated imagery

### Acceptance criteria
- Passes readability check at 48 dp (export at 3× and inspect at 64 px)
- Passes greyscale conversion — arrows remain distinguishable from background
- No text or letterforms visible inside the icon boundary

---

## 2. Feature Graphic — 1024 × 500 px

**Format:** PNG or JPG, sRGB
**Layout (left to right):**
  - Left 30%: app icon (centred vertically, ~200 px)
  - Centre 40%: headline text (see copy below)
  - Right 30%: phone mockup showing the Browse / Sort screen (portrait phone frame, cropped if needed)
**Background:** dark theme — #0D1B2A (navy) or a subtle dark gradient
**Headline copy:**
  - EN: "Sort photos & videos in seconds"
  - RU: "Сортируй фото и видео за секунды"
  - UK: "Сортуй фото та відео за секунди"
**Sub-headline (optional):** "Local · NAS · Cloud" (same across locales)
**Typography:** sans-serif (Roboto or Inter), bold headline ≥ 48 pt in source file
**Contrast:** headline text contrast ratio ≥ 4.5:1 (WCAG AA) against the dark background

### Acceptance criteria
- All three locale headlines fit without truncation at 1024 × 500 px
- No Google, Apple, Microsoft, or competitor logos/trademarks

---

## 3. Screenshots — 6 Slots × 3 Locales = 18 Files

**Format:** PNG, sRGB, no alpha
**Dimensions:** portrait phone, 1080 × 1920 px minimum (up to 1440 × 2560)
**Device frame:** optional, consistent across all 6 slots
**Overlay style:**
  - Font: Roboto Bold or Inter ExtraBold, ≥ 56 pt in source file
  - Background: semi-transparent dark plaque (#000000 at 65% opacity) behind text
  - Contrast ratio of text over plaque ≥ 4.5:1 (WCAG AA)
  - Text max 2 lines; fits within 90% of screen width

**Screenshot sequence (fixed — do not reorder):**

### Slot 1 — Sorting in action
**Source screen:** Browse screen with files visible, one file selected / mid-operation
**Overlay EN:** "Sort thousands of files in minutes"
**Overlay RU:** "Тысячи файлов — отсортированы за минуты"
**Overlay UK:** "Тисячі файлів — відсортовані за хвилини"

### Slot 2 — One-tap gesture
**Source screen:** Close-up of the sort-destination button bar (tap targets visible)
**Overlay EN:** "One tap — file moved. That's it."
**Overlay RU:** "Одно нажатие — файл перемещён"
**Overlay UK:** "Одне натискання — файл переміщено"

### Slot 3 — Format support
**Source screen:** Browse screen showing a mix of photo, video, GIF, audio, and document thumbnails
**Overlay EN:** "Photos · Videos · Audio · Docs — one app"
**Overlay RU:** "Фото · Видео · Аудио · Документы — одно приложение"
**Overlay UK:** "Фото · Відео · Аудіо · Документи — один додаток"

### Slot 4 — Storage sources
**Source screen:** Add resource screen OR a composite showing SMB/SFTP/FTP/GDrive/OneDrive/Dropbox icons
**Overlay EN:** "Local · SMB · SFTP · FTP · Cloud"
**Overlay RU:** "Локально · SMB · SFTP · FTP · Облако"
**Overlay UK:** "Локально · SMB · SFTP · FTP · Хмара"

### Slot 5 — Before / After
**Source screen:** Split composition — left: unsorted cluttered folder; right: named subfolders tidy
**Overlay EN:** "From chaos to order — in minutes"
**Overlay RU:** "Из хаоса в порядок — за минуты"
**Overlay UK:** "З хаосу в порядок — за хвилини"

### Slot 6 — Settings (one screen only, not first)
**Source screen:** Settings screen — minimal, readable, not cluttered
**Overlay EN:** "Configure once, sort forever"
**Overlay RU:** "Настроил один раз — сортируй всегда"
**Overlay UK:** "Налаштував один раз — сортуй завжди"

---

## 4. File Naming Convention

```
store_assets/screenshots/final/
  slot1_sort-action_en.png
  slot1_sort-action_ru.png
  slot1_sort-action_uk.png
  slot2_one-tap_en.png
  ... (follow slot+slug+locale pattern)

store_assets/icon_512.png
store_assets/feature_graphic_1024x500.png
```

Figma / Inkscape source files (if produced): `store_assets/design_sources/` (tracked in git).

---

## 5. Acceptance Checklist (reviewer, before Phase 05)

- [ ] Icon passes greyscale test at 48 dp
- [ ] Icon contains no text
- [ ] Feature graphic headline legible at 1024 × 500 native resolution
- [ ] All 6 screenshot slots filled for all 3 locales (18 files)
- [ ] No screenshots show the Settings screen at slot 1
- [ ] Overlay text contrast ≥ WCAG AA (use browser dev-tools or Figma accessibility plugin)
- [ ] All files present in `store_assets/` with correct naming convention
```

**Verification:**

- `Glob` — `store_assets/design_brief.md` exists.
- `Grep` — `Slot 1` present in `store_assets/design_brief.md`.
- `Grep` — `Acceptance Checklist` present in `store_assets/design_brief.md`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Step 02.1 is `[x] done`.
- [ ] Dev log entry added: `.\scripts\add_to_dev_log.ps1 "store_assets/design_brief.md" "spec-dev" "Add designer brief for S0135"`.

---

## Handoff Notes to Next Phase

`store_assets/design_brief.md` is ready for handoff to the designer or owner. Phase 05 cannot proceed until the design brief deliverables (icon, feature graphic, 18 screenshots) are produced and placed in `store_assets/` per the naming convention above. Phases 03 and 04 (code) have no dependency on this phase.

---

## Rollback Plan

Revert phase commit — no build impact, no code change.
