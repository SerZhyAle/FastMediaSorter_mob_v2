# Стратегическая спецификация: S0617 - Имя ресурса снапшота без ellipsize в ландшафте видео-настроек

**Ticket:** S0617
**Status:** Archived
**Priority:** 45
**Date:** 2026-06-22
**Tier:** 1 - Quick Win
**Roadmap entry:** Ad-hoc - захвачено при research S0609 (2026-06-22)
**Tactical spec:** будет создан через `/spec-tech`

> **Scope:** STRATEGIC. Draft-инбокс.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-22
**Захвачено во время:** research S0609 (инвентаризация раскладок настроек)

**Симптом:** в ландшафтной раскладке видео-настроек поле с именем выбранного ресурса для снапшота не обрезается. Длинный путь/имя ресурса переполняет строку или клипуется без многоточия.

**Доказательства:**

- `tvSelectedSnapshotResource` в `app_v2/src/main/res/layout-land/fragment_settings_video.xml` имеет `layout_width="wrap_content"` без `android:ellipsize` и без `android:maxLines`.
- В portrait тот же элемент - `width=0dp` + `weight=1` (растягивается), поэтому проблема только в landscape, где он помещён в горизонтальный ряд.

**Что выяснить при доработке:**

- Желаемая стратегия усечения (ellipsize end / middle, maxLines=1) с учётом читаемости имени ресурса.
- Не ломает ли усечение горизонтальный ряд «текст + Select + Clear + формат + RadioGroup».

**Связь:** обнаружено в ходе S0609, самостоятельный косметический дефект, вне объёма S0609.

**Вложения:** нет.

---

## 1. Реализация

**Стратегия усечения:** `ellipsize="end"` + `maxLines="1"`. Поле показывает имя ресурса (`resource?.name`, не путь), поэтому усечение с конца читаемо - начало имени важнее хвоста.

**Landscape** (`layout-land/fragment_settings_video.xml`):

- `tvSelectedSnapshotResource`: добавлены `ellipsize="end"`, `maxLines="1"`, `maxWidth="@dimen/fragment_settings_video_tvSelectedSnapshotResource_maxWidth"` (220dp).
- Ширина поля и родительского ряда `layoutSnapshotResourceSelector` остались `wrap_content` - кластерная компоновка «текст + Select + Clear + формат + RadioGroup» сохранена (соответствует комментарию «tighter alignment»). `maxWidth` ограничивает только длинное имя, не растягивая ряд.

**Portrait** (`layout/fragment_settings_video.xml`):

- `tvSelectedSnapshotResource` уже `width=0dp` + `weight=1` (ограничен по ширине). Добавлены `ellipsize="end"` + `maxLines="1"`, чтобы длинное имя усекалось в одну строку вместо переноса по вертикали. Counterpart-правка по Rule 11.

**Dimen:** добавлен `fragment_settings_video_tvSelectedSnapshotResource_maxWidth` = 220dp (безопасен на landscape от ~600dp ширины: контролы ряда ~320dp + indent оставляют запас).

**Ряд не ломается:** контролы справа сохраняют естественную ширину; усекается только текст.

---

## Last Audit

### Manual device test - 2026-06-23 (emulator-5554, Android 17, x86_64, noLegal debug; src/main shared layouts)

**Verdict: FAIL** - в landscape (раскладка бага) имя ресурса НЕ усекается: показывается полностью, без многоточия. Portrait усекается корректно.

**Resource name used:** `AnotherVeryLongSnapshotDestinationFolderNameToVerifyEllipsizeTruncationABCDEFG` (77 символов; зарегистрирован как локальный folder-ресурс с галкой "Mark for quick sort", иначе DestinationPickerDialog его не показывает - `GetDestinationsUseCase` фильтрует по `isDestination && destinationOrder >= 0 && !isReadOnly`).

**Landscape (FAIL):**
- `tvSelectedSnapshotResource` отрисовал полное имя `..TruncationABCDEFG` на одной строке без `…`; ширина view = 1819px (~910dp) при `maxWidth=220dp` (=440px) - `android:maxWidth` НЕ ограничивает измеренную ширину, ellipsize не срабатывает.
- Причина (гипотеза, не входит в этот прогон): `wrap_content` TextView внутри `wrap_content` горизонтального LinearLayout измеряется в UNSPECIFIED; `maxWidth` не задаёт усекающую границу, поэтому `ellipsize=end` не активируется. Кнопки Select/Clear остаются видимы (экран 2560px широкий), но критерий усечения не выполнен.
- Скриншоты: `temp/S0617_sweep/30_landscape_settings.png`, `32_landscape_fullres.png`, `33_landscape_row_crop.png` (zoom).

**Portrait (PASS):**
- Имя усечено на одной строке с хвостовым `…` (`..Truncation…`); ширина view = 859px (ограничена `width=0dp`+`weight=1`). Ряд цел: Select Destination + Clear Selection полностью видимы, без переполнения/переноса.
- Скриншоты: `temp/S0617_sweep/27_selected_portrait.png`, `34_portrait_fullres.png`, `35_portrait_row_crop.png` (zoom).

**Fired probes:** `Timber.d("S0617: snapshot resource name bound, ellipsize check (name=AnotherVeryLongSnapshotDestinationFolderNameToVerifyEllipsizeTruncationABCDEFG)")` (1 раз, при привязке имени).

**Note (device):** AVD - крупнопланшетный, активность Settings не вращается через `user_rotation` из-за `ignoreOrientationRequest=true` на display 0; landscape достигнут только через `adb emu rotate`. По завершении `ignoreOrientationRequest` восстановлен в `true`, ориентация - portrait.
