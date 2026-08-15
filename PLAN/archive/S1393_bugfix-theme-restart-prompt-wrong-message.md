# Спецификация (compact bugfix): S1393 - Диалог перезапуска после смены темы говорит об импорте настроек

**Ticket:** S1393
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-04
**Tier:** 3 - Moderate (ad-hoc)

<!-- auto-approved by /spec-all - 2026-08-04 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-04

**Захвачено во время:** S1390 (device-проверка выпадающих списков настроек)

**Текст:**

GeneralSettingsColorThemeHelper shows the restart prompt with the wrong message: changing the color theme in Settings -> General pops "Restart Required / Settings have been imported. Please restart the app to apply changes." No settings import happened. Observed on emulator-5554 (API 35), standard-debug v2.60.8041.533-DEBUG, 2026-08-04, while verifying S1390. Source: app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsColorThemeHelper.kt:76-77 reuses R.string.restart_required_message, which is written for the settings-import flow in GeneralSettingsImportExportHelper.kt:144-145. Needs its own theme-specific message string in EN/RU/UK; check whether the language and compact-elements restart prompts reuse the same wrong string.

---

## 1. Проблема / симптом

Смена значения в строке «Цветовая тема» (Настройки -> Общие) открывает диалог перезапуска с текстом «Settings have been imported. Please restart the app to apply changes.», хотя никакого импорта настроек не было. Наблюдалось на emulator-5554 (API 35), standard-debug `v2.60.8041.533-DEBUG`, 2026-08-04.

---

## 2. Корневая причина

`GeneralSettingsColorThemeHelper.showRestartDialog()` берёт строки из флоу импорта настроек: `restart_required_title` + `restart_required_message` + кнопки `restart_now` / `restart_later`. Эта пара строк написана под `GeneralSettingsImportExportHelper` и утверждает факт импорта.

Три соседних диалога перезапуска в тех же настройках используют другое, корректное семейство строк:

- Язык - `GeneralSettingsViewSetupHelper.showRestartDialog()`: `restart_app_title` + `restart_app_message` + `restart` / `cancel`.
- Размер кэша - `GeneralSettingsCacheHelper.showCacheSizeRestartDialog()`: `restart_app_title` + `restart_app_cache_message` + `restart`.
- Компактные элементы - `GeneralSettingsViewSetupHelper`: `restart_app_title` + `restart_app_compact_elements_message` + `restart` / `cancel`.

То есть переиспользование ограничено одной цветовой темой: язык и компактные элементы затронуты не были. Отдельной строки под смену темы в семействе `restart_app_*` просто нет.

---

## 3. Исправление

Привести диалог цветовой темы к тому же семейству, что и три соседних:

- Добавить строку `restart_app_color_theme_message` в EN/RU/UK по образцу `restart_app_compact_elements_message`.
- Переключить `GeneralSettingsColorThemeHelper.showRestartDialog()` на `restart_app_title`, новую строку и кнопки `restart` / `cancel`.
- Ветку отказа не трогать: она возвращает спиннер к предыдущему значению и это поведение остаётся.

Полное выравнивание, а не только подмена текста сообщения, потому что KDoc самого класса объявляет требование - «prompts for restart (same pattern as language / compact-elements)».

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1390 (в ходе его проверки найдено)
- **UI decision:** выравнивание по семейству `restart_app_*` выведено из KDoc класса, вопрос владельцу не требуется.
- **Sensitive scope:** нет - ни разрешений, ни сетевых вызовов, ни схемы Room, ни новых Hilt-скоупов.

---

## 4. Проверка

Компиляция `standard debug` плюс проверка паритета локалей строк. Финальная проверка текста диалога - на устройстве.

---

## Phase 01 - Align the color-theme restart prompt with the restart_app family

**Status:** ✅ Done
**Steps done:** 3 / 3

### Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +1 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +1 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +1 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsColorThemeHelper.kt` | Modified | ≤ 100 |

### Steps

#### Step 01.1 - Add the theme-specific restart message in EN/RU/UK

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add key `restart_app_color_theme_message` across EN/RU/UK with one call to `scripts/utils/set-android-string.ps1 -Action add`. Word it after `restart_app_compact_elements_message`: state that applying the new color theme needs an app restart and ask to continue. Take no format argument - the dialog passes none.

**Why:**

