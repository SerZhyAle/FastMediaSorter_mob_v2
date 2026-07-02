---
name: detekt-baseline-signature-resurface
description: Adding a constructor param to an already-baselined LongParameterList class resurfaces the finding under scoped detekt
type: feedback
---

Adding/removing a constructor (or function) parameter to a class that already has a baselined detekt finding (e.g. `LongParameterList`, `LongMethod`) **resurfaces that finding** even though it was "accepted" debt.

**Why:** detekt baseline entries are keyed by a full signature string (the whole param list). Change one param and the signature no longer matches the baseline entry -> detekt treats it as a NEW finding. Under `post-change.ps1 -ScopeToFile` (diff-scoped detekt), that file is in your diff, so the gate FAILs on a finding you didn't really introduce.

Also: a class sitting *exactly at* the threshold (10 params, threshold 10 = no finding) tips into a genuine new finding at +1.

**How to apply:** when you add a dependency/param to a stream/VM/manager/adapter constructor and the scoped detekt gate FAILs with `LongParameterList` on that file, don't re-baseline the whole project (captures others' WIP). Add a localized `@Suppress("LongParameterList")` on the class/constructor with a one-line WHY comment (matches how sibling adapters/VMs already tolerate it). Re-run with `:app_v2:detekt --rerun-tasks` - the report caches stale line numbers otherwise (see [[project_detekt_ktlint_import_layout]]).
