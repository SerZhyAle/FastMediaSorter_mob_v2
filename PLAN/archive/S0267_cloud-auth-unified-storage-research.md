# Стратегическая спецификация: S0267 — Изучить хранение Dropbox/OneDrive/GoogleDrive в общем хранилище авторизаций

**Ticket:** S0267
**Status:** Verified
**Priority:** 50
**Date:** 2026-05-20
**Tier:** 3 — Moderate (ad-hoc, research-only)
**Roadmap entry:** Ad-hoc — запрос 2026-05-20
**Tactical spec:** `PLAN/S0267_cloud-auth-unified-storage-research/` (создан `/spec-tech`, 2026-05-20)
**Tactical plan:** `PLAN/S0267_cloud-auth-unified-storage-research/INDEX.md`

> **Scope:** STRATEGIC, RESEARCH-ONLY. Цель — собрать факты, оценить плюсы/минусы и сложность, выдать рекомендацию (Go / No-Go / Partial). Реализация в этот спек не входит и будет жить в отдельных спеках.

---

## 1. Проблема

Сейчас в проекте сосуществует несколько хранилищ авторизаций, и пользователь видит их разрозненно.

- Сетевые ресурсы (SMB, SFTP, FTP) — Room-таблица `network_credentials`, пароль шифруется через Keystore. Пользователь управляет ими в Settings (ресурсы и общий список credentials).
- Cookie-сессии соцсайтов (Instagram, Threads, Pinterest, Reddit, YouTube и т. п.) — отдельное per-host шифрованное хранилище за `AuthSessionRepository`, экран Settings → Authorizations (`AuthSessionsListFragment`). Это и есть «общее хранилище авторизаций, где пользователь управляет записями».
- OAuth-токены трёх облаков (Google Drive, Dropbox, OneDrive) — каждый провайдер хранит токены своим способом: Google Drive после S0200 опирается на Credential Manager + identity-домен; Dropbox — собственный `EncryptedSharedPreferences` (`dropbox_credentials`); OneDrive — внутренний кэш MSAL, недоступный извне SDK. Управление сейчас рассыпано между настройкой ресурса, экраном бэкапа и серверной отзывом токенов.

Эффект для пользователя: нет одного места «вот все мои авторизации — переименуй/удали/перелогинься». Облака приходится «отзывать» через настройку ресурса, тогда как соцсайты — через отдельный экран. Это нарушает обещание единой управляемой точки и затрудняет аудит подключений.

Область — слой `data/cloud/` (OAuth-координаторы и SDK-обёртки), `data/local/db/` (Room), `data/link/auth/` и `ui/settings/auth/` (управляющий UI).

---

## 2. Цели

1. Документировать текущую архитектуру хранения авторизаций по трём облакам и двум локальным системам (`network_credentials`, `AuthSessionRepository`).
2. Оценить, какие части OAuth-токенов трёх облаков **возможно** держать в общем хранилище без нарушения SDK-контрактов (MSAL, Dropbox SDK, Google Identity Services / Credential Manager).
3. Определить, что именно пользователь должен **видеть и трогать** в общем экране управления (учётная запись, провайдер, scope, дата последнего использования, действия), и что обязано остаться в SDK-нативном кэше.
4. Сформулировать варианты архитектуры (минимум три), сравнить их по риску / стоимости / пользовательской ценности.
5. Выпустить рекомендацию: Go (с указанием выбранного варианта), No-Go (с обоснованием), либо Partial (что объединяем сейчас, что откладываем).

**Non-goals:**

- Любая реализация (Room-миграции, Hilt-модули, новые экраны UI). Этот спек не порождает кода.
- Изменения в SDK-нативных кэшах MSAL / Dropbox / Credential Manager (не трогаем приватные API).
- Объединение `network_credentials` и `AuthSessionRepository` между собой — это отдельная задача, выходящая за рамки запроса.
- Введение нового OAuth-провайдера (например, Yandex Disk) — вне объёма.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Один экран Settings, где пользователь видит и облачные аккаунты, и cookie-сессии, и сетевые credentials (хотя бы с навигацией между ними).
2. Действия «переименовать», «удалить с отзывом токена», «перелогиниться» по одному паттерну для всех провайдеров.
3. Поддержка мульти-аккаунта на провайдер сохраняется (Drive/OneDrive уже поддерживают, Dropbox — формально один аккаунт, расширяемо).
4. Если полное слияние невозможно — допустимо «зеркало для управления» (UI читает из общего реестра, но фактические токены живут в SDK-кэше).

