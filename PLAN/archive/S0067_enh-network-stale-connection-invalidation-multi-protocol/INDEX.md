# Tactical Plan: S0067 — enh-network-stale-connection-invalidation-multi-protocol

**Strategic spec:** [`../S0067_enh-network-stale-connection-invalidation-multi-protocol.md`](../S0067_enh-network-stale-connection-invalidation-multi-protocol.md)
**Feature:** Unified liveness-gate / single-retry / lifecycle hooks for FTP / SFTP / Cloud
**Tier:** 3 — Moderate
**Priority:** 60
**Status:** Done
**Phases:** 7 / 7 done
**Last updated:** 2026-05-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | gate-foundations | — | ✅ Done | 6/6 | [PHASE_01__gate-foundations.md](PHASE_01__gate-foundations.md) |
| 02 | smb-gate-adapter | 01 | ✅ Done | 4/4 | [PHASE_02__smb-gate-adapter.md](PHASE_02__smb-gate-adapter.md) |
| 03 | sftp-gate | 01 | ✅ Done | 5/5 | [PHASE_03__sftp-gate.md](PHASE_03__sftp-gate.md) |
| 04 | ftp-gate | 01 | ✅ Done | 5/5 | [PHASE_04__ftp-gate.md](PHASE_04__ftp-gate.md) |
| 05 | cloud-gate | 01 | ✅ Done | 5/5 | [PHASE_05__cloud-gate.md](PHASE_05__cloud-gate.md) |
| 06 | lifecycle-diagnostics | 02, 03, 04, 05 | ✅ Done | 5/5 | [PHASE_06__lifecycle-diagnostics.md](PHASE_06__lifecycle-diagnostics.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items have been resolved inline (see resolutions below). No blockers; Phase 01 may start immediately.

- [x] **Research §6.1 — Unification vs duck-typed.** Resolved per ADR-1: introduce `NetworkConnectionGate<C>` interface; `ConnectionGateRegistry` keyed by `NetworkProtocol`. SMB stays an adapter.
- [x] **Research §6.2 — Cloud lifecycle.** Resolved per ADR-2: cloud `closeFor(UI_*)` is a **no-op for sockets**; only triggers preemptive token-refresh state cleanup. OkHttp pool is left to its own management.
- [x] **Research §6.3 — FTP NOOP threshold.** Resolved: `NOOP` is sent only when `now - lastSuccess > IDLE_HEALTH_RECHECK_MS` (60 s). Below the threshold — local `isConnected` only.
- [x] **Research §6.4 — Coordination with S0066.** Resolved: gate exposes `Connection.lastRecreate: Long?` metadata; S0066 decoder may consult it without affecting S0067 surface. S0066 implementation deferred — not a blocker.
- [x] **Research §6.5 — Local gate.** Resolved: no local gate. Local I/O has no lifecycle/health semantics; consumers continue using direct `java.io` / SAF.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (cloud / sftp / ftp auto-recovery line).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated; new gate classes have `role` + `status` set.
- [ ] `/spec-check S0067` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0067`.

---

## Blockers Log

- 2026-05-03 — Phase 02 refined and implemented (lifecycle-only adapter, no SmbConnectionManager modifications, BUILD SUCCESSFUL).
- 2026-05-03 — **Phases 03–06 require strategic re-scope.** Investigation of `SftpConnectionPool` (510 LOC) reveals that, unlike `SmbConnectionPool.closeAllExceptWorker()`, the SFTP and FTP pools have no per-consumer (UI / WORKER) tagging. The same is true for the cloud REST clients. Achieving the strategic ADR-3 contract ("UI sessions close on `onStop`; `BACKGROUND_WORKER` survives") for SFTP / FTP / Cloud therefore requires:
  - **Per-pool refactor:** add a `consumer: ConsumerType` field on each pool entry, plus a `closeAllExceptWorker()`-style method.
  - **Touching pool internals:** `SftpConnectionPool` (510 LOC), `FtpExoPlayerPool`, `UnifiedCloudAuthManager` — each non-trivial.
  - This is significantly larger than the "thin adapter" implied by the original strategic spec.

  **Recommended next step (one of):**
  1. **Reduce S0067 scope** to registry + diagnostics + SMB-only (already implemented). Spawn separate tickets `S0068..S0071` for SFTP / FTP / Cloud per-pool tagging refactors.
  2. **Keep S0067 scope** but accept that Phases 03–05 are full pool refactors, not adapter work — re-run `/spec-tech S0067` against the actual pool APIs to regenerate phase plans.
  3. **Half-measure:** implement Phases 03–05 as no-op `closeFor` adapters with `lastRecreateMs` tracking only (gate is registered for uniform iteration, but does NOT actually close UI sessions). Phase 06 lifecycle observer becomes informational for SFTP/FTP/Cloud, behavioural for SMB.

  **Phase 01 + Phase 02 are solid and compile.** The infrastructure (interface, registry, diagnostics, SMB adapter) is in place and exercises uniformly. SMB users get the full S0061 lifecycle benefit through the gate already.

---

## Change Log

- 2026-05-03 — Initial tactical plan authored by `/spec-tech`.
