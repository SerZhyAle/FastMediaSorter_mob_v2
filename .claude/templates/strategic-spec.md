<!-- Template consumed by: /spec (Process step 5), /spec-draft (Process step 5, full strategic skeleton). -->
<!--
Reconciliation note (S1338 step 07.4). /spec and /spec-draft each carried a near-identical copy of
this skeleton. The superset is kept here; every difference is recorded rather than dropped:
- "## 0. Захваченный материал (inbox)" existed only in the /spec-draft copy. It is marked
  "Draft only" below - /spec omits that whole section.
- "### 3.3 Owner inputs (Approval gate)" carried two different hint bodies. Both are kept below,
  each marked with the command that uses it.
- The /spec-draft copy carried shorter one-line hints in §2, §5, §5.1, §8, §10 and §11, and in §6
  and §9 it kept only the fallback line without the structured item body. The fuller /spec wording
  is the superset and is what stays; no placeholder token was changed, nothing unique was dropped.
Substitute: <Sxxxx>, <short-name>, <Название фичи>, <YYYY-MM-DD>, <метка>, priority, roadmap entry.
-->

# Стратегическая спецификация: <Sxxxx> - <Название фичи>

**Ticket:** <Sxxxx>
**Status:** Draft
**Priority:** <0..100>
**Date:** <YYYY-MM-DD>
**Tier:** <метка>
**Roadmap entry:** <текст из роадмапа или «Ad-hoc - запрос <дата>»>
**Tactical spec:** `PLAN/<Sxxxx>_<short-name>/` (будет создан через `/spec-tech`)

---

<!-- Draft only (/spec-draft): keep this section. /spec omits it entirely. -->

## 0. Захваченный материал (inbox)

**Захвачено:** <YYYY-MM-DD>

**Текст:**

<вербатим-текст пользователя, без переписывания; или «нет текста»>

**Вложения:** (опустить если нет)
- <подпись> - `PLAN/<Sxxxx>_<short-name>/attachments/<файл>`

---

## 1. Проблема

<2–4 предложения. Что сломано или чего не хватает? Эффект на пользователя. Область - модуль/feature-path без имён классов.>

---

## 2. Цели

<Нумерованный список наблюдаемых улучшений. «Что станет возможным / что перестанет происходить».>

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

Каждое поле ниже должно содержать конкретное значение, чтобы спека могла перейти Draft → Approved. Состав полей определяется характером спеки (см. Process step 5.1) - irrelevant поля не эмитятся, их отсутствие гейтом не блокируется. Универсально обязательное поле - `Related tickets`. Проверка: `pwsh -NoProfile -File scripts/spec_catalog/check-owner-inputs.ps1 -Id Sxxxx`.

<!-- /spec-draft uses this single hint line instead of the paragraph above, and emits no bullet
     beyond Related tickets - §3.3 is filled at the Draft -> Approved gate, not at capture time. -->

<Заполняется при переходе Draft → Approved (через /spec или /spec-update). В скелете оставить пустым, кроме обязательного поля ниже.>

<!-- /spec emits ONLY the bullets matching the detected scope. Examples below; emit only the relevant subset. -->
<!--
- **Flavor scope:** <flavor-aware tag matched>
- **API level constraints:** <api-bound tag matched>
- **Wear OS:** <wear-os tag matched>
- **Performance budget:** <perf-critical tag matched>
- **Data compatibility:** <data-surface tag matched>
- **Localization:** <localization-touched tag matched>
- **UI placement contract:** <ui-facing tag matched>
- **Accessibility:** <ui-facing tag matched>
- **Communication policy:** <comm-policy-applies tag matched>
- **Validation level:** <any tag matched>
- **Owner sign-off:** <any tag matched - YYYY-MM-DD>
-->
- **Related tickets:** <Sxxxx-зависимости / зависящие, либо «none»>

---

## 4. Контекст текущей архитектуры

<1–2 абзаца. Какие слои/компоненты отвечают за затронутую область. Почему сейчас нельзя решить проблему из §1. Без перечисления классов.>

---

## 5. Предлагаемый подход

<Архитектурный уровень: какие роли появятся, откуда читают / куда пишут, что меняет ответственность. Имена классов, файлов, методов - запрещены.>

### 5.1 Основные столпы / модули

<Крупные логические блоки. Каждый - подглава с целью и требованиями.>

### 5.2 Потоки данных и событий

<Высокоуровневая схема. «UI → слой применения → кэш → ..». Без имён методов.>

### 5.3 Точки расширяемости

<Что должно остаться открытым к расширению.>

---

## 6. Открытые вопросы / Research items

1. **<Заголовок>**
   - **Вопрос:** <формулировка>
   - **Варианты:** <если известны>
   - **Нужно выяснить:** <что проверить>
   - **Статус:** Open / Resolved
   - **Артефакт:** `PLAN/<Sxxxx>_<short-name>/research/<NN>__<topic-slug>.md` <обязателен, если Resolved получен проведённым ресёрчем; опустить для Open или тривиального ответа>

<Если вопросов нет - «Открытых вопросов нет.»>

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| <описание> | Низкая / Средняя / Высокая | <что сломается> | <как предотвратить> |

---

## 8. Влияние на пользователя (docs/FEATURES)

<По умолчанию: «Без изменений в docs/FEATURES.» - если только этот спек не вводит способность, которую пользователь воспринял бы как новую фичу. Улучшения кода, рефакторинги, фиксы, UX-полировка, перфоманс, внутренние изменения - всегда «Без изменений». Если по факту новое: одно предложение для `docs/FEATURES.md` + `_RU` + `_UK`.>

---

## 9. Архитектурные решения (ADR)

**ADR-1: <Заголовок>**

- **Решение:** <что решено>
- **Альтернативы:** <что рассматривалось>
- **Почему:** <обоснование>

<Если нет - «ADR нет - решение по устоявшимся паттернам проекта.»>

---

## 10. Связи с другими спеками

<Список связей или «Связей нет.» Если статус будет `BlockByOtherTask` - указать блокирующий `Sxxxx` здесь.>

---

## 11. Критерии готовности (strategic-level)

<Нумерованный список. Наблюдаемые результаты, не архитектурные утверждения. «Пользователь видит X» или «Batch завершается за N минут».>
