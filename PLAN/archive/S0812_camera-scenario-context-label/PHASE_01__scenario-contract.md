# Phase 01 - Scenario contract

**Strategic spec:** [`../S0812_camera-scenario-context-label.md`](../S0812_camera-scenario-context-label.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Introduce a typed `CameraScenario` enum with a localized label string, and plumb it through `CameraCaptureContract` and the `CameraCaptureActivity` intent factory. No UI or caller changes yet.

---

## Prerequisites

- [ ] Strategic §6 has no open research items.
- [ ] Working tree may be dirty (single-dev WIP is normal).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraScenario.kt` | New | ≤ 40 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureContract.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 1000 |

---

## Steps

### Step 01.1 - Add localized scenario label string

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one string key `camera_scenario_ocr_translate` across EN/RU/UK in a single lockstep call:
> `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key camera_scenario_ocr_translate -En "Text recognition" -Ru "Распознавание текста" -Uk "Розпізнавання тексту"`.
> Text is a short scenario label rendered over the camera preview; keep it concise (fits above the zoom presets). Verify against `docs/COMMUNICATION_POLICY.md` §2 (label formula) and §6 (tone checklist).

**Verification:**

- `Grep` - `camera_scenario_ocr_translate` present in all three `strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "camera_scenario"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[ ]` not done

---

### Step 01.2 - Add CameraScenario enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraScenario.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create enum `CameraScenario` mirroring `CameraCaptureMode` (same package sibling `model/`). Each constant carries a `@StringRes val labelRes: Int`; `NONE` uses `0` (no label), `OCR_TRANSLATE` uses `R.string.camera_scenario_ocr_translate`. Add a companion `fromName(name: String?): CameraScenario` that returns the matching entry or `NONE`. KDoc: one line stating this names the caller scenario so the host can show a context label (S0812); scenarios reaching the visible camera only.

**Verification:**

- `Glob` - `CameraScenario.kt` exists.
- `Grep` - `enum class CameraScenario` matches exactly once.
- `Grep` - `OCR_TRANSLATE` and `fun fromName` present.

**Status:** `[ ]` not done

---

### Step 01.3 - Extend CameraCaptureContract with scenario extra

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureContract.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `const val EXTRA_SCENARIO = "scenario"` with a KDoc line (S0812: names the calling scenario; default `NONE` hides the label). Add a `scenario: CameraScenario = CameraScenario.NONE` parameter to the primary `createIntent(context, outputUri, outputPath, mode, microphoneDefault, ...)` factory and write `.putExtra(EXTRA_SCENARIO, scenario.name)`. Add `fun readScenario(intent: Intent): CameraScenario = CameraScenario.fromName(intent.getStringExtra(EXTRA_SCENARIO))`.

**Verification:**

- `Grep` - `EXTRA_SCENARIO` and `fun readScenario` present.
- `Grep` - `scenario: CameraScenario` present in `createIntent` signature.

**Status:** `[ ]` not done

---

### Step 01.4 - Add scenario overload to CameraCaptureActivity factory

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> In the `companion object`, add a `createIntent` overload `(context, outputUri, outputPath, mode, scenario: CameraScenario)` delegating to `CameraCaptureContract.createIntent(..., scenario = scenario)`. Keep existing overloads intact (backward-compatible entry points).

**Verification:**

- `Grep` - a `createIntent` overload referencing `scenario: CameraScenario` present in the companion.
- Project compiles - `.\a.ps1 fk`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fk`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added (batched at ticket close).
- [ ] `dev/CATALOG/app_v2.jsonl` regen deferred to Phase 03.

---

## Handoff Notes to Next Phase

`CameraScenario` enum, `CameraCaptureContract.readScenario`, the scenario intent extra, and the Activity factory overload exist. Phase 02 consumes these to render the label and wire the OCR caller.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed (new string key + unused enum only).
