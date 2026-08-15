# Phase 1 — Coverage Audit Matrix

**Status:** TODO

## Goal

Enumerate all 15 Activity + key dialogs.
For each: determine coverage state for touch / mouse / keyboard / D-pad / gamepad / accessibility.

## Coverage states

- `handled` — explicit correct implementation confirmed
- `pass-through` — native Android behaviour is sufficient, confirmed by code review
- `gap` — identified deficiency requiring work
- `not-applicable` — modality is not relevant to this screen
- `unknown` — not yet audited

## Activity × Modality Matrix

| Activity | Touch | Mouse | Keyboard | D-pad | Gamepad | A11y |
|----------|:-----:|:-----:|:--------:|:-----:|:-------:|:----:|
| MainActivity | handled | pass-through | handled | handled | handled | unknown |
| BrowseActivity | handled | pass-through | handled | handled | handled | unknown |
| PlayerActivity | handled | handled | handled | handled | handled | unknown |
| StandalonePlayerActivity | handled | handled | handled | handled | handled | unknown |
| WelcomeActivity | handled | pass-through | gap | gap | not-applicable | unknown |
| SettingsActivity | handled | pass-through | gap (stub) | gap (stub) | not-applicable | unknown |
| AddResourceActivity | handled | pass-through | gap (stub) | gap (stub) | not-applicable | unknown |
| ResourceEditorActivity | handled | pass-through | gap (stub) | gap (stub) | not-applicable | unknown |
| DuplicatesActivity | handled | pass-through | gap (stub) | gap (stub) | not-applicable | unknown |
| DropboxFolderPickerActivity | handled | pass-through | gap (stub) | gap (stub) | not-applicable | unknown |
| GoogleDriveFolderPickerActivity | handled | pass-through | gap (stub) | gap (stub) | not-applicable | unknown |
| OneDriveFolderPickerActivity | handled | pass-through | gap (stub) | gap (stub) | not-applicable | unknown |
| KeybindingRemapActivity | handled | pass-through | gap (stub) | gap (stub) | not-applicable | unknown |
| ReceiveShareActivity | handled | pass-through | pass-through | pass-through | not-applicable | unknown |
| AuthSessionsActivity | handled | pass-through | pass-through | pass-through | not-applicable | unknown |

## Key findings

- 9 of 15 Activity have `gap` for keyboard / D-pad (no override, or override returns false/super without logic).
- WelcomeActivity is the priority gap: first screen users see on TV; has a non-scrolling ViewPager2 that ignores DPAD.
- List-based screens (Settings RecyclerView, Duplicates RecyclerView, Cloud Pickers RecyclerView) likely get free DPAD via Android focus traversal — but initial focus is not set, so the first DPAD press has no target.
- Mouse: `BaseActivity.dispatchTouchEvent` already passes through to `super` after logging — no mouse blocking at the base layer. Custom touch consumers in sub-screens need per-screen audit.
- Accessibility: not audited in this pass — deferred to Phase 5 post-code verification.

## Steps

- [x] List all Activity from catalog (done above — 15 Activity in `app_v2/src/main/`)
- [x] Classify by reading `dispatchKeyEvent`/`onKeyDown` overrides
- [ ] Verify RecyclerView-based screens get free DPAD by running on TV emulator (open question §1 from strategic spec — deferred to device test phase)
- [ ] Verify mouse pass-through on screens with custom touch consumers (open question §4 — code search)

## Mouse custom touch consumer search (preflight for mouse audit)

Screens to check manually:
- `WelcomeActivity` — ViewPager2 handles horizontal swipe (intercepts `ACTION_MOVE`), may or may not block mouse horizontal drag. Not a hard blocker since mouse clicks map to tap events.
- `PlayerActivity` — has custom `onTouchEvent` for swipe gestures; verified in strategic spec that mouse-click falls through.

Result: no hard mouse-blocking pattern identified in code review. Matrix cell "pass-through" is correct for these screens.
