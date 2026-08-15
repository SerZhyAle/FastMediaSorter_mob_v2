# Tactical Plan: S0336 - nolegal-extended-system-info

**Strategic spec:** [`../S0336_nolegal-extended-system-info.md`](../S0336_nolegal-extended-system-info.md)
**Feature:** Extended System info diagnostics for the noLegal flavor
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done - awaiting on-device verification (spec status `BlockNeedUserTest`)
**Phases:** 4 / 4 done
**Last updated:** 2026-06-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | diagnostics-contract | - | ✅ Done | 5/5 | [PHASE_01__diagnostics-contract.md](PHASE_01__diagnostics-contract.md) |
| 02 | nolegal-contributor | 01 | ✅ Done | 4/4 | [PHASE_02__nolegal-contributor.md](PHASE_02__nolegal-contributor.md) |
| 03 | redaction-reveal-ui | 01, 02 | ✅ Done | 3/3 | [PHASE_03__redaction-reveal-ui.md](PHASE_03__redaction-reveal-ui.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Architecture Decisions (carried from research)

- **Isolation mechanism: Hilt multibinding, not a symmetric No-Op split.** The contract is a `@Multibinds Set<ExtendedDiagnosticsContributor>` declared in `src/main`. Non-noLegal flavors contribute nothing → empty set → no extra sections, no No-Op class, no `build.gradle.kts` source-set change. This mirrors the established `OcrContributorModule` / `NoLegalLinkDownloadModule` pattern.
- **noLegal code placement: `src/noLegal/java` only.** Not `src/vr/java` - that source set is also mounted into the Store-published `vr` flavor and would leak the diagnostics there. The noLegal contributor MAY depend on XR types (`src/vr/java` is mounted into noLegal) for VR-runtime fields.
- **Reveal mechanism (resolves strategic §6.3 open choice): data-driven "Copy full report" confirmed action.** Strategic §6.3 left "toggle vs. separate action" open; strategic §11.3's example ("confirming full-report export") and the §3.3 autonomy rule select the confirmed full-report copy. The opener shows the action only when the report carries sensitive content (`hasSensitive == true`), which is false on every non-noLegal flavor (empty contributor set). No `BuildConfig` gate is introduced - visibility is purely data-driven.
- **Sensitive-value masking lives in the contract mapper (`src/main`), not in the contributor.** The contributor tags fields `sensitive = true`; the main-side mapper renders `[REDACTED]` unless reveal is requested. Default-masked values appear in the on-screen text, normal copy and share (strategic §6.3).

---

## Pre-Implementation Blockers

All strategic §6 research items are `Resolved` (including §6.1 hard-exclusions table). No open research blocks Phase 01.

- (none)

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES_noLegal.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates noLegal-only docs; public `docs/FEATURES*.md` MUST stay unchanged).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated; noLegal-only classes carry `-NoFlavors "standard,lite,photos,legacy,vr"`.
- [ ] `standardDebug` compiles with zero noLegal diagnostics code in the APK (isolation invariant).
- [ ] `noLegalDebug` compiles and shows the extended section.
- [ ] `/spec-check S0336` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0336`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-03 - Initial tactical plan authored by `/spec-tech`. Reveal mechanism resolved to data-driven confirmed "Copy full report" action (strategic §6.3) under the §3.3 autonomy rule.
