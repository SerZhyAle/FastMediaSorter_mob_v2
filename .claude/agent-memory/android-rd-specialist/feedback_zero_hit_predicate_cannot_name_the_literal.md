---
name: zero-hit-predicate-cannot-name-the-literal
description: A "grep returns zero hits for X" predicate fails if the replacement text quotes X - describe the shape, never the banned literal
type: feedback
---

When a step's Verification says `Grep - <Literal> returns zero hits in <file>`, the rewritten content of that same file must not contain `<Literal>` anywhere - including inside the new verification predicate, a Step Log entry, or a rationale sentence explaining what was removed. Describe the forbidden shape instead: "no feature-local chart class is created anywhere in `app_v2/src`".

**Why:** hit twice in one session on S1446. Step 02.1 demanded `SignalChartView` and `attrs_signal_chart` reach zero hits in S1433's phase 05; the rewrite scored 1 hit each, purely because the replacement predicate I wrote quoted both names. The same trap sits in every phase file's `Grep for TODO(phase-NN) returns zero hits` criterion, which matches itself - so a naive repo grep reports "56 files with leftovers" when the real count is zero.

**The KDoc that explains an absence is the commonest source of the self-match.** On 2026-08-08 (S1433 phase 02) it fired twice more, both times against code I had just written. Step 02.1 demanded zero case-insensitive hits for `imei`/`iccid` in the model package, and the model's own KDoc names both to say why no field holds them. I fixed that predicate, then in step 02.2 authored a *fresh* one - zero hits for `ConnectivityManager.NetworkCallback` - and it scored 1 against the KDoc explaining why no second callback is registered. Writing the rule down did not stop me repeating it in the very next step.

**The same self-match bites on-device, where the counter is `adb shell logcat | grep`.** 2026-08-11, S1447: counting probe fires with `adb.ps1 shell -Cmd "logcat -d | grep -c 'S1447: dialog bound'"` grew by exactly one on every invocation *with no dialog on screen*, because adb writes the shell command line into the same logcat buffer the grep then reads - so each call counts all its own predecessors. It inflated a one-dialog delta to +2 and would have certified a probe that never fired. The `[u]` bracket trick does not repair it retroactively: the earlier command lines are already in the buffer and still match. Filter by **tag** instead - `logcat -d -s LifecycleDialogExtKt | grep -c 'bound to'` - because the polluting entries do not carry the app's tag; that counter was stable across repeated calls and matched the dialogs opened exactly.
**How to apply:** before trusting any on-device count, run the counter twice with nothing happening in between. A number that moves is counting itself.

**How to apply:**
- Make the predicate test a *declaration or a call*, not a word: `val [a-z]*(imei|iccid)` rather than `imei`; `registerNetworkCallback` (the call that would create the defect) rather than `ConnectivityManager.NetworkCallback` (the type any explanation must name).
- Run every newly authored zero-hit predicate against the file you just wrote, before marking the step done - authoring and violating can happen in the same edit.
- Before trusting a failing one, check whether the grep matched the criterion line or a Step Log rather than real content, and scope it to the region that matters.

See [[spec-tech-plan-quality]] and [[documented-invariant-is-a-claim]].
