# Tactical Plan: S0035 — android17-local-network-permission

**Strategic spec:** [`../S0035_android17-local-network-permission.md`](../S0035_android17-local-network-permission.md)
**Feature:** Android 17 local-network permission gate for SMB / SFTP / FTP / Cast
**Tier:** 0 — Security / Compliance (urgent)
**Priority:** 70
**Status:** Done
**Phases:** 7 / 7 done
**Last updated:** 2026-05-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | manifest-sdk-guard | — | ✅ Done | 4/4 | [PHASE_01__manifest-sdk-guard.md](PHASE_01__manifest-sdk-guard.md) |
| 02 | permission-contract | 01 | ✅ Done | 4/4 | [PHASE_02__permission-contract.md](PHASE_02__permission-contract.md) |
| 03 | ui-rationale-settings | 01, 02 | ✅ Done | 4/4 | [PHASE_03__ui-rationale-settings.md](PHASE_03__ui-rationale-settings.md) |
| 04 | addresource-entrypoints | 01, 02, 03 | ✅ Done | 4/4 | [PHASE_04__addresource-entrypoints.md](PHASE_04__addresource-entrypoints.md) |
| 05 | protocol-readers | 01, 02, 03 | ✅ Done | 4/4 | [PHASE_05__protocol-readers.md](PHASE_05__protocol-readers.md) |
| 06 | cast-guard | 01, 02, 03 | ✅ Done | 4/4 | [PHASE_06__cast-guard.md](PHASE_06__cast-guard.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 5/5 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] Approval gate resolved by explicit user confirmation on 2026-05-03. This tactical bundle advances the strategic spec from `Approved` to `Tactical`.
- [x] **Research §6.1 resolved.** Cast requires the permission; will be explicitly checked before initialization.
- [x] **Research §6.2 resolved.** Network operations return socket-level errors (`EPERM`), so explicit checks are needed before triggering network layers.
- [x] **Research §6.3 resolved.** `ACCESS_LOCAL_NETWORK` is confirmed as a `runtime dangerous` permission in the `NEARBY_DEVICES` group.

**Stop condition (LIFTED):** The initial blocker concerning whether `ACCESS_LOCAL_NETWORK` is a runtime permission has been resolved (it is runtime dangerous). Implementation phases 02-06 can proceed using standard runtime permission UX logic.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `dev/CATALOG/app_v2.md` regenerated.
- [ ] `./gradlew.bat :app_v2:compileStandardDebugKotlin` passes.
- [ ] `./gradlew.bat :app_v2:assembleStandardDebug` passes.
- [ ] `/spec-check S0035` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.
- [ ] API 37 manual smoke proves explicit missing-permission handling for Add Resource discovery, SMB / SFTP / FTP open paths, and Cast init with no crash loops.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0035`.

---

## Blockers Log

- 2026-05-03 — Initial tactical plan authored by `/spec-tech` after explicit user approval.

---

## Change Log

- 2026-05-03 — Initial tactical plan authored by `/spec-tech` from explicit user invocation.