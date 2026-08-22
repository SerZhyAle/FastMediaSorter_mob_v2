# Стратегическая спецификация: S1874 - Имена hook-ов в CLAUDE.md расходятся с инвентарём

**Ticket:** S1874
**Status:** Archived
**Priority:** 40
**Date:** 2026-08-21
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - находка при исследовании S1872, 2026-08-21
**Tactical spec:** `PLAN/S1874_claude-md-hook-names-diverge-from-inventory/` (будет создан через `/spec-tech`)

---

<!-- Draft only (/spec-draft): keep this section. /spec omits it entirely. -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-21

**Текст:**

Найдено при инвентаризации скриптов и памяти по тикету S1872 (агент-исследователь, дословно):

> **Symptom:** CLAUDE.md Rules 24-28 describe a single hook `guard-bash.ps1` as enforcing four distinct refusals (find-safety, `.ps1`-as-Bash-head, fire-and-forget, unavailable-command). `docs/AGENT_HOOKS.md`'s own hook table (the doc CLAUDE.md Rule 29 calls "the complete inventory") lists these as **four separately-named global hooks**: `guard-ps1-in-bash.ps1` (Rule 25), `guard-fire-and-forget.ps1` (Rule 26), `guard-bash-unavailable-command.ps1` (Rule 28) (`docs/AGENT_HOOKS.md:21-23`). Android-rd-specialist's memory independently uses the `docs/AGENT_HOOKS.md` names, not CLAUDE.md's collapsed name, and they're correct. This may be intentional shorthand in CLAUDE.md's prose rather than a bug - I did not have grounds to conclude either way. Dedup-checked (`search.ps1 "AGENT_HOOKS"`, `search.ps1 "guard-bash"`) - no existing ticket.

Почему это стоит тикета, а не правки на месте: Rule 29 объявляет `docs/AGENT_HOOKS.md` полным инвентарём и требует править его в том же изменении, что и регистрацию hook-а, а гейт `assert-hook-inventory.ps1` судит расхождение. При этом сам текст правил 24-28 называет один hook там, где инвентарь называет четыре. Решить, что из этого истина - сокращение в прозе или ошибка - нельзя без владельца канона: hook-и глобальные, живут в плагине `sza`, и переименование в правилах затрагивает канон, а не только этот репозиторий.

**Дедуп-проверка:** `search.ps1 "hook"` - `(no records)`; `search.ps1 "guard"` - только S1809 `fire-and-forget-guard-misses-wear-targets` (BlockExternal), про другой предмет - покрытие целей wear, не именование.

**Захвачено во время:** S1872 research (инвентаризация скриптов), без прерывания активного тикета.

---

## Resolution

**Resolved:** 2026-08-21

`docs/AGENT_HOOKS.md` lists the legacy per-machine global registrations, while its 2026-08-18 canon re-sync note separately states that the installed `sza` plugin ships one `guard-bash.ps1` which absorbs the `find`, `.ps1` command-head, cmdlet-head and missing-interpreter checks. `guard-fire-and-forget.ps1` remains a separate plugin hook.

The shorthand in CLAUDE.md Rules 24, 25 and 28 therefore describes the plugin correctly; the inventory still correctly describes the live registrations. No hook name, registration, rule, or inventory entry is divergent. Archive this false-positive finding without a source change.

---

## 1. Проблема

<2–4 предложения. Что сломано или чего не хватает? Эффект на пользователя. Область - модуль/feature-path без имён классов.>

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

- **Related tickets:** <Sxxxx-зависимости / зависящие, либо «none»>

---

## 4. Контекст текущей архитектуры

<1–2 абзаца. Какие слои/компоненты отвечают за затронутую область.>

---

## 5. Предлагаемый подход

<Архитектурный уровень. Имена классов, файлов, методов - запрещены.>

---

## 6. Открытые вопросы / Research items

1. **<Заголовок>**
   - **Вопрос:** <формулировка>
   - **Варианты:** <если известны>
   - **Нужно выяснить:** <что проверить>
   - **Статус:** Open / Resolved

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| <описание> | Низкая / Средняя / Высокая | <что сломается> | <как предотвратить> |

---

## 8. Влияние на пользователя (docs/FEATURES)

<По умолчанию: «Без изменений в docs/FEATURES.»>

---

## 9. Архитектурные решения (ADR)

<Если нет - «ADR нет - решение по устоявшимся паттернам проекта.»>

---

## 10. Связи с другими спеками

<Список связей или «Связей нет.»>

---

## 11. Критерии готовности (strategic-level)

<Нумерованный список. Наблюдаемые результаты, не архитектурные утверждения.>
