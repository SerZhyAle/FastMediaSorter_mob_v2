# Tactical Plan: S0118 - friendly-ui-copy-revision

**Strategic spec:** [`../S0118_friendly-ui-copy-revision.md`](../S0118_friendly-ui-copy-revision.md)
**Feature:** Friendly UI copy revision
**Tier:** 3 - Moderate
**Priority:** 60
**Status:** Done
**Phases:** 7 / 7 done
**Last updated:** 2026-05-10

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

> Note: The 7-phase implementation record and the `.github/prompts/*` communication-policy sync are now closed. A fresh full audit on 2026-05-10 returned `Verified`; the remaining real-device follow-ups stay tracked in the strategic `## Last Audit` block and no longer reopen the tactical record.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | copy-foundations | - | ✅ Done | 4/4 | [PHASE_01__copy-foundations.md](PHASE_01__copy-foundations.md) |
| 02 | error-projection | 01 | ✅ Done | 4/4 | [PHASE_02__error-projection.md](PHASE_02__error-projection.md) |
| 03 | help-routing | 01 | ✅ Done | 4/4 | [PHASE_03__help-routing.md](PHASE_03__help-routing.md) |
| 04 | settings-surface-sweep | 02, 03 | ✅ Done | 4/4 | [PHASE_04__settings-surface-sweep.md](PHASE_04__settings-surface-sweep.md) |
| 05 | feature-surface-sweep | 02, 03, 04 | ✅ Done | 4/4 | [PHASE_05__feature-surface-sweep.md](PHASE_05__feature-surface-sweep.md) |
| 06 | locale-tests | 04, 05 | ✅ Done | 4/4 | [PHASE_06__locale-tests.md](PHASE_06__locale-tests.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 5/5 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Strategic §6 has no open research items as of 2026-05-08.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (user-facing feature, see strategic §8).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` + `dev/CATALOG/app_v2.md` regenerated after every touched `.kt` file batch.
- [x] Residual hardcoded user-facing strings in target directories reduced to zero or explicitly waived in the strategic spec.
- [x] Copilot prompt layer mirrors the `docs/COMMUNICATION_POLICY.md` requirements called out in strategic §13.3.
- [x] Latest `/spec-check S0118` rerun after the prompt-layer sync returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.
- [x] Remaining on-device follow-ups are tracked in the strategic `## Last Audit` block and no longer block static closure.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0118`.

---

## Blockers Log

- 2026-05-08 - Initial tactical plan authored by `/spec-tech` flow.
- 2026-05-09 - Later Broken audit in the strategic spec superseded the earlier completion claim and reopened residual copy and error-projection work before S0118 can return to `/spec-check`.
- 2026-05-09 - Follow-up fix-up removed the last temporary S0118 verification Timber tags, but a later review found the Copilot prompt layer still missing the communication-policy gate required by strategic §13.3.
- 2026-05-09 - Copilot prompt-layer sync is now complete. Remaining blocker: real-device verification plus a fresh `/spec-check` before S0118 can return to `Verified`.
- 2026-05-10 - Fresh full audit returned `Verified`; remaining device-only follow-ups stay documented in the strategic `## Last Audit` block without reopening the tactical record.

---

## Change Log

- 2026-05-08 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-09 - Completion Gate reopened after the premature `Verified` transition was traced to a missing Copilot prompt-layer policy sync.
- 2026-05-09 - Completion Gate updated again after the missing `.github/prompts/*` communication-policy sync landed.
- 2026-05-10 - Completion Gate re-synced with the restored `Verified` audit after the final S0118 copy sweep.

---

## Revision History

- **2026-05-09** - by `/spec-update` (GPT-5.4, focus: consistency, completeness, style, `--force-locked`)
	- Applied: 1 (added a Broken-audit note near the top, reopened the three invalidated Completion Gate checks, and logged the superseding blocker without changing historical phase rows).
	- Proposed: 0.
	- Override reason: This tactical index remains locked as part of the historical Broken spec package, but the user explicitly requested a force-locked refinement to align stale completion guidance with the later audit without changing status fields.
- **2026-05-09** - by `/spec-update` (GPT-5.4, focus: consistency, completeness, `--force-locked`)
	- Applied: 2 (advanced `Last updated` plus Completion Gate back to a fully satisfied state and logged the restoring verified pass in the blockers/change history).
	- Proposed: 0.
	- Override reason: The index still reflected the transient Broken state after the last static fix landed, so it needed one final force-locked alignment to match the restored `Verified` outcome.
- **2026-05-09** - by `/spec-update` (GPT-5.4, focus: completeness, consistency, `--force-locked`)
	- Applied: 4 (reopened the Completion Gate after the missing Copilot prompt-layer sync was identified, added a dedicated prompt-sync gate item, and aligned the current truth with strategic `BlockNeedUserTest` instead of `Verified`).
	- Proposed: 0.
	- Override reason: Пользователь явно указал, что `Verified` было преждевременным, а prompt-layer sync из §13.3 ещё не был доведён до конца.
- **2026-05-10** - by `/spec-update` (GPT-5.4, focus: consistency, completeness, `--force-locked`)
	- Applied: 3 (обновлены `Last updated`, top note и Completion Gate под новый full-audit verdict `Verified`, а также зафиксировано закрытие reopening-хвоста в blockers/change history).
	- Proposed: 0.
	- Override reason: Пользователь явно запросил завершить S0118 как следует после финального полного аудита.