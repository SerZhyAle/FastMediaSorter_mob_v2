# Phase 04 - Pinned shortcut migration

**Strategic spec:** [`../S1471_stream-shortcut-starts-background-playback.md`](../S1471_stream-shortcut-starts-background-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Rewrite the tap intent of stream shortcuts that were already pinned before this ticket, so existing users get the new behaviour without re-pinning anything.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/MigrateStreamShortcutsUseCase.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/DeferredStartupWorker.kt` | Modified | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamShortcutPinManager.kt` | Modified | ≤ 70 |

> Third file added during implementation (2026-08-09): the `stream_` id prefix the migration matches on is authored by `StreamShortcutPinManager`. The plan had the new use case carry its own copy of the literal, which would let the two id schemes drift apart with no compiler or gate to catch it. Promoted to `StreamShortcutPinManager.SHORTCUT_ID_PREFIX` and referenced from both.

---

## Steps

### Step 04.1 - Add `MigrateStreamShortcutsUseCase`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/MigrateStreamShortcutsUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class MigrateStreamShortcutsUseCase @Inject constructor(@ApplicationContext private val context: Context)` with `suspend operator fun invoke()`. Guard on `ShortcutManagerCompat.isRequestPinShortcutSupported(context)` and return early when it is false. Enumerate `ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)`, keep the ones whose id starts with `stream_` and whose current intent targets `StreamsActivity`, rebuild each as a `ShortcutInfoCompat` carrying the same id, labels and icon but `StreamPlayLaunchActivity.createIntent(context, url)`, and apply them with `ShortcutManagerCompat.updateShortcuts`. Read the URL from the old intent's `StreamsActivity.EXTRA_STREAM_URL` extra and skip any entry where it is blank. Log the migrated count once with `Timber.i`.

**Why:**

Strategic §3.2 records that already-pinned shortcuts carry the old intent, so without this step the new behaviour reaches only shortcuts pinned after the update.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/MigrateStreamShortcutsUseCase.kt` exists.
- `Grep` - `class MigrateStreamShortcutsUseCase` matches exactly once in that file.
- `Grep` - `FLAG_MATCH_PINNED` and `updateShortcuts` both present in that file.
- ~~`Grep` - `isRequestPinShortcutSupported` present in that file.~~ **Retired 2026-08-09 by the phase-boundary audit.** The predicate demanded a guard that answers the wrong question: `isRequestPinShortcutSupported` reports whether the *current* launcher will accept a *new* pin, not whether pinned shortcuts exist. A user who pinned under one launcher and switched to one that refuses new pins keeps a working shortcut and would never be migrated. The guard is gone; enumerating an empty set costs one binder call.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

**Result (2026-08-09):** all five predicates pass. Two deviations from the prompt, both recorded above and below:

- The rebuild uses the `ShortcutInfoCompat.Builder(ShortcutInfoCompat)` copy constructor rather than re-setting label and icon by hand. `ShortcutInfoCompat.getIcon()` is `@RestrictTo` library-group, and the icon a shortcut was pinned with is S1067's favicon tile, which cannot be rebuilt from the id - copying preserves it. Constructor confirmed present by `javap` against `androidx.core:core:1.16.0`, not assumed.
- The stale-pin test is on the intent's component, not on its action. `ACTION_PLAY_STREAM` survives on the fallback intent the trampoline itself starts, so an action test would rewrite an already-migrated shortcut on every start.

---

### Step 04.2 - Run the migration from deferred startup

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/DeferredStartupWorker.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Inject `dagger.Lazy<MigrateStreamShortcutsUseCase>` into `DeferredStartupWorker` and add `runTask("migrate-stream-shortcuts") { migrateStreamShortcuts.get().invoke() }` alongside the existing tasks. Place it before the installed-app-cache seed so the cheap shortcut rewrite is not queued behind the slowest task.

**Why:**

Strategic §3.2 needs the rewrite to happen once per install without a user gesture, and this worker is the project's existing host for exactly that kind of deferred one-shot work.

**Verification:**

- `Grep` - `migrate-stream-shortcuts` present in `DeferredStartupWorker.kt`.
- `Grep` - `MigrateStreamShortcutsUseCase` present in `DeferredStartupWorker.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Result (2026-08-09):** the task sits ahead of `seed-installed-app-cache` as the prompt requires. `.\a.ps1 fk` exit 0; the Hilt graph itself is proven by the full `standard debug` build below, which `fk` does not validate.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 d` (full `standard debug`, the run that validates the new `dagger.Lazy` binding).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` - batched with Phase 03/05 through `post-change.ps1` in Step 05.3.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - run in Step 05.2.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

Every stream shortcut in existence, old or new, now targets `StreamPlayLaunchActivity`. Nothing outside `StreamShortcutPinManager` and this use case builds a pinned shortcut intent.

---

## Rollback Plan

Revert phase commit(s). Shortcuts already rewritten keep pointing at the trampoline, which still exists unless Phase 02 is reverted as well - revert Phase 04 and Phase 02 together or neither.
