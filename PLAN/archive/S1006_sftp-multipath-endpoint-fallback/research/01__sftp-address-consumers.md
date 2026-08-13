# Research 01 - Consumers of a SFTP resource address

**§6 item:** 1 - single chokepoint or many?
**Status:** Resolved
**Method:** codebase grep of `parseSftpPath` + reading connection-establishment code (2026-07-12).

## Finding: no single chokepoint - address is derived from the path string in ~11 places

`SftpPathUtils.parseSftpPath(path)` (host/port/remotePath from `sftp://host:port/..`) is called from, at minimum:

- `data/remote/sftp/SftpMediaScanner.kt` - browse/scan (4 call sites + own `parseSftpPath(path, credentialsId)` helper at L511 that also resolves credentials).
- `data/network/SftpFileOperationHandler.kt` - file operations; `parseSftpPath(path)` helper (L421) resolves credentials **by host:port** (see below).
- `data/transfer/strategy/SftpOperationStrategy.kt` - copy/move/rename/delete; own `parseSftpPath` (L287), ~15 call sites.
- `data/repository/ResourceRepositoryImpl.kt` L395 - inside `testSftpConnection`.
- `data/hash/SftpFileHasher.kt` L24.
- `domain/usecase/ResourceEditorUseCase.kt` L505.
- `domain/usecase/companion/ExportCompanionConfigUseCase.kt` L47 - export only, NOT a connection.
- `ui/settings/helpers/SzaResourcesImporter.kt`, `ui/main/helpers/MainSftpShareManager.kt` - display of host, NOT connections.
- `core/util/AudioMetadataLoader.kt` L374, `core/util/MediaFilePathDescriptor.kt` L64.

The ExoPlayer transport (`data/network/datasource/SftpDataSource.kt`) does NOT parse a path - it is constructed with `host/port/username/password` already resolved (by `SftpDataSourceFactory`), then obtains a pooled session via `SftpClient.getConnectionForExoPlayer`.

## Credentials are keyed by host:port, not by resource.credentialsId

`SftpFileOperationHandler.parseSftpPath` (L432-435):

```
credentials = credentialsRepository.getByTypeServerAndPort("SFTP", host, port)
if (credentials == null) credentials = credentialsRepository.getCredentialsByHost(host)
```

Implication: a WAN candidate (different host:port than the imported primary/LAN) has NO credential row → its by-host lookup returns null → connection fails at credential resolution, before any socket. The companion uses ONE username/password for all its access paths, so the fix is to **save a credential row per candidate host:port at import** (same user/pass). Consumers that instead carry credentials from the resource's single `credentialsId` still work after a host swap because user/pass are identical across candidates.

## Design implication

- The resolver врезка is per connection-establishment helper, not one point. Provide a shared `resolveEffectiveEndpoint(host, port)` consulted right after `parseSftpPath` and before credential lookup, in: `SftpMediaScanner`, `SftpFileOperationHandler`, `SftpOperationStrategy`, `SftpDataSourceFactory` construction, and `testSftpConnection`.
- The resolver must map a requested host:port to its full candidate group without needing the `MediaResource` object at the call site (low-level helpers only hold `path`). It resolves the group by consulting the resource store (match the requested host across each resource's primary path + alt list).
- The scanned `MediaFile.path` values keep the primary host as a stable group identifier; the resolver re-resolves host→reachable at connect time, so stored paths need not be rewritten per network.
