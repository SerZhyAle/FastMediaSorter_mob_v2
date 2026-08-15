# Спецификация (draft): S1472 - CHANGE_WIFI_STATE объявлен без строки реестра, парити-гейт красный на standard

**Ticket:** S1472
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-07
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-07

**Захвачено во время:** S1454 (прогон `/spec-all` по блоку багов; находка вне области того тикета)

**Текст:**

```
PermissionRegistryManifestParityTest > every declared permission is a registry row or a named exemption FAILED
    java.lang.AssertionError: Declared with no registry row and no exemption:
    [android.permission.CHANGE_WIFI_STATE]. Add the row, or add it to
    PermissionManifestExemptions.declaredWithoutRow with the reason the user never decides about it.
```

Наблюдено на варианте **standard**, 2026-08-07 13:49, командой `check-standard-fast.ps1 -Mode Unit -Tests "*PermissionRegistry*"` - 12 тестов, 1 упал. XML: `app_v2/build/test-results/testStandardDebugUnitTest/TEST-..PermissionRegistryManifestParityTest.xml`.

**Что уже установлено при захвате (чтением, не предположением):**

- Разрешение объявлено в `app_v2/src/networkMonitor/AndroidManifest.xml:13`, с комментарием «Wi-Fi section: toggling the radio from the section's own control row». Это набор исходников монитора сети, то есть работа S1433.
- Находка не относится к S1454 и его правками не вызвана: тот тикет снял `READ_CONTACTS` в оверлее `lite`, добавил именованное исключение для `POST_NOTIFICATIONS` и правил тесты - ни одна из этих правок не может внести `CHANGE_WIFI_STATE` в манифест `standard`.
- Дедуп выполнен: `search.ps1` по `wifi` не дал ни одной записи.

**Почему это важно.** `docs/RELEASE_READINESS_STANDARD.md` называет этот парити-тест блокером релиза, и сейчас он красный на **основном** флейворе, а не на краевом. Канон, инвариант 13: объявляем только те разрешения, которыми среда выполнения действительно пользуется, каждое - с объяснением, видимым пользователю.

**Развилка, которую надо решить, а не угадать** (ровно та же, что разбиралась в S1454 по ADR-1 из S1442): либо у переключателя радио есть путь выполнения и тогда нужна строка реестра, либо переключение недостижимо и разрешение снимается, либо оно держится не пользовательским решением и тогда нужно именованное исключение с написанной причиной.

---

## 1. Проблема / симптом

`PermissionRegistryManifestParityTest` красный на **standard** - основном флейворе, для которого `docs/RELEASE_READINESS_STANDARD.md` называет этот тест блокером релиза. Подтверждено 2026-08-07 полным прогоном `.\a.ps1 fu`: `3217 tests completed, 2 failed`, и один из двух - именно он.

Тест считает разрешение необъяснённым, если оно объявлено в слитом манифесте, но не имеет ни строки реестра, ни именованного исключения. `CHANGE_WIFI_STATE` не имеет ни того, ни другого.

---

## 2. Корневая причина

### 2.1 Разрешение объявлено под возможность, которой в дереве нет

Проверено чтением всего дерева, а не предположением:

- `setWifiEnabled` не встречается ни в одном `.kt` во всём `app_v2/src`; строки `CHANGE_WIFI_STATE` в коде тоже нет ни одной;
- весь набор исходников `src/networkMonitor` - это два файла: `NetworkMonitorContractImpl.kt` (9 строк, `isAvailableInBuild = true`) и Hilt-модуль, который его отдаёт;
- `SUPPORT_NETWORK_MONITOR` читается ровно в одном месте продукта - при вычислении гейтов реестра разрешений.

То есть «Network Monitor» на сегодня - шов доступности возможности и ничего больше: ни экрана, ни секции Wi-Fi, ни управляющей строки, о которой говорит комментарий над объявлением. Переключатель, ради которого разрешение объявлено, не существует.

Развилка из §0 этим и решается, причём без обращения к владельцу: правило записано в самом манифесте, который это разрешение объявляет, - «a permission declared without the feature behind it is what PermissionRegistryManifestParityTest fails a release on». Ровно это и произошло. То же говорит инвариант 13 канона: объявляем только то, чем среда выполнения действительно пользуется.

Заметка памяти о том, что переключение радио на прошивках API 26-28 реально работает, здесь ничего не меняет: она про то, будет ли работать код, когда он появится, а не про то, есть ли он сейчас.

### 2.2 Почему именно это разрешение, а не соседние

В том же манифесте объявлены ещё три, и тест на них молчит - по трём разным причинам, и все три законны:

