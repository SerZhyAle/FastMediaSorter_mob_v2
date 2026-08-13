# Tactical Plan: S0386 - ondemand-ocr-translation-delivery

**Strategic spec:** [`../S0386_ondemand-ocr-translation-delivery.md`](../S0386_ondemand-ocr-translation-delivery.md)
**Feature:** On-demand OCR/translation/heavy-asset delivery
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 65
**Status:** BlockNeedUserTest (all 13 phases done & six flavors build-green; de-bundle, restructure, AND the upgrade force-OFF migration device-verified on emulator API 33; remaining = native-attach on real OCR/DTS use + release unpack-verify; 11.3 manifest-banner deferred cosmetic)
**Phases:** 13 / 13 done (12.2 N/A, 11.3 deferred cosmetic)
**Last updated:** 2026-06-10

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | recognize-translate-facade-split | - | ✅ Done | 5/5 | [PHASE_01__recognize-translate-facade-split.md](PHASE_01__recognize-translate-facade-split.md) |
| 02 | capability-contract-state | 01 | ✅ Done | 5/5 | [PHASE_02__capability-contract-state.md](PHASE_02__capability-contract-state.md) |
| 03 | default-off-migration | 02 | ✅ Done | 2/2 | [PHASE_03__default-off-migration.md](PHASE_03__default-off-migration.md) |
| 04 | delivery-channel-core | 02 | ✅ Done | 5/5 | [PHASE_04__delivery-channel-core.md](PHASE_04__delivery-channel-core.md) |
| 05 | set-definitions-build-debundle | 04 | ✅ Done | 7/7 | [PHASE_05__set-definitions-build-debundle.md](PHASE_05__set-definitions-build-debundle.md) |
| 06 | enable-intercept-ux | 03, 04 | ✅ Done | 5/5 | [PHASE_06__enable-intercept-ux.md](PHASE_06__enable-intercept-ux.md) |
| 07 | attach-autorun | 05, 06 | ✅ Done | 4/4 | [PHASE_07__attach-autorun.md](PHASE_07__attach-autorun.md) |
| 08 | extensions-manager-screen | 06 (ahead of 07) | ✅ Done | 4/4 | [PHASE_08__extensions-manager-screen.md](PHASE_08__extensions-manager-screen.md) |
| 09 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_09__docs-catalog-cleanup.md](PHASE_09__docs-catalog-cleanup.md) |
| 10 | extensions-entry-relocation | 08 | ✅ Done | 3/3 | [PHASE_10__extensions-entry-relocation.md](PHASE_10__extensions-entry-relocation.md) |
| 11 | extensions-screen-grouping | 08 | ✅ Done | 2/3 | [PHASE_11__extensions-screen-grouping.md](PHASE_11__extensions-screen-grouping.md) |
| 12 | language-data-into-groups | 11 | ✅ Done | 2/3 | [PHASE_12__language-data-into-groups.md](PHASE_12__language-data-into-groups.md) |
| 13 | upgrade-state-reconciliation | 03 | ✅ Done | 2/2 | [PHASE_13__upgrade-state-reconciliation.md](PHASE_13__upgrade-state-reconciliation.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

These are tactical research items left open by strategic §6.1 plus one feasibility spike surfaced during tactical grounding. Phases 04, 05, 07 must not start while their blocker is unchecked. Phases 01, 02, 03, 06 do not depend on these and may proceed first.

- [x] **Research B1 - Source mapping per set (resolved 2026-06-09):** module `.so`/asset sets (A/B/C/D) have no vendor URL → our GitHub mirror is primary. Language data keeps its vendor URL primary + our mirror as auto-failover: Tesseract `.traineddata` = `tessdata_best/4.1.0`, Paddle `.nb` = `paddlelite-demo.bj.bcebos.com`. ML Kit translate language packs stay ML-Kit-managed (not in our sets). See strategic §6.1 B1.
- [x] **Research B2 - Authenticity & versioning format (resolved 2026-06-09):** SHA-256 app-pinned (compiled hash + min-size, like `TesseractModelManager.SHA256_RUS`) as the authenticity anchor; detached signature rejected. Remote manifest supplies URLs only (keyed by `versionCode`, tag `delivery-v<versionCode>`), never native `.so` hashes; bundled fallback descriptor covers the shipping version. Verify size+SHA-256 before `System.load`. See strategic §6.1 B2.
- [~] **B3 - host deliverable `.so` (partially done 2026-06-09):** no rebuild needed (all `.so` already ≥16 KB-aligned). OSS sets hosted + verified: GitHub release `delivery-so-v1` carries Tesseract/Paddle (Set B) + FFmpeg DTS (Set D), arm64-v8a; public download confirmed (HTTP 200, SHA-256 match). URLs + app-pinned SHA-256/size in `temp/S0386_B3_so_staging.md`. **Remaining:** OSS `armeabi-v7a` upload for 32-bit store coverage. Set A is no longer part of mirror-hosting scope after the 2026-06-09 decision (store = Play dynamic-feature, sideload/VR = bundled). Gates Phase 05.
- [x] **Research B4 - ML Kit native-lib delivery feasibility (spike, done 2026-06-08):** ML Kit `.so` have no clean self-delivery path - Play dynamic-feature needs a Play install (excludes sideload/Quest); ML Kit unbundled needs GMS (excludes Quest). **Final owner decision (2026-06-09, supersedes the 2026-06-08 self-load plan):** do not re-host Google `.so`; Set A = Play dynamic-feature on store flavors, ML Kit Translate stays **bundled** on sideload/VR (`noLegal`/`vr`). OCR drops ML Kit Text-Recognition entirely (Tesseract/Paddle cover OCR). See strategic §6.1 B4 and §5.4.
- [x] **Gate B4-PoC - OBSOLETE (2026-06-09):** self-load of ML Kit Translate is no longer used (store = Play dynamic-feature, sideload/VR = bundled), so the on-device self-load proof is moot. No device test gates Phase 05 anymore.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a behavior-change sentence); `docs/FEATURES_noLegal*` updated for the self-download specifics.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API changed).
- [ ] `/spec-check S0386` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0386`.

---

## Blockers Log

- 2026-06-08 - Authored with 4 open Pre-Implementation Blockers (B1-B4). Phases 04/05/07 gated; foundation phases 01/02/03/06 are clear to start.
- 2026-06-08 - B4 resolved via spike: ML Kit `.so` not cleanly self-deliverable. Owner chose self-load for translation; ML Kit Text-Recognition dropped (Tesseract/Paddle cover OCR). New gate B4-PoC added before Phase 05 strips ML Kit Translate. B1/B2/B3 remain open.
- 2026-06-09 - B1 + B2 closed (author-resolved, no external dependency): source mapping per set + SHA-256 app-pinned auth & `versionCode`-keyed manifest versioning. See strategic §6.1 B1/B2 and Phase 04. Remaining gates: B3 (external) + B4-PoC (device).
- 2026-06-09 - B3 narrowed: all deliverable `.so` already ≥16 KB-aligned (no rebuild); B3 = extract + host. SHA-256/size staging kit in `temp/S0386_B3_so_staging.md`. Phase 01 revised to instance/factory split (see PHASE_01).
- 2026-06-09 - B3 OSS hosting done: GitHub release `delivery-so-v1` (Tesseract/Paddle/FFmpeg, arm64-v8a), public download verified. After the owner decision on 2026-06-09, Set A is no longer in the mirror-hosting scope; B3 now tracks OSS/non-arm64 coverage only.
- 2026-06-09 - Implementation paused by owner pending blockers; ticket → `BlockExternal`.
- 2026-06-09 - Step 05.2 completed on `DEBUG-v013`: the store translation runtime moved into shared source set `src/translationDynamicFeature/java`, `legacy` now compiles, and `standard`/`legacy` base APKs no longer package `libtranslate_jni.so` or `liblanguage_id_l2c_jni.so`. Remaining pause scope is 05.3-05.7/07 only.
- 2026-06-09 - Set A decision finalized: do not re-host Google `.so`; store = Play dynamic-feature for ML Kit Translate, sideload/VR = bundled. B4-PoC obsolete. Ticket remains `BlockExternal` because Set B/C/D still need hosting/release/device completion.
- 2026-06-09 - Step 05.3 re-scoped after local analysis: ML Kit Text-Recognition removal, `RecognitionBackend` cleanup, and store translation move are already done. The remaining 05.3 delta is the Tesseract Set B move on store flavors.
- 2026-06-09 - Next implementation anchor for 05.3: the shared default OCR path still binds the Tesseract-backed engine from common code. Removing `cz.adaptech:tesseract4android` outright from store-flavor base will break compile until that boundary is changed, or until the Java wrapper remains compile-time-only while native `.so` move to Set B.
- 2026-06-09 - Tree was found broken: the `:translate_feature` dynamic-feature module failed to compile the shared `src/translationMlKit/java` source set (`Unresolved reference 'tasks'/'await'` - missing `kotlinx-coroutines-play-services`). Added the `1.7.3` dep (matching `app_v2`) to `translate_feature/build.gradle.kts`; `standardDebug` BUILD SUCCESSFUL again. The earlier 05.2 "green" claim did not cover the feature module's compile.
- 2026-06-09 - Debundle core finished (05.3-05.6 + Phase 07). All-ABI Set B/D `.so` were already hosted (`temp/s0386_so_table.txt`), so the multi-ABI gate is cleared. Set B (Tesseract, all flavors; +PaddleOCR on noLegal) and Set D (FFmpeg DTS) native `.so` are stripped from every base via packaging excludes; `DeliveredNativeLibraryLoader` now splices the delivered dir into the classloader native search path (SoLoader/ReLinker reflection) so the engines' own `System.loadLibrary` resolves from filesDir; OCR attach was already wired in `RecognitionBackend`, FFmpeg attach added in `createPlaybackRenderersFactory` via a Hilt EntryPoint. standardDebug/noLegalDebug/legacyDebug/vrDebug all BUILD SUCCESSFUL; APK unpack confirms the stripped `.so` are gone and translation stays bundled on noLegal/vr. Ticket → `BlockNeedUserTest` (on-device enable→download→use + release unpack-verify is the only remaining gate). NOTE: the hidden-API reflection in the loader must be validated on the target API levels (23/26+) on a real device.
- 2026-06-09 - Phase 08 (extensions-manager screen) completed ahead of Phase 07 (safe/additive): finished + cleaned the partially-started screen - added `layout-land` counterparts for both the screen and the settings entry (Rule 11), moved status colors out of inline `Color.parseColor("#..")` into `@color/` resources + `ColorUtils`, fixed the row status flow to react to both repository state and live download via `combine`, sourced module sizes from the contributed descriptor (pinned estimate fallback), and added a `MaterialAlertDialogBuilder` delete confirmation with EN/RU/UK strings. Debundle core 05.3-07 stays `BlockExternal`.

---

## Change Log

- 2026-06-08 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-10 - Emulator device-test (API 33 x86_64) confirmed de-bundle + Set B download/verify/install + Extensions screen + manifest fallback (evidence `temp/spec-sweep_S0386/report.md`). Owner added three follow-on phases restructuring the Extensions entry/screen (see Phases 10-12); strategic §5.1 Pillar G and §3.3 updated. Minor UX finding logged: the optional-manifest 404 is surfaced as a red ERROR banner though it is a handled fallback - fold the fix into Phase 11.
- 2026-06-10 - Phase 12.3 done + device-verified: removed the now-redundant inline "High-Quality Offline Models" (rus/ukr Tesseract download UI) from the Translation/OCR settings group (`OtherMediaSettingsFragment` + `fragment_settings_other.xml` portrait/land + 4 methods/fields/handlers/unused imports + 7 orphaned `ocr_best_*` strings). The OCR language models are now managed solely in the Extensions screen OCR section; the settings group keeps the runtime OCR/translation toggles + selectors + the contextual download shortcut. All 12 phases now done (12.2 N/A, 11.3 cosmetic deferred). standard+noLegal green; neuroslop PASS. Commit forthcoming.
- 2026-06-10 - Phases 10-11 implemented + device-verified on emulator API 33: the primary "Downloadable Extensions" entry now sits on the General tab (non-full-width, portrait+land) after all groups before the permission/about buttons; the Translation/OCR group keeps a contextual "OCR & translation downloads" shortcut; the screen renders OCR / Translation / Media-Playback section headers; OCR language models (rus/ukr) appear under the OCR section. Phase 12.1 done (OCR langs sectioned); 12.2 N/A (translation langs are ML-Kit-managed runtime selectors); 12.3 (remove the inline OCR-best-models from the settings group) deferred as a separate safe change. 11.3 (manifest-404 banner) deferred - shared OkHttp infra. Implemented on the working tree alongside a concurrent (now stopped) `ocrEnabled`/`ocrDisabled` refactor; only S0386 files committed.
