**Status:** Archived

# S0587 - Streams list scroll-navigation buttons (top / page-up / page-down / bottom)

## Goal (RU)

Повторить на списке Стримов четыре кнопки навигации из файлового браузера: в самый верх, на страницу вверх, на страницу вниз, в самый низ. Поведение и вид - как в браузере (auto show/hide, скрытие на краях), без пересечения с мини-плеером и системными барами, в портрете и ландшафте.

## 0. Raw capture

User report (RU, verbatim):
4. В браузере файлов у нас есть для списка кнопки "В самый верх списка", "На страницу вверх", "на страницу вниз", "В самый низ списка". Нужно повторить то как они работают для списка Стримов.

## 2. Reference implementation

- File browser: `BrowseScrollButtonManager` (visibility) + `BrowseButtonSetupHelper.setupScrollButtons()` (click actions), FABs in `activity_browse.xml`.
- Streams: `StreamsActivity` + `rvStreams` (`RecyclerView`) inside a `FrameLayout` in `activity_streams.xml` (+ `layout-land`).

## 3. Resolved decisions

- Reuse vs copy: a thin Streams-local `StreamScrollButtonManager` (`ui/streams/helpers/`) carries both the visibility rule and the four click actions. `BrowseScrollButtonManager` is coupled to `MediaFileAdapter` (its `notifyItemRangeChangedSafely`), so promoting it to shared is higher-risk than a small focused manager. The same drawables / dimens / strings are reused, so the look and behaviour do not diverge.
- Placement: a vertical FAB stack (`LinearLayout`, `gravity=bottom|end`) overlaid inside the existing `FrameLayout` that wraps `rvStreams`. The mini-player strip (`streamMiniControl`) is a sibling below that `FrameLayout`, so the buttons never overlap it; the root is `fitsSystemWindows`, keeping them inside the system bars.
- Visibility: identical to Browse - all four hidden when every row fits; top/page-up hidden at the top; bottom/page-down hidden at the bottom. Updated on scroll and on list submit.
- Reuse the existing resources: `bg_scroll_button`, `ic_arrow_upward`, `ic_double_arrow_up`, `ic_double_arrow_down`, `ic_arrow_downward`, `selector_player_button_tint`, dimens `browse_scroll_button_*`, strings `scroll_to_top` / `scroll_page_up` / `scroll_page_down` / `scroll_to_bottom`. No new resources.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0565 (Streams screen), S0570 (curated catalog import)
- **UI placement:** vertical FAB stack bottom-end inside the `rvStreams` `FrameLayout`, above the mini-player strip; mirrors the file browser.
- **UI visibility/fallback:** auto show/hide matching the browser; all hidden when the list fits one screen; no buttons shown for an empty list.
- **Input support:** buttons are `focusable` with D-pad/TV reachability, consistent with existing stream controls.

## 4. Acceptance

- Streams list has the same four navigation actions behaving like the file browser.
- Buttons do not overlap the mini-player strip or system bars (portrait + landscape).
- Reuses the Browse scroll resources/behaviour rather than duplicating the look.

## 5. Implementation phases

### Phase 01 - FAB overlay in both layouts

- Add a vertical FAB stack (top, page-up, page-down, bottom) inside the `FrameLayout` wrapping `rvStreams` in `res/layout/activity_streams.xml`.
- Ids: `fabStreamsScrollToTop`, `fabStreamsPageUp`, `fabStreamsPageDown`, `fabStreamsScrollToBottom`. Each `gone` initially, `focusable`, reusing the Browse drawables/dimens/tint and the existing content-description strings.
- Mirror the identical block into `res/layout-land/activity_streams.xml`.
- Verification: both files contain the four ids; `a.ps1 fr` passes.

### Phase 02 - StreamScrollButtonManager

- New `ui/streams/helpers/StreamScrollButtonManager.kt`: constructor takes the `RecyclerView` and the four button views; exposes `attach()` (wires click actions + a scroll listener) and `updateVisibility()`.
- Click actions mirror `BrowseButtonSetupHelper.setupScrollButtons`: top/bottom via `scrollToPositionWithOffset`, page up/down via `smoothScrollBy(0, ±recyclerView.height)`.
- Visibility rule mirrors `BrowseScrollButtonManager.updateScrollButtonsVisibility`, reading `itemCount` from the layout manager.
- Verification: `a.ps1 fk` passes; class present via catalog query.

### Phase 03 - Wire into StreamsActivity

- Instantiate the manager in `setupViews()` after `rvStreams` is configured; call `attach()`.
- Call `updateVisibility()` from the existing `observeData` list collector (after `adapter.submitList`) so the buttons react to filter/sort/search changes.
- Verification: `a.ps1 fc` passes; manual list scroll shows/hides buttons.
