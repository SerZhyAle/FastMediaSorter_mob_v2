# Phase 06 - Cross-Axis Synthesis + Follow-up Spec Proposals

**Strategic spec:** [`../S0285_nolegal-ocr-cyrillic.md`](../S0285_nolegal-ocr-cyrillic.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04, Phase 05
**Blocks:** Phase 07
**Steps done:** 5 / 5
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Aggregate findings from all seven categories into the cross-axis matrix, ranked noLegal candidate list, and a list of proposed follow-up implementation specs. Produce explicit answers for strategic criteria §11.

---

## Prerequisites

- [ ] Phases 02, 03, 04, 05 are ✅ Done.
- [ ] All seven category sub-sections in `ocr-cyrillic.md` populated with Quality + Verdict blocks.
- [ ] Resolves strategic §6 Open items: 8 (mixed-script behavior), 9 (UK Cyrillic separateness), 10 (dynamic-loading boundary final reading).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md` | Modified | ≤ 800 |

---

## Steps

### Step 06.1 - Build the cross-axis matrix

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Append top-level section `## Кросс-осевая матрица кандидатов`. Build a markdown table with columns: `Кандидат | Категория | Ось A verdict | Ось B verdict | Primary blocker | Качество (печать/фото/вывеска/рукопись/PDF) | APK delta`. Populate one row per candidate from all seven categories. Sort rows by axis A verdict first (`Accept` → `Conditional` → `Reject`), then by axis B verdict (`Strong fit` → `Acceptable` → `Weak fit` → `Reject`). The quality column uses 4-character codes `s/a/w/-/?` (strong/acceptable/weak/n.a./device-test-required) per scenario separated by `/`.

**Verification:**

- `Grep` - `^## Кросс-осевая матрица кандидатов` matches exactly once.
- `Grep` - the matrix table has at least the seven listed column headers.
- `Grep -c "^\|"` (rough row count) returns at least 8 (1 header + at least 7 candidates, expect more).
- `Grep` - rows are present for at least 5 of the 7 categories (some categories may have 0 candidates if all rejected outright).

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 4/4 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+19 LOC). Dev log recorded.

---

### Step 06.2 - Ranked noLegal candidate list

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Append section `## Ранжированный список noLegal-кандидатов`. List candidates with axis B verdict `Strong fit` or `Acceptable`, sorted by expected user value (not by complexity, not by license type — by what the user actually gains in Cyrillic OCR quality). For each candidate write: `1. <Candidate name> — <category> — <primary blocker for axis A> — <one-sentence value statement>`. If only one candidate is `Strong fit`, list it explicitly as the top noLegal recommendation. If no candidate is `Strong fit`, state that explicitly: `**Top noLegal recommendation: none reach Strong fit — top recommendation is the leading Acceptable candidate: <name>.**`

**Verification:**

- `Grep` - `^## Ранжированный список noLegal-кандидатов` matches exactly once.
- `Grep -c "^[0-9]+\. "` returns at least 1 (at minimum the leading recommendation).
- `Grep` - explicit `Top noLegal recommendation:` line or equivalent leading-candidate marker present.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+8 LOC). Dev log recorded.

---

### Step 06.3 - Axis A store-safe outcome statement

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Append section `## Ось A: store-safe upgrade for STANDARD`. Produce one of two explicit outcomes: (a) **positive:** at least one candidate is `Accept` or `Conditional` for axis A — list it with concrete next-step description ("ML Kit Cyrillic module" / "Tesseract 5.x model swap" / "Cloud Vision opt-in"); (b) **negative:** no candidate passes axis A — list the closest-to-passing candidate and the specific blocker that prevents acceptance, AND provide a justification for the negative result that goes beyond "Google doesn't ship it" (look at whether the gap is solvable with future research). This section satisfies strategic §11 item 5.

**Verification:**

- `Grep` - `^## Ось A: store-safe upgrade for STANDARD` matches exactly once.
- `Grep` - one of the two outcome markers is present: `**positive:**` OR `**negative:**`.
- `Grep` - the section contains either at least one explicit candidate name (positive) or an explicit closest-candidate name with blocker (negative).

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+5 LOC). Dev log recorded.

---

### Step 06.4 - Follow-up implementation spec proposals

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 06.3

**Prompt for developer:**

> Append section `## Потенциальные follow-up implementation спеки`. List 1–4 proposed follow-up specs without assigning ids. For each: `- **<proposed-slug>** — Ось A or B — <one-sentence scope> — Owner: S0156 epic`. For axis B follow-ups the slug MUST contain `nolegal` (S0156 §F rule); for axis A follow-ups the slug should not contain `nolegal` (it's already going into STANDARD). Examples of well-formed names: `ml-kit-cyrillic-module-integration` (axis A), `nolegal-paddleocr-tflite-bundle` (axis B), `nolegal-cloud-vision-opt-in` (axis B if cloud lands in noLegal sidecar UX, or axis A if it's a STANDARD opt-in).

**Verification:**

- `Grep` - `^## Потенциальные follow-up implementation спеки` matches exactly once.
- `Grep -c "^- \*\*"` returns at least 1 and at most 4.
- `Grep` - every axis B proposed slug contains the substring `nolegal`.
- `Grep` - every proposed item references the epic with `Owner: S0156 epic` or equivalent string.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 4/4 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+4 LOC). Dev log recorded.

---

### Step 06.5 - Resolve strategic §6 questions 8, 9, 10 with explicit answers

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 06.4

**Prompt for developer:**

> Append section `## Закрытие strategic §6 research items`. For each of questions 8 (Latin+Cyrillic mixed-script), 9 (UK-specific Cyrillic), 10 (dynamic-loading boundary), write one bullet with the resolution learned from the research: `- **Q8 — Latin+Cyrillic mixed:** <resolution>` / `- **Q9 — UK Cyrillic:** <resolution>` / `- **Q10 — Dynamic-loading boundary:** <resolution>`. The Q10 answer is particularly important: state explicitly whether `.tflite` / `.onnx` / `.traineddata` download counts as data (allowed) or code (forbidden) for the project's signing key, citing the source of authority (current Play developer policy URL or precedent app).

**Verification:**

- `Grep` - `^## Закрытие strategic §6 research items` matches exactly once.
- `Grep -c "^- \*\*Q[0-9]+ "` returns exactly 3 (Q8, Q9, Q10).
- `Grep` - Q10 entry contains a citation URL (`https://`).

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+3 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Cross-axis matrix, ranked noLegal list, axis A outcome statement, follow-up spec proposals, and §6 Q8–Q10 closure are all present.
- [x] At least one follow-up spec proposal is listed (positive or negative axis A result, plus at least one axis B proposal).
- [x] Dev log entry added for the modified file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 07 (final) handles dev log entries, S0156 INDEX integration hint (if S0156 maintains an INDEX), and confirmation that no FEATURES update is needed.

---

## Rollback Plan

Restore the `ocr-cyrillic.md` document to its phase-05 state by removing all sections appended in phase 06: the matrix, ranked list, axis A outcome, follow-up proposals, and Q8–Q10 closure.
