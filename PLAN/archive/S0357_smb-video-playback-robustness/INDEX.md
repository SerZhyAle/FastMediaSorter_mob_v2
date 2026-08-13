# Tactical Plan: S0357 - smb-video-playback-robustness

**Strategic spec:** [`../S0357_smb-video-playback-robustness.md`](../S0357_smb-video-playback-robustness.md)
**Feature:** Устойчивость воспроизведения сетевого видео (SMB)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 60
**Status:** Not started
**Phases:** 0 / 5 done
**Last updated:** 2026-06-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | datasource-uri-contract | - | ⬜ Not started | 0/4 | [PHASE_01__datasource-uri-contract.md](PHASE_01__datasource-uri-contract.md) |
| 02 | buffering-recovery | 01 | ⬜ Not started | 0/4 | [PHASE_02__buffering-recovery.md](PHASE_02__buffering-recovery.md) |
| 03 | decoder-graceful-refusal | - | ⬜ Not started | 0/4 | [PHASE_03__decoder-graceful-refusal.md](PHASE_03__decoder-graceful-refusal.md) |
| 04 | decoder-message-localization | 03 | ⬜ Not started | 0/2 | [PHASE_04__decoder-message-localization.md](PHASE_04__decoder-message-localization.md) |
| 05 | docs-catalog-cleanup | all | ⬜ Not started | 0/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phase 03 (decoder refusal) has no data dependency on Phase 01/02 - it touches the player error ladder, not the network DataSource contract. It may run in parallel with the DataSource work; only Phase 04 depends on it (the user-visible string).

---

## Pre-Implementation Blockers

Phase 01 must not start while blocker R1 is unchecked. Phase 02 must not start while R2 is unchecked. Phase 03 must not start while R3 is unchecked. Each maps to a strategic §6 research item that is currently `Status: Open`.

- [ ] **Research R1:** root cause of zero-read / empty-URI on SMB open - required before Phase 01. See strategic §6.1. Resolve: is the empty `getUri()` driven by pooled-connection reopen, seek-to-end DataSpec reuse, or media3 DataSpec re-use across files? Confirm whether the contract must reject a zero-read open or only guarantee a stable URI.
- [ ] **Research R2:** root cause of stuck buffering on SMB - required before Phase 02. See strategic §6.2. Resolve: does the source stall on a dropped socket, pool exhaustion, or timeout, and does a session-level reopen recover it on the failing files from the 2026-06-04 log?
- [ ] **Research R3:** decoder support boundary on Quest - required before Phase 03. See strategic §6.3. Resolve: which resolution/profile is reliably unsupported by the hardware decoder so it can be recognised from `DecoderInitializationException` rather than a resolution heuristic.

---

## Scope-Overlap Note (owner confirmation required before Phase 01/02)

Two sibling tickets created from the same 2026-06-04 Quest 3 log already cover the first two sub-problems and are currently `BlockNeedUserTest` (implemented, awaiting device test):

- **S0343** (priority 90) - SMB/FTP/SFTP DataSource `getUri()` null race on close (errorCode 2000). Already preserves URI identity in `SmbDataSource.close()` and `FtpDataSource.close()`.
- **S0344** (priority 50) - SMB streaming robustness, buffering-hang classification (errorCode 1004). Already classifies `ERROR_CODE_FAILED_RUNTIME_CHECK` as a named "mark-and-advance" path in `VideoPlayerErrorHandler`.

S0357 is therefore scoped as an umbrella that adds only the delta over S0343/S0344:

- Phase 01 formalises the URI-after-open invariant on a **shared contract** (strategic ADR-1) spanning SMB/SFTP/FTP, rather than repeating the per-class patch S0343 already shipped.
- Phase 02 adds **bounded reopen/retry recovery before escalation** at the session level; S0344 escalates to a named error but does not attempt session-level recovery first.
- Phase 03 (decoder refusal, errorCode 4001) has **no** sibling ticket and is net-new in `src/main`.

Owner decision needed: confirm S0357 proceeds as the umbrella delta above, or fold Phase 01/02 into S0343/S0344 and reduce S0357 to the decoder work (Phases 03-05). Recorded in Blockers Log 2026-06-04.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip: strategic §8 states "Без изменений" (reliability hardening, not a new capability).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0357` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0357`.

---

## Blockers Log

- 2026-06-04 - Phase 01/02 scope overlaps S0343 (BlockNeedUserTest) and S0344 (BlockNeedUserTest). Next: owner confirms umbrella-delta framing (see Scope-Overlap Note) before implementation starts; if S0343/S0344 pass device test as-is, Phase 01/02 narrow to the shared-contract abstraction and session-level reopen only.
- 2026-06-04 - Phases 01/02/03 gated by research blockers R1/R2/R3 (strategic §6, all Open). Next: resolve each before the corresponding phase.

---

## Change Log

- 2026-06-04 - Initial tactical plan authored by `/spec-tech`.
