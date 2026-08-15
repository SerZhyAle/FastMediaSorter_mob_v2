# Стратегическая спецификация: S0239 — Credential Manager GMS version guard

**Ticket:** S0239
**Status:** Archived
**Priority:** 70
**Date:** 2026-05-17
**Tier:** 3 — Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc — fix-up для S0200 risk row 2

> **Scope:** STRATEGIC. Bug-fix для риска "Credential Manager отказывает на устройствах без актуального Google Play Services" из §7 S0200. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк.

---

## 1. Проблема

В реализации S0200 риск №2 (§7 S0200: "Credential Manager отказывает на устройствах без актуального Google Play Services") митигирован недостаточно. Заявленная митигация — "Явное сообщение пользователю с указанием обновить Google Play Services; код ошибки Credential Manager пробрасывается в UI на понятном языке" — на практике не работает на устройствах, где установленный GMS свежее версии, с которой собран `play-services-auth`, но старее версии, требуемой Credential Manager API (например, GMS `21.24.23` против требуемой `23.08.15`).

В таком сценарии система-уровня выдаёт `SERVICE_VERSION_UPDATE_REQUIRED`, который проходит через generic `isGooglePlayServicesAvailable(context)` как `SUCCESS`. Существующий guard кеширует этот SUCCESS на старте процесса и не пере-проверяет на момент клика sign-in. В результате Credential Manager падает с `GetCredentialProviderConfigurationException: no provider dependencies found`, который не разбирается отдельно и попадает в общую ветку `UnknownError`. Пользователь видит generic "Sign-in failed for an unexpected reason — Try again" вместо понятного CTA "Обновить Play Услуги".

Кейс не теоретический: воспроизводится на Android XR Developer Preview r7 (system image `system-images;android-34;google-xr;x86_64` rev 7) и аналогичных устройствах с pinned GMS. После XR Stable / следующих Preview ожидается естественное закрытие на стороне платформы, но текущая UX остаётся актуальной для Quest 3, AOSP-устройств и любых устройств с устаревшим GMS, охваченных в §7 / §11.7 S0200.

---

## 2. Цели

1. На устройстве с устаревшим GMS клик sign-in приводит к понятному диалогу "Google Play Services устарели — Обновить Play Услуги", CTA открывает страницу Google Play services в Play Store.
2. Митигация работает для случая, когда установленный GMS пинит версию, недостаточную именно для Credential Manager, но проходит общий availability check.
3. Если по какой-то причине pre-flight guard не сработал и Credential Manager всё же дошёл до выброса `GetCredentialProviderConfigurationException` — exception маппится в тот же `PlayServicesOutdated`, не в `UnknownError`.

**Non-goals:**

- WebView OAuth / любой альтернативный sign-in flow (явно запрещён ADR-5 + ADR-7 S0200).
- Sideload Google Play services APK на устройство.
- Авто-обновление GMS из приложения (вне сферы ответственности).
- Поддержка устройств без CCT-провайдера в принципе (это §7 risk row 3 + §11.7 S0200, отдельная тема).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Сообщение пользователю остаётся уже существующей локализованной парой `s0234_card_state_error_play_services_outdated_summary` + `s0234_card_state_error_play_services_outdated_cta` (EN / RU / UK уже на месте от S0200 / S0234). Никаких новых строк.
2. CTA продолжает открывать Play Store через существующий `openPlayServicesInPlayStore()` маршрут.
3. Если GMS обновится между запусками приложения — следующий клик sign-in сразу даёт корректный flow, без перезапуска процесса.

### 3.2 Жёсткие ограничения

- **Flavor:** наследует от S0200 — фикс применяется в cloud-enabled flavors (`standard`, `noLegal`, `photos`, `legacy`, `vr`, `vrUnlicensed`). `lite` не затронут.
- **API level:** наследует от S0200 — `minSdk 26`.
- **Архитектура:** реализация остаётся в shared source set `src/cloudEnabled/java/` (Credential Manager-репозиторий) и в общем `src/main/java/` (GMS-чекер).
- **Источник минимальной версии GMS:** константа, ассоциированная с версией `androidx.credentials:credentials-play-services-auth` в `app_v2/build.gradle.kts`. Бамп этой константы при upgrade библиотеки — часть тактической дисциплины обновлений зависимостей.
- **Совместимость с ADR-5 / ADR-7 S0200:** не вводит альтернативных каналов sign-in. Усиливает существующий graceful-degradation путь.
- **Логирование:** Timber.w-строка от GMS-чекера должна быть достаточно специфичной для логического разбора в `/log-reader` ("Google Play Services update required" с указанием minApkVersion).
- **Локализация:** новых строк не появляется — все формулировки уже на EN/RU/UK от родительского S0200.

---

## 4. Контекст текущей архитектуры

