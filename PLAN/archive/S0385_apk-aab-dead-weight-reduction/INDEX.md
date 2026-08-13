# Tactical Plan: S0385 - apk-aab-dead-weight-reduction

**Strategic spec:** [`../S0385_apk-aab-dead-weight-reduction.md`](../S0385_apk-aab-dead-weight-reduction.md)
**Feature:** APK/AAB dead-weight reduction
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 70
**Status:** Implemented (closed 2026-06-08)
**Phases:** 6 / 7 done (05 superseded by S0386)
**Last updated:** 2026-06-08

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | dead-dependency-removal | - | ✅ Done | 3/4 | [PHASE_01__dead-dependency-removal.md](PHASE_01__dead-dependency-removal.md) |
| 02 | dead-class-removal | - | ✅ Done | 3/4 | [PHASE_02__dead-class-removal.md](PHASE_02__dead-class-removal.md) |
| 03 | test-fixture-debug-isolation | - | ✅ Done | 3/3 | [PHASE_03__test-fixture-debug-isolation.md](PHASE_03__test-fixture-debug-isolation.md) |
| 04 | keep-narrow-resource-shrink | - | ✅ Done (fast-wins; rest dropped) | 2/5 | [PHASE_04__keep-narrow-resource-shrink.md](PHASE_04__keep-narrow-resource-shrink.md) |
| 05 | ml-ocr-flavor-confinement | 01, blockers | ⏭️ Superseded by S0386 | 0/6 | [PHASE_05__ml-ocr-flavor-confinement.md](PHASE_05__ml-ocr-flavor-confinement.md) |
| 06 | flavor-dead-media-placement | - | ✅ Done (no-op: release shrinks) | 1/2 | [PHASE_06__flavor-dead-media-placement.md](PHASE_06__flavor-dead-media-placement.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phases 01-04 and 06 are independent and may land in any order. Phase 05 is the headline weight reduction and is gated by the blockers below. Phase 07 closes after all others.

---

## Pre-Implementation Blockers

These gate **Phase 05 only**. Phases 01-04 and 06 may proceed without them.

- [x] **Research (RESOLVED 2026-06-08):** ML/OCR surface enumerated - see strategic §6.1. ~37 `src/main` files: Tier A (5, direct dep import), Tier B (capability internals), Tier C (~20 call sites). Linchpin = monolithic `TranslationManager` injected across the player/viewer/camera/settings stack. Conclusion: large, high-risk refactor, not a quick confinement.
- [x] **Owner decision (2026-06-08): Phase 05 SUPERSEDED by S0386.** Instead of flavor-confining ML to lite/photos, the owner chose on-demand delivery (download-on-enable, default-off, self-hosted on our GitHub) which removes the weight from the base of ALL flavors. Phase 05 is not implemented under S0385; the §6.1 enumeration feeds S0386's facade/backend extraction. See S0386 (`BlockQuestions`, awaiting delivery-mechanism decision §6.1).
- [ ] **Research (non-blocking, resolve in-phase):** Camera/CameraX reachability in `lite`/`photos` (strategic §6.2) - determines whether camera native libs are also confined in Phase 05; if unresolved, Phase 05 confines ML/OCR only.

Non-blocking research items resolved within their own phase by build inspection: anim shrink behaviour (§6.3, Phase 06), dead string/drawable weight (§6.4, Phase 04), dependency-removal safety (§6.5, Phases 01/04).

### Cross-ticket guards (verified 2026-06-08)

- **S0046 `sftp-key-auth-hardening` (Partial):** owns `HostKeyMismatchException` as scaffolding for its unfinished Phase 05. **Excluded** from Phase 02 deletion - do not remove it, do not narrow `data.remote.**` keep around it.
- **S0381 `neuroslop-hygiene-hardening` (In Progress):** goal 3 is to outline the incremental move of flavor-flag usages out of `src/main` into flavor source sets. This spec's Phase 05 (ML/OCR confinement) is a concrete instance of that direction - coordinate sequencing so the two do not edit the same `build.gradle.kts`/source-set wiring simultaneously.
- **S0383 `neuroslop-code-and-resource-hygiene` (In Progress):** targets duplicate strings + hardcoded colors (different axis from dead-but-packaged weight). No shared artifact with this spec, but both touch resource hygiene - avoid concurrent `strings*.xml`/keep-rule edits.
- All other deleted classes (Glide byte-buffer cluster, `EncryptedString`, `BaseFragment`, `UiEvent`, `PdfHelper`, `KpiAlertChecker`, `MetricsExporter`) confirmed referenced by **no** active ticket.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (classes removed/moved).
- [ ] Measured before/after release-AAB size delta recorded per affected flavor.
- [ ] `/spec-check S0385` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log; set journal status to the matching `Block*` state if the whole spec is blocked.
5. All done: flip `Status:` to `Done`, run `/spec-check S0385`.

---

## Blockers Log

- 2026-06-08 - Phase 05 gated: research §6.1 (ML-touching code enumeration) Open + owner sign-off on Pillar A pending. Next: resolve enumeration via catalog/grep sweep, obtain owner scope confirmation.

---

## Change Log

- 2026-06-08 - Initial tactical plan authored by `/spec-tech`.
