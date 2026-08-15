# Стратегическая спецификация: S0751 - Чистка detekt-дрейфа в cloud-auth и browse

**Ticket:** S0751
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-27
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-27
**Tactical spec:** `PLAN/S0751_cleanup-detekt-drift-cloud-browse/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст пользователя и вложения. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`; секцию можно удалить, когда материал перенесён.

**Захвачено:** 2026-06-27

**Захвачено во время:** S0749 (post-change detekt-гейт упал на несвязанных файлах)

**Текст:**

Авто-захват из CLAUDE.md §3.1. Detekt-гейт (`assert-detekt`, часть `post-change.ps1`) падает с NEW issues above baseline на файлах, не связанных с активным тикетом S0749. Нарушения тривиальные/механические, накопленный дрейф baseline в зонах cloud-auth и browse. Блокируют закрытие через `post-change` любому тикету, пока не исправлены или не перефрижен baseline.

Список нарушений на момент захвата:

- `app_v2/src/cloudEnabled/java/com/sza/fastmediasorter/identity/GoogleTokenIssuer.kt:82,84` - TooGenericExceptionThrown (throw generic Exception)
- `app_v2/src/cloudEnabled/java/com/sza/fastmediasorter/identity/GoogleTokenIssuer.kt:3` - ImportOrdering
- `app_v2/src/cloudEnabled/java/com/sza/fastmediasorter/identity/GoogleTokenIssuer.kt:67` - UnusedParameter (`email`)
- `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveInteractiveSignInCoordinator.kt:3` - ImportOrdering
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt:3` - ImportOrdering
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt:3` - ImportOrdering
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseObserverManager.kt:3` - ImportOrdering
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudauth/GoogleDriveAuthResolutionActivity.kt:3` - ImportOrdering
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt:144` - MaxLineLength

**Вложения:**

Вложений нет.

---

## 1. Проблема

Detekt-гейт (`assert-detekt`, часть `post-change.ps1`) падал с NEW issues above baseline на файлах в зонах cloud-auth и browse, не связанных с активным тикетом. Накопленный механический дрейф (порядок импортов, generic-исключения, длинная строка, неиспользуемый параметр) блокировал закрытие через `post-change` любому последующему тикету. Эффекта на пользователя нет - чисто гигиена статанализа.

---

## 2. Цели

1. Гейт `:app_v2:detekt` проходит без NEW findings поверх baseline (без рефриза baseline).
2. Все 9 захваченных нарушений устранены построчно, поведение неизменно.

**Non-goals:**

- Рефриз detekt baseline (спрятал бы дрейф, а не убрал).
- Любая правка вне 4 затронутых зон (identity, data/cloud, ui/browse, domain/model, ui/cloudauth).
- Изменение рантайм-поведения выдачи токенов или browse.

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

- **Related tickets:** S0749 (обнаружено во время его реализации)

---

## 4. Контекст текущей архитектуры

Затронутые зоны - identity (выдача OAuth-токенов), data/cloud (интерактивный sign-in), ui/browse (+ менеджеры), domain/model (настройки), ui/cloudauth (resolution-активити). Архитектурных изменений не требуется: проблема чисто в статанализе, а не в слоях.

---

## 5. Предлагаемый подход

Механическая правка под существующие правила detekt/ktlint. Новых ролей, потоков данных и точек расширяемости не вводится.

---

## 6. Открытые вопросы / Research items

- РЕШЕНО: построчная правка (нарушения механические), без рефриза baseline.
- Замечание реализации: ktlint-порядок импортов при `formatting.android=true` = layout `*,java.**,javax.**,kotlin.**,^` - сначала всё прочее (вкл. `kotlinx`/`timber`), затем `java.*`, `javax.*`, `kotlin.*` в конце; внутри группы сортировка case-sensitive ASCII (заглавные раньше строчных). Detekt не автокорректит (`autoCorrect: false`).
- `TooGenericExceptionThrown` устранено через `check(..)`/`error(..)` (требование `UseCheckOrError`), а не `throw IllegalStateException`.
- `UnusedParameter email` оставлен в сигнатуре (контракт, который ассертят тесты) под `@Suppress("UnusedParameter")` с пояснением: GMS резолвит аккаунт неявно, кэш чистится при смене аккаунта.

---

## 7. Риски

- Пересортировка импортов могла бы случайно удалить/дублировать импорт. Митигация: число импортов сверено с detekt-сигнатурой, `compileStandardDebugKotlin` зелёный.
- Замена `throw IllegalStateException` на `check`/`error` могла бы изменить тип исключения. Митигация: оба бросают `IllegalStateException`, ловятся тем же `runCatching` -> `Failed`, поведение неизменно.

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта.

---

## 10. Связи с другими спеками

- S0749 - обнаружено при его реализации (detekt-гейт падал на несвязанных файлах).

---

## 11. Критерии готовности (strategic-level)

1. `:app_v2:detekt --rerun-tasks` завершается без NEW findings поверх baseline.
2. `compileStandardDebugKotlin` проходит (пересортировка импортов не потеряла ссылок).
3. Baseline `config/detekt/baseline-app_v2.xml` не менялся.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0751` - создаст `PLAN/S0751_cleanup-detekt-drift-cloud-browse/` с фазами.

---

## Last Audit

**Date:** 2026-06-27
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 11 · WARN 0 · FAIL 0 · MANUAL 2 · EXEMPT 1

All 9 captured violations resolved by static inspection:

- `GoogleTokenIssuer.kt` - `check(..)`/`error(..)` replace generic throws; `@Suppress("UnusedParameter")` on `email`; import block ordered `*` -> `java` -> `javax`.
- 5x ImportOrdering (`GoogleDriveInteractiveSignInCoordinator`, `BrowseViewModel`, `BrowseManagerInitializer`, `BrowseObserverManager`, `GoogleDriveAuthResolutionActivity`) - all blocks ordered `*` (ASCII case-sensitive) -> `java.**` -> `javax.**`, no stray `kotlin.*`.
- `AppSettings.kt:144` - 116 chars (< 120 MaxLineLength).

Baseline `config/detekt/baseline-app_v2.xml` unchanged (working tree clean). FEATURES §8 "Без изменений" -> EXEMPT.

### Manual / on-device

- [ ] `:app_v2:detekt --rerun-tasks` green (per-violation static-verified here; authoritative gradle run owned by pipeline build gate).
- [ ] `compileStandardDebugKotlin` green (implied by Implemented status; no symbol-level import loss detected).
