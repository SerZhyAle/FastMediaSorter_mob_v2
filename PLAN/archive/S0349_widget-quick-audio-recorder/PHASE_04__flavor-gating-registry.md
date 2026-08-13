# Phase 04 - Flavor gating & registry

**Strategic spec:** [`../S0349_widget-quick-audio-recorder.md`](../S0349_widget-quick-audio-recorder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Pending
**Depends on:** Phase 03
**Blocks:** Phase 05

---

## Objective

Make the widget unavailable where `SUPPORT_MIC_RECORDING == false` (`lite`, `photos`) by removing receiver + activity + service via manifest merger, and register the widget in `HomeWidgetCatalog` so the in-app picker offers it where present.

Read `dev/FLAVOR_DEVELOPMENT_RULES.md` §3-§4 before touching flavor manifests.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `src/lite/AndroidManifest.xml` | Modified | ≤ 12 |
| `src/photos/AndroidManifest.xml` | Modified | ≤ 12 |
| `widget/registry/HomeWidgetCatalog.kt` | Modified | ≤ 10 |

---

## Steps

### Step 04.1 - Remove in `lite` and `photos`

- In both flavor manifests add three `tools:node="remove"` entries (mirror existing RandomMusic removal in photos):
  - `<receiver android:name=".widget.QuickAudioRecorderWidgetProvider" tools:node="remove" />`
  - `<activity android:name=".widget.QuickAudioRecorderActivity" tools:node="remove" />`
  - `<service android:name=".widget.QuickAudioRecorderService" tools:node="remove" />`
- Do NOT touch `legacy`/`vr`/`noLegal` (mic recording true there).

**Verification:**
- `Grep "QuickAudioRecorder"` + `node="remove"` present 3× in each of `src/lite` and `src/photos` manifests - expected: 3 each | actual: <fill>.
- `Grep "QuickAudioRecorder"` in `src/legacy`/`src/vr`/`src/noLegal` manifests - expected: zero (untouched) | actual: <fill>.

### Step 04.2 - HomeWidgetCatalog entry

- Add a `HomeWidgetEntry` for `QuickAudioRecorderWidgetProvider` (label/icon/description = the new resources). No `settingGate` (flavor-gated by manifest only).
- Add the matching import.

**Verification:**
- `Grep "QuickAudioRecorderWidgetProvider"` in `HomeWidgetCatalog.kt` once.
- `Grep "BuildConfig"` zero hits in `HomeWidgetCatalog.kt` (unchanged invariant).

### Step 04.3 - Availability matrix verification

- After Phase 05 builds, inspect merged manifests:
  - `standard`/`legacy`/`vr`/`noLegal`: receiver present.
  - `lite`/`photos`: receiver + activity + service absent.

**Verification:**
- `Grep "QuickAudioRecorderWidgetProvider"` in `app_v2/build/intermediates/merged_manifests/<variant>/AndroidManifest.xml` - record expected vs actual per checked variant (at minimum `standardDebug` present, `photosDebug` absent).

---

## Phase Done Criteria

- Removals present in `lite` + `photos`; `legacy`/`vr`/`noLegal` untouched.
- Catalog entry added; no `BuildConfig` in registry.
- `liteDebug` + `photosDebug` manifest merge succeeds (verified in Phase 05 build gate).
