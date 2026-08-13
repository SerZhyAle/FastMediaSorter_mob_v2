# Tactical Plan: S1154 - channel-preview-atlas

**Strategic spec:** [`../S1154_channel-preview-atlas.md`](../S1154_channel-preview-atlas.md)
**Research inputs:** [`research/01__as-is-atlas-subsystems.md`](research/01__as-is-atlas-subsystems.md)
**Feature:** Channel preview atlas
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 7 / 7 done (device verification of Phase 03/04/06 pending)
**Last updated:** 2026-07-26

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File | F3 driveability |
|---|-------|-----------|--------|------:|------|-----------------|
| 01 | atlas-deliverable-plumbing | - | ✅ Done | 7/7 | [PHASE_01__atlas-deliverable-plumbing.md](PHASE_01__atlas-deliverable-plumbing.md) | Autonomous |
| 02 | atlas-store-and-slicer | 01 | ✅ Done | 4/4 | [PHASE_02__atlas-store-and-slicer.md](PHASE_02__atlas-store-and-slicer.md) | Autonomous |
| 03 | grid-preview-tier | 02 | ✅ Done | 3/3 | [PHASE_03__grid-preview-tier.md](PHASE_03__grid-preview-tier.md) | Code done; visual render device-gated (BlockNeedUserTest) |
| 04 | post-import-atlas-prompt | 01 | ✅ Done | 3/3 | [PHASE_04__post-import-atlas-prompt.md](PHASE_04__post-import-atlas-prompt.md) | Code done; end-to-end download device-gated (BlockNeedUserTest) |
| 05 | video-filter-auto-grid | - | ✅ Done | 2/2 | [PHASE_05__video-filter-auto-grid.md](PHASE_05__video-filter-auto-grid.md) | Autonomous |
| 06 | offline-atlas-packer | 01, 02 | ✅ Done | 4/4 | [PHASE_06__offline-atlas-packer.md](PHASE_06__offline-atlas-packer.md) | Built + published 2026-07-26; on-device render still device-gated |
| 07 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) | Autonomous |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Decisions fixed by this plan

- Append `CHANNEL_PREVIEW_ATLAS` at the END of the `DeliverableSet` enum so existing ordinals stay stable (`InstalledSetMarkerStore` markers, `DeliverableDownloadWorker.notificationId = 7300 + ordinal`).
- Atlas is a DATA payload (image sheet + `url->index` JSON sidecar), never native `.so`: `RealDeliverableSetDownloader.isNativeCodeSet()` returns `false` for it, so it downloads on Play installs too (Play `.so` ban does not apply).
- Atlas is on-demand, never bundled: it is contributed only through `DeliverableSetContributor.descriptors()`, never through `BundledDeliverableSetContributor.bundledSets()`.
- Atlas payload (sheet + sidecar) is fetched together into `filesDir/delivery/CHANNEL_PREVIEW_ATLAS/`; the on-device store reads both from there. No separate write path (the downloader promotes the payload).
- New slicer is a distinct class (`ChannelPreviewAtlasSlicer`), NOT a change to `FaviconAtlasSlicer`: 240x135 tiles on an 8192x8192 sheet use per-tile `BitmapRegionDecoder` (ADR-2); the favicon 32px decode-once path is untouched.
- Grid preview tier is added as ONE new adapter lambda (`atlasPreviewLoader: suspend (url) -> Bitmap?`), not multiple, to respect the existing `@Suppress("LongParameterList")` on `StreamGridAdapter` (risk §7).
- Descriptor integrity pins (SHA-256/size/asset name) are declared with documented placeholders in Phase 01 and FINALIZED in Phase 06 from the real generated binary. Runtime download is not exercisable until Phase 06 publishes.
- No Room schema change, no new `res/layout/*.xml`, no new settings.

---

## Pre-Implementation Blockers

Strategic §6 research is complete (artifact present) and both owner decisions (Q-A, Q-B) are resolved (2026-07-23). No open research items.

- [x] **Research:** AS-IS atlas subsystems - resolved. See strategic §6 and [`research/01__as-is-atlas-subsystems.md`](research/01__as-is-atlas-subsystems.md).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 routes public showcase through `/skill-release`; per-spec edit forbidden).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new classes in Phase 02/03/04).
- [ ] `docs/ALL_FEATURES.jsonl` has the Streams atlas record (Phase 07).
- [ ] `/spec-check S1154` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to Blockers Log. If the whole spec is blocked, set the journal status to the matching `Block*` state.
5. All done: flip `Status:` to `Done`, run `/spec-check S1154`.

---

## Blockers Log

- 2026-07-23 - Phase 06 is device/ops-gated: it requires generating the atlas binary against ~2025 live channels and publishing it via `gh`, plus the device-gated visual verification of Phase 03. F3 may drive Phases 01, 02, 03 (code), 04 (code), 05 now; 06 and the visual/end-to-end predicates of 03/04 defer to BlockNeedUserTest.

---

## Change Log

- 2026-07-23 - Initial tactical plan authored by `/spec-tech`.
- 2026-07-26 - Phase 06 executed: packer extended, 1881 channel frames captured, sheet + sidecar published to `delivery-so-v1`, descriptor pins finalized. Payload ships as two plain assets instead of a zip (the shipped descriptor fetches each file by its versioned name).
