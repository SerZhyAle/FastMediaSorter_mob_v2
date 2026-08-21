---
name: device-subagent-needs-known-tap-path
description: Delegate a device-driving task to a subagent only when the exact tap path is already known - a device subagent's tool-call budget runs out mid-UI-navigation, and even a one-log-line task costs more delegated than driven directly
metadata:
  type: feedback
---

Dispatch an `android-device-operator` subagent only for a device task whose **exact tap path is already
known**. If the scenario still has to be discovered on screen, drive it in the parent instead.

**Why:** measured on 2026-08-21 during a `/spec-sweep` on `RFCR110NBQJ`. Three device subagents were
dispatched; two stopped at a ~10-16 tool-call budget in the middle of UI navigation and returned nothing
usable:

- "pin 4 channels" - tapped 4 pin buttons, the list re-sorted underneath, and it could not say which two
  had actually taken. The database showed 2 of 4, and one of those was a channel it never intended to pin.
- "open an image and invoke OCR" - spent its whole budget reaching the wrong folder in the file browser and
  never triggered the recogniser. The parent then did the same task in about six calls.

The pattern is not agent quality - each of them reported its partial state honestly and refused to guess,
which is exactly right. It is that UI discovery is unbounded work handed to a bounded budget. A dump ->
decide -> tap loop costs 2-3 calls per screen, so anything more than three screens deep does not fit.

**How to apply:**

- Known path (resource ids, labels, or coordinates in hand, ≤3 screens): delegate - context isolation is
  worth it, since screenshots and UI trees stay out of the parent.
- Unknown path, or the scenario branches on what appears: drive it in the parent.
- Cheaper than either: skip the UI. Read state straight from the app database
  (`adb exec-out run-as <pkg> cat databases/<db>` and open it locally), or make the target reachable in
  one tap - the browse list sorts newest-first, so `cp` a fixture to `/sdcard/` and `touch` it and it
  lands first.
- When you do delegate, put the navigation you already know **into the brief** and tell the agent not to
  rediscover it, plus "a precise negative beats a guess" - that instruction is what made both failed runs
  still useful.

Related: [[subagent-impl-skips-final-phase]] (the same budget wall on multi-phase implementation plans),
[[orchestrator-owns-device-installs]], [[test-device-galaxy-s21]].
