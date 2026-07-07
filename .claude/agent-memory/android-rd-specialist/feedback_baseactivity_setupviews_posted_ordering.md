---
name: baseactivity-setupviews-posted-ordering
description: BaseActivity defers setupViews()/attach() to binding.root.post{}, so onCreate-body restore logic sees un-attached managers
type: feedback
---

`BaseActivity.onCreate` defers `setupViews()` (and everything it wires, e.g. `permissionsManager.attach()` / `enableAllManager.attach()`) to `binding.root.post { .. }` so the first frame renders fast (BaseActivity.kt ~145-155, documented at ~73-75). Any code an Activity runs in its own `onCreate` body after `super.onCreate()` - including manual `manager.onRestoreInstanceState(savedInstanceState)` calls - therefore executes BEFORE `attach()`. Cross-manager references set only in `attach()` are still null at that point.

**Why:** S0910's Phase 1 "fix" reattached a lost completion callback inside `enableAllManager.onRestoreInstanceState()`, called from `WelcomeActivity.onCreate`. On real process death (2026-07-07 device test) the reattach was a null-safe no-op because `enableAllManager.permissionsManager` was still null (attach() had not run yet), so the probe never fired and the enable-all sequence stalled on the Welcome page. The spec had assumed setupViews runs inside super.onCreate before onRestoreInstanceState - false because of the posted defer.

**How to apply:** When writing any onSaveInstanceState/onRestoreInstanceState recreation-restore fix in a BaseActivity subclass, do NOT depend on managers wired in setupViews()/attach() from the onCreate body. Put restore logic that needs an attached manager either inside `attach()` itself (guarded by the just-restored flags) or in `onResumeWithViews()` (fires only after setupViews/observeData complete). This ordering trap applies to every recreation path (rotation, Don't-keep-activities, process death), not just process death.
