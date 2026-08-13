# Стратегическая спецификация: S0336 - Расширенная System info для noLegal

**Ticket:** S0336
**Status:** Archived
**Implemented date:** 2026-06-03
**Priority:** 50
**Date:** 2026-06-03
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-03
**Tactical spec:** `PLAN/S0336_nolegal-extended-system-info/`
**Tactical plan:** `PLAN/S0336_nolegal-extended-system-info/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классо в, путей, лимитов строк, миграций Room, модулей Hilt.

---
## 1. Проблема

S0335 добавляет общий System info диалог с безопасным базовым набором данных для всех вариантов сборки. Для noLegal версии может быть полезен более подробный диагностический профиль, потому что эта сборка не ограничена публичной витриной Google Play и может включать sideload-only возможности, VR/XR, GPL-зависимости и расширенную локальную диагностику.

При этом отсутствие Google Play ограничений не отменяет Android sandbox, runtime permissions и приватность пользователя. Нужна отдельная спека, которая сначала исследует допустимый и полезный набор noLegal-only полей, а затем реализует его без утечки в публичные flavor-ы.

---

## 2. Цели

1. Определить noLegal-only набор расширенных диагностических данных, полезных для поддержки и отладки.
2. Реализовать расширение поверх существующего System info опыта, не дублируя базовый диалог из S0335.
3. Изолировать noLegal-only сбор данных и UI-поведение от публичных flavor-ов.
4. Защитить чувствительные значения через явную редакцию, скрытие или отдельное подтверждение перед копированием/отправкой.
5. Зафиксировать, какие поля остаются недоступными из-за Android ограничений или требуют неприемлемых разрешений.

**Non-goals:**

- Не менять базовый набор S0335 для `standard`, `lite`, `photos` и `legacy`.
- Не добавлять автоматическую телеметрию, фоновые отчёты или отправку данных без действия пользователя.
- Не запрашивать опасные разрешения только ради заполнения диагностического диалога.
- Не собирать неизменяемые персональные идентификаторы, аккаунты, точное местоположение или содержимое пользовательских файлов.
- Не публиковать noLegal-only описание в публичных `FEATURES` документах.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Найти, что можно добавить в noLegal версию сверх базовой System info.
2. Сделать это как связку research + execution после утверждения спеки.
3. Отдельно оценить данные, которые Google Play сборка не должна показывать или даже иметь в кодовой поверхности.
4. Сохранить формат, удобный для копирования в баг-репорт.

### 3.2 Жёсткие ограничения

- **Flavor:** noLegal only. Любая реализация должна жить за flavor-границей, без новых noLegal-гейтов в общем коде.
- **API level:** noLegal наследует основную Android 8.0+ линию; любые API-специфичные поля должны иметь graceful fallback.
- **Wear OS:** не затрагивается.
- **Производительность:** снимок диагностики собирается по запросу и не блокирует UI.
- **Совместимость данных:** persistent-хранилище и миграции не затрагиваются.
- **Приватность:** чувствительные значения не попадают в копируемый текст без явного решения в §6.
- **Локализация:** видимые UI-строки EN/RU/UK обязательны; технические ключи внутри отчёта могут быть фиксированными английскими, если это подтвердит owner gate.
- **Доступность:** если появится новый noLegal UI-элемент, он должен поддерживать touch, mouse, keyboard и D-pad на уровне соседних действий.
- **Коммуникация:** предупреждения, заголовки и действия соответствуют `docs/COMMUNICATION_POLICY.md`; техническое тело отчёта не переписывается в дружелюбный стиль.
- **Feature docs:** noLegal-only описание идёт только в `docs/FEATURES_noLegal.md` и зеркала, не в публичные `docs/FEATURES*.md`.

### 3.3 Owner inputs (Approval gate)

- **Success signal:** В noLegal-сборке в диалоге «Сведения о системе» (System info) появляется расширенный набор диагностических данных (сверх базового набора S0335/S0337). В публичных (standard, lite и др.) сборках эти данные отсутствуют и код их сбора полностью исключен на этапе компиляции.
- **Autonomy rule:** Агент имеет право расширять или корректировать набор noLegal-полей в процессе тактического проектирования на основе технических возможностей и доступности API, фиксируя изменения в тактической спецификации.
- **UI design & placement:** Расширенные noLegal-данные отображаются в общем диалоге «Сведения о системе» в виде дополнительного раздела «noLegal Diagnostics» (или аналогичного), расположенного после базовых разделов. Чувствительные данные (например, локальные пути, отпечатки подписи) маскируются по умолчанию (redaction policy).
- **Related tickets:** S0335 (базовый диалог System info), S0337 (расширенная общая диагностика и бенчмарки), S0156 (noLegal capability surface audit - изоляция flavor-ов).
- **Owner sign-off:** 2026-06-03 (ad-hoc запрос владельца).

---

## 4. Контекст текущей архитектуры

S0335 уже создаёт общий вход в настройках и общий текстовый опыт: пользователь открывает System info, видит сгруппированную техническую сводку, может её скопировать или отправить через системный share. Это должно остаться базовым слоем, доступным всем поддерживаемым flavor-ам.

noLegal уже является sideload-only flavor-ом с отдельной поверхностью возможностей: VR/XR, GPL extractor stack, Python runtime и другие компоненты не должны протекать в публичные сборки. Новая спека должна рассматривать noLegal расширение как дополнительный диагностический вклад, а не как расширение общего продукта.

---

## 5. Предлагаемый подход

Сохранить S0335 и S0337 как базовый общий System info слой. Для noLegal добавить отдельный flavor-specific поставщик расширенной диагностики, который собирает специфичные и чувствительные данные только в noLegal сборке и полностью изолирован от остальных flavor-ов на этапе компиляции.

### 5.1 Основные столпы / модули

- **Базовый отчёт (Common + Extended).** Базовая сводка S0335/S0337 остаётся без изменений.
- **noLegal расширение.** Дополнительный поставщик данных, собирающий noLegal-диагностику.
- **Каталог диагностических полей noLegal.** Набор полей разделён на 7 основных логических блоков:
  1. **OS & Security:** Root-статус (su, busybox, Magisk, KernelSU, APatch), статус SELinux (Enforcing/Permissive/Disabled), статус Debug/Developer режимов (`DEVELOPMENT_SETTINGS_ENABLED`, `adb_enabled`), статус Xposed/LSPosed.
  2. **Permissions Audit:** Фактическое состояние специальных разрешений (`MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`).
  3. **Installer & Signature:** Имя пакета установщика (Installer Package Name, e.g. F-Droid, adb shell), SHA-256 хэш сертификата подписи APK (проверка целостности), наличие альтернативных магазинов.
  4. **noLegal Runtimes:** Статус сред окружения Python (версия, путь, `yt-dlp` версия/дата, бинарники `ffmpeg`/`ffprobe`), PaddleOCR/PaddleLite (файлы моделей, статус загрузки), Tesseract (локальные файлы `.traineddata`), OpenXR/VR SDK (активный OpenXR runtime, VR companion APK badge, VR profile).
  5. **Mounts & File System:** Список точек монтирования (из `/proc/mounts` при доступности) или перечень внешних директорий, размеры системных разделов `/data` и `/cache`.
  6. **Network Diagnostics:** Настроенные адреса DNS-серверов, активные VPN-интерфейсы (`tun0`, `ppp0`), системные настройки HTTP-прокси.
  7. **Process Resources & Logs:** Native Heap (выделенная нативная память), количество открытых файловых дескрипторов (из `/proc/self/fd`), общее число активных потоков процесса, кольцевой буфер последних 50 строк логов Timber.
- **Приватность и экспорт.** Маскирование чувствительных полей (сертификаты, системные пути монтирования, локальные DNS/VPN адреса) по умолчанию, с возможностью экспортировать полный отчёт при явном выборе действия.

### 5.2 Потоки данных и событий

Пользователь открывает System info в noLegal сборке → фоновый асинхронный поток собирает базовую сводку (S0335/S0337) → noLegal расширение опрашивает специфичные источники данных → собранные данные проходят через фильтр маскирования (Redaction policy) → UI отображает разделы и добавляет вложенную секцию "noLegal Diagnostics" → при копировании/отправке применяется выбранный уровень приватности.

### 5.3 Точки расширяемости

- Новые категории диагностики добавляются как отдельные логические блоки, чтобы расширять отчёт без изменения архитектуры сборщика.
- noLegal-only API вызовы инкапсулированы внутри flavor-специфичного source set (`src/noLegal`).
- Каждое поле в отчёте должно поддерживать graceful degradation (при недоступности API выдаётся `unknown` или `n/a`).

---

## 6. Открытые вопросы / Research items

0. **Baseline audit before new research (S0335/S0337)**
   - **Решение:** noLegal-сборка расширяет комбинированную диагностику S0335 (базовые параметры системы) и S0337 (асинхронная сеть, батарея, бенчмарки), добавляя разделы, недопустимые в Google Play или специфичные для noLegal фич.
   - **Статус:** Resolved

1. **Граница Google Play vs Android platform**
   - **Решение:** noLegal версия не ограничена правилами Google Play Data Safety, поэтому может собирать параметры, обычно вызывающие отклонение приложения (проверка Root, считывание установленных пакетов магазинов, детальный аудит разрешений, хэш подписи, чтение монтирований и ресурсов процесса). Ограничения Android Sandbox (например, недоступность IMEI или MAC-адреса без опасных разрешений) соблюдаются согласно таблице §6.1.
   - **Статус:** Resolved

2. **Кандидаты noLegal-only полей**
   - **Решение:** Утверждено 7 категорий полей (OS & Security, Permissions, Installer & Signature, noLegal Runtimes, Mounts & File System, Network, Process Resources & Logs). Подробности сбора полей приведены в §5.1.
   - **Статус:** Resolved

3. **Приватность и редакция**
   - **Решение:** Чувствительные поля (SHA-256 подписи, детальные пути в точках монтирования, локальные IP-адреса DNS и VPN) маскируются по умолчанию в UI и при обычном копировании (например, `Signature SHA-256: [REDACTED]`). В диалог добавляется переключатель "Показывать чувствительные данные" или отдельное действие "Скопировать полный отчёт", требующие подтверждения.
   - **Статус:** Resolved

4. **UI surface**
   - **Решение:** Расширенные noLegal-поля отображаются в том же диалоге System info, вложенными в раскрывающуюся секцию "Extended Diagnostics (noLegal)" в самом низу списка, чтобы не перегружать основной экран.
   - **Статус:** Resolved

5. **Flavor isolation**
   - **Решение:** Создаётся интерфейс поставщика диагностики `ExtendedDiagnosticsProvider` в `common` коде. В source set `standard` создаётся пустая реализация (возвращает пустую строку/список), а в `noLegal` - полноценная реализация, опрашивающая noLegal-компоненты. Общий код обращается к провайдеру через dependency injection (Hilt) без runtime-проверок.
   - **Статус:** Resolved

6. **Validation**
   - **Решение:** Достаточно компиляции и прогона на эмуляторе/телефоне для первой итерации. Обязательны unit-тесты на `ExtendedDiagnosticsProvider` в noLegal source set и проверка компиляции standard-варианта без утечек noLegal-кода.
   - **Статус:** Resolved

### 6.1 Hard exclusions (research baseline)

| Field | Why excluded |
|------|--------------|
| **IMEI / MEID** | `TelephonyManager.getImei()` needs `READ_PHONE_STATE` (dangerous); on Android 10+ apps cannot read it at all (returns null / SecurityException). Hardware identifier - top Data Safety red flag. |
| **Serial number** | `Build.getSerial()` requires `READ_PHONE_STATE` since **Android 8.0 (O)**; on **Android 10+** returns `Build.UNKNOWN` for normal apps. Persistent identifier. AVOID. |
| **MAC address (Wi-Fi/BT)** | Randomized / `02:00:00:00:00:00` since Android 6+; real value needs privileged perms. Identifier. AVOID. |
| **ANDROID_ID** | `Settings.Secure.ANDROID_ID` - permission-free but is a per-app persistent identifier; collecting/displaying it is exactly the kind of device fingerprinting that trips Data Safety review. AVOID. |
| **Advertising ID** | Requires Play Services / GMS and `AD_ID` permission (33+); explicitly out - owner wants no GMS. AVOID. |
| **Accounts / email** | `AccountManager.getAccounts()` needs `GET_ACCOUNTS`. PII. AVOID. |
| **Phone number** | `TelephonyManager.getLine1Number()` needs `READ_PHONE_NUMBERS` / `READ_PHONE_STATE` (dangerous). PII. AVOID. |
| **Precise/coarse location** | `ACCESS_FINE` / `ACCESS_COARSE_LOCATION` (dangerous), location category in Data Safety. AVOID. |
| **Contacts** | `READ_CONTACTS` (dangerous). AVOID. |
| **SIM / carrier / subscriber id** | `TelephonyManager` carrier/IMSI fields need `READ_PHONE_STATE`. Identifier + dangerous perm. AVOID. |
| **SSID / BSSID** | Location-gated; BSSID is an identifier. AVOID. |
| **Bluetooth adapter state/name** | `BLUETOOTH_CONNECT` runtime perm on Android 12+. AVOID (see Bucket 4). |

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Слишком широкая диагностика попадёт в share-текст | Средняя | Утечка приватных данных в баг-репорт | Ввести sensitivity matrix и safe-copy режим |
| noLegal логика попадёт в публичные flavor-ы | Средняя | Нарушение flavor isolation и public compliance | Держать реализацию за flavor-specific boundary |
| Android API не отдаёт ожидаемые поля без разрешений | Высокая | Часть идеи окажется невозможной | Research item фиксирует статус not available / rejected |
| Диалог станет слишком длинным и плохо читаемым | Средняя | Пользователь не найдёт важные строки | Группировка секций и опциональная expanded-часть |
| Расширение начнёт дублировать S0335 сборщик | Средняя | Расхождение базовых значений | noLegal слой только дополняет базовый отчёт |

---

## 8. Влияние на пользователя (docs/FEATURES)

После реализации обновлять только noLegal feature docs: расширенная noLegal System info показывает дополнительную локальную диагностику для sideload, VR/XR и bundled runtime компонентов. Публичные `docs/FEATURES*.md` не менять.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Расширять S0335, не создавать второй общий System info**

- **Решение:** noLegal добавляет дополнительные секции поверх базового отчёта.
- **Альтернативы:** отдельный независимый диагностический экран; fork общего System info для noLegal.
- **Почему:** S0335 уже решает общий UX, а новая задача касается только дополнительного набора данных.

**ADR-2: noLegal-only данные проходят sensitivity matrix**

- **Решение:** каждое поле получает статус чувствительности до реализации.
- **Альтернативы:** показывать всё доступное; скрывать всю расширенную диагностику из share.
- **Почему:** noLegal снимает часть distribution-ограничений, но не отменяет приватность пользователя.

**ADR-3: Flavor isolation важнее краткости реализации**

- **Решение:** расширение должно быть собрано так, чтобы публичные flavor-ы не содержали noLegal-only поведения.
- **Альтернативы:** runtime check внутри общего слоя.
- **Почему:** проектная модель запрещает новые flavor-гейты в общем коде.

---

## 10. Связи с другими спеками

- S0335 - базовый System info диалог, который новая спека расширяет для noLegal.

---

## 11. Критерии готовности (strategic-level)

1. В noLegal-сборке в диалоге «Сведения о системе» появляется секция «Расширенная диагностика (noLegal)», содержащая детальные параметры безопасности (Root, SELinux, Developer options), разрешения, подпись APK, состояние noLegal рантаймов (Python/yt-dlp, PaddleOCR, OpenXR/VR badge, MuPDF), точки монтирования, сетевые отладочные адреса (DNS, VPN) и ресурсы процесса.
2. В публичных flavor-ах (standard, lite и др.) секция «Расширенная диагностика (noLegal)» полностью отсутствует, а весь код сбора и сопутствующие строки исключены из скомпилированного APK.
3. Чувствительные данные (такие как отпечатки подписей, локальные адреса DNS/VPN, пути монтирования) маскируются по умолчанию и выводятся полностью только при явном действии пользователя (например, при подтверждении экспорта полного отчёта).
4. Процесс сбора noLegal-диагностики выполняется асинхронно в фоновом потоке и не блокирует основной UI приложения.
5. Реализация не вводит новых опасных runtime разрешений (все данные собираются через стандартное API или безопасные системные файлы).
6. В noLegal-документацию (например, `docs/FEATURES_noLegal.md`) добавлены сведения об этой возможности; публичные файлы документации (`docs/FEATURES.md` и др.) не меняются.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0336` - создаст `PLAN/S0336_nolegal-extended-system-info/` с фазами.

