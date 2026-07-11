# Maestro Suite Index

Status: active S0551 capability-regression suite.

## Entry Points

- `run-tests.ps1` - primary compact runner with off-context logs and stable exit codes.
- `README.md` - current flow map, preconditions, and runner contract.
- `WRITING_TESTS.md` - oracle and authoring rules.
- `INSTALLATION_WINDOWS.md` / `TROUBLESHOOTING.md` - local setup and failure triage.

## Active Flow Groups

- `smoke/` - app launch, local browse, 3D video smoke, screen tour, add-resource forms, settings toggle sweep.
- `critical/` - file operations and settings.
- `features/browse/` - All Images, filter, sort + empty state.
- `features/files/` - rename, trash/undo, overwrite conflict.
- `features/resource/` - create local, edit (rename round-trip), full create+delete lifecycle.
- `features/settings/` - settings search empty-state.
- `features/player/` - video, image, audio lyrics, documents, resume, info dialog.
- `features/slideshow/` - slideshow start/stop regression.
- `features/edge/` - no-extension and large-video edge cases, back-from-every-screen.
- `_shared/` - reusable permission/navigation fragments.

`-Suite all` is the emulator-default suite. It excludes device-only file-operation flows until
`Ops/src` and `Ops/dst` are registered as writable operation resources on the target device.
It also excludes the `features/resource/` flows (need All-Files access granted for the debug
package on API 30+); run those with an explicit `-Suite features\resource`.
Network/cloud flows are deferred from the active suite because they require external reachability.

## Run Examples

```powershell
pwsh -NoProfile -File maestro/run-tests.ps1 -Suite all -Json
pwsh -NoProfile -File maestro/run-tests.ps1 -Suite features\player -Json
pwsh -NoProfile -File maestro/run-tests.ps1 -Suite smoke -DeviceId emulator-5554
```
