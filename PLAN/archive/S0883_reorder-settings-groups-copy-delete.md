# Стратегическая спецификация: S0883 - Поменять порядок групп настроек "Управление"

**Ticket:** S0883
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-02
**Tier:** 1 - Quick Win (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-02
**Tactical spec:** N/A - compact spec (Simple path), фазы реализации инлайн в §12 ниже.

<!-- auto-approved by /spec-all - 2026-07-04 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Настройки - Управление - поменять порядок групп. Поставить "Копирование, перемещение и перезапись" выше чем "Удаление файлов, корзина"

---

## 1. Проблема

На экране настроек "Управление" (вкладка Operations, layout `fragment_settings_destinations`) группа "Удаление файлов, корзина" отображается выше группы "Копирование, перемещение и перезапись" - и в портретной, и в альбомной ориентации. Копирование/перемещение - более часто используемая функциональность, чем деструктивные операции удаления, поэтому владелец хочет видеть её первой. Область - один экран настроек, только визуальный порядок двух уже существующих групп, без изменения их содержимого или поведения.

---

## 2. Цели

1. Группа "Копирование, перемещение и перезапись" отображается выше группы "Удаление файлов, корзина" на экране "Управление".
2. Тот же порядок соблюдён в альбомной ориентации (`layout-land`).
3. Состояние свёрнутости/развёрнутости секций, обработчики переключателей и вся бизнес-логика не меняются.

**Non-goals:**

- Изменение состава полей внутри каждой из двух групп.
- Изменение порядка остальных групп экрана "Управление" (Destinations, Scheduled, Behaviour, Other features, Additional programs, System apps, Screen gestures).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

Сверх исходного запроса пожеланий не поступало - точечная перестановка двух групп.

### 3.2 Жёсткие ограничения

- **Flavor:** все (standard/lite/photos/legacy) - общий layout в `src/main`, `BuildConfig`-специфики нет.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** не критично - статическая перестановка блоков в XML, без влияния на разметку/измерение во время выполнения.
- **Совместимость данных:** миграция не требуется - персистентные ключи свёрнутости секций (`operations__safety`, `operations__file_ops`) не переименовываются и не удаляются.
- **Локализация:** без изменений строк - обе группы используют существующие ключи `settings_category_safety` / `settings_category_copy_move` (EN/RU/UK уже заполнены).
- **Доступность:** линейный порядок обхода (TalkBack, D-pad) определяется порядком детей в `LinearLayout` и автоматически следует новому визуальному порядку; явных `nextFocus*`/`accessibilityTraversal*` ссылок между этими двумя блоками в разметке нет (проверено `grep` по обоим layout-файлам) - разрыва фокуса не возникает.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** Два существующих `MaterialCardView`-блока ("Копирование, перемещение и перезапись" и "Удаление файлов, корзина") меняются местами в вертикальной последовательности экрана "Управление" - Copy/Move выше, Safety/Trash ниже. Ни один `id`, атрибут, строка или обработчик не меняются - только физическая позиция блоков в XML.
- **Accessibility:** Линейный порядок обхода (TalkBack/D-pad) автоматически следует новому порядку `LinearLayout`-детей; явных cross-block `nextFocus*`/`accessibilityTraversal*` ссылок между двумя блоками не найдено - см. §3.2.
- **Flavor scope:** все flavor'ы одинаково - общий `src/main` layout, `BuildConfig`-гейтов нет.
- **Validation level:** статическая - `Grep`-проверка порядка строк в обоих layout-файлах + `standard debug` сборка (`a.ps1 dq`) + гейт `assert-settings-doc-sync.ps1` (доказывает отсутствие дрейфа `docs/settings/*`). Device-тест не требуется - логики выполнения нет, результат детерминирован статикой XML.
- **Owner sign-off:** 2026-07-02 (исходный запрос владельца).
- **Related tickets:** none

---

## 4. Контекст текущей архитектуры

Экран "Управление" - один фрагмент (`ui/settings/fragments/`, layout `fragment_settings_destinations` + `-land` вариант) с последовательностью `MaterialCardView`-секций. Каждая секция зарегистрирована как сворачиваемая группа через общий оркестратор сворачивания (`ui/common/widget/`), который отвечает за expand/collapse-анимацию и персистентность состояния по строковому ключу (`operations__<section>`) - оркестратор адресует секции по ключу, а не по позиции. Видимый вертикальный порядок групп задаётся исключительно порядком объявления `MaterialCardView`-блоков в XML; Kotlin-слой их порядок не читает и не устанавливает. Поэтому изменить порядок можно только перестановкой XML-блоков.

---

## 5. Предлагаемый подход

Переставить местами два existing XML-блока ("Копирование, перемещение и перезапись" и "Удаление файлов, корзина") в обоих вариантах layout экрана "Управление" (portrait + landscape), не трогая их внутреннее содержимое, идентификаторы или Kotlin-слой.

### 5.1 Основные столпы / модули

Единственный столп - декларативная перестановка двух existing секций в XML-дереве экрана "Управление", идентично в portrait и landscape вариантах.

### 5.2 Потоки данных и событий

Без изменений - потоки данных (ViewModel -> Fragment -> View) и обработчики событий не зависят от визуальной позиции секции в дереве разметки.

### 5.3 Точки расширяемости

Не применимо - точечная косметическая правка порядка, не архитектурное изменение.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет. Research подтвердил: обе секции используют `sectionId: destinations` в `docs/settings/settings-manifest.json`; манифест сериализуется отсортированным по `sectionId`+`key` (см. `SettingsManifestSerializer` в `SettingsManifestSerializer.kt`), то есть визуальная позиция в XML не входит в схему манифеста и не влияет на `docs/SETTINGS_REFERENCE*.md` - гейт `assert-settings-doc-sync.ps1` проходит без правок артефактов документации.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Обход фокуса TalkBack/D-pad перепрыгивает неожиданно между несвязанными блоками | Низкая | Небольшая путаница при навигации клавиатурой/TalkBack на двух блоках | Явных `nextFocus*`/`accessibilityTraversal*` ссылок между этими блоками не найдено (`grep`-проверка) - линейный обход и так следует новому порядку автоматически |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES - косметическая перестановка порядка существующих групп, не новая capability.

---

## 9. Архитектурные решения (ADR)

ADR нет - точечная перестановка XML-блоков в рамках устоявшегося паттерна сворачиваемых секций, без изменения архитектуры.

---

## 10. Связи с другими спеками

Связей нет.

---

## 11. Критерии готовности (strategic-level)

1. На экране "Управление" (portrait) группа "Копирование, перемещение и перезапись" визуально расположена выше группы "Удаление файлов, корзина".
2. То же самое соблюдено в landscape-варианте экрана.
3. `standard debug` собирается без ошибок.
4. Гейт `assert-settings-doc-sync.ps1` проходит без правок `docs/settings/*` / `docs/SETTINGS_REFERENCE*.md`.

---

## 12. Реализация (inline phases - Simple path)

### Phase 1 - Reorder the two card sections in the portrait layout

1. In `app_v2/src/main/res/layout/fragment_settings_destinations.xml`, relocate the "Copy, move and overwrite" `MaterialCardView` block (`headerCopyMove` / `containerFileOperations`) to immediately before the "File deletion and trash" `MaterialCardView` block (`headerSafety` / `containerSafety`, including its leading comment). Pure block relocation - no id, attribute, string, or listener change.
   - Verification: `Grep -n "headerCopyMove|headerSafety"` on the file shows `headerCopyMove` at a smaller line number than `headerSafety`.
   - **Status:** `[x]` done - `headerCopyMove` now at line 13, `headerSafety` at line 103 (was 14/86 reversed before).

### Phase 2 - Mirror the reorder in the landscape layout

1. Apply the identical block relocation (including each block's own leading comment) to `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` (CLAUDE.md Rule 11 - landscape counterpart must stay in sync).
   - Verification: same `Grep` line-order check on the `-land` file.
   - **Status:** `[x]` done - `headerCopyMove` now at line 33, `headerSafety` at line 172 (was 33/172 reversed before - comment blocks moved with their cards).

### Phase 3 - Build + gate proof

1. Both layout files remain well-formed XML with an unchanged line count and an unchanged multiset of lines (sorted-line hash compared against pre-edit backups in `temp/S0883/`) - proves a pure reorder, zero content loss/duplication.
   - Verification: PowerShell `[xml]` parse succeeds on both files; `wc -l` unchanged (1154 / 1387); SHA-256 of sorted lines matches the backup for both files.
   - **Status:** `[x]` done.
2. Fast static gates scoped to the two changed files: `assert-neuroslop.ps1 -Gate -ChangedFiles <both>`, `assert-focus-highlight.ps1 -Gate`, `assert-fgs-notifications.ps1 -Gate`.
   - Verification: all three exit 0 with zero new occurrences / zero delta.
   - **Status:** `[x]` done - neuroslop 0/0/0/0/0/0/0/0, focus-highlight delta 0, fgs-notifications PASS.
3. Settings-doc-sync composite gate: `assert-settings-doc-sync.ps1 -Gate` (catalog completeness, manifest freshness via `SettingsManifestExportTest`, annotation coverage, reference re-render byte-diff, HOW_TO path freshness).
   - Verification: exits 0 - confirms §6's prediction that manifest/reference stay byte-identical (schema has no position field).
   - **Status:** `[x]` done - `settings-doc-sync: OK - catalog complete, manifest fresh, annotations covered, reference up to date, HOW_TO recipes in sync.`
4. `standard debug` compiles: `.\a.ps1 dq`.
   - Verification: BUILD SUCCESSFUL.
   - **Status:** `[x]` done - `BUILD SUCCESSFUL in 4s`, APK `FastMediaSorter_standard_debug_v2.60.7040.321-DEBUG.apk`.

---

## Last Audit

**Date:** 2026-07-04
**Result:** Verified

**Static evidence:**

- Portrait `app_v2/src/main/res/layout/fragment_settings_destinations.xml`: `headerCopyMove` line 13 < `headerSafety` line 103 < `headerDestinations` line 175 (was 14/86/175 before - Safety/CopyMove reversed, Destinations untouched).
- Landscape `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml`: `headerCopyMove` line 33 < `headerSafety` line 172 < `headerDestinations` line 298 (was 33/172/298 before with Safety first - Destinations untouched).
- Both files: unchanged line count (1154 / 1387), `[xml]` parse OK, SHA-256 of sorted lines matches pre-edit backup in `temp/S0883/` for both - proves a pure reorder, zero content loss or duplication.
- Gates: `assert-neuroslop.ps1` 0/0/0/0/0/0/0/0 new occurrences, `assert-focus-highlight.ps1` delta 0, `assert-fgs-notifications.ps1` PASS, `assert-settings-doc-sync.ps1` OK (manifest/annotations/reference/HOW_TO all fresh, no edits needed - confirms §6).
- Build: `standard debug` - `BUILD SUCCESSFUL in 4s`.

**Live device evidence (emulator-5554, package installed from the build above):**

- Portrait screenshot of Settings -> Управление: order top-to-bottom is "Копирование, перемещение и перезапись", "Удаление файлов, корзина", "Назначения быстрой сортировки", .. (matches §11 criterion 1).
- Landscape screenshot of the same screen: identical order (matches §11 criterion 2).
- Emulator orientation/app state restored after capture (auto-rotation re-enabled, app stopped).

**§11 Критерии готовности - all 4 met.** No manual/device-only step remains - both static proof and live-device screenshots confirm the reorder; no `BlockNeedUserTest` needed for this ticket.
