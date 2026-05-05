# Tactical Plan: S0058 — vr-passthrough-camera-capture

**Strategic spec:** [`../S0058_vr-passthrough-camera-capture.md`](../S0058_vr-passthrough-camera-capture.md)
**Feature:** Passthrough Camera capture from Browse on Quest 3 (Horizon OS v74+)
**Tier:** 4 — Hard
**Priority:** 55
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-05-05

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Research Closure (was §6 in strategic spec)

All blocking research items resolved before tactical authoring (2026-05-05):

| § | Question | Resolution |
|---|----------|------------|
| 6.1 | Permission name | `horizonos.permission.HEADSET_CAMERA` — dangerous/user-grantable. Standard `android.permission.CAMERA` also works but grants broader access. |
| 6.2 | Min Horizon OS + detection | v74+. Quest 3 and Quest 3S only (Quest Pro unsupported). Detect via `CameraCharacteristics.Key("com.meta.extra_metadata.camera_source", Int)` == 0. |
| 6.3 | OpenXR + Camera2 compat | Camera2 session opens alongside active OpenXR session without conflict (confirmed Meta docs 2025). |
| 6.4 | Save target | Use `resource` passed by `BrowseActivity` (current browse context) — no separate picker needed. |
| 6.5 | File naming | `passthrough_YYYY-MM-DD_HH-mm-ss.jpg` — sufficient for v1. |

No special Meta Spatial SDK gradle dependency required — standard Android Camera2 API.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | interface-hilt-wiring | — | ✅ Done | 6/6 | [PHASE_01__interface-hilt-wiring.md](PHASE_01__interface-hilt-wiring.md) |
| 02 | vr-manifest-binding | 01 | ✅ Done | 4/4 | [PHASE_02__vr-manifest-binding.md](PHASE_02__vr-manifest-binding.md) |
| 03 | camera-capture | 02 | ✅ Done | 4/4 | [PHASE_03__camera-capture.md](PHASE_03__camera-capture.md) |
| 04 | save-ux-feedback | 03 | ✅ Done | 3/3 | [PHASE_04__save-ux-feedback.md](PHASE_04__save-ux-feedback.md) |
| 05 | strings-localization | 04 | ✅ Done | 1/1 | [PHASE_05__strings-localization.md](PHASE_05__strings-localization.md) |
| 06 | docs-catalog-cleanup | 05 | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All research items closed — no blockers. Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (§8 of strategic spec).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0058` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## Architecture Summary

### Integration pattern
`BrowsePassthroughCaptureProvider` interface lives in the main source set. Hilt `@BindsOptionalOf` module declares an optional binding (empty on non-VR builds). `BrowseActivity` injects `Optional<BrowsePassthroughCaptureProvider>` — present only in the VR build where `VrModule` provides `VrBrowsePassthroughCaptureManager`.

### Flow (VR build)
```
BrowseManagerInitializer.onResourceOpsClicked()
  → passthroughProvider?.isAvailable(context) == true  →  button visible
  → BrowseActivity.onCameraCaptureClicked()
      → passthroughProvider.launch(activity, resource, onFileSaved)
          → VrBrowsePassthroughCaptureManager
              → permission check (horizonos.permission.HEADSET_CAMERA)
              → Camera2: openCamera → createCaptureSession → JPEG capture
              → flash overlay + haptic
              → write JPEG to resource.path
              → onFileSaved(fileName) → viewModel.reloadFiles()
```

### Non-VR builds
`passthroughProvider` Optional is empty → `hasCameraHandler()` path unchanged → no code or behaviour change.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0058`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-05 — Initial tactical plan authored by `/spec-tech`.