The dialog currently asserts a settings import that never happened, and the `restart_app_*` family has no entry for a color-theme change, so the correct message does not exist yet.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "restart_app_color_theme"` exits 0.

**Status:** `[x]` done - exit 0, `all 1 key(s) present in en/ru/uk`.

---

#### Step 01.2 - Point the color-theme dialog at the family strings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsColorThemeHelper.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `showRestartDialog()` replace `R.string.restart_required_title` with `R.string.restart_app_title`, `R.string.restart_required_message` with `R.string.restart_app_color_theme_message`, `R.string.restart_now` with `R.string.restart`, and `R.string.restart_later` with `R.string.cancel`. Leave the two button bodies untouched - the negative branch still restores the spinner to the previous value.

**Why:**

The class KDoc states the prompt follows the same pattern as language and compact-elements, so matching only the message text would leave the title and both button labels diverging from the family it declares itself part of.

**Verification:**

- `Grep` - `restart_required` returns zero hits in `GeneralSettingsColorThemeHelper.kt`.
- `Grep` - `restart_app_color_theme_message` matches once in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done - `restart_required` gone from the file, `.\a.ps1 fk` exit 0 (`BUILD SUCCESSFUL in 37s`).

---

#### Step 01.3 - Confirm the import-flow strings kept their owner

**Files:** none - verification only
**Depends on:** Step 01.2

**Prompt for developer:**

> Grep the whole `app_v2/src` for `restart_required_title`, `restart_required_message`, `restart_now` and `restart_later`. Each must still be referenced by `GeneralSettingsImportExportHelper`. Any key left with zero Kotlin references is now dead and gets removed from all three locales.

**Why:**

Rule 20 (dead-weight hygiene) requires orphaned string keys to be deleted in the same change, and this step is the only thing standing between moving the last non-import consumer off those keys and leaving unused resources behind.

**Verification:**

- `Grep` - each of the four keys has at least one `.kt` reference, or is absent from all three `strings.xml`.

**Status:** `[x]` done - all four keys still referenced by `GeneralSettingsImportExportHelper.kt:144-150`; no orphans, nothing removed.

---

### Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `.\a.ps1 fk` exits 0.
- [x] `scripts/check_strings_localized.ps1` exits 0 for the new key prefix.
- [x] Dev log entry added via `scripts/post-change.ps1`.

---

## Last Audit

**Date:** 2026-08-04
**Verdict:** Verified

### Что изменено

- `app_v2/src/main/res/values/strings.xml`, `values-ru/`, `values-uk/` - добавлен ключ `restart_app_color_theme_message`.
- `GeneralSettingsColorThemeHelper.showRestartDialog()` - `restart_app_title` + новая строка + кнопки `restart` / `cancel` вместо пары из флоу импорта.

### Доказательства

- `.\a.ps1 fk` - exit 0, `BUILD SUCCESSFUL in 19s` (прогон после снятия probe-тега).
- `.\a.ps1 d` - exit 0, APK `FastMediaSorter_standard_debug_v2.60.8041.533-DEBUG.apk`.
- `scripts/check_strings_localized.ps1 -KeyPrefix "restart_app_color_theme"` - exit 0, `all 1 key(s) present in en/ru/uk`.
- Device, emulator-5554, дамп иерархии окна: заголовок `Restart Application`, тело `To apply the new color theme, the application needs to restart. Continue?`, кнопки `Restart` (`android:id/button1`) и `Cancel` (`android:id/button2`). Упоминания импорта нет.
- Device: после `Cancel` строка «Цветовая тема» вернулась к `Auto (follow device)` - ветка отказа цела.
- Probe-тег отработал: `08-04 19:51:35.445 D GeneralSettingsColorThemeHelper: S1393: color theme restart prompt shown (AUTO -> DARK)`.
- Probe-тег снят: `Grep S1393` по `app_v2/**/*.kt` - ноль совпадений.

### Замечания

- Строки `restart_required_title`, `restart_required_message`, `restart_now`, `restart_later` остались за `GeneralSettingsImportExportHelper` - сирот не образовалось, удалять нечего.
- Первая попытка снять лог дала ложный «tag not found»: `adb.ps1 log -Tail 400` попал в окно из 420 строк системного спама одного PID. Тег нашёлся при `-Tail 20000`. Узкое окно `-Tail` на шумном эмуляторе - не доказательство отсутствия.
