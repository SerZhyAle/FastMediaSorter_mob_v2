# Phase 01 - consolidate-path-normalization

**Goal:** Extract the byte-identical network-path normalization from the two resource strategies into one shared function.

## Steps

- [ ] **1.1** Add `internal fun normalizeNetworkResourcePath(path: String): String` to `domain/strategy/ResourceStrategy.kt` (top-level, same file as the strategy contract), body verbatim from the existing identical private copies. Name distinct from `PathUtils.isNetworkPath` / `CloudFileOperationPathUtils.normalizeNetworkPath` (different rule). Verify: function present; compiles.
- [ ] **1.2** Route `domain/strategy/SftpResourceStrategy.kt` and `FtpResourceStrategy.kt` through it; delete both private `normalizeNetworkPath` copies (:78-85 / :75-82). Verify: no private copy remains; both call the shared function.
- [ ] **1.3** Build + regression: `a.ps1 dq` PASS; existing `SftpResourceStrategyTest` + `FtpResourceStrategyTest` pass UNMODIFIED (they are the regression net). Verify: `gradlew testStandardDebugUnitTest --tests "*ResourceStrategyTest*"` PASS.

## Done criteria
- One shared normalizer; two copies gone; behavior preserved; tests green.
