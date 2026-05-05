# S0055 — Diagnostic Noise Cleanup: Tactical Index

**Strategic spec:** `PLAN/S0055_diagnostic-noise-cleanup.md`
**Status:** Tactical
**Tier:** 2 — Easy
**Updated:** 2026-05-03

---

## Phase Overview

| # | Phase | Status | Steps | Key files |
|---|-------|--------|-------|-----------|
| 01 | [Glide fetcher cancel + SMB label fixes](PHASE_01__glide-fetcher-cancel-smb.md) | `[ ]` not started | 2 | `NetworkFileModelLoader.kt` |
| 02 | [Test-creds log level](PHASE_02__test-creds-log-level.md) | `[ ]` not started | 1 | `NetworkCredentialsRepositoryImpl.kt` |
| 03 | [Operation cancel log](PHASE_03__operation-cancel-log.md) | `[ ]` not started | 2 | `FileOperationDestinationDialog.kt` |
| 04 | [Docs & catalog cleanup](PHASE_04__docs-catalog-cleanup.md) | `[ ]` not started | 3 | `dev/CHANGELOG.md`, catalog |

---

## Affected Files (all `app_v2/src/main/java/com/sza/fastmediasorter/`)

| File | Lines | Fix | Phase |
|------|-------|-----|-------|
| `data/network/glide/NetworkFileModelLoader.kt` | 758 | A: remove debug stack trace in `cancel()`; B: distinguish CancellationException vs real timeout in `fetchBytesFromSmb` | 01 |
| `data/repository/NetworkCredentialsRepositoryImpl.kt` | 273 | C: lower missing-file warning to debug | 02 |
| `ui/dialog/FileOperationDestinationDialog.kt` | 606 | D: suppress stack in cancellation catch, downgrade W→I | 03 |

No new classes, no Room schema changes, no UI strings, no Wear OS changes.

---

## Completion Criteria (from §11)

- [ ] `grep -n 'Exception("Trace")' NetworkFileModelLoader.kt` → 0 matches.
- [ ] `grep -n 'fetchBytesFromSmb TIMEOUT' NetworkFileModelLoader.kt` → only in real-exception branch (not CancellationException branch).
- [ ] `grep -n 'Timber.w.*TEST_CREDS.*not found' NetworkCredentialsRepositoryImpl.kt` → 0 matches; `Timber.d` present instead.
- [ ] `grep -n 'Timber.w.*performOperation.*cancelled' FileOperationDestinationDialog.kt` → 0 matches; `Timber.i` without exception arg present.
- [ ] Build passes: `.\gradlew.bat assembleStandardDebug`.
- [ ] Lint clean: `.\gradlew.bat lintStandardDebug`.

---

## Open Questions (from strategic §6)

1. **A — minimal log on cancel?** Default: remove completely. Decide in Step 1.1.
2. **D — CancellationException from system vs user?** Default: any CancellationException without reason → `Timber.i` without stack. Decide in Step 3.1.
