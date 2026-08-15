# Tactical Plan: S1025 - transfer-fail-fast-unreachable-destination

**Strategic spec:** [`../S1025_transfer-fail-fast-unreachable-destination.md`](../S1025_transfer-fail-fast-unreachable-destination.md)
**Research inputs:** [`research/01__preflight-probe-map.md`](research/01__preflight-probe-map.md)
**Feature:** One destination-reachability probe before the batch loop; abort the whole batch with a clear "destination unreachable" result on failure.
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done (awaiting device verification - BlockNeedUserTest)
**Phases:** 2 / 2 done
**Last updated:** 2026-07-14

> Owner decision: SINGLE pre-flight probe before the per-file loop; abort on one failure; keep in-loop retry. Insert at the ONE dispatch site (`FileOperationUseCase.executeInternal`) that covers all 6 downstream loops + both foreground and worker paths.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | preflight-probe-and-test | - | ⬜ Not started | 4 | [PHASE_01__preflight-probe-and-test.md](PHASE_01__preflight-probe-and-test.md) |
| 02 | build-and-device-probe | 01 | ⬜ Not started | 2 | [PHASE_02__build-and-device-probe.md](PHASE_02__build-and-device-probe.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Completion Gate

- [ ] Batch Copy/Move to an unreachable SMB/SFTP/FTP destination aborts before the per-file loop with `Failure(destination unreachable)`.
- [ ] Cloud destination + Delete/Rename unaffected; local unaffected; in-loop retry preserved.
- [ ] New string EN/RU/UK; unit test in `FileOperationUseCaseTest` green.
- [ ] standard debug build PASS.
- [ ] Device verification (Copy/Move to a down SMB host aborts fast with a clear message; reachable host still works) - deferred to `BlockNeedUserTest`.

---

## Change Log

- 2026-07-14 - Tactical plan authored by `/spec-tech` (F2). Forks resolved from codebase (research/01).
