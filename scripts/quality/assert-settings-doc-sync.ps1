<#
.SYNOPSIS
    S0440 Phase 04 - composite settings-doc drift gate.

.DESCRIPTION
    One gate that proves the settings documentation is in sync with the app.
    Stages (any failure -> exit 1 naming the failing stage):
      1. catalog completeness (assert-settings-catalog-complete.ps1)
      2. manifest freshness (the SettingsManifestExportTest verify run)
      3. annotation coverage/parity (check-settings-annotations.ps1)
      4. reference freshness (re-render to a temp dir and byte-diff the committed
         published SETTINGS_REFERENCE*.md files)
      5. HOW_TO recipe freshness (S0558 - the "Settings -> .." recipes in the
         HOW_TO guides resolve against the manifest and stay in EN/RU/UK parity)
    Exit 0 only when all stages pass.

    Run with -Gate from post-change.ps1; without -Gate it behaves identically
    (the switch exists so the call site reads intentionally).

    -ChangedFiles narrows stage 2, the only expensive one: the manifest can only move
    when a settings layout, a strings file, ui/settings code or the settings-search DI
    changed, so a closure touching none of them skips the ~28 s gradle run and the gate
    finishes in about three seconds. The four cheap stages always run.

    Exit codes:
      0 - every stage passed.
      1 - a stage found real drift (the failing stage is named).
      2 - a stage could not be judged: either the manifest test never ran because the
          build failed before it (compile/kapt), so "did not look" is reported as
          such instead of as drift (S1338 step 04.5); or stage 2 waited out
          -WaitTimeoutSeconds for a sibling BUILD.LOCK holder and gave up without
          building at all (S1349) - same "did not look" contract, different cause.
      3 - every stage passed except stage 4, which found a reference divergence the
          changed set cannot have caused (S2604). Returned only when -ChangedFiles was
          supplied, i.e. on a scoped per-ticket closure: stage 4 re-renders the whole
          SETTINGS_REFERENCE family, so its finding belongs to whichever ticket last
          moved a renderer input, and charging this closure for it blocked the close on
          another session's in-flight row. An unscoped run never returns 3 - it keeps
          the strict project-wide judgement and fails with 1.
#>
param(
    [switch] $Gate,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [switch] $SkipManifestTest,  # escape hatch for environments without a JVM/gradle
    [string[]] $ChangedFiles,    # delta path: skip the gradle stage when nothing feeds the manifest
    [switch] $SkipHowToStage,    # the caller runs assert-howto-settings-paths.ps1 as its own gate
    [int] $TimeoutSeconds = 600
)

$ErrorActionPreference = 'Stop'
. (Join-Path $RepoRoot "scripts/utils/agent-lock.ps1")
. (Join-Path $RepoRoot "scripts/builders/gradle-run-verdict.ps1")
. (Join-Path $RepoRoot "scripts/utils/process-timeout.ps1")
# S2604: which changed path feeds which stage is declared once, and post-change.ps1's trigger
# reads the same file - so the facade cannot run this gate on a set no stage can see, and this
# gate cannot judge a stage the facade did not intend to fire.
. (Join-Path $RepoRoot "scripts/quality/lib/settings-doc-inputs.ps1")

function Fail([string] $stage, [string] $detail) {
    Write-Host "settings-doc-sync: FAIL at stage '$stage'" -ForegroundColor Red
    if ($detail) { Write-Host "  $detail" }
    exit 1
}

function CannotVerify([string] $stage, [string] $detail) {
    Write-Host "settings-doc-sync: CANNOT-VERIFY at stage '$stage'" -ForegroundColor Yellow
    if ($detail) { Write-Host "  $detail" }
    exit 2
}

# Stage 1 - catalog completeness ------------------------------------------------
& (Join-Path $PSScriptRoot 'assert-settings-catalog-complete.ps1') -RepoRoot $RepoRoot
if ($LASTEXITCODE -ne 0) { Fail 'catalog-complete' 'a settings layout with rows is missing from SettingsSearchLayoutCatalog' }

# Stage 2 delta path (S1338 step 04.7). The manifest is produced by scanning the settings
# LAYOUTS through LayoutSettingsSearchSource + SettingsSearchTabMapping and resolving titles
# from values*/strings*.xml, so only those inputs can change it. A closure that touched none
# of them cannot have moved the manifest, and paying ~28 s of gradle to re-prove that is the
# 35 s-per-run cost this gate was cited for. Everything else in the file is still judged: the
# four cheap stages always run.
# S2604 moved the patterns themselves into scripts/quality/lib/settings-doc-inputs.ps1, where
# post-change.ps1's trigger reads them too; the behaviour of this stage is unchanged.
$scoped = @(Expand-SettingsDocPaths -ChangedFiles $ChangedFiles)
$manifestAffected = Test-SettingsManifestInput -ChangedFiles $ChangedFiles -RepoRoot $RepoRoot
if ($scoped.Count -gt 0 -and -not $manifestAffected) {
    Write-Host "settings-doc-sync: manifest stage skipped - none of the $($scoped.Count) changed file(s) feeds the settings scan (a layout carrying a settings row, strings, ui/settings, settings-search DI)." -ForegroundColor Yellow
}

