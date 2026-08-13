# Tactical Plan: S0359 - camera-permission-inapp-capture

**Strategic spec:** [`../S0359_camera-permission-inapp-capture.md`](../S0359_camera-permission-inapp-capture.md)
**Feature:** CAMERA permission + in-app CameraX capture (sole capture path), Camera-to-Resource settings section, open-captured-in-drawing-editor
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 8 / 8 done
**Last updated:** 2026-06-05

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

> **Design decisions baked in (from strategic §6, owner-resolved 2026-06-05):**
> - Variant 1: CAMERA mandatory for capture; in-app CameraX is the sole capture path; `ACTION_IMAGE_CAPTURE` removed from the OCR and Browse capture flows. No system-camera fallback.
> - New in-app `CameraCaptureActivity` is a **drop-in replacement** for the system capture intent: callers pass `EXTRA_OUTPUT`, it writes the JPEG there and returns `RESULT_OK`. Existing manager result-handling stays almost identical.
> - Settings section "Camera-to-Resource" lives in `AudioSettingsFragment` (next to the dictaphone rows, per owner "рядом с диктофоном"), built from `SettingsToggleRow`.
> - "Enabled" toggle reuses `!disableCameraCapture` (no new master flag). "Ask filename" reuses `!skipCameraFilenameDialog`. Only ONE genuinely new boolean: `cameraCaptureOpenForEditing`.
> - Permission registry CAMERA entry is `optional=true`. The screen groups all `optional==true` entries under one synthetic "Optional permissions" header; required entries keep their group headers.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations-permission-deps | - | ✅ Done | 4/4 | [PHASE_01__foundations-permission-deps.md](PHASE_01__foundations-permission-deps.md) |
| 02 | inapp-camera-screen | 01 | ✅ Done | 5/5 | [PHASE_02__inapp-camera-screen.md](PHASE_02__inapp-camera-screen.md) |
| 03 | ocr-inapp-capture | 02 | ✅ Done | 3/3 | [PHASE_03__ocr-inapp-capture.md](PHASE_03__ocr-inapp-capture.md) |
| 04 | settings-keys | 01 | ✅ Done | 3/3 | [PHASE_04__settings-keys.md](PHASE_04__settings-keys.md) |
| 05 | camera-resource-settings-ui | 04 | ✅ Done | 4/4 | [PHASE_05__camera-resource-settings-ui.md](PHASE_05__camera-resource-settings-ui.md) |
| 06 | browse-inapp-capture-editing | 02, 04 | ✅ Done | 4/4 | [PHASE_06__browse-inapp-capture-editing.md](PHASE_06__browse-inapp-capture-editing.md) |
| 07 | optional-permissions-grouping | 01 | ✅ Done | 3/3 | [PHASE_07__optional-permissions-grouping.md](PHASE_07__optional-permissions-grouping.md) |
| 08 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_08__docs-catalog-cleanup.md](PHASE_08__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] All strategic §6 research items are Resolved (owner sign-off 2026-06-05, Variant 1). No open blockers.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates it: new Camera-to-Resource section + in-app capture).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes).
- [ ] `/spec-check S0359` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log; set journal status to the matching `Block*`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0359`.

---

## Blockers Log

- Build validation is currently noisy: `:app_v2:compileStandardDebugKotlin` succeeded once during implementation, but later retries and `:app_v2:assembleStandardDebug` were interrupted by external `Gradle build daemon has been stopped: stop command received` / stale build-state failures. See `temp/sessions/20260605_133400_s0359_recover_compile.txt`, `temp/sessions/20260605_132000_s0359_recover_assemble.txt`, and `temp/sessions/20260605_133300_s0359_recover_assemble_after_clean.txt`.

---

## Change Log

- 2026-06-05 - Initial tactical plan authored by `/spec-tech` after owner resolved §6.6 (Variant 1).
- 2026-06-05 - `/spec-dev` implementation completed; ticket advanced to `BlockNeedUserTest` with temporary capture-entry debug probes.
