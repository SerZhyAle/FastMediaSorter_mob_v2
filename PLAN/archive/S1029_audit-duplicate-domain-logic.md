# Стратегическая спецификация: S1029 - Аудит дублирующейся доменной логики по всему проекту

**Ticket:** S1029
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-13
**Tier:** 4 - Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-13
**Tactical spec:** `PLAN/S1029_audit-duplicate-domain-logic/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-13

**Текст:**

Title: project-wide sweep for duplicated/scattered domain-logic predicates (same code-smell class as S1028)

User's own request (verbatim, RU): "я хоч чтобы мы выявили здесь неприятность и завели тикет для поиска такого же типа говнокода или похожего ии исправления по всему проекту"
(Translation: identify the problem found in FileOperationUseCase.kt and file a ticket to search for the same kind/class of bad code across the whole project, and fix it.)

Seed example (already filed as its own concrete fix ticket, S1028 - dedupe-network-path-detection): the same conceptual predicate "is this path a network resource" is implemented three separate times with three different algorithms and three different sets of edge-case coverage:
- `app_v2/src/main/java/com/sza/fastmediasorter/core/util/PathUtils.kt:54`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationPathUtils.kt:38`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FileOperationUseCase.kt:228` (locally-scoped `fun File.isNetworkPath(protocol: String)`, reviewed line-by-line in chat: the 4-branch OR chain works around a real java.io.File slash-collapsing quirk, but one of its own inline comments - "// Single colon case" - mislabels the actual distinguishing factor, which is slash-count/leading-slash, not colon count)

This ticket is NOT about that one predicate (S1028 owns the concrete fix). This ticket is about the general pattern/class of smell: the same conceptual check or transformation reimplemented independently in 2+ places (often in different layers - core/util, data, domain) with silently divergent edge-case handling, so a fix to one copy does not propagate to the others. Scope: sweep app_v2 (and wear if applicable) for other instances of this same class - candidates to look for:
- other path/URI/scheme classification helpers (isContentUri, isLocalPath, getScheme-style functions) beyond the ones already found, which may have their own inconsistent siblings
- other "detect X across Copy/Move/Delete/Rename" duplicated when-blocks similar to the one surrounding the S1028 finding (FileOperationUseCase.executeInternal repeats the same 4-branch when four times, once per protocol, at lines ~239-337)
- any other protocol/extension/mime-type/permission classification logic that appears more than once with different literal forms or different completeness

Deliverable for this ticket: an inventory (not necessarily a full fix in one pass) of every duplicated-predicate cluster found, each either merged into a canonical implementation or explicitly deferred with a note, following whatever direction S1028 settles on for its own predicate as a template/precedent.

Related: S1028 (dedupe-network-path-detection) is the concrete first instance and should inform the approach here, but this ticket's scope is project-wide, not limited to network-path detection.

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

- **Related tickets:** S1028 (dedupe-network-path-detection - конкретный первый экземпляр этого класса проблемы, служит прецедентом для подхода здесь)

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

- Является ли аудит разовым проходом (инвентаризация + фикс найденного) или должен стать повторяемым механическим гейтом (по аналогии с `scripts/quality/assert-*.ps1`) - зависит от того, сколько кластеров дублирования найдётся и насколько часто появляются новые. **RESOLVED (2026-07-14):** разовый аудит + фикс топ-кластеров (ограниченный tier-4 ad-hoc): инвентаризировать кластеры дублирования, починить самые рискованные по образцу S1028, остальное явно отложить с пометкой. Механический повторяемый `assert-*` гейт НЕ строим.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Объём аудита неизвестен заранее (может найтись много кластеров дублирования) | Средняя | Тикет разрастается за пределы одной тактической итерации | Зафиксировать найденные кластеры инвентарём в §6/tactical-plan, чинить по одному, не блокируя весь тикет на полном покрытии |
| Слияние дублирующихся реализаций в одну меняет поведение edge-case для существующих вызывающих мест | Средняя | Регрессия в детекции путей/протоколов/MIME на реальных данных пользователя | Юнит-тесты на объединённый канонический предикат, покрывающие edge-case каждой из ранее существовавших реализаций, до переключения вызывающих мест |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES - технический долг, не пользовательская фича.

---

## 9. Архитектурные решения (ADR)

<Заполняется на этапе /spec: какой критерий отличает «этот же класс проблемы» от обычного, оправданного разделения похожей логики по слоям.>

---

## 10. Связи с другими спеками

S1028 (dedupe-network-path-detection) - конкретный первый найденный экземпляр этого класса проблемы; данный тикет - его обобщение на весь проект.

---

## 11. Критерии готовности (strategic-level)

1. Инвентарь кластеров дублирующейся доменной логики зафиксирован (research/01).
2. Топ низкорисковый кластер консолидирован в одну функцию; дубликаты удалены; поведение сохранено (регресс-тесты зелёные).
3. Остальные кластеры явно отложены с пометкой (кандидаты на follow-up тикеты).

---

## Last Audit

**Date:** 2026-07-14 | **Verdict:** Verified

- **Audit inventory:** 5 duplicate-domain-logic clusters found and ranked (research/01__duplicate-logic-inventory.md).
- **Fix delivered (cluster #1):** extracted `normalizeNetworkResourcePath(path)` into `domain/strategy/ResourceStrategy.kt`; routed `SftpResourceStrategy` + `FtpResourceStrategy` through it; deleted both byte-identical private copies. Behavior 100% preserved.
- **Regression net:** `SftpResourceStrategyTest` + `FtpResourceStrategyTest` pass UNMODIFIED (standard debug + targeted unit run green).
- **Deferred (per owner bounded scope, follow-up candidates):** #5 SMB error classification (divergent, live retry, device-gated); #4 extension->MIME (union-table decision); #3 host:port parsing (PlayerMediaLoaderManager needs tests first); #2 FileOperationResultExt copy (trivial visibility+delete quick win).
- **Gates:** standard debug BUILD SUCCESSFUL; assert-fast-gates PASS (neuroslop, ticket-logs 0). No device needed (pure refactor).
