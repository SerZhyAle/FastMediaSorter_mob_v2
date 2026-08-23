# S1779 - Гейт: маршрут часов только из реестра

**Ticket:** S1779
**Status:** Archived
**Priority:** 45
**Date:** 2026-08-17
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** ad-hoc

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-17

**Захвачено во время:** S1726

**Текст:**

S1726 свёл все адреса навигации модуля часов в один реестр `wear/src/main/java/com/sza/fastmediasorter/wear/ui/navigation/WearRoutes.kt` и проверил вручную, что литералов маршрутов вне реестра не осталось.

Ручная проверка живёт ровно один раз. Следующий экран часов добавят голым литералом, и дефект вернётся - именно тот, ради которого заведён S1726: опечатка в маршруте не ошибка компиляции, а молчаливое бездействие при нажатии.

Нужен механический гейт в духе `scripts/quality/assert-*.ps1`: строковый литерал в позиции аргумента `composable(..)`, `navigate(..)`, `popUpTo(..)` или `startDestination = ..` внутри `wear/src/**` - отказ; единственное исключение - сам файл реестра. Подключить в набор быстрых гейтов (`.\a.ps1 fg`).

Открытый вопрос: ловить ли той же проверкой имена аргументов (`navArgument("..")`, `getInt("..")`) - там расхождение объявления и чтения даёт ту же тихую поломку.

---

## 1. Проблема

Маршруты были вынесены в реестр, но имена аргументов оставались строками в read-side `BrowseScreen`. Такая опечатка даёт тот же молчаливый отказ навигации, что и raw route literal.

---

## 6. Открытые вопросы / Research items

1. **Область проверки**
   - **Вопрос:** только маршруты, или ещё имена аргументов навигации?
   - **Решение:** маршруты и аргументы. Реестр уже содержит аргументные константы, а существующие raw reads доказали, что более узкий гейт оставит дефект.
   - **Статус:** Resolved

## 3. Исправление

Добавлен fast gate для raw literals в navigation-call sites и argument reads за пределами route registries. `BrowseScreen` переведён на существующие константы `WearRoutes`.

### 3.3 Owner inputs (Approval gate)

- **Flavor scope:** Wear module only; no handset flavor source set changes.
- **Wear OS:** Enforces the Wear navigation contract already declared by `WearRoutes` and `SettingsRoutes`.
- **Validation level:** Static gate plus scoped tooling closure.
- **Owner sign-off:** No product decision required; the gate preserves existing route-registry ownership.
- **Related tickets:** S1726.

## 4. Проверка

- `pwsh -NoProfile -File scripts/quality/assert-wear-route-literals.ps1 -Gate` exits 0.
- `post-change.ps1` scoped closure for the gate, wiring and Wear consumer exits 0.

## Last Audit

**Date:** 2026-08-18
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- No manual or device verification is required for this static route contract.
