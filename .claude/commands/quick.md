# Quick Fix

> **GLOBAL DIRECTIVES (ANTI-BUREAUCRACY):**
> 1. Strictly technical language - dry prose, no filler.
> 2. Autonomy over bureaucracy - no confirmation on trivial changes. Execute, log, report.
> 3. Terse reporting - one dry sentence stating what changed.

Ускоренный путь для **очень незначительных** правок (дизайн, опечатка, цвет/отступ/строка, переименование одной локализации, мелкая правка XML/dimens/colors). Спека не создаётся, доки и билд не проверяются.


## Usage

```
/quick <короткое описание задачи> [--verify-device]
```

Examples:
- `/quick поправь padding у кнопки Save в settings_fragment.xml на 16dp`
- `/quick цвет accent в colors.xml - на #FF6B00`
- `/quick опечатка в strings.xml: "Загруска" → "Загрузка"`
- `/quick padding у Save в settings_fragment.xml на 16dp --verify-device` (минимальный smoke на устройстве)

`--verify-device` off by default - `/quick` остаётся zero-bureaucracy. Включай только когда визуальная правка реально может сломать compose/layout (тронул constraint, не уверен в результате).


---

## When NOT to use

`/quick` запрещён для:

- Любого изменения бизнес-логики, навигации, потоков данных. Узкий фикс поведения → `/skill-fix`; шире → `/spec`.
- Новых классов/функций/UseCase/Repository/ViewModel.
- Правок Room-схемы, миграций, Hilt-модулей, build.gradle, манифеста.
- Многофайловых рефакторингов (>3 файлов или >50 LOC суммарно).
- Любого изменения UI-поведения, видимости, ориентации, состояний, оверфлоу. Понятный багфикс → `/skill-fix`; нужны UI-решения → `/ui-clarify` + `/spec`.
- Формулировок «хочу фичу», «добавь возможность», «сделай чтобы можно было».
- Любой правки в `src/main/java/**`, добавляющей `BuildConfig.IS_NO_LEGAL_FLAVOR`, `BuildConfig.SUPPORT_VR_PLAYER`, `BuildConfig.VR_UI_COMPOSITION_LAYER_ENABLED` или другой `BuildConfig.SUPPORT_*` / `ENABLE_*` / `IS_*` flavor-гейт - нарушает CLAUDE.md Rule 15 и `dev/FLAVOR_DEVELOPMENT_RULES.md`. Это всегда `/spec` (interface в main + impl в `src/<flavor>/java/` + flavor-specific Hilt module).
- Любого нового файла в `src/main/java/com/sza/fastmediasorter/vr/**` или с flavor-семантикой (`*Vr*`, `*NoLegal*` в имени класса) - должен лежать в `src/<flavor>/java/`, не в main.

При срабатывании любого признака - **отказать и предложить `/skill-fix` (узкий багфикс) либо `/spec` / `/spec-all` (шире)**, не выполнять.

---

## Process

**Step 1 - Sanity gate.**
Read `$ARGUMENTS`. Задача выходит за рамки «очень незначительная» (см. выше) - вернуть одну строку, остановиться:
`«/quick не подходит - это <причина>. Если это узкий багфикс, используй /skill-fix; иначе /spec или /spec-all.»`

**Step 2 - Локализовать файл.**
Класс/файл → `dev/CATALOG/scripts/query.ps1` (CLAUDE.md §Research Order). XML/ресурсы → прямой путь либо `Glob`/`Grep`. Никаких глубоких аудитов.

**Step 3 - Read целевой файл, внести правку через `Edit`.**
Author style: `..` не `...`, `ё`/`Ё` в русских строках. Английский в коде/комментариях, русский в UI-строках.

> **⚠ COMMUNICATION_POLICY:** правка пользовательского текста (toast, dialog, empty state, error, snackbar, CTA) - сверить с `docs/COMMUNICATION_POLICY.md` §2 (формулы) и §6 (чек-лист тона). Не проходит → переформулировать.

> **⚠ LAYOUT_ORIENTATION:** правка `res/layout/*.xml` - **сразу проверить** `res/layout-land/<тот же файл>.xml`. Есть → эквивалентная правка туда же в рамках того же `/quick`. Нет, а экран двуориентационный → это не «очень незначительная» правка: отказать, предложить `/spec`.

> **⚠ STRINGS:** правки `<string>` через `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action set -Locale en|ru|uk -Key <key> -Value <text>` (байт-сохраняюще, `-ExpectedOldValue` для защиты), не ручным редактированием `strings.xml`. Ручная правка - только `plurals`, `string-array`, комментарии, перегруппировка.

> **⚠ NEUROSLOP (CLAUDE.md Rule 20):** даже мелкая правка не порождает AI-слоп - не добавлять тривиальные комментарии-пересказы, пустые/широкие глотающие `catch`, захардкоженные `="#hex"` в `res/layout*` (только `?attr/`/`@color/`), сырые `lifecycleScope.launch { flow.collect { } }` на view-bound Flow (только `collectOnLifecycle`). При `ChangeType Kotlin|Xml|Mixed` гейт `neuroslop-gate` в `post-change.ps1` отклонит регресс.

