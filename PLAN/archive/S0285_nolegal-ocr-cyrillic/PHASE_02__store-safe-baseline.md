# Phase 02 - Store-Safe Baseline (ML Kit family + Tesseract)

**Strategic spec:** [`../S0285_nolegal-ocr-cyrillic.md`](../S0285_nolegal-ocr-cyrillic.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 5 / 5
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Fill the two store-safe categories of the research document (ML Kit family and Tesseract family) with concrete candidate analysis, focusing on identifying at least one store-safe upgrade path for axis A or producing an explicit negative result.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done; `ocr-cyrillic.md` contains the seven empty category sub-sections.
- [ ] Resolves strategic §6 Open items: 1 (ML Kit Cyrillic module), 2 (Tesseract LSTM fresh models).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md` | Modified | ≤ 500 |

---

## Steps

### Step 02.1 - Research ML Kit Cyrillic-specific text recognition modules

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Use `WebSearch` and `WebFetch` against `developers.google.com/ml-kit`, the ML Kit GitHub `googlesamples/mlkit`, and recent ML Kit release notes (2024–2026). Determine: (a) does `com.google.mlkit:text-recognition-cyrillic` (or equivalent Cyrillic-specific module) exist as a standalone Apache 2 dependency? (b) if yes, what is its API surface, model size, GMS dependency, minSdk; (c) if no, what is Google's current public position on Cyrillic OCR support inside the base `text-recognition:16.0.1` module. Record findings in the research document under `### 1. GMS/Apache 2 модульный OCR (ML Kit family)`.

**Verification:**

- `Grep` - text under `### 1. GMS/Apache 2` no longer contains `_(populated in Phase`.
- `Grep` - entry block contains at least one of the substrings: `com.google.mlkit:text-recognition-cyrillic`, `not published`, `not available as standalone`, `tracked in mlkit issue`.
- `Grep` - explicit verdict line `**Ось A:** ` is present with one of `Accept`, `Conditional`, `Reject`.
- `Grep` - explicit verdict line `**Ось B:** ` is present with one of `Strong fit`, `Acceptable`, `Weak fit`, `Reject` or `n/a (already covered by axis A)`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 4/4 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+14 LOC). Dev log recorded.

---

### Step 02.2 - Research Tesseract 5.x LSTM model availability for Cyrillic

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Use `WebSearch` against `github.com/tesseract-ocr/tessdata`, `github.com/tesseract-ocr/tessdata_best`, `github.com/tesseract-ocr/tessdata_fast`, and the Tesseract 5.x release notes. Identify: (a) latest Tesseract 5.x LSTM `rus.traineddata` and `ukr.traineddata` and their release dates; (b) ABI compatibility with `cz.adaptech:tesseract4android:4.8.0` (currently engine v4.x — does it accept Tesseract 5.x trained models or requires 4.x-trained?); (c) any community-trained Cyrillic models on GitHub under Apache 2 / public domain with documented accuracy gain over default models. Append findings under `### 2. Tesseract-семейство`.

**Verification:**

- `Grep` - text under `### 2. Tesseract-семейство` no longer contains `_(populated in Phase`.
- `Grep` - entry block contains both `rus.traineddata` and `ukr.traineddata`.
- `Grep` - explicit ABI compatibility note (`compatible with tesseract4android 4.8.0` OR `requires engine upgrade` OR equivalent phrasing).
- `Grep` - explicit `**Ось A:**` and `**Ось B:**` verdict lines.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 4/4 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+15 LOC). Dev log recorded.

---

### Step 02.3 - Apply Столп D quality scenarios to ML Kit and Tesseract candidates

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> For each candidate produced in steps 02.1 and 02.2, append a `**Качество на сценариях Столпа D:**` block. Use public benchmarks, GitHub issues, academic papers, and community reports — DO NOT run own benchmarks. For each of the five scenarios (clean print / paper photo / signage / handwriting / dense PDF), record one of: `strong`, `acceptable`, `weak`, `not applicable`, `verdict requires device-test on implementation`. Track RU and UK separately for scenarios 2 and 5. Always cite the source (URL or paper title) for each claimed performance characteristic.

**Verification:**

- `Grep -c '\*\*Качество на сценариях Столпа D:\*\*'` returns at least 2 (one per category section, possibly more if multiple candidates per category).
- `Grep` - the five scenario labels (`clean print`, `paper photo`, `signage`, `handwriting`, `dense PDF`, or their Russian equivalents) are referenced in each quality block.
- `Grep` - at least one citation URL (`https://`) appears under each `Качество` block.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+18 LOC). Dev log recorded.

---

### Step 02.4 - Cross-check against project's APK size and runtime budgets

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 02.3

**Prompt for developer:**

> For each store-safe candidate identified in steps 02.1–02.3, append a `**Бюджеты:**` block with: APK size delta (concrete MB number from public artifact sizes), cold start delta estimate (use baseline-устройство Quest 3 and mid-range phone references from strategic §3.2), runtime memory peak estimate. Compare against the strategic budgets: STANDARD APK +15 MB cap, cold start +1.5 sec cap, memory 2× current peak cap. Mark any candidate that exceeds a cap as `Conditional` or `Reject` on axis A with explicit reason.

**Verification:**

- `Grep -c '\*\*Бюджеты:\*\*'` returns at least 2.
- `Grep` - each `**Бюджеты:**` block contains explicit MB number AND latency estimate.
- `Grep` - any candidate marked `Conditional` or `Reject` for budget reasons has a "Reason: " line referencing the specific cap exceeded.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+13 LOC). Dev log recorded.

---

### Step 02.5 - Add phase summary at end of categories 1+2

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 02.4

**Prompt for developer:**

> Append a `**Резюме фазы 02:**` paragraph at the end of category section 2 (after the Tesseract analysis). Three sentences max: (1) which store-safe upgrade path was identified, if any (axis A `Accept` or `Conditional` candidate); (2) which Tesseract enhancement is worth a follow-up implementation spec; (3) what remains for axis B handoff to phases 03–05 (e.g. "categories 1+2 fully analyzed; no axis B candidates here — all blockers are size/license-free, so move to axis B in next categories").

**Verification:**

- `Grep` - `\*\*Резюме фазы 02:\*\*` matches exactly once.
- `Grep` - the summary paragraph contains either an explicit "axis A `Accept`" / "axis A `Conditional`" candidate name OR explicit "axis A negative result" wording.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+1 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Both category sections (1 and 2) no longer contain the `_(populated in Phase…)_` placeholder.
- [ ] Both category sections contain explicit verdict lines for axis A and axis B.
- [ ] At least one citation URL is present in each category's quality block.
- [ ] Dev log entry added for the modified file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phases 03–05 follow the same template established here: candidate listing → quality on Столп D scenarios → budget check → axis A/B verdict → phase summary. The verdict block format MUST stay consistent across phases for phase 06 to build the cross-axis matrix mechanically.

---

## Rollback Plan

Restore the placeholder line `_(populated in Phase 02 — see tactical plan)_` under category sub-sections 1 and 2; remove all appended content. No other artifacts affected.
