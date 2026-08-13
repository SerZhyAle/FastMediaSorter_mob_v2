# S0772 - OutOfMemoryError crash playing 7K VR video on Quest 3

**Status:** Archived

## 0. Raw capture / evidence

Device: Oculus Quest 3 (eureka), Android 14, 7.58 GB RAM, heapMax 512 MB, MemoryTier=LOW.
App: 2.60.6281.708-NoLegal-DEBUG (260628170).
File: `/storage/emulated/0/Movies/18VR_The_Best_is_Yet_to_Come_7K_180_180x180_3dh.mp4`
- 7168x3584, HEVC, 60fps, bitrate ~40 Mbit/s, size 21,885,990,726 bytes (~20.4 GB).

Crash (`logs/fastmediasorter_crash_20260628_180058.log`):
```
=== Thread: ExoPlayer:Playback [id=1929] ===
java.lang.OutOfMemoryError: Failed to allocate a 288 byte allocation with 9632 free bytes
and 9632B until OOM, target footprint 536870912, growth limit 536870912;
giving up on allocation because <1% of heap free after GC.
```

Sustained memory pressure on the same file across the session
(`logs/fastmediasorter_20260628_180102.log`, `..._172525.log`):
```
VideoPlayerManager: native heap low before playback - free=2..20MB, running Glide eviction + GC
VideoPlayerManager: native heap after GC - free=2MB
getFrameAtTime skipped ... reason=DECODER_BUSY fallback=glide-memory  (nativeFreeMb=2..9)
MEM_ENDURANCE | SUMMARY | scenario=VID-playback | peak=382..430MB | verdict=FAIL
MEM_ENDURANCE | COOLDOWN_RESULT | drift_from_baseline=350%
```

## 1. Symptom

Playing a very large (~20 GB) 7K/60fps HEVC VR video on a LOW-tier 512 MB-heap headset drives the heap to its growth limit and crashes with `OutOfMemoryError` on the ExoPlayer playback thread. Before the crash, native heap is repeatedly near-zero, frame extraction is skipped (DECODER_BUSY), and endurance verdict is FAIL with 350% drift.

## 2. Root cause (researched 2026-06-28)

Primary driver - unbounded heap-resident buffer for high-bitrate content:
- `PrefetchLoadControlFactory.build()` ([app_v2/.../helpers/PrefetchLoadControlFactory.kt:78](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchLoadControlFactory.kt#L78)) calls `setPrioritizeTimeOverSizeThresholds(true)` and never sets `setTargetBufferBytes(..)`. With time-over-size priority, `DefaultLoadControl` ignores its byte cap and buffers `maxBufferMs` worth of data regardless of bitrate.
- Default video `MAX_BUFFER_MS = 30_000` ([VideoPlayerManager.kt:176](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt#L176)). The crash file is HEVC ~40 Mbit/s (~5 MB/s), so 30 s buffer ≈ 150 MB.
- ExoPlayer `DefaultAllocator` allocates buffer chunks as `byte[]` on the **Java heap**, not native. heapMax is 512 MB (Quest 3, LOW tier). ~150 MB of buffer + decoder + Glide + UI drives heap to the growth limit -> `OutOfMemoryError` on `ExoPlayer:Playback` (heapUsed peaked 382 MB in MEM_ENDURANCE).
- `targetBufferBytes` is set nowhere in the codebase (grep clean), confirming the byte budget is effectively unbounded for this path.

Contributing:
- `PrefetchLoadControl[createPlayer]: fallback standard defaults` (Timber.w) - no `PrefetchPlan` delivered before `createPlayer()`, so even the plan-based sizing path is bypassed for this file.
- `getFrameAtTime` thumbnail extraction firing at native free=2-9 MB (already partly guarded, still adds pressure).

## 3. Fix implemented (2026-06-28)

Single-file change in [PrefetchLoadControlFactory.build()](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchLoadControlFactory.kt) - the factory feeds every video path (local, SMB, FTP, SFTP, cloud; plan and no-plan fallback), so one change covers all 5 call sites.

- When the process Java heap-limit is `<= 512 MB` (the LOW-tier dominant signal, matching `MemoryTier.classify`), the video `LoadControl` now gets an explicit `setTargetBufferBytes(cap)` and `setPrioritizeTimeOverSizeThresholds(false)` so the byte cap is actually enforced (with time-priority on, the cap is ignored below `minBufferMs`).
- Cap is heap-derived: `heapMax / 8`, clamped to `[24 MB, 96 MB]`. For a 512 MB heap that is 64 MB vs the previously unbounded ~150 MB.
- Normal-bitrate content is unaffected: it reaches `maxBufferMs` (30 s) in time well before the 64 MB byte budget binds, so only pathological high-bitrate content is capped.
- Audio path untouched (`isAudio` buffers are already <= 20 s of low-bitrate data; cap never binds).
- Heap source is `Runtime.maxMemory()` read inside the factory - the same signal `MemoryTier` uses - so no `MemoryProfile` injection threading through the 5 call sites.
- Cap resolver `videoBufferCapBytesForHeap(maxHeapMb)` is pure + `internal`, unit-tested (`PrefetchLoadControlFactoryTest`, 4 new cases, all green).

Not done in this change (separate, non-OOM-critical now that the buffer is bounded):
- `PrefetchPlan` still falls back to standard defaults at `createPlayer()` (Timber.w in §2 contributing) - the byte cap makes that path safe regardless, so plan delivery is a separate optimisation.
- Frame-extraction / Glide deferral on LOW tier (§2 contributing) - already partly guarded; the buffer was the decisive ~150 MB driver.

## 5. Device verification required (BlockNeedUserTest)

- The fix mutates the release-critical buffering path for **all** video; confirming it (a) eliminates the OOM and (b) introduces no rebuffering/stutter regression requires the actual Quest 3 (LOW tier, 512 MB heap) with the ~20 GB 7K HEVC asset. No emulator reproduces this.
- On device, play `18VR_The_Best_is_Yet_to_Come_7K_180_180x180_3dh.mp4` (and a normal-bitrate video for regression) and confirm in the log:
  - `PrefetchLoadControl[createPlayer]: heap-bounded video buffer cap=64MB (heapMax=512MB)` appears at player creation.
  - `S0772: video LoadControl heap-bounded ...` debug marker fires.
  - No `OutOfMemoryError` on `ExoPlayer:Playback`; `MEM_ENDURANCE` peak stays well below 512 MB; normal-bitrate playback shows no new rebuffering.

## 4. Notes

- Prior OOM hardening S0213 (bugfix-video-playback-oom-hardening, Archived) did not cover this 7K-on-512MB case - this is a fresh recurrence.
- Same file also misclassified in immersive XR - tracked separately (S0771).
