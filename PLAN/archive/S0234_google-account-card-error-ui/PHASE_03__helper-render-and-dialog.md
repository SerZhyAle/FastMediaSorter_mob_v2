# Phase 03 — Helper: per-reason render + ErrorDialog + click telemetry

**Strategic spec:** [`../S0234_google-account-card-error-ui.md`](../S0234_google-account-card-error-ui.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-17
**Completed:** 2026-05-17

---

## Objective

Update `GoogleAccountSettingsHelper` to:

1. Render `PrimaryGoogleAccountState.Error` with per-reason summary and per-reason CTA (Decision D1).
2. Collect `viewModel.events` and show `ErrorDialog` once per Error transition (Decision D2).
3. Wrap action and diagnostics-toggle clicks with `UserActionLogger.wrapClickListener` (strategic Goal 4).
4. For `PlayServicesOutdated`, route the action button (and the dialog's positive button) to open Google Play Services in the Play Store; for all other reasons, retry `signInPrimary`.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GoogleAccountSettingsHelper.kt` | Modified | ≤ 240 |

> No layout XML touched — no landscape parity concern.

---

## Steps

### Step 03.1 — Add `errorReasonStrings(reason)` mapping helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GoogleAccountSettingsHelper.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a `private fun errorReasonStrings(reason: IdentityFailureReason): Pair<Int, Int>` to `GoogleAccountSettingsHelper`. Returns `(summaryStringRes, ctaStringRes)`. Mapping per Decision D1:
>
> ```kotlin
> private fun errorReasonStrings(reason: IdentityFailureReason): Pair<Int, Int> = when (reason) {
>     IdentityFailureReason.PlayServicesOutdated ->
>         R.string.s0234_card_state_error_play_services_outdated_summary to
>             R.string.s0234_card_state_error_play_services_outdated_cta
>     IdentityFailureReason.NetworkError ->
>         R.string.s0234_card_state_error_network_summary to
>             R.string.s0234_card_state_error_network_cta
>     IdentityFailureReason.UserCancelled ->
>         R.string.s0234_card_state_error_user_cancelled_summary to
>             R.string.s0234_card_state_error_user_cancelled_cta
>     IdentityFailureReason.CctUnavailable ->
>         R.string.s0234_card_state_error_cct_unavailable_summary to
>             R.string.s0234_card_state_error_cct_unavailable_cta
>     IdentityFailureReason.UnknownError ->
>         R.string.s0234_card_state_error_unknown_summary to
>             R.string.s0234_card_state_error_unknown_cta
> }
> ```
>
> Import `com.sza.fastmediasorter.domain.identity.IdentityFailureReason`.

**Verification:**

- `Grep -n` — `private fun errorReasonStrings(reason: IdentityFailureReason)` matches exactly once.
- `Grep -n` — `R.string.s0234_card_state_error_play_services_outdated_summary` matches at least once.
- `Grep -n` — `R.string.s0234_card_state_error_unknown_cta` matches at least once.
- `Grep -n` — `import com.sza.fastmediasorter.domain.identity.IdentityFailureReason` matches exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 4/4 PASS. Files: GoogleAccountSettingsHelper.kt (+19 LOC). Dev log deferred to step 03.4 (same file across the phase).

---

### Step 03.2 — Per-reason render for `Error` state and per-reason action click

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GoogleAccountSettingsHelper.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> 1. Replace the existing `is PrimaryGoogleAccountState.Error -> { ... }` branch in `render(..)` with:
>
> ```kotlin
> is PrimaryGoogleAccountState.Error -> {
>     val (summaryRes, ctaRes) = errorReasonStrings(state.cause)
>     tvSummary.setText(summaryRes)
>     tvEmail.visibility = View.GONE
>     ivAvatar.visibility = View.GONE
>     progress.visibility = View.GONE
>     btnAction.setText(ctaRes)
>     btnAction.isEnabled = true
> }
> ```
>
> 2. Replace the `is PrimaryGoogleAccountState.Error -> viewModel.signInPrimary(..)` branch in `onActionClicked(..)` with:
>
> ```kotlin
> is PrimaryGoogleAccountState.Error -> handleErrorAction(currentState.cause)
> ```
>
> 3. Add a new private method:
>
> ```kotlin
> private fun handleErrorAction(reason: IdentityFailureReason) {
>     when (reason) {
>         IdentityFailureReason.PlayServicesOutdated -> openPlayServicesInPlayStore()
>         else -> viewModel.signInPrimary(fragment.requireActivity())
>     }
> }
>
> private fun openPlayServicesInPlayStore() {
>     val context = fragment.requireContext()
>     val marketUri = android.net.Uri.parse("market://details?id=com.google.android.gms")
>     val webUri = android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.gms")
>     val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, marketUri).apply {
>         flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
>     }
>     try {
>         context.startActivity(intent)
>     } catch (e: android.content.ActivityNotFoundException) {
>         Timber.w(e, "GoogleAccountSettingsHelper: Play Store not available, falling back to web")
>         try {
>             context.startActivity(
>                 android.content.Intent(android.content.Intent.ACTION_VIEW, webUri).apply {
>                     flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
>                 }
>             )
>         } catch (e2: Exception) {
>             Timber.e(e2, "GoogleAccountSettingsHelper: web fallback for Play Services also failed")
>         }
>     }
> }
> ```
>
> Strings pass `docs/COMMUNICATION_POLICY.md` §6 — strings themselves were vetted in Phase 01; this step adds no new copy.

**Verification:**

- `Grep -n` — `val (summaryRes, ctaRes) = errorReasonStrings(state.cause)` matches exactly once.
- `Grep -n` — `is PrimaryGoogleAccountState.Error -> handleErrorAction(currentState.cause)` matches exactly once.
- `Grep -n` — `private fun handleErrorAction(reason: IdentityFailureReason)` matches exactly once.
- `Grep -n` — `private fun openPlayServicesInPlayStore()` matches exactly once.
- `Grep -n` — `market://details?id=com.google.android.gms` matches exactly once.
- `Grep -n` — `Log\.d\(` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 6/6 PASS. Files: GoogleAccountSettingsHelper.kt (+~30 LOC). Dev log deferred to step 03.4.

---

### Step 03.3 — Collect `viewModel.events` and show `ErrorDialog`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GoogleAccountSettingsHelper.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Inside `bind(cardView)`, add a second collector — alongside the existing `viewModel.uiState.collect { .. }` — that collects `viewModel.events` and routes each event to `ErrorDialog.show(..)`:
>
> ```kotlin
> fragment.viewLifecycleOwner.lifecycleScope.launch {
>     fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
>         viewModel.events.collect { event ->
>             when (event) {
>                 is GoogleAccountSettingsViewModel.Event.ShowSignInError -> showSignInErrorDialog(event.reason)
>             }
>         }
>     }
> }
> ```
>
> Add a private method:
>
> ```kotlin
> private fun showSignInErrorDialog(reason: IdentityFailureReason) {
>     val context = fragment.context ?: return
>     val (summaryRes, ctaRes) = errorReasonStrings(reason)
>     val message = context.getString(summaryRes)
>     val details = context.getString(R.string.s0234_card_state_error_diag_details_prefix, reason.name)
>     com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
>         context = context,
>         title = context.getString(R.string.s0234_error_dialog_title),
>         message = message,
>         details = details,
>         actionButtonText = context.getString(ctaRes),
>         onActionClick = { handleErrorAction(reason) }
>     )
> }
> ```
>
> The second collector lives in its own `lifecycleScope.launch` block so neither collector starves the other.

**Verification:**

- `Grep -n` — `viewModel.events.collect` matches exactly once.
- `Grep -n` — `GoogleAccountSettingsViewModel.Event.ShowSignInError` matches exactly once.
- `Grep -n` — `private fun showSignInErrorDialog(reason: IdentityFailureReason)` matches exactly once.
- `Grep -n` — `com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(` matches exactly once.
- `Grep -n` — `R.string.s0234_error_dialog_title` matches exactly once.
- `Grep -n` — `R.string.s0234_card_state_error_diag_details_prefix` matches exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 6/6 PASS. Files: GoogleAccountSettingsHelper.kt (+~26 LOC). Dev log deferred to step 03.4.

---

### Step 03.4 — Click telemetry: wrap action + diagnostics buttons

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GoogleAccountSettingsHelper.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Replace the two `setOnClickListener { .. }` calls in `bind(cardView)` with `UserActionLogger.wrapClickListener("<name>", "Settings", ..)`:
>
> ```kotlin
> btnDiagToggle.setOnClickListener(
>     com.sza.fastmediasorter.utils.UserActionLogger.wrapClickListener(
>         buttonName = "GoogleAccountDiagnosticsToggle",
>         context = "Settings",
>         listener = View.OnClickListener { viewModel.toggleDiagnostics() }
>     )
> )
>
> btnAction.setOnClickListener(
>     com.sza.fastmediasorter.utils.UserActionLogger.wrapClickListener(
>         buttonName = "GoogleAccountAction",
>         context = "Settings",
>         listener = View.OnClickListener { onActionClicked(currentState) }
>     )
> )
> ```
>
> Add `import com.sza.fastmediasorter.utils.UserActionLogger` if you prefer a non-FQN call site — either form satisfies the verification.
>
> Build the standard debug variant via `.\a.ps1 bd` (or `/build` skill). Expected: no compile errors.

**Verification:**

- `Grep -n` — `UserActionLogger.wrapClickListener` matches exactly twice in `GoogleAccountSettingsHelper.kt`.
- `Grep -n` — `"GoogleAccountAction"` matches exactly once.
- `Grep -n` — `"GoogleAccountDiagnosticsToggle"` matches exactly once.
- `Bash` — build exits 0 (`expected: 0 | actual: 0`). On non-zero: read failing lines, fix, retry up to MAX_BUILD_RETRIES=3.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Static verification 3/3 PASS (`UserActionLogger.wrapClickListener` ×2, button-name strings ×1 each). Build `./build-debug.PS1` exited 0 (BUILD SUCCESSFUL in 40s). Files: GoogleAccountSettingsHelper.kt (+~14 LOC).

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles — `./build-debug.PS1` exit 0 (40s).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- All behavioural changes are in place. Phase 04 is administrative cleanup only.
- `Timber.d("S0234: ...")` debug tags are inserted by `/spec-dev` when it flips status to `BlockNeedUserTest` at the end of the pipeline — not in this phase.

---

## Rollback Plan

Revert all changes to `GoogleAccountSettingsHelper.kt`. The strings (Phase 01) and the ViewModel events (Phase 02) become unused but compile cleanly — safe to leave in place during rollback iteration.