### 3.2 Жёсткие ограничения

- **Flavor:** касается только тех вариантов, где включён `CLOUD` (`standard`). `lite`/`photos`/`legacy` cloud-возможности не имеют. `noLegal`/`vr` — внутри `standard`-дерева, ограничения те же.
- **API level:** без API-специфики, минимум `minSdk 26` (стандарт).
- **Wear OS:** не затрагивается.
- **Производительность:** доступ к токену из горячего пути (плеер, миниатюры) обязан оставаться без I/O на основном потоке. Если выбранный вариант добавляет лишний Room-read в hot path — это блокер.
- **Совместимость данных:** существующие подключения пользователей не должны разорваться. Любая миграция — однократная, идемпотентная, с fallback на пере-логин.
- **Безопасность токенов:** refresh-токены MSAL и Dropbox шифруются Keystore-обвязкой `EncryptedSharedPreferences`. Любое наше хранилище должно держать тот же уровень (Keystore-backed AES). Никаких токенов в открытом виде в Room без отдельного слоя шифрования.
- **Контракты SDK:** MSAL не принимает «внешние» access/refresh-токены — попытка обойти его кэш нарушает контракт библиотеки и ломает silent refresh. Это закреплённая внешняя граница, не предмет переговоров в этом спеке.
- **Локализация:** EN/RU/UK обязательны, если этот спек породит видимые строки (в исследовательской фазе строк не вводим).
- **Communication policy:** при появлении пользовательских строк (на этапе реализации) — обязательное соответствие `docs/COMMUNICATION_POLICY.md`, тон-чеклист (§6 политики).
- **Доступность:** TalkBack, focus chain — будут проверены на этапе реализации выбранного варианта.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0200 (Google Drive Credential Manager + identity-domain — источник правды по Drive auth), S0166 (per-host encrypted auth storage — pattern-донор для общего реестра), S0157 / S0182 / S0211 (UI Settings → Authorizations — целевой UX-шаблон).
- **Data scope:** новая Room-таблица `cloud_auth_accounts` (account_id PK + provider + email + display_name + scopes + expires_at + last_used_at + encrypted token_payload); миграция `AppDatabase` v31 → v32, аддитивная, без модификации `network_credentials`.
- **UI scope:** один новый экран Settings → Authorizations (unified) — список + фильтр по типу (Cloud / Social / Network) + действия rename / delete-with-revoke / relogin. Заменяет/расширяет существующий `AuthSessionsListFragment`.
- **Flavor scope:** только `standard` (имеет `CLOUD` BuildConfig). `lite`/`photos`/`legacy` cloud-возможности не имеют — экран в этих flavor'ах не появляется. `noLegal`/`vr` наследуют `standard`. Per Strict Rule 15: интерфейс `AuthAccountSource` живёт в `src/main/`, cloud-реализации — в `src/cloudEnabled/`.
- **Executable scope:** ticket research-only — кода не порождает. Имплементация декомпозируется на отдельные стратегические тикеты per §11.3 после `/spec-tech S0267`.

---

## 4. Контекст текущей архитектуры

Облачный слой `data/cloud/` исторически собран вокруг трёх SDK с очень разной моделью владения токеном. Google Drive после S0200 делегирует владение primary-аккаунтом identity-домену через Credential Manager; токен короткоживущий и переиздаётся при каждом запросе. Dropbox использует PKCE-флоу через свой SDK и сериализует `DbxCredential` (access + refresh + expiry) в собственный `EncryptedSharedPreferences`. OneDrive завязан на MSAL: библиотека владеет кэшем токенов и аккаунтов, ключи в Keystore, refresh — silent через MSAL API.

В Room уже есть запись-«огрызок» для облачных аккаунтов: `OneDriveAuthCoordinator.handleAuthenticationResult` вставляет строку в `network_credentials` с пустым паролем — как «маркер» для мульти-аккаунт-пикера. Это де-факто частичная унификация ради UX списка, но реальный токен живёт в MSAL.

Параллельно `AuthSessionRepository` (S0166/S0182/S0211) даёт экран Settings → Authorizations с операциями rename / delete / relogin по аккаунтам, сгруппированным по хосту. Архитектурно это и есть готовая роль «общее управляемое хранилище авторизаций», но сейчас оно завязано на cookie-модель (HttpCookie + UA), не на OAuth-токены.

