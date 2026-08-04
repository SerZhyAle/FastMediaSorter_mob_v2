---
name: dialogs-invisible-under-wm-override
description: On API33 AVD an active wm size/density override can make dialog windows render invisibly (they still own input/BACK) - verify dialogs at native geometry
metadata:
  type: feedback
---

On the API 33 emulator, while an `adb shell wm size` / `wm density` override is active, dialog windows (MaterialAlertDialog etc.) can exist and own input (BACK dismisses them, activity callback does not fire) while rendering NOTHING on screen. In the same states `screencap` starts returning 0-byte files; the emulator console screenshot (`adb emu screenrecord screenshot`) still works but shows no dialog either.

**Why:** S1264 (2026-07-28) burned a full investigation on a "Back cannot exit audio player" report that was really the ASK exit dialog showing invisibly during store-screenshot wm reshaping; blind keyevent BACKs alternately opened/closed the invisible dialog, so dumpsys showed the activity never changing.

**How to apply:**
- Any dialog-dependent flow verified on an emulator must be checked at NATIVE geometry (wm size reset; wm density reset) before concluding the dialog is missing or the flow is stuck.
- Symptom fingerprint of an invisible dialog under blind driving: activity's back callback fires on odd presses only (every second BACK produces no app-side log), topResumedActivity never changes, no dialog in screenshots.
- When a flow mysteriously "eats" input under wm override, dumpsys window + a probe log in the back callback discriminate in minutes.
- Sibling-session state transplant trick used here: `run-as .. base64 files/datastore/settings.preferences_pb` from the source device -> decode -> `run-as .. base64 -d` onto the test device reproduces another device's full settings; user key remaps live separately in Room `input_bindings`.
