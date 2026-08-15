# Tactical Plan: S0174 — nolegal-ytdlp-universal-extractor

**Strategic spec:** [`../S0174_nolegal-ytdlp-universal-extractor.md`](../S0174_nolegal-ytdlp-universal-extractor.md)
**Feature:** noLegal: universal media extractor via yt-dlp + Chaquopy
**Tier:** 3 — Moderate
**Priority:** 70
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-05-12

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | gradle-chaquopy-setup | — | ✅ Done | 5/5 | [PHASE_01__gradle-chaquopy-setup.md](PHASE_01__gradle-chaquopy-setup.md) |
| 02 | priority-order-fix | 01 | ✅ Done | 2/2 | [PHASE_02__priority-order-fix.md](PHASE_02__priority-order-fix.md) |
| 03 | cookie-bridge | 02 | ✅ Done | 4/4 | [PHASE_03__cookie-bridge.md](PHASE_03__cookie-bridge.md) |
| 04 | ytdlp-strategy | 03 | ✅ Done | 5/5 | [PHASE_04__ytdlp-strategy.md](PHASE_04__ytdlp-strategy.md) |
| 05 | auth-resources | 04 | ✅ Done | 3/3 | [PHASE_05__auth-resources.md](PHASE_05__auth-resources.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All §6 Open items resolved inline:

- [x] **§6.1 CANONICAL_ORDER architecture decision** — Resolved: variant A (add `"ytdlp"` id to `CANONICAL_ORDER` in `LinkExtractionRegistry.kt` in main sourceSet). The id is inert unless a strategy with that id is bound — when noLegal DI is absent the list simply contains an id that matches no registered strategy, harmless to other flavors. Phase 02 implements this.
- [x] **§6.2 facebook.com in KnownAuthResources** — Resolved: add `facebook.com` with `previewOnlyMeansLogin = true`. Phase 05 implements this.

No unchecked blockers — Phase 01 may start immediately.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [x] `dev/CHANGELOG.md` has an entry for every modified file (37 S0174 entries).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated after new `.kt` files (1018 records).
- [x] `/spec-check S0174` returns `Verified` — 2026-05-12.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check` — 2026-05-12.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0174`.

---

## Blockers Log

*(empty)*

---

## Change Log

- 2026-05-12 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-12 — Phase 01 completed. Key learnings: Chaquopy 17.x has no Kotlin-DSL variantFilter; `apply(plugin)` conditional + `beforeVariants { enable = false }` for non-noLegal flavors is the only zero-compromise approach. Python 3.12 selected (3.11 not on host machine); noLegal ABI set to arm64-v8a + x86_64 (Python 3.12 doesn't ship armeabi-v7a/x86 wheels). Build flag: `-Pchaquopy.enabled=true`.
