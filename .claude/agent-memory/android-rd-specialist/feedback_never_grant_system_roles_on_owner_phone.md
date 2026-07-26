---
name: never-grant-system-roles-on-owner-phone
description: On a device-test, never accept a system role/default-app dialog, and re-read button bounds immediately before tapping an animating dialog
metadata:
  type: feedback
---

Two rules, learned together on 2026-07-26 during the S1107 run on the owner's working phone (SM-G781B).

**1. Never accept a system role or default-app dialog on the owner's device.** Verifying that the dialog *presents* is the check; granting the role is not required and is not yours to do. Decline instead - and on a ticket like S1107 the decline path is itself a criterion worth exercising.

**Why:** on that run I intended to tap "Cancel" and the app became the phone's home screen. Restoring took `cmd role remove-role-holder` + `add-role-holder` for the Samsung launcher, and only luck made it recoverable without the owner's help - a launcher swap on a phone whose lock screen adb cannot open would have stranded him.

**2. Re-read element bounds immediately before the tap when a dialog is still animating.** Not just after a recreate - after any transition. A system dialog slides in: the logged `Relayout returned: old=(0,1658,..) new=(0,1455,..)` shows the buttons moving ~200px while the dump you already took goes stale in your hand.

**How to apply:** dump -> tap must be adjacent with nothing in between, and for a dialog that just appeared, wait for it to settle (or dump twice and compare bounds) before committing. When the wrong tap would be destructive - role grants, default-app changes, delete confirmations - prefer `input keyevent KEYCODE_BACK` to dismiss over aiming at a "Cancel" button whose position you inferred.

Related device-drive traps: [[onboarding-device-test-gotchas]], [[avd-device-sweep-gotchas]], [[test-device-galaxy-s21]].