Прямая причина невозможности тривиального слияния — модель данных трёх облаков несовместима с cookie-моделью `AuthSessionRepository` и с password-моделью `network_credentials`: OAuth-учётка — это (refresh_token, access_token, expires_at, scopes, account_id, account_email), плюс провайдер-специфичные параметры кэша SDK. Без явной новой модели — нечего объединять.

---

## 5. Предлагаемый подход

Подход исследовательский: не выбираем единственный путь, а формулируем варианты и критерии сравнения. Решение фиксируется по итогам §6.

### 5.1 Основные столпы / варианты архитектуры

**Вариант A — Management Facade (минимальный риск).** Токены остаются в SDK-нативных кэшах (MSAL, Dropbox prefs, identity-домен Drive). Появляется новый домен «реестр управляемых аккаунтов», агрегирующий:

- записи `network_credentials`;
- записи `AuthSessionRepository`;
- стабы облачных аккаунтов (уже частично есть в `network_credentials`).

UI Settings → Authorizations расширяется группами «Cloud» / «Social» / «Network». Действия rename / delete / relogin диспетчируются провайдер-специфичным адаптером (Drive → identity-домен, Dropbox → SDK revoke + clear prefs, OneDrive → MSAL signOut).

- **Что унифицируется:** UX, операции, observability, audit (`CredentialAuditor`).
- **Что не унифицируется:** физическое хранилище токена.
- **Стоимость:** новый ViewModel + адаптер списков + 3 провайдер-адаптера; нет миграции Room.

**Вариант B — Hybrid Mirror (умеренный риск).** SDK-кэш остаётся primary, но при каждом успешном auth/refresh мы зеркалим в новое Room-хранилище `cloud_auth_accounts` метаданные: provider, account_id, email, display_name, scopes, expires_at, last_used_at, изоляция token-payload в Keystore-backed encrypted blob. Refresh продолжает делать SDK; зеркало читается UI и `CredentialAuditor`. Удаление в нашем UI вызывает SDK-revoke и затем чистит зеркало.

- **Плюс над A:** видим скоупы и истечение, можем показать «у вас токен истёк, перелогиньтесь» без дополнительного round-trip в SDK.
- **Минус:** два источника правды, риск рассинхронизации (token revoked снаружи — зеркало не знает).
- **Стоимость:** одна новая Room-таблица + миграция, провайдер-хуки на колбэки SDK, отдельный слой шифрования blob'ов.

**Вариант C — Full Take-over (отвергнут окончательно).** Мы сами храним access/refresh-токены трёх облаков и обходим SDK-кэши. Для Dropbox принципиально возможно — `DbxCredential.Reader`/`Writer` стабильны, `refresh()` это чистый HTTP `oauth2/token`, текущий код уже рехидрирует `DbxCredential` (см. `DropboxClient.initializeWithCredential`). Для Google Drive после S0200 токены и так короткоживущие и переиздаются `GoogleAuthUtil.getToken(email, scopes)` per-email — формально достижимо. Для OneDrive — **подтверждено невозможно** (research §6 Q1): MSAL Android 6.0.1+ не имеет публичного API для экспорта `IAccount`/refresh-токена, `TokenCacheItem` в MSAL не существует, broker-mode не помогает, любой reflection-обход `com.microsoft.identity.common.*` ломается на минорном апгрейде SDK. Поэтому C блокируется OneDrive и не рассматривается дальше.

### 5.2 Потоки данных и событий

- UI → ViewModel → доменный фасад «UnifiedAuthRegistry» → провайдер-адаптеры → (SDK-кэш + опционально зеркало).
- Auth-успех от любого провайдера → доменный event → реестр пересчитывается → UI получает Flow с группами аккаунтов.
- Удаление → реестр диспатчит revoke в провайдер-адаптер → ждёт подтверждения → чистит зеркало (если есть) → UI обновляется.
- Перелогин → провайдер-адаптер запускает interactive-флоу (Credential Manager / Dropbox PKCE / MSAL) → результат пишется и в SDK-кэш, и в зеркало.

### 5.3 Точки расширяемости

