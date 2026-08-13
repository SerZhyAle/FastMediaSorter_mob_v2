# Спецификация (compact bugfix): S1004 - Полноэкранный IME скрывает UI диалогов при вводе текста

**Ticket:** S1004
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-12
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-12

**Текст:**

Symptom: Android soft keyboard "fullscreen extract mode" completely hides app UI (including action buttons like Apply Filter / search results) when a user types into a text field on this landscape emulator profile (Pixel_4, 2280x1080 landscape). Reproduced twice during /spec-prerelease sweep 2026-07-12 via Maestro:
1. maestro/features/browse/browse_filter.yaml: opened "Все изображения" virtual resource, tapped btnFilter, tapped etFilterName, typed "IMG", tapped btnApplyFilter. Screenshot at failure (temp evidence: C:\Users\serzh\.maestro\tests\2026-07-12_005212\screenshot-❌-1783810380305-(browse_filter.yaml).png) shows a plain white fullscreen text-edit view with just "IMG" and a "NEXT" IME action button - the actual dialog with btnApplyFilter is completely gone from view, so the tap on btnApplyFilter likely landed on the fullscreen IME extract UI instead of the real button. assertVisible rvMediaFiles then failed (10s timeout).
2. maestro/smoke/settings_search.yaml: tapped searchButton, tapped searchInput, typed "zzzzzzq". Screenshot at failure (C:\Users\serzh\.maestro\tests\2026-07-12_010326\screenshot-❌-1783811058457-(settings_search.yaml).png) shows the identical pattern: fullscreen white edit view with "zzzzzzq" and a "SEARCH" IME action button, hiding searchEmptyText entirely.

Both cases: the tap/assert immediately after inputText failed because the app's real UI (dialog buttons, empty-state text) was not on screen - Android's IME switched into fullscreen "extract mode" (common when available vertical space after the keyboard is too small, which happens on this wide-landscape-short-height emulator profile) and the app apparently does not suppress it (e.g., via android:imeOptions="flagNoExtractUi" on the relevant EditTexts). This is also a real usability risk for actual users on landscape phones/tablets: typing into the filter-by-name field or the settings search field could hide the Apply/result UI behind the keyboard with no obvious way to proceed except manually dismissing the keyboard.

Secondary cascading symptom (not a separate defect, same root cause): the very next Maestro flow after browse_filter.yaml's failure (browse_sort_empty.yaml) failed too, because go_home.yaml's 3 back-presses could not recover from the app being stuck in this state and ended up backing all the way out to the Android home launcher (screenshot: C:\Users\serzh\.maestro\tests\2026-07-12_005352\screenshot-❌-1783810475357-(browse_sort_empty.yaml).png). Root-caused and confirmed as fallout from finding #1, not filed separately.

Scope: affects any TextInputEditText-driven dialog/panel across the app on this device profile - filter dialog (dialog_filter.xml, etFilterName) and Settings search (searchInput) are the two confirmed instances; likely also affects add-resource forms, default-user/password fields, etc. wherever a modal/panel places the primary action button below or dependent on a text field that can trigger the OS's fullscreen IME.

Evidence dir: C:\Users\serzh\.maestro\tests\2026-07-12_005212\, C:\Users\serzh\.maestro\tests\2026-07-12_010326\, C:\Users\serzh\.maestro\tests\2026-07-12_005352\. Full run log: temp/S0484/run_20260712_003339.log. Maestro suite JSON: temp/S0484/maestro_suite_20260712_003339.json.

---

## 1. Проблема / симптом

На широкой ландшафтной раскладке (2280x1080, эмулятор Pixel_4) ввод текста в однострочные поля переключает системную клавиатуру в полноэкранный режим "extract mode" - системный оверлей поверх ВСЕГО окна активности, включая тулбары и кнопки действий. Кнопка "Применить" в диалоге фильтра и результаты поиска в настройках становятся недостижимы, пока клавиатура не будет закрыта вручную. Воспроизведено в двух Maestro-флоу (`browse_filter.yaml`, `settings_search.yaml`) во время `/spec-prerelease` 2026-07-12; полные скриншоты и логи - в §0.

---

## 2. Корневая причина

