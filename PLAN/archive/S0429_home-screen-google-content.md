# Стратегическая спецификация: S0429 - Операции с Google-контентом на домашней поверхности

**Ticket:** S0429
**Status:** Archived
**Tactical plan:** `PLAN/S0429_home-screen-google-content/INDEX.md`
**Priority:** 40
**Date:** 2026-06-15
**Tier:** 4 - Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc - выделено из research S0404 (§6 item 10) 2026-06-15

> **Scope:** STRATEGIC, research-first. Цели, ограничения, открытые вопросы. Draft - до апрува допускаются черновые формулировки. Технический объём - отдельным спеком после research-апрува (см. §7).

---

## 0. Происхождение

- Запрос: на домашней поверхности лаунчера (S0404) «оперировать с Google-контентом» - YouTube, музыка (YouTube Music), Gmail, Google Keep и т.п.: показывать контент-блоки и/или запускать действия.
- Решено вынести из S0404 в отдельный независимый спек: Google-контент не нужен для роли Home/запуска приложений и должен жить как опциональный info-блок/набор плиток в любом профиле (S0404 §5.3 гарантирует расширяемость контракта info-блоков).
- Это **research-спек**: фиксирует осуществимость и лестницу уровней; результат - owner-решение по объёму и **отдельный технический спек** на выбранные уровни.
- Research-вход: `PLAN/S0404_android-launcher-mode-profiles/research/10__google-content-integration.md`.
- Допущение скоупа: «Google-контент» рассматривается в рамках домашней поверхности S0404 (glance-блоки + быстрый запуск/действия), параллельно погоде (S0426), app-shortcuts (S0427), call/SMS (S0428). Не про загрузку/сортировку Google-контента ядром медиасортера.

---

## 1. Проблема

Домашняя поверхность лаунчера (S0404) показывает контекст профиля и умеет запускать приложения, но не даёт быстрых точек к Google-сервисам и их контенту: нельзя одним касанием с домашнего экрана увидеть «что играет» в YT Music, открыть YouTube/Gmail/Keep или быстро написать письмо. Для устройства-фоторамки/медиабокса как домашнего экрана это типовое ожидание.

При этом «оперировать с Google-контентом» технически очень неоднородно по сервисам: от тривиального deep-link до дорогого OAuth с restricted-scope и обязательной ежегодной security-assessment. Без research легко переоценить осуществимость (Keep-контент, например, в принципе недоступен через публичный API).

---

## 2. Цели

1. Определить осуществимую лестницу уровней «операций с Google-контентом» на домашней поверхности S0404 и зафиксировать, что реально без OAuth, что требует OAuth, а что недостижимо.
2. Опциональные точки к Google-сервисам подключаются через существующий контракт info-блоков/плиток S0404 - без изменения launcher-ядра.
3. Дешёвый базовый уровень (deep-link запуск + compose через `mailto:`) - Play-safe, без новых чувствительных разрешений, на всех целевых флейворах S0404.
4. Блок «сейчас играет» (провайдер-агностичный, через медиасессии Android) - без Google API; единственный спец-доступ (доступ к уведомлениям) - опт-ин с честной disclosure.
5. Более глубокие уровни (YouTube Data API; Gmail-glance) - явно выделены как owner-gated из-за стоимости OAuth-верификации / restricted-scope CASA.

**Non-goals:**

- Чтение/показ содержимого Google Keep (нет публичного consumer-API - только запуск приложения).
- Встроенное воспроизведение YouTube внутри лаунчера (нативный Player API устарел; остаётся deep-link).
- Полноценный почтовый клиент / музыкальный сервис внутри лаунчера.
- Любые операции с Google-контентом вне домашней поверхности S0404.

---

## 3. Пожелания и ограничения (черновые, до апрува)

