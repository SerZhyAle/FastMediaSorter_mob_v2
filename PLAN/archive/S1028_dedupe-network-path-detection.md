# Стратегическая спецификация: S1028 - Дедупликация детекции network-path (SMB/SFTP/FTP/Cloud)

**Ticket:** S1028
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-13
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-13
**Tactical spec:** `PLAN/S1028_dedupe-network-path-detection/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-13

**Текст:**

Title: consolidate duplicate network-path-detection logic (SMB/SFTP/FTP/Cloud scheme sniffing)

Symptom/evidence (found while reviewing FileOperationUseCase.kt around the S1027 logging-trim work, out of scope for S1027 itself):

Three independent implementations of "is this path a network resource" exist in app_v2, each with different edge-case coverage and no shared source of truth:

1. `app_v2/src/main/java/com/sza/fastmediasorter/core/util/PathUtils.kt:54` - `isNetworkPath(path: String)` - extracts scheme via `path.indexOf("://")` and checks membership in `listOf("smb","sftp","ftp","cloud")`. Does NOT handle a leading "/" prefix (e.g. "/smb://host/x" would extract scheme "/smb" and miss the match) and does NOT handle the single-slash-after-colon form ("smb:/host/x").

2. `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationPathUtils.kt:16` and `:38` - `normalizeNetworkPath(path)` strips one leading "/" then upgrades "scheme:/" to "scheme://" for cloud/smb/sftp/ftp, then `isNetworkPath(path)` checks `startsWith("smb://")`/`sftp://`/`ftp://` (cloud handled separately via `cloudPathParser.isCloudPath`).

3. `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FileOperationUseCase.kt:228` - a locally-scoped `fun File.isNetworkPath(protocol: String)` (single protocol per call, invoked separately for "smb"/"sftp"/"ftp"/"cloud") that ORs four literal prefix forms: "$protocol://", "/$protocol://", "/$protocol:/", "$protocol:/". This is checking for the same File-path-normalization quirk (java.io.UnixFileSystem collapses "//" to "/", hence a File built from a "scheme://host" string can end up with `.path` == "scheme:/host") that utility #1 misses entirely and #2 handles differently (normalize-then-check vs check-every-literal-form).

Additionally, `FileOperationUseCase.executeInternal` duplicates the same 4-branch `when (operation) { Copy/Move/Delete/Rename -> ... }` detection block four times (once per protocol: smb, sftp, ftp, cloud) at lines ~239-337, differing only in the protocol string literal and local variable names - a candidate for extraction into one generic helper (e.g. iterate over the protocol list, or a `detectProtocol(operation, protocol): ProtocolUsage` helper) regardless of which network-path predicate is chosen as canonical.

Risk: a fix to one of the three predicates (e.g. handling a new malformed-URI edge case) silently does not apply to the other two, so SMB/SFTP/FTP/Cloud detection can diverge across call sites (thumbnail loading, file operations, cloud handler) without any test catching the divergence.

Suggested direction (for the assigned research/dev pass, not decided here): pick one canonical predicate (likely a `String.isNetworkPath()` extension in `core/util/PathUtils.kt` since it is dependency-free, extended to also normalize the single-slash and leading-slash forms) and route the other two call sites through it; then collapse the FileOperationUseCase protocol-detection when-blocks into one loop-based helper.

Not part of S1027 (which only trims log volume) - filing separately so it isn't lost.

---

## 1. Проблема

<2-4 предложения. Что сломано или чего не хватает? Эффект на пользователя. Область - модуль/feature-path без имён классов.>

---

## 2. Цели

<Нумерованный список наблюдаемых улучшений.>

**Non-goals:**

- <что явно вне объёма>

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<Нумерованный список желаемого, но необязательного к первой итерации.>

### 3.2 Жёсткие ограничения

- **Flavor:** <затронутые варианты сборки>
- **API level:** <минимальный уровень Android или «без API-специфики»>
- **Wear OS:** <затрагивается или нет>
- **Производительность:** <бюджет CPU/память/батарея, если критично>
- **Совместимость данных:** <форма миграции без номера версии Room>
- **Локализация:** EN/RU/UK - всегда обязательно, или уточнение.
- **Доступность:** <TalkBack, touch target, не-цветовое отличие - если фича визуальная>

