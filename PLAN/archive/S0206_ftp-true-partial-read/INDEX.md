# Tactical Plan: S0206 — ftp-true-partial-read

**Strategic spec:** [`../S0206_ftp-true-partial-read.md`](../S0206_ftp-true-partial-read.md)
**Feature:** True byte-bounded FTP partial-read with client-side ABOR
**Tier:** 2 — Easy (ad-hoc)
**Priority:** 60
**Status:** BlockNeedUserTest — code merged, debug APK built (v2.60.5160.123), awaiting on-device FTP verification
**Phases:** 3 / 3 done
**Last updated:** 2026-05-16

> **Scope:** tactical, English, developer handoff. Every step has a static verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | bounded-read-helper | — | ✅ Done | 4/4 | [PHASE_01__bounded-read-helper.md](PHASE_01__bounded-read-helper.md) |
| 02 | wire-ftp-wrappers | 01 | ✅ Done | 5/5 | [PHASE_02__wire-ftp-wrappers.md](PHASE_02__wire-ftp-wrappers.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 4/4 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 lists three Open research items. The owner explicitly accepted proceeding with defensive defaults baked into Phase 01; final answers are verified empirically on real servers during `BlockNeedUserTest` after Phase 03. All blockers are therefore resolved-by-design.

- [x] **Research §6.1:** ABOR behavior across vsftpd / ProFTPD / FileZilla Server / NAS-built-in — resolved by Phase 01 fallback policy: bytes already collected are returned as success even if ABOR triggers a non-positive completion code; the client is marked as needing reconnect. Final empirical confirmation in `BlockNeedUserTest`.
- [x] **Research §6.2:** Network read-buffer size for bounded reads — resolved by Phase 01: fixed 8 KiB buffer (compromise between syscall overhead and overrun-past-limit; bounded to never exceed the requested cap by more than one buffer).
- [x] **Research §6.3:** Diagnostic-noise impact — resolved by Phase 01 invariant: ABOR is invoked **only** when the read reached the byte cap; natural EOF and any IO error before the cap follow the existing failure path unchanged.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — no changes (strategic §8 = «Без изменений»).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` + `app_v2.md` regenerated 2026-05-16 (1325 records). `FtpBoundedReadResult` indexed.
- [ ] `/spec-check S0206` returns `Verified`. **Pending:** transition to `BlockNeedUserTest` first (empirical confirmation of §6.1 ABOR behaviour across vsftpd / FileZilla / ProFTPD / NAS-built-in).
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`. **Pending** on the same gate.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0206`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-05-15 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-15 — Phase 01 (bounded-read-helper) complete: constant + result type + `readBoundedAndAbort` helper added to `FtpCommandUtils.kt`; standardDebug build PASS.
- 2026-05-16 — Phase 02 (wire-ftp-wrappers) complete: all four bounded `readFileBytes` paths (`FtpConnectedOperations` passive + active, `FtpStandaloneOperations` passive + active) now delegate to `readBoundedAndAbort`; unbounded branches preserved byte-for-byte; assembleStandardDebug PASS in 50s.
- 2026-05-16 — Phase 03 (docs-catalog-cleanup) complete: catalog regenerated (1325 records), `FtpCommandUtils.kt` role/status set, functionality log FIX entry appended. Tactical plan markers reconciled with actual repo state.
