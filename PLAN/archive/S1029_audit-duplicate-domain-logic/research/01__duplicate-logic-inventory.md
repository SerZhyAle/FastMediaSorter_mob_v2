# S1029 research 01 - duplicate-domain-logic inventory (bounded audit)

**Date:** 2026-07-14
**Owner scope:** one-time audit + fix the single top low-risk cluster; defer the rest with a note (no recurring gate). S1028 precedent = consolidate with preserved behavior only.

## Clusters (ranked)

| # | Cluster | Sites | Value | Risk |
|---|---|---|---|---|
| 1 | Resource-strategy network-path normalization (byte-identical) | domain/strategy/SftpResourceStrategy.kt:78-85 + FtpResourceStrategy.kt:75-82 | Med | **Lowest** - pure, no I/O, both sites unit-tested |
| 2 | `FileOperationResultExt.cleanErrorMessage` copy of `FileOperationErrorFormatter` | core/util/FileOperationErrorFormatter.kt:137-161 vs domain/usecase/FileOperationResultExt.kt:110-136 (self-admitted copy) | Low-med | Very low - identical, but a follow-up |
| 3 | `host:port` split reinvented ad hoc (incl. a file duplicating its own `networkAuthority()` helper) | data/cloud/NetworkCredentialsResolver.kt; core/util/NetworkFileDownloader.kt; data/network/glide/NetworkFileModelLoader.kt; ui/player/helpers/PlayerMediaLoaderManager.kt:562-625 (dup of its own :526-538) | High | Med - PlayerMediaLoaderManager has no tests; network-download codepaths |
| 4 | Extension->MIME mapping | data/cloud/CloudFileOperationPathUtils.kt:66-78; ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt:401-430; ui/dialog/helpers/FileInfoLaunchManager.kt:268-300 | Med-high | Med - NOT identical (divergent coverage/fallbacks); needs union-table decision |
| 5 | SMB error classification triplicated | data/network/SmbErrorClassifier.kt:39-124; SmbClientErrorFormatter.kt:17-85; exceptions/NetworkErrorClassifier.kt:244-255 | High | High - divergent, feeds live retry + user text; needs device verification |

Ruled out (not the same rule): SmbResourceStrategy/CloudResourceStrategy path normalization (genuinely different per-protocol rules); SmbConnectionManager purge-retry (distinct from the correctly-shared RetryPolicy/withRetry - no duplication there).

## Fix delivered this ticket - Cluster #1

- Extract `internal fun normalizeNetworkResourcePath(path: String): String` (body verbatim, both copies identical) into `domain/strategy/ResourceStrategy.kt` (hosts the strategy contract + schema for this family). Name deliberately distinct from `PathUtils.isNetworkPath` / `CloudFileOperationPathUtils.normalizeNetworkPath` (S1028's different rule) to avoid conflation.
- Route `SftpResourceStrategy` + `FtpResourceStrategy` through it; delete both private copies.
- Behavior 100% preserved (delete-and-repoint of identical bodies). Existing `SftpResourceStrategyTest` / `FtpResourceStrategyTest` are the regression net (unchanged).

## Deferred (documented per owner scope - follow-up candidates)

- Cluster #5 SMB error classification - highest value but divergent + live retry; needs S1028-style canonical+union pass with device verification. Own ticket.
- Cluster #4 extension->MIME - union-table design decision, not a pure move. Own ticket.
- Cluster #3 host:port parsing - widest; PlayerMediaLoaderManager needs tests first. Own ticket (or folds into S1028).
- Cluster #2 FileOperationResultExt copy - trivial (visibility + delete); good quick secondary win in a follow-up pass.
