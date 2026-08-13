# Phase 05 - ML/OCR Flavor Confinement

**Strategic spec:** [`../S0385_apk-aab-dead-weight-reduction.md`](../S0385_apk-aab-dead-weight-reduction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⏭️ Superseded by S0386 (on-demand delivery) - not implemented under S0385
**Depends on:** Phase 01; Pre-Implementation Blockers (research §6.1 + owner sign-off)

> **Superseded 2026-06-08.** The owner chose on-demand delivery (S0386) over per-flavor confinement: download-on-enable, default-off, self-hosted on our GitHub, removing the ~35.5 MB from the base of ALL flavors (not just lite/photos). The §6.1 ML/OCR surface enumeration (below / strategic §6.1) carries over to S0386's facade+backend extraction. Do not implement this phase under S0385. See `PLAN/S0386_ondemand-ocr-translation-delivery.md`.
**Blocks:** none
**Steps done:** 0 / 6
**Started:** -
**Completed:** -

---

## Objective

Make translation/OCR a flavor-confined capability behind a port, so the ML Kit and Tesseract dependencies (and their ~35.5 MB/arm64 native libraries + model assets) are packaged only in flavors where the capability is live (`standard`, `noLegal`, `legacy`, `vr`) and absent from `lite`/`photos`.

---

## Prerequisites

- [ ] **Blocker:** strategic §6.1 resolved - the complete set of `src/main` classes that reference ML Kit / Tesseract is enumerated.
- [ ] **Blocker:** owner sign-off on the Pillar A refactor scope (strategic §3.3).
- [ ] Phase 01 done.

> **Flavor discipline (CLAUDE.md Rule 15, `dev/FLAVOR_DEVELOPMENT_RULES.md`):** the capability port + No-Op fallback live in `src/main`; the real implementation lives in the existing `src/translationEnabled/java` shared source set (mounted into `standard`/`noLegal`/`legacy`/`vr`); the No-Op binding lives in a new `src/translationDisabled/java` shared source set mounted into `lite`/`photos`. No `BuildConfig.ENABLE_TRANSLATION` guard may be added in `src/main`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/TranslationOcrCapabilityPort.kt` | New (interface) | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/NoOpTranslationOcrCapability.kt` | New (No-Op fallback) | ≤ 120 |
| `app_v2/src/translationEnabled/java/com/sza/fastmediasorter/di/TranslationOcrCapabilityModule.kt` | New (real binding) | ≤ 80 |
| `app_v2/src/translationDisabled/java/com/sza/fastmediasorter/di/NoOpTranslationOcrCapabilityModule.kt` | New (No-Op binding) | ≤ 80 |
| ML/OCR-touching classes enumerated in §6.1 | Moved `src/main` → `src/translationEnabled/java` | per file |
| `app_v2/build.gradle.kts` | Modified (sourceSets + dep configs) | n/a (>500 - backup first) |

> The concrete moved-file list is fixed by Blocker §6.1. Each moved class keeps its package; only its source set changes. Call sites in `src/main` must reference the port, never the moved implementations directly.

---

## Steps

### Step 05.1 - Define the capability port and No-Op fallback in shared code

**Files:** `domain/ocr/TranslationOcrCapabilityPort.kt`, `domain/ocr/NoOpTranslationOcrCapability.kt`
**Depends on:** blockers resolved

**Prompt for developer:**

> Introduce a `TranslationOcrCapabilityPort` interface in `src/main` exposing the translation/OCR operations the player and camera-OCR call sites need (recognize text, translate, detect language, availability flag). Add a `NoOpTranslationOcrCapability` in `src/main` that reports "unavailable" and returns empty results - the same outcome the current `ENABLE_TRANSLATION=false` short-circuit produces.

**Verification:**

- `Glob` - both files exist under `src/main`.
- `Grep` - `interface TranslationOcrCapabilityPort` matches once.
- `Grep` - `com.google.mlkit` and `tesseract` are absent from both files (the port must not reference the deps).

**Status:** `[ ]` not done

---

### Step 05.2 - Move the real implementations into the translation-enabled source set

**Files:** ML/OCR-touching classes from §6.1 → `src/translationEnabled/java/...`
**Depends on:** Step 05.1

**Prompt for developer:**

> Move every enumerated ML Kit / Tesseract-touching class from `src/main` into `src/translationEnabled/java` (same package). Have the real capability implementation realize `TranslationOcrCapabilityPort`. Repoint `src/main` call sites (player helpers, camera-OCR entry) to the injected port instead of the concrete classes.

**Verification:**

- `Grep` - `com.google.mlkit` and `cz.adaptech.tesseract4android` return zero hits under `app_v2/src/main/**`.
- `Grep` - the same imports are present under `app_v2/src/translationEnabled/java/**`.

**Status:** `[ ]` not done

---

### Step 05.3 - Bind the real implementation in the translation-enabled DI module

**Files:** `src/translationEnabled/java/.../di/TranslationOcrCapabilityModule.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Add a Hilt module in `src/translationEnabled/java/.../di/` binding `TranslationOcrCapabilityPort` to the real implementation.

**Verification:**

- `Glob` - the module file exists under `src/translationEnabled/java`.
- `Grep` - it binds `TranslationOcrCapabilityPort`.

**Status:** `[ ]` not done

---

### Step 05.4 - Create the translation-disabled No-Op binding and mount the source set

**Files:** `src/translationDisabled/java/.../di/NoOpTranslationOcrCapabilityModule.kt`, `app_v2/build.gradle.kts`
**Depends on:** Step 05.3

**Prompt for developer:**

> Add a Hilt module in a new `src/translationDisabled/java/.../di/` source set binding `TranslationOcrCapabilityPort` to `NoOpTranslationOcrCapability`. In `build.gradle.kts` `sourceSets`, mount `src/translationDisabled/java` into `lite` and `photos` (mirroring how `translationEnabled` is mounted into the other flavors).

**Verification:**

- `Glob` - the No-Op module exists under `src/translationDisabled/java`.
- `Grep` - `build.gradle.kts` mounts `src/translationDisabled/java` under both `getByName("lite")` and `getByName("photos")`.

**Status:** `[ ]` not done

---

### Step 05.5 - Flavor-scope the ML/OCR dependencies

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 05.4

**Prompt for developer:**

> Change `com.google.mlkit:translate`, `com.google.mlkit:text-recognition`, `com.google.mlkit:language-id`, and `cz.adaptech:tesseract4android` from global `implementation(...)` to `standardImplementation` / `noLegalImplementation` / `legacyImplementation` / `vrImplementation` (the translation-enabled flavors), following the existing media3-hls/dash/midi pattern. Do not add them to `lite`/`photos`.

**Verification:**

- `Grep` - none of the four ML/OCR coordinates appear under a plain `implementation(` in `build.gradle.kts`; each appears under flavor-scoped configurations only.

**Status:** `[ ]` not done

---

### Step 05.6 - Verify native libs dropped from lite/photos

**Files:** build artifacts (no source change)
**Depends on:** Step 05.5

**Prompt for developer:**

> Build `liteDebug` and `photosDebug`, unzip each, and confirm the ML/OCR native libraries are gone. Optionally extend confinement to CameraX if strategic §6.2 found camera capture unreachable in these flavors; otherwise leave camera as-is and note the decision.

**Verification:**

- After unzip - `lib/arm64-v8a/libtranslate_jni.so`, `libmlkit_google_ocr_pipeline.so`, `liblanguage_id_l2c_jni.so`, `libtesseract.so`, `libleptonica.so` are all absent from the `lite` and `photos` artifacts.
- `Glob` - `assets/mlkit-google-ocr-models/` is absent from the `lite`/`photos` artifacts.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `/build` all flavors: `standardDebug`, `liteDebug`, `photosDebug`, `legacyDebug`, plus `noLegalDebug` and `vr` (translation-enabled flavors still compile against the moved code).
- [ ] `lite`/`photos` artifacts confirmed free of ML/OCR `.so` and model assets (~35.5 MB/arm64 removed).
- [ ] Translation/OCR still works on a `standardDebug` device run (port resolves to the real impl).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` returns zero hits in any moved/modified `.kt` file.
- [ ] Dev log entry added for every touched file via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Translation/OCR is a flavor-confined capability. `lite`/`photos` no longer carry the ML native libs. The `translationEnabled`/`translationDisabled` partition is the reusable pattern for any future heavy optional capability.

---

## Rollback Plan

Revert the phase commits: restore the moved classes to `src/main`, revert the dependency configs to global `implementation`, and remove the `translationDisabled` source set. No data migration or schema change is involved; user-visible behaviour is unchanged either way.
