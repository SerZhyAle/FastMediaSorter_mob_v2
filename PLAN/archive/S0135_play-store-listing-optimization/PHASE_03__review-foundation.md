# Phase 03 — Review Foundation

**Strategic spec:** [`../S0135_play-store-listing-optimization.md`](../S0135_play-store-listing-optimization.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase (can run in parallel with Phases 01, 02)
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Add Google Play In-App Review dependency, create `ReviewEligibilityDataStore` (persistent counter storage), and `RecordSortSuccessUseCase` (threshold logic). No UI wiring yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/build.gradle.kts` accessible for edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | current + 2 lines |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/preferences/ReviewEligibilityDataStore.kt` | New | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RecordSortSuccessUseCase.kt` | New | ≤ 100 |

---

## Steps

### Step 03.1 — Add Google Play In-App Review dependency

**Files:** `app_v2/build.gradle.kts`
**Depends on:** — start of phase

**Prompt for developer:**

> In `app_v2/build.gradle.kts`, in the `dependencies { }` block, add immediately after the existing
> `implementation("com.google.android.material:material:1.13.0")` line:
>
> ```kotlin
> implementation("com.google.android.play:review-ktx:2.0.2")
> ```
>
> `review-ktx` is the Kotlin-extensions wrapper for `com.google.android.play:review`.
> Version 2.0.2 is the latest stable as of 2026-05 — verify at
> https://developer.android.com/guide/playcore/in-app-review#kotlin-java before building.
> Min API supported by `review-ktx:2.0.2` is API 16 — no minSdk conflict with any flavor (all ≥ 23).
> Do not add `exclude` groups — the library has no conflict with existing deps.

**Verification:**

- `Grep` — `com.google.android.play:review-ktx` present in `app_v2/build.gradle.kts`.
- `/build` — project compiles without error after sync.

**Status:** `[x] done (auto-build PASS)`

---

### Step 03.2 — Create ReviewEligibilityDataStore

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/preferences/ReviewEligibilityDataStore.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `ReviewEligibilityDataStore.kt` in `data/local/preferences/`. This class stores three
> counters in the existing `DataStore<Preferences>` (file `"settings"`, provided by `AppModule`).
> No new `@Module` or `@Provides` required — Hilt resolves `DataStore<Preferences>` via the
> existing `provideDataStore()` binding.
>
> Implement the class as shown:

```kotlin
package com.sza.fastmediasorter.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists counters and state for the In-App Review eligibility check (S0135).
 *
 * Keys are namespaced with `review_` to avoid collision with existing preference keys.
 * Uses the shared `DataStore<Preferences>` instance from AppModule.
 */
@Singleton
class ReviewEligibilityDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        val SORT_OPS_COUNT      = longPreferencesKey("review_sort_ops_count")
        val SESSIONS_WITH_SORT  = longPreferencesKey("review_sessions_with_sort")
        val REVIEW_SHOWN_EPOCH  = longPreferencesKey("review_shown_epoch_ms")
    }

    /** Increments cumulative sort-op count by [delta]. Returns new value. */
    suspend fun incrementSortOps(delta: Long = 1L): Long {
        var newValue = 0L
        dataStore.edit { prefs ->
            newValue = (prefs[SORT_OPS_COUNT] ?: 0L) + delta
            prefs[SORT_OPS_COUNT] = newValue
        }
        return newValue
    }

    /** Increments session-with-sort count by 1. Returns new value. */
    suspend fun incrementSessions(): Long {
        var newValue = 0L
        dataStore.edit { prefs ->
            newValue = (prefs[SESSIONS_WITH_SORT] ?: 0L) + 1L
            prefs[SESSIONS_WITH_SORT] = newValue
        }
        return newValue
    }

    /** Records that the review dialog was shown at [epochMs]. */
    suspend fun markReviewShown(epochMs: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs -> prefs[REVIEW_SHOWN_EPOCH] = epochMs }
    }

    /** Returns a one-shot snapshot of the three counters. */
    suspend fun snapshot(): ReviewEligibilitySnapshot {
        val prefs = dataStore.data.first()
        return ReviewEligibilitySnapshot(
            sortOpsCount     = prefs[SORT_OPS_COUNT]     ?: 0L,
            sessionsWithSort = prefs[SESSIONS_WITH_SORT] ?: 0L,
            reviewShownEpoch = prefs[REVIEW_SHOWN_EPOCH] ?: 0L
        )
    }

    /** Resets all counters to zero. Debug builds only. */
    suspend fun resetDebug() {
        dataStore.edit { prefs ->
            prefs[SORT_OPS_COUNT]     = 0L
            prefs[SESSIONS_WITH_SORT] = 0L
            prefs[REVIEW_SHOWN_EPOCH] = 0L
        }
    }
}

