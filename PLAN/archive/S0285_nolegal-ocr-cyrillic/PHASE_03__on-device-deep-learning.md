# Phase 03 - On-Device Deep Learning (ONNX/TFLite + LLM with vision)

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

Fill categories 3 (DL ONNX/TFLite ports) and 4 (on-device LLM with vision) of the research document with concrete candidate analysis, snapshot-fixed to 2026-05 because both technologies evolve rapidly.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Resolves strategic §6 Open items: 3 (PaddleOCR/EasyOCR ONNX port), 4 (on-device LLM applicability).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md` | Modified | ≤ 700 |

---

## Steps

### Step 03.1 - Research PaddleOCR ONNX/TFLite Android ports

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Use `WebSearch` against `github.com/PaddlePaddle/PaddleOCR`, `paddlepaddle/Paddle-Lite`, and `github.com/topics/paddleocr-android`. Identify: (a) availability of pre-built PaddleOCR Cyrillic detection + recognition models in ONNX or TFLite format; (b) total model file sizes (`det`, `rec`, `cls`) for Cyrillic; (c) inference latency on ARM64 CPU and via NNAPI from published benchmarks; (d) Apache 2 license confirmation for both the runtime and the model files; (e) any community-issues about Cyrillic accuracy regression vs Latin/Chinese. Append findings under `### 3. Deep-learning ONNX/TFLite порты`.

**Verification:**

- `Grep` - text under `### 3. Deep-learning ONNX/TFLite порты` no longer contains `_(populated in Phase`.
- `Grep` - entry block contains all three model component names (`det`, `rec`, `cls`) AND total model size in MB.
- `Grep` - explicit license confirmation line `License: Apache 2` OR `License: <other>`.
- `Grep` - explicit `**Ось A:**` and `**Ось B:**` verdict lines.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 4/4 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+12 LOC). Dev log recorded.

---

### Step 03.2 - Research EasyOCR and alternative ONNX ports for Cyrillic

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Use `WebSearch` against `github.com/JaidedAI/EasyOCR`, `github.com/topics/easyocr-android`, and the `onnx-models` registry. Identify: (a) EasyOCR Cyrillic detection model availability in ONNX format; (b) alternative dbnet+CRNN community ports specifically trained for Cyrillic; (c) any TROCR (transformer-based OCR) Cyrillic-fine-tuned model card on Hugging Face with Apache 2 / MIT license. Append findings under category 3 alongside PaddleOCR analysis.

**Verification:**

- `Grep` - category 3 contains at least two candidate sub-blocks (PaddleOCR + EasyOCR or equivalent).
- `Grep` - each candidate sub-block has its own model size, license, and verdict lines.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+23 LOC). Dev log recorded.

---

### Step 03.3 - Research on-device LLM with vision (Gemini Nano, Phi-3.5-vision, others)

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Use `WebSearch` against `developer.android.com/ai/gemini-nano`, `huggingface.co/microsoft/Phi-3.5-vision-instruct`, `aihub.qualcomm.com`, and `mediatek.com/genio-ai`. Identify per provider: (a) Cyrillic OCR capability — documented or undocumented; (b) device-support matrix (which Quest / which phones); (c) license and AICore-style distribution mechanics; (d) Apache 2 / MIT / proprietary classification; (e) explicit Google Play policy stance on `AICore` model use (does it count as dynamic code loading?). Append under `### 4. On-device LLM с vision`. Mark every verdict with explicit snapshot date `_snapshot: 2026-05_` per strategic ADR-3.

**Verification:**

- `Grep` - text under `### 4. On-device LLM с vision` no longer contains `_(populated in Phase`.
- `Grep` - at least two LLM candidates listed (e.g. Gemini Nano + Phi-3.5-vision).
- `Grep` - each candidate entry contains `_snapshot: 2026-05_` marker.
- `Grep` - explicit `**Ось A:**` and `**Ось B:**` verdict lines per candidate.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 4/4 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+28 LOC). Dev log recorded.

---

### Step 03.4 - Apply Столп D quality scenarios + budget check to all on-device DL candidates

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 03.3

**Prompt for developer:**

> For every candidate listed in steps 03.1–03.3, append `**Качество на сценариях Столпа D:**` and `**Бюджеты:**` blocks identical in format to phase 02's blocks. Pay particular attention to: (a) RU vs UK separation in handwriting and dense PDF scenarios; (b) APK size delta when models are bundled (likely +50 MB or more — verify whether axis A is even feasible without download-after-install mechanism); (c) cold start cost (model load latency on first inference). Apply strategic §3.2 budgets ruthlessly: any model bundle >15 MB defaults to `Reject` on axis A unless a clean download-after-install path exists (see strategic open question 10).

**Verification:**

- `Grep -c '\*\*Качество на сценариях Столпа D:\*\*'` increases by at least 4 (one per candidate from steps 03.1–03.3).
- `Grep -c '\*\*Бюджеты:\*\*'` increases by at least 4.
- `Grep` - at least one candidate has axis A verdict `Reject` with reason `APK size cap` OR `Conditional` with reason `requires download-after-install path (see strategic Q10)`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+32 LOC). Dev log recorded.

---

### Step 03.5 - Add phase summary at end of category 4

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 03.4

**Prompt for developer:**

> Append `**Резюме фазы 03:**` paragraph at the end of category section 4. Three sentences max: (1) which on-device DL candidate is the leading axis B Strong fit candidate; (2) which on-device LLM is realistically usable today vs requires re-evaluation in 6–12 months; (3) explicit statement of whether any on-device DL candidate could legitimately pass axis A given the dynamic code loading constraint.

**Verification:**

- `Grep` - `\*\*Резюме фазы 03:\*\*` matches exactly once.
- `Grep` - summary contains explicit candidate names and verdict references.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+1 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Both category sections (3 and 4) populated without placeholder; each candidate has Quality + Budget + Verdict blocks.
- [x] Every LLM candidate carries `_snapshot: 2026-05_` per ADR-3.
- [x] At least one citation URL appears under each quality block.
- [x] Dev log entry added for the modified file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 04 covers categories 5 (Closed-source SDK) and 6 (Cloud APIs). These are commercial pathways with their own privacy/cost/region implications; the same Quality + Budget + Verdict format must be preserved. Phase 05 covers category 7 (Sidecar runtimes), which has unique dynamic-loading and IPC implications.

---

## Rollback Plan

Restore the `_(populated in Phase 03 — see tactical plan)_` placeholder lines under category sub-sections 3 and 4; remove all appended content.
