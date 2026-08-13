# Спецификация (compact): S1357 - зеркалирование live-лога требует явного включения

**Ticket:** S1357
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-02
**Tier:** 3 - Moderate (ad-hoc)

<!-- auto-approved by /spec-all - 2026-08-04 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-02

**Текст:**

файлы afstmediasorter_debug_live.log заполонили мой телефон. их созжание даже в дебаг версии должно быть опционафльным и отклюыено по умолчанию

---

## 1. Проблема

В debug-сборке открытие локального файла в просмотрщике изображений включает зеркалирование лога сессии в ту же папку под именем `fastmediasorter_debug_live.log`. Триггер срабатывает молча и на каждой новой папке, поэтому за сессию просмотра файл появляется в каждой папке, куда заходил владелец. Выключить это нечем: единственное условие - `BuildConfig.DEBUG`.

Задумывалось как удобство (забрать лог, не копаясь в песочнице приложения), а на практике засоряет хранилище устройства и подмешивает посторонний файл в папки с медиа, которые пользователь потом сортирует.

---

## 2. Цели

1. По умолчанию debug-сборка не создаёт ни одного `fastmediasorter_debug_live.log`.
2. Зеркалирование включается явным переключателем и остаётся выключенным, пока его не включили.
3. Выключение переключателя немедленно прекращает запись в текущую папку-цель, не требуя перезапуска.

**Non-goals:**

- Удаление уже созданных `fastmediasorter_debug_live.log` с устройства. Владелец чистит их сам; автоудаление чужих файлов из пользовательских папок - отдельный риск, не решаемый попутно.
- Изменение основного файлового лога в песочнице приложения. Он не засоряет пользовательские папки и остаётся как есть.
- Появление переключателя в релизных сборках. Механизм debug-only и таким остаётся.

---

## 3. Ограничения

- **Flavor:** все - механизм в `src/main`, гейт по типу сборки, а не по flavor.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Локализация:** EN/RU/UK обязательно.
- **Ориентация:** строка добавляется и в `res/layout/`, и в `res/layout-land/` (правило 11).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1310 (`debug-log-mirror-unbounded`, тот же механизм зеркала).
- **UI decision:** размещение выведено из архитектуры, вопрос владельцу не требуется - в `fragment_settings_general` уже есть debug-only секция `headerDebugSettings` / `containerDebugSettings`, скрытая при `!BuildConfig.DEBUG`. Переключатель ставится туда и наследует её гейт видимости.
- **Sensitive scope:** нет - ни разрешений, ни сети, ни схемы Room, ни новых Hilt-скоупов.

---

## 4. Подход

Значение хранится в собственном файле `SharedPreferences` рядом с логированием, а не в `AppSettings`. Причина: `LoggingHelper` - синглтон-`object` без внедрения зависимостей, инициализируемый на старте процесса раньше, чем поднимается асинхронное хранилище настроек, поэтому читать оттуда он не может. Это тот же приём, что у `ColorThemePrefs` и `PlayerLayoutModePrefs` - синхронное зеркало для значения, нужного вне графа DI.

Точка отсечения - `LoggingHelper.updateDebugMirrorTargetFromPath()`. Сейчас там единственная проверка `BuildConfig.DEBUG`; к ней добавляется проверка флага. Отсечение именно здесь, а не в вызывающем `ImageLoadingManager`, потому что это единственный вход в механизм, и любой будущий вызывающий получит гейт автоматически.

При выключении переключателя текущая папка-цель сбрасывается, иначе уже выбранная цель продолжила бы получать записи до конца сессии.

---

## 5. Критерии готовности

1. Свежая debug-сборка после просмотра нескольких локальных папок не оставляет в них ни одного `fastmediasorter_debug_live.log`.
2. Включение переключателя и повторный просмотр папки создаёт файл в ней.
3. Выключение переключателя прекращает дозапись в уже выбранную папку без перезапуска приложения.
4. В релизной сборке переключателя не видно.

---