- **Независимость:** реализует контракт info-блоков/плиток профиля S0404; добавление/удаление не затрагивает роль Home, реестр приложений, строку состояния.
- **OAuth - дорогой и не флейворный:** верификация OAuth-клиента на стороне Google-аккаунта, не Play; restricted scope (Gmail) тяжелы и на `standard`, и на `noLegal`. Это продуктово-комплаенсное owner-решение, а не обычная флейвор-развилка S0404.
- **Подпись:** Android-OAuth-клиент привязан к SHA-1 на каждый signingConfig → отдельные client ID на флейвор/кейстор (как MSAL-хэш подписи в проекте).
- **Now-playing без Google API:** `MediaSessionManager` + `NotificationListenerService` (special access `BIND_NOTIFICATION_LISTENER_SERVICE`); провайдер-агностичен, переиспользует потребность аудио-профиля S0404.
- **Сеть/производительность (S0404):** Data API - редко + кэш, без поллинга на каждый возврат на Home; deep-link/MediaController - без сети.
- **Флейворы:** там же, где домашняя поверхность S0404 (`noLegal`/`standard`/`legacy`/`photos`); `lite`/`vr` - нет (нет домашней поверхности). Уровни 0-1 одинаковы на всех; уровни 2-3 - по owner-решению.
- **Переиспользование (поправка research 2026-06-23):** Google-аккаунт-плумбинг НЕ архивный, а ЖИВОЙ и современный - `domain/identity/GoogleIdentityRepository` (контракт: `signInPrimary`, `requestAdditionalScopes`, `getAccessToken(scopes)`), `cloudEnabled/identity/CredentialManagerGoogleIdentityRepository` (Credential Manager, не deprecated GoogleSignIn), `cloudEnabled/identity/GoogleTokenIssuer` (`GoogleAuthUtil` access-токены, кэш+refresh), `domain/identity/GoogleScope` (типизированные scope), `cloudDisabled/identity/NoOpGoogleIdentityRepository` (no-op для `lite`). Уровень 2 = одна новая `GoogleScope`-константа + token-request поверх этого стека, не новый auth-слой.
- **Локализация:** EN/RU/UK обязательно (названия блоков/плиток, disclosure доступа к уведомлениям, пустые/ошибочные состояния, атрибуция).
- **Доступность:** блоки и плитки работают с D-pad/пульта/клавиатуры/мыши (контракт ввода S0404).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0404 (эпик лаунчера, Archived - рабочий стол и гаджеты доставлены); S0427 (шорткаты сторонних приложений); S1103 (категории ячеек); S0426 (гаджет погоды - образец гаджета стола); S0428 (сиблинг той же серии).
- **Объём первой итерации:** только уровень 1 - гаджет «Сейчас играет» (медиасессия, показ и управление).
- **Уровень 0 (deep-link плитки):** не реализуется здесь - уже покрыт ячейками сторонних приложений и их шорткатами (S0427). Отдельная плитка compose `mailto:` в объём не входит.
- **Уровень 2 (YouTube-glance):** отложен - отдельный owner-gated follow-on поверх живого identity-стека, не часть первого тех-спека.
- **Уровень 3 (Gmail-glance):** вне объёма бессрочно - restricted scope требует ежегодного CASA Tier 3.
- **Спец-доступ:** notification listener - opt-in на всех целевых флейворах, с prominent in-app disclosure до перехода в системные настройки. При отказе гаджет показывает только сессию самого приложения, а не скрывается.
- **Точка включения спец-доступа:** кнопка на самом гаджете в его урезанном состоянии (образец - диалог доступа ко всем файлам, `AddResourceScanManager.kt:365-379`), плюс запись в реестре разрешений для честного перечисления способности. Отдельной строки в `LauncherSettingsDialogFragment` не заводим (quiz 2026-08-06). **Поправка 2026-08-06:** образец `manage_external_storage` устроен из двух частей, и §5 item 4 указывал только на первую - строка реестра лежит в `PermissionRegistryRepositoryImpl.kt:58-66`, а проверка спец-доступа собственным предикатом вместо `checkSelfPermission` живёт в `CheckPermissionStatusUseCase.kt:26-37`, где `when (entry.manifestName)` разбирает `MANAGE_EXTERNAL_STORAGE`, `MANAGE_MEDIA` и оптимизацию батареи. Тикету нужны обе.
- **Flavor:** там же, где домашняя поверхность лаунчера; `lite`/`vr` - нет.
- **Локализация:** EN/RU/UK - название гаджета, текст disclosure, пустые и ошибочные состояния.

---

## 4. Лестница уровней (из research 10)