**Step 4 - Закрыть правку через `scripts/post-change.ps1`** (обязательно, одной командой):
```powershell
pwsh -NoProfile -File scripts/post-change.ps1 -File "<relative/path/to/file>" -Target "<target>" -Description "<short EN description>" -ChangeType <Doc|Script|Config|Kotlin|Xml|Mixed> [-Module <app_v2|wear>] [-KeyPrefix "<key_prefix>"]
```
`<target>` - имя класса/ресурса/строки (`colors.xml`, `settings_fragment.xml`, `string/login_title`).

`ChangeType` по фактическому типу:
- `Doc` - `.md`, comments-only, `PLAN/`, prompt/rule text.
- `Xml` - `strings.xml`, layout/resource-only; при добавлении/удалении string-keys передать `-KeyPrefix`.
- `Kotlin` - только если мини-правка реально меняет исполняемый Kotlin/Java и нужен catalog sync.
- `Mixed` - только если одна правка тронула код и строки.
- `Script` / `Config` - `.ps1` / `.kts` / `.json` / build-like без смешанного набора.

> **Performance hint.** Правка `.kt` + dev log + каталог одним вызовом: `scripts/post-change.ps1 ... -ChangeType Kotlin` (или `Mixed` если заодно `strings.xml` с `-KeyPrefix`). Для `/quick` обычно избыточно, но при пачке `.kt`-правок удобнее серии вызовов.

**Step 4a - Feature inventory (условно).**
Если правка реально видна пользователю как изменение поведения существующей фичи (UI-строка, цвет акцента, отступ видимого элемента, ориентация виджета) - обнови или добавь запись в `docs/ALL_FEATURES.jsonl` (EN-only инвентарь, заменил `dev/FUNCTIONALITY.log`):
```powershell
.\scripts\all_features\add.ps1 -Id "<area>.<feature>" -Area "<Area>" -Name "<short EN>" -Description "<short EN summary>" -Flavors "standard"
```
`docs/FEATURES*` (публичная витрина) здесь не трогаем - её наполняет только `/skill-release` из диффа инвентаря.

Skip (тихо), если:
- Правка чисто косметическая и невидима (опечатка в комментарии, форматирование, переименование приватной переменной).
- Строки/ресурсы не доходят до пользователя (debug overlay, лог-сообщения, имена в `tools:`).
- Правка в `PLAN/`, `dev/`, `docs/`, `scripts/`, `temp/`.

Сомневаешься - лог.

**Step 4b - On-device проверка (только при `--verify-device`).** Сразу после `post-change.ps1`:
1. Pre-flight: `pwsh -NoProfile -File scripts/devtest/device-ready.ps1 -Package com.sza.fastmediasorter.debug -Json`. Exit ≠ 0 → залогировать причину в чат, пропустить шаг (не блокировать `/quick`).
2. `/verify` без аргументов (default smoke: launch + screenshot home + crash scan). Артефакты в `temp/verify_*` - `/quick` их не трогает.
3. В отчёт хвост: `verify: PASS/FAIL, errors N`.

Этот шаг **не**:
- меняет статус, журнал, dev log (уже записано через `post-change.ps1`);
- запускает full build - `/verify` сам решает рестарт APK (XML обычно не нужно, `.kt` потребовал бы `--build`, но `/quick` не для `.kt` такого масштаба).

**Step 5 - НЕ запускать:**
- `docs/FEATURES*.md` обновление (это `/doc-update`).
- Отдельные `add_to_dev_log.ps1`, `catalog_sync.ps1`, `check_strings_localized.ps1` - применимые шаги уже выбирает `post-change.ps1`.
- Билд (`/build`).
- `/ui-clarify` gate - игнорируется в `/quick` by design. Нужны UI-уточнения → задача не «очень незначительная» → Step 1, отказать.

**Step 6 - Отчёт.** Одно предложение: что изменено, в каком файле, факт логирования. Без сводок, планов, markdown-секций.
Пример: `Padding кнопки Save в settings_fragment.xml поднят с 8dp до 16dp; правка закрыта через post-change.ps1.`

---

## Что пропускается осознанно

| Шаг                  | Статус в `/quick` |
|----------------------|-------------------|
| Спецификация         | skip              |
| `/ui-clarify` gate   | skip              |
| `docs/FEATURES*`     | skip              |
| Build verification   | skip              |
| `post-change.ps1`    | **обязательно**   |
| `docs/ALL_FEATURES.jsonl` | условно (только если видно пользователю как изменение поведения) |
| `/verify` on-device smoke | опционально, только по `--verify-device` |
| Author style (`..`, `ё`) | **обязательно** |

Всё в skip - ответственность пользователя.

---

## Spec Catalog hooks

- **Trigger:** только если правка задевает файл с путём `PLAN/S\d{4}_` (любой spec-артефакт). Иначе пропустить.
- **Действие:** одно `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id <Sxxxx>` без других флагов - обновить `updated`. Статус не трогать.
- **ID:** из префикса `Sxxxx_` имени файла.
- **Запрещено:** менять статус из `/quick`; писать в `PLAN/spec-catalog.jsonl` напрямую.
