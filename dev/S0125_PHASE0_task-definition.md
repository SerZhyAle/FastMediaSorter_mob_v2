# S0125 - PHASE 0 Task Definition

**Date:** 2026-05-18  
**Branch:** DEBUG-v004  
**Strategic spec:** `PLAN/S0125_settings-activity-revision.md`  
**Tactical plan:** `PLAN/S0125_settings-activity-revision/INDEX.md`

---

## 1. Задача

Выполнить S0125 как безопасную параллельную миграцию экрана настроек без удаления текущей системы настроек.

Целевой результат этой итерации:

- существующий legacy Settings остаётся рабочим;
- revised Settings создаётся как параллельный UI-host;
- Browse получает dual-run surface с двумя разными entry points;
- пользователь не теряет ни одной существующей настройки, её описания, helper-affordance, search-target semantics, dependent inline control или встроенного form-behavior;
- новая и старая surfaces обязаны поддерживать touch, mouse, keyboard и D-pad / TV remote;
- legacy path запрещено удалять до полного parity-audit и отдельного human sign-off.

---

## 2. Что уже согласовано с владельцем

Следующие решения считаются принятыми и не требуют дополнительных вопросов перед Research / Implementation:

1. Legacy Settings не удаляется и не заменяется прямым cutover.
2. Revised Settings строится параллельно, а не поверх существующего host.
3. В Browse допускается временное сосуществование двух settings entry points.
4. Primary keyboard / remote path в Browse можно перебиндить на revised path только после отдельной зелёной validation-matrix.
5. Legacy path обязан сохранять явный non-touch access route даже после перебинда primary shortcut.
6. Потеря даже одного summary, helper-text, dependent inline control, embedded action или confirm-flow считается regression.
7. Речь идёт о параллельной миграции UI, а не о создании второй независимой модели хранения настроек.

---

## 3. Scope текущей работы

### In scope

- зафиксировать task definition и все owner-approved constraints;
- собрать AS-IS research по текущему settings host, search, input model и Browse entry paths;
- подготовить базу для tactical Phase 01 inventory work;
- подтвердить стартовые owning files и архитектурные швы.

### Out of scope на PHASE 0

- любые `.kt` / `.xml` изменения runtime-поведения;
- запуск revised host;
- перенос реальных settings rows;
- изменение storage semantics;
- удаление legacy paths.

---

## 4. UI / UX ambiguity checklist

Критичные решения на текущий момент считаются закрытыми:

### Placement

- Dual-run surface разрешён только в Browse.
- Global launchers вне Browse остаются на legacy `SettingsActivity` в рамках S0125.

### Visibility

- Legacy Browse settings path остаётся видимым.
- Revised Browse settings path добавляется рядом как отдельный explicit entry point.
- Две одинаково подписанные кнопки настроек недопустимы.

### Interaction

- Для обеих settings surfaces обязателен parity по touch, mouse, keyboard и D-pad / TV remote.
- Search, focus traversal, open current, back navigation и help route не могут ухудшиться.

### Fallback / safety

- Если revised path не проходит validation, primary shortcut остаётся на legacy path.
- Legacy removal в S0125 запрещён.

### Data / persistence

- Revised host не получает собственную независимую persistence model.
- Оба UI-path работают с одной и той же underlying settings semantics.

---

## 5. Acceptance for workflow PHASE 0

PHASE 0 считается выполненным, если:

- task boundary описана письменно;
- owner decisions выписаны явно;
- UI ambiguity checklist закрыт на уровне текущей задачи;
- следующая фаза Research может идти без дополнительных уточнений.

**Result:** gate passed. Можно переходить к PHASE 1 Research.

---

## 6. Следующий шаг

PHASE 1 должен собрать:

- точные current entry points в `MainActivity`, `WelcomeActivity`, `BrowseManagerInitializer`, `BrowseEventHandler`;
- текущий settings host, keyboard manager и search contract;
- current management behaviors в General / Operations;
- текущие layout/source-set surfaces для portrait / landscape;
- список рисков для tactical Phase 01 inventory-first execution.
