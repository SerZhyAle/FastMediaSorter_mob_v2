# Спецификация (compact bugfix): S1794 - Правило суффиксов классов не может сработать

**Ticket:** S1794
**Status:** Archived
**Priority:** 55
**Date:** 2026-08-18
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-18

**Текст:**

Найдено при проверке реализации S1786 (шаг 03.1). Правило `class-architecture-naming` заведено в `scripts/quality/lib/source-matchers.ps1`, бейслайн `class-architecture-naming-baseline.txt` = 0, гейт печатает свою строку - но нарушение Rule 6 не ловит.

Эксперимент: создан файл `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/S1786NamingProbeHelper.kt` с `internal class S1786NamingProbeHelper`, то есть класс в каталоге use-case без суффикса `UseCase`.

```text
class-architecture-naming in src/main: baseline 0 | actual 0 | delta 0
assert-source-gates: 20 rule(s) over ONE walk of 4182 file(s), 2992 read, 36857 ms.
assert-source-gates: PASS (all rules at or below baseline).
gate exit: 0
```

Файл-проба удалён после прогона.

Причина - логика перевёрнута. Счётчик считает только имена, которые **уже содержат** подстроку `usecase` / `repository`, и проверяет у них форму окончания:

```powershell
if ($name -match '(?i)usecase' -and $name -notmatch 'UseCase(s|Factory)?$' -and $name -notmatch 'UseCase[A-Z]') { $count++ }
```

То есть поймает `UsecaseHelper`, но не `DataFetcher` - а Rule 6 требует обратного: любой класс или интерфейс в `domain/usecase/` обязан **заканчиваться** на `UseCase`, в `data/repository/` - на `Repository` / `RepositoryImpl`.

Дополнительно: `PathFilter` ограничен `app_v2/src/main/java/...`, поэтому flavor-сорсеты (`src/launcherEnabled`, `src/vr`, ..) и модуль `wear/` вне охвата.

---

## 1. Проблема / симптом

<см. §0 - эвиденс захвачен>

---

## 2. Корневая причина

Matcher теперь проверяет каждый class/interface в `domain/usecase` и `data/repository` на требуемый суффикс, а roots включают все source sets `app_v2` и модуль `wear`. Бейcлайн фиксирует 377 существующих исключений. Изолированная Kotlin-проба без суффикса подняла actual с 377 до 378 и строгий gate завершился с exit 1; после удаления пробы strict gate вернулся к 377 и exit 0.

---

## 3. Исправление

Исправление уже присутствует в matcher и baseline общего change set. Этот тикет подтвердил его restore-after probe без сохранения временного Kotlin-файла в дереве.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1786.

---

## 4. Проверка

Временный файл с неверным именем в `domain/usecase/` дал `delta 1` и `pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Gate` exit 1; после удаления пробы тот же command завершился exit 0.

## Last Audit

**Date:** 2026-08-18
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- No manual or device verification is required for this static quality gate.
