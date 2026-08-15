# Phase 01 - Capability Contract

**Strategic spec:** [`../S0396_welcome-availability-contract.md`](../S0396_welcome-availability-contract.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Introduce `CapabilityAvailability` (src/main) backed by a multibound `@CompiledCapabilities Set<String>` with an empty-safe `@Multibinds` default; no flavor contributions and no consumers yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/CapabilityAvailability.kt` | New | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/CapabilityAvailabilityModule.kt` | New | ≤ 30 |

---

## Steps

### Step 01.1 - Create the CapabilityAvailability contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/CapabilityAvailability.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a `@Singleton class CapabilityAvailability @Inject constructor(@CompiledCapabilities private val compiled: Set<@JvmSuppressWildcards String>)` in a new `core/capability` package. Declare the qualifier `@Qualifier @Retention(AnnotationRetention.BINARY) annotation class CompiledCapabilities` in the same file (mirror `SupportedMediaSection` in `ui/settings/search/SettingsSearchAvailability.kt`). Add a `companion object` with `const val CAP_OCR = "ocr"`, `CAP_TRANSLATION = "translation"`, `CAP_VR = "vr"`. Methods, all pure set-membership except the OCR runtime combine:
> - `fun isTranslationAvailable(): Boolean = CAP_TRANSLATION in compiled`
> - `fun isVrAvailable(): Boolean = CAP_VR in compiled` (compile-time only - runtime XR detection stays in `XrDetectionFacade`, out of scope here)
> - `fun isOcrCompiledIn(): Boolean = CAP_OCR in compiled`
> - `fun isOcrAvailable(context: Context): Boolean = isOcrCompiledIn() && DeviceCapabilities.isOcrSupported(context)` (combines compile-time presence with device RAM/API gate)
> - `fun isExtensionsScreenAvailable(): Boolean = isOcrCompiledIn() || isTranslationAvailable()`
>
> KDoc must state: no `BuildConfig.*` read here (Rule 15); the compiled-capability set is the single source of truth, fed by per-capability-source-set modules (`ocrEnabled`/`translationEnabled`/`vrOnly`). No `Log.d`. The structured OCR-reason variant (for UI copy) is deliberately deferred to the consumer ticket S0400.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/CapabilityAvailability.kt` exists.
- `Grep` - `class CapabilityAvailability` matches exactly once.
- `Grep` - `annotation class CompiledCapabilities` present.
- `Grep` - `CAP_OCR` and `CAP_TRANSLATION` and `CAP_VR` present.
- `Grep` - `isOcrAvailable` and `isTranslationAvailable` and `isVrAvailable` present.
- `Grep` - `BuildConfig` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 6/6 PASS. Created core/capability/CapabilityAvailability.kt (+54 LOC): @CompiledCapabilities qualifier, multibound set, isTranslation/Vr/OcrCompiledIn/OcrAvailable/ExtensionsScreen methods. Reworded KDoc to drop literal "BuildConfig" prose (predicate = zero hits).

---

### Step 01.2 - Declare the empty-safe multibinding default

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/CapabilityAvailabilityModule.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `@Module @InstallIn(SingletonComponent::class) abstract class CapabilityAvailabilityModule` with a single `@Multibinds @CompiledCapabilities abstract fun compiledCapabilities(): Set<String>` (mirror the empty-set default pattern used by `SettingsSearchModule` in `di/`). This lets flavors that contribute nothing (lite/photos: ocrDisabled + vrStub + no translationEnabled) resolve an empty set instead of failing DI.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/di/CapabilityAvailabilityModule.kt` exists.
- `Grep` - `@Multibinds` present.
- `Grep` - `@CompiledCapabilities` present.
- `Grep` - `abstract class CapabilityAvailabilityModule` matches exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 4/4 PASS. Created di/CapabilityAvailabilityModule.kt (+20 LOC): @Multibinds empty-set default for @CompiledCapabilities, mirroring SettingsSearchModule.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for both new files.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public class added).

---

## Handoff Notes to Next Phase

`CapabilityAvailability` resolves with an empty set on every flavor until Phase 02 adds contributions. Do not migrate any consumer before Phase 02 - reads would all return false.

---

## Rollback Plan

Revert phase commit(s) - two new files, no existing code touched, no DI consumer yet.
