# Tactical Plan: S0195 — network-first-use-trigger

**Strategic spec:** [`../S0195_network-first-use-trigger.md`](../S0195_network-first-use-trigger.md)
**Feature:** Move network lifecycle bootstrap from process start to consumer-side first-use boundary
**Tier:** 3 — Moderate
**Priority:** 55
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-14

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Pre-Implementation Design Decisions (resolves strategic §6)

The strategic §6 questions demand answers in the tactical spec itself, not deferred to phases. Resolved here:

### §6.1 — Chosen consumer-side network entry boundary

Audit of `app_v2/src/main/`:

- `ConnectionGateRegistry` (the abstract gate registry) is currently injected into **only** `NetworkLifecycleObserver`. Business consumers do **not** call `registry.gateFor(...)` yet — gates are an infrastructure-only registration sink.
- Real consumer-side entries are per-protocol manager / client methods:
  - **SMB:** `SmbConnectionManager.withConnection(...)` and `SmbConnectionManager.getConnectionForExoPlayer(...)` — every SMB remote operation funnels through one of these two methods.
  - **SFTP:** `SftpClient` — first network-touching method (e.g. `connect()` / `withConnection(...)` / similar).
  - **FTP:** `FtpClient` — first network-touching method.
  - **Cloud:** `GoogleDriveRestClient`, `DropboxClient`, `OneDriveRestClient` — first HTTP-issuing method per client.

Chosen boundary: **the first network-touching method of each per-protocol consumer manager / client**. Each such method receives `dagger.Lazy<NetworkLifecycleBootstrapper>` and calls `.get().ensureInitialized()` synchronously before issuing the network operation. Multiple call sites are fine — `ensureInitialized()` is idempotent (AtomicBoolean-gated).

Why not a single facade: introducing a unifying `NetworkAccess` facade would require migrating every direct consumer of `SmbConnectionManager` / `SftpClient` / `FtpClient` / cloud clients — significantly broader scope than S0195. The per-method trigger is local and cheap.

### §6.2 — Deferred protocol-gate assembly strategy

Audit shows that `ConnectionGateRegistry` and all four protocol gates (`SmbConnectionGate`, `SftpConnectionGate`, `FtpConnectionGate`, `CloudConnectionGate`) are referenced only from:

- `NetworkLifecycleModule.kt` (DI binding)
- `NetworkLifecycleObserver.kt` (consumer)
- `ConnectionGateRegistry.kt` (the registry itself)

No other file injects the registry or any individual gate. Therefore, once `NetworkLifecycleObserver` is no longer eagerly injected into `FastMediaSorterApp` (Phase 03), the entire registry + gates graph is materialized **only** when `NetworkLifecycleBootstrapper.ensureInitialized()` first dereferences `Lazy<NetworkLifecycleObserver>`.

No multibinding refactor or separate deferred-assembly phase is required. Phase 03 alone satisfies ADR-3.

### §6.3 — Cleanup path behaviour before first remote use

`NetworkLifecycleObserver.onStop` iterates `registry.all()` and calls `closeFor(...)` on each gate. After Phase 03, `NetworkLifecycleObserver` does not exist in heap until first remote use → no `onStop` callback registered → no cleanup path runs. Strategic §6.3 invariant ("cleanup path is no-op pre-first-use") is enforced by physical non-existence of the observer, not by code branching.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | bootstrapper-foundation | — | ✅ Done | 3/3 | [PHASE_01__bootstrapper-foundation.md](PHASE_01__bootstrapper-foundation.md) |
| 02 | consumer-boundary-wiring | 01 | ✅ Done | 5/5 | [PHASE_02__consumer-boundary-wiring.md](PHASE_02__consumer-boundary-wiring.md) |
| 03 | remove-eager-hooks | 02 | ✅ Done | 3/3 | [PHASE_03__remove-eager-hooks.md](PHASE_03__remove-eager-hooks.md) |
| 04 | docs-catalog-cleanup | 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All §6 research items resolved above. No external blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` — no update (strategic §8: "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `NetworkLifecycleBootstrapper` class).
- [ ] `/spec-check S0195` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0195`.

---

## Blockers Log

_(none yet)_

---

## Change Log

- 2026-05-14 — Initial tactical plan authored by `/spec-tech`. §6.1, §6.2, §6.3 resolved in tactical spec; no external blockers.
