# Tactical Plan: S0565 - internet-stream-playback

**Strategic spec:** [`../S0565_internet-stream-playback.md`](../S0565_internet-stream-playback.md)
**Research inputs:** [`research/01__streaming-playback-research.md`](research/01__streaming-playback-research.md)
**Feature:** Трансляции (internet audio / video / RTSP streaming)
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 8 / 8 done
**Last updated:** 2026-06-21

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | - | ✅ Done | 5/5 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | stream-capability-contract | 01 | ✅ Done | 4/4 | [PHASE_02__stream-capability-contract.md](PHASE_02__stream-capability-contract.md) |
| 03 | source-catalog-data | 01 | ✅ Done | 4/4 | [PHASE_03__source-catalog-data.md](PHASE_03__source-catalog-data.md) |
| 04 | stream-playback | 02 | ✅ Done | 5/5 | [PHASE_04__stream-playback.md](PHASE_04__stream-playback.md) |
| 05 | source-catalog-usecases | 03 | ✅ Done | 4/4 | [PHASE_05__source-catalog-usecases.md](PHASE_05__source-catalog-usecases.md) |
| 06 | streams-screen | 04, 05 | ✅ Done | 6/6 | [PHASE_06__streams-screen.md](PHASE_06__streams-screen.md) |
| 07 | entry-points | 06 | ✅ Done | 4/4 | [PHASE_07__entry-points.md](PHASE_07__entry-points.md) |
| 08 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_08__docs-catalog-cleanup.md](PHASE_08__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are `Status: Resolved` (codebase batch-run 2026-06-20). No open research blockers - Phase 01 may start immediately.

---

## Deferred (explicit, per strategic §5.4 + §11 criterion 1)

- **Widget entry-point** - no `AppWidgetProvider` exists in the app today; adding one is a separate surface. Settings + main-window entry-points satisfy §11 criterion 1; widget deferred to a follow-up. Recorded here so it is an explicit deferral, not an omission.
- **HLS/DASH live** - `LiveConfiguration`, `BehindLiveWindow` recovery, live-offset tuning. Iteration 1 ships HLS VOD only.
- **`.pls` / `.xspf` playlist import** - iteration 1 imports only a simple remote `.m3u` plus manual URL entry (research §6 item 4).
- **RTSP UDP-only / multicast** - baseline is RTP-over-RTSP/TCP (interleaved). UDP/multicast best-effort, not gated into iteration 1 (research §6 item 3).
- **Auto-play last/favorite from a dedicated entry-point** - all entry-points open the list screen (research §6 item 5).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped this spec (strategic §8 = "Без изменений до фактической поставки"; FEATURES is populated by `/skill-release` from the `ALL_FEATURES` diff).
- [ ] `docs/ALL_FEATURES.jsonl` records the delivered "Трансляции" capability via `scripts/all_features/add.ps1`.
- [ ] `dev/CHANGELOG.md` has an entry for every logical change.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes).
- [ ] Settings doc sync regenerated (new settings entry, CLAUDE.md Rule 22).
- [ ] `/spec-check S0565` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to the matching `Block*`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0565`.

---

## Blockers Log

- 2026-06-20 - **Implementation handed off to a concurrent actor (owner decision).** Mid-Phase-03 a second actor (IDE AI / parallel agent) began implementing S0565 on the same files: it added `HTTP_STREAM`/`RTSP_STREAM` `when` branches across 24 src/main files, created `ui/player/helpers/StreamPlaybackHelper.kt` (`playStreamVideo`, minimal `MediaItem.fromUri` path - no flavor isolation / RTSP-over-TCP / ICY / streaming buffers), and wired the `VideoPlayerManager.playVideo()` dispatch. To avoid clobbering, the owner chose "let the other finish; I stop". `/spec-dev` halted. Phase 04+ is owned by that actor; `determineResourceType()` was NOT yet updated to classify stream schemes when I stopped - that routing step remains open for whoever finishes.

---

## Handoff state (2026-06-20)

My uncommitted, self-contained work left in the tree for the other actor to build on (all additive, compiles independently):

- **Phase 01** - `media3-exoplayer-rtsp` for standard/noLegal/legacy/vr; `SUPPORT_STREAMS` BuildConfig (false only in photos); cleartext base-policy relaxed; `ResourceType.HTTP_STREAM`/`RTSP_STREAM` (restored - matches the 24 branches).
- **Phase 02** - `StreamProtocolSupport` contract + `Full`/`ProgressiveOnly` impls in `streamingEnabled`/`streamingDisabled` + Hilt modules (isolates `RtspMediaSource` from lite/photos). Currently unused until a playback helper injects it.
- **Phase 03** - Room `stream_sources` catalog: entity, DAO, `MIGRATION_32_33`, `@Database` v33, `StreamSourceRepository` + DAO provider.

If the other actor's simpler path is kept, Phases 02-03 (flavor isolation + catalog) are the parts most worth salvaging; the minimal `StreamPlaybackHelper` lacks the flavor gating that lite/photos require (research §5, strategic §3.2).

---

## Device smoke (2026-06-21, emulator-5556, standard debug)

Automated smoke via `/spec-all` device-test gate - confirms UI/entry-points/runtime wiring, not network playback:

- App installs + launches; full Hilt graph resolves (no crash) with the new `streamProtocolSupport` injection.
- Main-window dropdown shows the **Streams** entry; tapping it opens `StreamsActivity` (probe `S0565: Streams screen opened` fired in logcat).
- Screen renders: toolbar (title + Add/Import/Refresh actions), cleartext note ("This screen can play public http streams, which are not encrypted."), empty state ("No streams yet. Add a URL or import a list.").
- Add dialog renders: URL field ("Stream URL (http, https, rtsp)") + optional title + OK/Cancel.
- Screenshots: `temp/s0565_smoke/streams_empty.png`, `streams_add_dialog.png`.

Still requires human on a real device/network (kept `BlockNeedUserTest`): actual progressive/HLS/RTSP/radio playback, ICY now-playing, inline-audio mini-control, fullscreen video + Back, `.m3u` import over network, lite progressive-only + RTSP-unsupported message, photos has no entry.

---

## Change Log

- 2026-06-20 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-20 - `/spec-dev` executed Phases 01-02 (gates passed) and Phase 03 (steps verified, compile gate not run). Halted at Phase 03 boundary on owner decision after detecting a concurrent implementation of the same ticket.
- 2026-06-21 - `/spec-all` resumed (owner: take over and finish). Reconciled Phase 03 + the concurrent actor's half-wired enum seam (patched Phase 04 to the enum routing). Completed Phases 04-08: stream playback, catalog use cases, Streams screen, dropdown + Playback-settings entry-points, docs/catalog/gates. standard+lite+photos compile; settings-doc-sync + neuroslop green; full standard debug APK built. 4 device-test probes inserted; spec -> `BlockNeedUserTest`; emulator smoke passed (UI + entry-points + render).
