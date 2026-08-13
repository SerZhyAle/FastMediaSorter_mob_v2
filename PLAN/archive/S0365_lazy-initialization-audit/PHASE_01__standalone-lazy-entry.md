# Phase 01 - Standalone lazy entry

**Strategic spec:** [`../S0365_lazy-initialization-audit.md`](../S0365_lazy-initialization-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 05
**Steps done:** 2 / 2
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Defer standalone-only network stack resolution and skip default-player probe launches before heavy standalone helpers are created.

---

## Prerequisites

- [ ] Strategic §6 research items are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] Existing standalone probe behavior is reproducible from the current branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Modified | ≤ 1150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt` | Modified | ≤ 1000 |

> Both Kotlin files exceed 500 LOC after change - create timestamped backups in `temp/` before editing.

---

## Steps

### Step 01.1 - Short-circuit the default-player probe before standalone wiring

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a single helper that resolves the incoming standalone URI once, call it at the top of `setupViews()`, and return early when the URI contains `default_player_probe`. This guard must run before `StandaloneViewManager`, PiP wiring, and other heavy helpers are created. Keep the later `parseIncomingIntent()` probe check as secondary safety net.

**Verification:**

- `Grep` - `short-circuiting default-player probe before standalone init` present in `StandalonePlayerActivity.kt`.
- `Grep` - `private fun resolveIncomingUri(): Uri?` matches exactly once in `StandalonePlayerActivity.kt`.
- `Grep` - `val incomingUri = resolveIncomingUri()` present in `StandalonePlayerActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 3/3 PASS. Files: `StandalonePlayerActivity.kt`. Dev log recorded.

---

### Step 01.2 - Convert standalone network collaborators to `dagger.Lazy`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Replace eager standalone network/cloud/file-operation injections with `dagger.Lazy<T>` wrappers. In `StandaloneViewManager`, keep `NetworkFileManager` behind its existing lazy block and resolve the collaborators with `.get()` only inside that block. In the activity, resolve the file-info dependencies only at the `FileInfoDialog` call site so normal local/content opens do not build the network stack.

**Verification:**

- `Grep` - `@Inject lateinit var smbClient: Lazy<SmbClient>` present in `StandalonePlayerActivity.kt`.
- `Grep` - `private val networkFileManager: NetworkFileManager by lazy` present in `StandaloneViewManager.kt`.
- `Grep` - `googleDriveClient = googleDriveClient.get()` present in `StandaloneViewManager.kt`.
- `Grep` - `smbClient.get(),` present in the `FileInfoDialog` call inside `StandalonePlayerActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 4/4 PASS. Files: `StandalonePlayerActivity.kt`, `StandaloneViewManager.kt`. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Standalone local/content launches no longer resolve the full network stack on entry, and default-player probe files exit before heavyweight helper initialization.

---

## Rollback Plan

Revert the standalone activity and view-manager edits - no schema change or user data migration involved.
