# Tactical Plan: S0207 — radical-memory-reduction

**Strategic spec:** [`../S0207_radical-memory-reduction.md`](../S0207_radical-memory-reduction.md)
**Feature:** Radical memory reduction (toast `warning_low_memory_playback` on cold start + first MP3)
**Tier:** 4 — Strategic (ad-hoc)
**Priority:** 85
**Status:** BlockNeedUserTest
**Phases:** 1 / 8 done (Phase 01 — 5/6 steps done, 1 deferred; Phase 02 — static 3/3, calibration deferred to on-device run; Phase 03 — 8/8 implemented but blocked by an unrelated noLegal Chaquopy compile failure; Phase 04 — 5/6 implemented, calibration pending; Phase 05 — 4/5 implemented, audio calibration pending; Phase 06 — 4/5 implemented, startup calibration pending; Phase 07 — 4/5 implemented, idle-disconnect calibration pending)
**Last updated:** 2026-05-15

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

> **Coordination note:** the strategic header still says `Tactical`. That mismatch is currently intentional and preserved in-place because `/spec-update` does not mutate status fields outside the owning workflow.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | memory-instrumentation | — | 🚧 In Progress | 5/6 | [PHASE_01__memory-instrumentation.md](PHASE_01__memory-instrumentation.md) |
| 02 | memory-tier-reclassification | 01 | ✅ Done (calibration deferred) | 3/3 | [PHASE_02__memory-tier-reclassification.md](PHASE_02__memory-tier-reclassification.md) |
| 03 | memory-profile-abstraction | 01, 02 | ⛔ Blocked | 8/8 | [PHASE_03__memory-profile-abstraction.md](PHASE_03__memory-profile-abstraction.md) |
| 04 | adaptive-rgb565-pressure | 01, 03 | 🚧 In Progress | 5/6 | [PHASE_04__adaptive-rgb565-pressure.md](PHASE_04__adaptive-rgb565-pressure.md) |
| 05 | small-allocations | — | 🚧 In Progress | 4/5 | [PHASE_05__small-allocations.md](PHASE_05__small-allocations.md) |
| 06 | startup-workers-defer | 01 | 🚧 In Progress | 4/5 | [PHASE_06__startup-workers-defer.md](PHASE_06__startup-workers-defer.md) |
| 07 | network-idle-disconnect | 01 | 🚧 In Progress | 4/5 | [PHASE_07__network-idle-disconnect.md](PHASE_07__network-idle-disconnect.md) |
| 08 | docs-catalog-cleanup | all | 🚧 In Progress | 3/4 | [PHASE_08__docs-catalog-cleanup.md](PHASE_08__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

## Phase Summary Notes

- Phase 01 is an observability foundation only: five checkpoints are wired today, `THUMBNAILS_LOADED` is still deferred, and legacy `MEMORY_DEBUG` output remains in the image-loading path.
- Phase 03 code is landed: startup Glide-budget selection now comes from `MemoryProfileCoordinator`, browse/player emit runtime scenarios, and VR contributes a single RGB888 override reused by `noLegal`. Final phase closure is still blocked by an unrelated noLegal Chaquopy compile failure.
- Phase 04 code is landed: pressure-aware decode-format decisions are centralized in `MemoryPressureDecodeFormatResolver`, request builders use it, and the non-Glide bitmap decoders now read the same bitmap-config decision. Only canonical logcat calibration remains open.
- Phase 05 must preserve the current extension-text placeholder UX. Allocation reduction comes from dedup and smaller caches, not from replacing text badges with a generic audio icon.
- Phase 05 code is landed: extension placeholders now reuse the shared 96 px generator path, upstream placeholder caches are much smaller, and audio sessions use compact local/network load-control profiles. Local+SFTP MP3 runtime verification is still pending.
- Phase 06 code is landed: startup maintenance moved behind `DeferredStartupWorker`, `FastMediaSorterApp` now waits for the first visible frame before releasing deferred startup work, and `AppStartupInitializer` keeps only the Glide cache-size mirror on the eager path. Cold-start logcat calibration and worker-runtime evidence are still pending.
- Phase 07 code is landed: a shared `IdleDisconnectPolicy` now arms 30 s idle timers for SFTP/SMB/FTP transport choke points, `SftpConnectionPool` has an independent periodic sweep, and focused JVM coverage exists for the timer contract plus the SFTP sweep lifecycle. Runtime reconnect/logcat evidence is still pending.
- Phase 07 must touch both `SftpClient` and `SftpConnectionPool`: current SFTP cleanup is reactive-only, so a per-session timer alone is insufficient.

---

## Pre-Implementation Blockers

Treat the following as execution guards before `/spec-dev` resumes. They do not force a status flip by themselves, but they do define what later phases are allowed to claim:

- [ ] **Phase 01 reality gap** — observability is still partial: `THUMBNAILS_LOADED` has no callback yet, `MemoryProbeImpl` currently logs in all builds, and `ImageLoadingDiagnostics` still emits `MEMORY_DEBUG`. No later phase may claim six-point coverage or full single-channel consolidation unless one of those gaps is actually closed.
- [ ] **Phase 03 scope guard** — runtime scenario changes may update coordinator state, RGB565 policy, and best-effort `Glide.clearMemory()/trimMemory()` hooks. They may **not** claim live process-wide Glide cache re-sizing unless a separate follow-up spec explicitly introduces a restart/re-init strategy.
- [ ] **Research 2 + 7 remain critical** — the leading hypothesis is browse-side audio metadata extraction, not Glide. Phase 05 calibration must isolate that delta before S0207 can be considered on track for closure.
- [ ] **Research 1 is no longer a Phase 03 blocker** — Phase 03 uses a conservative non-zero startup budget. True zero-cache audio mode remains a follow-up question only if later evidence says it is needed.
- [ ] **Research 3** — **JSch** native buffer release on `Session.disconnect()` → resolved in Phase 07 Step 07.3 measurement.
- [ ] **Research 4** — safe threshold for adaptive RGB565 → resolved in Phase 04 Step 04.6 calibration.
- [ ] **Research 5** — which startup workers can be deferred → resolved in Phase 06 Step 06.1 audit (full audit table available in `temp/S0207_research/05_startup_workers_audit.md`).
- [ ] **Research 6** — VR-flavor profile requirement → resolved in Phase 03 Step 03.8 via a single VR-source-set override reused by `vr`, `vrUnlicensed`, and `noLegal`; no separate `noLegal` binding unless behaviour diverges.
- [ ] **Research 8** — eager startup registration of network image-loader components defeats S0194 lazy-singletons → resolution phase TBD after Phase 03 lands.
- [ ] **Research 9** — `MediaFilesCacheManager` 128 MB process-scope Java-LRU → resolution candidate if Phase 05 calibration still shows large browse-to-play delta after icon/buffer wins.
- [ ] **Missing tests must be planned with the code changes** — MemoryTier (Phase 02), GlideAppModule + coordinator startup contract (Phase 03), AdapterThumbnailLoader placeholder reuse plus any AudioMetadataLoader / MediaFilesCacheManager mitigation (Phase 05), AppStartupInitializer / FirstFrameSignal (Phase 06), and SftpConnectionPool idle sweep (Phase 07).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] Phase 01 remaining gaps are either actually closed (`THUMBNAILS_LOADED`, debug-only gating, `MEMORY_DEBUG` consolidation) or explicitly accepted as deferred by the final audit.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — skip; strategic §8 states "Без изменений".
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] Targeted tests are added or updated for the touched slices: MemoryTier, GlideAppModule / coordinator startup contract, AdapterThumbnailLoader placeholder reuse, AppStartupInitializer / FirstFrameSignal, and SftpConnectionPool idle sweep. If Phase 05 expands into AudioMetadataLoader or MediaFilesCacheManager mitigation, those slices get dedicated tests too.
- [ ] `/spec-check S0207` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.
- [ ] Acceptance scenario passes on emulator with `heapMax=512MB` for audio-capable flavors only (`photos` signs off the shared image/cache/startup/network phases instead):
  - cold start → SFTP browse with 7 MP3 → tap MP3
  - `Debug.getNativeHeapAllocatedSize()` ≤ 30 MB
  - `Debug.getNativeHeapFreeSize()` ≥ 30 MB
  - Toast `warning_low_memory_playback` does NOT fire

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0207`.

---

## Blockers Log

- 2026-05-15 — Phase 01 Step 01.6 `THUMBNAILS_LOADED` probe DEFERRED: the spec assumed an existing "initial load complete" callback on `AdapterThumbnailLoader`, but the adapter only exposes per-row `load(...)` (driven by RecyclerView/Glide). Wiring this probe needs a new aggregation hook (likely on `MediaFileAdapter` via debounced Glide completion or `RecyclerView.OnGlobalLayoutListener`). Out of Phase 01 scope; re-evaluate in Phase 03.
- 2026-05-15 — Phase 01 still has two non-step closure gaps: `MemoryProbeImpl` logs unconditionally (strategic wish says debug-build by default) and `ImageLoadingDiagnostics` still emits `MEMORY_DEBUG` through the image-loading path. Treat Phase 01 as a partial observability foundation until one of those outcomes is explicitly chosen.
- 2026-05-15 — Phase 03 runtime Glide assumption corrected: use a conservative startup budget once per process, then rely on runtime trim/clear + decode-policy. No phase may claim live process-wide cache resizing without a dedicated follow-up.
- 2026-05-15 — Browse-side audio metadata extraction plus process-scope media-list caching remain the leading explanation for the canonical MP3 memory spike. Phase 05 must measure them explicitly before S0207 can credibly target `Verified`.
- 2026-05-16 — Phase 01 gap partially closed: `MemoryProbeImpl.record()` and `ImageLoadingDiagnostics.logMemoryStats()` are now both gated behind `BuildConfig.DEBUG`. Release builds no longer pay the measurement cost. Two separate channels still coexist in debug builds (`MEM_PROBE` + `MEMORY_DEBUG`); full single-channel consolidation of the image-loading path remains a follow-up.
- 2026-05-15 — Phase 04 calibration is still pending because no canonical logcat session has been run after the resolver/bypass-decoder wiring landed. Until that run happens, the phase cannot claim whether pressure mode triggered or stayed dormant.
- 2026-05-15 — Phase 05 calibration is still pending: code/test/build work is done, but no local+SFTP MP3 playback session has yet confirmed audible behaviour or measured the `AFTER_STATE_READY` delta. Browse-side audio metadata extraction / process-scope media-list caching remain the main residual suspects until that measurement exists.
- 2026-05-15 — Phase 06 calibration is still pending: the first-frame gate, deferred worker, and startup single-shot tests are landed, but no cold-start logcat session has yet recorded the `APP_STARTED` / `MAIN_DRAWN` deltas or confirmed `DeferredStartupWorker` execution 30..40 s after launch.
- 2026-05-16 — Phase 07 calibration is still pending: code/tests/build work is done, but no logcat session has yet confirmed `IdleDisconnect: timeout fired` and transparent reconnect for SFTP/SMB/FTP after 30 s of inactivity.

---

## Change Log

- 2026-05-15 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-15 — Phase 01 (memory-instrumentation): 5/6 steps done, build PASS; THUMBNAILS_LOADED probe deferred. Catalog status flipped to `In Progress`.
- 2026-05-15 — `/spec-update` applied seven-agent research findings: SSHJ→JSch correction; new strategic research items 7-9 (MetadataRetriever per audio row, eager Glide EntryPointAccessors, MediaFilesCacheManager 128 MB); strategic §5.8 extended to cover upstream LRUs; strategic §7 risk row for Cast SDK eager init; Phase 02 boundary `< 512` → `<= 512`; Phase 03 step 03.8 (VR / noLegal `MemoryProfileCoordinator` override) added; Phase 04 expanded from 4 to 6 steps (resolver + thumbnail loader + bypass decoders); Phase 05 step 05.4 (icon-generator LRU shrink + RGB_565); Phase 06 step 06.4 (FirstFrameSignal + generalized deferral primitive); Phase 07 SFTP per-session timer + pool-level periodic sweep.
- 2026-05-15 — `/spec-update` refined execution realism: strategic/tactical narrative now excludes `photos` from audio/video/player acceptance, Phase 01 explicitly remains partial, Phase 03 was rewritten around startup-only Glide cache sizing plus runtime trim/clear, Phase 05 preserves extension-text placeholder UX, and Phase 07 now explicitly lists `SftpConnectionPool.kt` in scope.
- 2026-05-15 — Phase 02 (memory-tier-reclassification) static closure: predicate `<= 512 MB heap -> LOW` + HIGH preservation path implemented; `internal fun classify(...)` extracted for JVM unit coverage; `MemoryTierTest` 7 cases (incl. canonical emulator + Quest 3 boundary) pass. Calibration measurement (Step 02.3) deferred to operator's on-device run; not gating later phases.
- 2026-05-15 — Phase 03 implementation landed: added coordinator-driven startup Glide budget, runtime browse/player scenario emission, best-effort audio/browse memory release hooks, a single VR RGB888 override reused by `noLegal`, and focused unit coverage. Phase closure remains blocked by the unrelated noLegal Chaquopy compile failure.
- 2026-05-15 — Phase 04 implementation landed: added `NativePressureMonitor`, `MemoryPressureDecodeFormatResolver`, non-Hilt entry points, pressure-aware request wiring in browse/player paths, resolver-backed bypass decoder configs, and focused unit coverage. Calibration/logcat capture remains open.
- 2026-05-15 — Phase 05 implementation landed: kept extension-text placeholder UX while reusing the shared generator path, added compact local/network audio buffer profiles, routed network helpers through the audio-aware load-control selection, shrank upstream extension/binary thumbnail caches, and added focused unit coverage. Local+SFTP MP3 calibration remains open.
- 2026-05-15 — Phase 06 implementation landed: wrote the startup audit artifact, added `DeferredStartupWorker`, split `AppStartupInitializer` into eager/deferred paths, gated deferred startup work on `FirstFrameSignal`, added focused JVM coverage for the startup single-shot contract, and refreshed dev log + catalog metadata. Cold-start runtime validation remains open.
- 2026-05-16 — Phase 07 implementation landed: added the shared idle-disconnect policy contract + implementation + Hilt binding, wired SFTP/SMB/FTP request choke points to 30 s idle timers, added `SftpConnectionPool` periodic sweep coverage, and refreshed dev log + catalog metadata. Runtime idle-disconnect calibration remains open.

---

## Revision History

- **2026-05-15** — by `/spec-update` (Claude Opus 4.7, focus: completeness, consistency)
  - Applied: research-item retargeting (SSHJ→JSch in Research 3), three new research items (7, 8, 9), phase step-count updates (03: 7→8, 04: 4→6, 05: 4→5, 06: 4→5), pointer fix from "Phase 03 Step 03.6" → "Phase 03 Step 03.8" for Research 6, pointer fix from "Phase 04 Step 04.2" → "Phase 04 Step 04.6" for Research 4 (calibration step renumbered). Proposed (DISCUSS): 0.
  - Evidence: `temp/S0207_research/00_SUMMARY.md` + six per-agent reports.
- **2026-05-15** — by `/spec-update` (GPT-5.4, focus: consistency, completeness, verifiability)
  - Applied: added explicit scope guards for the known Phase 01 partial state, narrowed Phase 03 to startup-only Glide budgeting plus runtime trim/clear, moved the VR/noLegal plan to a single VR-source-set override reused by `noLegal`, added explicit test expectations for the missing slices named in the audit, clarified that `photos` is excluded from audio/video/player acceptance, and strengthened the blocker trail around audio metadata extraction / media-list caching. Proposed (DISCUSS): 0.
