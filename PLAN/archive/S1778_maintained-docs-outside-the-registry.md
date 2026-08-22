# Спецификация (compact bugfix): S1778 - Поддерживаемые документы вне реестра, включая обязательный к чтению

**Ticket:** S1778
**Status:** Archived
**Priority:** 55
**Date:** 2026-08-17
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-17 (авто-захват по CLAUDE.md 3.1 при работе над S1720)

**Симптом:**

S1720 закрыл отсутствие набора Maestro в `docs/DOCUMENT_REGISTRY.jsonl`. Тем же прогоном по дереву видно,
что он не одинок, и один из пропусков хуже остальных.

`docs/COMMUNICATION_POLICY.md` (и его `_RU` / `_UK`) в реестре отсутствует. При этом CLAUDE.md §11 прямо
предписывает: «Read before modifying user-visible strings». То есть документ, объявленный обязательным к
чтению перед правкой строк, недостижим через обязательный же цикл обращения к реестру: запрос по областям
`strings` или `ui` на него не выводит, и правка самого документа проходит мимо гейта устаревания.

Рядом, той же природы, но без такого статуса:

- `docs/FAQ.md`, `docs/FAQ_RU.md`, `docs/FAQ_UK.md`
- `docs/DOWNLOADS_EN.md`, `_RU`, `_UK`
- `delivery/INVENTORY.md`
- `store_assets/PLAY_CONSOLE_CHECKLIST.md`, `store_assets/RELEASE_QUICK_GUIDE.md`
- `play/listing/README.md`

**Измерено 2026-08-17:** в реестре 30 записей и 108 путей (31 и 117 после S1720). Файлов `.md` вне `PLAN/`,
архивов, `temp/` и сборочных каталогов - на порядок больше, и подавляющее большинство регистрировать не
надо: это README скриптов, сгенерированные каталоги, фикстуры тестов. Реестр избирателен по замыслу -
поэтому задача не «зарегистрировать всё», а «пройти список кандидатов и по каждому решить».

**Что решить:**

- По каждому кандидату: поддерживаемый документ (в реестр) или разовая заметка (оставить как есть).
- Нужен ли гейт, который ловит новый *поддерживаемый* документ вне реестра - и как он отличит его от
  разовой заметки, не превращаясь в требование регистрировать каждый `.md`.
- Отдельно и первым: `COMMUNICATION_POLICY` - его отсутствие противоречит прямому предписанию CLAUDE.md,
  и это не вопрос вкуса.

**Захвачено во время:** S1720 (регистрация набора Maestro).

---

## 1. Проблема / симптом

Обязательная `COMMUNICATION_POLICY`, FAQ и Downloads уже покрыты записями `ui-communication`, `user-guides` и `legal-downloads`. Неохваченными оставались поддерживаемый инвентарь on-demand delivery и operator-документы публикации.

---

## 2. Корневая причина

Реестр намеренно не является списком всех Markdown-файлов. Пропуски возникли из-за отсутствия явной классификации документа при создании, а не из-за отсутствующей wildcard-поддержки: существующие широкие paths уже покрывают FAQ и Downloads.

---

## 3. Исправление

Добавлены записи `delivery-inventory` и `play-release-operator-runbooks`. `RELEASE_QUICK_GUIDE.md` сохранён как историческая справка, но получил явное предупреждение не использовать его команды вместо текущего standard release gate.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1720 (тикет, при котором находка всплыла и который закрыл её первую часть).

---

## 4. Проверка

- `scripts/document_registry/validate.ps1` exits 0.
- `scripts/document_registry/generate.ps1 -Check` exits 0.
- Queries by release/documentation return the new records.

## Last Audit

**Date:** 2026-08-18
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- No manual or device verification is required for documentation registration.
