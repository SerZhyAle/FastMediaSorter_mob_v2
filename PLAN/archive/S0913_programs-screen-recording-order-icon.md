# Compact spec: S0913 - Screen recording programs item: order after Camera-OCR + settings icon

**Ticket:** S0913
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-03
**Tier:** 2 - Small (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-03
**Parent:** S0774 (сценарий «Видеозапись экрана» в блоке программ)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-03

**Текст:**

> Видеозапись экрана. Если включена в настройках - появляется в списке «программ и сценариев» - Это либо меню «три точки» в основном окне, либо панель «программы и сценарии». С той иконкой, какая в настройках. Выполнена аналогично другим пунктам этого меню/панели. Порядок появления - после «Фото-OCR-перевод».

---

## Goal

S0774 уже добавил пункт «Видеозапись экрана» в меню «три точки» и на панель программ. Эта спека доводит его до пожеланий владельца: пункт должен использовать ту же иконку, что и строка настройки (`ic_display`), а не собственную `ic_video`, и появляться **после** пункта «Фото-OCR-перевод» (Camera-OCR), а не перед ним. Панель программ - зеркало меню (единый источник через `populateMenu`), поэтому правки в меню автоматически отражаются на панели.

Уточнение терминов: «Фото-OCR-перевод» = единственный OCR-пункт меню программ, строка `setting_camera_ocr_translation_title` (RU «Быстрый перевод с камеры»), пункт `MENU_ITEM_CAMERA_OCR`.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0774, S0770
- **UI placement:** пункт «Видеозапись экрана» идёт сразу после Camera-OCR в меню и на панели (порядок сортируется общим `populate`).
- **UI icon:** иконка меню/панели = иконка строки настройки, `@drawable/ic_display` (была `ic_video`).
- **UI visibility/fallback:** без изменений - пункт по-прежнему появляется только при включённом тумблере и связанном контроллере записи (наследуется от S0774); flavor-охват не меняется.
- **Flavor reach:** без изменений - standard (`fms.screenCapture=on`) + noLegal; отсутствует в lite/photos/legacy.

---

## Phases

### Phase 1 - Match the settings icon on the programs item

- **Step 1.1** - In `MainScreenRecordingMenuManager.populate`, replace `.setIcon(R.drawable.ic_video)` with `.setIcon(R.drawable.ic_display)` so the menu/panel item uses the same drawable as the settings toggle row (`rowScreenRecordingEnabled`, `app:str_icon="@drawable/ic_display"`).
  - *Verification:* `Grep` for `ic_display` in `MainScreenRecordingMenuManager.kt` returns the `setIcon` line; no remaining `ic_video` reference in that file.
- **Step 1.2** - Update the class KDoc / add a one-line WHY comment noting the icon mirrors the settings row (S0913), replacing any wording implying a bespoke icon.
  - *Verification:* KDoc mentions the settings-icon parity; comment is EN-only and explains WHY, not WHAT.

### Phase 2 - Reorder the programs item after Camera-OCR

- **Step 2.1** - In `MainProgramsMenuCoordinator` companion, renumber the `MENU_ORDER_*` constants so screen recording sorts directly after Camera-OCR: `QUICK_CAPTURE = 3`, `CALCULATOR = 4`, `CAMERA_OCR = 5`, `SCREEN_RECORDING = 6`, `LINK_DOWNLOAD = 7`, `MINI_GAME = 8` (Streams = 1, app-launch panel = 2 unchanged).
  - *Verification:* `MENU_ORDER_SCREEN_RECORDING` (6) is strictly greater than `MENU_ORDER_CAMERA_OCR` (5) and strictly less than `MENU_ORDER_LINK_DOWNLOAD` (7); all order constants remain unique.
- **Step 2.2** - Update the canonical-order comment inside `populate` (the `S0758` note) so it lists screen recording in its new slot after Camera-OCR.
  - *Verification:* the comment enumerates the item order and places screen recording after camera-OCR.
- **Step 2.3** - Grep unit tests for `MENU_ORDER_` / `MainProgramsMenuCoordinator` order assertions; update any that pin the old numbering.
  - *Verification:* `Grep` shows no test asserting the pre-change order values; `.\a.ps1 fk` (or targeted test) compiles.

### Phase 3 - Build gate

- **Step 3.1** - Run `standard debug` compile (`.\a.ps1 dq` / `fk`).
  - *Verification:* build succeeds (`BUILD SUCCESSFUL`); no unresolved `ic_display` / order-constant references.

---

## Last Audit

**Date:** 2026-07-03 (spec-all inline audit)
**Verdict:** Verified

- Phase 1 (icon) - PASS. `MainScreenRecordingMenuManager.populate` sets `R.drawable.ic_display`, the same drawable the settings toggle row `rowScreenRecordingEnabled` declares via `app:str_icon="@drawable/ic_display"`. No `ic_video` reference remains in the file.
- Phase 2 (order) - PASS. `MENU_ORDER_CAMERA_OCR = 5` < `MENU_ORDER_SCREEN_RECORDING = 6` < `MENU_ORDER_LINK_DOWNLOAD = 7`; all order constants unique. The programs item now sorts directly after Camera-OCR ("Фото-OCR-перевод").
- Panel + overflow parity - PASS. `MainProgramsPanelManager.rebuild` builds a throwaway PopupMenu through the shared `populateMenu`, then reads its order-sorted items copying `item.icon` into each button; the overflow popup reuses the same icons. Both the new order and the settings icon propagate to the panel and overflow without extra edits.
- Tests - PASS. No unit test pins `MENU_ORDER_*` values or the item icon (grep of `app_v2/src/test` returned nothing); no test change required.
- Build - PASS. `:app_v2:compileStandardDebugKotlin` full recompile (`--rerun-tasks`) `BUILD SUCCESSFUL`; warnings only in unrelated pre-existing files, none in the two touched files.
- Flavor reach unchanged - the item still gates on the settings toggle AND a bound `ScreenVideoRecordingController` (S0774); standard (`fms.screenCapture=on`) + noLegal only.
