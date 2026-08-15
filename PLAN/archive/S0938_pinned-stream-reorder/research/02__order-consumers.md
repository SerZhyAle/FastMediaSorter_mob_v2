# Research 02 - Order consumers and the "select a stream" surface

**Strategic §6 item:** 2 (Поверхность «выбор трансляции для виджета») + 3 (граница применения)
**Status:** Resolved

## Question

Where is a pinned stream "selected" (e.g. for a widget), and do those surfaces already read the shared ordered source, or do they re-sort and lose the manual order?

## Findings - consumers of pinned order

- **Main-window panel** (`MainStreamsPanelManager`) reads `ObservePinnedStreamSourcesUseCase` -> `observePinned()` = `sortIndex ASC`. Honours manual order already.
- **Player prev/next channel navigation** (`PlayerViewModel`, S0640) snapshots `ObserveStreamSourcesUseCase` -> `observeAll()` = `pinned DESC, sortIndex ASC`. Honours manual order already.
- **Streams list/grid screen** (`StreamsViewModel.applyFilter`) re-sorts the pinned block by the chosen `SortMode` (default NAME): `compareByDescending { pinned }.then(secondary)`. This **discards** `sortIndex` order within the pinned block - so reorder commands would have no visible effect on this screen.

## Findings - widget "selection"

- The home-screen widget config (`ResourceLaunchWidgetConfigActivity`) selects **Resources**, not `stream_sources` rows. There is no dedicated pinned-stream picker list separate from the surfaces above.
- The user's "например для виджета" is illustrative: any surface that lists pinned channels for selection consumes the shared ordered DAO queries (`observePinned` / `observeAll`). No extra picker needs a code change.

## Decisions

- **Item 2:** ordering is governed by one shared column consumed by all surfaces; the only surface that overrides it is `StreamsViewModel.applyFilter`. Fix: keep the pinned block in its incoming `sortIndex` order (the input list from `observeAll` is already `sortIndex ASC` within pinned), apply the chosen `SortMode` only to the unpinned rows. This makes the menu commands visibly reorder the list/grid and keeps unpinned catalog rows sorted.
- **Item 3 (scope boundary):** reorder operates only within the pinned set. The reorder menu items appear only for a pinned row (and only when more than one channel is pinned); unpinned rows never show them. Non-pinned catalog order is out of scope.

## Impact on plan

- `StreamsViewModel.applyFilter` partitions matched rows into pinned (kept in input `sortIndex` order) + unpinned (sorted by `SortMode`); existing `StreamsFilterTest` expectations for pinned-block ordering are updated to the manual-order contract.
- Reorder menu items are gated on `source.pinned` and disabled at the edges (top row: no up / no to-top; bottom row: no down).
