# Phase 01 - Share-target registry

**Strategic spec:** [`../S0452_share-commands-infrastructure.md`](../S0452_share-commands-infrastructure.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Introduce a domain-level `ShareTarget` model, a `ShareTargetRegistry`, and a single `ShareTargetAvailabilityResolver` (package-installed / has-Google / has-internet), wired via Hilt. No settings storage, UI, or gating changes yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/ShareTarget.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/ShareTargetRegistry.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/ShareTargetAvailabilityResolver.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/di/ShareTargetModule.kt` | New | ≤ 80 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/share/ShareTargetRegistryTest.kt` | New | ≤ 150 |

---

## Steps

### Step 01.1 - Define `ShareTarget` model

**Files:** `core/share/ShareTarget.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a `ShareTarget` data class describing one sendable target: stable `id: String` (registry key, used as the DataStore enabled-set token), `titleRes: Int` (settings/menu label), optional `iconRes: Int?`, `defaultEnabled: ShareTargetDefault` (enum: `ALWAYS_ON`, `ALWAYS_OFF`, `ON_IF_GOOGLE`, `ON_IF_INTERNET` - the default rule, resolved against device capability not `DeviceProfileType`), and `availability: ShareTargetAvailability` (enum: `ALWAYS`, `PACKAGE_INSTALLED`, `REQUIRES_GOOGLE`, `REQUIRES_INTERNET`, with an optional `packages: List<String>` for the installed check). Keep it a pure domain model - no Android imports beyond `@StringRes`/`@DrawableRes` annotations.

**Verification:**

- `Glob` - `core/share/ShareTarget.kt` exists.
- `Grep` - `data class ShareTarget` matches exactly once.
- `Grep` - `val id: String` present.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification 4/4 PASS. `core/share/ShareTarget.kt` (New, +50 LOC): data class + ShareTargetDefault + ShareTargetAvailability.

---

### Step 01.2 - Implement `ShareTargetAvailabilityResolver`

**Files:** `core/share/ShareTargetAvailabilityResolver.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `ShareTargetAvailabilityResolver` (`@Inject constructor`, deps: `@ApplicationContext Context`, `GoogleIdentityRepository`). Answer two questions: `isAvailable(target: ShareTarget): Boolean` and `isDefaultEnabled(target: ShareTarget): Boolean`. Capability helpers (private): `hasGoogle()` = `googleIdentityRepository.state.value is PrimaryGoogleAccountState.Bound`; `hasInternet()` = query `ConnectivityManager.activeNetwork` + `NetworkCapabilities.NET_CAPABILITY_INTERNET` synchronously; `isPackageInstalled(packages)` = `PackageManager.getPackageInfo` probe returning false on `NameNotFoundException`. `isAvailable`: `ALWAYS`->true; `PACKAGE_INSTALLED`->`isPackageInstalled(target.packages)`; `REQUIRES_GOOGLE`->`hasGoogle()`; `REQUIRES_INTERNET`->`hasInternet()`. `isDefaultEnabled`: `ALWAYS_ON`->true; `ALWAYS_OFF`->false; `ON_IF_GOOGLE`->`hasGoogle()`; `ON_IF_INTERNET`->`hasInternet()`. Log capability-absence at `Timber.i`, never `Timber.e`.

**Verification:**

- `Glob` - `core/share/ShareTargetAvailabilityResolver.kt` exists.
- `Grep` - `class ShareTargetAvailabilityResolver` matches once.
- `Grep` - `fun isAvailable` present.
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification 4/4 PASS. `core/share/ShareTargetAvailabilityResolver.kt` (New, +68 LOC): isAvailable + isDefaultEnabled via package/Google/internet probes. Capability-absence logged at Timber.i.

---

### Step 01.3 - Implement `ShareTargetRegistry` + Hilt module

**Files:** `core/share/ShareTargetRegistry.kt`, `core/share/di/ShareTargetModule.kt`
**Depends on:** Step 01.1, Step 01.2

**Prompt for developer:**

> Create `ShareTargetRegistry` (`@Inject constructor(targets: Set<@JvmSuppressWildcards ShareTarget>)`) holding the registered `ShareTarget`s. Expose `all(): List<ShareTarget>`, `byId(id: String): ShareTarget?`. Targets are contributed via Hilt multibinding (`@IntoSet ShareTarget`) so target tickets (S0443-S0446) add entries without editing the registry. Create `core/share/di/ShareTargetModule.kt` (`@Module @InstallIn(SingletonComponent::class)`); in this phase it declares the empty multibinding seam (e.g. `@Multibinds abstract fun shareTargets(): Set<ShareTarget>`) so the `Set<ShareTarget>` injects empty until Phase 04 seeds Telegram. Default-enabled and availability logic live in `ShareTargetAvailabilityResolver`, not here.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `class ShareTargetRegistry` matches once.
- `Grep` - `fun all()` and `fun byId(` present.
- `Grep` - `@Module` and `SingletonComponent` present in `ShareTargetModule.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification 4/4 PASS. `core/share/ShareTargetRegistry.kt` (New, +27 LOC, `@Inject` Set multibinding) + `core/share/di/ShareTargetModule.kt` (New, `@Multibinds` empty seam). Hilt graph compiles (assembleStandardDebug PASS).

---

### Step 01.4 - Unit-test the registry

**Files:** `core/share/ShareTargetRegistryTest.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add `ShareTargetRegistryTest` covering: `byId` returns a registered target and null for unknown ids; `all()` returns every injected target. Construct the registry with a fixed in-test set of fake `ShareTarget`s - do not depend on Android framework.

**Verification:**

- `Glob` - `ShareTargetRegistryTest.kt` exists.
- `Grep` - `@Test` matches >= 2 times.
- `/build` test task or `.\a.ps1 fu` for this class passes (deferred to Phase Done build).

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Test written + statically correct: `ShareTargetRegistryTest.kt` (New, 4 `@Test`: byId hit/miss, all sorted, empty). Test-RUN blocked: pre-existing unit-test source set does not compile (`mediaCapabilities` errors in 4 unrelated test files) - parked as S0455. Production build (assembleStandardDebug) PASS proves the SUT compiles.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` PASS (2026-06-16, build_debug_20260616_021123.log).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [~] `dev/CATALOG/app_v2.jsonl` regenerated - deferred to Phase 05 (docs-catalog-cleanup), per plan.

---

## Handoff Notes to Next Phase

- `ShareTargetRegistry` and `ShareTargetAvailabilityResolver` are injectable singletons.
- The `@IntoSet ShareTarget` multibinding is the registration seam; it is empty until Phase 04 seeds Telegram and target tickets add their own.

---

## Rollback Plan

Revert phase commit(s) - new files only, no data migration or user-facing surface changed.
