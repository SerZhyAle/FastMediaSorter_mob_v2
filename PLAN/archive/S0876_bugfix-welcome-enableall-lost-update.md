# Спецификация (compact bugfix): S0876 - WelcomeEnableAllManager - lost-update между конкурентными full-snapshot писателями настроек

**Ticket:** S0876
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: CONFIRMED P1 (2026-07-02, dedicated skeptic). Actually a THREE-writer race, wider than filed. Mechanics: enqueueAndEnableOnInstall (:179-192) is invoked for OCR_ENGINES (:163) and TRANSLATION (:166), each spawning an independent appScope.launch (:184) doing getSettings().first() (:188) -> updateSettings(enable(current)) (:189); no Mutex/synchronized in this file or SettingsRepositoryImpl (grep zero). SettingsRepositoryImpl.updateSettings (:527) is NOT a transform-update: dataStore.edit block (:556+, ~250 lines) unconditionally stamps EVERY field from the caller snapshot, zero live-preferences merge-reads (grep zero) - stale snapshot silently reverts the other writer's flag (enableOcr/enableTranslation stamped via TextRecognitionSettingsStore.write, SettingsRepositoryImpl:610). @ApplicationScope = CoroutineScope(ioDispatcher + SupervisorJob) (AppModule.kt:90-91) - genuinely multi-threaded. Third writer: ApplyEnableAllSettingsUseCase (start() :106, same read-modify-write idiom per its own doc comment :21) races the two deliverable writers with no join (:111 -> :163/:166 not joined). Trigger: Enable all -> both models install in close succession (fast connection / cache-warm) -> installed engine left silently disabled (violates S0386 enable-only-after-install). Fix shape: single serialized writer - Mutex around read-modify-write, or per-field DataStore transform update instead of full-snapshot stamp.

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeEnableAllManager.kt:184** - Lost-update race between concurrent full-snapshot settings writers in Enable-all deliverable completion (enableOcr/enableTranslation can silently revert)
  - Evidence: enqueueAndEnableOnInstall() launches one appScope coroutine per deliverable set (OCR_ENGINES at line 163, TRANSLATION at line 166): `appScope.launch { val terminal = downloadRunner.progressOf(set).first { ... }; if (terminal is DownloadProgress.Installed) { val current = settingsRepository.getSettings().first(); settingsRepository.updateSettings(enable(current)) } }` (lines 184-191). SettingsRepositoryImpl.updateSettings(settings: AppSettings) (line 527) persists the ENTIRE AppSettings snapshot inside dataStore.edit (line 556); the `getSettings().first()` read happens outside the transaction and the file contains no Mutex/synchronized/withLock. Runtime path: Enable-all enqueues both sets; both coroutines wake on Installed; interleaving D.read -> E.read -> D.write(enableOcr=true) -> E.write(full snapshot built from the pre-D read, i.e. enableOcr=false, enableTranslation=true) silently reverts enableOcr although its engine installed - violating the S0386 enable-only-after-install invariant in the opposite direction (installed but disabled). The same unserialized read-modify-write class is asserted safe by comments that only reduce, not eliminate, the window: ApplyEnableAllSettingsUseCase.kt:21 ('Read-modify-write of the latest snapshot so a concurrent profile/preset write is not clobbered') and WelcomeFunctionalityController.kt:429 ('so concurrent toggle taps do not clobber each other's fields') - two in-flight writers on the multi-threaded @ApplicationScope are not serialized by reading the latest snapshot. Per docs/CODE_AUDIT_PROTOCOL.md Layer 2 ('shared mutable state ... no read-modify-write race') and severity taxonomy P1 ('data race on shared mutable state').
  - Fix hint: Add a transform-based SettingsRepository.updateSettings((AppSettings)->AppSettings) whose read+fold happens inside dataStore.edit (or serialize all writers behind a repository-level Mutex), then migrate the welcome writers (enqueueAndEnableOnInstall, persist(), applySettingsOnly, ApplyEnableAllSettingsUseCase) to it.

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

WelcomeEnableAllManager - lost-update между конкурентными full-snapshot писателями настроек. Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

`SettingsRepositoryImpl.updateSettings(AppSettings)` пишет ПОЛНЫЙ снапшот всех полей, а каждый писатель делал `getSettings().first()` + `updateSettings(copy(..))` вне какой-либо сериализации. Три конкурентных писателя на многопоточном `@ApplicationScope` (два `enqueueAndEnableOnInstall`-корутины OCR/Translation + `ApplyEnableAllSettingsUseCase`) - поздний писатель со стейл-снапшотом молча откатывал флаг раннего (`enableOcr`/`enableTranslation`), нарушая инвариант S0386 enable-only-after-install в обратную сторону (установлен, но выключен).

---

## 3. Исправление

- `SettingsRepository`: добавлен transform-overload `updateSettings(transform: suspend (AppSettings) -> AppSettings)` (KDoc фиксирует контракт сериализации).
- `SettingsRepositoryImpl`: `transformMutex` (`Mutex.withLock`) вокруг read+fold+write - поздний писатель складывается на закоммиченный результат предыдущего, не на стейл-снапшот.
- Мигрированы все конкурирующие за это окно писатели: `WelcomeEnableAllManager` (enqueueAndEnableOnInstall x2 + enableStreams), `WelcomeFunctionalityController.persist`, `WelcomeRemoteSourcesController.persist`, `WelcomeViewModel` (2 места), `ApplyEnableAllSettingsUseCase`, `ApplyProfilePresetUseCase.applySettingsOnly`.
- `ApplyProfilePresetUseCase`: ветка "skip if nothing changed" удалена - no-op гарантирует S0018 idempotency guard внутри снапшот-overload.
- Оставшиеся `getSettings().first()` в welcome (`WelcomeRemoteSourcesController:36`, `WelcomeFunctionalityController:94`) - read-only чтение для UI-биндинга, не писатели.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

- `SettingsRepositoryImplTest` - регрессионный тест сериализации mutex: два конкурентных read-modify-write не теряют обновление.
- `ApplyEnableAllSettingsUseCaseTest`, `ApplyProfilePresetUseCaseTest` - контракт transform-overload.
- Прогон: `:app_v2:testStandardDebugUnitTest --tests <3 класса>` - 14/14 PASS (2026-07-02).
- Девайс-тест не требуется: гонка недетерминирована на устройстве, доказательство - unit-регрессия.

---

## Last Audit

**Date:** 2026-07-02
**Verdict:** Verified

- Fix already present in working tree at audit time (drift-check: 12 `S0876:` markers in 10 files, review-mode closure).
- `SettingsRepositoryImpl.kt:726-731` - transform overload guarded by `transformMutex.withLock { getSettings().first() -> transform -> updateSettings(snapshot) }`.
- All Welcome-window writers route through the transform overload (grep: zero remaining read-modify-write pairs among writers; the two remaining `getSettings().first()` sites are read-only UI binds).
- Stale test expecting "no updateSettings call when no overrides" updated to the new identity-transform contract (`ApplyProfilePresetUseCaseTest`); dead helper `captureTransformResult()` removed.
- Validation: `testStandardDebugUnitTest` for `SettingsRepositoryImplTest` (4), `ApplyEnableAllSettingsUseCaseTest` (3), `ApplyProfilePresetUseCaseTest` (7) - expected: 0 failures | actual: 0 failures.

