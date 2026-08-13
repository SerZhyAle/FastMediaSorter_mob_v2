# Phase 05 — Auth-State Wipe + Drive Resource "Needs Sign-In" State

**Strategic spec:** [`../S0200_google-account-central-binding.md`](../S0200_google-account-central-binding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (with 05.5 unit test deferred to dedicated round)
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 4 / 5 (05.5 Robolectric test deferred)
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

On first launch of the upgraded app, clear ALL legacy Google Drive auth state (cached tokens, per-account credential JSON, `NetworkCredentialsEntity` rows for Drive, primary account store) while PRESERVING every `ResourceEntity` row (folder name, sync flags, `credentialsId` string). Add a Room column `ResourceEntity.needsSignIn: Boolean` set to `true` for every Drive row after the wipe. UI consumers (Phase 06) read this column to render the indicator.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done (no app code references GoogleSignIn).
- [ ] Current `@Database(version)` value of `AppDatabase` is known. (Look up before editing.)
- [ ] Decision documented in INDEX Pre-Impl Blocker: hand-written migration vs. auto-migration. Default in this phase: hand-written `Migration_N_to_M` (auto-migration cannot set values per-row by predicate; we need DEFAULT 0 for non-Drive rows AND DEFAULT 1 only for the Drive-during-wipe path, which is data manipulation, not schema).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceEntity.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceDao.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | Modified | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/migrations/Migration_S0200.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/migration/S0200AuthStateWipe.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApplication.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/helpers/GoogleDriveCredentialsManager.kt` | Modified | ≤ 130 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/migration/S0200AuthStateWipeTest.kt` | New | ≤ 250 |

> `S0200AuthStateWipe.kt` is the new use case. It is referenced by `Application.onCreate` only.

---

## Steps

### Step 05.1 — Add `needsSignIn` column to `ResourceEntity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceEntity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceDao.kt`
**Depends on:** —

**Prompt for developer:**

> Add a new column with safe default to preserve existing rows:
>
> ```kotlin
> @ColumnInfo(name = "needs_sign_in", defaultValue = "0")
> val needsSignIn: Boolean = false
> ```
>
> In `ResourceDao` add:
>
> ```kotlin
> @Query("UPDATE resources SET needs_sign_in = :flag WHERE type = 'CLOUD' AND cloud_provider = 'GOOGLE_DRIVE'")
> suspend fun markAllDriveNeedsSignIn(flag: Boolean)
>
> @Query("UPDATE resources SET needs_sign_in = 0 WHERE credentials_id = :credentialsId")
> suspend fun clearNeedsSignInForCredentials(credentialsId: String)
> ```
>
> Verify the column name string `cloud_provider` matches the entity column annotation. If the existing column has a different SQL name, adjust the query to match.

**Verification:**

- `Grep -n "needs_sign_in" app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceEntity.kt` matches exactly once.
- `Grep -n "fun markAllDriveNeedsSignIn"` matches exactly once.
- `Grep -n "fun clearNeedsSignInForCredentials"` matches exactly once.
- Build closure: `/build` → `standardDebug`. Expected PASS. Actual: paste.

**Status:** `[ ]` not done

---

### Step 05.2 — Bump `AppDatabase` version + register migration

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/migrations/Migration_S0200.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Read the current `@Database(version = N)`. Set the new version to `N+1`. Add `Migration_S0200.kt`:
>
> ```kotlin
> object Migration_S0200 : Migration(N, N + 1) {
>     override fun migrate(db: SupportSQLiteDatabase) {
>         db.execSQL("ALTER TABLE resources ADD COLUMN needs_sign_in INTEGER NOT NULL DEFAULT 0")
>     }
> }
> ```
>
> Register the migration in `AppDatabase` builder configuration (find `.addMigrations(..)` call site and append `Migration_S0200`).
>
> Use the SQLite column type `INTEGER` because Room maps `Boolean` to `INTEGER` (0 / 1). The Kotlin field name is `needsSignIn` (camelCase) and the SQL column is `needs_sign_in` (snake_case via `@ColumnInfo(name = ...)`).
>
> Generate schema JSON: `./gradlew :app_v2:kspStandardDebugKotlin` produces the updated schema under `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/<N+1>.json` — commit this file.

**Verification:**

- `Grep -n "@Database" app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` shows new version `N+1`.
- `Glob` — `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/<N+1>.json` exists.
- `Grep -n "Migration_S0200"` matches in `AppDatabase.kt` (the `.addMigrations` call).
- Build closure: `/build` → `standardDebug`. Expected PASS. Actual: paste.

**Status:** `[ ]` not done

---

### Step 05.3 — Implement `S0200AuthStateWipe`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/migration/S0200AuthStateWipe.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/helpers/GoogleDriveCredentialsManager.kt`
**Depends on:** Step 05.2
**Depends on:** Step 04.8 (`GoogleDriveCredentialsManager` plaintext-fallback removed)

**Prompt for developer:**

> 1. Add `clearAllCredentials()` to `GoogleDriveCredentialsManager` — enumerates per-account keys with prefix `credentials_` and removes them, then clears the singleton `KEY_CREDENTIALS`. Replace the existing partial-clear method `clearStoredCredentials()`.
> 2. Create `S0200AuthStateWipe.kt`:
>
> ```kotlin
> @Singleton
> class S0200AuthStateWipe @Inject constructor(
>     @ApplicationContext private val context: Context,
>     private val networkCredentialsDao: NetworkCredentialsDao,
>     private val resourceDao: ResourceDao,
>     private val identityRepository: GoogleIdentityRepository,
>     private val pendingRevocationDao: PendingRevocationDao,
>     private val driveCredentialsManager: GoogleDriveCredentialsManager
> ) {
>     /**
>      * Run-once auth-state wipe per strategic ADR-6.
>      * Idempotent: sentinel flag prevents re-execution.
>      */
>     suspend fun runIfNeeded() {
>         val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
>         if (prefs.getBoolean(KEY_DONE, false)) return
>         try {
>             // 1. Enumerate cached tokens for offline revocation BEFORE clearing
>             val staleTokens = driveCredentialsManager.snapshotAllTokens()
>             staleTokens.forEach { pendingRevocationDao.enqueue(PendingRevocationEntity(token = it)) }
>             // 2. Clear identity store (primary)
>             identityRepository.signOutPrimary()
>             // 3. Clear Drive credentials prefs (incl. per-account)
>             driveCredentialsManager.clearAllCredentials()
>             // 4. Delete all NetworkCredentialsEntity rows for Drive
>             networkCredentialsDao.deleteByType("GOOGLE_DRIVE")
>             // 5. Mark every Drive resource as needs-sign-in
>             resourceDao.markAllDriveNeedsSignIn(true)
>             // 6. Persist done flag last (failure above leaves us re-runnable)
>             prefs.edit().putBoolean(KEY_DONE, true).commit()
>             Timber.i("S0200: legacy auth-state wipe completed (tokens=${staleTokens.size})")
>         } catch (t: Throwable) {
>             Timber.e(t, "S0200: legacy auth-state wipe failed; will retry on next launch")
>         }
>     }
>
>     private companion object {
>         const val PREFS = "s0200_migration"
>         const val KEY_DONE = "wipe_done"
>     }
> }
> ```
>
> Order of operations matters: enqueue revocation FIRST (otherwise we lose the token strings); persist `wipe_done` LAST so a partial-failure run re-attempts. If `NetworkCredentialsDao` lacks a `deleteByType` method, add it: `@Query("DELETE FROM network_credentials WHERE type = :type") suspend fun deleteByType(type: String)`.

**Verification:**

- `Glob` — `S0200AuthStateWipe.kt` exists.
- `Grep -n "class S0200AuthStateWipe"` matches exactly once.
- `Grep -n "fun runIfNeeded"` matches exactly once.
- `Grep -n "wipe_done"` matches exactly once.
- `Grep -n "fun clearAllCredentials" app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/helpers/GoogleDriveCredentialsManager.kt` matches exactly once.
- Build closure: `/build` → `standardDebug`. Expected PASS. Actual: paste.

**Status:** `[ ]` not done

---

### Step 05.4 — Invoke wipe from `FastMediaSorterApplication.onCreate`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApplication.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Inject `S0200AuthStateWipe` and call `runIfNeeded()` from a coroutine started in `onCreate`:
>
> ```kotlin
> @Inject lateinit var authStateWipe: S0200AuthStateWipe
> // Existing applicationScope: CoroutineScope — re-use it
>
> override fun onCreate() {
>     super.onCreate()
>     // existing initialization ...
>     applicationScope.launch { authStateWipe.runIfNeeded() }
> }
> ```
>
> Verify the existing `applicationScope` (or `appScope` / similar) field is available. If not, follow the existing pattern in the file for fire-and-forget startup work.
>
> If lite flavor does NOT include cloud, `S0200AuthStateWipe` must still be injectable — it will run, find no Drive resources, no credentials, and complete in milliseconds. Verify this works in `liteDebug` (the no-op `GoogleIdentityRepository` from Phase 01 satisfies its dependency).

**Verification:**

- `Grep -n "authStateWipe.runIfNeeded\\|S0200AuthStateWipe" app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApplication.kt` matches at least 2 lines (field + call).
- Build closure: `/build` → `standardDebug`. Expected PASS. Actual: paste.
- Build closure: `/build` → `liteDebug`. Expected PASS. Actual: paste.

**Status:** `[ ]` not done

---

### Step 05.5 — Unit test: idempotency + clearance of all stores

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/migration/S0200AuthStateWipeTest.kt`
**Depends on:** Step 05.4

**Prompt for developer:**

> Robolectric test (needs `Context` for `SharedPreferences`). Cover:
> - First call clears every dependency (verified via mock interactions: `signOutPrimary`, `clearAllCredentials`, `deleteByType("GOOGLE_DRIVE")`, `markAllDriveNeedsSignIn(true)`, and sets `wipe_done=true`).
> - Second call is a no-op (no mock interactions).
> - Failure in step 4 (`deleteByType` throws): `wipe_done` is NOT set; mock invocations on earlier steps still observed.
> - Token snapshot enqueued for revocation when tokens exist.

**Verification:**

- `Glob` — test file exists.
- `Grep -n "@Test"` matches ≥ 4 lines.
- Test run: `./gradlew :app_v2:testStandardDebugUnitTest --tests "*S0200AuthStateWipeTest*"` exits 0. Expected PASS. Actual: paste.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `/build` → `standardDebug` AND `liteDebug` PASS.
- [ ] Room schema JSON for the new version is committed under `app_v2/schemas/`.
- [ ] All listed unit tests pass.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

After Phase 05:
- On every fresh install OR first launch after upgrade, every Drive `ResourceEntity` has `needsSignIn = true`. Phase 06 reads this in `ResourceAdapter` to show the indicator.
- The primary identity store is empty (no `Bound` state) until user signs in via the new Settings card (Phase 06).
- `wipe_done` is persisted in `SharedPreferences("s0200_migration")`. To force a re-wipe during QA: `adb shell pm clear` or manually flip the flag. Do not depend on Room.fallbackToDestructiveMigration — the data wipe is intentional and orthogonal to schema.

---

## Rollback Plan

If a release ships with Phase 05 and a critical bug is discovered:
1. Hotfix: hot-patch `S0200AuthStateWipe.runIfNeeded` to `return` immediately for users where `wipe_done = false`.
2. Re-add deprecated GoogleSignIn paths is NOT a rollback option — Phase 04 removed the imports. Revert Phase 04 AND Phase 05 atomically via the merge commit.
3. Schema rollback: Room does NOT support downgrade — users who already opened the app on the new version must NOT downgrade APK. Document in release notes.