- `NEARBY_WIFI_DEVICES` и `BLUETOOTH_CONNECT` имеют строки реестра (`PermissionRegistryRepositoryImpl:314-333`, гейт `SUPPORT_NETWORK_MONITOR`);
- `BLUETOOTH_ADMIN` несёт `android:maxSdkVersion="30"`, а тест исполняется под `@Config(sdk = [33])` и читает `requestedPermissions`, которое само отфильтровано по SDK, - выше 30 это объявление просто не сообщается, поэтому тест его не видит.

`CHANGE_WIFI_STATE` - единственное без ограничения по SDK и без строки. Отсюда и одиночное имя в сообщении об ошибке.

### 2.3 Почему не исключение, а снятие

`declaredWithoutRow` предназначен для нормальных install-time разрешений, и `CHANGE_WIFI_STATE` по типу защиты именно такое - соседи `ACCESS_WIFI_STATE` и `CHANGE_WIFI_MULTICAST_STATE` уже там. Но запись туда требует причины, а причина - «этим пользуется управляющая строка секции Wi-Fi» - была бы утверждением о коде, которого нет. Файл исключений прямо запрещает такую запись: «A reason is prose for the next reader, not a formality».

Снятие обратимо и дёшево: S1433 вернёт объявление вместе с кодом переключателя и напишет причину, которая к тому моменту будет правдой.

Единственный источник объявления подтверждён отчётом слияния манифестов (`manifest-merger-blame-standard-debug-report.txt:20-22`): `src/networkMonitor/AndroidManifest.xml:13`, ни одна библиотека его не вносит. Значит снятие строки действительно убирает разрешение из слитого манифеста.

---

## 3. Исправление

Убрать объявление `CHANGE_WIFI_STATE` из `app_v2/src/networkMonitor/AndroidManifest.xml` вместе с его комментарием; в комментарии манифеста зафиксировать, что разрешение вернётся вместе с управляющей строкой.

Строки реестра и файл исключений не трогаются: добавлять туда нечего, а трогать соседние разрешения - не дело этого тикета.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1433 (владелец набора исходников `networkMonitor`, где объявлено разрешение), S1436 (ввёл парити-гейт), S1442 (ADR-1 - правило, по которому выбирается мера), S1454 (в его прогоне находка и всплыла), S1459 и S1460 (соседние расхождения того же реестра)

---

## 4. Проверка

- **Тест зелёный на основном флейворе.** `check-standard-fast.ps1 -Mode Unit -Tests "*PermissionRegistry*"` на `standard` - без отказов; в XML `failures="0"` у `PermissionRegistryManifestParityTest`.
- **Тест действительно исполнился, а не пропущен.** В отчёте `tests >= 1` и `skipped = 0` - иначе зелёный цвет ничего не значит (ровно подмена из [S1464]).
- **Разрешение ушло из слитого манифеста.** `processStandardDebugMainManifest` -> `CHANGE_WIFI_STATE` не встречается в `merged_manifest/standardDebug/`.
- **Соседние флейворы не задеты.** `noLegal` - второй и последний, монтирующий `src/networkMonitor`: `check-standard-fast.ps1 -Mode Unit -Flavor NoLegal -Tests "*PermissionRegistry*"` не содержит ни одного отказа, называющего `CHANGE_WIFI_STATE`. Полностью зелёным этот прогон быть не может и предикатом этого тикета не является: `noLegal` красный по разрешениям OpenXR - находка вне области, запаркована как [S1475].

---

## Last Audit

**Date:** 2026-08-07. **Verdict:** Verified.

- Развилка §0 решена по факту дерева, а не догадкой: `setWifiEnabled` нет ни в одном `.kt`, `src/networkMonitor` состоит из шва доступности (9 строк) и Hilt-модуля, управляющей строки Wi-Fi не существует. Значит ветка «переключение недостижимо - разрешение снимается».
- Единственный источник объявления подтверждён отчётом слияния (`manifest-merger-blame-standard-debug-report.txt:20-22`) - ни одна библиотека его не вносит.
- После снятия: `check-standard-fast.ps1 -Mode Unit -Tests "*PermissionRegistry*"` на `standard` - **exit 0**; отчёт `PermissionRegistryManifestParityTest` - `tests="3" skipped="0" failures="0" errors="0"`, то есть тест исполнился и прошёл, а не был пропущен.
- В слитом манифесте `standardDebug` не осталось ни одного `<uses-permission .. CHANGE_WIFI_STATE`.
- Побочно закрыто наблюдение S1463 о `tests=1 skipped=1` на этом тесте: там был артефакт умершего воркера, теперь `tests=3 skipped=0`.
- `noLegal` не содержит отказов по `CHANGE_WIFI_STATE`; его собственная краснота по разрешениям OpenXR - вне области, запаркована как [S1475].
