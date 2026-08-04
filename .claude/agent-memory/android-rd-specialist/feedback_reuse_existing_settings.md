---
name: reuse-existing-settings-toggles
description: Before adding a new settings toggle for a feature, check for an existing toggle covering the same capability and gate on it instead of duplicating
type: feedback
---

Before adding a NEW settings toggle / `AppSettings` field to gate a feature, check whether an existing toggle already governs that capability; if so, gate the new feature on the existing toggle instead of creating a parallel one.

**Why:** S0523 (main-menu quick voice/video/photo capture) - I added three new toggles (`quickVoiceMenuEnabled` / `quickVideoMenuEnabled` / `quickPhotoMenuEnabled`) to gate the three new overflow-menu entries. The owner rejected them as redundant: the app already had "Включить запись с микрофона" (`micRecordingEnabled`), "Включить запись видео" (`disableVideoCapture`, inverted), and "Включить съёмку фото" (`disableCameraCapture`, inverted). I had to remove all three new toggles (fields, DataStore keys, CSV rows, both layout orientations, fragment wiring, 6 strings) and gate the menu entries directly on the existing toggles. The owner's rule: new menu items "полностью зависят от уже существовавших галочек".

**How to apply:** When a spec says "feature X gated by a toggle/setting", first grep `AppSettings.kt` + the relevant settings fragment for an existing toggle covering the same capability (mic / video / photo / a specific media op) before adding a new field. Prefer gating on the existing one. Note the inversion quirks: camera/video master toggles persist as negative flags (`disableCameraCapture` / `disableVideoCapture`), so "enabled" = `!disable*`. This is the settings-layer twin of [[check-existing-tooling-first]] (grep for an existing helper before authoring a new one).
