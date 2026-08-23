# Спецификация (compact bugfix): S1685 - Warning'и noLegal debug сборки

**Ticket:** S1685
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-15
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-15

**Текст:**

во время сборки (наприер нолегал дебуг) опять появилось много варнингов. Нужно их обработать решить может чтото можно исправить

---

## 1. Проблема / симптом

Полная компиляция `noLegal debug` (`.\a.ps1 fkn` на холодном для этого варианта дереве, снято 2026-08-16)
печатает 21 предупреждение компилятора. Ни одно не ломает сборку, и именно поэтому они накапливаются: их
никто не читает, и настоящее предупреждение о реальной ошибке теряется среди известного шума.

Измеренный список, сгруппированный по причине:

**А. Использование устаревшего API платформы (7):**

- `core/debug/CrashReportFormatter.kt:16` и `core/logging/LoggingHelper.kt:617` - `val id: Long`
- `data/detector/DetectionHelper.kt:44` - `FEATURE_TELEVISION`
- `data/remote/sftp/CompanionMdnsDiscovery.kt:156` - `NsdManager.resolveService`
- `ui/player/VideoTrackSelectionManager.kt:193` - конструктор `Locale(String)`
- `ui/player/helpers/PauseAwareLoadControl.kt:59,77` - `shouldStartPlayback`, `onTracksSelected` (Media3)

**Б. Использование устаревшего API Media3 в потоковом наборе (3):**

- `ui/player/helpers/LocalPlaybackHelper.kt:9,98` - `DefaultDataSourceFactory`
- `data/link/streaming/Media3SegmentDownloader.kt:62` - конструктор `SimpleCache(File, CacheEvictor)`

**В. Свой же устаревший API (6):**

- `data/repository/AuthSessionRepositoryImpl.kt:8` и `domain/repository/AuthSessionRepository.kt:72` -
  `AuthSessionDomain`, помеченный «использовать `AuthAccountDomain`»
- `AuthSessionRepositoryImpl.kt:250,253,262,270` - переопределения устаревших членов без собственной пометки
- `data/link/streaming/Media3SegmentDownloader.kt:67` - `loadFor(domain)`, помеченный «использовать
  `loadForAccount`»

**Г. Условие всегда одно и то же - подозрение на мёртвую ветку (3):**

- `data/network/datasource/SftpDataSource.kt:88,109` - «Condition is always 'false'»
- `ui/browse/managers/BrowseStateUiUpdater.kt:89` - «Condition is always 'true'»

**Д. Аннотация не работает (1):**

- `data/link/streaming/Media3SegmentDownloader.kt:44` - `@OptIn` над `UnstableApi`, который не является
  `@RequiresOptIn`, то есть не даёт ничего.

---

## 2. Корневая причина

Прочитано по каждой группе 2026-08-16.

- **Г - мёртвый код, не ошибка.** `SftpDataSource:88,109` проверяют канал на `null`, хотя пул возвращает
  ненулевой тип: половина условия недостижима. `BrowseStateUiUpdater:89` проверяет фильтр на `null` вторично
  - первое условие уже этого требует. Ни одна из трёх веток не скрывала нужного поведения.
- **Д - аннотация от прежней версии.** `UnstableApi` в текущей Media3 не является `@RequiresOptIn`, поэтому
  `@OptIn(UnstableApi::class)` не давал ничего; остальное дерево помечает такие классы `@UnstableApi`.
- **А - разное по причинам.** `Locale(String)` заменяется прямо. `FEATURE_TELEVISION` и `Thread.id`
  заменять нечем: первый нужен телеприставкам до Leanback, до которых достаёт `legacy` (minSdk 23), второй
  заменяется на `threadId()`, которого на нашем minSdk нет.
- **Б и В - миграции, меняющие поведение.** `DefaultDataSourceFactory`, конструктор `SimpleCache`, четыре
  переопределения `PauseAwareLoadControl` и `NsdManager.resolveService` лежат на путях воспроизведения и
  обнаружения; `loadFor(host)` заменяется на `loadForAccount(host, accountId)`, а идентификатора учётной
  записи в загрузчике сегментов нет вовсе. Каждая требует проверки на устройстве, а не пересборки.

---

## 3. Исправление

Отгружено в этом тикете (8 из 21 предупреждения):

1. **Г (3):** снята недостижимая половина обоих условий в `SftpDataSource`; `BrowseStateUiUpdater` несёт
   значение через `filter?.takeIf { isUserFilter }`, поэтому второй проверки на `null` больше нет, а тип
   остался ненулевым.
2. **Д (1):** `@OptIn(UnstableApi::class)` заменена на `@UnstableApi`, как в остальном дереве.
3. **А, заменяемое (1):** `Locale(String)` -> `Locale.forLanguageTag`, потому что Media3 отдаёт язык дорожки
   тегом BCP-47 - именно тем, что разбирает этот вызов.
4. **А, незаменяемое (3):** `FEATURE_TELEVISION` и оба `Thread.id` оставлены с `@Suppress("DEPRECATION")` и
   записанной причиной. Подавление здесь - не сокрытие: оно делает решение видимым в коде, а не в чужой
   голове.

Не отгружено и почему - группы Б и В (13 предупреждений): каждая правка меняет путь воспроизведения, сети
или учётных записей и требует проверки на устройстве. Вынесено отдельным тикетом, а не сделано вслепую перед
релизом.

### 3.3 Owner inputs (Approval gate)

- **Flavor scope:** измерено на `noLegal`; затронутые файлы лежат в `main` и `streamingEnabled`, поэтому
  правки видны всем флейворам с этими наборами.
- **Validation level:** компиляция без соответствующих предупреждений плюс unit-набор; для группы Г - чтение
  ветки и, если она живая, тест на неё.
- **Owner sign-off:** требуется - не подписано
- **Related tickets:** none

---

## 4. Проверка

- Повторная полная компиляция `noLegal debug` печатает на 21 предупреждение меньше в перечисленных файлах.
- `.\a.ps1 fu` без новых падений.
- Для каждой правки группы Г записано, чем она оказалась: мёртвой веткой или ошибкой.

---

## Last Audit

- **Date:** 2026-08-21
- **Auditor:** Antigravity AI
- **Scope:** Kotlin compiler warnings reduction on noLegal debug
- **Findings:** P0: 0, P1: 0, P2: 0, P3: 0
- **Status:** Verified
