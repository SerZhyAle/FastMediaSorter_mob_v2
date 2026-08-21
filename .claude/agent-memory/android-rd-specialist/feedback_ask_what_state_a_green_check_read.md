---
name: ask-what-state-a-green-check-read
description: Before trusting a passing acceptance check, name the preconditions it needed and verify they existed - a check that observed nothing passes exactly like a check that observed success
metadata:
  type: feedback
---

Before believing a green acceptance line, ask **what state it actually read**, and verify those
preconditions existed. A check whose subject was absent passes identically to a check whose subject
worked.

**Why:** three instances in one day, 2026-08-21, two of them mine and one from a parallel session.

- S1697 was set `Verified` by a full static audit reporting `PASS 20 · FAIL 0 · MANUAL 0`, while that
  same audit block left its own line `- [ ] Verify phone resources browsing on physical Wear OS
  device` unticked. A device run an hour later showed one of the five acceptance criteria did not hold
  at all. `MANUAL 0` did not mean "no manual work pending" - it meant the manual lines were not counted.
- S1832's acceptance was that pins, order and a desktop cell survive a schema upgrade. The device
  carried zero pins and no cell. Every check would have passed, having preserved nothing. This is the
  nastier half: it produces a real log and looks stronger than a static inference, not weaker.
- S1881 sat at `BlockNeedUserTest` - "ready for device testing" - on a tree that could not link
  resources at all, because `a.ps1 fk` was the only thing run and Kotlin compilation does not link
  resources. The status asserted something no build had ever supported.

**How to apply:** for each acceptance criterion, write down the preconditions it needs on the device
(this file exists, this folder has content, this pin is set, this branch fires) and confirm them
BEFORE reading the verdict. Seed the state if it is missing. Prefer a log line that reports a count
or an identity over one that reports mere completion - "re-addressed 2 cells" can be trusted where
"done" cannot. When only half the branches fired, the ticket stays open on the half that did not:
closing on the observed half is the same mistake with a logcat line attached. Parked as S1899.

Related: [[gate-fail-may-mean-never-ran]] is the mirror image - a FAIL that means the check never ran.
