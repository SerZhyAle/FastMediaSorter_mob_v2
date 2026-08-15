# S0434 - standardDebugUnitTest source set fails to compile (constructor drift)

**Ticket:** S0434
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-15
**Tier:** 2 - Simple (ad-hoc test fix)

## Goal

Восстановить компиляцию unit-test source set `app_v2`. Три теста перестали компилироваться после того, как их продакшн-конструкторы получили новые зависимости, а инстанцирования в тестах не обновили. Весь test source set гейтится одной компиляцией, поэтому ни один unit-тест модуля не запускался. Цель - добавить недостающие аргументы и подтвердить чистую компиляцию + прохождение затронутых классов.

## 0. Raw capture (auto-parked during S0423 implementation, 2026-06-15)

Symptom
- `:app_v2:compileStandardDebugUnitTestKotlin` fails, so no unit test in the module can run (the whole test source set is gated behind one compile).

Evidence (compile errors)
- `AppStartupInitializerTest.kt:32` - `No value passed for parameter 'remoteSourceDisableCoordinator'`.
- `SyncNetworkResourcesUseCaseTest.kt:28` - `No value passed for parameter 'remoteSourceGate'`.
- `StandalonePlayerViewModelTest.kt:49` - `No value passed for parameter 'materializeUriToFileUseCase'`.

Cause
- Production constructors (`AppStartupInitializer`, `SyncNetworkResourcesUseCase`, `StandalonePlayerViewModel`) gained new dependencies without updating their tests.

## Resolution

- [AppStartupInitializerTest.kt](app_v2/src/test/java/com/sza/fastmediasorter/core/init/AppStartupInitializerTest.kt): added `remoteSourceDisableCoordinator = mockk<RemoteSourceDisableCoordinator>(relaxed = true)`.
- [SyncNetworkResourcesUseCaseTest.kt](app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/SyncNetworkResourcesUseCaseTest.kt): added a `RemoteSourceAvailabilityGate` mock stubbed `isEnabled(any<MediaResource>()) returns true` so the S0391 availability filter does not short-circuit sync, then passed it as the 4th constructor argument. A relaxed mock would default `isEnabled` to `false` and break the count assertions, so the stub is required.
- [StandalonePlayerViewModelTest.kt](app_v2/src/test/java/com/sza/fastmediasorter/ui/player/StandalonePlayerViewModelTest.kt): added `materializeUriToFileUseCase = mockk<MaterializeUriToFileUseCase>(relaxed = true)` (named arg; the materialize call runs on the un-advanced `StandardTestDispatcher`, so a relaxed mock is safe).

## Phases

### Phase 01 - Restore test compilation and verify touched classes

1. Add the missing constructor argument to each of the three tests using the existing mockk pattern.
   - Verification: `:app_v2:compileStandardDebugUnitTestKotlin` -> `BUILD SUCCESSFUL` (20s, 2026-06-15). PASS.
2. Run the three touched classes to confirm runtime correctness (not just compilation).
   - Verification: `:app_v2:testStandardDebugUnitTest --tests *AppStartupInitializerTest --tests *SyncNetworkResourcesUseCaseTest --tests *StandalonePlayerViewModelTest` -> `BUILD SUCCESSFUL`; per-class XML: 1/0/0, 7/0/0, 3/0/0 (tests/failures/errors). PASS.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** parked during S0423 (translation delivery) implementation; constructor drift originated from unrelated production changes. No code dependency.

## Last Audit

**Date:** 2026-06-15
**Mode:** compact (spec-all Simple path)
**Outcome:** Verified
**Counts:** PASS 2 · WARN 0 · FAIL 0 · MANUAL 0

> The three constructor-drift compile errors are resolved. `:app_v2:compileStandardDebugUnitTestKotlin` returns `BUILD SUCCESSFUL`, re-opening the whole unit-test gate. The three touched classes pass at runtime (1+7+3 tests, 0 failures). Scope is the compilation break only; any remaining runtime failures in other pre-existing unit tests are a separate known hotspot and out of this ticket's scope per the raw capture's fix sketch. No device verification required.