GMS-чекер вызывается один раз при старте процесса в `FastMediaSorterApp` и кеширует результат на жизнь процесса. Credential Manager-репозиторий читает этот кеш через `gmsGuard()` перед запуском interactive sign-in и при `OK`-статусе пропускает запрос в `androidx.credentials.CredentialManager`. Маппинг exception → `IdentityFailureReason` живёт в `mapException()` того же репозитория и используется UI-слоем настроек для выбора локализованного сообщения и CTA.

Корневая причина проблемы — `GoogleApiAvailability.isGooglePlayServicesAvailable(context)` без второго параметра проверяет совместимость с baseline-версией собранной `play-services-auth`, а не с конкретным минимумом, нужным Credential Manager. Этот minimum выше (GMS `23.08.15`, build `230815045` для `credentials-play-services-auth:1.3.0`). Двупараметровая перегрузка `isGooglePlayServicesAvailable(context, minApkVersion)` существует, но в текущем чекере не использовалась.

---

## 5. Предлагаемый подход

GMS-чекер получает явное знание о минимальных версиях GMS для конкретных API. Credential Manager-репозиторий при каждом вызове sign-in инициирует **live re-check** против минимума для Credential Manager API (не использует кеш на момент старта процесса) и только потом проходит в `getCredential()`. Mapping exception → reason получает явную ветку для `GetCredentialProviderConfigurationException`, гарантирующую правильную классификацию даже если live guard по какой-то причине прошёл успешно.

### 5.1 Основные столпы / модули

- **Константа минимальной версии GMS для Credential Manager** в существующем GMS-чекере. Значение фиксировано в коде, документировано через KDoc, привязано в комментарии к версии библиотеки `credentials-play-services-auth`.
- **Live re-evaluation overload** GMS-чекера. Принимает минимальную версию параметром. Не использует кеш — каждый вызов опрашивает `GoogleApiAvailability` свежим запросом и обновляет cached status.
- **Pre-flight gate в Credential Manager-репозитории.** Перед `getCredential()` вызывает live re-evaluation с минимумом для Credential Manager. Существующая логика sign-out short-circuit'а на UPDATE_REQUIRED → `PlayServicesOutdated` сохраняется.
- **Safety-net branch в mapException.** Явный case для `GetCredentialProviderConfigurationException` → `PlayServicesOutdated`. Покрывает все возможные расхождения между ожиданием guard и реальностью провайдера.

### 5.2 Потоки данных и событий

- UI клик sign-in → Credential Manager-репозиторий → live GMS re-evaluation → если UPDATE_REQUIRED → возврат `Failed(PlayServicesOutdated)` без вызова `getCredential()`. UI показывает локализованный диалог + CTA "Обновить Play Услуги".
- Live re-evaluation не блокирует UI — это синхронный системный вызов миллисекундного порядка, выполняется в той же coroutine что и общий sign-in flow.
- Если live guard вернул OK, но `getCredential()` всё равно выбросил `GetCredentialProviderConfigurationException` (например, GMS обновился между двумя вызовами или есть какой-то непокрытый кейс конфигурации) — mapException направляет в тот же `PlayServicesOutdated`.

### 5.3 Точки расширяемости

- Константа минимальной версии для Credential Manager — пример паттерна. Будущие GMS-зависимые API (например, Google Sign-In для других сервисов, Awareness API, Wallet) могут получить свои константы и переиспользовать `recheckFor` для своих guard'ов.

---

## 6. Открытые вопросы / Research items

1. **Когда поднимать константу `MIN_GMS_VERSION_FOR_CREDENTIAL_MANAGER`?**
   - Вопрос: при каждом bump библиотеки `credentials-play-services-auth` или только при явных breaking changes минимума?
   - Решение: при каждом bump library. Минимум documented в release notes библиотеки; синхронизация — часть дисциплины обновления зависимости. В KDoc константы явно указано "Bump this constant when the credentials library is upgraded".
   - Статус: Resolved.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Live re-evaluation добавляет заметную задержку перед запуском Credential Manager | Низкая | Малозаметная пауза в UI | `isGooglePlayServicesAvailable` — синхронный системный вызов миллисекундного порядка, исполняется в той же coroutine что и сам sign-in; пользователь не различает |
| Бамп константы забывают при upgrade библиотеки | Средняя | Регрессия — guard снова не сработает | KDoc-инструкция; в будущих PR upgrade library reviewer проверяет константу |
| `GoogleApiAvailability` возвращает другой код для XR-эмулятора в новой Preview | Низкая | Diagnostic message неточен | Чекер логирует raw `code` в Timber.w — диагноз в логах быстрый |
| Safety-net в mapException маскирует другие провайдер-конфигурационные ошибки | Низкая | Пользователь видит "Обновить Play Услуги" в случае, не связанном с GMS | Эта exception subclass по документации Android только про provider-config issues, наиболее частый из которых — GMS version. Альтернативы (manifest issues, missing dependencies) у нас исключены сборочной системой |

