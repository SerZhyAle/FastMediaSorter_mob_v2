**Status:** Archived

# S0608 - SettingsActivity tab-sync posted runnable crashes on launch/recreate race

## Symptom

- `java.lang.IllegalStateException: Binding is only valid between onCreateView and onDestroyView`.
- Thread: main. Reproduced repeatedly while direct-launching `SettingsActivity` (and on config/recreate races).

## Evidence

- Stack:
  - `BaseActivity.getBinding(BaseActivity.kt:67)`
  - `SettingsActivity.setupConnectedTabs$lambda$6(SettingsActivity.kt:286)`
- [SettingsActivity.kt:285] posts `binding.tabLayout.post { .. binding.tabLayout .. }`. The posted runnable dereferences `binding` with no `_binding != null` / lifecycle guard. If the activity is destroyed/recreated before the frame runs, `getBinding()` throws.

## Fix applied

- `setupConnectedTabs` now captures the `tabLayout` view into a local val before `post {}`, so the posted runnable reads the captured reference instead of going through `binding` (which throws once the activity is torn down).
- Added an `isDestroyed` short-circuit at the top of the runnable: the recreated instance re-runs `setupConnectedTabs`, so syncing tab state on a dead instance is pointless.
- `syncConnectedTabState` only touches `tab.customView`, so no further `binding` access remains in the deferred path.
- Not built/verified yet (implemented under NO BUILD).

## Notes

- Found while investigating S0606 (3D-VR landscape). Unrelated to that ticket. No dedup match in catalog.

## Last Audit

**Date:** 2026-06-23
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 5 · WARN 0 · FAIL 0 · MANUAL 2 · EXEMPT 1

### Notes

- `SettingsActivity.setupConnectedTabs` (SettingsActivity.kt:292) captures `val tabLayout = binding.tabLayout` before `post {}`; the posted runnable (293-300) reads only the captured `tabLayout` - no `binding` dereference survives in the deferred path, removing the `IllegalStateException` root cause.
- `if (isDestroyed) return@post` short-circuit at the top of the runnable (294); `syncConnectedTabState` (337-340) touches only `tab.customView`, no further `binding` access.
- Statically type-correct: `isDestroyed` is the inherited Activity API (no new import); all `tabLayout.*` calls are TabLayout members.
- Debug-tag invariant PASS: zero `Timber.d("S0608:` tags (status Implemented).
- FEATURES trilingual EXEMPT: internal crash fix, no user-visible showcase change.

### Manual / on-device

- [ ] Compile-verify (spec's "NO BUILD" caveat - the change is trivially compile-safe but not built in this static audit).
- [ ] Direct-launch `SettingsActivity` and force a config/recreate race: the connected-tabs sync no longer throws `IllegalStateException: Binding is only valid between onCreateView and onDestroyView`.
