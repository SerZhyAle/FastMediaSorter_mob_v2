---
name: project_write_permission_gating
description: Resource write-permission gating - isWritable (probe) vs isReadOnly (policy), unified by MediaResource.allowsWriteOperations() (S1019)
metadata:
  type: project
---
Two distinct flags on `MediaResource` govern "can the user write here", and they are NOT the same thing:
- `isReadOnly` = user POLICY (the editor "read-only" checkbox). Authoritative user intent.
- `isWritable` = a PROBED capability set async by `ResourceScanCoordinator` (main-screen scan), NOT at import/edit. Crucially, `SftpMediaScanner.isWritable` (and the other network scanners) implement it as a CONNECTIVITY check (`testConnection().isSuccess`), so for SFTP/SMB/FTP it means "reachable", not "server grants write". It starts `false` and only flips true after a scan.

The effective write gate was historically `isWritable && !isReadOnly`, but the sites DIVERGED: the player (`resolvePlayerFilePermissions`/CommandPanelPermissions) treated any network resource as writable unless `isReadOnly` (ignoring the probe), while browse (`BrowseStateUiUpdater`, `BrowseActivity` -> `MediaFileAdapter.isWritable`) required `isWritable` too. Net bug (S1019): a QR/companion-imported SFTP resource has `isWritable=false` (import never probes; edit preserves it via `ResourceEditorUseCase.save`'s runtime-field copy at line ~224), so clearing "read-only" did nothing in browse - Move/Rename/Delete stayed hidden - even though the player showed them.

**Why:** S1019 (2026-07-12), reported by owner after S1016 added writable companion shares. Principle demanded: "what the user sees offered = what the operation layer attempts."

**How to apply:**
- Never gate write affordance on raw `isWritable` for a network resource - use `MediaResource.allowsWriteOperations()` (domain/model/ResourceWriteCapability.kt), the single resolver: `isReadOnly` wins; network (SMB/SFTP/FTP) writable when not read-only; LOCAL/CLOUD keep the probe; streams never. Route BOTH UI affordances and operation-boundary guards (NetworkFileManager.prepareFileForWrite, SaveTextFileToResourceUseCase, ResourceToAddAdapter destination eligibility, PlayerMediaLoaderManager text edit) through it, or "button shows but op blocked" reappears.
- Consequence to accept: a server-side-read-only share now shows write buttons and the write fails at the server with an error (the companion server enforces read-only per the S1016 contract). Making buttons hide instead would need a REAL SFTP write probe (create+delete temp) replacing the connectivity check - that is the deferred "direction B" in S1019 spec, not done.
- S1019 shipped as BlockNeedUserTest (device verification needed: clearing read-only enables + performs Move/Rename/Delete on an SFTP resource; player matches).
