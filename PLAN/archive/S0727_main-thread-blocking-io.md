# Спецификация (fix): S0727 - Снять блокировку Main-потока на disk/network IO (main-safety)

**Ticket:** S0727
**Status:** Archived
**Priority:** 60
**Date:** 2026-06-26
**Tier:** 2 - Bugfix
**Roadmap entry:** Ad-hoc - находки аудита S0716 (Layer 2, кластер P1+P2 main-safety/blocking)
**Umbrella:** S0714

> **Scope:** Перенос блокирующего IO с Main-потока. Найдено статически (S0716), ANR-риск.

---

## 0. Источник

Кластер находок аудита S0716 (`PLAN/S0716_concurrency-correctness-audit/AUDIT_FINDINGS.md`) измерений «blocking on Main» и «main-safety»: 3×P1 + 2×P2. Все - `runBlocking`/прямой блокирующий IO на UI-потоке.

## 1. Находки и правки

1. **P1 - `src/standardScreenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayControllerImpl.kt:66` (+ noLegal-двойник :123/:127).** `isEnabled()`/`readStripVisible()` делают `runBlocking { settingsRepository.getSettings().first() }` (холодный DataStore + per-emission SharedPreferences IO). Оба вызывающих - на Main: `OperationsGesturesManager` (`setOnCheckedChangeListener`) и `ScreenGestureOverlayStartupCoordinator` (`withContext(Main.immediate)`). **Fix:** сделать `setEnabled`/`setStripVisible` suspend или читать настройки off-Main; вызывающие берут `gestureOverlayEnabled`/`screenshotGestureStripVisible` через корутину заранее.
2. **P1 - `data/network/lifecycle/SftpConnectionGate.kt:58` `closeFor`.** `ProcessLifecycleOwner.ON_STOP` → `NetworkLifecycleObserver.onStop()` (Main) → `gate.closeFor()` inline → `runBlocking { client.disconnectAllPool() }` = синхронный SSH-teardown на Main при каждом сворачивании. Sibling `disconnectAllOnNetworkChange()` уже использует `cleanupScope.launch(IO)`. **Fix:** заменить `runBlocking` на launch в инжектированном IO-`CoroutineScope`; применить к прочим socket-гейтам.
3. **P1 - `widget/ScheduledTasksWidgetProvider.kt:123` `updateAppWidget`/`onUpdate`.** `onUpdate` (BroadcastReceiver, Main) зовёт `updateAppWidget` напрямую; `runBlocking` собирает два холодных disk-Flow (Room `getAll()` + DataStore `getSettings()`). Sibling `onReceive` уже использует `goAsync()+IO`. **Fix:** обернуть цикл `updateAppWidget` в `goAsync()` + `CoroutineScope(SupervisorJob()+IO).launch { .. finally pendingResult.finish() }`.
4. **P2 - `ui/main/MainActivity.kt:733`.** `setupViews()` (в `binding.root.post{}` на Main) делает `runBlocking { getSettings().first().allowSeparateWindow }` - disk-IO stall (после первого кадра, кэш DataStore часто тёплый, но не гарантированно). **Fix:** убрать `runBlocking`; применять `allowSeparateWindow` к адаптеру асинхронно через существующий `collectOnLifecycle(getSettings())` (MainActivity:1016) или `lifecycleScope.launch`.
5. **P2 - `domain/usecase/ImportSettingsUseCase.kt:53` `invoke`.** Нет `withContext` во всём файле; блокирующий IO (MediaStore query, `FileInputStream.readBytes`, XmlPullParser) исполняется на Main (вызывается из `viewLifecycleOwner.lifecycleScope.launch` = Main.immediate). Пользовательский импорт, ANR-риск ограничен. **Fix:** обернуть тело `invoke` (и `ApplyBackupPayloadUseCase.invoke`) в `withContext(Dispatchers.IO)` на границе use-case (осторожно: много return-точек в длинной функции).

## 2. Критерии приёмки

- [x] Ни один из пяти путей не исполняет disk/network IO на Main (подтверждено чтением диспетчера): SFTP-гейт `applicationScope.launch`; widget `runBlocking` теперь под `goAsync()+IO`; import/apply `withContext(IO)`; MainActivity setupViews без `runBlocking`; оба overlay-близнеца без `runBlocking` (значения передают вызывающие).
- [x] Поведение сохранено (без гонок старта): StartupCoordinator читает снимок настроек off-Main и передаёт `stripVisible`; OperationsGesturesManager берёт `viewModel.settings.value`; Welcome - `initialStripVisible`. Билды standard+noLegal зелёные.
- [x] Виджет: `goAsync()` завершается `pendingResult.finish()` в `finally`.

## 3. Связанные тикеты

- S0716 (аудит-источник), S0714 (зонтик).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0716, S0714
- **Flavor:** находка №1 правит обе flavor-копии `ScreenGestureOverlayControllerImpl` (`standardScreenCapture` + `noLegal`) синхронно; интерфейс `ScreenGestureOverlayController` изменён (методы берут настройки параметрами вместо чтения из DataStore).
- **Data:** находка №5 меняет только диспетчер исполнения (`withContext(IO)`), не формат настроек/бэкапа.
- **API:** публичных API-изменений нет; правки внутренние (диспетчеры/корутины).

## Last Audit

**Date:** 2026-06-26
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

### Manual / on-device

- [ ] (optional) Smoke-test the user-facing runtime paths on device: gesture-overlay enable/strip-visible toggle, Scheduled Tasks widget refresh, settings/backup import. Static dispatcher audit + standard/noLegal builds already green; this is a confidence check, not a gate.
