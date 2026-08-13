# S1085 - WelcomeActivity dead fragmentContainerWelcome + WelcomeCompleteListener

**Status:** Archived
**Priority:** 30
**Tier:** 5
**Date:** 2026-07-18
**Source:** parked from S0404 Phase 08 audit (2026-07-17)

## 0. Raw capture

Auditor finding (S0404 Phase 08, dimension: UI/lifecycle). Rule 20 dead-weight, out of S0404 scope.

**Symptom:** `WelcomeActivity` carries never-reachable code. `fragment_container_welcome` (id `@+id/fragmentContainerWelcome`) is declared `0dp x 0dp`, `visibility="gone"`, and is never populated anywhere in the app - the only two sites that instantiate `PermissionsManagementFragment` target `android.R.id.content` from `SettingsActivity`, never `WelcomeActivity`. So `binding.fragmentContainerWelcome.isVisible` is unconditionally `false`, and the branch in `handleOnBackPressed()` that calls `completeWelcomeFlow()` for the fragment case is dead; `WelcomeActivity`'s `PermissionsManagementFragment.WelcomeCompleteListener` implementation (`onWelcomeComplete()`) is consequently also dead.

**Evidence:**
- `app_v2/src/main/res/layout/activity_welcome.xml` - `fragment_container_welcome` 0dp/gone.
- `ui/welcome/WelcomeActivity.kt` - the `WelcomeCompleteListener` impl + the `fragmentContainerWelcome.isVisible` back-press branch.
- Usage of `PermissionsManagementFragment(` only in `ui/settings/helpers/GeneralSettingsPermissionsHelper.kt` and `ui/settings/fragments/GeneralSettingsFragment.kt`, both `android.R.id.content`.

**Note (S0404 side effect):** this ticket's audit changed the Welcome launcher-toggle flow to `markAsHomeCandidate()` (no role dialog from the finishing Activity). The Back-drops-toggle behaviour is intentional ("nothing committed until Finish") but asymmetric with the language/theme pickers, which persist immediately - worth an owner note when this is picked up.

**Why its own ticket:** dead-code removal needs a verification pass that nothing (incl. TV/remote ESCAPE routing at the `onBackPressedDispatcher`) depends on the vestigial container before deleting.

## 1. Next step

`/spec-all S1085`. Confirm reachability is truly zero, remove the container + listener, verify Back/ESCAPE flows on device.
