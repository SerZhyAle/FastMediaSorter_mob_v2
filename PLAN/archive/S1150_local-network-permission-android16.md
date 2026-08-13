# Стратегическая спецификация: S1150 - Готовность к разрешению на локальную сеть (Android 16/17)

**Status:** Archived
**Ticket:** S1150
**Priority:** 40
**Date:** 2026-07-22
**Tier:** 3 - Moderate
**Parent:** S1149 (bump targetSdk 36) - находка аудита поведения Android 16

<!-- auto-approved by /spec-all - 2026-07-23 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-22 (при аудите Android 16 в рамках S1149)

- Android вводит отдельное разрешение на доступ к локальной сети (mDNS/SSDP/NsdManager/raw sockets/multicast). В Android 16 - фаза **opt-in** (затрагивает только явно включивших enforcement), в Android 17 (API 37) enforcement обязателен для приложений с `targetSdk >= 37`.
- Источник: developer.android.com/about/versions/16/behavior-changes-16 (Local Network Protections) и developer.android.com/privacy-and-security/local-network-permission. Приложение может включить enforcement для теста через `adb shell am compat enable RESTRICT_LOCAL_NETWORK <pkg>` + reboot.

**Затронутый код FMS (найдено грепом):**

- `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/CompanionMdnsDiscovery.kt` - `NsdManager` (mDNS/DNS-SD) + `WifiManager.MulticastLock` для обнаружения десктоп-компаньона (fms_companion) в LAN.
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DiscoverNetworkResourcesUseCase.kt:191` - сканирование сети через `InetAddress.getByName` + `Socket.connect`.

---

## 1. Проблема и текущее состояние (уточнено при /spec-all 2026-07-23)

**Финальная политика уже известна, а базовая обвязка уже реализована** - при парковке из S1149 не был проверен уже существующий тикет S0035.

Установленные факты:

- Разрешение финализировано как `android.permission.ACCESS_LOCAL_NETWORK`.
- Enforcement - **Android 17 / API 37**, только для `targetSdk >= 37`. В Android 16 (API 36) - opt-in, по умолчанию не форсится. FMS на targetSdk 36 - под enforcement НЕ попадает.
- Ядро разрешения уже поставлено тикетом **S0035** (Verified):
  - декларация в `app_v2/src/main/AndroidManifest.xml:26` (`android:minSdkVersion="37"`), удалена из `lite` тикетом S0448;
  - строковый литерал `PermissionHelper.LOCAL_NETWORK_PERMISSION` + `LOCAL_NETWORK_API = 37` (константа `Manifest.permission.*` недоступна на compileSdk < 37);
  - runtime-запрос, rationale-диалог и маршрут permanent-deny в `BrowseActivity`/`AddResourceActivity`;
  - `LocalNetworkPermissionDeniedException` + классификация `SecurityException` в `NetworkErrorClassifier`;
  - S0614/S0625 - грант `ACCESS_LOCAL_NETWORK` в prerelease-tooling на API >= 37.

**Остаточная брешь (в двух путях из §0):**

- `CompanionMdnsDiscovery.startDiscovery()` ловит только `IllegalArgumentException`, а колбэк `onStartDiscoveryFailed` - пустой (`= Unit`). При асинхронном отказе старта (mDNS заблокирован сетью сегодня, либо отказ `ACCESS_LOCAL_NETWORK` на API 37+) удержанный `MulticastLock` **утекает**, а `discoveryListener` остаётся выставленным - повторный старт в той же foreground-сессии блокируется до `onStop`. На API 37+ синхронный `SecurityException` из `discoverServices` мог бы уйти неперехваченным в колбэк `ProcessLifecycleOwner.onStart`.
- `DiscoverNetworkResourcesUseCase` - все сокет-пути (`isTcpPortOpen`, `resolveHost`, `getLocalIpAddress`) уже ловят `Exception` широко, поэтому при отказе разрешения сканирование деградирует к пустому результату без падения. Изменений не требует.

## 2. Объём (narrowed)

- Укрепить `CompanionMdnsDiscovery`: единый путь очистки старт-отказа - освобождать `MulticastLock` и сбрасывать `discoveryListener` и в синхронном `catch`, и в `onStartDiscoveryFailed`; добавить перехват `SecurityException` (деградация к config-fallback S1006, без падения).
- Подтвердить, что `DiscoverNetworkResourcesUseCase` уже деградирует безопасно (широкий `catch`) - без правок.
- Подтвердить, что декларация/запрос/rationale для основного сетевого пути уже поставлены S0035 - без правок.

Изящная деградация (требование §0) выполняется: при отказе разрешения companion-discovery и сетевое сканирование недоступны, но приложение работает - ручной ввод адреса остаётся.

## 3. Связанные тикеты

- S1149 - родительский bump targetSdk 36 (эта находка запаркована оттуда).
- S0035 - поставил ядро разрешения `ACCESS_LOCAL_NETWORK` (декларация, запрос, rationale, классификация).
- S0448 - удалил `ACCESS_LOCAL_NETWORK` из merged-манифеста `lite`.
- S0614 / S0625 - грант `ACCESS_LOCAL_NETWORK` в prerelease-tooling (API >= 37).
- S0421 - fms_companion (потребитель mDNS-обнаружения).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1149, S0035, S0448, S0614, S0625, S0421

## Примечание

Не блокирует S1149: под targetSdk 36 enforcement не активен (Android 17/API 37, `targetSdk >= 37`). Полный runtime-путь запроса для mDNS/скана на устройстве проверяем только при будущем bump до targetSdk 37 на Android 17 - текущее устройство (Android 15) enforcement не воспроизводит. Правка этого тикета - защитная (устранение утечки lock и потенциального падения), проверяется инспекцией + сборкой, on-device gate не требует.

## Last Audit

**Audited:** 2026-07-23 (/spec-all review mode). **Verdict:** Verified.

- Ядро `ACCESS_LOCAL_NETWORK` (декларация, runtime-запрос, rationale, permanent-deny, классификация `SecurityException`) поставлено S0035 (Verified) - код присутствует: `AndroidManifest.xml:26` (`minSdkVersion=37`), `PermissionHelper.LOCAL_NETWORK_PERMISSION`/`LOCAL_NETWORK_API=37`, `NetworkErrorClassifier`, `LocalNetworkPermissionDeniedException`, обработчики в `BrowseActivity`/`AddResourceActivity`. Изменений не требовало.
- `DiscoverNetworkResourcesUseCase` - все сокет-пути ловят `Exception` широко (`isTcpPortOpen`, `resolveHost`, `getLocalIpAddress`), деградирует к пустому результату без падения. Изменений не требовало.
- `CompanionMdnsDiscovery` - укреплён: старт-отказ (синхронный `IllegalArgumentException`/`SecurityException` и асинхронный `onStartDiscoveryFailed`) теперь идёт через `abortStart()`, освобождающий `MulticastLock` и сбрасывающий `discoveryListener`; ранее асинхронный отказ утекал lock и заклинивал повторный старт до `onStop`. Деградация к config-fallback (S1006) сохранена.
- **Evidence:** `.\a.ps1 dq` -> `BUILD SUCCESSFUL in 52s`, exit 0 (лог `temp/build_debug_20260723_012106.log`). Корректность освобождения ресурса на обеих ветвях отказа - инспекцией. Enforcement-путь (API 37 + targetSdk 37) на текущем Android 15 устройстве не воспроизводим и не требуется для этой защитной правки; on-device проверка отложена до bump targetSdk 37.
- **Residual/follow-up:** проактивный запрос `ACCESS_LOCAL_NETWORK` перед mDNS/subnet-сканом (вместо реактивного через основной путь) - низкоценно и непроверяемо до targetSdk 37; не открывается отдельным тикетом.
