<#
.SYNOPSIS
    Gate argument vectors of the doc/config/wear-wire family for post-change.ps1, extracted to
    hold the facade under the 2000-line ceiling of CLAUDE.md Rule 2 (S3254).

.DESCRIPTION
    Dot-sourced by post-change.ps1 at the spot these vectors used to occupy, so $root,
    $ScopeToFile and $changedFiles are all already set when it runs and every vector lands in the
    facade's scope unchanged. The gate TRIGGER flags stay at the facade's top level on purpose:
    post-change.tests/Run-Tests.ps1 reads them statically there.

    S2828: -ChangedFiles joins the vector BEFORE Start-PooledGate, so the pool is keyed by the
    same vector the gate is later invoked with.
    S2824: the launcher-reset vector is scoped HERE, not at the call site - Start-PooledGate
    below keys the warmed job by the exact argument vector, so a call site that consumed a
    different one would miss the pool and run inline.
#>

$argvAllFeatures = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-allfeatures-sync.ps1"), '-Gate', '-Quiet')
$argvHowToPaths = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-howto-settings-paths.ps1"), '-Gate')
$argvScriptCheatsheet = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-script-cheatsheet-sync.ps1"), '-Gate', '-Quiet')
$argvCodeDomainWriters = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-code-domain-writers.ps1"), '-Gate', '-Quiet')
$argvFlavorMatrixDoc = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-flavor-matrix-docs.ps1"), '-Gate', '-Quiet')
if ($ScopeToFile -and $changedFiles.Count -gt 0) { $argvFlavorMatrixDoc += @('-ChangedFiles', ($changedFiles -join ',')) }
$argvOssNotices = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-oss-notices.ps1"), '-Gate', '-Quiet')
if ($ScopeToFile -and $changedFiles.Count -gt 0) { $argvOssNotices += @('-ChangedFiles', ($changedFiles -join ',')) }
$argvRuleDigest = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-rule-digest-sync.ps1"), '-Gate')
if ($ScopeToFile -and $changedFiles.Count -gt 0) { $argvRuleDigest += @('-ChangedFiles', ($changedFiles -join ',')) }
$argvLauncherReset = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-launcher-reset-coverage.ps1"), '-Gate', '-Quiet')
if ($ScopeToFile -and $changedFiles.Count -gt 0) { $argvLauncherReset += @('-ChangedFiles', ($changedFiles -join ',')) }
$argvWearWireVocabularyParity = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-wear-wire-vocabulary-parity.ps1"), '-Gate', '-Quiet')
$argvWearWireNullability = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-wear-wire-nullability.ps1"), '-Gate', '-Quiet')
if ($ScopeToFile -and $changedFiles.Count -gt 0) { $argvWearWireVocabularyParity += @('-ChangedFiles', ($changedFiles -join ',')) }
