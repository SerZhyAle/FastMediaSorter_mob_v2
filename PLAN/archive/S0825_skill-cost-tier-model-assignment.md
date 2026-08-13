# Стратегическая спецификация: S0825 - Назначение cheaper model-tier механическим skill'ам

**Ticket:** S0825
**Status:** Archived
**Priority:** 45
**Date:** 2026-06-30
**Tier:** 4 - Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc - child of S0816 (skill cost tiers pillar)

> **Scope:** STRATEGIC. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

> Отколото от S0816 §5.1 (Skill cost tiers) / §10 как отдельный механический deliverable - чтобы не смешивать doc-only playbook с поведенческой правкой skill-конфигурации.

**Захвачено:** 2026-06-30

**Текст:**

```text
Применить дешёвый `model:` frontmatter к чётко-механическим leaf-skill'ам.
Кандидаты: doc-update, git, ns, quick, skill-fix, caveman-commit.
Явно исключить reasoning-sensitive оркестраторы: /spec-dev, /spec-all, /spec-tech.
Policy и обоснование - в docs/AGENT_COST_PLAYBOOK.md (## Skill cost tiers).
```

**Контекст:**

- `model:` frontmatter уже механически поддержан оболочкой (7 команд + 2 агента на `sonnet`).
- Риск: понижение модели у reasoning-чувствительных skill'ов роняет качество исполнения - граница описана в playbook'е.
- Требуется per-skill оценка, какие маршруты безопасно перевести на дешёвый tier; возможно owner sign-off.

**Вложения:**

- Отдельных файлов нет.

---

## 1. Проблема

S0816 зафиксировал policy дешёвых skill-tier'ов в playbook'е, но не применил `model:` frontmatter ни к одному leaf-skill'у. Механическая правка отложена, потому что выбор «какой skill безопасно удешевить» требует отдельной per-skill оценки и не должен смешиваться с doc-only deliverable родителя.

---

## 2. Цели

1. Перевести чётко-механические leaf-skill'ы на cheaper `model:` tier без потери качества.
2. Зафиксировать критерий «механический skill» (детерминированный, structured, не reasoning-sensitive).
3. Не трогать reasoning-чувствительные оркестраторы.

**Non-goals:**

- Не менять модель у `/spec-dev`, `/spec-all`, `/spec-tech`.
- Не менять agent-defs, если не доказана безопасность.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Решение по каждому skill'у - доказуемо безопасное, а не «сэкономить любой ценой».

### 3.2 Жёсткие ограничения

- Не относится к Android flavor matrix / API level / Wear.
- Не ломать существующее skill-routing поведение.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0816 (parent - agent-session-cost-optimization)

---

## 5. Решение (applied)

Каждый кандидат оценён по содержимому `.claude/commands/<skill>.md` на mechanical vs reasoning-sensitive.

**Переведено на `model: sonnet` (механические leaf-skills):**

- `caveman-commit` - генерация commit-сообщения, чистое форматирование.
- `caveman` - переключение режима чата, near-zero reasoning.
- `quick` - микроправки (опечатка/цвет/отступ/одна строка).
- `ns` - только правка файла, без build/spec/доков.
- `git` - git-процедура (branch/stage/commit/push).
- `doc-update` - синк документации (agent `friendly-android-doc-writer` уже на sonnet).

**Исключено (reasoning-sensitive, остаются на сильной модели):**

- `skill-fix` - диагностика бага + гипотеза + минимальный фикс = code-reasoning. Был в исходных кандидатах §0, но research переопределил: понижение модели роняет качество фиксов.
- `caveman-review` - code review = ловля багов, reasoning-sensitive.
- Оркестраторы `/spec-dev`, `/spec-all`, `/spec-tech` и release-маршруты (`/release`, `/skill-release`, `/skill-fix-release`) - не трогаются per S0816 playbook caution.

Дешёвый tier = `sonnet` - конвенция репозитория (уже у `arc`/`catalog`/`log-reader`/`spec-arc`/`spec-check`/`spec-sweep`/`verify`).

---

## 6. Открытые вопросы / Research items

1. **Какие именно leaf-skill'ы безопасно удешевить** - **Resolved.** 6 механических переведены, 2 reasoning-sensitive (`skill-fix`, `caveman-review`) исключены - см. §5.
2. **Нужен ли owner sign-off перед изменением модели** - **Resolved.** Владелец явно потребовал выполнить полностью сейчас (2026-06-30); применено автономно.

---

## 10. Связи с другими спеками

- **S0816** - родитель; playbook `docs/AGENT_COST_PLAYBOOK.md` (## Skill cost tiers) держит policy и caution.

---

## Last Audit

**Date:** 2026-06-30 | **Verdict:** Verified | **Mode:** inline (config-only, grep-verifiable)

- 6 целевых команд несут `^model: sonnet` в frontmatter (`caveman-commit`, `caveman`, `quick`, `ns`, `git`, `doc-update`) - PASS.
- frontmatter well-formed (`---` / `model: sonnet` / `---` на строках 1-3) - PASS.
- `skill-fix` и `caveman-review` НЕ содержат `model:` (остались на сильной модели) - PASS.
- Оркестраторы (`spec-dev`/`spec-all`/`spec-tech`) и release-маршруты не тронуты - PASS.
- Итого команд на sonnet: 13 (7 исходных + 6 новых).

Config-only - нет Kotlin/build/device-test/Timber-тегов. Playbook `docs/AGENT_COST_PLAYBOOK.md` обновлён списком применённых skill'ов.
