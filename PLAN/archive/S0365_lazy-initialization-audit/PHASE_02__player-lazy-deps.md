# Phase 02 - Player lazy dependencies

**Strategic spec:** [`../S0365_lazy-initialization-audit.md`](../S0365_lazy-initialization-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 05
**Steps done:** 2 / 2
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Remove eager player-path collaborator resolution from local-only sessions and gate optional audio background managers behind actual audio/slideshow state.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §6 research items are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 950 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerObserverManager.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt` | Modified | ≤ 420 |

> `PlayerActivity.kt` and `PlayerManagerInitializer.kt` exceed 500 LOC after change - create timestamped backups in `temp/` before editing.

---

## Steps

### Step 02.1 - Wrap heavy player collaborators in `dagger.Lazy`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Convert player-path network/cloud/file-operation collaborators, plus the optional background-audio managers, from eager `@Inject lateinit var` fields to `dagger.Lazy<T>` when the activity does not need them on every launch path. Keep direct non-heavy state repositories and lightweight probes eager unless this phase proves they are also avoidable.

**Verification:**

- `Grep` - `@Inject internal lateinit var smbClientLazy: Lazy<SmbClient>` present in `PlayerActivity.kt`.
- `Grep` - `@Inject internal lateinit var dropboxClientLazy: Lazy<` present in `PlayerActivity.kt`.
- `Grep` - `@Inject internal lateinit var backgroundMusicManagerLazy: Lazy<` present in `PlayerActivity.kt`.
- `Grep` - `@Inject internal lateinit var cloudFileOperationHandlerLazy: Lazy<` present in `PlayerActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 4/4 PASS. Files: `PlayerActivity.kt`. Dev log recorded.

---

### Step 02.2 - Configure optional audio background managers on demand

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerObserverManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Replace the unconditional background-media bootstrap with a one-shot helper that resolves and wires `BackgroundMusicManager` / `AudioBackgroundPhotosManager` only when an audio or slideshow state requires them. Update observer and lifecycle cleanup call sites to use the configured activity getters only after the helper has run, preserving release behavior on destroy.

**Verification:**

- `Grep` - `ensureAudioBackgroundManagersConfigured` present in `PlayerManagerInitializer.kt`.
- `Grep` - `activity.playerManagerInitializer.ensureAudioBackgroundManagersConfigured()` present in `PlayerObserverManager.kt`.
- `Grep` - `if (activity.areAudioBackgroundManagersConfigured)` present in `PlayerLifecycleManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 3/3 PASS. Files: `PlayerManagerInitializer.kt`, `PlayerObserverManager.kt`, `PlayerLifecycleManager.kt`, `AudioSlideshowPhotoModeManager.kt`, `PlayerDialogHelper.kt`, `NetworkFileManager.kt`, `BrowseCloudAuthManager.kt`. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Player local-only sessions should no longer resolve optional cloud/network/media background collaborators until a path truly needs them.

---

## Rollback Plan

Revert the player activity / initializer / observer / lifecycle edits together so lazy access and release semantics do not drift.
