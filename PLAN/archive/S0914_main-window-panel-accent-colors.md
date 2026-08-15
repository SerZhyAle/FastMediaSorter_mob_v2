# Спецификация (compact): S0914 - Цвета-акценты и иконки сворачиваемых панелей главного окна

**Ticket:** S0914
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-03
**Tier:** 2 - Easy (ad-hoc)

---

## Goal

Три сворачиваемые панели главного окна - «Программы и сценарии», «Трансляции..», «Фильтр типов ресурсов» - сейчас визуально неразличимы: все свёрнутые чипы окрашены одной бирюзой (`main_resource_filter_strip_background`), а развёрнутые панели сидят на нейтральном `?attr/colorSurface`. Надо присвоить каждой панели уникальный фон-акцент, одинаковый в развёрнутом и свёрнутом состоянии, чтобы панель узнавалась по цвету. В свёрнутый чип, помимо текста, добавить маленькую узнаваемую иконку панели (уже принятые в приложении символы) - без увеличения высоты свёрнутого состояния. Контент развёрнутой панели (иконки/текст/табы) подстроить под контраст на цветном фоне.

Решения владельца (собраны на входе, см. §3.3): сплошной цвет в обоих состояниях, контент подстроен под светлый передний план. Палитра из существующего `#CC`-семейства action-panel: Программы = олива `#CC4D4D00`, Трансляции = пурпур `#CC4D004D`, Фильтр = бирюза `#CC004D4D` (текущий). Иконки: программы = `ic_apps`, трансляции = `ic_cast`, фильтр = `ic_tune`.

**Non-goals:**

- Не трогать логику сворачивания/разворачивания, focus/D-pad, persist-состояние.
- Не вводить `values-night` варианты - цвета фиксированы по дизайну (прецедент `player_panel_*`).
- Не заводить новые drawable - переиспользуются существующие.
- Не менять overflow-попап панели программ (остаётся на нейтральном фоне темы).

---

## Phase 1 - Accent colour resources

Steps:
1. In `app_v2/src/main/res/values/colors.xml`, replace the S0781 pair (`main_resource_filter_strip_background` `#CC004D4D` + `main_resource_filter_strip_foreground` `@color/white`) with a per-panel accent set in the same `#CC` action-panel family:
   - `main_programs_panel_accent` = `#CC4D4D00`
   - `main_streams_panel_accent` = `#CC4D004D`
   - `main_filter_panel_accent` = `#CC004D4D`
   - `main_panel_accent_foreground` = `@color/white` (shared white foreground for text/icons on every accent)
2. Update the block comment to reference S0914 and explain the "same colour for expanded body + collapsed chip" contract.

Verification:
- `expected:` `grep -c "main_.*_panel_accent" colors.xml` = 3, plus one `main_panel_accent_foreground`.
- `expected:` no remaining references to `main_resource_filter_strip` anywhere under `app_v2/src/main` after Phase 2 (grep = 0).

## Phase 2 - Collapsed chips (3 layout variants)

Steps:
1. In `res/layout/activity_main.xml`, `res/layout-land/activity_main.xml`, `res/layout-w600dp/activity_main.xml`, for each collapsed chip:
   - `chipProgramsCollapsed`: `android:background` -> `@color/main_programs_panel_accent`; add `app:drawableStartCompat="@drawable/ic_apps"`.
   - `chipStreamsCollapsed`: `android:background` -> `@color/main_streams_panel_accent`; add `app:drawableStartCompat="@drawable/ic_cast"`.
   - `chipFilterCollapsed`: `android:background` -> `@color/main_filter_panel_accent`; add `app:drawableStartCompat="@drawable/ic_tune"`.
   - All three: `android:textColor` and `app:drawableTint` -> `@color/main_panel_accent_foreground`.
2. Keep existing `app:drawableEndCompat="@drawable/ic_double_arrow_down"`, `paddingVertical="6dp"`, `drawablePadding` untouched so the collapsed height does not grow (leading icon is 24dp = same as the existing trailing icon).

Verification:
- `expected:` each of the 3 chips in each of the 3 variants has a `drawableStartCompat` and its own `*_panel_accent` background (9 chips total).
- `expected:` `.\a.ps1 fr` (resources/manifest) PASS.

## Phase 3 - Programs panel (expanded)

Steps:
1. `res/layout/view_main_programs_panel.xml`: `programsPanelContent` `android:background` -> `@color/main_programs_panel_accent`; `btnProgramsPanelMenu` and `btnProgramsPanelOverflow` `app:iconTint` -> `@color/main_panel_accent_foreground`.
2. `MainProgramsPanelManager.rebuild()`: after inflating `item_main_program`, tint `btnProgram` icon + text and `btnProgramMenu` icon to `main_panel_accent_foreground`. Resolve the colour once via `ContextCompat.getColor`. Do NOT apply this tint inside `showOverflowPopup()` - the overflow window renders on the theme popup surface, where white would be illegible.

Verification:
- `expected:` `.\a.ps1 fc` PASS.
- `expected:` grep of `MainProgramsPanelManager.kt` shows the accent tint applied inside `rebuild()` only, not `showOverflowPopup()`.

## Phase 4 - Streams panel (expanded)

