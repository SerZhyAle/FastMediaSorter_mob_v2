# Phase 03 — Auth Account Cleanup Once

**Strategic spec:** [`../S0211_webview-auth-account-dedup-and-loop-prevention.md`](../S0211_webview-auth-account-dedup-and-loop-prevention.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Run a one-shot, idempotent cleanup of duplicate auth accounts at first launch after install. For each host, group active accounts by computed identity; in each group with >1 entries, keep the record with the highest `lastUsedAt` (falling back to `savedAt`) and delete the rest. Records without a computable identity are left untouched. A DataStore marker prevents re-execution.

---

## Prerequisites

- [ ] Phase 01 ✅ Done — `AccountIdentityExtractor` available.
- [ ] Phase 02 ✅ Done — upsert path is live, so new duplicates won't be created during user testing of this phase.
- [ ] Working tree clean or on the active DEBUG branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/EncryptedCookieStore.kt` | Modified | ≤ 430 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DedupAuthAccountsUseCase.kt` | New | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt` | Modified | ≤ 720 (current 709; backup if projected ≥ 1500) |

---

## Steps

### Step 03.1 — Expose stored cookies per account for cleanup

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/EncryptedCookieStore.kt`
**Depends on:** —

**Prompt for developer:**

> The store already has a public `loadForAccount(host, accountId): List<HttpCookie>`. Cleanup uses that directly — no new API needed. Confirm by reading the file: if `loadForAccount` is public (it is), this step is a no-op verification. If not, expose it.
>
> No code change is expected. This step exists only to lock in the dependency contract for Step 03.2.

**Verification:**

- `Grep -n "fun loadForAccount\(host: String, accountId: String\): List<HttpCookie>" EncryptedCookieStore.kt` — exactly one match, public (no `private` keyword before).

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — Verification 1/1 PASS. No file change (contract confirmation). `loadForAccount` is public at line 107.

---

### Step 03.2 — Create `DedupAuthAccountsUseCase`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DedupAuthAccountsUseCase.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create the new use case, modelled on `MigrateS0059UseCase`:
>
> ```kotlin
> package com.sza.fastmediasorter.domain.usecase
>
> import androidx.datastore.core.DataStore
> import androidx.datastore.preferences.core.Preferences
> import androidx.datastore.preferences.core.booleanPreferencesKey
> import androidx.datastore.preferences.core.edit
> import com.sza.fastmediasorter.data.link.auth.AccountIdentityExtractor
> import com.sza.fastmediasorter.data.link.cookie.EncryptedCookieStore
> import kotlinx.coroutines.Dispatchers
> import kotlinx.coroutines.flow.first
> import kotlinx.coroutines.withContext
> import timber.log.Timber
> import java.time.Instant
> import javax.inject.Inject
>
> /**
>  * S0211: one-shot cleanup of duplicate auth accounts.
>  *
>  * For each host, groups active accounts by computed identity. In each group with >1
>  * entries, keeps the record with max(lastUsedAt ?: savedAt) and deletes the rest.
>  * Records without a computable identity are left untouched.
>  *
>  * Idempotent: stores a done-flag in DataStore and returns immediately on subsequent
>  * calls. On a clean DB it scans then no-ops.
>  */
> class DedupAuthAccountsUseCase @Inject constructor(
>     private val store: EncryptedCookieStore,
>     private val dataStore: DataStore<Preferences>,
> ) {
>
>     companion object {
>         private val KEY_DONE = booleanPreferencesKey("dedup_s0211_done")
>     }
>
>     suspend operator fun invoke() {
>         val isDone = dataStore.data.first()[KEY_DONE] ?: false
>         if (isDone) return
>         Timber.d("S0211: DedupAuthAccountsUseCase.invoke start")
>
>         var deleted = 0
>         withContext(Dispatchers.IO) {
>             val active = store.listAllAccounts()
>                 .filter { (_, entry) ->
>                     entry.type == EncryptedCookieStore.TYPE_ACTIVE && entry.cookieCount > 0
>                 }
>             val byHost = active.groupBy { (host, _) -> host }
>
>             byHost.forEach { (host, pairs) ->
>                 val withIdentity = pairs.mapNotNull { (h, entry) ->
>                     val cookies = store.loadForAccount(h, entry.accountId)
>                     val identity = AccountIdentityExtractor.extract(h, cookies)
>                     if (identity != null) Triple(h, entry, identity) else null
>                 }
>                 withIdentity
>                     .groupBy { it.third }
>                     .filter { (_, group) -> group.size > 1 }
>                     .forEach { (identity, group) ->
>                         val keep = group.maxByOrNull { (_, e, _) ->
>                             e.lastUsedAt ?: e.savedAt ?: Instant.MIN
>                         } ?: return@forEach
>                         group
>                             .filter { it.second.accountId != keep.second.accountId }
>                             .forEach { (h, entry, _) ->
>                                 store.deleteForAccount(h, entry.accountId)
>                                 deleted += 1
>                                 Timber.i(
>                                     "S0211 cleanup: deleted dup host=%s accountId=%s identity=%s keepId=%s",
>                                     h, entry.accountId, identity, keep.second.accountId,
>                                 )
>                             }
>                     }
>             }
>         }
>
>         dataStore.edit { it[KEY_DONE] = true }
>         Timber.i("S0211 cleanup completed: deleted=%d", deleted)
>     }
> }
> ```
>
> The `Timber.d("S0211: DedupAuthAccountsUseCase.invoke start")` line is the debug verification tag (per CLAUDE.md "Debug Verification Tags") — owned by the spec status `BlockNeedUserTest`, removed by `/spec-check` on Verified.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DedupAuthAccountsUseCase.kt` exists.
- `Grep -n "class DedupAuthAccountsUseCase" DedupAuthAccountsUseCase.kt` — exactly one match.
- `Grep -n "dedup_s0211_done" DedupAuthAccountsUseCase.kt` — exactly one match (the DataStore key).
- `Grep -n "Timber.d\(\"S0211:" DedupAuthAccountsUseCase.kt` — exactly one match.

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. Files: DedupAuthAccountsUseCase.kt (+76 LOC). Dev log recorded.

---

### Step 03.3 — Wire `DedupAuthAccountsUseCase` into `MainViewModel.init`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> 1. Add a constructor parameter alongside the existing `MigrateS0059UseCase`:
>    ```kotlin
>    private val dedupAuthAccountsUseCase: DedupAuthAccountsUseCase,
>    ```
>    (Order constructor params consistently — append after the existing `migrateS0059UseCase` parameter, or place next to it; keep the file alphabetised if it already is.)
> 2. In the same `init { viewModelScope.launch { ... } }` block where `migrateS0059UseCase()` is invoked, append a second invocation after it:
>    ```kotlin
>    runCatching { dedupAuthAccountsUseCase() }
>        .onFailure { Timber.w(it, "S0211: DedupAuthAccountsUseCase failed") }
>    ```
>    Wrap in `runCatching` because storage IO failures must not crash app start; the marker stays unset → cleanup retries next launch.
> 3. Add the import `import com.sza.fastmediasorter.domain.usecase.DedupAuthAccountsUseCase`.
>
> The use case is idempotent — running it once per cold start is safe; the DataStore marker short-circuits on the second run.

**Verification:**

- `Grep -n "dedupAuthAccountsUseCase" MainViewModel.kt` — at least two matches (constructor param + init invocation).
- `Grep -n "import com.sza.fastmediasorter.domain.usecase.DedupAuthAccountsUseCase" MainViewModel.kt` — exactly one match.
- `Grep -n "runCatching \{ dedupAuthAccountsUseCase\(\)" MainViewModel.kt` — exactly one match.

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — Verification 3/3 PASS. Files: MainViewModel.kt (+4 LOC: import + ctor param + invocation). Dev log recorded.

---

### Step 03.4 — Compile check

**Files:** —
**Depends on:** Steps 03.1–03.3

**Prompt for developer:**

> Run `pwsh -File scripts\build-debug.PS1 -Flavor standard` (or invoke `/build` skill) and confirm `BUILD SUCCESSFUL`. Hilt graph must accept the new `DedupAuthAccountsUseCase` injection chain (constructor-injected; `EncryptedCookieStore` is already `@Singleton`; the DataStore binding already exists).

**Verification:**

- `expected: BUILD SUCCESSFUL | actual: <result>` recorded in chat.
- `Grep -n "@HiltViewModel" MainViewModel.kt` — exactly one match (no new Hilt module needed; constructor injection cascades).

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — `expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL — PASS`. Hilt graph accepted constructor injection of `DedupAuthAccountsUseCase` (EncryptedCookieStore @Singleton, DataStore binding pre-existing).

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — `standardDebug` build PASS.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every changed file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 04 does not depend on cleanup completion at runtime. UI changes are independent.

---

## Rollback Plan

Revert phase commits. The DataStore key `dedup_s0211_done` becomes orphaned but harmless. No schema change.