/** Immutable snapshot of review eligibility state at a point in time. */
data class ReviewEligibilitySnapshot(
    val sortOpsCount: Long,
    val sessionsWithSort: Long,
    val reviewShownEpoch: Long
)
```

**Verification:**

- `Glob` — file `data/local/preferences/ReviewEligibilityDataStore.kt` exists.
- `Grep` — `class ReviewEligibilityDataStore` matches exactly once.
- `Grep` — `review_sort_ops_count` present (key uniqueness probe vs. existing `filter_*` keys).
- `Grep` — `Log\.d\(` returns zero hits in the new file.

**Status:** `[x] done (auto-build PASS)`

---

### Step 03.3 — Create RecordSortSuccessUseCase

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RecordSortSuccessUseCase.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create `RecordSortSuccessUseCase.kt` in `domain/usecase/`. This use case is the sole owner of
> the "should we show the review dialog?" decision. It increments the counter, checks thresholds,
> and returns `true` exactly once per cooldown window when the user is eligible.
>
> Thresholds are companion-object constants — change them without touching domain logic.
>
> Implement the class as shown:

```kotlin
package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.preferences.ReviewEligibilityDataStore
import timber.log.Timber
import javax.inject.Inject

/**
 * Records one or more successful sort operations (Move or Copy) and returns `true`
 * when the user has reached the eligibility threshold and the review cooldown has expired.
 *
 * Threshold constants are tunable without domain-layer changes (S0135).
 * Caller must immediately trigger the review flow on `true` and call
 * [ReviewEligibilityDataStore.markReviewShown] to reset the trigger.
 */
class RecordSortSuccessUseCase @Inject constructor(
    private val store: ReviewEligibilityDataStore
) {

    companion object {
        /** Minimum cumulative Move+Copy operations before first eligibility check. */
        const val OPS_THRESHOLD: Long = 20L

        /** Minimum number of sessions that included at least one sort operation. */
        const val SESSIONS_THRESHOLD: Long = 3L

        /** Minimum gap between consecutive review requests (90 days). */
        const val COOLDOWN_MS: Long = 90L * 24L * 60L * 60L * 1_000L
    }

    /**
     * Records [count] successful sort operations and evaluates eligibility.
     *
     * @param count  Number of files successfully moved or copied in this operation.
     * @return `true` if the review dialog should be triggered; `false` otherwise.
     *
     * Session tracking: the first call per process lifetime increments [ReviewEligibilityDataStore.SESSIONS_WITH_SORT].
     * Subsequent calls in the same process skip the session increment (one increment per launch).
     */
    suspend fun record(count: Int = 1): Boolean {
        val newOps = store.incrementSortOps(count.toLong())
        Timber.d("RecordSortSuccessUseCase: ops=$newOps count=$count")

        val snap = store.snapshot()
        val cooldownExpired = snap.reviewShownEpoch == 0L ||
            (System.currentTimeMillis() - snap.reviewShownEpoch) >= COOLDOWN_MS

        val eligible = snap.sortOpsCount >= OPS_THRESHOLD &&
            snap.sessionsWithSort >= SESSIONS_THRESHOLD &&
            cooldownExpired

        Timber.d(
            "RecordSortSuccessUseCase: eligible=$eligible " +
            "ops=${snap.sortOpsCount}/$OPS_THRESHOLD " +
            "sessions=${snap.sessionsWithSort}/$SESSIONS_THRESHOLD " +
            "cooldownExpired=$cooldownExpired"
        )

        return eligible
    }

    /**
     * Increments the session counter. Call once per app launch that includes a sort activity.
     * Safe to call multiple times — [ReviewEligibilityDataStore.incrementSessions] is idempotent
     * relative to session semantics only when the caller gates it per process lifetime.
     */
    suspend fun recordSession() {
        val sessions = store.incrementSessions()
        Timber.d("RecordSortSuccessUseCase: session recorded sessions=$sessions")
    }
}
```

> Note: session gating per process lifetime (to avoid incrementing on every operation) is the
> caller's responsibility — `ReviewRequestManager` (Phase 04) tracks a `sessionRecorded` flag.

**Verification:**

- `Glob` — file `domain/usecase/RecordSortSuccessUseCase.kt` exists.
- `Grep` — `class RecordSortSuccessUseCase` matches exactly once.
- `Grep` — `OPS_THRESHOLD` with value `20L` present.
- `Grep` — `SESSIONS_THRESHOLD` with value `3L` present.
- `Grep` — `Log\.d\(` returns zero hits in the new file.

**Status:** `[x] done (auto-build PASS)`

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`ReviewEligibilityDataStore` and `RecordSortSuccessUseCase` are ready for wiring in Phase 04.
No UI visible yet. `ReviewRequestManager` in Phase 04 gates the `recordSession()` call per process lifetime.

---

## Rollback Plan

Revert phase commit. `DataStore<Preferences>` keys prefixed with `review_` are additive — no migration needed, no data loss on revert.
