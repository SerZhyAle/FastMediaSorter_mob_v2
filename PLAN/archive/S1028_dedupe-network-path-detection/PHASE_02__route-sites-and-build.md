# Phase 02 - route-sites-and-build

**Goal:** Route all 5 sites through the canonical functions; delete the duplicate bodies. Behavior preserved.

## Steps

- [ ] **2.1** Route the string-path sites (inject/inline `PathUtils` as each already does or via its existing access):
  - `data/cloud/CloudFileOperationPathUtils.kt:38` - keep its `normalizeNetworkPath(path)` call, then delegate to `pathUtils.isNetworkPath(normalized, includeCloud = false)`; delete the local `startsWith` body.
  - `domain/usecase/NetworkImageEditUseCase.kt:188` - replace the private body with a delegate to `pathUtils.isNetworkPath(path, includeCloud = false)` (inject `PathUtils` if not already available; it is a stateless util).
  - `ui/dialog/helpers/GifEditorHelper.kt:24` - delegate to `pathUtils.isNetworkPath(path, includeCloud = true)` (cloud included, matching current).
  - Verify: no local `startsWith`-based `isNetworkPath` body remains in these 3 files; each delegates; compiles.
- [ ] **2.2** Route the File-path site: `domain/usecase/FileOperationUseCase.kt:228` - replace the local `fun File.isNetworkPath(protocol)` with a call to `pathUtils.fileMatchesProtocol(this.path, protocol)` (keep the `hasProtocol(protocol)` wrapper and all 4 operation branches unchanged). Verify: local 4-branch body gone; `hasProtocol` uses the canonical; smb/sftp/ftp/cloud calls unchanged.
- [ ] **2.3** Insert one `Timber.d("S1028: network-path classified path=<..> net=<..>")` at the `hasProtocol` entry in FileOperationUseCase (the live-dispatch entry - device probe; this ticket goes to BlockNeedUserTest) before the final build. Then build: `a.ps1 dq` standard debug PASS; re-run `*PathUtilsTest*` + any `*FileOperation*` tests that exist. Verify: `BUILD SUCCESSFUL`; tests green.

## Done criteria
- All 5 sites consolidated; duplicate bodies gone; build + tests green; device probe in place.
