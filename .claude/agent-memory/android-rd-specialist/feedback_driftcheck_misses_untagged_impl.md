---
name: driftcheck-misses-untagged-impl
description: spec-next/spec-all marker-based drift-check gives false CLEAN when impl was committed untagged; sanity-check code before assuming greenfield
type: feedback
---

`/spec-next` preflight and `/spec-all` 0a-drift use a **marker-based** drift-check: it only flags DRIFT when a git commit message carries the `Sxxxx` marker OR the code has inline `// Sxxxx:` markers. It reports **CLEAN** (false negative) when the implementation was committed under a generic message (e.g. "release: commit pending WIP before plateau merge") with no ticket tag and no inline markers.

**Why:** 2026-07-19, S1118 (radio-stream-buffer-tolerance) was selected as `In Progress` with `drift=CLEAN`, no tactical folder. The full implementation (`RadioStreamBufferConfig.kt` + resilience/history-branch in `StreamInlineAudioManager`/`AudioPlaybackService`) was **already in the tree**, committed untagged in `a934f22f`. Drift-check missed it; a naive F3 re-drive would have re-implemented existing code. Researcher flagged "same class as" other tickets - this recurs because this repo routinely lands work under generic "commit pending WIP" messages.

**How to apply:** when a resumed spec is `In Progress`/`Implemented` with **no tactical folder** (or no `## Last Audit`) yet `drift=CLEAN`, do NOT assume greenfield. First grep/read the spec's target classes to check the impl isn't already present. If it is -> switch to spec-all **review mode**: write a `## Last Audit`, resolve §6 research items against the actual code, and land at `BlockNeedUserTest` (device gate) or `Verified`. Cheap check, saves a full wasted F2+F3 cycle.