# S2604 stage 4 delta path. Stage 4 re-renders the whole SETTINGS_REFERENCE family and
# byte-compares it, so a divergence it finds belongs to whichever ticket last moved a renderer
# input - not necessarily to this closure. When the changed set feeds none of those inputs, the
# divergence is recorded and reported, and the script ends in exit 3 for the caller to downgrade;
# it is never silently dropped. The predicate is the RENDERER's input set, deliberately wider
# than the manifest scan's: an annotations-only change skips stage 2 and still owns the render.
$referenceAffected = Test-SettingsReferenceInput -ChangedFiles $ChangedFiles -RepoRoot $RepoRoot
if ($scoped.Count -gt 0 -and -not $referenceAffected) {
    Write-Host "settings-doc-sync: reference stage runs advisory - none of the $($scoped.Count) changed file(s) feeds the SETTINGS_REFERENCE render (manifest, annotations, a per-flavor availability module, the icon map, the renderer)." -ForegroundColor Yellow
}
# Populated by stage 4 instead of failing when $referenceAffected is false. Reported after
# stage 5, so a real HOW_TO recipe failure still exits 1 rather than being masked by an
# early exit 3.
$deferredReferenceDrift = [System.Collections.Generic.List[string]]::new()

# Stage 2 - manifest freshness (verify-mode test) -------------------------------
if (-not $SkipManifestTest -and $manifestAffected) {
    $reportFileFiltered = Join-Path $RepoRoot 'app_v2/build/test-results/testStandardDebugUnitTest-filtered/TEST-com.sza.fastmediasorter.ui.settings.search.SettingsManifestExportTest.xml'
    $reportFileStandard = Join-Path $RepoRoot 'app_v2/build/test-results/testStandardDebugUnitTest/TEST-com.sza.fastmediasorter.ui.settings.search.SettingsManifestExportTest.xml'
    $reportFile = if (Test-Path $reportFileFiltered) { $reportFileFiltered } else { $reportFileStandard }
    $runStart = Get-Date
    # S1349: post-change.ps1 starts detekt-gate as a backgrounded Start-ThreadJob that holds
    # BUILD.LOCK for the full run; this stage lands only ~10s later in the pipeline, well before
    # that job finishes, so a non-waiting acquire here loses the race against our own sibling job
    # (not cross-session contention) and reports a false lock-contention FAIL. -Wait queues instead;
    # a genuine timeout still surfaces as exit 2 CANNOT-VERIFY (documented above), never exit 1.
    # S2538: 900 s, not the harness default of 3600. assert-detekt.ps1 queues for this same
    # Build.Phone lock under 900 s, and two steps of one closure waiting out ceilings four times
    # apart is a difference nothing decided - the looser one holds an ordinary closure for an hour
    # before reporting, correctly, that it never looked.
    Enter-BuildLockOrExit -Reason "assert-settings-doc-sync.ps1 (SettingsManifestExportTest)" -Wait -Domain Build.Phone -WaitTimeoutSeconds 900
    Push-Location $RepoRoot
    try {
        # S1786: execute with timeout ceiling
        $gradleScript = if ($IsWindows -ne $false) { 'gradlew.bat' } else { 'gradlew' }
        $gradlew = Join-Path $RepoRoot $gradleScript
        $run = Invoke-ProcessWithTimeout -FilePath $gradlew -WorkingDirectory $RepoRoot `
            -ArgumentList @(':app_v2:testStandardDebugUnitTest', '--tests', '*SettingsManifestExportTest') `
            -TimeoutSeconds $TimeoutSeconds
        $manifestOutput = $run.Output
        $manifestExit = if ($run.TimedOut) { 2 } else { $run.ExitCode }
        $timedOut = $run.TimedOut
    } finally { Pop-Location; Exit-AgentLock -Name 'Build' -Domains @('Build.Phone') }
    if ($timedOut) {
        CannotVerify 'manifest-fresh' "the SettingsManifestExportTest gradle run exceeded ${TimeoutSeconds}s and was killed (exit 2)."
    }
    if ($manifestExit -ne 0) {
        # A non-zero gradle exit means the test asserted drift OR the run never got that far - a
        # compile failure, or a dead test worker. Each of those is "did not look", and saying
        # "the manifest is stale" instead sends the reader hunting a difference nobody measured.
        if (Test-GradleWorkerDeath -Lines $manifestOutput) {
            CannotVerify 'manifest-fresh' "the Gradle test worker died, so SettingsManifestExportTest never judged anything (see PLAN/S1463). No claim is made about settings-manifest.json freshness."
        }
        $fresh = (Test-Path $reportFile) -and ((Get-Item $reportFile).LastWriteTime -ge $runStart)
        if (-not $fresh) {
            CannotVerify 'manifest-fresh' "the SettingsManifestExportTest never ran - app_v2 failed to build. Fix the build, then re-run; no claim is made about settings-manifest.json freshness."
        }
        # S1464: a fresh report is not a finished test. A worker that dies after the test body leaves
        # exactly this - correct timestamp, skipped="1", failures="0" - and the old check read it as
        # proof of execution, then blamed the manifest.
        $outcome = Get-JUnitSuiteOutcome -ReportPath $reportFile
        if (-not $outcome.Executed) {
            CannotVerify 'manifest-fresh' "the SettingsManifestExportTest report exists but proves nothing - $($outcome.Reason). No claim is made about settings-manifest.json freshness."
        }
        if (-not $outcome.Failed) {
            CannotVerify 'manifest-fresh' "the SettingsManifestExportTest ran and recorded no failure, yet the task went red - the failure is elsewhere in the run, not in the manifest."
        }
        Fail 'manifest-fresh' 'committed settings-manifest.json differs from the live scan - regenerate with -Dsettings.manifest.generate=true'
    }
} elseif ($SkipManifestTest) {
    Write-Host "settings-doc-sync: manifest test skipped (-SkipManifestTest)" -ForegroundColor Yellow
}