### 3.3 Owner inputs (Approval gate)

<Заполняется при переходе Draft → Approved (через /spec или /spec-update). В скелете оставить пустым, кроме обязательного поля ниже.>

- **Related tickets:** S1027 (adjacent - the File.isNetworkPath snippet reviewed here lives in the same function S1027 touched, but S1027 itself only trims log volume and does not change detection logic)

---

## 4. Контекст текущей архитектуры

<1-2 абзаца. Какие слои/компоненты отвечают за затронутую область. Почему сейчас нельзя решить проблему из §1. Без перечисления классов.>

---

## 5. Предлагаемый подход

<Архитектурный уровень: какие роли появятся, откуда читают / куда пишут. Имена классов, файлов, методов - запрещены.>

### 5.1 Основные столпы / модули

<Крупные логические блоки.>

### 5.2 Потоки данных и событий

<Высокоуровневая схема. «UI → слой применения → кэш → ..». Без имён методов.>

### 5.3 Точки расширяемости

<Что должно остаться открытым к расширению.>

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет.

**Owner decision (2026-07-14):** только КОНСОЛИДАЦИЯ с сохранением поведения - все три вызывающих места маршрутизируются через одну функцию, повторяющую сегодняшнюю объединённую (union) логику матчинга ровно как есть; изменения поведения сейчас нет. Правки краевых случаев (ведущий `/`, форма `scheme:/` с одним слэшем после колона) ОТКЛАДЫВАются в отдельный follow-up тикет и в этом объёме не делаются.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Три реализации расходятся по краевым случаям (leading "/", single-slash после колона) | Средняя | Detection SMB/SFTP/FTP/Cloud может дать разный результат в разных вызывающих местах для одного и того же пути | Свести к одному каноничному предикату, покрыть краевые случаи тестами |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES.

---

## 9. Архитектурные решения (ADR)

<Заполняется на этапе /spec: какая из трёх реализаций становится каноничной и почему.>

---

## 10. Связи с другими спеками

Обнаружено при работе над S1027 (trim-file-transfer-logging-large-folders), но не является его частью.

- **S1023 (archived, audit)** - аудит показал, что все 9 сайтов `object : File(path)` с `override fun getPath(): String = path` безопасны (path везде local val/параметр, затеняет синтетическое свойство объекта; рекурсии S1021 нет). Но там же вскрыто дублирование фабрики network-aware File: два варианта `createNetworkAwareFile` (`ui/player/fileops/PlayerFileOperation.kt:134` - с `content://`; `ui/browse/managers/BrowseShareOperationsHelper.kt:250` - без) + 5 инлайнов в Browse-менеджерах, каждый со своим набором прежиксов. Консолидация сюда должна заодно ввести один безопасный `networkAwareFile(path, name?, size?)` (параметры -> невозможна ловушка затенения) и mechanical-гейт `assert-*`, запрещающий `object : File(..)` с `getPath`-override вне фабрики. **Внимание:** предикат `content://` расходится между сайтами - унификация меняет поведение и требует device-проверки файловых операций.

---

## 11. Критерии готовности (strategic-level)

<Нумерованный список. Наблюдаемые результаты, не архитектурные утверждения.>

---

## Remote log pass 2026-08-01/02

Device SM-S731B (Galaxy S25 FE), Android 16 / API 36, noLegal debug 2.60.7302.058. Bundle imported
via `/newlog` from `logs/fastmediasorter_20260729_162305.log` .. `logs/fastmediasorter_20260801_183450.log`.
This is a probe-firing record, not an acceptance verdict - a log proves the code path ran, not that
the screen looked right.

- Probe fired 120 times across all four protocols: `proto=ftp` (30), `proto=smb` (30), `proto=cloud` (27+), `proto=sftp` (23+7).
- Cloud routing confirmed end to end: `FileOperation.Move: smb=false sftp=false ftp=false cloud=true` then `FileOperation: Using Cloud handler`.
- Not covered: an actual copy/move/delete/rename was observed only on the cloud handler; smb/sftp/ftp were classified but not executed.
