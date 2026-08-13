# Phase 02 - Capability Source-Set Contributions

**Strategic spec:** [`../S0396_welcome-availability-contract.md`](../S0396_welcome-availability-contract.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-10
**Completed:** 2026-06-10

---

## Objective

Contribute each compiled capability id into `@CompiledCapabilities` from its existing capability source set: `ocrEnabled` → OCR, `translationEnabled` → translation, `vrOnly` → VR. `ocrDisabled`/`vrStub`/(no translationEnabled) contribute nothing - the Phase-01 empty default covers lite/photos.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`CapabilityAvailability` + `@CompiledCapabilities` + module exist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/ocrEnabled/java/com/sza/fastmediasorter/di/OcrCapabilityModule.kt` | New | ≤ 30 |
| `app_v2/src/translationEnabled/java/com/sza/fastmediasorter/di/TranslationCapabilityModule.kt` | New | ≤ 30 |
| `app_v2/src/vrOnly/java/com/sza/fastmediasorter/di/VrCapabilityModule.kt` | New | ≤ 30 |

> **Flavor placement.** These are capability-source-set classes (not flavor-only). `ocrEnabled` is mounted by standard/noLegal/legacy/vr; `translationEnabled` by the same four; `vrOnly` by vr/noLegal (build.gradle.kts 522-558). They MUST live under the named `src/<capabilitySourceSet>/java/` - never `src/main`.

---

## Steps

### Step 02.1 - OCR contribution from ocrEnabled

**Files:** `app_v2/src/ocrEnabled/java/com/sza/fastmediasorter/di/OcrCapabilityModule.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `@Module @InstallIn(SingletonComponent::class) object OcrCapabilityModule` with `@Provides @IntoSet @CompiledCapabilities fun provideOcr(): String = CapabilityAvailability.CAP_OCR`. Mirror the structure of `src/standard/.../di/StandardSettingsSearchAvailabilityModule.kt`. This source set is mounted only by OCR-capable flavors, so the contribution is automatically absent in lite/photos (which mount `ocrDisabled`).

**Verification:**

- `Glob` - `app_v2/src/ocrEnabled/java/com/sza/fastmediasorter/di/OcrCapabilityModule.kt` exists.
- `Grep` - `@IntoSet` and `@CompiledCapabilities` present.
- `Grep` - `CAP_OCR` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 3/3 PASS. Created src/ocrEnabled/.../di/OcrCapabilityModule.kt (@IntoSet @CompiledCapabilities → CAP_OCR).

---

### Step 02.2 - Translation contribution from translationEnabled

**Files:** `app_v2/src/translationEnabled/java/com/sza/fastmediasorter/di/TranslationCapabilityModule.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `@Module @InstallIn(SingletonComponent::class) object TranslationCapabilityModule` with `@Provides @IntoSet @CompiledCapabilities fun provideTranslation(): String = CapabilityAvailability.CAP_TRANSLATION`. The `translationEnabled` source set is mounted only by translation flavors (standard/noLegal/legacy/vr); lite/photos mount nothing for translation, so the empty default applies there.

**Verification:**

- `Glob` - `app_v2/src/translationEnabled/java/com/sza/fastmediasorter/di/TranslationCapabilityModule.kt` exists.
- `Grep` - `@IntoSet` and `@CompiledCapabilities` present.
- `Grep` - `CAP_TRANSLATION` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 3/3 PASS. Created src/translationEnabled/.../di/TranslationCapabilityModule.kt (@IntoSet → CAP_TRANSLATION).

---

### Step 02.3 - VR contribution from vrOnly

**Files:** `app_v2/src/vrOnly/java/com/sza/fastmediasorter/di/VrCapabilityModule.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `@Module @InstallIn(SingletonComponent::class) object VrCapabilityModule` with `@Provides @IntoSet @CompiledCapabilities fun provideVr(): String = CapabilityAvailability.CAP_VR`. The `vrOnly` source set is mounted by vr/noLegal; vrStub flavors (standard/lite/photos/legacy) contribute nothing, so `isVrAvailable()` is false there.

**Verification:**

- `Glob` - `app_v2/src/vrOnly/java/com/sza/fastmediasorter/di/VrCapabilityModule.kt` exists.
- `Grep` - `@IntoSet` and `@CompiledCapabilities` present.
- `Grep` - `CAP_VR` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 3/3 PASS. Created src/vrOnly/.../di/VrCapabilityModule.kt (@IntoSet → CAP_VR).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - standard debug BUILD SUCCESSFUL (joint with Phase 03, 1m44s); standard mounts ocrEnabled+translationEnabled, exercising the OCR+translation contributions. Lite empty-default path verified in Phase 04.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for all three new files.

---

## Handoff Notes to Next Phase

After this phase `CapabilityAvailability` returns correct per-flavor values: standard/noLegal/legacy/vr → translation+OCR true; vr/noLegal → VR true; lite/photos → all false. Phase 03 can now migrate settings visibility with behavioral parity.

---

## Rollback Plan

Revert phase commit(s) - three new flavor-source-set files; the contract falls back to the empty default (all capabilities false). No consumer migrated yet.
