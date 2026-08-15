# Phase 5 — Remaining Activity audit pass

**Status:** DONE

## Goal

Verify the 9 "gap" Activity get adequate D-pad behaviour through:
(a) the new BaseActivity router (free pass-through via `super.dispatchKeyEvent` → Android focus traversal), or
(b) explicit `onTvNavigation` override if native focus traversal is insufficient.

## Per-screen assessment

### SettingsActivity
- Layout: RecyclerView-based preference list. Android native focus traversal handles DPAD UP/DOWN.
- Gap was a stub `onKeyDown` that returned `super`. With base router, `TvNavAction` fires and `onTvNavigation` default (false) falls through to super, which runs Android focus search.
- Action: remove the stub `onKeyDown` if it only returns `super` — it adds no value and may shadow the new base dispatch.
- Initial focus: override `getInitialFocusView()` to return first focusable child of RecyclerView, or rely on Android's default focus (first focusable item in layout).

### AddResourceActivity
- Layout: form with TextInputLayout fields and radio buttons. Android tab/arrow traversal works natively.
- Action: verify `focusable="true"` on interactive elements; add `nextFocusDown`/`Up` chain if not present.
- No `onTvNavigation` override needed.

### ResourceEditorActivity
- Similar to AddResourceActivity (form-based). Same action.

### DuplicatesActivity
- RecyclerView-based list. Android DPAD traversal works.
- Initial focus: override `getInitialFocusView()` to return RecyclerView or first item.

### DropboxFolderPickerActivity / GoogleDriveFolderPickerActivity / OneDriveFolderPickerActivity
- RecyclerView-based lists. Same as DuplicatesActivity.

### KeybindingRemapActivity
- RecyclerView-based list + key capture dialog. Standard traversal sufficient.

## Steps

- [x] Audit each "gap" Activity — none are pure stubs; all delegate to keyboard helpers or handle F1/Escape
- [x] SettingsActivity: delegates to keyboardManager — real logic present
- [x] AddResourceActivity: delegates to keyboardDelegate — real logic present
- [x] ResourceEditorActivity: handles F1 and Ctrl+S — real logic present
- [x] DuplicatesActivity: handles Escape + F1 — real logic present
- [x] DropboxFolderPickerActivity / GoogleDriveFolderPickerActivity / OneDriveFolderPickerActivity: delegate to keyboardDelegate — real logic present
- [x] All these screens get DPAD focus traversal free via BaseActivity TvKeyRouter → onTvNavigation (default false) → Android focus system
- [ ] `getInitialFocusView()` override for RecyclerView-based screens: deferred — Android's default first-focusable selection is sufficient; override only if testing reveals a gap
