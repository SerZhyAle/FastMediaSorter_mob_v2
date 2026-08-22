# Tactical Plan: S1801 - wear-documentation-site-pages

**Strategic spec:** [`../S1801_wear-documentation-site-pages.md`](../S1801_wear-documentation-site-pages.md)
**Research inputs:** [`research/01__site-genre-and-wear-gap.md`](research/01__site-genre-and-wear-gap.md) · [`research/02__locales-and-s1211.md`](research/02__locales-and-s1211.md) · [`research/03__wear-screenshots.md`](research/03__wear-screenshots.md) · [`research/04__existing-wear-docs-classification.md`](research/04__existing-wear-docs-classification.md)
**Feature:** Wear OS user documentation as site pages
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** ✅ Complete
**Phases:** 6 / 6 done
**Last updated:** 2026-08-19

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | wear-docs-registry-split | - | ✅ Done | 4/4 | [PHASE_01__wear-docs-registry-split.md](PHASE_01__wear-docs-registry-split.md) |
| 02 | scenario-watch-music | - | ✅ Done | 5/5 | [PHASE_02__scenario-watch-music.md](PHASE_02__scenario-watch-music.md) |
| 03 | scenario-watch-network | 02 | ✅ Done | 4/4 | [PHASE_03__scenario-watch-network.md](PHASE_03__scenario-watch-network.md) |
| 04 | wear-screenshots | 02, 03 | ✅ Done | 4/4 | [PHASE_04__wear-screenshots.md](PHASE_04__wear-screenshots.md) |
| 05 | site-entrances-and-showcase | 02, 03 | ✅ Done | 4/4 | [PHASE_05__site-entrances-and-showcase.md](PHASE_05__site-entrances-and-showcase.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **S1781 must reach `Verified` before Phase 02 starts.** `Blocker: S1781`. S1781 rebuilds the Wear main screen - its sections (Last used, Favourites, Resources, Phone, Local, Streams, Apps) and its view modes. Every scenario in Phases 02-03 opens on that screen, so both the step text and every screenshot taken before S1781 lands describe a screen that will not exist. Phase 01 is deliberately independent of it and may run first.

Strategic §6 items that are `Open` and do **not** block: §6.5 (per-page sitemap indexing) is carried by S1803 and explicitly out of scope; §6.6 (scenario composition) is resolved by this plan - two scenarios, named in Phases 02 and 03.

---

## Scope decision carried into this plan

The strategic spec left the scenario list to the tactical level (§6.6). Two scenarios ship in this ticket, both chosen because every capability they describe already carries an `ALL_FEATURES` record, so the guide describes shipped behaviour rather than a plan:

- **Watch music** - paired-phone resource (S1697) plus the watch audio player: shuffle, bezel volume, draggable position bar (S1701), album art (S1689), controls, paging and screen-off (S1683).
- **Watch network** - network sources on the watch: SFTP connection test (S1497), FTP test (S1554), pinned SFTP host key (S1555), source base path (S1556), media-type filtering while browsing (S1690).

Further scenarios extend the same pattern and need no new machinery - one page per locale plus two index rows, per strategic §5.3. They are not in this ticket.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - strategic §8 carries a FEATURES sentence, so `/skill-release` picks it up from the `ALL_FEATURES` diff; no per-spec edit here.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/*.jsonl` - not regenerated: no Kotlin source changes in this ticket.
- [x] `scripts/document_registry/validate.ps1` exits 0 and `generate.ps1 -Check` reports no drift.
- [x] `scripts/quality/assert-howto-settings-paths.ps1` exits 0.
- [ ] `/spec-check S1801` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1801`.

---

## Blockers Log

- 2026-08-18 - Phases 02-05 were blocked on S1781 rebuilding the Wear main screen. **Resolved:** S1781 reached `Verified`, which is the condition this entry named, and the ticket left `BlockByOtherTask`.
- 2026-08-18 - Phase 02 now waits on a device precondition rather than on a ticket. Step 02.1 walks the flow from the paired-phone resource, and the attached Wear emulator reports `0 connected out of 1` - it is not paired with the phone. Measured, not assumed: `dumpsys activity service com.google.android.gms.wearable` on emulator-5554. Nothing in the plan or the tree blocks the phase; it needs a watch actually paired to a phone carrying a flavor that mounts the GMS bridge.
- 2026-08-18 - Phase 01 done and closed through `post-change.ps1` (PASS). Registry now separates `wear-docs` (user, published, indexable) from `wear-dev-docs` (developer, unpublished, unindexed); sitemap URL count unchanged at 19 and the single Wear address moved from the Android Studio quick start to the watch-owner SMB guide.
- 2026-08-18 - Residual gap on strategic §2.4 left for the owner: the six developer documents still carry Jekyll front matter, so the site still builds them and they answer on a direct address. Removing them from the build needs the owner sign-off strategic §3.3 names. §11.6 (indexable surface) is already satisfied.

---

## Change Log

- 2026-08-18 - Initial tactical plan authored by `/spec-tech`.