- **Уровень 0 - Play-safe deep-link-плитки** (zero OAuth, zero чувствительных прав): запуск YouTube / YT Music / Gmail / Keep + compose `mailto:`. Реалистичный первый объём. На Android 11+ - объявить `<queries>` для целевых пакетов + `SENDTO`/`mailto` и `VIEW`/`https`, resolve через `PackageManagerCompat` (Rule 21), плитки скрывать при отсутствии цели.
- **Уровень 1 - блок «сейчас играет»** (special access, без Google API): показ + управление активной медиасессией; провайдер-агностичен (YT Music/Spotify/собственный плеер), переиспользует потребность аудио-профиля.
- **Уровень 2 - блок метаданных YouTube** (OAuth, **sensitive** scope `youtube.readonly`): «свежее из подписок» glance-списком → deep-link в YouTube. **Поправка research:** это SENSITIVE-scope - только OAuth app verification (бренд + обоснование + demo-видео, ~10 дней), БЕЗ CASA и БЕЗ платы Google. Есть и no-OAuth-вариант: чтение фиксированного публичного канала/плейлиста по API-key (без account-scoped данных). Quota 10k units/день на проект - с кэшем достаточно.
- **Уровень 3 - Gmail-glance** (OAuth + **restricted** scope `gmail.readonly` + CASA Tier 3): **вне объёма (рекомендация research).** Restricted-scope → ежегодный независимый security assessment (CASA Tier 3, пентест) ~$5,000+/год + недели работы, повторяется каждые 12 мес. ROI явно отрицательный для glance-плитки.
- **Keep-контент: не реализуемо** - нет публичного consumer-API, только запуск/share-to-create-note.

---

## 5. Открытые вопросы / Research items

1. **Объём первого технического спека** - [OWNER-DECISION, research-informed 2026-06-23]
   - **Рекомендация:** первый тех-спек = уровни 0 + 1 (deep-link плитки + now-playing). Оба Play-safe, ноль Google API, ноль денег; единственный спец-доступ - notification listener уровня 1. Уровень 2 - отдельно гейтящийся follow-on поверх живого identity-стека (дёшев: sensitive-verification, без CASA и платы), зависит от §5.2.
   - **Статус:** Resolved (quiz 2026-07-24) - первый тех-спек = только уровень 1. Уровень 0 отпал: пока спек стоял в блоке, рабочий стол получил ячейки сторонних приложений и их шорткаты (S0427), то есть запуск YouTube/YT Music/Gmail/Keep уже доступен без отдельных плиток.

2. **Готовность платить за OAuth-комплаенс** - [OWNER-DECISION-INFORMED: развилка стоимостей разная]
   - **Уровень 2 (YouTube):** sensitive scope - БЕЗ повторяющейся стоимости, БЕЗ платы Google, разовая верификация ~10 дней (бренд + обоснование + demo-видео). Низкая нагрузка → приемлемо, если YouTube-glance нужен.
   - **Уровень 3 (Gmail):** restricted scope - ежегодный CASA Tier 3 (пентест), $5,000+/год, недели, бессрочная ревалидация → рекомендуется отклонить/вне объёма.
   - **Статус:** Resolved (quiz 2026-07-24) - уровень 2 отложен как отдельный follow-on (не финансовый вопрос, а объём первого шага), уровень 3 исключён бессрочно.

3. **Состав и место Google-блоков на домашней поверхности по профилям** - [UI-CLARIFY]
   - **Вопрос:** какие плитки/блоки в каком профиле (аудио - now-playing; фоторамка/читалка - набор быстрых запусков), как соотносятся с реестром приложений и контентом профиля.
   - **Меню доступных блоков (из research):** сетка deep-link-плиток (все профили), now-playing-блок (прежде всего аудио-профиль), опц. YouTube-glance-список (owner-gated). Research-блокера нет.
   - **Статус:** Open, сузился (quiz 2026-07-24) - в объёме остался один гаджет «Сейчас играет», поэтому `/ui-clarify` на этапе тех-спека решает только его вид, размеры и набор органов управления.
   - **Снят research'ем 2026-08-06:** гаджет уже существует - `AudioNowPlayingGadget` / `AudioNowPlayingGadgetView` (S1170) с готовым макетом `gadget_launcher_now_playing.xml`, спанами и набором органов управления (previous / play-pause / next, тап по телу открывает плеер). Вид, размеры и состав кнопок отвечает архитектура, а не владелец: этот тикет меняет **источник данных** гаджета, а не его presentation. `/ui-clarify` на этапе тех-спека не нужен.