# Stage 3 - annotation coverage/parity ------------------------------------------
& (Join-Path $RepoRoot 'scripts/docs/check-settings-annotations.ps1')
if ($LASTEXITCODE -ne 0) { Fail 'annotations' 'a manifest key is unannotated, orphaned, or has an empty en/ru/uk value' }

# Stage 4 - reference freshness (re-render + diff) ------------------------------
$tmp = Join-Path $RepoRoot 'temp/_settings_ref_gate'
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
try {
    & (Join-Path $RepoRoot 'scripts/docs/render-settings-reference.ps1') -RepoRoot $RepoRoot -OutDir $tmp | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail 'reference-render' 'renderer returned non-zero' }
    $published = @('SETTINGS_REFERENCE.md', 'SETTINGS_REFERENCE_RU.md', 'SETTINGS_REFERENCE_UK.md')
    foreach ($f in $published) {
        $committed = Join-Path $RepoRoot "docs/$f"
        $fresh = Join-Path $tmp $f
        # A missing committed file is not drift someone else can own - the render has never been
        # run for it, or it was deleted - so this branch stays fatal on every path.
        if (-not (Test-Path $committed)) { Fail 'reference-fresh' "committed docs/$f is missing - run render-settings-reference.ps1" }
        $a = [System.IO.File]::ReadAllText($committed)
        $b = [System.IO.File]::ReadAllText($fresh)
        if ($a -ne $b) {
            if ($referenceAffected) { Fail 'reference-fresh' "docs/$f is stale - re-run scripts/docs/render-settings-reference.ps1" }
            $deferredReferenceDrift.Add("docs/$f")
        }
    }
} finally {
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
}

# Stage 5 - HOW_TO settings-path freshness (S0558) ------------------------------
# post-change.ps1 runs the same script as its own `howto-settings-paths-gate`, so on that path this
# stage was the second execution of one check in one closure. -SkipHowToStage lets the caller keep
# the gate it already reports and drop the duplicate; every other caller still gets stage 5.
if (-not $SkipHowToStage) {
    & (Join-Path $PSScriptRoot 'assert-howto-settings-paths.ps1') -RepoRoot $RepoRoot
    if ($LASTEXITCODE -ne 0) { Fail 'howto-paths' 'a HOW_TO "Settings -> .." recipe drifted from the manifest - see the lines above' }
}

# Name what was actually judged: a verdict that claims "manifest fresh" after skipping the
# manifest stage is the same false certification the closure facade was fixed for in phase 02.
$manifestVerdict = if (-not $SkipManifestTest -and $manifestAffected) { 'manifest fresh' } else { 'manifest stage NOT run' }
$howToVerdict = if ($SkipHowToStage) { 'HOW_TO recipes judged by the caller' } else { 'HOW_TO recipes in sync' }

# S2604: every other stage passed and stage 4 found a divergence this changed set cannot have
# produced. Name the files - an advisory whose text does not say WHAT diverged sends the reader
# to re-run the render just to find out, which is the work the downgrade exists to avoid.
if ($deferredReferenceDrift.Count -gt 0) {
    Write-Host "settings-doc-sync: ADVISORY at stage 'reference-fresh' - $($deferredReferenceDrift.Count) rendered file(s) differ from the committed copy:" -ForegroundColor Yellow
    foreach ($drifted in $deferredReferenceDrift) { Write-Host "  $drifted" }
    Write-Host "  No changed file feeds the reference render, so this divergence belongs to whichever ticket last moved a renderer input - regenerating it here would commit that ticket's user-visible text under this change." -ForegroundColor Yellow
    Write-Host "  Project-wide verdict: re-run this script with no -ChangedFiles." -ForegroundColor Yellow
    Write-Host "settings-doc-sync: OK WITH ADVISORY - catalog complete, $manifestVerdict, annotations covered, reference divergence NOT attributed to this change, $howToVerdict." -ForegroundColor Yellow
    exit 3
}

$referenceVerdict = if ($referenceAffected) { 'reference up to date' } else { 'reference up to date (stage ran advisory)' }
Write-Host "settings-doc-sync: OK - catalog complete, $manifestVerdict, annotations covered, $referenceVerdict, $howToVerdict." -ForegroundColor Green
exit 0
