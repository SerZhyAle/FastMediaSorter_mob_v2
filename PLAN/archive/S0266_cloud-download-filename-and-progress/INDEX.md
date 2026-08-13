# Tactical Plan: S0266 — cloud-download-filename-and-progress

**Strategic spec:** [`../S0266_cloud-download-filename-and-progress.md`](../S0266_cloud-download-filename-and-progress.md)
**Feature:** Cloud download — filename, progress, silent APK launch
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 70
**Status:** Not started
**Phases:** 5 / 5 done
**Last updated:** 2026-05-20

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | filename-propagation | — | ✅ Done | 6/6 | [PHASE_01__filename-propagation.md](PHASE_01__filename-propagation.md) |
| 02 | cloud-byte-progress | 01 | ✅ Done | 4/4 | [PHASE_02__cloud-byte-progress.md](PHASE_02__cloud-byte-progress.md) |
| 03 | progress-dialog-cleanup | — | ✅ Done | 5/5 | [PHASE_03__progress-dialog-cleanup.md](PHASE_03__progress-dialog-cleanup.md) |
| 04 | nolegal-cloud-apk-install | 01 | ✅ Done | 5/5 | [PHASE_04__nolegal-cloud-apk-install.md](PHASE_04__nolegal-cloud-apk-install.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All §6 research items resolved inline in this plan:

- §6.1 (filename carrier): use `CloudFileHandle : File` subclass with `displayName` field. Minimal-invasive — only the cloud branch changes; SMB/local/SFTP/FTP stay on plain `File`. Resolution recorded in Phase 01.
- §6.2 (defensive metadata fetch): `CloudFileOperationHandler.downloadFromCloudTo` calls `client.getFileMetadata(fileId)` as a fallback only when the resolved name still looks like a bare fileId (no extension AND matches `^[A-Za-z0-9_-]{20,}$`). Resolution recorded in Phase 01.
- §6.3 (silent APK UX): default — short `Toast` "Подготавливается установка.." at start, system installer at end. No `FileOperationProgressDialog`. Resolution recorded in Phase 04.
- §6.4 (recovered last-viewed cloud paths): same defensive fetch in Phase 01 covers this case automatically.

No external blockers. Implementation may start with Phase 01.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES_noLegal.md` + `_RU` + `_UK` updated for noLegal APK auto-launch (Phase 05). Public `docs/FEATURES.md` not touched — fix, not new feature.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1 -Module app_v2`.
- [ ] `/spec-check S0266` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0266`.

---

## Blockers Log

- (empty)

---

## Change Log

- 2026-05-20 — Initial tactical plan authored by `/spec-tech`.
