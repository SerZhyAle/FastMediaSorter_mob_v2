# Tactical Plan: S0116 — url-media-downloader

**Strategic spec:** [`../S0116_url-media-downloader.md`](../S0116_url-media-downloader.md)
**Feature:** URL Media Downloader (HLS/DASH, generic streaming sniffer, universal WebView auth)
**Tier:** 3 — Moderate
**Priority:** 50
**Status:** Not started
**Phases:** 8 / 8 done
**Last updated:** 2026-05-08 (post-audit, refinement pass)

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | — | ✅ Done | 8/8 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | streaming-sniffer | 01 | ✅ Done | 5/5 | [PHASE_02__streaming-sniffer.md](PHASE_02__streaming-sniffer.md) |
| 03 | streaming-downloader | 01, 02 | ✅ Done | 10/10 | [PHASE_03__streaming-downloader.md](PHASE_03__streaming-downloader.md) |
| 04 | cookie-storage | 01 | ✅ Done | 6/6 | [PHASE_04__cookie-storage.md](PHASE_04__cookie-storage.md) |
| 05 | webview-auth | 04 | ✅ Done | 6/6 | [PHASE_05__webview-auth.md](PHASE_05__webview-auth.md) |
| 06 | post-download-ux | 03, 05 | ✅ Done | 5/5 | [PHASE_06__post-download-ux.md](PHASE_06__post-download-ux.md) |
| 07 | degradation-and-logging | 02, 03, 04, 05, 06 | ✅ Done | 6/6 | [PHASE_07__degradation-and-logging.md](PHASE_07__degradation-and-logging.md) |
| 08 | docs-catalog-cleanup | all | ✅ Done | 5/5 | [PHASE_08__docs-catalog-cleanup.md](PHASE_08__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are `Closed`. No blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0116` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0116`.

---

## Blockers Log

- 2026-05-08 — Implementation paused mid-Phase 03 after step 03.1b. Reason: session-budget guard. Phases 01-02 fully Done with builds verified; Phase 03 has source-set wiring + StreamingPipeline contract in place. Next: step 03.2 (`ManifestDrmDetector`) when resumed via `/spec-dev S0116`.
- 2026-05-08 — Resumed. Phases 03 and 04 completed end-to-end (10+6 steps; standardDebug + liteDebug builds PASS; instrumentation test placeholder fixtures noted MANUAL-REQUIRED for binary content). Pausing again before Phase 05 — UI-heavy phase (≥10 new files: WebView dialog fragment+VM, sessions list fragment+VM+adapter, 4 layouts with portrait/landscape parity, EN/RU/UK strings). Next: step 05.1 (backup oversize files).

---

## Change Log

- 2026-05-08 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-08 — Post-author audit applied (claude-opus-4-7):
   - Phase 01.5: added whitelist validation for `linkDownloadMaxResolution` matching project's `videoSnapshotFormat` pattern.
   - Phase 02.4: clarified that streaming branching happens in `HtmlPageExtractionStrategy.open`, not inside `CandidateSelectionPolicy.choose` (selection contract preserved).
   - Phase 03.1: reworked source-set wiring to use shared `streamingEnabled` / `streamingDisabled` directories with explicit `getByName(<flavor>) { java.srcDir(..) }` per flavor. Added Step 03.1b to relocate `interface StreamingPipeline` and `sealed PipelineOutcome` to `main/` so `lite`/`photos` compile against the contract; previously placed them only in `streamingEnabled/` which broke the no-op flavor.
   - Phase 03.7: explicitly enumerated the **new** `Result.Failed` variants (`DrmBlocked`, `StreamingDisabled`, `MuxFailed`, `AuthRequired`) without converting existing `object`s — preserves S0003 contract.
   - Phase 03.9: moved `MediaMuxerRemuxer` test from `src/test/` (Robolectric) to `src/androidTest/` (`@RunWith(AndroidJUnit4::class)`) — `MediaMuxer`/`MediaExtractor` need real native code Robolectric does not shadow.
   - Phase 04.1: replaced deprecated `EncryptedSharedPreferences.create(masterKeyAlias=..)` API with the project's standard `MasterKey.Builder(context).setKeyScheme(AES256_GCM).build()` pattern (matches `DropboxClient.kt:81-87`, `GoogleDriveCredentialsManager.kt:33-39`).
   - Phase 05.4: removed reference to non-existent `res/navigation/settings_nav_graph.xml`. Settings sub-screen wiring switches to `SettingsActivity` + FragmentTransaction (project's actual pattern).
   - Phase 05.6: merged former Step 05.7 into Step 05.6 (the dialog has no Result handling — outcome maps in Phase 06 presenter).
   - Phase 06.3: precise line refs (181-218 in `ReceiveShareActivity.kt`); listed exact existing R.string ids removed from the activity.
   - Phase 06.4 (was 06.5): exact existing string key `link_autodownload_open_in_player_summary` (verified in `values/strings.xml:1065`).
   - Phase 06: removed former Step 06.4 — `LinkAutoDownloadProgressDialog` does not branch on Result (verified — current 94 lines show progress only).
   - Phase 07.0 (new): adds `androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")` so 07.1 can use `MockWebServer`.
   - Phase 08.1: confirmed `docs/FEATURES.md` §22 starts at line 350 with existing S0003 bullet at line 360; new bullet placement is now line-precise.

- 2026-05-08 — Tactical refinement pass (`/spec-update`, GPT-5.4):
   - Phase 01 now carries the compile-safe placeholder handling for new sealed `OpenResult` / `BlockedReason` cases so Phase 02 can land without breaking exhaustive `when` sites.
   - Phase 03 no longer owns `Result.Failed.AuthRequired`; it adds only streaming-specific failure variants, updates the existing activity mapping in the same phase, and drops the unsanctioned `coordinator.runStreaming` debug tag.
   - Phase 04 replaces `HttpCookie.toString()` persistence with an attribute-preserving serialized cookie record so domain/path/expiry/secure/httpOnly survive round-trip.
   - Phase 05 owns `AuthRequired` extraction + coordinator mapping + activity-level dialog retry flow. The settings row now follows the existing enable/disable pattern and explicitly creates `layout-land/fragment_settings_playback.xml` because no landscape counterpart exists today.
   - Phase 06 uses the actual direct result variants (`Failed.DrmBlocked`, `Failed.StreamingDisabled`, `Failed.MuxFailed`, `Failed.AuthRequired`) and only centralizes final presenter/toast UX.
   - Phase 07 shifts degradation tests to seams that already exist (`UrlExtractionStrategy` multibindings and `StreamingPipeline` module replacement) instead of assuming direct helper replacement hooks.

## Revision History

- **2026-05-08** - by `/spec-update` (`GPT-5.4`, focus: consistency, completeness, verifiability)
  - Applied: phase-ordering fixes, AuthRequired ownership alignment, verification/files-touched cleanup. Proposed (DISCUSS): 0.