Ни один `EditText`/`TextInputEditText` в проекте не задаёт `android:imeOptions="flagNoExtractUi"`. Android переключается в fullscreen extract mode автоматически, когда после появления клавиатуры остаётся мало вертикального места - типичная ситуация на широкой ландшафтной раскладке. Флаг `flagNoExtractUi` отключает именно эту особенность ввода (это не влияет на визуальный стиль поля и не требует переопределения темы - проверено: `Theme.FastMediaSorter.App` определена только в `values/themes.xml`, но переопределение `editTextStyle` на уровне темы рискованно, т.к. явный `android:imeOptions` на конкретном поле в XML полностью перекрывает атрибут стиля, а не объединяется с ним - фикс сделан точечно на каждом поле).

---

## 3. Исправление

Добавлен `android:imeOptions="flagNoExtractUi"` (или `|flagNoExtractUi` к уже заданному `actionSearch`/`actionDone`/`actionNext`) на каждое однострочное текстовое поле в модальных диалогах, панелях поиска и формах добавления/редактирования ресурса - 33 layout-файла (включая `layout-land` и `layout-w600dp` пары), ~57 полей. Список файлов и обоснование границ (что сознательно НЕ тронуто) - см. `## Last Audit`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

- `[xml]` well-formed check всех 33 изменённых файлов (PowerShell XML parser) - PASS.
- `.\a.ps1 dq` (assembleStandardDebug) - должен пройти без ошибок (чисто ресурсный XML-атрибут, не влияет на компиляцию Kotlin).
- Ручная on-device проверка (эмулятор, ландшафт): открыть диалог фильтра (`Все изображения` -> Фильтр), ввести текст, нажать "Применить" без ручного закрытия клавиатуры - кнопка должна быть достижима, полноэкранный edit не должен появляться. Аналогично для поиска в настройках.

---

## Last Audit

**Дата:** 2026-07-12

**Изменено:** только `res/layout*` (XML-атрибуты), Kotlin не тронут. 33 файла, ~57 полей.

**Group A** (поле не имело `imeOptions` - добавлен `flagNoExtractUi`):
- `dialog_filter.xml` + `layout-land` (etFilterName)
- `dialog_filter_resource.xml` + `layout-land` (etNameFilter)
- `dialog_folder_selection.xml` + `layout-land` (etManualPath)
- `dialog_rename.xml` + `layout-land` (etFileName)
- `dialog_access_password.xml` (etPassword, numberPassword; land-варианта нет)
- `fragment_settings_general.xml` + `layout-land` (etDefaultUser, etDefaultPassword)
- `view_settings_input_row.xml` (sir_input - переиспользуемый компонент, покрывает несколько settings-строк без точечных правок)
- `dialog_companion_import_confirm.xml` (editImportPassword)
- `dialog_scheduled_operation.xml` + `layout-land` (etStartHour, etStartMinute, etIntervalHours, etIntervalMinutes - числовые, риск ниже, добавлено для консистентности)
- `player_text_viewer_container_content.xml` + `layout-land` (etFindQuery, etReplaceQuery; `etTextContent` сознательно НЕ тронут - полноэкранный текстовый редактор, не паттерн "поле + скрытая кнопка")
- `item_resource_to_add.xml` (etName), `item_rename_file.xml` (etFileName) - инлайн-поля переименования в списке пакетного диалога
- `activity_add_resource.xml` (18 полей: etLocalPinCode, etSmbUsername/Password/ShareName/ResourceName/Comment/PinCode/Domain/Port, etSftpPort/Username/Password/PrivateKey/KeyPassphrase/HostKeyFingerprint/ResourceName/Comment/PinCode). `etSmbServer`/`etSftpHost` (`IpAddressEditText`) и `etSftpPath` (`NetworkPathEditText`) - кастомные виджеты, вне scope точечного XML-фикса.
- `fragment_resource_editor.xml` (13 полей: etName, etPath, etHost, etPort, etUsername, etPassword, etDomain, etShareName, etServerPath, etCloudFolderId, etComment, etAccessPin, etSlideshowInterval) - редактирование существующего ресурса, тот же паттерн, что и добавление.