4. **Что из уровня 1 уже доставлено** - [RESOLVED-BY-RESEARCH 2026-08-06]
   - **Есть:** гаджет «Сейчас играет» на столе, но **только для собственной сессии приложения** - он читает `widget/AudioNowPlayingSnapshotStore` и шлёт команды в `AudioPlaybackService`. Это ровно то состояние, которое §3.3 описывает как деградацию при отказе от доступа к уведомлениям.
   - **Нет ничего провайдер-агностичного:** во всём репозитории ноль вхождений `MediaSessionManager`, `NotificationListenerService`, `BIND_NOTIFICATION_LISTENER_SERVICE` и `getActiveSessions`. YT Music, Spotify и любой чужой плеер гаджету сейчас не видны.
   - **Следствие для объёма:** тикет сводится к одному - научить существующий гаджет читать активную медиасессию любого приложения через `MediaSessionManager.getActiveSessions` за opt-in доступом к уведомлениям, с prominent in-app disclosure до перехода в системные настройки и с падением обратно на собственную сессию при отказе. Новый гаджет не создаётся.
   - **Что уже разведано под тех-спек (2026-08-06):** гаджет тянет данные `AudioNowPlayingSnapshotStore.read(context)` опросом раз в 2 с (`AudioNowPlayingGadget.kt:83-91`) и шлёт транспортные команды прямо в `AudioPlaybackService` через `startService` (106-115) - оба конца зашиты на собственное приложение, промежуточного интерфейса нет. Реестр разрешений уже умеет special access: запись `manage_external_storage` проверяется через `Environment.isExternalStorageManager()`, а не `checkSelfPermission` (`PermissionRegistryRepositoryImpl.kt:58-66`). Сервис-слушатель уведомлений объявлять в `src/launcherEnabled/AndroidManifest.xml` - он инжектится только для `standard` и `noLegal` (`build.gradle.kts:1062-1064`), сейчас `<service>` там нет ни одного.

5. **Где включается доступ к уведомлениям** - [UI-CLARIFY, блокирующий тех-спек]
   - Это последнее, что осталось от §5.3: presentation гаджета отвечает архитектура, а вот точка включения - новая поверхность, которой в спеке нет.
   - **В приложении есть два равноправных прецедента, и они противоречат друг другу:**
     - **(A) Контекстно, в момент нужды.** Так сделан доступ ко всем файлам: диалог `showAllFilesAccessPermissionDialog()` с «Предоставить» / «Продолжить с ограничениями» / «Отмена» и уходом в системные настройки (`AddResourceScanManager.kt:365-379`, повтор в `BrowseLifecycleHelper.kt:97-99`). В нашем случае это была бы кнопка на самом гаджете в его урезанном состоянии.
     - **(B) Строкой в настройках.** Так сделан `manage_external_storage` - запись в реестре разрешений и строка на экране разрешений; для лаунчера уже есть свой экран `LauncherSettingsDialogFragment` (`SettingsDocScopeCatalog.kt:38-43`, `rowLauncherSettings`).
   - **Рекомендация:** (A) плюс запись в реестре разрешений для честного перечисления - опция ценна ровно там, где видна её польза, а гаджет и так обязан иметь урезанное состояние по §3.3. Но выбор между «кнопка на гаджете», «строка в настройках лаунчера» и «оба места» - продуктовый, и угадывать его нельзя.
   - **Статус:** Resolved (quiz 2026-08-06) - вариант (A) плюс запись в реестре разрешений, ровно по рекомендации. Отдельной строки в настройках лаунчера нет.

### Quiz decisions (2026-08-06)

- Точка включения доступа к уведомлениям → кнопка на гаджете в урезанном состоянии плюс запись в реестре разрешений. Кнопка стоит там, где польза очевидна; реестр не даёт способности остаться незаявленной.
- Следствие для тех-спека: новых экранов и новых строк настроек не появляется, `LauncherSettingsDialogFragment` не трогаем.

### Quiz decisions (2026-07-24)

- Объём первого тех-спека → только уровень 1, гаджет «Сейчас играет» (уровень 0 уже покрыт ячейками приложений и шорткатами S0427).
- Доступ к чужим медиасессиям → notification listener с opt-in и disclosure на всех целевых флейворах; при отказе гаджет деградирует до сессии самого приложения.
- Уровень 2 (YouTube-glance) → отложен отдельным follow-on; уровень 3 (Gmail) → исключён.
- Блокер S0404 снят: эпик лаунчера доставлен и архивирован.

6. **Disclosure доступа к уведомлениям (уровень 1)** - [RESOLVED-BY-RESEARCH: форма ясна, остаётся copy]
   - **Решение:** требуется prominent in-app disclosure + явный opt-in ДО перехода на системный экран Notification access, с формулировкой «лаунчер читает активную медиасессию только для показа/управления „сейчас играет“»; при отказе - блок скрыт. Разрешённое Play-использование (core functionality + disclosure). Остаётся только EN/RU/UK-формулировка - copy-задача, не research-блокер.
   - **Статус:** Resolved - формулировка по `docs/COMMUNICATION_POLICY.md` на этапе тех-спека.

---

## 6. Связи с другими спеками

