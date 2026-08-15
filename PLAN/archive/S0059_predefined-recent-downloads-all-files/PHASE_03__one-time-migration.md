# Phase 03 — One-Time Migration of Existing Installs

**Strategic spec:** [`../S0059_predefined-recent-downloads-all-files.md`](../S0059_predefined-recent-downloads-all-files.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Run a single idempotent pass on the next launch of every existing install: locate every Recent and Downloads row in the resource DB, set `allFiles = true`, log one info line per touched row, then persist a one-shot flag so the pass never repeats — even if the user later flips the toggle back manually.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Strategic §6.1–§6.4 confirmed — defaults baked below; if any answer differs from the default, edit the matching step body before starting.
- [ ] Working tree is clean or on a feature branch.

### Defaults baked into this phase (revise here if §6 answers differ)

- §6.1 — Deleted Recent: **do not recreate**. Migration only touches existing rows.
- §6.2 — Renamed Downloads (custom name, canonical path): **migrate it** — match by path only, ignore name.
- §6.3 — Multiple LOCAL rows on the canonical Downloads path: **migrate all of them**.
- §6.4 — User-visible notification: **silent**, one Timber `i`-line per touched row.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MigrateRecentDownloadsAllFilesUseCase.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt` | Modified | ≤ 400 |

> `AppStartupInitializer` is currently 360 lines; after this phase it will stay below 400. No backup step required.

---

## Steps

### Step 03.1 — Create `MigrateRecentDownloadsAllFilesUseCase`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MigrateRecentDownloadsAllFilesUseCase.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> Create a new use-case `MigrateRecentDownloadsAllFilesUseCase` in package `com.sza.fastmediasorter.domain.usecase`. Inject `ResourceRepository` via Hilt (`@Inject constructor(private val resourceRepository: ResourceRepository)`). The Hilt module that already binds `ResourceRepository` (search the project for `@Provides.*ResourceRepository` or `@Binds.*ResourceRepository` — typically `RepositoryModule` under `di/`) needs no change because `@Inject constructor` use-cases are auto-discovered.
>
> Expose a single `suspend operator fun invoke(): Int` that:
> 1. Reads all resources via `resourceRepository.getAllResources().first()` (import `kotlinx.coroutines.flow.first`).
> 2. Filters to rows where `PredefinedResourceClassifier.isPredefinedRecent(it.path) || PredefinedResourceClassifier.isPredefinedDownloads(it.path, it.type)` AND `it.allFiles == false`.
> 3. For each match: `resourceRepository.updateResource(row.copy(allFiles = true))`. Use Timber: `Timber.i("S0059 migrated resource id=%d path='%s' allFiles false→true", row.id, row.path)`.
> 4. Returns the number of rows actually updated.
>
> No catch — let exceptions bubble up; the caller in `AppStartupInitializer` already wraps with try/catch and logs.
> File ≤ 120 lines.

**Verification:**

- `Glob` — `MigrateRecentDownloadsAllFilesUseCase.kt` exists.
- `Grep` — `class MigrateRecentDownloadsAllFilesUseCase` matches once.
- `Grep` — `suspend operator fun invoke\(\): Int` matches once.
- `Grep` — `PredefinedResourceClassifier.isPredefinedRecent` matches.
- `Grep` — `PredefinedResourceClassifier.isPredefinedDownloads` matches.
- `Grep` — `Log\.d\(` returns zero hits in the new file.

**Status:** `[ ]` not done

---

### Step 03.2 — Wire migration into `AppStartupInitializer`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a constructor parameter `private val migrateRecentDownloadsAllFilesUseCase: MigrateRecentDownloadsAllFilesUseCase` to `AppStartupInitializer`. Update the Hilt provider that constructs `AppStartupInitializer` (search for `provideAppStartupInitializer` or `AppStartupInitializer(` in `di/`/`Application` class) to pass the new dependency.
>
> Add a private method `migrateRecentDownloadsAllFiles()` modelled on the existing `fixCloudResourcesWritableFlag` / `fixLocalResourcesWritableFlag` pattern: launches on `applicationScope`, reads/writes the one-shot flag from a private `SharedPreferences` file `"startup_migrations"` under key `"s0059_recent_downloads_done"` (mirroring the `"glide_config"` pattern already used at `AppStartupInitializer.syncCacheSizeToSharedPreferences`). If the flag is already `true`, return early. Otherwise call `migrateRecentDownloadsAllFilesUseCase()`, log a single summary line (`Timber.i("S0059 migration: updated %d Recent/Downloads resources", count)`), and **only on successful completion** set the flag to `true`. Any thrown exception is logged via `Timber.e(e, "S0059 migration failed")` and leaves the flag unset so the next launch retries.
>
> Add a call to `migrateRecentDownloadsAllFiles()` inside `initialize()` immediately **after** `fixLocalResourcesWritableFlag()` and **before** `renameVirtualResourceNames()` so the migration runs in the same startup batch as the other resource-table fixes.

**Verification:**

- `Grep` — `migrateRecentDownloadsAllFilesUseCase: MigrateRecentDownloadsAllFilesUseCase` matches once in the constructor signature.
- `Grep` — `private fun migrateRecentDownloadsAllFiles\(\)` matches once.
- `Grep` — `"startup_migrations"` matches.
- `Grep` — `"s0059_recent_downloads_done"` matches.
- `Grep` — `migrateRecentDownloadsAllFiles\(\)` is called exactly once inside `initialize()`.
- `Grep` — the call appears between `fixLocalResourcesWritableFlag\(\)` and `renameVirtualResourceNames\(\)` (manual ordering check; line numbers monotone).

**Status:** `[ ]` not done

---

### Step 03.3 — Smoke check: idempotency contract

**Files:** none — verification of behavior introduced in Steps 03.1–03.2
**Depends on:** Steps 03.1, 03.2

**Prompt for developer:**

> Build a debug APK via `/build`, install on a device that already has at least one Recent or Downloads row with `allFiles = false`. Launch app, observe one `S0059 migrated …` line per touched row plus one summary line. Force-quit and relaunch — the migration must **not** run again (no new log lines). Open the resource editor, flip `allFiles` back to `false` on a migrated row, save, force-quit, relaunch — the row stays at `false` (migration is one-shot, not lecturing).
>
> No source changes here; this step is the contract test for idempotency. Record the observed counts in the dev log entry below.

**Verification:**

- `Grep` for `S0059 migration: updated` in the captured logcat — exactly **one** occurrence per fresh first launch on the test device.
- After the user-flip-back scenario, the second launch's logcat shows **zero** occurrences of `S0059 migrated resource id=` and **zero** of `S0059 migration: updated`.
- A short note (1–2 lines) appended to the dev log entry for `AppStartupInitializer.kt` summarising the counts: `dev-test: N rows migrated; idempotency confirmed (M=0 on relaunch)`.

**Status:** `[ ]` not done

---

### Step 03.4 — Dev log

**Files:** the two files modified in this phase
**Depends on:** Step 03.3

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1` for `MigrateRecentDownloadsAllFilesUseCase.kt` (target `feature`, description `S0059 phase 03: migration use-case`) and for `AppStartupInitializer.kt` (target `feature`, description `S0059 phase 03: startup wiring + one-shot flag`).

**Verification:**

- `Grep` for `S0059 phase 03` in `dev/CHANGELOG.md` matches at least twice.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Idempotency confirmed on a real device (Step 03.3).
- [ ] Dev log entries added for both modified files.
- [ ] Public API surface added: catalog regen deferred to Phase 04.

---

## Handoff Notes to Next Phase

- The migration is now **closed** — flipping the flag back is impossible without clearing app data. Documentation in Phase 04 must therefore tell the user that the manual override sticks (one of the strategic-level criteria).
- A new use-case class was added (`MigrateRecentDownloadsAllFilesUseCase`) — Phase 04 must regenerate `dev/CATALOG/app_v2.jsonl` and `app_v2.md`.

---

## Rollback Plan

Revert phase commit(s). Any rows already migrated retain `allFiles = true` (no reverse migration is required — the strategic spec explicitly accepts this). The one-shot SharedPreferences flag becomes orphaned but harmless. If the rollback ships, the migration code is gone, so the flag is never read again; if a future build re-introduces the migration, decide at that point whether to use a new key.
