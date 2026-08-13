# Phase 05 - Sidecar Runtimes (termux-style + Python runtime + native CLI)

**Strategic spec:** [`../S0285_nolegal-ocr-cyrillic.md`](../S0285_nolegal-ocr-cyrillic.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Fill category 7 (Sidecar-binary решения) with concrete candidate analysis. Sidecar approaches deserve a dedicated phase because they touch the Google Play dynamic-code-loading boundary directly (strategic §3.2, S0156 ADR-6) and have unique IPC/security implications.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Resolves strategic §6 Open item: 7 (sidecar-binary practicality).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md` | Modified | ≤ 400 |

---

## Steps

### Step 05.1 - Research termux-derived runtimes for embedded Python+PaddleOCR

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Use `WebSearch` against `github.com/termux/termux-app`, `github.com/topics/termux-android-library`, `kivy.org/python-for-android`, and `beeware.org`. Identify: (a) availability of a minimal embeddable Python ARM64 runtime that can be packaged inside an AAR/APK alongside PaddleOCR Python wheels; (b) total bundle size (Python + paddle + paddleocr-models + dependencies, optimized); (c) IPC mechanism options (file-based pipes, Unix domain sockets, content provider); (d) startup latency for the first OCR call (Python cold start cost); (e) Google Play distribution stance — both termux-derived approach and beeware/python-for-android stances are subject to "dynamic code execution" interpretation. Append findings under `### 7. Sidecar-binary решения`.

**Verification:**

- `Grep` - text under `### 7. Sidecar-binary решения` no longer contains `_(populated in Phase`.
- `Grep` - at least one candidate sub-block (e.g. "Python-for-android + PaddleOCR").
- `Grep` - explicit `Bundle size:` line with concrete MB number.
- `Grep` - explicit `Play policy stance:` line citing the specific section of Play developer policy.
- `Grep` - explicit `**Ось A:**` verdict line — by strategic §3.2 sidecar approaches are de-facto axis A `Reject` unless a clean compile-time bundle path exists; expect `Reject` with reason cited.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 5/5 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+18 LOC). Dev log recorded.

---

### Step 05.2 - Research native CLI sidecar (tesseract-current, ddjvu, paddleocr CLI)

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Use `WebSearch` against current Tesseract 5.x release binaries for ARM64, `github.com/PaddlePaddle/Paddle-Lite` CLI/demo, and any community-maintained "OCR-binary-bundle" projects for Android. Identify: (a) feasibility of bundling a statically-linked native binary at install time (vs `extractNativeLibs=true`); (b) `Runtime.exec` invocation cost on Android (process spawn latency); (c) NDK/CMake build complexity for cross-compilation; (d) SELinux constraints on subprocess execution in `/data/data/<package>/files/`; (e) explicit comparison vs. embedding the library via JNI (which is what the existing Tesseract4Android does — sidecar is the alternative when JNI wrapper is not available or quality differs significantly). Append under category 7 alongside the Python-runtime candidate.

**Verification:**

- `Grep` - category 7 contains at least two candidate sub-blocks (Python runtime + native CLI).
- `Grep` - explicit `SELinux constraint:` AND `Runtime.exec cost:` lines for the native CLI candidate.
- `Grep` - explicit comparison-to-JNI bullet present.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+16 LOC). Dev log recorded.

---

### Step 05.3 - Apply Столп D scenarios + dynamic-loading boundary analysis

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> For each sidecar candidate in steps 05.1–05.2 append `**Качество на сценариях Столпа D:**` block (inherit from the underlying engine — Python+PaddleOCR inherits PaddleOCR's quality already analyzed in phase 03; CLI tesseract-current inherits Tesseract 5.x quality already analyzed in phase 02). Append `**Dynamic-loading boundary:**` block explicitly stating: (a) does the candidate require any code download after install (Reject for axis A by strategic §3.2); (b) does the candidate execute code from `/data/data/<package>/files/` not in `lib/<abi>/` (potential Play policy red flag); (c) does the candidate change the dynamic-loading risk profile of the entire signing key per S0156 ADR-6.

**Verification:**

- `Grep -c '\*\*Качество на сценариях Столпа D:\*\*'` increases by at least 2.
- `Grep -c '\*\*Dynamic-loading boundary:\*\*'` returns at least 2 (one per candidate).
- `Grep` - the boundary block for each candidate cites strategic §3.2 OR S0156 ADR-6 explicitly.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+8 LOC). Dev log recorded.

---

### Step 05.4 - Add phase summary at end of category 7

**Files:** `PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md`
**Depends on:** Step 05.3

**Prompt for developer:**

> Append `**Резюме фазы 05:**` paragraph at the end of category 7. Three sentences max: (1) does any sidecar approach deliver meaningfully better quality than the on-device DL options from phase 03 to justify the integration complexity; (2) explicit verdict on whether any sidecar candidate could be axis B `Acceptable` despite the integration cost, or whether all sidecar candidates fall to `Weak fit` due to maintenance burden (strategic §3.2 Sidecar-binary risk + ADR-7 add-pattern doesn't apply when add-pattern itself is heavyweight); (3) handoff statement that category 7 is the most risk-laden of all seven and phase 06 cross-axis matrix should mark it accordingly.

**Verification:**

- `Grep` - `\*\*Резюме фазы 05:\*\*` matches exactly once.
- `Grep` - summary contains explicit axis B verdict reference (`Acceptable`, `Weak fit`, or `Reject`).

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: PLAN/S0156_nolegal-capability-surface-audit/ocr-cyrillic.md (+1 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Category section 7 populated without placeholder; each candidate has Quality + Dynamic-loading boundary + Verdict blocks.
- [x] Dev log entry added for the modified file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After phase 05, all seven categories are populated. Phase 06 aggregates: cross-axis matrix, ranked noLegal candidate list, follow-up implementation spec proposals.

---

## Rollback Plan

Restore the `_(populated in Phase 05 — see tactical plan)_` placeholder line under category sub-section 7; remove all appended content.