- **S0404** (launcher-mode) - предоставляет контракт info-блоков/плиток и домашнюю поверхность; S0429 - независимая надстройка, не блокирует и не блокируется S0404. Реализуется после того, как контракт info-блоков/реестра приложений S0404 существует.
- **S0426** (погода), **S0427** (app-shortcuts), **S0428** (call/SMS) - сиблинги той же серии независимых способностей домашней поверхности; общий паттерн «контракт в S0404 + независимый спек».
- Переиспользование: **живой** Google-аккаунт-плумбинг (`identity/GoogleIdentityRepository` + Credential Manager + `GoogleTokenIssuer` + `GoogleScope`, per-build web client ID), родом из S0200/S0233/S0294 - не архив, а текущий фундамент для уровня 2 (см. §3, §8).
- Research-вход: `PLAN/S0404_android-launcher-mode-profiles/research/10__google-content-integration.md`.

---

## 7. Следующий шаг

- Лестница утверждена владельцем (quiz 2026-07-24), точка включения спец-доступа выбрана (quiz 2026-08-06), блокер S0404 снят.
- `/spec-tech S0429` - тактический план **источника данных существующего гаджета**, не нового гаджета: `MediaSessionManager` + `NotificationListenerService` (объявить в `src/launcherEnabled/AndroidManifest.xml`) за интерфейсом слоя данных, поверх которого текущий путь `AudioNowPlayingSnapshotStore` остаётся фолбэком; opt-in-поток с disclosure по кнопке на гаджете; запись в реестре разрешений с собственным предикатом проверки; локализация EN/RU/UK.

---

## 8. Research-итоги (2026-06-23)

Источник: research-агент, сверка с developers.google.com / support.google.com / cloud.google.com + grep рабочего дерева на 2026-06-23. Research-вход - `PLAN/S0404_android-launcher-mode-profiles/research/10__google-content-integration.md`.

- **YouTube Data API v3 (уровень 2):** quota 10k units/день на проект (search.list ~100, list-чтения ~1-3); увеличение - не self-service и не платный SKU, только через compliance-audit. Чтение подписок/активности - account-scoped → OAuth; публичные данные (uploads-плейлист канала) - по API-key без OAuth. `youtube.readonly` - **sensitive** (verification only, без CASA, без платы Google, ~10 дней). <https://developers.google.com/youtube/v3/guides/quota_and_compliance_audits>, <https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification>
- **Gmail API (уровень 3):** `gmail.readonly` - **restricted** → ежегодный CASA Tier 3 (пентест), $5,000+/год, ревалидация каждые 12 мес. Вне объёма. <https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification>
- **Now-playing (уровень 1):** `MediaSessionManager.getActiveSessions` требует включённого `NotificationListenerService` (`BIND_NOTIFICATION_LISTENER_SERVICE`, спец-доступ через Settings). Провайдер-агностичен, без Google API. Play разрешает при core-functionality + prominent disclosure + opt-in; отдельной Console-формы (как у SMS/Call-Log) нет, но обоснование в листинге обязательно. <https://developer.android.com/reference/android/service/notification/NotificationListenerService>, <https://support.google.com/googleplay/android-developer/answer/16558241>
- **Deep-links (уровень 0):** YouTube (`com.google.android.youtube`, `youtu.be`/`youtube.com`), YT Music (`com.google.android.apps.youtube.music`/`music.youtube.com`), Gmail (`com.google.android.gm`, compose `mailto:`), Keep (`com.google.android.keep`, share-to-create). На Android 11+ обязателен `<queries>` (пакеты + intent-фильтры), иначе resolve возвращает null и плитки выглядят недоступными. <https://developer.android.com/training/package-visibility/use-cases>
- **Переиспользование (поправка к §3/§6):** Google-плумбинг ЖИВОЙ (`GoogleIdentityRepository`, `CredentialManagerGoogleIdentityRepository`, `GoogleTokenIssuer`, `GoogleScope`, `NoOpGoogleIdentityRepository`; per-build web client ID через `R.string.google_web_client_id`; MSAL уже использует per-signingConfig хэши). Уровень 2 = одна новая scope-константа + token-request, не новый auth-слой.
- **Ловушки:** account-scoped YouTube-чтение всё равно требует verification (экран «unverified app» до завершения); quota - на проект, не на флейвор (баг-поллинг исчерпает её на всё приложение → строго «редко + кэш, без поллинга на каждый возврат на Home»); пропуск `<queries>` тихо ломает плитки на Android 11+; notification access Play ревьюит скептически - держать строго opt-in/core-functional; sideload `noLegal` НЕ обходит OAuth-верификацию (она на стороне Google Cloud, не Play).