**Group B** (уже был `imeOptions="actionX"` - дописан `|flagNoExtractUi`):
- `activity_settings.xml` + `layout-land` (searchInput, actionSearch)
- `activity_streams.xml` + `layout-land` (etSearch, actionSearch)
- `dialog_add_stream.xml` (etUrl actionNext, etTitle actionDone)
- `bottom_sheet_epub_search.xml` (etSearchAllQuery, actionSearch)
- `dialog_searchable_option_picker.xml` (editOptionSearch, actionSearch)
- `dialog_searchable_language_picker.xml` (editLanguageSearch, actionSearch)
- `player_search_panel_content.xml` + `layout-land` (etSearchQuery, actionSearch)

**Сознательно вне scope** (числовые min/max-фильтры внутри прокручиваемого экрана настроек, не модальный паттерн "поле + скрытая кнопка", numeric-клавиатура компактнее и структурно менее подвержена extract mode):
- `fragment_settings_images.xml` + `layout-land` (etImageSizeMin, etImageSizeMax)
- `fragment_settings_video.xml` + `layout-land` (etVideoSizeMin, etVideoSizeMax)
- `fragment_settings_audio.xml` + `layout-land` (аналогичные поля)

**Почему не тема целиком:** рассматривался единый фикс через `editTextStyle` в `values/themes.xml` (единственное место, где определена `Theme.FastMediaSorter.App` - qualified-варианты `values-v31`/`values-v35`/`values-night*` переопределяют только `Theme.FastMediaSorter.Base`, что подтверждено собственным комментарием в `values-v35/themes.xml` про S0655/S0653 про "no merge" для style-ресурсов). Отклонено: явный `android:imeOptions` на конкретном поле в XML полностью замещает атрибут из стиля (не объединяется побитово), поэтому все поля с уже заданным `actionSearch`/`actionDone`/`actionNext` (Group B, включая оба подтверждённых репро-кейса - `searchInput`) остались бы незатронутыми темой. Плюс риск непроверяемой на этом прогоне визуальной регрессии `TextInputEditText`/`TextInputLayout` по всему приложению. Точечный XML-фикс безопаснее (`imeOptions` не влияет на визуальный рендер) и полностью верифицируем.

**Валидация:** XML well-formed check всех 31 файлов - PASS (см. §4). Build gate - `.\a.ps1 dq` PASS.

**Дополнительная находка при верификации:** первая on-device проверка (после первого билда) показала, что фикс диалога фильтра работает (кнопка "Применить" достижима, фильтр применяется корректно - подтверждено на `photo_001.jpg`/`photo_002.jpg`), но поиск в настройках всё ещё показывал fullscreen extract mode. Причина: `activity_settings.xml` и `activity_streams.xml` имеют дополнительный резолюшн-бакет `res/layout-w600dp/` (`aapt2 dump resources` подтвердил три варианта: `layout/`, `layout-w600dp-v13/`, `layout-land/`). В Android resource matching `w600dp`/`sw600dp` квалификатор побеждает `land` (тот же паттерн, что и в памяти `project_res_sw_qualifier_beats_land.md`), поэтому на этом широком эмуляторе реально грузился НЕТРОНУТЫЙ `layout-w600dp/activity_settings.xml`, а не подправленный `layout-land`. Добавлены ещё 2 правки: `layout-w600dp/activity_settings.xml` (searchInput) и `layout-w600dp/activity_streams.xml` (etSearch) - итого **33 файла** вместо 31. Пересобрано, переустановлено, повторно верифицировано - поиск в настройках теперь тоже рендерится корректно над клавиатурой.

**On-device верификация (emulator-5554, Pixel_4, API 37, landscape 2280x1080, standard-debug после фикса):**
- Диалог фильтра (`Все изображения` -> Фильтр -> ввод "photo" -> Применить): кнопка "Применить" видна и достижима над клавиатурой (не fullscreen extract), фильтр применился - список сузился до `photo_001.jpg`, `photo_002.jpg`, `photo_large.jpg`, `photo_large_2.jpg`, `photo_panorama.jpg`, `photo_webp_001.webp`, `tvFilterBadge`="1". PASS.
- Поиск в настройках (`Настройки` -> иконка поиска -> ввод "zzzzzzq"): тулбар с вкладками и поле поиска с крестиком-очисткой остаются видимыми над клавиатурой, полноэкранный edit не появляется. PASS.