- Доменный интерфейс «AuthAccountSource» — единый поверх трёх классов источников (network creds, social cookies, cloud OAuth). Каждый источник реализует `observe()`, `rename()`, `delete()`, `relogin(activity)`.
- Реестр оперирует на этом интерфейсе и не знает о провайдер-специфике.
- Provider-адаптеры — раздельные классы, по одному на провайдер. Расширение на новый OAuth-провайдер сводится к одному адаптеру + опциональной строке в зеркале.
- Google Drive multi-account уже частично готов: `CredentialManagerGoogleIdentityRepository.requestSecondaryAccount(..)` возвращает дополнительный аккаунт **без** записи в `PrimaryGoogleAccountStore`, и `GoogleDriveCredentialsManager` хранит credentials per-email. Identity-домен по-прежнему single-primary (S0200 ADR-2), но Drive-таргеты могут хранить несколько привязок параллельно — это даёт нам естественный паттерн «active vs additional» для других провайдеров.
- В варианте B зеркало живёт в Room с зашифрованным `token_payload` blob; криптослой переиспользует `CryptoHelper` (Keystore-backed AES), как уже сделано для `network_credentials.password`.

---

## 6. Research items — все вопросы закрыты (2026-05-20)

Все семь пунктов прошли research (4 параллельных подагента + UX-вопрос владельцу). Резюме по каждому ниже; полные отчёты с цитатами зафиксированы в журнале и доступны через PR-историю.

### 6.1 Q1 — MSAL: экспорт/импорт refresh-токена. **Resolved — (a) невозможно**

