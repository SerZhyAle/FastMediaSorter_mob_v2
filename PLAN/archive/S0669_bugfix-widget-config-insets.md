# S0669 - Edge-to-edge insets в экранах конфигурации виджетов

**Ticket:** S0669
**Status:** Archived
**Priority:** 90
**Date:** 2026-06-24
**Tier:** 2 - Easy (ad-hoc)

> **Scope:** PRIMITIVE bugfix. Найдено в ходе аудита темы/UI-консистентности.

---

## Problem

Два экрана конфигурации виджетов наследуют общий `BaseActivity`, который включает edge-to-edge (`enableEdgeToEdge()`), но сами не применяют window insets. Контент рисуется под системными барами на Android 15 (API 35, `targetSdk 35`): верх уходит под status bar, низ - под navigation bar / gesture pill. Тема у обоих непрозрачная (`Theme.FastMediaSorter`), opt-out (`shouldEnableEdgeToEdge()=false`) не задан, в корне лейаута - голый `ComposeView` без `fitsSystemWindows`, а в хостируемом Compose-контенте нет ни `applySystemBarInsetPadding()`, ни `WindowInsets`/`systemBarsPadding`.

Это подтверждённое упущение, а не намеренный выбор: соседний `CameraQuickCaptureConfigActivity` с идентичным паттерном (ComposeView + Scaffold) вызывает `applySystemBarInsetPadding()` на хосте и сопровождает это KDoc-заметкой, что одного Scaffold в ComposeView-хосте недостаточно.

Затронуты экраны (оба - `APPWIDGET_CONFIGURE`, запускаются из системного выбора виджетов):
- `ResourceLaunchWidgetConfigActivity`
- `RandomPhotoFrameConfigActivity` (переиспользует тот же binding/Compose-экран)

---

## Approach

- `ResourceLaunchWidgetConfigActivity`: применить insets к ComposeView-хосту в `setupViews()`, зеркаля `CameraQuickCaptureConfigActivity` (общий хелпер `View.applySystemBarInsetPadding`, объединяющий `systemBars()`+`displayCutout()` per Rule 17). Альтернатива - корректные `systemBars`-insets внутри Scaffold; выбрать единый с соседом подход.
- `RandomPhotoFrameConfigActivity`: тот же фикс. Если оба используют один Compose-хост-экран, применить insets единожды в общей точке, а не дублировать.

---

## Done criteria

- `ResourceLaunchWidgetConfigActivity`: на API 35 (launcher -> добавить виджет -> экран конфигурации) контент не перекрывается status bar (верх) и navigation bar / gesture pill (низ) в portrait и landscape.
- `RandomPhotoFrameConfigActivity`: то же самое на API 35, portrait и landscape.
- Подход к insets согласован с `CameraQuickCaptureConfigActivity` (один паттерн на все три config-активити).

---

## 3. Owner inputs

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** существующие экраны конфигурации виджетов, новых элементов или перемещений нет - правка лишь гарантирует, что контент не заходит под системные бары.
- **Accessibility:** контент и тач-таргеты не должны перекрываться status bar (верх) и navigation bar / gesture pill (низ); фокус и достижимость элементов сохраняются.
- **API level constraints:** баг проявляется на API 35 (`targetSdk 35`, системный edge-to-edge); фикс API-агностичен (`applySystemBarInsetPadding` работает от minSdk 26).
- **Validation level:** ручная проверка на устройстве API 35 - оба экрана, portrait и landscape.
- **Owner sign-off:** 2026-06-24 - заведено по итогам аудита темы/UI-консистентности.
- **Related tickets:** none (поиск по каталогу «edge-to-edge» / «widget config inset» - записей нет).