## Phase 01 - Gate the debug live-log mirror behind an explicit opt-in

**Status:** ✅ Done
**Steps done:** 5 / 5

### Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/logging/DebugLogMirrorPrefs.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/logging/LoggingHelper.kt` | Modified | ≤ 700 |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | +1 row |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | +1 row |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | ≤ 700 |
| `app_v2/src/main/res/values/strings.xml` + `values-ru/` + `values-uk/` | Modified | +2 keys |

### Steps

#### Step 01.1 - Add the synchronous opt-in mirror

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/logging/DebugLogMirrorPrefs.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `object DebugLogMirrorPrefs` with its own SharedPreferences file, a boolean key, and a default of `false`. Expose `isEnabled(context)` and `setEnabled(context, enabled)`. Give it a KDoc naming `ColorThemePrefs` as the precedent and stating why the value cannot live in `AppSettings`.

**Why:**

`LoggingHelper` is an object initialised at process start with no Hilt graph available, so the flag must be readable synchronously outside DI - the same constraint that produced `ColorThemePrefs`.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `object DebugLogMirrorPrefs` matches once.

**Status:** `[x]` done

---

#### Step 01.2 - Gate the mirror entry point on the flag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/logging/LoggingHelper.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Retain the application context in `LoggingHelper.initialize()`. In `updateDebugMirrorTargetFromPath()` return early unless `DebugLogMirrorPrefs.isEnabled()` is true, keeping the existing `BuildConfig.DEBUG` guard ahead of it. Add a way to clear the current mirror target so turning the toggle off stops writes to the folder already selected.

**Why:**

This method is the only entry into the mirroring mechanism, so gating it here makes every present and future caller inherit the opt-in instead of each one re-checking.

**Verification:**

- `Grep` - `DebugLogMirrorPrefs` referenced in `LoggingHelper.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

#### Step 01.3 - Add the toggle row to both orientations

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a `com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow` inside `containerDebugSettings` in both layouts, modelled on `rowCompactElements`. Give it a stable id, a title and a subtitle string. Do not add any visibility attribute - the container is already gated on `BuildConfig.DEBUG`.

**Why:**

The debug-only container answers the placement question, and Rule 11 requires the landscape counterpart to change with the portrait one or the row silently disappears in landscape.

**Verification:**

- `Grep` - the new row id appears in both layout files.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

---

#### Step 01.4 - Add the strings and wire the listener

**Files:** `strings.xml` (EN/RU/UK), `GeneralSettingsViewSetupHelper.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add the title and subtitle keys across EN/RU/UK with `scripts/utils/set-android-string.ps1 -Action add`. Wire the row where the other debug-section views are set up: reflect the stored value on entry and write it through `DebugLogMirrorPrefs.setEnabled` on change, clearing the mirror target when it goes off. No restart prompt - the flag takes effect on the next mirror call.

**Why:**

Goal 3 requires turning the toggle off to stop writes without an app restart, which only holds if the change is applied immediately rather than read once at startup.

**Verification:**

- `scripts/check_strings_localized.ps1` for the new prefix exits 0.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

#### Step 01.5 - Regenerate the settings documentation

**Files:** `docs/settings/settings-manifest.json`, `docs/settings/settings-annotations.json`, `docs/SETTINGS_REFERENCE*.md`
**Depends on:** Step 01.4

**Prompt for developer:**

> Add an annotation for the new row and regenerate the settings manifest and reference. Confirm with `scripts/quality/assert-settings-doc-sync.ps1`.

**Why:**

Rule 22 makes the manifest and reference part of any change to a setting's presence, and `fragment_settings_general` is inside the documented scope - the existing debug log buttons in the same container are already annotated.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.

**Status:** `[x]` done

---

### Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `.\a.ps1 fc` exit 0 (`BUILD SUCCESSFUL in 34s`, code + resources, probe tags included).
- [x] `post-change.ps1` closes green - `post-change: PASS (Mixed)`.
- [ ] Device check on a debug build: criteria 1-3 of §5 observed.