- Публичного API в MSAL Android для экспорта `IAccount` + refresh-токена нет и не планируется. Позиция maintainer'а (`iambmelt`, AzureAD): refresh-токен задумывался как не-переносимый, рекомендуемый паттерн кросс-устройства — On-Behalf-Of flow на сервере, а не миграция RT клиентом.
- `TokenCacheItem` в MSAL **не существует** (это был ADAL-класс; в MSAL миграция кэша только in-place, не из внешнего blob'а).
- Broker-mode (Microsoft Authenticator / Company Portal) не помогает: при включённом брокере *"any SSO state previously available to MSAL isn't available to the broker"* — broker держит свой credential-store, привязанный к device-account, и не служит транспортом для RT между устройствами.
- Reflection-обход `com.microsoft.identity.common.*` ломается на минорных апгрейдах (внутренние классы часто рефакторятся; проект сейчас на MSAL 6.0.1, upstream 8.3.2).
- **Источник:** GitHub issues `AzureAD/microsoft-authentication-library-for-android` #202, #1086, #1037; learn.microsoft.com/entra/identity-platform/access-tokens, msal-android-single-sign-on. Confidence: High.
- **Следствие:** вариант C блокируется именно OneDrive и снят с рассмотрения окончательно (ADR-1 в §9 подтверждён).

### 6.2 Q2 — Dropbox: рехидрация `DbxCredential`. **Resolved — (a) полностью поддерживается**

- `DbxCredential.Reader` / `DbxCredential.Writer` — публичные стабильные `JsonReader`/`JsonWriter`, `toString()` реализован через `Writer.writeToString(this)`. Это документированный официальный путь сериализации с момента появления refresh-токенов.
- `DbxCredential.refresh(DbxRequestConfig)` — чистый HTTP-вызов `oauth2/token` с `grant_type=refresh_token`, **без** зависимости от какого-либо prefs-хранилища. После успеха мутирует `this.accessToken`/`this.expiresAt` на переданном инстансе. Работает на десериализованном credential идентично свежевыданному.
- `DbxClientV2(config, credential)` — публичный конструктор; `DbxUserRawClientV2.refreshAccessToken()` делегирует в `credential.refresh(requestConfig)` — общий код-путь.
- "Revoke callbacks" SDK не существует как концепта (см. §6.4); revoke surface'ится как `InvalidAccessTokenException` из HTTP-401, и не зависит от способа получения credential.
- **Текущий код:** `DropboxClientUtils.serializeCredential`/`deserializeCredential` (lines 102–122) использует hand-rolled `JSONObject` форму, **не** `Reader`/`Writer`. Это работает, но миграция на официальные `Reader/Writer` — тривиальный win (отказ от форка схемы; добавляет опциональный `app_secret`). `DropboxClient.initializeWithCredential` (lines 368–393) уже сейчас прокидывает рехидрированный credential напрямую в `DbxClientV2` — паттерн варианта B уже отработан в проде.
- **Источник:** `dropbox/dropbox-sdk-java` v5.4.5 source + javadoc. Confidence: High.

### 6.3 Q3 — Google Drive: множественные аккаунты. **Resolved — (b) технически возможно; уже частично используется**

- Credential Manager 1.3.0 не ограничивает число bound-аккаунтов на приложение. `GetCredentialRequest` + `GetGoogleIdOption.setFilterByAuthorizedAccounts(false)` возвращает один account за вызов (выбор пользователя в system chooser), но можно вызывать N раз для N аккаунтов. Email — стабильный идентификатор аккаунта в API.
- Токены выдаются per-email через `GoogleAuthUtil.getToken(context, email, scope)` (текущая реализация в `GoogleTokenIssuer.kt:54`) — primary-статус это чисто проектная метка, не платформенная.
- **Кодовая база уже это использует:** `CredentialManagerGoogleIdentityRepository.requestSecondaryAccount(..)` (`CredentialManagerGoogleIdentityRepository.kt:154–184`) возвращает дополнительный `PrimaryGoogleAccount` **без** записи в `_state`/`store`. Существует именно для Drive multi-account picker'а. `GoogleDriveCredentialsManager` (lines 55–83) хранит credentials per-email (`KEY_CREDENTIALS_PREFIX = "credentials_"`).
- Identity-домен (`GoogleIdentityRepository.state: StateFlow<PrimaryGoogleAccountState>`) single-binding по дизайну (S0200 ADR-2). Это означает: для **сценария Q3** "аккаунт A для Drive-таргета №1, аккаунт B для №2" — никакого rework identity-домена не требуется, multi-account уже работает через Drive-таргеты. Если в будущем нужно «N primary-class аккаунтов» (что в Q3 не запрашивалось) — потребуется умеренный refactor `state: StateFlow` в `boundAccounts + activeAccount`.
- **Источник:** local repo inspection (`PrimaryGoogleAccountStore.kt`, `CredentialManagerGoogleIdentityRepository.kt`, `GoogleDriveCredentialsManager.kt`, `GoogleDriveAuthCoordinator.kt`) + developer.android.com/identity/sign-in/credential-manager. Confidence: High.

### 6.4 Q4 — Server-side revoke detection. **Resolved — (a) только по 401 во всех трёх SDK**

Единогласный вердикт по всем трём SDK: proactive push-callback при server-side revoke **не существует**. Detection строго реактивный — на следующем API-вызове.

- **MSAL:** revoke surface'ится как `MsalUiRequiredException` (`invalid_grant`, `AADSTS70008`, `AADSTS50173`) на следующем `acquireTokenSilent`. AT остаётся валидным до своего истечения (default 1 ч). Aналогов `AccountManager.LOGIN_ACCOUNTS_CHANGED` для MSAL нет. Источник: GitHub issue #1037 от maintainer'а.
- **Dropbox:** revoke = `InvalidAccessTokenException` из 401 (или `DbxOAuthException` при refresh с отозванным RT). Пакет `com/dropbox/core/oauth` содержит 4 класса, ни один не listener. Webhooks Dropbox только для filesystem-событий, не для app-revoke.
- **Google Drive / GIS:** revoke = `UserRecoverableAuthException` из 401 / `getToken`. Credential Manager 1.3.0..1.4.x changelog не содержит listener'а для server-side grant-changes. OAuth 2.0 by design не имеет out-of-band revoke-канала для installed apps.
- **Следствие для S0267:** unified registry **не может** показать "токен только что отозван" без round-trip к провайдеру. Доступны два паттерна: (1) lazy reconcile на первом 401 → пометить аккаунт NeedsResignIn (как уже делает `GoogleDriveAuthCoordinator.makeAuthenticatedRequest` lines 142–183); (2) опциональная кнопка "проверить статус сейчас" в UI, дёргающая legkий ping-call. Push-листенера в варианте B не закладываем.
- Confidence: High.

### 6.5 Q5 — `network_credentials`: расширять или новая таблица. **Resolved — (b) новая таблица `cloud_auth_accounts`** (с (c) общим интерфейсом)

- Сейчас `NetworkCredentialsEntity` концептуально чистая SMB/SFTP/FTP-сущность (server, port, username, encryptedPassword, shareName, sshPrivateKey, manualShareNames). Существующая stub-row OneDrive (`server=""`, `port=0`, `password=""`, `accountId=email`) — анти-паттерн, который S0200 Phase 05 уже пришлось компенсировать через `deleteByType("GOOGLE_DRIVE")`.
- Добавление колонок `refresh_token_blob`/`expires_at`/`scopes` в существующую таблицу:
  - Затрагивает: `NetworkCredentialsEntity`, `NetworkCredentialsDao` (есть `deleteByType("GOOGLE_DRIVE")` — расширяемо), `NetworkCredentialsRepositoryImpl` + интерфейс, `CredentialAuditor`, `BackfillSmbCredentialShareNameUseCase`, `UnusedCredentialPolicy`, `NetworkCredentialsResolver`, `OneDriveAuthCoordinator`, `S0200AuthStateWipe`, плюс ~10 SMB-consumer'ов (`SmbTransferProvider`, `SmbFileOperationHandler`, etc.).
  - Заставляет `CredentialAuditor` и `UnusedCredentialPolicy` фильтровать по type, иначе cloud-rows будут проходить через SMB-orphan policy.
- Отдельная таблица `cloud_auth_accounts` (PK `account_id`, опциональный FK к `network_credentials.id` для совместимости):
  - SMB-consumer'ы остаются нетронутыми. `CredentialAuditor` / `UnusedCredentialPolicy` сохраняют SMB-only семантику.
  - Стоимость: один новый entity + DAO + repository + Hilt-binding + Room v32 миграция (текущая v31).
- Общий доменный `interface AuthAccountSource` (вариант (c)) поверх обоих таблиц обязателен для unified-экрана из §6.7 — без него UI вынужден знать о двух DAO. Физическое разделение таблиц остаётся, абстракция логическая.
- **Источник:** local catalog + entity inspection (`AppDatabase.kt:29` подтверждает v31; `OneDriveAuthCoordinator.kt:303–325` подтверждает stub-row pattern; `S0200AuthStateWipe.kt:67–68` подтверждает прецедент изоляции cloud-rows). Confidence: High.

### 6.6 Q6 — Migration существующих OAuth-сессий. **Resolved — (b) lazy fill**

- S0200 Phase 05 eager-wipe был **вынужденной** мерой: Phase 04 уже удалила GoogleSignIn-импорты, rollback возможен только атомарным revert'ом Phase 04+05 (см. `PLAN/S0200_google-account-central-binding/PHASE_05__auth-wipe-and-resource-state.md:268–272`). Mass re-login был принятой ценой, не дизайн-целью.
- S0267 не имеет аналогичной forcing function: MSAL / Drive Credential Manager / Dropbox SDK продолжают независимо владеть своими кэшами, упразднения нет.
- Eager-копирование `refresh_token_blob` из MSAL/Dropbox кэшей в зеркало невозможно без exercise'а refresh-пути каждого SDK (MSAL refresh-token не экспортируется — §6.1; Dropbox SDK preferences читаются как DbxCredential, см. §6.2 — это работает, но всё равно требует первого touch на бэкграунде, что означает риск массового UI-glitch'а при ошибке).
- Существующий stub-row pattern `OneDriveAuthCoordinator.handleAuthenticationResult` (`lines 303–325`) — готовый template для lazy-fill: при следующем успешном auth/silent-refresh в любом провайдере вставляем/обновляем запись в `cloud_auth_accounts`. Multi-release coexistence приемлемо.
- **Источник:** S0200 PHASE_05 spec + текущий OneDriveAuthCoordinator. Confidence: High.

