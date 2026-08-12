---
name: audit-fixes-need-their-own-round
description: Re-run the auditor on the fixes themselves - in this repo each round of P1 fixes has introduced a fresh defect
metadata:
  type: feedback
---

After applying fixes from a code audit, send the same auditor a second (and third) round describing exactly what changed, and tell it to assume the fixes introduced a new defect. Do not treat "fixes applied, tests green" as the end of an audit.

**Why:** measured on S1175 (2026-08-11), three rounds against the same subagent:
- Round 1 found 4 P1s in a fresh data path.
- Round 2 confirmed all four fixed and found a **new P1 created by one of the fixes** - the cache eviction added to bound directory growth deleted the offline fallback tile on the failure path, which was strictly worse than the unbounded cache it replaced.
- Round 3 confirmed that one fixed and found the previous round's mutex fix had removed the only rate limit on a geocoder call, plus a **regression test that passed without ever reaching the path it named** (it reused a warm repository instance, so the cache short-circuited before the code under test).

Two of three regressions lived in the class the auditor kept flagging as untested. A green suite is not evidence the fix is right when the suite was written by whoever wrote the fix.

**How to apply:** use `SendMessage` to resume the same agent rather than spawning a fresh one - it already holds the file context and its own prior findings, so the second round costs a fraction of the first. In the message, list each fix against its finding id, state what you verified and with which command, and name three or four specific things to attack ("can X delete the file Y is about to return"). Ask it to state plainly whether the round is clean. Stop when a round returns no P0/P1 and its remaining findings are ones you have consciously accepted. When it names an untested class twice, write the test - do not answer with a third round of prose. A vacuous test is the failure mode to look for: assert the call count of the boundary the test exists to exercise (`coVerify(exactly = n)`), or it can pass through a path that never touches the code under test.

Related: [[documented-invariant-is-a-claim]], [[verify-subagent-build-failures]], [[no-scaffolding-as-done]].
