# Research 03 - Bottom sheet focus on TV / D-pad

**Strategic spec:** [`../../S0459_unified-send-to-menu.md`](../../S0459_unified-send-to-menu.md) §6 (item 3), ADR-2
**Status:** Resolved
**Date:** 2026-06-16
**Method:** web research (Material Components, Android TV navigation docs) + project input policy (CLAUDE.md Rule 16).

---

## Question

The receiver list from the bar press is a bottom sheet (ADR-2). Confirm it is focusable and traversable with D-pad/TV remote and keyboard, with non-colour disabled distinction.

## Findings

- Material `BottomSheetDialogFragment` has documented D-pad focus quirks: initial focus is not guaranteed to land on sheet content, and focus can escape to the dimmed scrim/behind the sheet (material-components-android issue #1434).
- Android TV navigation is focus-based, not scroll-based: every actionable row must be `focusable` and reachable; the framework moves focus on D-pad, it does not auto-scroll.
- Standard remedy: host the rows in a `RecyclerView` (or a focusable `LinearLayout` of rows), make rows `focusable`/`focusableInTouchMode=false`, and explicitly request focus on the first enabled row when the sheet is shown.

## Decision

- Keep the bottom sheet (ADR-2). Make it TV-safe by construction:
  - Rows are `focusable=true` with a visible focus state (selector / `?attr` state), `minHeight` = touch target.
  - On show (`onStart`/post to the sheet view), request focus on the first **enabled** row.
  - Disabled (unavailable) rows: non-colour distinction - "Не установлено" label + reduced alpha icon, still announced by TalkBack (icon supplements text, never replaces it).
  - Ensure the sheet does not trap focus: outside-tap / BACK dismisses; D-pad up/down cycles within the list.
- Precedent to mirror: the app already ships `BottomSheetDialogFragment`s (`IconPickerBottomSheet`, `NowPlayingBottomSheetFragment`, `PermissionRationaleBottomSheet`, `PdfThumbnailSheet`, `StreamOffloadOfferDialog`). The receiver sheet should reuse their established focus / list pattern rather than invent one - this also keeps TV behaviour consistent with the rest of the app.
- Fallback already exists for the overflow path: the native `addSubMenu` submenu (ADR-2) is intrinsically D-pad friendly. If on-device testing reveals a sheet focus regression on TV, the overflow submenu form is the proven-focusable alternative for that surface - no architecture change needed.

## Spec impact

- §3.2 "Доступность" and §7 risk row "подменю/список неудобно на TV/D-pad" are addressed by explicit focus management; on-device D-pad traversal becomes a `BlockNeedUserTest` check in the menu-UI phase.
- No new owner decision required - form stays bottom sheet, mitigations are tactical.
