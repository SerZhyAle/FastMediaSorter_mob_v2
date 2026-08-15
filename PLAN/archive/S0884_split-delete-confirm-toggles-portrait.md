# Спецификация: S0884 - Развести тогглеры подтверждения удаления/перемещения в портрете

**Ticket:** S0884
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-02

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Настройки - Управление - в группе "Удаление файлов, корзина" . Только портрет! тогглеры "Подтверждать удаление" и "Подтверждать перемещение" разместить в две строки

---

## Goal

В группе настроек "File deletion and trash" (`headerSafety`/`containerSafety`, экран Settings -> Management -> Destinations) тогглеры "Подтверждать удаление" (`rowConfirmDelete`) и "Подтверждать перемещение" (`rowConfirmMove`) сейчас стоят бок о бок в одной горизонтальной строке (`containerConfirm`, два дочерних `LinearLayout` с `layout_weight="1"`). В портретной ориентации подписи и подзаголовки тесно прижаты друг к другу. Нужно развести их в две отдельные строки (вертикальный стек) **только в портрете** - альбомная раскладка (`layout-land/fragment_settings_destinations.xml`) остаётся без изменений (уже двухколоночная, что и требуется в ландшафте).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0881 (аналогичная задача, другая секция настроек - "Взаимодействие с операционной системой")
- **Flavor:** all (Destinations settings screen is flavor-agnostic)
- **Orientation:** portrait-only change; `layout-land/fragment_settings_destinations.xml` untouched
- **Localization:** no string changes - EN/RU/UK strings untouched

---

## Phases

### Phase 1 - Split confirm-delete/confirm-move toggles into two rows (portrait only)

1. In `app_v2/src/main/res/layout/fragment_settings_destinations.xml`, change the `containerConfirm` `LinearLayout` (currently wrapping `layoutConfirmDelete` + `layoutConfirmMove` side by side) from `android:orientation="horizontal"` to `android:orientation="vertical"`.
   - Verification: `containerConfirm` in `app_v2/src/main/res/layout/fragment_settings_destinations.xml` has `android:orientation="vertical"`.
2. In the same file, change `layoutConfirmDelete` and `layoutConfirmMove` child `LinearLayout`s from `android:layout_width="0dp" android:layout_weight="1"` to `android:layout_width="match_parent"` (no weight - weight distribution is meaningless once stacked vertically).
   - Verification: neither `layoutConfirmDelete` nor `layoutConfirmMove` in the portrait file has `layout_weight`; both have `android:layout_width="match_parent"`.
3. Leave `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` untouched - landscape keeps the existing two-column layout for `containerConfirm`.
   - Verification: `containerConfirm` in the `layout-land` file still has `android:orientation="horizontal"` and `layoutConfirmDelete`/`layoutConfirmMove` still have `layout_width="0dp"` + `layout_weight="1"`.
4. Build gate: `standard debug` compiles, resources link cleanly (`fr`/`fc` or `d`/`dq`).
   - Verification: build exits 0.

---

## Risks

| Risk | Probability | Impact | Mitigation |
|------|:---:|------|------|
| Vertical stack loses visual grouping between the two confirm toggles | Low | Cosmetic only | Existing `margin_tiny` bottom margin on `layoutConfirmDelete` already provides row separation once stacked |

---

## User impact (docs/FEATURES)

No change to docs/FEATURES - internal layout/spacing fix only, not a new capability.

---

## Acceptance criteria (strategic-level)

1. In portrait, "Подтверждать удаление" and "Подтверждать перемещение" render as two separate full-width rows instead of two side-by-side half-width columns.
2. Landscape rendering of the same section is pixel-identical to before this change.
3. No new/changed/removed string resources; no changed IDs referenced elsewhere (`rowConfirmDelete`, `rowConfirmMove`, `layoutConfirmDelete`, `layoutConfirmMove`, `containerConfirm` all keep their ids).

---

## Last Audit

**Date:** 2026-07-04
**Method:** grep-verified layout diff + `standard debug` build (`compileStandardDebugKotlin` + `processStandardDebugResources`, exit 0).

Findings:
- `app_v2/src/main/res/layout/fragment_settings_destinations.xml`: `containerConfirm` flipped `horizontal` -> `vertical`; `layoutConfirmDelete`/`layoutConfirmMove` flipped `0dp`+`layout_weight="1"` -> `match_parent`, no weight. Confirmed via grep (line 32/34/48).
- `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml`: untouched - `containerConfirm` still `horizontal`, both children still `0dp`+`layout_weight="1"`. Confirmed via grep (line 64-95).
- `OperationsSettingsFragment.kt` only toggles `layoutConfirmDelete`/`layoutConfirmMove` `visibility` (VISIBLE/GONE) - no width/weight logic in code, so the XML-only change is safe without a Kotlin edit.
- No string resources touched; no ids added/removed/renamed.
- Build: `standard debug` (`fc` fast check) - `BUILD SUCCESSFUL`, resources parsed and linked cleanly (portrait + land both re-merged).

Not captured: live on-device/emulator screenshot of the two-row portrait rendering. `temp/BUILD.LOCK` was held by a concurrent agent session at closure time, so packaging+install for a screenshot was skipped per CLAUDE.md Rule 23 (no concurrent gradle builds). The change is a single deterministic `LinearLayout` orientation flip mirroring the already-vertical sibling rows (`layoutEnableSafeMode`, `layoutUseTrash`) in the same container, so grep+build evidence is treated as sufficient for this Tier 2 cosmetic fix.

**Verdict:** Verified.
