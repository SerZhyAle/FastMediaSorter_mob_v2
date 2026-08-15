# S1124 - Quick launch tiles: white monochrome icons invisible on light-grey tile background

**Ticket:** S1124
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-20
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захват находки (verbatim, владелец 2026-07-20)

> иконки белые на сером - их не видно

Замечено владельцем на панели быстрого запуска (Quick launch) во время device-sweep (emulator-5554, build 2.60.7182.317-DEBUG). Скриншот: `temp/S1124/quick_launch_white_icons.png`.

**Симптом:**
- Плитки Quick launch имеют светлый серо-лавандовый фон.
- Встроенные destination-иконки нарисованы монохромным БЕЛЫМ глифом: Calculator, Mini-game, Streams (каст), Settings (шестерёнка). Белое на светло-сером - контраст почти нулевой, иконки не читаются.
- Цветные иконки читаются нормально: лого «Fast Media Sorter» (красный круг), «All Music» (зелёный телефон).

**Причина (гипотеза):** встроенные иконки ярлыков/действий тонируются белым (рассчитаны на тёмную/оверлейную поверхность), тогда как фон плитки в текущей теме светлый. Тинт иконки не согласован с фоном плитки Quick launch.

---

## 1. Проблема

На панели Quick launch монохромные белые иконки встроенных destination'ов сливаются со светлым фоном плитки - пользователь не различает, какой ярлык на плитке. Затрагивает все плитки с белым глифом (Calculator, Mini-game, Streams, Settings и, вероятно, прочие встроенные действия с монохромной иконкой).

---

## 2. Дальнейшие шаги

- Ресёрч: найти адаптер/биндинг плиток Quick launch (`AppLaunchPanelDialogFragment` / tile-adapter), определить, где задаётся тинт иконки destination'а и фон плитки; какие иконки монохромные vs цветные.
- Решение-кандидат: тонировать монохромные иконки контрастным `?attr/colorOnSurface` (или дать иконке контрастную подложку), согласовав со светлой/тёмной темой (фон плитки следует теме - фикс не должен ломать тёмную тему, где белый глиф как раз читается).
- Проверить смежные поверхности с теми же иконками (виджет быстрого запуска, лаунчер-ярлыки), чтобы не чинить точечно там, где корень общий.
- Approval gate (§3.3) заполняется при Draft -> Approved.

---

## 3. Исправление

Корень: встроенные destination-иконки (INTERNAL_ROUTE) - монохромные вектор-глифы с `android:fillColor="@android:color/white"` (пример: `ic_cast.xml`), рендерятся БЕЗ тинта поверх плитки `?attr/colorSurface`. В светлой теме белое на светлом = почти нулевой контраст. Значки приложений (OWN_APP/EXTERNAL_APP) - цветные `pm.getApplicationIcon()`, видны нормально.

Фикс - тинт по признаку монохромности иконки (флаг `tintable`), решаемому в resolve, где известен ИСТОЧНИК иконки. Тип плитки НЕ дискриминатор: `INTERNAL_ROUTE` включает и монохромные глифы (Feature/OsShortcut), и ЦВЕТНЫЕ resource-бейджи (`ic_resource_local` зелёный = «All Music», `ic_resource_smb` синий, sftp оранжевый, ftp фиолетовый, cloud cyan). Только stream-ресурсы (HTTP/RTSP) используют монохромный `ic_cast`.

- Новое поле `AppLaunchPanelTileUi.tintable: Boolean`, выставляется в `ResolveAppLaunchPanelTilesUseCase`:
  - Feature route, OsShortcut route -> `true` (монохромные `ic_*` глифы).
  - Resource route -> `ResourceTypeIconMap.isMonochrome(type)` (true только для HTTP_STREAM/RTSP_STREAM = `ic_cast`; ic_resource_* цветные -> false).
  - OWN_APP / EXTERNAL_APP -> `false` (цветные launcher-значки).
- Оба адаптера (`AppLaunchPanelTileAdapter`, `EditAppLaunchPanelTileAdapter`) тонируют по `tile.tintable`; пустой «+» `ic_add` - всегда монохром (tint on-surface). Тинт `?attr/colorOnSurface` через `ImageViewCompat.setImageTintList` + `MaterialColors.getColor` - тот же цвет, что у подписи (theme-aware: в тёмной теме on-surface светлый, регрессии нет).

Файлы:
- `domain/model/AppLaunchPanelTileUi.kt` (+ поле tintable)
- `core/panel/ResourceTypeIconMap.kt` (+ isMonochrome)
- `domain/usecase/panel/ResolveAppLaunchPanelTilesUseCase.kt` (выставляет tintable по источнику)
- `ui/applaunchpanel/AppLaunchPanelTileAdapter.kt`, `.../edit/EditAppLaunchPanelTileAdapter.kt`

Смежные поверхности проверены: `AppLaunchPanelTileService` - системный QS-tile (один значок приложения, не сетка, вне охвата); RemoteViews-виджеты - отдельные фичи, не панель. Иных поверхностей с этими иконками нет.

