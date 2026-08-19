---
name: never-drive-a-device-an-agent-holds
description: While a device-operator subagent is driving a phone, do not tap it yourself - two drivers create states neither one planned and the agent reports on a screen you moved
metadata:
  type: feedback
---

While an `android-device-operator` agent is running against a device, issue no input to that device: no
`adb.ps1 tap`, no `key`, no `text`, no `launch`. Read-only probes are fine and useful - `current`,
`shot`, `log`, `run-as ls` - and reading the screenshots the agent captures is the cheapest way to
verify its claims first-hand.

**Why:** 2026-08-19, S1821. The agent seemed slow to press the Streams toolbar "ОБНОВИТЬ", so I tapped
it myself at the scaled coordinates from its own screenshot (2000x900 displayed, x1.20 to the real
2400x1080). The tap missed the toolbar and opened the channel row instead, so the app jumped into the
player and started a live stream. The agent's next screenshot then showed a state it had not created,
on a screen it had not navigated to, while it was mid-plan. Impatience cost more than the wait would
have: a correction message, an agent re-plan, and a screenshot trail that no longer reads as one
sequence.

**How to apply:**
- Slowness is not stuckness ([[silent-subagent-is-not-stuck]]). Judge progress from artifacts - new
  screenshots, new `adb_log_*.log` captures, a changing `topResumedActivity` - not from elapsed quiet.
- If the agent is genuinely off track, send it a message; that is the arbitration channel. Taking the
  device is not.
- If you truly must take over, say so in a message first and let it stop, then drive alone.
- When a tap you computed from a screenshot lands wrong, distrust the mapping before the app: the
  scale factor is stated in the image note, but toolbar hit targets, overflow collapsing and the
  window insets can still put the real target elsewhere. Prefer the app's own affordance - a snackbar
  action, a `key back` - over a computed pixel.
