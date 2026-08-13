# Phase 03 - Reference Generation

**Strategic spec:** [`../S0440_settings-declaration-docs.md`](../S0440_settings-declaration-docs.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Generate the canonical "Settings: what is what" reference pages (EN/RU/UK + gitignored noLegal) from manifest + annotations + per-flavor availability, and surface them from the public site.

---

## Prerequisites

- [ ] Phase 01 ✅ Done - manifest present.
- [ ] Phase 02 ✅ Done - annotations complete, coverage passes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/docs/render-settings-reference.ps1` | New | ≤ 280 |
| `docs/SETTINGS_REFERENCE.md` | New (generated) | n/a |
| `docs/SETTINGS_REFERENCE_RU.md` | New (generated) | n/a |
| `docs/SETTINGS_REFERENCE_UK.md` | New (generated) | n/a |
| `docs/SETTINGS_REFERENCE_noLegal.md` | New (generated, gitignored) | n/a |
| `index.html` | Modified | ≤ +15 |
| `index-ru.html` | Modified | ≤ +15 |
| `index-uk.html` | Modified | ≤ +15 |

> The three published `SETTINGS_REFERENCE*` files are generated artifacts (never hand-edited). The noLegal variant follows the `FEATURES_noLegal*` convention and stays gitignored (strategic §3.3).

---

## Steps

### Step 03.1 - Derive per-flavor media availability

**Files:** `scripts/docs/render-settings-reference.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> In the renderer, build a flavor -> supported-media-sections table by parsing the six `*SettingsSearchAvailabilityModule.kt` files under `app_v2/src/<flavor>/java/com/sza/fastmediasorter/di/`. Extract the media section string literals each module contributes. This keeps availability sourced from the same modules the app uses at runtime (no hardcoded duplicate). Non-media sections are always available per `SettingsSearchAvailability`.

**Verification:**

- `Glob` - `scripts/docs/render-settings-reference.ps1` exists.
- `Grep` - `SettingsSearchAvailabilityModule` referenced in the renderer.
- `Grep` - all flavor folder names `standard`, `lite`, `photos`, `legacy`, `noLegal`, `vr` handled.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification PASS. `render-settings-reference.ps1` parses each `*SettingsSearchAvailabilityModule.kt` via regex on `@SupportedMediaSection fun .. = "section"`; iterates all six flavor folders. Matrix: standard/legacy/vr/noLegal = images+video+audio+documents, lite = images+video, photos = images.

---

### Step 03.2 - Render the published reference (EN/RU/UK)

**Files:** `scripts/docs/render-settings-reference.ps1`, `docs/SETTINGS_REFERENCE.md`, `docs/SETTINGS_REFERENCE_RU.md`, `docs/SETTINGS_REFERENCE_UK.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Extend the renderer to merge manifest + annotations + availability into one Markdown document per locale, grouped by tab/section (General, Media -> Images/Video/Audio/Documents/Other, Playback, Operations/Destinations), one row per setting with its title and description, plus an availability note for media settings (which flavors include them). Exclude any media section unavailable in the standard family from the published files. Write the three published files. The Markdown body is fully generated - add a leading "generated, do not edit by hand" banner.

**Verification:**

- `Glob` - `docs/SETTINGS_REFERENCE.md`, `_RU.md`, `_UK.md` all exist.
- `Grep` - "generated" banner present at top of each file.
- `Grep` - at least one section header per tab present in `docs/SETTINGS_REFERENCE.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification PASS. EN/RU/UK rendered (8 section headers each), "generated, do not edit" banner present. Media sections carry availability notes (Images: Standard/Lite/Photos/Legacy; Video: Standard/Lite/Legacy; Audio + Documents: Standard/Legacy). LF + UTF-8 no-BOM output; re-render diff = SAME (idempotent).

---

### Step 03.3 - Render the gitignored noLegal variant

**Files:** `scripts/docs/render-settings-reference.ps1`, `docs/SETTINGS_REFERENCE_noLegal.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add a renderer mode that emits `docs/SETTINGS_REFERENCE_noLegal.md` covering the all-inclusive noLegal availability set (mirrors the `FEATURES_noLegal*` split). Confirm `docs/SETTINGS_REFERENCE_noLegal*` is matched by `.gitignore`; if not, add the pattern next to the existing `FEATURES_noLegal` ignore rule.

**Verification:**

- `Glob` - `docs/SETTINGS_REFERENCE_noLegal.md` exists.
- `Bash` - `git check-ignore docs/SETTINGS_REFERENCE_noLegal.md` returns the path (is ignored).

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification PASS. Renderer emits `docs/SETTINGS_REFERENCE_noLegal.md` (all 8 sections, all media available). Added `docs/SETTINGS_REFERENCE_noLegal.md` to `.gitignore` next to the `FEATURES_noLegal*` rules; `git check-ignore` confirms it is ignored.

---

### Step 03.4 - Link the reference from the public site

**Files:** `index.html`, `index-ru.html`, `index-uk.html`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add a link/entry to each locale of the public site pointing at the matching `SETTINGS_REFERENCE*` page, so users can reach the settings reference from the site. Link only - do not inline the full settings table into HTML. Match the existing site link style; no hardcoded inline colors.

**Verification:**

- `Grep` - `SETTINGS_REFERENCE` referenced in `index.html`, `index-ru.html`, `index-uk.html`.
- `Grep` - no inline `style="color:#` added by the edit.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification PASS. Added a "Settings Reference" card to the Technical Documentation grid in all three site files, linking the matching locale page (EN -> SETTINGS_REFERENCE.md, RU -> _RU.md, UK -> _UK.md). Matched existing card markup; no inline hex colors added.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Re-running the renderer produces no diff against the committed published files (idempotent).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The renderer is deterministic and idempotent: the Phase 04 gate re-renders and diffs to detect drift. The render command and the manifest verify test are the two halves the gate will call.

---

## Rollback Plan

Revert phase commit(s) - delete the renderer and generated reference files, revert the three site link edits. No code or data migration changed.
