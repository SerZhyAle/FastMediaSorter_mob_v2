# Phase 04 - Commercial Pathways (Closed-source SDK + Cloud APIs)

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

Fill categories 5 (Closed-source SDK) and 6 (Cloud OCR APIs) with concrete candidate analysis. Treat them as a single phase because both are commercial pathways but with distinct profile: closed-SDK has license/redistribution focus, cloud APIs have privacy/cost/region focus.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Resolves strategic §6 Open items: 5 (closed-source SDK redistribution terms), 6 (cloud OCR provider matrix).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md` | Modified | ≤ 600 |

---

## Steps

### Step 04.1 - Research closed-source SDK redistribution terms

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Use `WebSearch` against the official sites of ABBYY Mobile OCR SDK, Anyline OCR SDK, ReadIRIS Mobile SDK, and (where relevant) Tencent OCR / Huawei HMS OCR. For each provider identify: (a) does the SDK have a public per-device or per-volume redistribution tier (not just "evaluation only"); (b) what is the typical license cost order of magnitude (free / under $100 / under $1000 / enterprise-only); (c) is Cyrillic explicitly listed in supported scripts; (d) SDK download size; (e) can a personal sideload-only build legally include it. Append findings under `### 5. Closed-source SDK`.

**Verification:**

- `Grep` - text under `### 5. Closed-source SDK` no longer contains `_(populated in Phase`.
- `Grep` - at least two SDK providers listed.
- `Grep` - explicit `Redistribution: <terms>` line per candidate.
- `Grep` - explicit `Cyrillic supported: yes|no|partial` line per candidate.
- `Grep` - explicit `**Ось A:**` and `**Ось B:**` verdict lines per candidate.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 5/5 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+31 LOC). Dev log recorded.

---

### Step 04.2 - Research cloud OCR API providers

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Use `WebSearch` against Google Cloud Vision, Yandex Vision OCR, OpenAI Vision (chat completions image input), Anthropic Vision (claude messages with image content), and Azure Computer Vision Read API. For each provider identify: (a) Cyrillic OCR support explicitly documented; (b) public benchmark of accuracy on Cyrillic if available; (c) free-tier limits and paid pricing (per-image or per-1000-images); (d) GDPR and data residency policies for RU and UK text; (e) availability without VPN in regions where the app's users live (CIS focus per strategic §3.1.4). Append findings under `### 6. Облачные OCR API`.

**Verification:**

- `Grep` - text under `### 6. Облачные OCR API` no longer contains `_(populated in Phase`.
- `Grep` - at least four cloud providers listed (e.g. Google Cloud Vision, OpenAI Vision, Anthropic Vision, Azure Computer Vision; Yandex if accessible).
- `Grep` - explicit `Free tier:` AND `Paid tier:` lines per provider.
- `Grep` - explicit `Data residency:` line per provider.
- `Grep` - explicit `**Ось A:**` verdict line per provider — by strategic §3.2 cloud providers are always opt-in, so axis A is `Conditional` at minimum, never `Accept`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 5/5 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+78 LOC). Dev log recorded.

---

### Step 04.3 - Apply Столп D quality scenarios + privacy/cost annotations

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> For every candidate in steps 04.1–04.2 append `**Качество на сценариях Столпа D:**` block. For cloud providers, also append `**Privacy/Cost:**` block listing: per-image cost at paid tier, whether image content leaves device (always yes for cloud, but cite the provider's stated handling — "stored vs. processed-and-discarded"), opt-in friction estimate (one-click vs multi-step). For closed-SDK, append `**SDK integration cost:**` block listing: integration complexity (drop-in AAR / Maven / manual build), upgrade cadence, vendor support quality.

**Verification:**

- `Grep -c '\*\*Качество на сценариях Столпа D:\*\*'` increases by at least 6.
- `Grep -c '\*\*Privacy/Cost:\*\*'` returns at least 4 (one per cloud provider).
- `Grep -c '\*\*SDK integration cost:\*\*'` returns at least 2 (one per closed-SDK).

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+52 LOC). Dev log recorded.

---

### Step 04.4 - Communication policy gate for cloud opt-in messaging

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 04.3

**Prompt for developer:**

> Append a sub-section `**Communication policy note for cloud opt-in:**` at the end of category 6. State that any follow-up implementation spec adding cloud OCR MUST run user-visible opt-in strings (provider name, data residency, cost notice, off-by-default) through `docs/COMMUNICATION_POLICY.md` §2 (message formula for confirmation/opt-in) and §6 (tone checklist). Three concrete examples of strings that would need this gate: (a) cloud-OCR enable confirmation dialog title+body; (b) error toast when cloud quota exhausted; (c) settings entry label and supporting text. Do NOT write the actual strings here — this is a gate-reminder, not implementation.

**Verification:**

- `Grep` - `\*\*Communication policy note for cloud opt-in:\*\*` matches exactly once.
- `Grep` - references to `COMMUNICATION_POLICY.md §2` AND `§6` are present.
- `Grep` - three string-example bullets are present (opt-in dialog, error toast, settings label).
- **Strings pass COMMUNICATION_POLICY §6 checklist** — this step writes no production strings; the checklist applies to follow-up implementation specs, not to this gate-reminder.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+6 LOC). Dev log recorded.

---

### Step 04.5 - Add phase summary at end of category 6

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 04.4

**Prompt for developer:**

> Append `**Резюме фазы 04:**` paragraph at the end of category 6. Three sentences max: (1) is any closed-source SDK candidate `Acceptable` or better for axis B (noLegal sideload)?; (2) which cloud provider is the leading axis A `Conditional` candidate for a future opt-in feature; (3) explicit verdict on whether any commercial pathway provides a no-cost-to-user option (closed-SDK eval is not an option per strategic §3.2 license boundary).

**Verification:**

- `Grep` - `\*\*Резюме фазы 04:\*\*` matches exactly once.
- `Grep` - the summary references at least one explicit provider name AND one explicit verdict.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+1 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Both category sections (5 and 6) populated without placeholder.
- [x] Cloud category has explicit Communication policy gate-note.
- [x] Closed-SDK category has explicit redistribution-terms verdict per candidate.
- [x] Dev log entry added for the modified file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 05 covers the last remaining category — sidecar runtimes — which has unique distribution and dynamic-loading implications. Phase 06 then aggregates all categories into the cross-axis matrix.

---

## Rollback Plan

Restore the `_(populated in Phase 04 — see tactical plan)_` placeholder lines under category sub-sections 5 and 6; remove all appended content.
