---
name: stylus-overlay-eats-typed-text
description: Android 15 AVD "Try out your stylus" overlay swallows input text while the a11y tree still reports the app's field focused - typed text silently lands nowhere
metadata:
  type: feedback
---

On an Android 15 AVD the system's stylus-handwriting onboarding overlay ("Try out your stylus") can sit on top of the app and capture `input text`, while `uiautomator dump` still reports the app's own `EditText` as `focused="true"` and empty. Every read says "field focused, field empty" and every write appears to succeed, so the loop looks like a mysterious no-op.

**Why:** the overlay is a separate system window that `uiautomator dump` does not include, so the tree describes the app underneath it rather than what is actually receiving key events. Console/tool output confirms "text typed" because adb delivered it - to the wrong window. Hit on S1509 (2026-08-08): three consecutive attempts to type a stream URL vanished before a screenshot showed the overlay holding the text.

**How to apply:**
- Typed text does not appear AND the tree insists the field is focused -> take ONE inline `mobile_take_screenshot` immediately. This is the sanctioned case where the tree cannot answer; do not keep retrying blind.
- Kill it for the session: `adb shell settings put secure stylus_handwriting_enabled 0`, then dismiss the visible overlay (its own Cancel button).
- Related trap in the same flow: once the soft keyboard opens, the dialog shifts UP - buttons captured before the keyboard appeared are stale, and a tap on the old "OK" position lands on the keyboard and types a character into the field. Re-read bounds after every keyboard state change, not just once per screen (S1509: an `i` got appended to the URL this way). See [[avd-device-sweep-gotchas]].
