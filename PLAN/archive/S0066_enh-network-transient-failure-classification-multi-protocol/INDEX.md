# Tactical Plan: S0066 — enh-network-transient-failure-classification-multi-protocol

**Strategic spec:** [`../S0066_enh-network-transient-failure-classification-multi-protocol.md`](../S0066_enh-network-transient-failure-classification-multi-protocol.md)
**Feature:** Unified transient-failure classification for thumbnail extraction across SMB / SFTP / FTP
**Tier:** 3 — Moderate
**Priority:** 45
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.
>
> **Scope narrowing vs. strategic spec:** Cloud (Google Drive) thumbnails use an independent Glide pipeline (`GoogleDriveThumbnailModelLoader`) and do not flow through `NetworkVideoFrameDecoder` / `NetworkMediaDataSource`. Cloud transient classification is therefore **out of scope** for S0066 and will be addressed in a separate spec if needed. This plan unifies SMB / SFTP / FTP only.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations-resource-key-enum | — | ✅ Done | 4/4 | [PHASE_01__foundations-resource-key-enum.md](PHASE_01__foundations-resource-key-enum.md) |
| 02 | datasource-transient-detection | 01 | ✅ Done | 4/4 | [PHASE_02__datasource-transient-detection.md](PHASE_02__datasource-transient-detection.md) |
| 03 | decoder-universal-classification | 02 | ✅ Done | 3/3 | [PHASE_03__decoder-universal-classification.md](PHASE_03__decoder-universal-classification.md) |
| 04 | clear-transient-on-deactivate | 03 | ✅ Done | 3/3 | [PHASE_04__clear-transient-on-deactivate.md](PHASE_04__clear-transient-on-deactivate.md) |
| 05 | docs-catalog-cleanup | 01–04 | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All §6 Open research items have been resolved from the codebase:

- [x] **Research §6.1 (Cloud granularity)** — Resolved: cloud thumbnails use a separate Glide pipeline (`GoogleDriveThumbnailModelLoader`), do not pass through `NetworkVideoFrameDecoder`. Cloud is **out of scope** for S0066. No `cloud://` handling required.
- [x] **Research §6.2 (FTP transient signals)** — Resolved by reading `NetworkMediaDataSource.readFromFtp`: classify as transient when (a) `IOException` cause is `SocketTimeoutException` or `SocketException`, (b) message contains "Broken pipe" / "Connection reset", or (c) `replyCode` matches 421 (idle disconnect) or 426 (data connection broken). Other FTP failures stay permanent.
- [x] **Research §6.3 (Cloud 429)** — Resolved: cloud out of scope (see §6.1).
- [x] **Research §6.4 (Player datasources call `activateVideoPlayerMode`)** — Confirmed in code: `SmbPlaybackHelper.kt:71`, `SftpPlaybackHelper.kt:46`, `FtpPlaybackHelper.kt:49` all call `activateVideoPlayerMode(resourceKey)` with `<protocol>://host:port`. No additional wiring required.
- [x] **Research §6.5 (SMB compat)** — Resolved: keep `clearTransientFailuresForHost(smbHost: String)` as `@Deprecated` alias delegating to `clearTransientFailuresForResource("smb://$smbHost..")`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (user-facing — see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API on `NetworkMediaDataSource` and `NetworkFileDataFetcher` companion changes).
- [ ] `/spec-check S0066` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0066`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-05-03 — Initial tactical plan authored by `/spec-tech`.
