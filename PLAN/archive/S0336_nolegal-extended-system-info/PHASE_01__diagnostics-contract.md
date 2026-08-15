# Phase 01 - Diagnostics contract & aggregation

**Strategic spec:** [`../S0336_nolegal-extended-system-info.md`](../S0336_nolegal-extended-system-info.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 5 / 5
**Started:** 2026-06-03
**Completed:** 2026-06-03

**Step Log:**

- 2026-06-03 - Steps 01.1-01.5 Verification PASS (Grep predicates + `assembleStandardDebug` + `testStandardDebugUnitTest --tests *GatherSystemInfoUseCaseTest`: tests=4 failures=0).

---

## Objective

Introduce the flavor-agnostic diagnostics contract in `src/main` (contributor interface, section/field models, redaction mapper, `@Multibinds` set) and make `GatherSystemInfoUseCase` aggregate contributor output into a masked + full report. No noLegal data and no UI change yet; behaviour on every flavor is unchanged because the contributor set is empty.

---

## Prerequisites

- [ ] Strategic §6 research items blocking this phase are Resolved (all are).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/systeminfo/ExtendedDiagnostics.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/systeminfo/SystemInfoReport.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/ExtendedDiagnosticsModule.kt` | New | ≤ 25 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCase.kt` | Modified | ≤ 470 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCaseTest.kt` | Modified | ≤ 90 |

> No file crosses 500 lines after the change - no backup step required.
> **Flavor placement:** every file in this phase is shared contract / aggregation and lives in `src/main`. The real implementation arrives in Phase 02 under `src/noLegal/java`.

---

## Steps

### Step 01.1 - Define the diagnostics contract and models

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/systeminfo/ExtendedDiagnostics.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `ExtendedDiagnostics.kt` in package `com.sza.fastmediasorter.core.systeminfo`. Declare:
> - `data class ExtendedDiagnosticsField(val label: String, val value: String, val sensitive: Boolean = false)`.
> - `data class ExtendedDiagnosticsSection(val title: String, val fields: List<ExtendedDiagnosticsField>)`.
> - `interface ExtendedDiagnosticsContributor { fun sections(): List<ExtendedDiagnosticsSection> }` with a KDoc line stating implementations live only in flavor source sets and must degrade gracefully (`unknown`/`n/a`) on missing API.
> - A mapper `fun List<ExtendedDiagnosticsSection>.toSystemInfoSections(reveal: Boolean): List<SystemInfoSection>` that maps each field to `label to (if (sensitive && !reveal) REDACTED else value)` and wraps it in `SystemInfoSection`.
> - `const val REDACTED = "[REDACTED]"` (fixed technical marker, not localized - consistent with S0335 fixed-English body values).
>
> Pure Kotlin, no Android imports beyond `SystemInfoSection`. Timber only if logging is ever needed (none expected here).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/systeminfo/ExtendedDiagnostics.kt` exists.
- `Grep` - `interface ExtendedDiagnosticsContributor` matches exactly once.
- `Grep` - `fun List<ExtendedDiagnosticsSection>.toSystemInfoSections` present.
- `Grep` - `data class ExtendedDiagnosticsField` present and contains `sensitive`.

**Status:** `[x]` done

---

### Step 01.2 - Add the aggregated report value type

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/systeminfo/SystemInfoReport.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `SystemInfoReport.kt` in package `com.sza.fastmediasorter.core.systeminfo`: `data class SystemInfoReport(val maskedText: String, val fullText: String, val hasSensitive: Boolean)`. KDoc: `maskedText` is the default body shown / copied / shared; `fullText` is revealed only on explicit user action; `hasSensitive` drives whether the reveal action is offered.

**Verification:**

- `Glob` - `SystemInfoReport.kt` exists.
- `Grep` - `data class SystemInfoReport` with `maskedText`, `fullText`, `hasSensitive`.

**Status:** `[x]` done

---

### Step 01.3 - Declare the multibound contributor set

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/ExtendedDiagnosticsModule.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `ExtendedDiagnosticsModule.kt` in package `com.sza.fastmediasorter.di`: `@Module @InstallIn(SingletonComponent::class) abstract class ExtendedDiagnosticsModule { @Multibinds abstract fun extendedDiagnosticsContributors(): Set<@JvmSuppressWildcards ExtendedDiagnosticsContributor> }`. This makes the set injectable even when empty (every non-noLegal flavor). Mirror `OcrContributorModule.kt`.

**Verification:**

- `Glob` - `ExtendedDiagnosticsModule.kt` exists.
- `Grep` - `@Multibinds` present.
- `Grep` - `Set<@JvmSuppressWildcards ExtendedDiagnosticsContributor>` present.

**Status:** `[x]` done

---

### Step 01.4 - Aggregate contributors in GatherSystemInfoUseCase

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCase.kt`
**Depends on:** Step 01.1, Step 01.2, Step 01.3

**Prompt for developer:**

> Add a constructor parameter `private val extendedDiagnosticsContributors: Set<@JvmSuppressWildcards ExtendedDiagnosticsContributor>` (Hilt injects it; empty on non-noLegal). Collect contributor sections once: `val extended = extendedDiagnosticsContributors.flatMap { it.sections() }`. Change `operator fun invoke()` to return `SystemInfoReport`:
> - base sections list = the existing `buildSections()`.
> - `maskedText = (base + extended.toSystemInfoSections(reveal = false)).renderSystemInfo()`.
> - `fullText = (base + extended.toSystemInfoSections(reveal = true)).renderSystemInfo()`.
> - `hasSensitive = extended.any { sec -> sec.fields.any { it.sensitive } }`.
> Wrap the `flatMap` in the existing `safeList`/`safe` defensive style so a throwing contributor cannot break the base report. Keep the existing base-section code intact (it is shared with S0337). Do not touch the `Timber.d("S0337: ...")` probe - it lives in the caller, not here.

**Verification:**

- `Grep` - `extendedDiagnosticsContributors: Set<@JvmSuppressWildcards ExtendedDiagnosticsContributor>` in the constructor.
- `Grep` - `operator fun invoke(): SystemInfoReport` present.
- `Grep` - `toSystemInfoSections(reveal = false)` and `toSystemInfoSections(reveal = true)` both present.
- `Grep -n "Log\.d\("` - zero hits in this file.
- Build invariant: `assembleStandardDebug` compiles (empty set ⇒ `maskedText == fullText`, `hasSensitive == false`).

**Status:** `[x]` done

---

### Step 01.5 - Update the use-case unit test for the new signature

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCaseTest.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Construct the use case with an empty contributor set: `GatherSystemInfoUseCase(context, emptySet())`. Replace each `useCase()` String usage with `useCase().maskedText`. Add one test `invoke with no contributors keeps masked equal to full`: assert `useCase().maskedText == useCase().fullText` and `useCase().hasSensitive` is `false`. Keep the existing localized-headers and version-name assertions (they must still pass - base sections are unchanged).

**Verification:**

- `Grep` - `GatherSystemInfoUseCase(context, emptySet())` present.
- `Grep` - `.maskedText` present; no bare `useCase()` used directly as a `String`.
- `Grep` - new test asserting `maskedText == ` ... `fullText`.
- Build invariant: `testStandardDebugUnitTest --tests "*GatherSystemInfoUseCaseTest"` passes (verify via that class's `test-results` XML; the suite carries unrelated pre-existing failures).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - `/build` `standardDebug` (isolation baseline: contributor set empty).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public types added).

---

## Handoff Notes to Next Phase

- `ExtendedDiagnosticsContributor` is the only seam Phase 02 implements. Phase 02 adds an `@Binds @IntoSet` of a real contributor under `src/noLegal/java/.../di/`.
- `SystemInfoReport.hasSensitive` is the only signal Phase 03's UI reads to decide whether to offer the reveal action.
- The mapper already masks sensitive fields; the contributor only needs to set `sensitive = true` on the right fields.

---

## Rollback Plan

Revert the phase commit(s). No data migration, no user-facing surface changed (empty set ⇒ identical report); existing S0335/S0337 behaviour is preserved.
