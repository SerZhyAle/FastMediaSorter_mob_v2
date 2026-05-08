# Tactical Plan: S0046 — sftp-key-auth-hardening

**Strategic spec:** [`../S0046_sftp-key-auth-hardening.md`](../S0046_sftp-key-auth-hardening.md)
**Feature:** SFTP key-auth hardening (predefined-XML key support + host-key fingerprint pinning)
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 50
**Status:** 🚧 In Progress
**Phases:** 0 / 6 done
**Last updated:** 2026-05-02

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | data-model-and-db | — | ⬜ Not started | 0/4 | [PHASE_01__data-model-and-db.md](PHASE_01__data-model-and-db.md) |
| 02 | fingerprint-verifier-core | 01 | ⬜ Not started | 0/3 | [PHASE_02__fingerprint-verifier-core.md](PHASE_02__fingerprint-verifier-core.md) |
| 03 | sftp-pin-wiring | 02 | ⬜ Not started | 0/4 | [PHASE_03__sftp-pin-wiring.md](PHASE_03__sftp-pin-wiring.md) |
| 04 | xml-schema-and-bundle | 01 | ⬜ Not started | 0/4 | [PHASE_04__xml-schema-and-bundle.md](PHASE_04__xml-schema-and-bundle.md) |
| 05 | resource-form-fingerprint-ui | 03 | ⬜ Not started | 0/4 | [PHASE_05__resource-form-fingerprint-ui.md](PHASE_05__resource-form-fingerprint-ui.md) |
| 06 | docs-catalog-cleanup | all | ⬜ Not started | 0/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are `Resolved` (see strategic spec §6.1–6.4). No blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0046` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0046`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-05-02 — Initial tactical plan authored by `/spec-tech`.
