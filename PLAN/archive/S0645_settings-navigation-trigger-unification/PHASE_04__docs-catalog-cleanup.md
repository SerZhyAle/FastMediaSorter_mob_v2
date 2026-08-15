# Phase 04 - Docs & catalog cleanup

**Strategic spec:** [`../S0645_settings-navigation-trigger-unification.md`](../S0645_settings-navigation-trigger-unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Document the navigation-mode etalon, regenerate the class catalog for the changed widget API, and record dev-log entries for every touched file.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ARCHITECTURE.md` | Modified | ≤ +12 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CHANGELOG.md` | Appended (via script) | n/a |

---

## Steps

### Step 04.1 - Document the nav-mode etalon

**Files:** `docs/ARCHITECTURE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> In the "UI Patterns - Trigger Row" section, document that `SettingsSelectionRow` has two trailing-glyph modes: value mode (default, chevron `>`, used by value-selection rows) and navigation mode (`app:ssr_navMode="true"`, real arrow `->`, no-stretch content, used by rows that open another screen/activity/dialog). State the cross-batch rule shared with S0644: arrow for navigation, chevron for value. Keep it to a short paragraph - no rationale prose duplication.

**Verification:**

- `Grep` - `ssr_navMode` referenced in `docs/ARCHITECTURE.md`.
- `Grep` - the "Trigger Row" section mentions both arrow (navigation) and chevron (value).

**Status:** `[ ]` not done

---

### Step 04.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl` (+ `.md`)
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to pick up the changed public API of `SettingsSelectionRow` (new `setNavigationMode`). The catalog index is a local gitignored artifact - regenerate, do not hand-edit.

**Verification:**

- Script exits 0.
- `Grep` - `SettingsSelectionRow` row in `dev/CATALOG/app_v2.jsonl` reflects the updated LOC/last-scan.

**Status:** `[ ]` not done

---

### Step 04.3 - Dev log + capability inventory

**Files:** `dev/CHANGELOG.md` (via script), `docs/ALL_FEATURES.jsonl` (via script)
**Depends on:** Step 04.2

**Prompt for developer:**

> Add one batched dev-log entry covering the S0645 change set (widget nav mode + four migrated/flagged nav rows + docs) via `.\scripts\add_to_dev_log.ps1`. Record the user-visible capability in `docs/ALL_FEATURES.jsonl` via `scripts/all_features/add.ps1` as a CHANGE: settings navigation rows unified to a single real-arrow etalon (no-stretch, arrow after text, icon/hint preserved), portrait + landscape. EN-only. `/spec-dev` emits this on the `Implemented` transition - do not double-write.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` has an entry mentioning S0645 nav-row unification.
- `Grep` - `docs/ALL_FEATURES.jsonl` has a record with `"spec":"S0645"`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `docs/ARCHITECTURE.md` documents the nav-mode etalon.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `dev/CHANGELOG.md` has the S0645 entry.
- [ ] No FEATURES showcase edit (owned by `/skill-release`).

---

## Handoff Notes to Next Phase

Final phase. Code complete - the change is a visual UI unification, so insert `Timber.d("S0645: …")` tags at the nav-row entry points, advance to `BlockNeedUserTest`, and device-verify the arrow etalon (no-stretch content, arrow after text, icons/hints preserved, both orientations) before `/spec-check` flips to `Verified`. See INDEX.md Completion Gate.

---

## Rollback Plan

Docs and catalog are non-functional - revert the doc edit; the catalog regenerates from source on the next sync.
