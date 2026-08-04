---
name: popup-invisible-to-uiautomator-is-modality
description: A popup missing from uiautomator/accessibility dumps is a window-modality problem - check mCurrentFocus before blaming FLAG_SECURE or the dump tooling
metadata:
  type: feedback
---

When a `PopupWindow`-backed list (dropdown, menu, anchored picker) does not appear in `uiautomator dump` or the accessibility tree, and D-pad keys do nothing, the cause is almost always that the popup is **non-focusable**, not that the dump is broken and not FLAG_SECURE.

**Why:** S1390 (2026-08-04). The settings dropdown row hosted a plain `AutoCompleteTextView`; its `ListPopupWindow` is deliberately non-modal so keyboard focus stays in the field. That window never takes system input focus, so it is skipped by the window walk both tools do, and every key press keeps going to whatever view held focus before. FLAG_SECURE was suspected first and is unrelated - it only blacks out screenshots (S1284).

**How to apply:**
- One-command diagnosis with the popup open: `adb shell dumpsys window | grep mCurrentFocus`. Still the Activity -> the popup is non-modal, and no amount of key injection will reach it. Shows `Window{.. PopupWindow:..}` -> focus is fine, look elsewhere.
- Fix is `ListPopupWindow.isModal = true` on a popup the component owns; the platform widgets that manage their own popup (`AutoCompleteTextView`, the Material `ExposedDropdownMenu` end-icon delegate) expose no modality switch, so the component has to open its own.
- In touch mode a `ListView` has no selected item: the first `DPAD_DOWN` only leaves touch mode and lands on the current row. Send one extra press before counting steps, and read `selected="true"` in the dump rather than assuming the count.
- The same reasoning applies before writing a Maestro flow against any popup - if `mCurrentFocus` is the Activity, no selector will ever find the options.
