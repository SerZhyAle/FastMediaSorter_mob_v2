# Tactical Plan: S1028 - dedupe-network-path-detection

**Strategic spec:** [`../S1028_dedupe-network-path-detection.md`](../S1028_dedupe-network-path-detection.md)
**Feature:** Consolidate the scattered `isNetworkPath` predicate into one canonical home; preserve behavior; defer edge-case fixes.
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done (awaiting device verification - BlockNeedUserTest)
**Phases:** 2 / 2 done
**Last updated:** 2026-07-14

> Owner scope: consolidate to one canonical matcher, PRESERVE union behavior, DEFER edge-case fixes (no fixing the mislabeled comment / no changing which paths classify as network). Live file-op dispatch -> device-gated.

## The 5 duplicate implementations

1. `core/util/PathUtils.kt:54` `isNetworkPath(path)` - scheme-based (`getScheme` finds `://`), includes cloud. **This becomes the canonical home.**
2. `data/cloud/CloudFileOperationPathUtils.kt:38` `isNetworkPath(path)` - `startsWith(smb/sftp/ftp)` after `normalizeNetworkPath`, NO cloud.
3. `domain/usecase/NetworkImageEditUseCase.kt:188` `isNetworkPath(path)` (private) - `startsWith(smb/sftp/ftp)`, NO cloud.
4. `ui/dialog/helpers/GifEditorHelper.kt:24` `isNetworkPath(path)` - `startsWith(smb/sftp/ftp/cloud)`, includes cloud.
5. `domain/usecase/FileOperationUseCase.kt:228` local `File.isNetworkPath(protocol)` - 4-branch File-mangling-tolerant, parameterized per protocol (called with smb/sftp/ftp/cloud).

Note: `data/cloud/CloudFileOperationHandler.kt:101` already delegates to `pathUtils.isNetworkPath` - NOT a duplicate, leave it.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | canonical-matcher-and-tests | - | ✅ Done | 3 | [PHASE_01__canonical-matcher-and-tests.md](PHASE_01__canonical-matcher-and-tests.md) |
| 02 | route-sites-and-build | 01 | ✅ Done | 3 | [PHASE_02__route-sites-and-build.md](PHASE_02__route-sites-and-build.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Completion Gate

- [ ] Canonical `PathUtils.isNetworkPath(path, includeCloud)` + `PathUtils.fileMatchesProtocol(rawFilePath, protocol)` exist with unit tests covering well-formed schemes + File-mangled forms + cloud on/off.
- [ ] All 5 sites route through the canonical functions; the 4 duplicate bodies (sites 2-5) deleted/replaced. Behavior preserved (each site keeps its cloud-semantics via the flag; site 5 keeps File-mangling tolerance).
- [ ] standard debug build PASS.
- [ ] Device verification (copy/move/delete/rename across smb/sftp/ftp/cloud still route to the correct handler) - deferred to `BlockNeedUserTest`.

---

## Change Log

- 2026-07-14 - Tactical plan authored by `/spec-tech` (F2).
