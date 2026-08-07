---
name: blocker-unblocks-at-needusertest
description: A blocking ticket reaching BlockNeedUserTest is enough to release its dependents from BlockByOtherTask - never make a dependent wait for Verified
metadata:
  type: feedback
---

A dependent ticket comes off `BlockByOtherTask` as soon as its blocker reaches **`BlockNeedUserTest`** (or anything higher - `Implemented`, `Verified`). Never write a status note that makes a dependent wait for the blocker to be `Verified`.

**Why:** owner ruling 2026-08-07, made while triaging S1403 (waiting on S1288). `BlockNeedUserTest` means the code is written and in the tree - only the owner's device pass is left. Everything a dependent needs to be specced, planned and built is already readable in the working tree at that point, so holding it costs a whole device-test round trip per link in the chain. Device passes arrive in batches (`/spec-sweep`), so a Verified-gate serialises the queue against the slowest human step.

**How to apply:**
- Writing a `-StatusNote` for a new `BlockByOtherTask`: the resume condition is "unblocks when `<blocker>` reaches BlockNeedUserTest", never "once `<blocker>` is Verified".
- Triaging the queue: any `BlockByOtherTask` whose blocker is at `BlockNeedUserTest` or above is stale - transition it out.
- The rule only covers *ticket* dependencies. It does not override a block that exists for another reason - an owner decision still open, or hardware that is not attached. Those move to `BlockQuestions` / `BlockExternal`, they do not become schedulable.
- Corollary worth checking every time: blockers named in a note also go stale when the blocker is `Verified` or `Archived`. On 2026-08-07 both S1175 (blocker `Verified`) and S1319 (blocker `Archived`) had been sitting blocked for nothing.

Related: [[verify-spec-id-before-pipeline]], [[blockneedusertest-status-before-gate]].
