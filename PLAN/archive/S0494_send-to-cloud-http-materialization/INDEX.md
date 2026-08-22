# Tactical Plan: S0494 - send-to-cloud-http-materialization

**Strategic spec:** [`../S0494_send-to-cloud-http-materialization.md`](../S0494_send-to-cloud-http-materialization.md)
**Research inputs:** none (research captured inline in strategic §0.1)
**Feature:** «Отправить в..» for cloud:// and http(s):// sources
**Tier:** 3 - Moderate
**Priority:** 55
**Status:** Done - awaiting device test
**Phases:** 4 / 4 done
**Last updated:** 2026-08-15

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | cloud-download-extraction | - | ✅ Done | 3/3 | [PHASE_01__cloud-download-extraction.md](PHASE_01__cloud-download-extraction.md) |
| 02 | cloud-progress-passthrough | 01 | ✅ Done | 2/2 | [PHASE_02__cloud-progress-passthrough.md](PHASE_02__cloud-progress-passthrough.md) |
| 03 | http-materialization | 02 | ✅ Done | 4/4 | [PHASE_03__http-materialization.md](PHASE_03__http-materialization.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- none - every strategic §3 research item is Resolved, and the cloud account required by the former `BlockExternal` state is verified live (Google Drive `serzhyale@gmail.com`, resource «H», 100 files, 2026-08-15).

---

## Architecture decisions taken at planning time

- **ADR-1 - http(s) reuses the link-download OkHttp client, not its extraction strategy.** `DirectFileExtractionStrategy` gates content through `MediaMimeWhitelist` and `BlockedReason`, which exist to protect the link-*ingest* feature from arbitrary web content. Share materialization starts from a file the user already has open, so a whitelist there would refuse legitimate shares. The new downloader therefore issues its own GET against the shared `@Named("linkDownload")` `OkHttpClient` and streams the body to the target file.
- **ADR-2 - only direct http(s) responses are materialized.** A manifest or live stream (HLS/DASH) has no finite file to hand a receiver, so it stays on the current failure path and the user sees the existing "could not prepare" message. No new user-visible surface, no new strings.
- **ADR-3 - the extraction takes the download trio only.** `CloudDownloadUseCase` receives `downloadFromCloudToPublic` / `downloadFromCloudTo` / `downloadFromCloud`; `executeCopy` / `executeMove` / `executeRename` / `executeDelete` stay in `CloudFileOperationHandler` because `FileOperationUseCase` binds them as a `FileOperationHandler` implementation.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip: showcase docs are `/skill-release`-owned.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - public API changed.
- [ ] Device test on the §3.3 account clears `BlockNeedUserTest`.
- [ ] `/spec-check S0494` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

The last three stay open by design. Cloud and web download cannot be proven by a compile or a JVM test, and strategic §3.3 supplies a live Google Drive account for exactly that reason, so the ticket closes into `BlockNeedUserTest` rather than `Implemented`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0494`.

---

## Blockers Log

- 2026-08-15 - RESOLVED, both without accepting debt. See Phase 04 "Deviations recorded during implementation" for what each fix was. `post-change: PASS` (Mixed, 10 files, `-ScopeToFile`).
- 2026-08-15 - Phase 04 blocked at `post-change.ps1`, two detekt gates, neither of them behavioural:
  - `detekt-baseline-absorption` fails on `ReturnCount in CastMediaManagerImpl.kt`, a finding this ticket never touched. It arrived in the working tree from another session (`CODE.LOCK` reason `S1696 type-gate empty state`). The gate is repo-wide, so S0494 cannot close while it sits there, and running `-Update` to clear it would silently accept that other ticket's debt - exactly the event the gate exists to make loud. Next: the other ticket lands or reverts it, or the owner accepts it as its own dev-log row.
  - `detekt-preflight` reports one finding: `LongParameterList` on `CloudFileOperationHandler`'s constructor, which was already over threshold and baselined at 16 parameters and now has 17 because the handler injects `CloudDownloadUseCase`. Every remaining parameter is still used by the handler, so there is no free removal. Re-keying the accepted entry needs the same `-Update` that the first blocker forbids.

---

## Change Log

- 2026-08-15 - Initial tactical plan authored by `/spec-tech`.