Итерация: первая версия тонировала по `type == INTERNAL_ROUTE` и сплющила цветную «All Music» (зелёный->тёмный) - поймано device-тестом (emulator-5554), исправлено на per-icon `tintable`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1038 (gesture-action picker - та же app-launch-panel поверхность), S0663/S0623 (стратегические спеки панели)

---

## 4. Проверка

- Build: `standard debug` компилируется (Kotlin-only изменение адаптеров).
- On-device (BlockNeedUserTest, требуется устройство): открыть Quick launch -> иконки Calculator/Mini-game/Streams/Settings читаются на светлой теме; переключить тему на тёмную -> по-прежнему читаются; цветные значки (лого FMS, All Music) не изменились. Открыть Edit панели -> те же иконки видны. Пустой «+» читается как ghost.
- Debug-теги (пока BlockNeedUserTest): `S1124: quick-launch panel tiles bound`, `S1124: edit-panel tiles bound`.

---

## Evidence

- `temp/S1124/quick_launch_white_icons.png` - панель Quick launch, белые иконки Calculator/Mini-game/Streams/Settings на светлом фоне.
- `temp/S1124/verify_panel.png` - панель после фикса (device verify 2026-07-20).
- `temp/S1124/verify_edit.png` - экран Edit после фикса (device verify 2026-07-20).
- `temp/S1124/logcat.txt` - probe-теги + пиксельное сравнение цветов иконок.

---

## Last Audit

### Pass 2 - re-verify after per-icon tint fix (2026-07-20, build 2.60.7201.237-DEBUG)

- **Method:** on-device (emulator-5554, light theme), terminate + relaunch so the new build re-resolves tiles, UI-driven via mobile-mcp; objective per-tile pixel sampling.
- **Verdict:** PASS. The pass-1 regression is resolved; tint is now decided per-icon (`tile.tintable`), not by tile type.

Panel (`reverify_panel.png`) - expected vs actual:
- Calculator / Mini-game / Streams / Settings dark and legible - PASS (R26 G27 B32).
- FMS logo colored - PASS (red R211 G47 B47).
- All Music preserved green - PASS. R26 G27 B32 (pass 1) -> R76 G175 B80 (green restored).
- Empty "+" tiles legible ghost - PASS.

Edit screen (`reverify_edit.png`) - same result:
- Four monochrome glyphs dark/legible - PASS (R26 G27 B32).
- FMS red unchanged - PASS.
- All Music green - PASS (R76 G175 B80 / greenest R101 G184 B106).

Probe tags (present, this build, pid 13653):
- `AppLaunchPanelTileAdapter: S1124: quick-launch panel tiles bound (on-surface icon tint)` - present.
- `EditAppLaunchPanelTileAdapter: S1124: edit-panel tiles bound (on-surface icon tint)` - present.

All Music RGB before/after fix: green R76 G175 B80 (original, pre-bug) -> dark R26 G27 B32 (pass-1 over-tint regression) -> green R76 G175 B80 (pass-2, preserved). Evidence: `temp/S1124/reverify_panel.png`, `temp/S1124/reverify_edit.png`.

Note: dark-theme non-regression (sec.4) still not re-checked on device (light theme only both passes).

### Pass 1 - initial verify, superseded by pass 2 (2026-07-20, build 2.60.7201.210-DEBUG)

- **Method:** on-device (emulator-5554, build 2.60.7201.210-DEBUG, light theme), UI-driven via mobile-mcp; objective per-tile pixel sampling of screenshots.
- **Verdict:** FAIL (primary bug fixed, but a colored-icon regression violated an explicit acceptance criterion; fixed in build ...237, see pass 2).

Panel (`verify_panel.png`) - expected vs actual:
- Calculator / Mini-game / Streams / Settings legible on light tile - PASS. Was near-white (R197-213), now on-surface dark R26 G27 B32.
- FMS logo stays colored - PASS. Red R211 G47 B47 unchanged.
- All Music stays colored (green) - FAIL. Was green R76 G175 B80, now on-surface dark R26 G27 B32 (green fully gone).

Edit screen (`verify_edit.png`) - same result:
- Calculator / Mini-game / Streams / Settings dark and legible - PASS. Empty "+" tiles legible.
- FMS red unchanged - PASS.
- All Music green -> on-surface dark - FAIL (same regression as panel).

Probe tags (present, path executed on both surfaces):
- `AppLaunchPanelTileAdapter: S1124: quick-launch panel tiles bound (on-surface icon tint)` - present.
- `EditAppLaunchPanelTileAdapter: S1124: edit-panel tiles bound (on-surface icon tint)` - present.

Root of the miss: the on-surface tint is applied by tile type (all non-OWN_APP/EXTERNAL_APP tiles) rather than by whether the drawable is a monochrome white-on-dark glyph. "All Music" is a resource-route tile whose icon is a colored (green) drawable, so it gets flattened to on-surface dark - contradicting the sec.0/3/4 requirement that colored icons (FMS logo, All Music) remain unchanged. Suggested narrowing: tint only INTERNAL_ROUTE glyphs that are actually monochrome (or exclude colored resource-route icons), not by tile-type category alone.

Note: theme test limited to light theme this session (dark-theme non-regression from sec.4 not re-checked on device). Onboarding had to be completed first (fresh app data); launcher "Use as home screen" was left OFF to avoid the HOME-role redirect.
