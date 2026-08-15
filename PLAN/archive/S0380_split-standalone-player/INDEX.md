# Tactical Plan: S0380 - split-standalone-player

**Strategic spec:** [`../S0380_split-standalone-player.md`](../S0380_split-standalone-player.md)
**Feature:** Разделение автономного плеера на специализированные активности
**Tier:** 3 - Moderate
**Priority:** 50
**Status:** BlockNeedUserTest (all phases implemented; awaiting on-device verification)
**Phases:** Phase 01 skipped; 02/03/04/05/06/07 ✅ all done (6/6 active phases)
**Last updated:** 2026-06-08

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Current-State Findings (research, 2026-06-07)

- `ui/player/StandalonePlayerActivity.kt` (≈939 LOC) is the single external-intent player. It has no direct `intent-filter`; routing is done by 8 `activity-alias` entries in `src/main/AndroidManifest.xml`, all `android:enabled="false"` by default.
- VIEW aliases target `StandalonePlayerActivity`: `.StandaloneAudioPlayer`, `.StandaloneVideoPlayer`, `.StandaloneImagePlayer`, `.StandaloneDocsPlayer`.
- SEND aliases target `ui/share/ReceiveShareActivity`: `.StandaloneAudioSender`, `.StandaloneVideoSender`, `.StandaloneImageSender`, `.StandaloneTextSender`.
- Aliases are enabled/disabled at runtime by the "default player" feature: `ui/settings/helpers/DefaultPlayerManager.kt`, `ui/settings/helpers/DefaultPlayerHelper.kt`, `core/init/DefaultPlayerStateBootstrapper.kt` (via `PackageManager.setComponentEnabledSetting`).
- Standalone helpers already exist and are reusable: `StandaloneFileOperationsHandler`, `StandaloneFullscreenManager`, `StandaloneViewManager`, `StandaloneVideoControlsManager`, `StandaloneVideoTouchDelegate`, `StandalonePlayerLifecycleManager`, `StandalonePlayerSettingsManager`, `StandaloneLaunchDebugLogger`.
- Viewer managers are shared and heavy: `PdfViewerManager` (≈1023), `EpubViewerManager` (≈1033), `TextViewerManager` (≈975), `OfficeDocumentViewerManager`, `BaseDocumentViewerManager`.
- Internal player `ui/player/PlayerActivity.kt` (≈1095 LOC) is out of scope for behavior change but is the parity reference for §5.1 Functional Alignment.

## Parity Audit (2026-06-07)

Compared `PlayerActivity` (internal, manager surface at fields ~94-206) against `StandalonePlayerActivity` (full read).

- **Shared foundation already in place:** `PlayerHostCapabilities` contract implemented directly by both activities; shared viewer managers (Pdf/Epub/Text/Office), `PlayerKeyboardHandler`, standalone file-ops/fullscreen/PiP/video-controls/stereo/document-search helpers. Strategic goal §5.1 (Functional Alignment + Unified Playback Logic) is substantially pre-done by the prior `standalone-vs-inapp-player-parity` spec.
- **Per-file features internal-only (NOT in standalone):** image editing (crop / rotate / flip / filters / adjust), GIF ops (extract frames / save first frame / change speed), image OCR, Google Lens, save video frame, audio lyrics, document print, draw overlay, text editor + calculator bridge, broader image translation, audio cover/metadata search, VR launch, Cast.
- **By design:** standalone is "detached from resource/database tree - no resource system, no playlists, no history"; `supportsCast` / `supportsPersistentAudio` / `supportsListNavigation` / `supportsSlideshow` are intentionally false.
- **Critical-for-reuse gaps blocking the split: NONE.** The standalone player already works and already shares the contract + helpers. Splitting into per-type activities only needs to preserve current standalone behavior.
- **Deferral:** broad per-file parity (the internal-only list above) is out of S0380 scope per owner "minimal critical parity" + §2 Non-goals. Open a separate follow-up ticket if/when standalone should gain those features.

Plan impact: Phase 01 → Skipped (foundation already coded). Phase 02 → reduced to a parity gap-check + deferral note (no broad feature implementation). Real work starts at Phase 03 (dispatcher).

## Phase 04 Architectural Finding (2026-06-07)

`StandaloneViewManager` and the three document viewer managers (`PdfViewerManager`, `EpubViewerManager`, `TextViewerManager`, ~1000 LOC each) are **hard-bound to `ActivityPlayerUnifiedBinding`** (the single monolithic layout). Photo/video/audio also drive `binding.photoView` / `binding.playerView`. These managers are **shared with the internal `PlayerActivity`**.

