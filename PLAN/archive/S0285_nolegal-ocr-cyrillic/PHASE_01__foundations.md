# Phase 01 - Foundations

**Strategic spec:** [`../S0285_nolegal-ocr-cyrillic.md`](../S0285_nolegal-ocr-cyrillic.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Create the research output document `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md` as an empty container with taxonomy, the five quality scenarios, and seven blank category sections that subsequent phases populate.

---

## Prerequisites

- [ ] Strategic spec S0285 is `Approved` or `Tactical`.
- [ ] Folder `PLAN/S0156_nolegal-capability-surface-audit/` exists and contains the established research-doc pattern (`01_images.md`, `apk-install.md`, `internet-media-extraction.md`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md` | New | ≤ 250 |

---

## Steps

### Step 01.1 - Create research document with header block

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the file with the standard S0156 research-doc header: `# Research: OCR кириллицы`, `**Направление:** S0156 Столп E (OCR/translation branch из §6.6)`, `**Дата первого прохода:** 2026-05-21`, `**Статус:** Initial findings`, `**Source spec:** S0285`. Match the exact frontmatter style used in `01_images.md` and `apk-install.md`.

**Verification:**

- `Glob` - `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md` exists.
- `Grep` - `^# Research: OCR кириллицы` matches exactly once.
- `Grep` - `\*\*Source spec:\*\* S0285` matches exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+5 LOC). Dev log recorded.

---

### Step 01.2 - Append taxonomy section (two-axis verdict legend + 7 categories)

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Append section `## Таксономия и оси анализа`. List the seven categories from strategic §5.1 Столп A verbatim (GMS/Apache 2 modular OCR; Tesseract family; DL ONNX/TFLite ports; On-device LLM with vision; Closed-source SDK; Cloud OCR APIs; Sidecar-binary). For each category list the typical blocker profile in one bullet. Then state the verdict legend for both axes: axis A = `Accept` / `Conditional` / `Reject`; axis B = `Strong fit` / `Acceptable` / `Weak fit` / `Reject`.

**Verification:**

- `Grep` - `^## Таксономия и оси анализа` matches exactly once.
- `Grep -c "^### " path/to/file` returns 0 (no sub-sections yet) OR the section uses bullet list only.
- `Grep` - all seven category names listed (`GMS/Apache 2`, `Tesseract`, `ONNX/TFLite`, `On-device LLM`, `Closed-source SDK`, `Cloud OCR`, `Sidecar`).
- `Grep` - verdict legend tokens present: `Accept`, `Conditional`, `Reject`, `Strong fit`, `Acceptable`, `Weak fit`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 4/4 PASS (heading once; 7 categories present; verdict legend 2 lines). Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+21 LOC). Dev log recorded.

---

### Step 01.3 - Append the five quality scenarios (Столп D)

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Append section `## Пять сценариев оценки качества кириллицы (Столп D)`. List the five scenarios from strategic §5.1 Столп D verbatim: (1) clean printed text on white background; (2) photo of paper document with natural lighting and slight perspective; (3) photo of street signage with background noise; (4) handwritten text where applicable; (5) dense multi-column PDF page layout. For each scenario state one sentence describing what the scenario stresses (e.g. "baseline floor", "lighting + perspective tolerance"). For scenarios 2 and 5 explicitly note that RU and UK results must be tracked separately (Cyrillic UK has specific glyphs).

**Verification:**

- `Grep` - `^## Пять сценариев оценки качества кириллицы` matches exactly once.
- `Grep -E "^[0-9]+\."` returns 5 numbered scenario lines under that section.
- `Grep` - phrase `RU` AND `UK` appear in the scenario block.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS (heading once; 5 numbered scenarios; RU+UK mentioned in scenarios 2 and 5). Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+11 LOC). Dev log recorded.

---

### Step 01.4 - Append seven blank category sub-section headers

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 01.3

**Prompt for developer:**

> Append a top-level section `## Анализ по категориям`. Under it create seven empty third-level sub-sections, one per category, in this order: `### 1. GMS/Apache 2 модульный OCR (ML Kit family)`, `### 2. Tesseract-семейство`, `### 3. Deep-learning ONNX/TFLite порты`, `### 4. On-device LLM с vision`, `### 5. Closed-source SDK`, `### 6. Облачные OCR API`, `### 7. Sidecar-binary решения`. Under each sub-section write one placeholder line: `_(populated in Phase NN — see tactical plan)_` where NN is the phase that fills that category (02 for 1+2, 03 for 3+4, 04 for 5+6, 05 for 7).

**Verification:**

- `Grep` - `^## Анализ по категориям` matches exactly once.
- `Grep -c "^### [1-7]\."` returns exactly 7.
- `Grep -c "_(populated in Phase"` returns exactly 7.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS (section once; 7 numbered sub-headers; 7 placeholders). Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+30 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `Glob` confirms `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md` exists with ≤ 250 lines.
- [ ] All four section headers (`## Таксономия`, `## Пять сценариев`, `## Анализ по категориям`, and the seven category sub-headers) are in place.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phases 02–05 fill the seven empty category sub-sections established in step 01.4. Each phase MUST append content under the placeholder line (do not rewrite the sub-section header). Phase 06 reads all populated sub-sections to produce the cross-axis matrix.

---

## Rollback Plan

Delete `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md` - no other artifacts produced in this phase. No data migration, no user-facing surface, no code change.
