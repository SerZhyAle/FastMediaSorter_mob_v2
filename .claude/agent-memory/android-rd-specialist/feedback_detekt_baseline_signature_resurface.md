---
name: detekt-baseline-signature-resurface
description: Adding a constructor param to an already-baselined LongParameterList class resurfaces the finding under scoped detekt
type: feedback
---

Adding/removing a constructor (or function) parameter to a class that already has a baselined detekt finding (e.g. `LongParameterList`, `LongMethod`) **resurfaces that finding** even though it was "accepted" debt.

**Why:** detekt baseline entries are keyed by a full signature string (the whole param list). Change one param and the signature no longer matches the baseline entry -> detekt treats it as a NEW finding. Under `post-change.ps1 -ScopeToFile` (diff-scoped detekt), that file is in your diff, so the gate FAILs on a finding you didn't really introduce.

Also: a class sitting *exactly at* the threshold (10 params, threshold 10 = no finding) tips into a genuine new finding at +1.

**How to apply (preferred - avoid the signature change entirely):** discovered 2026-07-06 on S0962. I first threaded a gate flag + callback (`vrCinemaAvailable`, `onOpenInVrCinema`) through `BrowseFileOverflowMenuManager.showFor` (already 20 params, baselined `LongParameterList` + `CyclomaticComplexMethod`) AND up through `BrowseManagerInitializer`'s 40-param constructor + `BrowseActivity`. Adding those params resurfaced BOTH baselined findings on both god-methods, plus a `ComplexCondition` from a 4-term `if`. Fix that worked cleanly: **inject the new small helper (`BrowseVrCinemaLaunchManager`, @ActivityScoped) DIRECTLY into the menu manager's own tiny constructor (1->2 params, no threshold), and add a body-only branch to `showFor` that reads `helper.isAvailable` and calls `helper.launch(file)`.** The god-method SIGNATURE is unchanged -> its baseline entry still matches -> `LongParameterList`/`CyclomaticComplexMethod` stay suppressed even though the body grew. It also reverted the BMI + Activity edits (blast radius 4 files -> 2). Keep any new `if` to <=3 boolean sub-expressions or `ComplexCondition` fires as a genuine NEW finding (a directory is never `MediaType.VIDEO`, so I dropped the redundant `!isDirectory` term to get from 4 to 2).

**Do NOT reach for `@Suppress` first:** CLAUDE.md Rule 19 forbids adding `@Suppress` to a method that already carries a baselined finding - it shifts the baseline signature and surfaces a *different* finding (e.g. `FunctionNaming`). Prefer the inject-into-helper route above; re-baselining the whole project is also wrong (captures others' WIP). If a stale report shows the OLD signature after your edit, force a fresh report with `:app_v2:detekt --rerun-tasks` and check the report mtime (see [[project_detekt_baseline_hand_edit_daemon_stale]], [[project_detekt_ktlint_import_layout]]).
