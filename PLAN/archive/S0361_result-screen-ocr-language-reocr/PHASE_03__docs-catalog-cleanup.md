# Phase 03 - Docs & catalog cleanup

**Strategic spec:** [`../S0361_result-screen-ocr-language-reocr.md`](../S0361_result-screen-ocr-language-reocr.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Land the user-facing FEATURES note, regenerate the class catalog, and record dev-log entries.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |

---

## Steps

### Step 03.1 - Update FEATURES trilingual

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Use `/doc-update`. In the "Offline OCR & Translation" section, extend the existing OCR/result entry with one sentence: changing the OCR source language on the camera result screen re-runs recognition over the already-captured (cropped) image without re-shooting. Mirror EN/RU/UK; apply author style (`..`, `ё`/`Ё`). Do not create a new feature bullet - augment the existing one.

**Verification:**

- `Grep` - the new sentence (e.g. "re-runs recognition" / "повторно распознаёт" / "повторно розпізнає") present in each of `docs/FEATURES.md`, `_RU.md`, `_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 1/1 PASS (new sentence present in all 3 FEATURES files). Augmented existing Photo OCR entry.

---

### Step 03.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md` (gitignored, regenerated)
**Depends on:** Step 03.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` so the modified `CameraOcrFlowManager` / `CameraOcrTranslateActivity` records refresh.

**Verification:**

- Command exit code 0 (`expected: 0 | actual: <...>`).

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - catalog scan+render ran via close-and-log.ps1 (scan 39.0s, render 2.2s). expected: 0 | actual: 0.

---

### Step 03.3 - Dev log entries

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 03.2

**Prompt for developer:**

> Add one dev-log line per modified code/layout/doc file via `.\scripts\add_to_dev_log.ps1` (FlowManager, Activity, dialog layout, three FEATURES files) if not already recorded during their phases. Also append a functionality-log line: `.\scripts\add_to_functionality_log.ps1 -Id S0361 -Op ADD -Description "Result screen OCR language re-runs recognition over the retained image"`.

**Verification:**

- `Grep` - `S0361` present in `dev/CHANGELOG.md`.
- `Grep` - `S0361` present in `dev/FUNCTIONALITY.log`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - 7 dev-log lines + functionality ADD recorded via close-and-log.ps1. S0361 present in dev/CHANGELOG.md (7×) and dev/FUNCTIONALITY.log.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` carry the new sentence.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0361`.

---

## Rollback Plan

Revert doc edits; catalog and logs are regenerated artifacts - no rollback needed.
