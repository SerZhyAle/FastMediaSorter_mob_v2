# Стратегическая спецификация: S1787 - Паритет зеркал .github/prompts с библиотекой команд

**Ticket:** S1787
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-17
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - находка при подготовке точек входа для Codex/Gemini 2026-08-17
**Tactical plan:** [`PLAN/S1787_prompt-mirrors-command-parity/INDEX.md`](S1787_prompt-mirrors-command-parity/INDEX.md)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-17

**Текст:**

.github/prompts/*.prompt.md mirrors of .claude/commands/*.md have drifted: 23 mirrors against 32 commands (missing spec-next, spec-do, spec-draft, spec-quiz, release, skill-release, skill-fix-release, newlog, verify), and the surviving mirrors run behind their command (spec-dev.prompt.md 15 KB vs spec-dev.md 25 KB; build.prompt.md 8.5 KB vs build.md 18 KB). No parity gate exists. Decide: retire the mirrors, generate them from the command files, or gate the parity.

**Захвачено во время:** задача «точки входа для Codex и Gemini» (AGENTS.md §9, GEMINI.md, .github/copilot-instructions.md)

**Измерения на момент захвата:**

- `.claude/commands/*.md` - 32 файла; `.github/prompts/*.prompt.md` - 23 файла.
- Отсутствуют зеркала: `spec-next`, `spec-do`, `spec-draft`, `spec-quiz`, `release`, `skill-release`, `skill-fix-release`, `newlog`, `verify`.
- Даты последней записи зеркал: 11 из 23 не менялись с мая 2026, при том что команды правились в августе.
- Размеры: `spec-dev` 15185 vs 24832, `build` 8542 vs 18425, `spec-tech` 15890 vs 17497, `catalog` 8778 vs 8084 (зеркало больше - тоже расхождение, но в другую сторону).
- Гейта паритета нет: `assert-*` по `prompt.md` не находится, `scripts/quality` про них не знает.

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

<Заполняется при переходе Draft → Approved (через /spec или /spec-update). В скелете оставить пустым, кроме обязательного поля ниже.>

- **Related tickets:** none
- **Owner decision:** Retire `.github/prompts/*.prompt.md`. The canonical procedure library remains `.claude/commands/*.md`; Copilot prompt-file shortcuts are not maintained in this repository.

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

1. **Кому вообще служат зеркала**
   - **Вопрос:** читает ли их сегодня хоть один рантайм, кроме VS Code Copilot?
   - **Варианты:** только Copilot / никто / Copilot плюс ручные вставки.
   - **Нужно выяснить:** механизм prompt files в текущей версии Copilot и факт использования владельцем.
   - **Статус:** Resolved
   - **Решение:** GitHub Copilot supports prompt files as manually invoked templates in VS Code, Visual Studio and JetBrains. The workspace does not enable `chat.promptFiles`, the owner chose not to maintain these shortcuts, and no other repository consumer was found.

2. **Что делать с расхождением**
   - **Вопрос:** удалить зеркала, генерировать их из команд или ввести гейт паритета?
   - **Варианты:** retire / generate / gate.
   - **Нужно выяснить:** цену каждой ветки против того, что зеркала уже полгода не совпадают ни разу.
   - **Статус:** Resolved
   - **Решение:** retire. Remove the 23 manual mirrors and references treating them as a maintained path; do not create a generator or a parity gate for a second source of truth.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| <описание> | Низкая / Средняя / Высокая | <что сломается> | <как предотвратить> |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES.

---

## 9. Архитектурные решения (ADR)

<Если нет - «ADR нет - решение по устоявшимся паттернам проекта.»>

---

## 10. Связи с другими спеками

<Список связей или «Связей нет.»>

---

## 11. Критерии готовности (strategic-level)

<Нумерованный список. Наблюдаемые результаты, не архитектурные утверждения.>

### Quiz decisions (2026-08-18)

- What should happen to the stale Copilot prompt mirrors? → Retire (A). They have no enabled workspace consumer here and duplicate the canonical command library.