Consequence: a trimmed per-type layout produces a different generated binding type the managers will not accept. Strategic Goal §2.2 / Criterion 3 (≥40% inflation reduction via lighter layouts) is **not achievable without first decoupling the viewer managers from `ActivityPlayerUnifiedBinding`** - a refactor of code shared with the internal player. Without that decouple, only the class-loading win (Criterion 4: text activity avoids ExoPlayer) is reachable, by reusing the full layout.

This was under-specified in the tactical plan. Phase 04 approach pending owner decision (incremental per-type decouple vs class-loading-only vs explicit decouple phase).

---

## Migration Safety Principle

- `StandalonePlayerActivity` is repurposed as the shared base / dispatcher host, not deleted outright. Specialized activities reuse the existing helpers and viewer managers - no viewer logic is rewritten.
- The manifest cutover (Phase 05) is the only behavior-affecting change; every prior phase is additive or behavior-preserving and must keep the existing player launchable.
- The "default player" toggle (`DefaultPlayerManager` + bootstrapper) must enable/disable the new per-type components in lockstep with the aliases - a half-toggled state would break launch.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations-host-contract | - | ⏭️ Skipped | - | [PHASE_01__foundations-host-contract.md](PHASE_01__foundations-host-contract.md) |
| 02 | functional-alignment (gap-check) | - | ✅ Done | 1/1 | [PHASE_02__functional-alignment.md](PHASE_02__functional-alignment.md) |
| 03 | dispatcher-activity | 04 (for Step 03.2) | ✅ Done | 2/2 | [PHASE_03__dispatcher-activity.md](PHASE_03__dispatcher-activity.md) |
| 04 | specialized-activities (decouple-first) | 02 | ✅ Done | 4/4 | [PHASE_04__specialized-activities.md](PHASE_04__specialized-activities.md) |
| 05 | manifest-routing-default-player | 03, 04 | ✅ Done | 3/3 | [PHASE_05__manifest-routing-default-player.md](PHASE_05__manifest-routing-default-player.md) |
| 06 | flavor-parity | 05 | ✅ Done | 2/2 | [PHASE_06__flavor-parity.md](PHASE_06__flavor-parity.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

> Phase 01 skipped: `PlayerHostCapabilities` foundation already exists in code (see Parity Audit). Phase 02 reduced to a gap-check; broad per-file parity is deferred to a separate ticket.

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. Strategic §6 research item 1 (undefined MIME handling) is Resolved (dispatcher trampoline). The residual "system selector behavior with overlapping MIME types" is investigated in-phase at Phase 05, not a hard pre-implementation blocker.

---

## Completion Gate

- [x] All phases show ✅ Done. (Phase 01 skipped; 02-07 done)
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped (strategic §8 = "Без изменений"; 0 S0380 refs verified).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (1688 records; new classes have role/status).
- [ ] `/spec-check S0380` returns `Verified`. (after on-device verification - status is `BlockNeedUserTest`)
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0380`.

---

## Blockers Log

- 2026-06-07 - Phase 03 build-validation paused. `MediaFamilyResolver` (Step 03.1) is code-complete and static-verified, but the branch does not compile due to **unrelated uncommitted WIP**: `ui/player/FileOperationsHandler.kt:81` uses `Uri.parse` without `import android.net.Uri` (one of ~27 modified files, an in-progress UriPathResolver/SafHelper refactor). Owner will finish that WIP; re-run the Phase 03 build then. Also: Step 03.2 (dispatcher activity) reordered to run after Phase 04 (it forwards to the specialized activities).
- 2026-06-07 - Phase 01 hard-stop (premise already satisfied). `PlayerHostCapabilities` already exists at `ui/player/contracts/PlayerHostCapabilities.kt` and is implemented directly by **both** `PlayerActivity` and `StandalonePlayerActivity` (legacy of the `standalone-vs-inapp-player-parity` spec). The contract KDoc mandates "both activities implement this interface directly" - so Step 01.2's separate `StandalonePlayerHostController` contradicts the established architecture, and Step 01.1 would duplicate an existing interface (build break). Phases 01-02 (foundation + functional alignment) are largely pre-done by that prior spec. Plan must be revised via `/spec-update` before execution: drop/repurpose Phase 01, reduce Phase 02 to a parity gap-check, start real work at Phase 03 (dispatcher). No code written.

---

## Change Log

- 2026-06-07 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-07 - Parity audit + plan revision (owner-directed). Phase 01 → Skipped (`PlayerHostCapabilities` foundation already in code); Phase 02 → reduced to a parity gap-check with broad per-file parity deferred to a separate ticket. Phases 03-07 (the actual split) unchanged. Depends-on references to the skipped Phase 01 dropped.
