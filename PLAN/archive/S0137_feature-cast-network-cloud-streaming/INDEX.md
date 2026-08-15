# Tactical Plan: S0137 — feature-cast-network-cloud-streaming

**Strategic spec:** [`../S0137_feature-cast-network-cloud-streaming.md`](../S0137_feature-cast-network-cloud-streaming.md)
**Feature:** Cast network/cloud streaming via local proxy
**Tier:** 4 — Architectural
**Priority:** 50
**Status:** In Progress
**Phases:** 2 / 5 done
**Last updated:** 2026-05-10

> **Scope:** tactical, English, developer handoff. Every step has a static verification predicate. Rationale lives in the strategic spec.

---

## Strategic-to-tactical mapping

The strategic plan listed four phases (F1 SMB · F2 SFTP+FTP · F3 Cloud · F4 Progress). During context gathering it was discovered that `NetworkFileManager.prepareFileForRead` already provides a uniform SMB/SFTP/FTP/Cloud → `File` pipeline through `UnifiedFileCache`. The tactical plan therefore collapses F1+F2+F3 into a single delegation phase (Phase 02), making strategic F3 (Cloud) come "for free" with no extra protocol-specific code. Phase 04 covers F4 and is gated on a `/ui-clarify` decision.

This also resolves strategic open question §6.4 (cache reuse): `UnifiedFileCache` stores by `(path, size)` and is shared with the player, so re-casting the same file in a session is instant.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | wire-network-file-manager | — | ✅ Done | 2/2 | [PHASE_01__wire-network-file-manager.md](PHASE_01__wire-network-file-manager.md) |
| 02 | delegate-remote-download | 01 | ✅ Done | 3/3 | [PHASE_02__delegate-remote-download.md](PHASE_02__delegate-remote-download.md) |
| 03 | size-gates-audio-image | 02 | ⬜ Not started | 0/3 | [PHASE_03__size-gates-audio-image.md](PHASE_03__size-gates-audio-image.md) |
| 04 | progress-feedback | 02 | ⛔ Blocked | 0/2 | [PHASE_04__progress-feedback.md](PHASE_04__progress-feedback.md) |
| 05 | docs-catalog-cleanup | 03, 04 | ⬜ Not started | 0/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [ ] **Research §6.1 — file size limits.** Owner must confirm `MAX_AUDIO_CAST_BYTES` and `MAX_IMAGE_CAST_BYTES` (proposed defaults: 50 MB audio, 30 MB image). Required before Phase 03.
- [ ] **Research §6.3 + `/ui-clarify` — progress UI format.** Decide between updating toast, foreground notification with progress, or dialog. Required before Phase 04.

Phases 01 and 02 are unblocked and may start immediately.

Resolved during /spec-tech research:

- Strategic §6.2 (cloud sync InputStream) — `NetworkFileManager.downloadCloudFileForRead` already wraps Google Drive / Dropbox / OneDrive uniformly via `downloadFile(id, OutputStream)`; no further research needed.
- Strategic §6.4 (cache reuse) — `UnifiedFileCache` keyed on `(path, size)` is reused; same file is shared with player playback.

---

## Completion Gate

- [ ] All non-blocked phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated with the network/cloud Cast capability.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated and `app_v2.md` rendered.
- [ ] String locale audit (`scripts/check_strings_localized.ps1 -KeyPrefix "cast_"`) returns exit 0.
- [ ] `/spec-check S0137` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status via `update.ps1 -Status Block...`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0137`.

---

## Blockers Log

- 2026-05-10 — Phase 03 deferred until owner confirms `MAX_AUDIO_CAST_BYTES` / `MAX_IMAGE_CAST_BYTES`.
- 2026-05-10 — Phase 04 deferred until `/ui-clarify` resolves progress-indicator format.
- 2026-05-10 — Phases 01 + 02 implemented and built (debug, exit 0). Spec held at `BlockNeedUserTest` pending on-device cast of SMB / SFTP / FTP / Cloud media.

---

## Change Log

- 2026-05-10 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-10 — Phases 01 + 02 executed by `/spec-dev`; build PASS; spec → `BlockNeedUserTest`.
