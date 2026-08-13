# S0168 Phase 2 — PrefetchLoadControl fallback log level D → W

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchLoadControlFactory.kt`
**Spec ref:** §5.4

## Steps

### Step 2.1 — Change log level for "fallback standard defaults"

Change:
```kotlin
Timber.d("PrefetchLoadControl[%s]: fallback standard defaults", tag)
```
to:
```kotlin
Timber.w("PrefetchLoadControl[%s]: fallback standard defaults", tag)
```

WHY: when PrefetchLoadControl falls back to standard defaults it means the player received no
`PrefetchPlan` context — this is a diagnostic signal indicating the plan was not delivered
before `createPlayer()`. Elevating to W makes it visible in `search-log.ps1 -Warnings`.

Verification: `grep -n "fallback standard defaults"` in the file shows `Timber.w(`.
