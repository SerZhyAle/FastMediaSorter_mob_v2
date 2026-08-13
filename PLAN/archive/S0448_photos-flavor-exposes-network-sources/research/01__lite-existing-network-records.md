# Research 01 - Fate of already-added network records in `lite`

Resolves strategic §6 item 1.

## Question

What happens to SMB/SFTP/FTP resource records a user may have added in `lite` before the gate is introduced?

## Finding

Network records are persisted in the Room `ResourceEntity` table with a `type: ResourceType` column (`SMB`, `SFTP`, `FTP`). Display is already filtered through the single availability gate:

- `MainViewModel.applyFiltersAndSorting()` filters every resource via `remoteSourceGate.isEnabled(it)` before passing to the filter manager. Once `compileSupported(SMB/SFTP/FTP)` returns `false` in `lite`, those records disappear from the ALL tab and from their type tabs automatically.
- The type tabs themselves are built dynamically from the gate (`MainResourceTabsManager`), so they are not shown at all in `lite`.

No record reaches the UI once the gate is disabled.

## Resolution

Non-destructive hide. The existing gate-based filtering removes the records from display without any new code. Do NOT add a delete/notify migration:

- Deletion is destructive and irreversible if the user later installs a network-capable build (same `applicationId` suffix `.lite` is separate, but the policy must not silently destroy user data).
- The records are inert while hidden - no background network access is triggered for a source the UI never surfaces.

Decision: existing `lite` network records remain in storage, silently hidden by the gate. No migration, no user notification.

## Impact on plan

No dedicated phase/step. Covered implicitly by the Phase 01 gate extension; add a verification note that ALL-tab filtering excludes disabled network records (already exercised by `MainViewModel.applyFiltersAndSorting`).
