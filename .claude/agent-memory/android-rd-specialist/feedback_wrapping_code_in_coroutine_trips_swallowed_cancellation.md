---
name: wrapping-code-in-coroutine-trips-swallowed-cancellation
description: Moving an existing synchronous body into coroutineScope.launch makes its untouched broad catch blocks fail the swallowed-cancellation gate (S1363) - add the rethrow arms in the same edit
type: feedback
---

When an off-main-thread fix wraps an existing synchronous function body in `coroutineScope.launch { .. }` or `withContext(..)`, add `catch (e: CancellationException) { throw e }` as the **first** arm of every broad `catch (Throwable)` / `catch (Exception)` chain that ended up inside the coroutine - in the same edit, before running `post-change.ps1`. Import is `kotlinx.coroutines.CancellationException` (the repo's convention).

**Why:** the `swallowed-cancellation` dimension of `assert-neuroslop.ps1` (S1363) scores a **per-file delta**, and its notion of "new" is "broad catch that is now in coroutine code". Catch blocks you only re-indented therefore count as newly introduced, even though their text is unchanged and the ticket never meant to touch them. Observed 2026-08-12 on S1609: five untouched catch chains in `BrowseCameraCaptureManager` turned into `+3 new occurrences` and failed the closure, costing a full 47 s re-run of every gate. detekt itself stays green, so the scoped detekt preflight gives no warning first.

**How to apply:** the trigger is the shape of the edit, not the file - any "move this to `Dispatchers.IO`" ticket in the S1579/S1593/S1608/S1609 family. Scan the wrapped region for broad catches before closing. The rethrow arm is also correct on its merits, so there is no judgement call about whether to add it. See [[detekt-scoped-gate-surfaces-untouched-debt]], [[write-detekt-clean-first-time]].