---

## Proposed Structural Changes

### Proposal P-1 - Удалить `## 0. Approval Gate` в пользу §3.3

**Status:** Applied (2026-06-03)
**Affected:** `## 0. Approval Gate (owner input)` и `### 3.3 Owner inputs (Approval gate)`
**Rationale:** Устранение дублирования. Раздел `## 0` удалён, а все параметры согласования перенесены в раздел `### 3.3 Owner inputs (Approval gate)`, который автоматически проверяется валидатором `check-owner-inputs.ps1`.

---

## Revision History

- **2026-06-03** - by `/spec-update` (`gemini-3.5-flash`, focus: noLegal data collection, structure, style)
  - Applied: 1 (Resolved Section 0 in favor of Section 3.3 to pass `check-owner-inputs.ps1` validator; populated owner inputs with specific constraints).
  - Applied: 2 (Enriched Section 5 and Section 6 with detailed lists of private-only data candidates: security status, runtime environments like python/yt-dlp, PaddleOCR/Tesseract, VR/OpenXR parameters, file mounts, custom network values, and thread/heap metrics).
  - Applied: 3 (Updated strategic completion criteria and added structural proposals/revision history).

---

## Log Observation (2026-06-04)

Из device-логов 06/04 (`logs/fastmediasorter_20260604_004027.log`): сбор noLegal-диагностики на Quest ловит `FileNotFoundException` EACCES при чтении статуса SELinux (`/sys/fs/selinux/enforce`) и печатает полный стектрейс на уровне W («noLegal diagnostics: failed to read a field»). На устройстве без доступа это ожидаемо и не нарушает работу.

- Рекомендация на этапе device-test / закрытия: ожидаемый permission-denied логировать кратко (уровень I/W без стектрейса), полный стектрейс оставлять только для неожиданных ошибок.
- Наблюдение из анализа логов, не дефект функциональности; статус и debug-пробы не затронуты.
