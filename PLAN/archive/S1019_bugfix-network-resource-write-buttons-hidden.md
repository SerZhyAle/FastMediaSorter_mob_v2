# S1019 - Unchecking "read-only" does not enable write buttons for a network resource

**Status:** Archived

## 0. Raw report (owner, 2026-07-12)

Added a resource via barcode/QR (build before S1016). Opened its editor, unchecked "read-only",
exited. Re-entered the resource, selected a file - but "Move", "Rename", "Delete" did nothing / did
not appear. Is this a bug? Investigate and file a ticket if not a quick fix.

## 1. Symptom

For a companion/network (SFTP) resource, clearing the editor "read-only" checkbox does not make the
browse-screen Move / Rename / Delete buttons available on a selected file. The same resource shows
write actions in the player but not in browse.

## 2. Root cause - several interacting mechanisms

- Browse write-op gate requires the probed `isWritable`, not just policy.
  [BrowseStateUiUpdater.updateSelectionPanel] - `btnMove/btnRename/btnDelete.isVisible =
  hasSelection && (resource.isWritable == true && resource.isReadOnly != true)`. Clearing read-only
  is necessary but not sufficient; `isWritable` must also be true.
- Companion QR import never initialises `isWritable`. `ImportCompanionConfigUseCase.buildResource`
  does not set it (model default `false`); `AddResourceUseCase.addMultiple` does not probe it. So a
  QR-imported resource starts `isWritable = false`.
- Editing preserves the stale `isWritable`. `ResourceEditorUseCase.save` (EDIT) copies
  `isWritable = existing.isWritable` to avoid a different disappearing-buttons regression - so
  unchecking read-only keeps `isWritable = false` when it was never probed.
- `isWritable` only flips via the resource-list scan (`ResourceScanCoordinator`), which runs on the
  main screen, not on entering browse - leaving a window where browse renders with `isWritable =
  false`, and `BrowseState.resource` can stay stale after a later flip.
- The SFTP probe is semantically a connectivity check, not a write-permission check.
  `SftpMediaScanner.isWritable` returns `sftpClient.testConnection(..).isSuccess` - true whenever the
  host is reachable and credentials valid, regardless of real write permission (and false until a
  scan runs).
- Player and browse disagree. `resolvePlayerFilePermissions` sets `canWrite = true` for any network
  resource (only `isReadOnly` clears it), ignoring `isWritable`. So the same file offers write actions
  in the player but not in browse.

## 3. Why not a quick fix

The correct behaviour needs an owner decision on the canonical "can write" semantics for network
resources - none of the current signals is right on its own:

- connectivity probe (today's `isWritable` for SFTP) - wrong, reflects reachability not permission;
- local policy (`isReadOnly`) - user-controllable but ignores server-side enforcement;
- a real write probe (create+delete a temp entry) - not implemented.

Plus the player vs browse gates must be reconciled, and (post-S1016) companion server-side read-only
enforcement must be honoured. A one-line change to the browse gate would make browse match the player
but would surface write buttons for server-read-only shares, where writes then fail server-side - a
UX/behaviour tradeoff, not a safe mechanical fix.

## 4. Relationship to S1016

S1016 lets the companion mark a share writable (`readOnly:false`). That intent still will not reach
the browse write buttons, because import leaves `isWritable = false` until a scan flips it and the
gate depends on the connectivity-probe. S1019 is what makes an S1016 writable share actually usable in
browse - they should ship together or S1016 remains half-usable there.

## 5. Candidate directions (to decide at approval)

- A. Single `canWriteResource(resource)` helper shared by player + browse; for network resources gate
  on `!isReadOnly` only, treat the probe as connectivity. Consistent; writes to a server-read-only
  share fail at the server and need a clear error message.
- B. Companion import sets `isWritable` from the S1016 `readOnly` flag (writable share -> true) AND a
  real SFTP write probe replaces the connectivity check so `isWritable` reflects true permission.
- C. On clearing read-only in the editor for a network resource, re-probe/refresh `isWritable` so the
  browse gate updates immediately.

## 6. Implementation (chosen: A - unified write-policy resolver)

Principle: what the user sees offered = what the operation layer will attempt.

- New single source of truth `MediaResource.allowsWriteOperations()`
  (domain/model/ResourceWriteCapability.kt): `isReadOnly` wins; network (SMB/SFTP/FTP) writable when
  not read-only (server enforces at operation time, error surfaced); LOCAL/CLOUD gated by the probed
  `isWritable`; streams never writable.
- Routed every write affordance and operation guard through it, so player and browse can no longer
  diverge and an offered action is never blocked downstream:
  - browse selection panel (Move/Rename/Delete visibility) - BrowseStateUiUpdater
  - browse adapter permissions - BrowseActivity
  - player per-file permission resolver - CommandPanelPermissions (network branch now via resolver;
    per-file read + raw-file fallback preserved)
  - network file write guard - NetworkFileManager.prepareFileForWrite
  - save-text-to-resource guard - SaveTextFileToResourceUseCase
  - destination eligibility - ResourceToAddAdapter
  - text-viewer edit affordance - PlayerMediaLoaderManager
- Not changed: local/cloud keep the real `isWritable` probe; the connectivity-only SFTP probe is left
  as-is but no longer gates write affordance (so a real SFTP write probe, direction B, stays a
  possible follow-up if server-read-only shares should hide actions rather than error on write).

## 7. Evidence (files)

- app_v2/.../ui/browse/managers/BrowseStateUiUpdater.kt (updateSelectionPanel, the gate)
- app_v2/.../ui/player/CommandPanelPermissions.kt (resolvePlayerFilePermissions - divergent gate)
- app_v2/.../domain/usecase/ResourceEditorUseCase.kt (save EDIT preserves isWritable)
- app_v2/.../ui/main/helpers/ResourceScanCoordinator.kt (the only isWritable writer)
- app_v2/.../data/remote/sftp/SftpMediaScanner.kt (isWritable == testConnection, connectivity)
- app_v2/.../domain/usecase/companion/ImportCompanionConfigUseCase.kt (import leaves isWritable false)

---

## Remote log pass 2026-08-01/02

Device SM-S731B (Galaxy S25 FE), Android 16 / API 36, noLegal debug 2.60.7302.058. Bundle imported
via `/newlog` from `logs/fastmediasorter_20260729_162305.log` .. `logs/fastmediasorter_20260801_183450.log`.
This is a probe-firing record, not an acceptance verdict - a log proves the code path ran, not that
the screen looked right.

- Probe fired 676 times: `write-gate type=LOCAL readOnly=false canWrite=true` (593), `type=SFTP` (61), `type=CLOUD` (22).
- The SFTP leg the status note asks for was exercised and the gate answered `canWrite=true`.
- Not covered: the write actions were not observed to PERFORM on SFTP, the read-only re-check leg never ran, and no SMB or FTP resource was touched.