### 6.7 Q7 — Объём первого релиза unified-экрана. **Resolved — все три источника сразу (владелец, 2026-05-20)**

- В первый релиз unified Settings → Authorizations включаются: Cloud (Drive/Dropbox/OneDrive), Social (cookie-sessions через `AuthSessionRepository`), Network credentials (SMB/SFTP/FTP).
- UI-схема: единый список с фильтром по типу + бейджи (`☁ Cloud`, `🍪 Social`, `🖥 Network`).
- **Следствие для архитектуры:** доменный `interface AuthAccountSource` (см. §6.5 (c)) — обязательный, не опциональный. Три имплементации: `CloudAuthAccountSource` (поверх `cloud_auth_accounts` + провайдер-адаптеры), `SocialAuthAccountSource` (поверх `AuthSessionRepository`), `NetworkAuthAccountSource` (поверх `network_credentials`).
- Действия rename / delete-with-revoke / relogin диспатчатся через провайдер-специфичные адаптеры (Drive → `GoogleIdentityRepository`; Dropbox → SDK revoke + clear prefs; OneDrive → MSAL `signOut`; Social → существующий `AuthSessionRepository.relogin`; Network → existing edit-flow).
- Возможные расхождения семантики "delete" между источниками (cloud = revoke + clear; social = clear cookies; network = remove credential row) фиксируются в тактической спецификации и проявляются в UI как разные подсказки в диалоге подтверждения. Это не блокер.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Зеркало в B рассинхронизируется с SDK-кэшем при внешнем revoke (подтверждено §6.4: ни у одного SDK нет push-callback) | Высокая | UI показывает «авторизован», реально 401 | Lazy reconcile при первом 401 → пометить `NeedsResignIn`; опциональная кнопка «проверить статус» в UI (легкий ping-call); статус в записи `cloud_auth_accounts` не обещает свежести |
| Room v31 → v32 миграция + добавление `cloud_auth_accounts` | Высокая (вариант B обязателен) | Сбой первого запуска у части пользователей | Идемпотентная миграция; добавление новой таблицы без модификации существующих — низкорисковая операция; revert через откат версии |
| Обновление Dropbox SDK ломает hand-rolled JSON формат `DropboxClientUtils` | Средняя | Пере-логин Dropbox при апдейте | Перейти на официальные `DbxCredential.Reader/Writer` (см. §6.2) — устранит форк схемы |
| Совмещение `CredentialAuditor` с новой моделью даёт дубли в аудит-отчёте | Низкая (если идём по §6.5 (b) + (c)) | Шум в логах, ложные «unused credential» | `cloud_auth_accounts` живёт в отдельной таблице; `CredentialAuditor` остаётся SMB-only. Аудит для cloud-rows — отдельная задача (вне scope) |
| Регрессия в hot path (плеер, миниатюры) из-за лишнего I/O при чтении зеркала | Низкая | Заметные лаги | Запрет на синхронный Room-read в hot path; AccessToken остаётся in-memory у провайдер-адаптера; зеркало читается только UI / `CredentialAuditor` |
| Юридический риск хранения OAuth-refresh-токенов в нашем формате | Низкая | Требования к шифрованию/удалению | Keystore-AES через `CryptoHelper` (как у `network_credentials.password`); auto-clear при uninstall (Android); явная кнопка Settings → «Удалить все авторизации» |
| Разное поведение `delete` у трёх источников (cloud revoke vs cookie clear vs row delete) сбивает пользователя | Средняя | Жалобы «удалил аккаунт — а у провайдера он остался» | Разные подсказки в диалоге подтверждения per-source-type; для cloud — двухшаговый «revoke на устройстве» + опциональный «отозвать на сайте» с ссылкой |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES.md`. Спек исследовательский, новой возможности не вводит. Если по результатам §6 примем вариант A или B и реализуем его в отдельных спеках, апдейт FEATURES произойдёт там.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Полный перенос OAuth-токенов из SDK-кэшей в наше хранилище (вариант C) — отвергнут.**

- **Решение:** не рассматривать как реализуемый путь.
- **Альтернативы:** A (фасад без миграции токенов), B (зеркало метаданных).
- **Почему:** MSAL не принимает внешние токены и не поддерживает обход кэша — это нарушает контракт библиотеки и ломает silent refresh, причём при каждом мажорном апдейте MSAL риск повторяется. Преимущества по сравнению с A/B не оправдывают этой постоянной хрупкости.

**ADR-2: Шифрование любых сохранённых нами OAuth-данных — Keystore-backed AES (если будет B).**

- **Решение:** в случае выбора варианта B все `token_payload`/`refresh_token_blob` хранятся через тот же слой, что и пароли `network_credentials` (`CryptoHelper` поверх Keystore), либо через дедицированный `EncryptedSharedPreferences` для blob'ов.
- **Альтернативы:** хранить токены в открытом виде в Room (отвергнуто); полагаться только на encryption-at-rest файловой системы (отвергнуто — нужен Keystore-binding).
- **Почему:** требуемый уровень безопасности — не ниже текущего SDK-кэша; refresh-токены особенно чувствительны.

---

## 10. Связи с другими спеками

- S0166 — per-host encrypted auth storage (`AuthSessionRepository`). Возможный «донор» паттерна для общего реестра.
- S0157 / S0182 / S0211 — UI Settings → Authorizations и дедуп аккаунтов. Целевой UX-шаблон для нового экрана.
- S0200 — миграция Google Drive auth на Credential Manager + identity-домен. Источник правды по Drive после фазы 04/05.
- S0166 / S0182 — модель `account_id` + `host` + `displayName`, переиспользуема концептуально.
- Любая будущая реализация (если рекомендация Go) — отдельный спек, который ссылается на S0267 как на источник архитектурного решения.

---

## 11. Критерии готовности (strategic-level) + Рекомендация

### 11.1 Критерии — статус

1. ✅ Документировано в §4: Drive → identity-домен (Credential Manager + `PrimaryGoogleAccountStore`), Dropbox → `EncryptedSharedPreferences "dropbox_credentials"` через `DropboxClientUtils`, OneDrive → MSAL приватный кэш + stub-row в `network_credentials`.
2. ✅ Все семь пунктов §6 закрыты (см. §6.1..§6.7); прототип не требуется, ответы получены из публичных SDK-источников + локального кода.
3. ✅ Сравнительная сводка обновлена: C — окончательно отвергнут (§6.1, ADR-1); A и B оба реализуемы; B даёт `expires_at`/`scopes` в UI без round-trip к SDK, что критично для unified-экрана (Q7 → все три источника).
4. ✅ Рекомендация сформулирована — см. §11.2.
5. ✅ Спеки-следствия перечислены — см. §11.3.

### 11.2 Рекомендация: **Go — Hybrid Mirror (вариант B) + scope «все три источника»**

**Обоснование:**

- Вариант A (Management Facade без зеркала) технически работоспособен, но не даёт `expires_at`/`scopes` без round-trip в SDK. При scope «все три источника» (Q7) UI должен показывать сводный статус каждой записи без раскрытия — это значит метаданные обязаны быть в Room. A с дополнительным in-memory cache становится фактически undercooked B.
- Вариант B даёт прямой ответ на UX-требование (§3.1 п.1): единый список, единые действия, единый источник метаданных. Стоимость — одна Room-таблица + миграция + `interface AuthAccountSource` поверх трёх источников.
- Вариант C снят §6.1.
- §6.4 единогласно подтверждает: ни в A, ни в B push-callback нет — это общая константа, не аргумент против B.
- Сложность B концентрируется в одной точке (`cloud_auth_accounts` + `AuthAccountSource`), а не размазана по UI как в A. Это лучше для долгосрочной поддерживаемости.

### 11.3 Спеки-следствия (создаются отдельно после `/spec-tech S0267`)

После Go B следующая тактическая спецификация декомпозирует работу на отдельные стратегические тикеты:

1. **`AuthAccountSource` доменный слой + `cloud_auth_accounts` таблица + Room v31→v32 миграция** — фундамент.
2. **Cloud-провайдер адаптеры** (по одному стратегическому тикету или единым пакетом):
   - Google Drive — переиспользовать существующий `requestSecondaryAccount` (§6.3); адаптер пишет в `cloud_auth_accounts` при каждом `getAccessToken`-успехе.
   - Dropbox — миграция `DropboxClientUtils` с hand-rolled JSON на `DbxCredential.Reader/Writer`; адаптер пишет в зеркало при `initializeWithCredential` и `refresh`.
   - OneDrive — адаптер пишет в зеркало при MSAL `acquireToken*`-успехе; stub-row в `network_credentials` удаляется по lazy-fill принципу (см. §6.6).
3. **Social и Network sources** — `SocialAuthAccountSource` поверх `AuthSessionRepository`, `NetworkAuthAccountSource` поверх `network_credentials`; адаптация имеющихся методов rename/delete/relogin под общий интерфейс.
4. **Unified UI Settings → Authorizations** — список с фильтром по типу + бейджи + диалоги подтверждения per-source-type.
5. **`CredentialAuditor` cloud-расширение** (опционально, после релиза основного scope) — отдельный аудитор для `cloud_auth_accounts` без смешения с SMB-логикой.

Декомпозицию точнее зафиксирует `/spec-tech S0267` в `PLAN/S0267_cloud-auth-unified-storage-research/`.

---

## 12. Ссылка на тактическую спецификацию

Все семь вопросов §6 закрыты (2026-05-20). Рекомендация §11.2 — **Go B**. Следующий шаг:

- `/spec-tech S0267` → тактическая спецификация под Hybrid Mirror + Room v31→v32 миграцию + `interface AuthAccountSource` поверх трёх источников + декомпозиция на отдельные стратегические тикеты (см. §11.3).
- Если по итогам декомпозиции (например, на стадии прототипа Phase 1) обнаружится, что Room-миграция блокирующая — допустим возврат к варианту A. Этот edge case должен быть проверен до начала Phase 2.

---

## Last Audit

**Date:** 2026-05-22
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 13 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- [ ] No on-device verification required - S0267 is research-only and produces no executable artefact. The follow-on child tickets each carry their own on-device gates per `PLAN/S0267_cloud-auth-unified-storage-research/ROLLOUT_ORDER.md` stop-go checkpoints.
