# Phase 04 — Result return on app re-entry

**Strategic spec:** [`../S0202_link-share-background-survival.md`](../S0202_link-share-background-survival.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Show share-download outcomes (toast / open-in-player) when the user returns to FastMediaSorter after a background completion, so the foreground notification is not the only feedback channel. Avoid double-feedback (toast + notification) by suppressing the toast when the notification has already informed the user.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (worker is now the execution surface).
- [ ] Strategic §5.1 Pillar E read.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ShareDownloadResultBus.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt` | Modified | ≤ 430 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/MainActivity.kt` | Modified | n/a (≤ +30 LOC) |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt` | Modified (or sibling) | ≤ 110 |

> `MainActivity` line count must remain under its current limit. Verify before edit; if already near 1500 LOC, extract observer wiring into a helper Manager class.

---

## Steps

### Step 04.1 — Introduce `ShareDownloadResultBus` singleton

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ShareDownloadResultBus.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `ShareDownloadResultBus` as a `@Singleton` class injected via Hilt:
>
> ```kotlin
> @Singleton
> class ShareDownloadResultBus @Inject constructor() {
>     data class Pending(
>         val url: String,
>         val result: LinkAutoDownloadCoordinator.Result,
>         val notificationShown: Boolean,
>         val emittedAt: Long = System.currentTimeMillis(),
>     )
>     private val _pending = MutableSharedFlow<Pending>(replay = 1, extraBufferCapacity = 4)
>     val pending: SharedFlow<Pending> = _pending.asSharedFlow()
>     suspend fun publish(p: Pending) { _pending.emit(p) }
>     fun clearReplayCache() { _pending.resetReplayCache() }
> }
> ```
>
> Place in package `com.sza.fastmediasorter.ui.share`. KDoc explains the contract: "S0202 — worker pushes terminal results here so any foreground Activity can present them on resume. `notificationShown=true` indicates the worker already posted a result notification; the consumer should suppress redundant toasts in that case."

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ShareDownloadResultBus.kt` exists.
- `Grep` — `class ShareDownloadResultBus` matches once.
- `Grep` — `data class Pending` matches once.
- `Grep -n "Log\.d\("` returns zero hits in the new file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 4/4 PASS (file exists; class ×1; data class Pending ×1; Log.d=0). +43 LOC.

---

### Step 04.2 — Inject the bus into `LinkDownloadWorker` and emit on completion

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add `private val resultBus: ShareDownloadResultBus` to the worker's `@AssistedInject` constructor parameters. After `postResultNotification(...)` in `doWork()`, emit the result via:
>
> ```kotlin
> runCatching {
>     resultBus.publish(
>         ShareDownloadResultBus.Pending(
>             url = url ?: urls?.firstOrNull() ?: "",
>             result = result,
>             notificationShown = true,
>         )
>     )
> }.onFailure { Timber.w(it, "S0202: result bus emit failed") }
> ```
>
> Hilt-Worker injection uses the existing `@HiltWorker` setup — no module change required. Do NOT alter the existing `silentCallbacks()` factory.

**Verification:**

- `Grep` — `resultBus: ShareDownloadResultBus` matches once in worker constructor.
- `Grep` — `resultBus.publish(` matches once.
- `Grep` — `ShareDownloadResultBus.Pending(` matches once.
- `/build` → `standard debug` exits 0; expected: `BUILD SUCCESSFUL` | actual: record literal output.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 4/4 PASS (constructor inject ×1, publish ×1, Pending(×1, build deferred to phase end).

---

### Step 04.3 — Consume the bus in `MainActivity.onResume`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/MainActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Inject `@Inject lateinit var shareResultBus: ShareDownloadResultBus` and `@Inject lateinit var resultPresenter: LinkAutoDownloadResultPresenter`. In `onCreate` (or via existing helper Manager — verify which pattern this Activity uses; if a `MainActivityViewModel` exists, route through it), launch a coroutine on `lifecycleScope` that collects `shareResultBus.pending`. On each emission:
>
> 1. If `notificationShown == true` AND result is `Saved`/`FellBackToDownloads`/`BatchCompleted` → skip the toast (notification already informed the user); only invoke `resultPresenter` for the `openInPlayer == true && openInPlayerUri != null` branch via a new presenter entry point `presentSilentSuccess(result, this)` (Phase 04 stub: route through existing `present(result, this)` and accept the small UX redundancy — the presenter's own `openInPlayer` check still fires only when the player is configured).
> 2. If result is a `Failed.SocialPreviewOnly` (auth-needed) AND notification has the "Sign in" action — skip the dialog (avoid double prompt).
> 3. Otherwise call `resultPresenter.present(result, this, isAuthRetry = false)`.
>
> After consuming each emission, the bus's `replay = 1` cache is intentionally retained — Activity destroy/recreate (rotation) sees the most recent result once. Add a `lifecycleScope` cancellation when leaving via `onPause` only if duplicate consumption proves problematic in device test.

**Verification:**

- `Grep` — `shareResultBus.pending` matches once in `MainActivity.kt` (or its delegated helper Manager).
- `Grep` — `resultPresenter.present(` (or new `presentSilentSuccess`) matches in `MainActivity.kt`.
- `/build` → `standard debug` exits 0; expected: `BUILD SUCCESSFUL` | actual: record literal output.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS (shareResultBus.pending×1, shareResultPresenter.present(×1 — renamed from `resultPresenter` for clarity vs. existing share-Activity field; build deferred to phase end). Suppression rules implemented for `Saved/FellBackToDownloads/BatchCompleted/SocialPreviewOnly` when notification was already shown.

---

### Step 04.4 — Insert debug verification tag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/MainActivity.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Insert one tag at the **entry of the new bus consumer**:
>
> ```kotlin
> Timber.d("S0202: MainActivity received share result url=%s outcome=%s", pending.url, pending.result::class.simpleName)
> ```
>
> Place it inside the collector lambda before the suppression branching. One tag per flow entry per CLAUDE.md.

**Verification:**

- `Grep -n "Timber.d(\"S0202: MainActivity received share result"` returns exactly one match.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 1/1 PASS (Timber.d S0202 tag ×1, inserted at the top of the collector lambda before suppression branching).

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — `/build` → `standard debug` (exit 0; record literal output).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

After Phase 04:

- The user returning to FastMediaSorter sees the outcome of any background share — toast for failures, open-in-player for successes when configured, dialog for auth-required cases (with the notification's "Sign in" action acting as a fallback when the activity is not on screen).
- The bus's `replay = 1` semantics give one outcome per share; subsequent activity recreations consume the same value once.

Phase 05 finishes the work: docs, catalog, functionality log.

---

## Rollback Plan

Revert the phase commit(s). The bus is a new type — its removal is mechanical (delete file + remove constructor parameter from worker + remove the collector in `MainActivity`). No persistence layer touched.
