# Phase 03 - Docs & catalog cleanup

**Strategic spec:** [`../S0671_standard-mediaprojection-capture-suite.md`](../S0671_standard-mediaprojection-capture-suite.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-06-24
**Completed:** 2026-06-25

---

## Objective

Record the shipped capability, sync settings docs and the class catalog, and surface the manual Play release obligations. No behavior code here.

---

## Prerequisites

- [x] Phase 01 ✅ Done.
- [x] Phase 02 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (append) | ≤ +2 records |
| `docs/settings/settings-manifest.json` | Regenerated | n/a |
| `docs/SETTINGS_REFERENCE*.md` | Regenerated | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |

> `docs/FEATURES*.md` are NOT edited here - the public showcase is `/skill-release`-owned (strategic §8).

---

## Steps

### Step 03.1 - Record the capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a record via `pwsh -NoProfile -File scripts/all_features/add.ps1` (EN-only) for the Play-standard screen-capture suite: in-app screenshot via system consent (MediaProjection), with clipboard copy, drawing editor, on-device OCR-translate, send-to-recipients, and configurable save destination with fallback chain. Validate with `scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - a new record mentioning screen capture / MediaProjection exists in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 03.2 - Settings doc sync (Rule 22)

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json`
**Depends on:** Step 03.1

**Prompt for developer:**

> Enabling capture in `standard` changes which screenshot-related settings are visible in the Play build, and Phase 02 added a disclosure-accepted flag. Regenerate the settings manifest + reference and update annotations so the docs match the shipped standard surface. Run the project's settings-doc generator (see `scripts/quality/assert-settings-doc-sync.ps1` for the expected outputs).

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.

**Status:** `[x]` done

---

### Step 03.3 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl` (+ `.md`)
**Depends on:** Step 03.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to regenerate the catalog after the Phase 02 class changes. Fill `role`+`status` for any new class via `set.ps1` if prompted.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*ScreenCaptureConsentActivity*"` returns the class.

**Status:** `[x]` done

---

### Step 03.4 - Surface the manual Play release gate

**Files:** `PLAN/S0671_standard-mediaprojection-capture-suite/INDEX.md` (Completion Gate already lists it - confirm, do not invent new PLAN text)
**Depends on:** Step 03.3

**Prompt for developer:**

> This step is a checkpoint, not a code edit. Confirm the INDEX Completion Gate "Manual Play release gate" item is present and accurate (FGS mediaProjection declaration + demo video, privacy policy, Data safety). Report to the owner that the standard RELEASE build must not be published until those are complete. No source change.

**Verification:**

- `Grep` - "Manual Play release gate" present in `INDEX.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- [x] `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.
- [x] Dev log entry added for every modified file.
- [x] Catalog regenerated.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this, advance the ticket to `BlockNeedUserTest` (device verification of the disclosure -> consent -> save flow on a `standard` debug build), then `/spec-check S0671` once verified. Standard RELEASE publish is gated on the manual Play obligations.

---

## Rollback Plan

Revert the phase commit - docs/catalog regen only, no runtime surface. Re-run the generators to restore prior state.
