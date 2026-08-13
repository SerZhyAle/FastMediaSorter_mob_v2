# S1081 - Кольцо фокуса не рисуется ни на одной MaterialCardView

**Status:** Archived
**Priority:** 50
**Tier:** 4 - Strategic (ad-hoc)
**Created:** 2026-07-17

---

## 0. Как найдено (verbatim)

Обнаружено в ходе аудита S0404 фазы 06 как **out-of-scope находка** (CLAUDE.md 3.1): к гаджетам лаунчера отношения не имеет, тривиальной правкой не чинится, требует своего исследования. Дедуп по каталогу (`search.ps1 -Query "MaterialCardView" / "focus ring" / "foreground focus"`) - совпадений нет.

Отчёт аудитора, проверявшего по **байткоду material 1.14.0**:

> `MaterialCardView.<init>` (offset 109) вызывает `MaterialCardViewHelper.loadFromAttributes`, а тот в конце **безусловно перетирает** foreground:
>
> ```
> 232: invokespecial shouldUseClickableForeground:()Z
> 239: invokespecial getClickableForeground:()Landroid/graphics/drawable/Drawable;
> 264: invokevirtual com/google/android/material/card/MaterialCardView.setForeground:(Landroid/graphics/drawable/Drawable;)V
> ```
>
> `MaterialCardView` не переопределяет `setForeground` → замена, не слияние. `View`-конструктор парсит `android:foreground` внутри `super()`, до хелпера.
>
> **Конкретный отказ:** карточка кликабельна → `shouldUseClickableForeground()` = true → рисуется ripple из `app:rippleColor`, а **штатное 2dp-кольцо `focus_button_stroke` не появляется никогда**. На D-pad/TV ярлыки подсвечиваются мягкой заливкой вместо чёткой рамки, как на всех остальных фокусируемых поверхностях.
>
> Не регрессия Phase 06: та же строка есть в отгруженном `item_app_launch_panel_tile.xml:14`.

---

## 1. Проблема

`android:foreground="@drawable/focus_button_background"` на `MaterialCardView` - **мёртвый атрибут**: Material перетирает foreground своим clickable-foreground в конструкторе. Пишущий эту строку разработчик уверен, что поставил кольцо фокуса; в рантайме кольца нет.

Почему это важно именно здесь: проект прямо целится в устройства без сенсора (ТВ-боксы, магнитолы, медиабоксы - S0404 §3.2), где D-pad единственный способ навигации, а Rule 16 требует внятного фокуса. Мягкая ripple-заливка на карточке отличима от соседней невыделенной карточки заметно хуже, чем 2dp-кольцо, и именно на большом экране с дивана.

Ложное чувство покрытия усугубляет `scripts/quality/assert-focus-highlight.ps1`: он проверяет **наличие** атрибута, а не то, что он доезжает до экрана.

## 2. Цели

- Кольцо фокуса реально рисуется на каждой фокусируемой `MaterialCardView`.
- Гейт ловит именно этот случай (атрибут на MaterialCardView = нерабочий), а не только отсутствие атрибута.
- Разработчик не может тихо повторить ошибку.

## 3. Известные точки

- `app_v2/src/main/res/layout/item_app_launch_panel_tile.xml:14` - отгруженная панель быстрого запуска (S0623/S0663).
- `app_v2/src/launcherEnabled/res/layout/item_launcher_cell_shortcut.xml:15` - ярлык рабочего стола лаунчера (S0404 фаза 04).
- Полный список - grep `android:foreground` в файлах, чей корень `MaterialCardView`.

## 4. Направления решения (не выбрано)

- `app:checkedIcon`/`app:strokeColor` со `state_focused` в `ColorStateList` - Material-родной путь, кольцо через `strokeColor`/`strokeWidth`.
- Обёртка: `FrameLayout` с foreground поверх карточки.
- Свой сабкласс `MaterialCardView`, восстанавливающий foreground после `super()`.

Нужен замер на реальном ТВ/пульте: возможно, `strokeColor` со `state_focused` даёт нужную чёткость и без кольца.

## 5. Проверка

- На устройстве с D-pad: фокус на карточке визуально отличим от невыделенной.
- Гейт падает на новой `MaterialCardView` с `android:foreground`.

## Last Audit

### Manual (device) - 2026-07-20

- Result: PASS
- Device: emulator-5554, Android 15 (SDK 35). Build: com.sza.fastmediasorter.debug v2.60.7182.317-DEBUG.
- Fix under test: `FocusMaterialCardView` subclass draws the 2dp ring in `onDrawForeground` after Material's clickable foreground, gated on `isFocused`.
- Screens exercised (both host `FocusMaterialCardView`):
  - Add Resource (`AddResourceActivity`, `cardLocalFolder`/`cardNetworkFolder`/..): D-pad focus lands on the card; probe fires. Window carries `FLAG_SECURE`, so `screencap` returns black - no pixel screenshot possible on this screen (capture limitation, not a fix defect).
  - Quick launch panel (`AppLaunchPanelActivity`, non-secure): visual captured.
- Ring visible? Yes. Expected: clear ~2dp blue ring around the focused card. Actual: blue rounded-rect ring drawn on the focused tile ("Fast Media Sorter & Organizer"), distinct from the default gray stroke on siblings; ring follows focus card-to-card (moved to "Calculator" via DPAD_RIGHT, previous ring cleared). Evidence: `temp/S1081/07_focus_tile1.png`, `temp/S1081/08_focus_tile2.png`.
- Probe fired? Yes. Expected: `S1081:` line when the ring is applied. Actual: `D FocusMaterialCardView: S1081: drew MaterialCard focus ring`, firing only while a card is focused and on each newly focused card. Evidence: `temp/S1081/logcat_s1081.txt`, `temp/S1081/logcat_s1081_quicklaunch.txt`.
