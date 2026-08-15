# Phase 04 — Clear Transient Failures on Playback Deactivate (All Protocols)

**Strategic spec:** [`../S0066_enh-network-transient-failure-classification-multi-protocol.md`](../S0066_enh-network-transient-failure-classification-multi-protocol.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Generalize the transient-cache cleanup so any protocol's `deactivateVideoPlayerMode(resourceKey)` triggers `clearTransientFailuresForResource(resourceKey)`. Add the new universal method on `NetworkFileDataFetcher`. Mark `clearTransientFailuresForHost(smbHost)` as `@Deprecated` and have it delegate.

---

## Prerequisites

- [ ] Phase 03 ✅ Done.
- [ ] Decoder reads `extractNetworkResourceKey` and unified `transientFailureReason`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt` | Modified | ≤ 500 (verify current size; backup if >500) |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/ConnectionThrottleManager.kt` | Modified | ≤ 600 (current ~547; expected delta +5 lines inside `deactivateVideoPlayerMode`) |

> Before editing `NetworkFileModelLoader.kt`, read its current line count. If the projected size after edit crosses 500, create a timestamped backup in `temp/`.

---

## Steps

### Step 04.1 — Add `clearTransientFailuresForResource` and deprecate the SMB-only alias

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In the `NetworkFileDataFetcher` companion (where `clearTransientFailuresForHost` currently lives), add a new function:
>
> ```kotlin
> /**
>  * Remove all transient failures for any network resource (SMB / SFTP / FTP).
>  * Called from playback-stop hooks for the deactivated resource. S0066.
>  * [resourceKey] is the normalized "<scheme>://host:port" produced by extractNetworkResourceKey.
>  */
> fun clearTransientFailuresForResource(resourceKey: String) {
>     val cleared = transientFailedVideos.keys.filter { path ->
>         pathBelongsToResource(path, resourceKey)
>     }.toList()
>     cleared.forEach { transientFailedVideos.remove(it) }
>     if (cleared.isNotEmpty()) {
>         Timber.i("[scope=thumbnail S0066 resource=$resourceKey] Cleared ${cleared.size} transient failures")
>     }
> }
> ```
>
> Then change the existing `clearTransientFailuresForHost(smbHost: String)` to be `@Deprecated` and delegate:
>
> ```kotlin
> @Deprecated("S0066 — use clearTransientFailuresForResource(resourceKey)", ReplaceWith("clearTransientFailuresForResource(resourceKey)"))
> fun clearTransientFailuresForHost(smbHost: String) {
>     // Try common SMB ports — caller passed only host.
>     clearTransientFailuresForResource("smb://$smbHost:445")
>     clearTransientFailuresForResource("smb://$smbHost:139")
> }
> ```
>
> The import `pathBelongsToResource` is already in the same package; no additional import needed.

**Verification:**

- `Grep` — `fun clearTransientFailuresForResource\(resourceKey: String\)` matches exactly once.
- `Grep` — `@Deprecated\("S0066` matches at least once in this file.
- `Grep` — `fun clearTransientFailuresForHost` still present (delegating).
- `Grep` — `pathBelongsToResource\(path, resourceKey\)` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 4/4 PASS. Files: NetworkFileModelLoader.kt (backup recorded in temp/S0066-backup-20260503).

---

### Step 04.2 — Hook cleanup into `ConnectionThrottleManager.deactivateVideoPlayerMode`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/ConnectionThrottleManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `deactivateVideoPlayerMode(resourceKey: String)`, immediately after `videoPlayerResources.remove(resourceKey)` and before the `if (videoPlayerResources.isEmpty())` block, invoke the cleanup:
>
> ```kotlin
> // S0066: clear transient thumbnail failures for this resource so previews recover
> // automatically once playback ends — uniform across SMB / SFTP / FTP.
> try {
>     com.sza.fastmediasorter.data.network.glide.NetworkFileDataFetcher
>         .clearTransientFailuresForResource(resourceKey)
> } catch (e: Exception) {
>     Timber.w(e, "ConnectionThrottle: failed to clear transient failures for $resourceKey")
> }
> ```
>
> Do NOT remove the existing 300 ms delayed-resume logic — it stays.

**Verification:**

- `Grep` — `clearTransientFailuresForResource\(resourceKey\)` matches exactly once in `ConnectionThrottleManager.kt`.
- `Grep` — `delay\(300\)` still present (the delayed-resume block is untouched).
- `Grep` — `videoPlayerResources\.remove\(resourceKey\)` still present and on a line above the new block.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. Files: ConnectionThrottleManager.kt (backup recorded).

---

### Step 04.3 — Build gate

**Files:** —
**Depends on:** Steps 04.1–04.2

**Prompt for developer:**

> Run `/build` for `standard debug`. Confirm compilation passes.

**Verification:**

- `/build` skill returns success for `standard debug`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — BUILD SUCCESSFUL (standard debug, 33s, v2.60.5031.807).

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for both modified files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 05 finalizes the spec: trilingual FEATURES update, catalog regen for the two new files (`TransientReason.kt`, `NetworkResourceKey.kt`), modified-file catalog refresh, dev log audit.

---

## Rollback Plan

Revert phase commit — `clearTransientFailuresForHost` legacy path was already a no-op outside spec wiring; reverting reinstates the previous behavior with no data loss.
