# Phase 03 — Decoder: Universal Classification + Unified Log Format

**Strategic spec:** [`../S0066_enh-network-transient-failure-classification-multi-protocol.md`](../S0066_enh-network-transient-failure-classification-multi-protocol.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Replace SMB-only logic in `NetworkVideoFrameDecoder.decode` with the universal pipeline. Use `extractNetworkResourceKey(path)` everywhere `extractSmbServerKey(path)` was used. Read `mediaDataSource.transientFailureReason` and combine with `playbackActive` to compute `isTransient`. Emit a unified log format `[scope=thumbnail protocol=X resource=Y failureClass=Z playbackActive=W]`.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] `transientFailureReason` populated by SMB / SFTP / FTP datasource branches.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt` | Modified | ≤ 500 (current ~430; expected delta neutral or negative) |

---

## Steps

### Step 03.1 — Replace `extractSmbServerKey` with `extractNetworkResourceKey`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Delete the private function `extractSmbServerKey(path: String): String?` from `NetworkVideoFrameDecoder.kt`. Replace every internal call site (`val smbKey = extractSmbServerKey(...)` → `val resourceKey = extractNetworkResourceKey(...)`) and rename the local variable to `resourceKey`. Ensure the new helper is imported (same package `com.sza.fastmediasorter.data.network.glide`, so no import statement is needed). The two existing call sites are at lines 109 and 192 in the current file.

**Verification:**

- `Grep` — `extractSmbServerKey` does NOT match anywhere in this file.
- `Grep` — `extractNetworkResourceKey\(source\.path\)` matches at least twice.
- `Grep` — `val resourceKey =` matches at least twice.
- `Grep` — `private fun extractSmbServerKey` does NOT appear anywhere (declaration removed).

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 4/4 PASS. Files: NetworkVideoFrameDecoder.kt.

---

### Step 03.2 — Universal failure classification + unified log

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In the `else` branch where `outcome.bitmap == null` (current lines ~191–219), rewrite the failure-classification block as follows:
>
> ```kotlin
> // S0066: universal transient classification across SMB / SFTP / FTP.
> val resourceKey = extractNetworkResourceKey(source.path)
> val playbackActive = resourceKey != null &&
>     ConnectionThrottleManager.isVideoPlayerActiveForResource(resourceKey)
> val transientReason = mediaDataSource.transientFailureReason
>     ?: if (isStaleShare) TransientReason.STALE_SHARE else null
> // Timeout is transient only when playback was active for the same resource.
> val isTransient = transientReason != null ||
>     (outcome.isTimeout && playbackActive)
> val protocol = source.path.substringBefore("://", missingDelimiterValue = "local")
> val failureClass = transientReason?.name?.lowercase()
>     ?: if (outcome.isTimeout) "timeout" else "null-frame"
> Timber.w("[scope=thumbnail S0066 protocol=$protocol resource=${resourceKey ?: "n/a"} failureClass=$failureClass playbackActive=$playbackActive] Extraction failed: $fileName")
>
> if (isTransient) {
>     NetworkFileDataFetcher.markVideoAsTransientlyFailed(source.path)
> } else {
>     val cacheCheck = runBlocking {
>         try { thumbnailCacheRepository.getCachedThumbnail(source.path) } catch (e: Exception) { null }
>     }
>     if (cacheCheck == null || !cacheCheck.exists()) {
>         NetworkFileDataFetcher.markVideoAsFailed(source.path)
>     } else {
>         Timber.d("Not marking as failed — ThumbnailCache already has entry: $fileName")
>     }
> }
> null
> ```
>
> Also replace the upper transient-skip log (current line ~111) so it uses the unified format:
>
> ```kotlin
> Timber.v("[scope=thumbnail S0066 protocol=${source.path.substringBefore("://")} resource=$resourceKey] Skipping: transient failure during active playback: $fileName")
> ```
>
> Keep the rest of `decode()` untouched.

**Verification:**

- `Grep` — `\[scope=thumbnail S0066 protocol=` matches at least twice in this file.
- `Grep` — `mediaDataSource\.transientFailureReason` matches at least once.
- `Grep` — `isTransient = transientReason != null \|\|` matches exactly once.
- `Grep` — old log token `server=\$\{smbKey \?: "n/a"\}` does NOT match anywhere (replaced).
- `Grep` — `extractSmbServerKey` does NOT appear (removed in Step 03.1).
- `Grep -n "Log\.d\("` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 6/6 PASS. Files: NetworkVideoFrameDecoder.kt.

---

### Step 03.3 — Build gate + S0060 regression sanity

**Files:** —
**Depends on:** Steps 03.1–03.2

**Prompt for developer:**

> Run `/build` for `standard debug`. After it passes, perform a static-only regression check on the SMB path: `Grep` for `encounteredStaleShare` in `NetworkVideoFrameDecoder.kt` and `NetworkMediaDataSource.kt`. The decoder must still read it, and the datasource must still set it (S0060 backward compatibility).

**Verification:**

- `/build` skill returns success for `standard debug`.
- `Grep` in `NetworkVideoFrameDecoder.kt` — `mediaDataSource\.encounteredStaleShare` matches at least once.
- `Grep` in `NetworkMediaDataSource.kt` — `encounteredStaleShare = true` matches at least once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — BUILD SUCCESSFUL (standard debug, 33s, v2.60.5031.804). S0060 regression check passed.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for `NetworkVideoFrameDecoder.kt`.
- [ ] S0060 SMB pathway preserved (encounteredStaleShare still observed).

---

## Handoff Notes to Next Phase

Phase 04 will add `clearTransientFailuresForResource(resourceKey)` to `NetworkFileDataFetcher` and wire it to `deactivateVideoPlayerMode` so transient entries clear automatically when playback stops on any resource — not just SMB. The legacy `clearTransientFailuresForHost` becomes a `@Deprecated` alias.

---

## Rollback Plan

Revert phase commit — restores SMB-only classification. No data migration; no UI surface; no persistence changes.
