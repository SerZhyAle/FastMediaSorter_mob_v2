# Phase 01 - Recovery Seam

**Strategic spec:** [`../S0432_bugfix-delivered-payload-integrity-recovery.md`](../S0432_bugfix-delivered-payload-integrity-recovery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-06-15
**Completed:** 2026-06-15

---

## Objective

When a delivered set fails integrity verification at load time, the loader self-invalidates that set's install state (delete payload + clear the persisted flag) and throws a typed corruption exception, so the set stops reporting "installed" and the Extensions row reactively flips to a recoverable state. Set-agnostic; no consumer or UI-layer changes in this phase.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `@ApplicationScope` qualifier exists at `app_v2/src/main/java/com/sza/fastmediasorter/core/di/` (already injected elsewhere - `WelcomeViewModel`, `VrApkClassificationCache`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliveredPayloadCorruptException.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliveredNativeLibraryLoader.kt` | Modified | ≤ 180 |

> No `res/layout` edits - no landscape parity concern. No flavor-specific files - all changes in `src/main` (strategic §3.2: shared infra, no `BuildConfig.IS_*` guards).

---

## Steps

### Step 01.1 - Add typed payload-corruption exception

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliveredPayloadCorruptException.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a new exception `DeliveredPayloadCorruptException` in package `com.sza.fastmediasorter.data.delivery`, extending `java.io.IOException`. It must carry the failed `DeliverableSet` (`val set: DeliverableSet`) and a `reason: String`, and pass a descriptive message to the `IOException` superclass. Extending `IOException` keeps every existing `catch (e: Exception)` / `catch (e: IOException)` site backward-compatible while letting consumers match this specific type. Keep it a plain data-carrying exception - no logic.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliveredPayloadCorruptException.kt` exists.
- `Grep` - `class DeliveredPayloadCorruptException` matches once.
- `Grep` - `: IOException` (or `: java.io.IOException`) present in that file.
- `Grep` - `val set: DeliverableSet` present in that file.

**Status:** `[ ]` not done

---

### Step 01.2 - Inject recovery dependencies into the loader

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliveredNativeLibraryLoader.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add two constructor-injected dependencies to `DeliveredNativeLibraryLoader`: the `DeliverableCapabilityRepository` (domain interface, already Hilt-bound to `DeliverableCapabilityRepositoryImpl`) and an `@ApplicationScope CoroutineScope` (import the qualifier from `com.sza.fastmediasorter.core.di.ApplicationScope`; the scope is already provided and injected elsewhere). The class is `@Inject constructor` with no Hilt module - these are plain constructor additions, no new `@Module`/`@Provides`. Do not introduce a new scope or qualifier. Confirm no DI cycle: the loader depends on the repository interface one-way; nothing in the repository graph depends on the loader.

**Verification:**

- `Grep` - `capabilityRepository: DeliverableCapabilityRepository` present in `DeliveredNativeLibraryLoader.kt`.
- `Grep` - `@ApplicationScope` present in `DeliveredNativeLibraryLoader.kt`.
- `Grep` - `import com.sza.fastmediasorter.core.di.ApplicationScope` present.
- Compiles - phase build (Phase Done Criteria).

**Status:** `[ ]` not done

---

### Step 01.3 - Self-invalidate and throw typed exception on integrity failure

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliveredNativeLibraryLoader.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `load(set)`, at the two payload-failure points - the missing-set-directory branch (currently `throw IOException("Set directory for $set is missing ..")`) and the per-file integrity-failure branch (currently `throw IOException("Integrity check failed ..")`) - do two things before throwing: (1) launch `capabilityRepository.uninstall(set)` on the injected `@ApplicationScope` scope (it deletes the payload directory and clears the persisted install flag; running it off the `@Synchronized` method via the scope avoids calling a `suspend` function from synchronous code); (2) throw `DeliveredPayloadCorruptException(set, <reason>)` instead of the bare `IOException`. The invalidation is idempotent and set-agnostic - it applies to every de-bundled set routed through `load()`. Do NOT touch the `bundledSets` short-circuit, the `loadedSets` cache logic, or `isInstalledBlocking` (strategic §9 ADR-1: no eager re-hash; verification stays at point of use). Clearing the DataStore flag makes `DeliverableCapabilityRepository.stateOf` emit `NOT_INSTALLED`, which the Extensions `moduleStatusFlow` already observes - no inventory change needed.

**Verification:**

- `Grep` - `DeliveredPayloadCorruptException(` matches at least twice in `DeliveredNativeLibraryLoader.kt` (missing-dir + integrity-fail).
- `Grep` - `scope.launch` (or the injected scope identifier + `.launch`) followed by `capabilityRepository.uninstall(set)` present.
- `Grep` - `isInstalledBlocking` does NOT appear in `DeliveredNativeLibraryLoader.kt` (ADR-1: no integrity in the presence gate).
- `Grep -n "Log\.d\("` - zero hits in `DeliveredNativeLibraryLoader.kt` (Timber only).
- Compiles - phase build (Phase Done Criteria).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (new public class).

---

## Handoff Notes to Next Phase

- `DeliveredPayloadCorruptException` is now thrown by `load()` on corruption and self-heals the marker. Phase 02 maps it to an actionable user message at the OCR consumer.
- Same exception also propagates to the FFMPEG_DTS consumer (`PlaybackRenderersFactory.load(FFMPEG_DTS)`); that path degrades silently today and the self-invalidation already makes the Extensions row recoverable, so no DTS consumer change is required by this spec.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed in this phase (the exception type and marker invalidation are internal to the delivery layer).
