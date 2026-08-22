---
name: device-operator-tool-call-budget
description: android-device-operator subagents stop at ~10 tool calls - a multi-check device sweep in one brief burns the whole budget on preflight and returns nothing
metadata:
  type: feedback
---

Give an `android-device-operator` subagent **one check per call**, not a batch of five.

**Why:** on 2026-08-19 I sent two operators a five-check and a three-check sweep each. Both hit a
10-tool-call ceiling during preflight and navigation and returned with **zero** checks started -
one spent its whole budget on `pm path`, permission grants, a media listing, a ping, two
screenshots and a UI dump; the other spent it fighting a launch crash. Two full agent runs
(~97k subagent tokens) produced environment findings and no ticket verdicts at all.

**How to apply:**
- Budget roughly: preflight (install/version/permission checks) eats 3-5 calls on its own. Do the
  preflight yourself, in the parent, then hand the agent a device already in the right state.
- One brief = one ticket's checks, with the navigation path spelled out so the agent does not
  spend calls discovering it. Fan out several agents in parallel instead of nesting checks.
- Tell it the launcher component explicitly. One agent burned two calls guessing `.MainActivity`
  and got `Error type 3: Activity class does not exist`; the real one is
  `com.sza.fastmediasorter.ui.main.MainActivity`.
- Their environment reports are still worth having - both agents surfaced real blockers - but
  never plan on getting verdicts and blockers from the same run.

Related: [[silent-subagent-is-not-stuck]], [[subagent-pixel-measurements-unreliable]],
[[verify-subagent-build-failures]].
