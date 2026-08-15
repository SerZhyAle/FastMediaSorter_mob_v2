# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S0352_widget-random-photo-frame.md`](../S0352_widget-random-photo-frame.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all previous phases
**Blocks:** final audit only
**Steps done:** 3 / 3
**Started:** 2026-06-04
**Completed:** 2026-06-04

> **Step Log:**
> - 2026-06-04 - 04.1 PASS. Added aligned `Random Photo Frame` / `Случайный кадр` / `Випадковий кадр` bullets to the widget section in `docs/FEATURES*.md`, with no markdown diagnostics.
> - 2026-06-04 - 04.2 PASS. Ran `scripts/catalog_sync.ps1 -Module app_v2`; grep verified `RandomPhotoFrameConfigActivity` and `RandomPhotoFrameWidgetProvider` in `dev/CATALOG/app_v2.jsonl`.
> - 2026-06-04 - 04.3 PASS. Source/docs/scripts grep returned zero `TODO(phase-0[1-4])` hits, and every S0352 touched file is present in `dev/CHANGELOG.md`.
> - 2026-06-04 - Phase closure PASS. `docs/FEATURES*.md` updated, `dev/CATALOG/app_v2.jsonl` regenerated, `TODO(phase-04)` source/docs/scripts grep returned zero hits, and dev-log coverage is complete.

---

## Objective

Record the new widget in user-facing feature docs, regenerate the app catalog for the new classes, and close mechanical cleanup before the audit.

---

## Prerequisites

- [x] Phases 01, 02, and 03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |

---

## Steps

### Step 04.1 - Update the trilingual feature inventory

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** all previous phases

**Prompt for developer:**

> Add one concise bullet to the Smart Widgets / home-screen widgets section in EN/RU/UK describing the new Random Photo Frame widget, its selected-resource configuration, and the fullscreen-open tap behavior. Keep the three bullets semantically aligned.

**Verification:**

- `Grep` - a `Random Photo Frame` / `Случайный кадр` / `Випадковий кадр` bullet exists in all three docs.

**Status:** `[x]` done

---

### Step 04.2 - Regenerate the app catalog for the new widget classes

**Files:** `dev/CATALOG/app_v2.jsonl` (generated), `dev/CATALOG/app_v2.md` (generated)
**Depends on:** all previous phases

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` after the new widget classes are in place. Do not hand-edit catalog outputs.

**Verification:**

- `Grep` - `RandomPhotoFrameWidgetProvider` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `RandomPhotoFrameConfigActivity` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

---

### Step 04.3 - Close mechanical cleanup before audit

**Files:** none beyond touched artifacts above
**Depends on:** Step 04.1, Step 04.2

**Prompt for developer:**

> Ensure every modified file from Phases 01-04 has a dev-log entry via `scripts/post-change.ps1`, clear any temporary `TODO(phase-0X)` markers, and leave the spec ready for `/spec-check S0352`.

**Verification:**

- `Grep` - `TODO\(phase-0[1-4]\)` returns zero hits.
- `Grep` - every file touched in the tactical plan appears in `dev/CHANGELOG.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in the tactical plan.

---

## Handoff Notes to Next Phase

Final phase - see `INDEX.md` Completion Gate.

---

## Rollback Plan

Revert the feature-doc bullets and rerun catalog sync after reverting widget classes.