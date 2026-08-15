# Phase 02 - landscape-pair-and-build

**Goal:** Keep the layout pair symmetric (Rule 11) and prove the build.

## Steps

- [ ] **2.1** For each portrait layout edited in Phase 01, check `res/layout-land/` (and any `values-land`/`sw*dp` bucket) for a paired file. If a land counterpart exists, apply the equivalent shared-anchor edit so the pair does not diverge (Rule 11). If none exists, note that explicitly. Also confirm no `values-swNNNdp` dimen shadows the new anchor unexpectedly.
  - Verify: land pair edited or explicitly N/A; `a.ps1 fr` PASS.
- [ ] **2.2** Build: `a.ps1 dq` (standard debug). Insert one `Timber.d("S1037: top-panels leading-anchor applied")` at the main-screen panel-setup entry (only because this ticket goes to BlockNeedUserTest) before this build. Build must PASS.
  - Verify: `BUILD SUCCESSFUL`.

## Done criteria
- Layout pair symmetric; standard debug builds green; device probe in place.
