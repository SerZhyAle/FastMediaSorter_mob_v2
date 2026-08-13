# Phase 07 - docs-catalog-cleanup

**Strategic spec:** [`../S0288_nolegal-paddleocr-paddlelite-bundle.md`](../S0288_nolegal-paddleocr-paddlelite-bundle.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all - final phase
**Blocks:** none - end of tactical plan
**Steps done:** 4 / 4
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Finalize the ticket S0288. Update user-invisible sideload-only documentation `docs/FEATURES_noLegal.md`, register all changed files in `dev/CHANGELOG.md`, run catalogue sync tools, and invoke the spec check script to transition the strategic spec status.

---

## Prerequisites

- [ ] All prior phases (01 to 06) are ✅ Done.
- [ ] Working tree is clean on the development branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES_noLegal.md` | Modified / New | ≤ 150 |
| `dev/CHANGELOG.md` | Modified | ≤ 50 |

---

## Steps

### Step 07.1 - Update sideload-only feature documentation docs/FEATURES_noLegal.md

**Files:** `docs/FEATURES_noLegal.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add detail-rich descriptions of the new offline Cyrillic/East Slavic OCR capabilities powered by PaddleOCR PP-OCRv5 and Paddle-Lite to `docs/FEATURES_noLegal.md` (or create it if absent).
> Describe compilation parameters, JNI packaging structure, download links for optimized `.nb` model files, and SHA-256 validation hashes.
> Do NOT update public `docs/FEATURES.md` or any locale variants since this feature is restricted strictly to the sideload-only `noLegal` build.

**Verification:**

- `Glob` - `docs/FEATURES_noLegal.md` exists.
- `Grep` - `PaddleOCR` or `Paddle-Lite` matches.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: `docs/FEATURES_noLegal.md`, `docs/FEATURES_noLegal_RU.md`, `docs/FEATURES_noLegal_UK.md` (extended scope per CLAUDE.md noLegal trilingual policy). Expected: file exists + PaddleOCR|Paddle-Lite ≥ 1 hit | actual: file exists, 10 hits in EN file. Trilingual parity preserved across RU/UK mirrors.

---

### Step 07.2 - Record changes in dev/CHANGELOG.md

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 07.1

**Prompt for developer:**

> Update `dev/CHANGELOG.md` by appending entries for all modified and newly introduced files.
> Write clear, imperative, and professional descriptions for each file delta.

**Verification:**

- `Grep` - `S0288` matches inside `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 1/1 PASS. Expected: `S0288` ≥ 1 hit in `dev/CHANGELOG.md` | actual: 46 hits. Entries were recorded incrementally throughout Phases 01-07 via `scripts/post-change.ps1` which invokes `scripts/add_to_dev_log.ps1` per modified file; no separate append needed.

---

### Step 07.3 - Regenerate classes catalog and run catalog sync

**Files:** None
**Depends on:** Step 07.2

**Prompt for developer:**

> Synchronize and regenerate the classes catalog database since public and internal APIs have changed.
> Run catalog sync via:
> `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`

**Verification:**

- `VerificationPredicate` - catalog sync script runs and exits with code 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 1/1 PASS. Expected: exit 0 | actual: exit 0. `dev/CATALOG/app_v2.jsonl` scanned 1170 files / 1420 records; `dev/CATALOG/app_v2.md` rendered with 1420 records.

---

### Step 07.4 - Finalize spec status and stage on-device acceptance probes

**Files:** None
**Depends on:** Step 07.3

**Prompt for developer:**

> Strategic §11 acceptance criteria require on-device verification (engine selector works, PaddleOCR recognizes complex photos, STANDARD unaffected). Per CLAUDE.md "Debug Verification Tags", insert one `Timber.d("S0288: <entry>")` probe at the entry point of each changed user-visible flow, build both `standardDebug` and `noLegalDebug` to confirm tags compile, then flip strategic status to `BlockNeedUserTest` via `scripts/spec_catalog/close-and-log.ps1`.
>
> Original phase prompt called `update.ps1 -Status Tactical` — that demoted the spec; corrected here in line with the canonical `/spec-dev` finalization protocol.

**Verification:**

- `Grep` - exactly 5 `Timber.d("S0288:` probes across `.kt` source.
- `Build` - `assembleStandardDebug` exit 0.
- `Build` - `assembleNoLegalDebug` exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Tags inserted at: `OtherMediaSettingsFragment.spinnerOcrEngineType.onItemSelected` (Phase 02 settings UI), `TranslationManager.recognizeText` (Phase 06 text routing), `TranslationManager.recognizeAndTranslateBlocks` (Phase 06 block routing), `PlayerImageTranslationManager.translateCurrentImage` (Phase 06 progress bar UX), `PaddleOcrEngine.recognizeTextBlocks` (Phases 03-05 inference entry). Builds: standardDebug 2.60.5212.132 exit 0, noLegalDebug 2.60.5212.133 exit 0. Status transition handled via `close-and-log.ps1` in the phase-close ritual.

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` exit 0 + `assembleNoLegalDebug` exit 0 (after S0288 probe insertion).
- [x] Dev log entries added (3 trilingual docs via post-change Doc closure; code probes via close-and-log batch).
- [x] Catalogue sync runs successfully - `dev/CATALOG/app_v2.{jsonl,md}` regenerated (1170 files / 1420 records).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commits. Revert doc changes.
