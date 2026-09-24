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
$argvScriptCheatsheet = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-script-cheatsheet-sync.ps1"), '-Gate', '-Quiet', '-Repair')
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
# S3433: the changed set decides which glyphs are judged, so it is passed whether or not the closure is
# scoped - an unscoped closure over one drawable must not re-judge three hundred others.
$argvIconStyle = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-icon-style.ps1"), '-Gate', '-Quiet')
if ($changedFiles.Count -gt 0) { $argvIconStyle += @('-ChangedFiles', ($changedFiles -join ',')) }
# S3432: judges the whole tree against its baseline, and the changed set decides which keys are charged here.
$argvIconContract = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-icon-contract.ps1"), '-Gate', '-Quiet')
if ($changedFiles.Count -gt 0) { $argvIconContract += @('-ChangedFiles', ($changedFiles -join ',')) }

# S3371 security-profile, manifest-risk, journal-pairing and no-retry gates. Extracted here for
# the same reason as the family above: the facade crossed the Rule 2 ceiling at 2003 lines when
# this ticket's seventh gate was wired into it. $root, $ScopeToFile and $changedFiles are the
# caller's, exactly as for every vector already in this file.
# S3371: the changed set is this gate's whole subject, so it is passed whether or not the closure
# runs -ScopeToFile. The unscoped whole-tree scan is the scheduled security-scan workflow's job -
# it measured 44 s over 8109 files, which is a nightly cost, not a per-closure one.
$argvNoSecrets = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-no-secrets.ps1"), '-Gate')
if ($changedFiles.Count -gt 0) { $argvNoSecrets += @('-ChangedFiles', ($changedFiles -join ',')) }
$argvManifestRisk = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-manifest-risk-diff.ps1"), '-Gate')
if ($ScopeToFile -and $changedFiles.Count -gt 0) { $argvManifestRisk += @('-ChangedFiles', ($changedFiles -join ',')) }
$argvFileopJournalPairing = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-fileop-journal-pairing.ps1"), '-Gate')
# S3386: the caller half. Takes no changed set for the same reason the gate above does not - its
# subject is a class shape anywhere under the package, and every producer that exists today is
# already accounted for, so a finding is necessarily this change's.
$argvMutationProducer = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-mutation-producer-registration.ps1"), '-Gate')
$argvNoTestRetry = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-no-test-retry.ps1"), '-Gate')
$argvLogRedaction =@('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-log-redaction.ps1"), '-Gate')
if ($ScopeToFile -and $changedFiles.Count -gt 0) { $argvLogRedaction += @('-ChangedFiles', ($changedFiles -join ',')) }
$argvCredentialEncryption = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-credential-encryption.ps1"), '-Gate')
if ($ScopeToFile -and $changedFiles.Count -gt 0) { $argvCredentialEncryption += @('-ChangedFiles', ($changedFiles -join ',')) }
$argvDiagnosticsRedaction = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-diagnostics-redaction.ps1"), '-Gate')
if ($ScopeToFile -and $changedFiles.Count -gt 0) { $argvDiagnosticsRedaction += @('-ChangedFiles', ($changedFiles -join ',')) }
# S3371 phase 07 accessibility-semantics gate. The changed set is passed under -ScopeToFile only:
# unscoped, the gate reads its integer baseline over the whole of app_v2, which is the release and
# CI verdict; scoped, it judges a real per-file delta against HEAD, so another session's in-flight
# layout debt cannot fail this closure.
$argvA11ySemantics = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-a11y-semantics.ps1"), '-Gate')
if ($ScopeToFile -and $changedFiles.Count -gt 0) { $argvA11ySemantics += @('-ChangedFiles', ($changedFiles -join ',')) }
