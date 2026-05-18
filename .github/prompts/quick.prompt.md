---
agent: "agent"
description: "Use when: making a very minor change - design tweak, typo, color/padding/string fix, single-localization rename, small XML/dimens/colors edit; no spec, no docs, no build check, only dev/CHANGELOG.md; or asked to run /quick. Triggers on: quick, tiny fix, опечатка, поправь, мелкая правка, one-liner."
---

# Quick Fix

> **GLOBAL EXECUTION DIRECTIVES (ANTI-BUREAUCRACY):**
> 1. **STRICTLY TECHNICAL LANGUAGE:** No fluff, no conversational filler, dry technical prose only.
> 2. **AUTONOMY OVER BUREAUCRACY:** Do not ask for confirmation on a trivial change. Execute, log, report.
> 3. **TERSE REPORTING:** After execution, output a single dry sentence stating what was changed.

Ускоренный путь для **очень незначительных** правок (коррекция дизайна, опечатка, цвет/отступ/строка, переименование одной локализации, мелкая правка XML/dimens/colors). Спецификация не создаётся, документация и билд не проверяются.

## Usage

```
/quick <короткое описание задачи>
```

Examples:
- `/quick поправь padding у кнопки Save в settings_fragment.xml на 16dp`
- `/quick цвет accent в colors.xml - на #FF6B00`
- `/quick опечатка в strings.xml: "Загруска" → "Загрузка"`

---

## When NOT to use

`/quick` запрещён для:

- Любого изменения бизнес-логики, навигации, потоков данных.
- Новых классов/функций/UseCase/Repository/ViewModel.
- Правок Room-схемы, миграций, Hilt-модулей, build.gradle, манифеста.
- Многофайловых рефакторингов (>3 файлов или >50 LOC суммарно).
- Любого изменения UI-поведения, видимости, ориентации, состояний, оверфлоу - это `/ui-clarify` + `/spec`.
- Задач, которые пользователь формулирует через "хочу фичу", "добавь возможность", "сделай чтобы можно было".

При срабатывании любого из этих признаков - **отказать и предложить `/spec` или `/spec-all`**, не выполнять.

---

## Process

**Step 1 - Sanity gate.**
Прочитать `$ARGUMENTS`. Если задача выходит за рамки определения «очень незначительная» (см. список выше), вернуть одну строку:
`«/quick не подходит - это <причина>. Используй /spec или /spec-all.»`
И остановиться.

**Step 2 - Локализовать файл.**
Для поиска класса/файла - `dev/CATALOG/scripts/query.ps1` (см. CLAUDE.md §Research Order). Для XML/ресурсов - прямой путь либо `Glob`/`Grep`. Никаких глубоких аудитов.

**Step 3 - Прочитать целевой файл, внести правку через `Edit`.**
Соблюдать стиль автора: `..` вместо `...`, `ё`/`Ё` в русских строках. Английский в коде/комментариях, русский в UI-строках.

> **⚠ COMMUNICATION_POLICY:** Если правка затрагивает пользовательский текст (toast, dialog, empty state, error, snackbar, CTA) - сверить формулировку с `docs/COMMUNICATION_POLICY.md` §2 (формулы по типам) и §6 (чек-лист тона). Если строка не проходит чек-лист → переформулировать до сохранения.

> **⚠ LAYOUT_ORIENTATION:** Если правится `res/layout/*.xml` - **сразу проверить** `res/layout-land/<тот же файл>.xml`. Файл существует? → внести эквивалентную правку туда же в рамках того же `/quick`. Файл отсутствует, а экран поддерживает обе ориентации? → это уже не «очень незначительная» правка: отказать и предложить `/spec`.

**Step 4 - Залогировать в `dev/CHANGELOG.md`.**
Обязательно - одной командой:
```powershell
.\scripts\add_to_dev_log.ps1 "<relative/path/to/file>" "<target>" "<short EN description>"
```
`<target>` - имя класса/ресурса/строки (`colors.xml`, `settings_fragment.xml`, `string/login_title`).

**Step 5 - НЕ запускать:**
- `docs/FEATURES*.md` обновление (skip - это `/doc-update`).
- Catalog sync (`scan.ps1` / `render.ps1`) - даже для `.kt` (skip; пользователь синхронизирует отдельно при необходимости).
- Билд (`/build`).
- `/ui-clarify` gate - игнорируется в `/quick` по дизайну скилла. Если задача требует уточнений UI - это значит она не «очень незначительная» → вернуться на Step 1 и отказать.

**Step 6 - Отчёт.**
Одно предложение: что изменено, в каком файле, плюс факт логирования. Без сводок, без планов на будущее, без markdown-секций.

Пример: `Padding кнопки Save в settings_fragment.xml поднят с 8dp до 16dp; залогировано в dev/CHANGELOG.md.`

---

## Что пропускается осознанно

| Шаг                  | Статус в `/quick` |
|----------------------|-------------------|
| Спецификация         | skip              |
| `/ui-clarify` gate   | skip              |
| Catalog sync         | skip              |
| `docs/FEATURES*`     | skip              |
| Build verification   | skip              |
| `dev/CHANGELOG.md`   | **обязательно**   |
| Author style (`..`, `ё`) | **обязательно** |

Всё, что в skip - ответственность пользователя при необходимости.

---

## Spec Catalog hooks

- **Trigger:** `/quick` касается каталога только если правка задевает файл, путь которого начинается с `PLAN/S\d{4}_` (любой spec-артефакт). Иначе - пропустить.
- **Действие:** одно `pwsh -File scripts/spec_catalog/update.ps1 -Id <Sxxxx>` без других флагов - только обновить `updated`. Статус не трогать.
- **ID:** взять из имени файла (префикс `Sxxxx_`).
- **Запрещено:** менять статус из `/quick`, писать в `PLAN/spec-catalog.jsonl` напрямую.