Steps:
1. `res/layout/view_main_streams_panel.xml`: `streamsPanelContent` `android:background` -> `@color/main_streams_panel_accent`; `btnStreamsPanelEntry` `android:textColor` + `app:iconTint` -> `@color/main_panel_accent_foreground`.
2. `res/layout/item_main_stream_channel.xml`: `tvChannelLabel` `android:textColor` + `btnChannelMenu` `app:iconTint` -> `@color/main_panel_accent_foreground` (this layout is rendered only inside the panel via `StreamPanelChannelAdapter`, so a static white foreground is safe).

Verification:
- `expected:` `.\a.ps1 fc` PASS.

## Phase 5 - Filter tabs (expanded)

Steps:
1. In all 3 `activity_main.xml` variants, `tabResourceTypes`:
   - `android:background` -> `@color/main_filter_panel_accent`.
   - `app:tabIconTint`, `app:tabTextColor`, `app:tabSelectedTextColor`, `app:tabIndicatorColor` -> `@color/main_panel_accent_foreground` (owner accepted the loss of the theme `colorPrimary` accent on this row).

Verification:
- `expected:` land + w600dp match portrait for `tabResourceTypes`.
- `expected:` `.\a.ps1 fr` PASS.

## Phase 6 - Build, capability, closure

Steps:
1. `standard debug` build passes (`.\a.ps1 dq`).
2. Record the delivered capability in `docs/ALL_FEATURES.jsonl` via `scripts/all_features/add.ps1` (per-panel accent colours + collapsed-chip icons for the main-window panels).
3. Insert `Timber.d("S0914: ...")` probes at the changed rendering entry points (chip build / panel content render), set status `BlockNeedUserTest` with a note listing the on-device visual checks.

Verification:
- `expected:` build PASS; ALL_FEATURES validate PASS.
- **On-device (BlockNeedUserTest):** each panel shows its own colour in both expanded and collapsed state; collapsed chip shows leading icon (apps/cast/tune) with unchanged row height; content legible across the 6 selectable colour themes x day/night x portrait/land/w600dp.

---

## Revision 1 (2026-07-03) - drop the filter panel's accent, fix chip overflow

Device-test screenshot (real device, all three panels collapsed) showed two problems:

1. `chipFilterCollapsed` rendered far taller than its siblings with no visible text. Root cause
   (confirmed via live emulator repro + accessibility-tree bounds): `mainCollapsedPanelsRow` is a
   `LinearLayout` with no `layout_weight` on any chip, so the third chip in XML order only gets
   whatever width is left after the first two are measured. Without `maxLines`/`ellipsize`, a long
   label (translated "Resource type filter") wrapped into many near-zero-width lines, ballooning
   the `wrap_content` height and dragging the whole row - and the other two chips with it - taller.
2. Owner decision: the resource-type filter panel should not carry a distinct accent colour at all -
   only the programs and streams panels keep one. The filter panel (collapsed chip + expanded
   `tabResourceTypes`) reverts to the form's own `?attr/colorSurface` background with
   `?attr/colorControlNormal` content, same as before S0781/S0914 introduced any filter colouring.

Changes:

- All three collapsed chips (`chipProgramsCollapsed`, `chipStreamsCollapsed`, `chipFilterCollapsed`)
  in all three `activity_main.xml` variants get `android:maxLines="1"` + `android:ellipsize="end"` -
  symmetric fix, protects every chip from the same width-squeeze artifact regardless of which one
  ends up last in measurement order or which locale has the longest label.
- `chipFilterCollapsed`: `android:background` -> `?attr/colorSurface`; `android:textColor` and
  `app:drawableTint` -> `?attr/colorControlNormal`. No longer uses `main_filter_panel_accent`.
- `tabResourceTypes` (all three variants): `android:background` -> `?attr/colorSurface`; removed
  `app:tabIconTint` / `app:tabTextColor` / `app:tabSelectedTextColor` / `app:tabIndicatorColor`
  overrides - falls back to the Material theme's default tab colours, which already adapt correctly
  across the 6 colour themes x day/night (more robust than a second hardcoded value).
- `main_filter_panel_accent` removed from `colors.xml` (orphaned); `main_programs_panel_accent` /
  `main_streams_panel_accent` / `main_panel_accent_foreground` unchanged, still used by the programs
  and streams panels.
- Phase 2/Phase 5 steps above describe the pre-revision design; this section is the current source
  of truth for the filter panel's styling.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0781 (introduced the filter-strip colour, now generalised), S0807/S0808/S0809 (collapse mechanism + shared collapsed row), S0755/S0756 (programs/streams panels).
- **UI scope (resolved with owner):** expanded panel gets the SAME solid accent colour as its collapsed chip; panel content (icons/text/tabs) forced to a light foreground for contrast in all themes. Palette = existing `#CC` action-panel family (olive/purple/teal). Icons = `ic_apps` / `ic_cast` / `ic_tune` (already the accepted symbols for programs / streams / filter). Collapsed height must not increase.
- **Flavor scope:** Streams panel exists only where `SUPPORT_STREAMS` (standard/noLegal/legacy/vr; absent on lite/photos). Programs panel gated by user setting; filter panel by remote-source availability. No flavor-specific colour logic - accents live in `src/main` colours, panels that are hidden simply never render.
- **Data / API / accessibility:** no data change, no API-level concern (pure View/resource work). Icons are additive to existing chips; text labels remain, so the icon is redundant-coded (not colour-only), keeping the collapsed chips accessible.
