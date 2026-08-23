# Стратегическая спецификация: S1757 - Подпись ярлыка обрезается в плотной сетке

**Ticket:** S1757
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-16
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - эпик S1615 (кластер C-21)

---

## Goal

В плотной сетке подпись ярлыка рабочего стола должна полностью помещаться (до 2 строк) без обрезания. Для этого размеры иконки и отступы подписи динамически и пропорционально адаптируются под размер ячейки (cellSize).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1615 (родительский эпик, запись L-028), S1756.
- **UI placement contract:** подпись до двух строк читаема в плотной сетке.
- **Accessibility:** читаемость текста поверх иконки.
- **Validation level:** визуальная проверка длинных названий в плотной сетке.
- **Owner sign-off:** делегировано конвейеру /spec-all эпика S1615 - 2026-08-16.

<!-- auto-approved by /spec-all - 2026-08-18 -->

---

# Phase 01 - Proportional Icon and Label Scaling in Dense Launcher Grid

**Strategic spec:** `PLAN/S1757_launcher-cell-label-dense-grid-clipping.md`
**Status:** ✅ Done

## Objective

Add proportional scaling for launcher cell shortcut icon, monogram, badge, paddings, and label margin based on cell size in `LauncherGridGeometry` and apply it in `LauncherCellViewBinder`.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherGridGeometry.kt` | Modified | ≤ 500 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt` | Modified | ≤ 600 |
| `app_v2/src/launcherEnabled/res/layout/item_launcher_cell_shortcut.xml` | Modified | ≤ 150 |
| `app_v2/src/testLauncherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherGridGeometryTest.kt` | Modified | ≤ 500 |

## Steps

### Step 01.1 - Add shortcut layout scaling spec math to LauncherGridGeometry and unit tests

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherGridGeometry.kt`, `app_v2/src/testLauncherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherGridGeometryTest.kt`

**Prompt for developer:**

> Add `ShortcutCellLayoutSpec` data class and `shortcutLayoutSpec(cellSizeDp: Float)` helper to `LauncherGridGeometry` that scales shortcut icon size, monogram text size, mode badge size, vertical content padding, and label margin proportionally for dense cell sizes (`< 88dp`). Add unit tests in `LauncherGridGeometryTest` validating full size at >=88dp and scaled dimensions at dense sizes.

**Why:**

In dense grid mode shortcut text is clipped because fixed 44dp icon leaves insufficient vertical room for a two-line label.

**Verification:**

- `Grep` - `data class ShortcutCellLayoutSpec` present in `LauncherGridGeometry.kt`.
- `Grep` - `fun shortcutLayoutSpec` present in `LauncherGridGeometry.kt`.
- Unit test in `LauncherGridGeometryTest` passes.

**Status:** `[x]` done

---

### Step 01.2 - Update item_launcher_cell_shortcut.xml and apply scaling in LauncherCellViewBinder

**Files:** `app_v2/src/launcherEnabled/res/layout/item_launcher_cell_shortcut.xml`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt`

**Prompt for developer:**

> Add `android:id="@+id/cellContentLayout"` to the inner LinearLayout of `item_launcher_cell_shortcut.xml`. In `LauncherCellViewBinder.bindShortcut`, retrieve current cell size from container, compute `shortcutLayoutSpec`, and set icon width/height, monogram width/height/textSize, badge width/height, content layout vertical padding, and label top margin accordingly.

**Why:**

Cell binder needs to adjust visual child dimensions dynamically so shortcut labels up to two lines fit without clipping in dense grid mode.

**Verification:**

- `Grep` - `cellContentLayout` ID present in `item_launcher_cell_shortcut.xml`.
- `Grep` - `shortcutLayoutSpec` called in `LauncherCellViewBinder.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every Step 01.* above is `[x]` done.
- [x] Project compiles cleanly.
