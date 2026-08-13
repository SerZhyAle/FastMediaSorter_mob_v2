# Phase 04 — Docs / Catalog cleanup

**Strategic spec:** [`../S0142_ui-settings-behaviour-group-regroup.md`](../S0142_ui-settings-behaviour-group-regroup.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none — final phase
**Steps done:** 3 / 3
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Reflect the change in user docs (trilingual), regenerate the class catalog for `app_v2`, and consolidate dev log entries. No production code changes.

---

## Prerequisites

- [ ] Phases 01–03 ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CATALOG/app_v2.md` | Regenerated | n/a |

---

## Steps

### Step 04.1 — Update FEATURES (trilingual)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In the settings / playback feature area, add one concise bullet (or amend an existing one) noting that the Playback › Behaviour settings group now visually separates the link auto-download items and the camera-capture items into named sub-sections, and that "Saved authorizations" is a clickable sub-screen entry with an inline help tooltip. Mirror the wording in `FEATURES_RU.md` and `FEATURES_UK.md`. Do not duplicate an existing bullet — extend if one already covers the Behaviour group. Use `..` not `...`; keep `ё`/`Ё` in Russian.

**Verification:**

- `Grep -n` — the new/amended bullet text present in all three FEATURES files.
- No duplicated bullet describing the same Behaviour-group reorganization.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 2/2 PASS. Added "Behaviour group sub-sections" bullet to `docs/FEATURES.md`, `_RU.md`, `_UK.md` after the existing "Settings groups reorganized" entry. Dev log recorded.

---

### Step 04.2 — Regenerate the app_v2 class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. `PlaybackSettingsFragment.kt` line count changed slightly; no new classes were introduced, so no manual `role`/`status` edit is needed. Commit the regenerated `.jsonl` + `.md` together with the code change.

**Verification:**

- `git status` shows `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` modified (or unchanged if scan produced no diff — acceptable).
- No new entries with empty `role`/`status`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 2/2 PASS. `scan.ps1` + `render.ps1` for app_v2 ran clean (995 files); no catalog diff produced (no new classes; LOC delta below tracking threshold). Nothing to commit for catalog.

---

### Step 04.3 — Dev log + final strings audit

**Files:** (dev log only — no source files)
**Depends on:** Step 04.2

**Prompt for developer:**

> Ensure `.\scripts\add_to_dev_log.ps1` has an entry for every file modified across Phases 01–04 (3× `strings.xml`, 2× layout, `PlaybackSettingsFragment.kt`, 3× FEATURES, catalog files) if not already logged per-phase. Re-run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_subcategory_"` and `... -KeyPrefix "tooltip_saved_authorizations"` — both must exit 0.

**Verification:**

- `dev/CHANGELOG.md` contains entries for all modified files in this spec.
- `scripts/check_strings_localized.ps1` exits 0 for both prefixes.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 2/2 PASS. Dev log entries present for all S0142-modified files (3× strings.xml, 2× layout, PlaybackSettingsFragment.kt, 3× FEATURES). `check_strings_localized.ps1` OK for `settings_subcategory_` and `tooltip_saved_authorizations`.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated.
- [x] `dev/CATALOG/app_v2.jsonl` + `.md` regenerated (scan/render ran clean; no diff).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `scripts/check_strings_localized.ps1` passes for both prefixes.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Next: `/spec-check S0142`.

---

## Rollback Plan

Revert phase commit — docs/catalog only, no production code.
