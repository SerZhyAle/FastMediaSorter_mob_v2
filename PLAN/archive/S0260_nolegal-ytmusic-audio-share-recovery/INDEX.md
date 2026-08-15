# Tactical Plan: S0260 - nolegal-ytmusic-audio-share-recovery

**Strategic spec:** [`../S0260_nolegal-ytmusic-audio-share-recovery.md`](../S0260_nolegal-ytmusic-audio-share-recovery.md)
**Feature:** noLegal YTMusic audio-share recovery
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 80
**Status:** In Progress
**Phases:** 1 / 5 done
**Last updated:** 2026-05-19

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | diagnostic-instrumentation | - | ✅ Done | 5/5 | [PHASE_01__diagnostic-instrumentation.md](PHASE_01__diagnostic-instrumentation.md) |
| 02 | audio-only-output-contract | 01 + Q3 resolved | ⛔ Blocked | 0/3 | [PHASE_02__audio-only-output-contract.md](PHASE_02__audio-only-output-contract.md) |
| 03 | targeted-extraction-fix | 01 + triage | ⛔ Blocked | 0/4 | [PHASE_03__targeted-extraction-fix.md](PHASE_03__targeted-extraction-fix.md) |
| 04 | regression-coverage | 02, 03 | ⬜ Not started | 0/3 | [PHASE_04__regression-coverage.md](PHASE_04__regression-coverage.md) |
| 05 | docs-catalog-cleanup | all | ⬜ Not started | 0/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 carries three Open research items. Phase 01 does NOT depend on them - it is pure instrumentation and ships first. Phases 02 and 03 cannot start until the items below are resolved by `/spec-update`.

- [ ] **Evidence: Device-log of YTMusic share on current noLegal build.** Required to triage Q1 (where the flow fails: extraction / format-pick / download / write) and Q2 (whether PoTokenProvider is actually required). Phase 01 must ship and the user must run one share of `https://music.youtube.com/watch?v=<known-test-id>` on a fresh noLegal build with logcat captured to `logs/`. The log must contain the `S0260:` traces planted by Phase 01.
- [ ] **Triage: Root-cause classification (H1 / H2 / H3 / PoToken).** Run `/spec-update S0260` after the device test. The triage records which of the four hypotheses fired:
    - **H1** - cookies for `www.youtube.com` absent → `audioOnly` hint lost in `LinkDownloadSessionContext`.
    - **H2** - yt-dlp returns only manifest formats; Python cascade picks the wrong stream.
    - **H3** - yt-dlp fails entirely; registry falls through to `NewPipeSiteExtractionStrategy` which returns a thumbnail / preview URL.
    - **PoToken** - all four yt-dlp `player_client` attempts fail and the only way through is a real `PoTokenProvider`. ADR-2 in strategic spec keeps this out of the first iteration and routes it to a separate spec.
- [ ] **Owner decision: Q3 fallback contract.** `/spec-update S0260` records the chosen contract:
    - `audio-only or fail` - guard rejects any non-`audio/*` MIME and `LinkAutoDownloadCoordinator` returns an explicit failure.
    - `audio-only preferred + explicit fallback` - controlled fallback to a video stream is allowed, but user-visible string must declare the deviation.

Phase 02 reads Q3 to wire its guard variant. Phase 03 reads the triage result to pick which branch (A / B / C / D) ships.

---

## Completion Gate

- [ ] All phases show ✅ Done (or ⏭️ Skipped with explicit rationale).
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - strategic §8 says "Без изменений", so skipped.
- [ ] `docs/FEATURES_noLegal.md` + `_RU.md` + `_UK.md` - skipped (no new noLegal-only capability; this is a bugfix scope).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file via `add_to_dev_log.ps1`.
- [ ] `dev/FUNCTIONALITY.log` has one `FIX` entry tied to `S0260` (added during Phase 05).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1 -Module app_v2`.
- [ ] `/spec-check S0260` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, set journal status to `BlockNeedUserTest` (after Phase 01 ships, awaiting device-log) or `BlockQuestions` (awaiting Q3 owner decision).
5. All done: flip `Status:` to `Done`, run `/spec-check S0260`.

---

## Blockers Log

- 2026-05-19 - Phase 02 + Phase 03 born blocked: strategic §6 carries three Open items. Phase 01 resolves the evidence side; `/spec-update` resolves the triage and Q3 sides. Next: ship Phase 01, run device test, run `/spec-update S0260`.

---

## Change Log

- 2026-05-19 - Initial tactical plan authored by `/spec-tech`. Phase 02 and Phase 03 marked Blocked at creation time per strategic §6.
- 2026-05-19 - Phase 01 ✅ Done by `/spec-dev`. 5 steps shipped (canonical trace, session-context state + skipped, ytdlp route prefixes + new direct-okhttp line, ytdlp python result trace, python format-selector print). `assembleNoLegalDebug` PASS in 1m28s. Spec status flipped to `BlockNeedUserTest`. Next: user device test with `https://music.youtube.com/watch?v=<id>` on noLegal build, capture `logs/current.log`, then `/spec-update S0260` to record triage + Q3.
