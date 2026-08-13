# S0374 - Tactical Plan: Adaptive priority+overflow top command bar (Browse)

**Ticket:** S0374
**Status:** Tactical
**Strategic spec:** `PLAN/S0374_browse-top-command-buttons-20pct.md`
**Module:** app_v2
**Flavors:** all (mic gated by `SUPPORT_MIC_RECORDING`, black-screen by `SUPPORT_AUDIO`, automate by `ENABLE_SCHEDULED_OPERATIONS`)

## Goal (from strategic §3.3, revised 2026-06-07)

Replace the horizontal-scroll top command bar with an adaptive priority bar: measure available width, keep the highest-priority commands on the bar, push the rest into the existing "⋮" overflow menu. No horizontal scroll, no clipped or off-screen buttons. The +20% `browse_cmd_*` composite sizing is unchanged.

## Design decisions (resolve research open questions)

- **Push model.** A new `BrowseCommandOverflowManager` decides the visible/overflow partition and force-hides overflowed buttons (`GONE`). `ResourceOpsMenuManager` surfaces a menu item for a command iff the overflow manager reports that command overflowed. The old pull-model `isControlFullyVisibleInCommandViewport` viewport check is removed.
- **HSV removed.** `topCommandScroll` (`HorizontalScrollView`) is deleted; `layoutControls` becomes the width-constrained top bar container.
- **Priority order** (highest first - last to overflow): btnBack, btnSort, btnFilter, btnRefresh, btnToggleView, btnSelectAll, btnDeselectAll, btnPlay, btnPlayRandom, btnMicRecord, btnCreateFolder, btnCreateTextFile, btnCreateDrawing. `btnResourceOps` ("⋮") is never overflowed - it always stays as the menu anchor; its width is reserved before allocation.
- **Eligibility.** A command participates in allocation only when feature-visible (its runtime predicate already set it `VISIBLE`). Buttons hidden by feature gating never enter overflow; they are simply absent.
- **Menu routing.** Every overflow-eligible command has a `menu_resource_ops.xml` item routed to the same `BrowseButtonSetupHelper.ButtonCallbacks` action as its toolbar button. Mic in the menu triggers `onMicRecordSingleTap()` (press-and-hold is bar-only).
- **Pure core.** The allocation algorithm is a pure function (`allocateCommandBar`) extracted for unit testing; the manager is the thin View-bound shell.

## Phases

- PHASE_01 - Pure allocation core + unit test
- PHASE_02 - BrowseCommandOverflowManager (View-bound shell)
- PHASE_03 - Layout: remove HorizontalScrollView (portrait + landscape)
- PHASE_04 - Overflow menu: items + push-model in ResourceOpsMenuManager
- PHASE_05 - Wiring + recompute triggers + focus-chain repair
- PHASE_06 - Debug tags, build gate, device-test handoff

## Pre-Implementation Blockers

- None. All strategic §6 items resolved (owner contract 2026-06-07). Research open questions resolved inline above.

## Phase status

- [x] PHASE_01 - allocation core + unit test (test green)
- [x] PHASE_02 - BrowseCommandOverflowManager
- [x] PHASE_03 - HSV removed (portrait + landscape)
- [x] PHASE_04 - overflow menu items + push-model
- [x] PHASE_05 - wiring + triggers + focus repair (standardDebug PASS)
- [x] PHASE_06 - debug tags + build (2 tags, standardDebug PASS) - device-test pending