---

## 8. Влияние на пользователя (docs/FEATURES)

Bug fix существующей фичи S0200, без новой пользовательской способности. В `docs/FEATURES.md` записей не добавляется. В `dev/FUNCTIONALITY.log` отмечается как FIX — уже сделано в момент имплементации.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Live re-evaluation вместо инвалидации кеша на событиях**

- **Решение:** GMS-чекер получает overload, который выполняет свежий `isGooglePlayServicesAvailable` вызов на каждое обращение, не консультируясь с кешем.
- **Альтернативы:** (a) Listener на установку/обновление пакета `com.google.android.gms`, который инвалидирует кеш; (b) Time-based TTL для кеша.
- **Почему:** Listener на пакет — overengineering для одного гарда. TTL — компромисс, теряющий точность сразу после обновления GMS. Сам системный вызов миллисекундный, нет смысла его кешировать.

**ADR-2: Константа минимума GMS в коде, не в build config**

- **Решение:** константа живёт в Kotlin-объекте GMS-чекера рядом с использующим её методом.
- **Альтернативы:** (a) `BuildConfig.MIN_GMS_VERSION_CREDENTIAL_MANAGER` через `app_v2/build.gradle.kts`; (b) Файл ресурса `values/min_gms_version.xml`.
- **Почему:** константа специфична для одной библиотеки и её минимума; помещение в BuildConfig раздувает поле зависимостями build-системы без выигрыша. Файл ресурса делает константу гадаемой по `R.integer.*` — хуже читается.

**ADR-3: Safety-net в mapException даже при наличии guard**

- **Решение:** обе линии защиты остаются — pre-flight guard + явная ветка в exception mapping.
- **Альтернативы:** только guard или только mapping.
- **Почему:** guard и mapping разделены во времени (live re-evaluation против GMS-update между ними нелья исключить), и стоимость дублирования низкая. Defence-in-depth для user-visible UX оправдан.

---

## 10. Связи с другими спеками

- S0200 — google-account-central-binding (Verified). Этот тикет — fix-up §7 risk row 2 / §11.7 S0200. Не реоткрывает S0200, существует параллельно.
- S0234 — карточка Google account в Settings. Использует существующие локализованные строки `s0234_card_state_error_play_services_outdated_summary` / `_cta`.

Не блокируется внешними тикетами.

---

## 11. Критерии готовности (strategic-level)

1. На устройстве с GMS ниже `MIN_GMS_VERSION_FOR_CREDENTIAL_MANAGER` клик sign-in приводит к локализованному диалогу "Google Play Services устарели" с CTA "Обновить Play Услуги"; CTA открывает страницу Google Play services в Play Store.
2. В logcat присутствует строка `GmsAvailabilityChecker: Google Play Services update required` от `GmsAvailabilityChecker` с указанным `minApkVersion`.
3. На устройстве с актуальным GMS (≥ `MIN_GMS_VERSION_FOR_CREDENTIAL_MANAGER`) sign-in проходит без перехвата guard'ом — Credential Manager отдаёт chooser нормально.
4. В случае, когда guard пропустил по какой-то причине, а Credential Manager выбросил `GetCredentialProviderConfigurationException` — пользователь всё равно видит CTA "Обновить Play Услуги", не "Try again".
5. Константа `MIN_GMS_VERSION_FOR_CREDENTIAL_MANAGER` имеет KDoc, описывающий что и когда её бампить.
6. EN / RU / UK строки — без изменений (используются существующие от S0200 / S0234).

---

## 12. Ссылка на тактическую спецификацию

Тактическая спека не создаётся: реализация атомарная (две правки в двух файлах), сделана в одном раунде. Прямой переход Implemented → BlockNeedUserTest.

---

## Last Audit

**Date:** 2026-05-17
**Mode:** initial
**Flags:** —
**Outcome:** Implemented (pending device test)
**Counts:** PASS — · WARN — · FAIL — · MANUAL 3 · EXEMPT —

### Manual / on-device

- [ ] §11.1 + §11.2 — на устройстве с устаревшим GMS (Android XR Preview r7, GMS `21.24.23`) sign-in → диалог "Обновить Play Услуги" + строка `GmsAvailabilityChecker: Google Play Services update required` в logcat.
- [ ] §11.3 — на phone-эмуляторе с актуальным GMS (API 34+ Google Play, GMS ≥ 23.08.15) sign-in проходит до chooser'а аккаунтов.
- [ ] §11.4 — safety-net trigger опционально верифицируется через unit-test/mock; на устройстве — best-effort, если получится воспроизвести race.
