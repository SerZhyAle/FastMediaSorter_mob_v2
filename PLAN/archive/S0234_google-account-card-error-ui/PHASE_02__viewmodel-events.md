# Phase 02 — ViewModel: one-shot Error event channel

**Strategic spec:** [`../S0234_google-account-card-error-ui.md`](../S0234_google-account-card-error-ui.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-17
**Completed:** 2026-05-17

---

## Objective

Add a one-shot event channel to `GoogleAccountSettingsViewModel` that emits exactly once per transition INTO `PrimaryGoogleAccountState.Error`. Consumed by the helper in Phase 03 to trigger the `ErrorDialog`. No state-flow change; rotation does not re-emit.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/GoogleAccountSettingsViewModel.kt` | Modified | ≤ 160 |

---

## Steps

### Step 02.1 — Define `Event` sealed interface inside the ViewModel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/GoogleAccountSettingsViewModel.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a `sealed interface Event` declaration inside `GoogleAccountSettingsViewModel` (top-level inside the class body, after the `UiState data class`). One variant for now:
>
> ```kotlin
> sealed interface Event {
>     data class ShowSignInError(val reason: IdentityFailureReason) : Event
> }
> ```
>
> Import `com.sza.fastmediasorter.domain.identity.IdentityFailureReason`.

**Verification:**

- `Grep -n` — `sealed interface Event` matches exactly once in `GoogleAccountSettingsViewModel.kt`.
- `Grep -n` — `data class ShowSignInError(val reason: IdentityFailureReason)` matches exactly once.
- `Grep -n` — `import com.sza.fastmediasorter.domain.identity.IdentityFailureReason` matches exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 3/3 PASS (`expected: 1 | actual: 1` each). Files: GoogleAccountSettingsViewModel.kt (+5 LOC). Dev log deferred to 02.2 (same file).

---

### Step 02.2 — Add Channel + public Flow + emit on Error transition

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/GoogleAccountSettingsViewModel.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Inside `GoogleAccountSettingsViewModel`:
>
> 1. Add a private `Channel<Event>(Channel.BUFFERED)` field named `_events`.
> 2. Expose it as `val events: Flow<Event> = _events.receiveAsFlow()`.
> 3. In the `init` block (add one if absent), launch a coroutine on `viewModelScope` that collects `identityRepository.state` and tracks the previous emission. When the previous state is NOT an `Error` AND the current state IS an `Error`, send `Event.ShowSignInError(currentState.cause)` to `_events`.
>
> Implementation hint:
>
> ```kotlin
> private val _events = Channel<Event>(Channel.BUFFERED)
> val events: Flow<Event> = _events.receiveAsFlow()
>
> init {
>     viewModelScope.launch {
>         var previous: PrimaryGoogleAccountState? = null
>         identityRepository.state.collect { current ->
>             if (previous !is PrimaryGoogleAccountState.Error && current is PrimaryGoogleAccountState.Error) {
>                 _events.send(Event.ShowSignInError(current.cause))
>             }
>             previous = current
>         }
>     }
> }
> ```
>
> Imports required: `kotlinx.coroutines.channels.Channel`, `kotlinx.coroutines.flow.Flow`, `kotlinx.coroutines.flow.receiveAsFlow`, `kotlinx.coroutines.flow.collect`.
>
> Rationale (informational, for the reader — do not paste into code): the predicate "prev != Error && current == Error" emits exactly once per error episode and never re-emits on subsequent `Error → Error` ticks (e.g. if the identity layer ever sends two Error values back-to-back). Rotation does not re-collect `identityRepository.state` from scratch inside this ViewModel — `viewModelScope` survives configuration changes — so `previous` is preserved and no duplicate emission occurs.

**Verification:**

- `Grep -n` — `private val _events = Channel<Event>(Channel.BUFFERED)` matches exactly once.
- `Grep -n` — `val events: Flow<Event> = _events.receiveAsFlow()` matches exactly once.
- `Grep -n` — `_events.send(Event.ShowSignInError(current.cause))` matches exactly once.
- `Grep -n` — `import kotlinx.coroutines.channels.Channel` matches exactly once.
- `Grep -n` — `import kotlinx.coroutines.flow.receiveAsFlow` matches exactly once.
- `Grep -n` — `Log\.d\(` returns zero hits in this file (Timber-only rule).

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 6/6 PASS (`expected: 1 | actual: 1` for Channel/Flow/send/imports; `expected: 0 | actual: 0` for Log.d). Files: GoogleAccountSettingsViewModel.kt (+19 LOC). Dev log to be written after step 02.3 build.

---

### Step 02.3 — Build gate (compile-only)

**Files:** none modified
**Depends on:** Step 02.2

**Prompt for developer:**

> Compile the standard debug variant via `.\a.ps1 bd` (or `/build` skill). Expected: no compile errors. Pre-existing unit-test failures are out of scope (see `feedback_build_pre_existing_test_failures`); only the compile gate matters here.

**Verification:**

- `Bash` — build exits 0 (`expected: 0 | actual: 0`). On non-zero: read failing lines, fix, retry up to MAX_BUILD_RETRIES=3.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — `./build-debug.PS1` exited 0. BUILD SUCCESSFUL in 1m 28s. No compile errors introduced by Channel/Flow additions.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — `./build-debug.PS1` exit 0.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `GoogleAccountSettingsViewModel.events: Flow<Event>` is collectable.
- Each transition INTO `Error` produces exactly one `Event.ShowSignInError(reason)`.
- Phase 03 collects this flow in the helper and shows the `ErrorDialog`.

---

## Rollback Plan

Revert the ViewModel change — no consumer yet (`events` flow is unused until Phase 03), so the rollback is non-destructive.
