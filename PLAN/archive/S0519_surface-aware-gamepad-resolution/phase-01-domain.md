# Phase 01 - Domain: browser CommandIds + group

Goal: introduce the `browser.*` command namespace and `BROWSER_ACTIONS` group, and wire the two compiler-checked `when`-over-enum sites.

## Steps

- [ ] In `domain/input/CommandId.kt`, add a `// --- BROWSER_ACTIONS ---` block with constants:
  - `BROWSER_SELECT = "browser.select"`
  - `BROWSER_BACK = "browser.back"`
  - `BROWSER_MULTI_SELECT = "browser.multi_select"`
  - `BROWSER_CONTEXT_MENU = "browser.context_menu"`
  - `BROWSER_SEARCH = "browser.search"`
  - `BROWSER_TAB_NEXT = "browser.tab_next"`
  - `BROWSER_TAB_PREV = "browser.tab_prev"`
  - Verification: `grep -c "browser\." app_v2/src/main/java/com/sza/fastmediasorter/domain/input/CommandId.kt` returns 7.

- [ ] In `domain/input/CommandGroup.kt`, add `BROWSER_ACTIONS` to the enum (append after `SORTING_ACTIONS`, before `VR_ONLY` to keep browser rows above VR in `ordinal` sort).
  - Verification: enum contains `BROWSER_ACTIONS`.

- [ ] In `domain/input/usecase/ResetGroupUseCase.kt`, add the branch `CommandGroup.BROWSER_ACTIONS -> "browser."` to `prefixForGroup`.
  - Verification: `when (group)` compiles exhaustively (no `else`); branch present.

- [ ] In `ui/keybinding/KeybindingRemapViewModel.kt`, add `commandId.startsWith("browser.") -> CommandGroup.BROWSER_ACTIONS` to `commandGroupOf` (before the `else` fallback).
  - Verification: browser commandIds map to `BROWSER_ACTIONS`, not the `SYSTEM_UI` fallback.

## Notes

- Enum insertion position matters only for UI sort order, not correctness.
- No other `when (group: CommandGroup)` without `else` exists beyond `ResetGroupUseCase` (verified via grep in F2); if a build surfaces another, add the branch there too.
