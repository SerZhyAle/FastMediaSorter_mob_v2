# Phase 01 — Designer Prompt

**Strategic spec:** [`../S0034_resource-icons-system.md`](../S0034_resource-icons-system.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 03
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Produce a complete, self-contained designer brief for the 50-icon vector set (5 themed groups) plus a reference document the team can hand to a designer. No code in this phase.

---

## Prerequisites

- [ ] Strategic §6 research items are Resolved (all four marked Resolved as of 2026-04-29).
- [ ] Working tree clean or on feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0034_resource-icons-system/DESIGNER_PROMPT.md` | New | ≤ 500 |
| `PLAN/S0034_resource-icons-system/DESIGNER_PROMPT_RU.md` | New | ≤ 500 |
| `PLAN/S0034_resource-icons-system/ICON_INVENTORY.md` | New | ≤ 200 |

---

## Steps

### Step 01.1 — Author English designer prompt

**Files:** `PLAN/S0034_resource-icons-system/DESIGNER_PROMPT.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a single Markdown brief for an external designer covering all five themed icon sets. The brief must contain, in order: (1) Project context — Android media browser app, icons appear in resource list (grid/list cells, ~64dp). (2) Total deliverable count: 50 SVG files = 10 music + 10 video + 10 image + 10 docs + 20 other. (3) Per-set theme guidance: music = treble clefs / notes / instruments; video = film reels / cinema / Hollywood motifs; image = houses / flowers / cars / nature; documents = books / folders / business cards; other = abstract spheres / cubes / diamonds / geometric shapes. (4) Technical specs: SVG, viewBox 24×24, single-color paths (fillColor `#000000` so the runtime can tint), no embedded raster, no filters/gradients, transparent background, line weight tuned for 24dp legibility, anti-alias safe at 16dp. (5) Naming convention output: `ico_NN_NNN.svg` where NN = set id (`01`=music, `02`=video, `03`=image, `04`=docs, `05`=other) and NNN = 1-based ordinal within set. (6) Acceptance criteria: every icon must remain recognisable at 16dp, share visual weight (similar bounding-box density), be tintable as a single path. (7) Out of scope: connection-type overlays (local/SMB/SFTP/FTP/cloud) — those are existing app assets and are NOT part of this delivery. (8) Delivery format: zip archive with five subfolders matching set ids.

**Verification:**

- `Glob` — `PLAN/S0034_resource-icons-system/DESIGNER_PROMPT.md` exists.
- `Grep` — `viewBox 24` matches in that file.
- `Grep` — `ico_NN_NNN` (literal placeholder) matches in that file.
- `Grep` — all five set names present: `music`, `video`, `image`, `docs`, `other`.

**Status:** `[ ]` not done

---

### Step 01.2 — Author Russian mirror

**Files:** `PLAN/S0034_resource-icons-system/DESIGNER_PROMPT_RU.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Translate Step 01.1 brief into Russian, preserving structure section-for-section. Use `..` (two dots) instead of `...` and always `ё`/`Ё` where grammatically required (`всё`, `ещё`). Keep all technical tokens (`viewBox`, `SVG`, `fillColor`, file naming pattern) verbatim in English. The Russian version is the canonical artefact for the owner; English version is the canonical artefact for the designer.

**Verification:**

- `Glob` — `PLAN/S0034_resource-icons-system/DESIGNER_PROMPT_RU.md` exists.
- `Grep` — `viewBox 24` matches (technical token kept in English).
- `Grep` — Russian text indicator: `файл` or `иконк` matches at least once.

**Status:** `[ ]` not done

---

### Step 01.3 — Author icon inventory checklist

**Files:** `PLAN/S0034_resource-icons-system/ICON_INVENTORY.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a tracking sheet that lists every expected file by its target name (`ico_01_001.svg` through `ico_05_020.svg`) grouped by set, with columns: filename, theme hint (e.g. "treble clef", "film reel"), received status checkbox, accepted status checkbox, notes. The hint column may be left blank initially — designer fills it on delivery. This file is the authoritative checklist used in Phase 03 to confirm completeness before embedding into the project.

**Verification:**

- `Glob` — `PLAN/S0034_resource-icons-system/ICON_INVENTORY.md` exists.
- `Grep` — `ico_01_001.svg` matches.
- `Grep` — `ico_05_020.svg` matches.
- `Grep` — at least 50 occurrences of the pattern `ico_\d{2}_\d{3}\.svg` (one per icon).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] No build required — this phase is documentation-only.
- [ ] Dev log entry added for all three new files via `.\scripts\add_to_dev_log.ps1`.
- [ ] Owner has copy-pasted `DESIGNER_PROMPT.md` to the chosen designer / agency.
- [ ] Spec catalog status flipped to `BlockExternal` until icon assets arrive: `pwsh -File scripts/spec_catalog/update.ps1 -Id S0034 -Status BlockExternal`.

---

## Handoff Notes to Next Phase

Phase 02 (data foundations) does not depend on receiving the icons — it can run in parallel with the designer's work. Phase 03 cannot start embedding assets until the designer delivers all 50 SVGs and they are accepted in `ICON_INVENTORY.md`.

---

## Rollback Plan

Documentation-only phase — discard the three files if scope changes; no code or schema affected.
