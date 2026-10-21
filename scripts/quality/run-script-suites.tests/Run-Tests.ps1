#requires -Version 7.0
<#
.SYNOPSIS
    S2122 - regression suite for the script-suite runner itself.

.DESCRIPTION
    Subject: scripts/quality/run-script-suites.ps1

    S3515 adds the pass-cache cases (C1-C5); S4413 the worker-pool cases (P1-P4) and the
    campaign-cache cases (K1-K6), with the
    campaign record, its journal and the campaign store all sandboxed through
    FMS_RELEASE_CAMPAIGN_ROOT and FMS_SCRIPT_SUITE_CAMPAIGN_CACHE, so no case can touch temp/metrics.

    This ticket exists because 37 suites sat in the tree with no run site, and shipping the run site
    without a suite of its own would leave exactly one script in the repository whose correctness
    nothing checks - the same defect in a new place. So the runner is asserted on the four things it
    can silently get wrong: discovery by convention, neighbour selection, the exit-2/exit-1
    distinction, and the aggregated verdict.

    HERMETIC. Every case points the runner at a throw-away fixture tree under temp/S2122/, never at
    scripts/, so this suite can never trigger a recursive sweep of the repository's real suites. The
    fixture is removed in a finally block whatever happens.

    The re-entry guard is cleared on purpose (see the assignment below): when this suite is executed
    BY the runner during a full sweep, the runner has set FMS_SCRIPT_SUITE_RUNNER=1 in the
    environment the child inherits, and every runner invocation below would skip and report 0. The
    guard protects against unbounded nesting, not against a test that deliberately drives the runner.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/run-script-suites.tests/Run-Tests.ps1

.NOTES
    Exit codes:
      0  every case passed.
      1  at least one case failed.
      2  could not verify - the runner script is missing.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$runner = Join-Path $repoRoot 'scripts/quality/run-script-suites.ps1'
if (-not (Test-Path -LiteralPath $runner)) {
    Write-Error "run-script-suites tests: COULD NOT VERIFY - the runner is missing at $runner." -ErrorAction Continue
    exit 2
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
$inheritedGuard = $env:FMS_SCRIPT_SUITE_RUNNER
$env:FMS_SCRIPT_SUITE_RUNNER = $null
# Every case records into a fixture store, never into the closure's real pass cache.
$inheritedCache = $env:FMS_SCRIPT_SUITE_CACHE
# A release campaign open on this machine would otherwise put every full-sweep case below into
# campaign mode and journal fixture suites into the real campaign.
$inheritedCampaignRoot = $env:FMS_RELEASE_CAMPAIGN_ROOT
$inheritedCampaignCache = $env:FMS_SCRIPT_SUITE_CAMPAIGN_CACHE

$script:pass = 0
$script:fail = 0

function Assert-That([string]$Name, [bool]$Ok, [string]$Detail) {
    if ($Ok) {
        Write-Host "  PASS  $Name" -ForegroundColor Green
        $script:pass++
    }
    else {
        Write-Host "  FAIL  $Name -> $Detail" -ForegroundColor Red
        $script:fail++
    }
}

$fixtureRoot = Join-Path $repoRoot ('temp/S2122/fixture-' + [guid]::NewGuid().ToString('N'))
$fixtureRel = 'temp/S2122/' + (Split-Path -Leaf $fixtureRoot)

function New-FixtureSuite([string]$Name, [int]$ExitCode) {
    $subject = Join-Path $fixtureRoot "$Name.ps1"
    Set-Content -LiteralPath $subject -Value "# fixture subject $Name" -Encoding utf8NoBOM
    $suiteDir = Join-Path $fixtureRoot "$Name.tests"
    New-Item -ItemType Directory -Path $suiteDir -Force | Out-Null
    $body = @(
        "Write-Host 'fixture suite $Name reporting'",
        "exit $ExitCode"
    )
    Set-Content -LiteralPath (Join-Path $suiteDir 'Run-Tests.ps1') -Value $body -Encoding utf8NoBOM
}

function Invoke-Runner([string[]]$RunnerArgs) {
    $out = @(& $pwshExe -NoProfile -File $runner @RunnerArgs *>&1 | ForEach-Object { [string]$_ })
    return [pscustomobject]@{ Code = [int]$LASTEXITCODE; Text = ($out -join "`n") }
}

try {
    New-Item -ItemType Directory -Path $fixtureRoot -Force | Out-Null
    $env:FMS_SCRIPT_SUITE_CACHE = Join-Path $fixtureRoot 'pass-cache.json'
    $campaignRoot = Join-Path $fixtureRoot 'campaign'
    New-Item -ItemType Directory -Path $campaignRoot -Force | Out-Null
    $env:FMS_RELEASE_CAMPAIGN_ROOT = $campaignRoot
    $env:FMS_SCRIPT_SUITE_CAMPAIGN_CACHE = Join-Path $fixtureRoot 'campaign-cache.json'
    New-FixtureSuite -Name 'alpha' -ExitCode 0
    New-FixtureSuite -Name 'beta' -ExitCode 1
    New-FixtureSuite -Name 'gamma' -ExitCode 2
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'delta.ps1') -Value '# fixture subject with no suite' -Encoding utf8NoBOM

    # A suite whose subject the path cannot reach, declaring it in its own header instead.
    New-Item -ItemType Directory -Path (Join-Path $fixtureRoot 'oddly-named.tests') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $fixtureRoot 'lib') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'lib/produces-notices.ps1') -Value '# fixture subject reached only by declaration' -Encoding utf8NoBOM
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'oddly-named.tests/Run-Tests.ps1') -Encoding utf8NoBOM -Value @(
        "# Subject: $fixtureRel/lib/produces-notices.ps1",
        "Write-Host 'fixture suite oddly-named reporting'",
        'exit 0'
    )

    Write-Host 'Discovery' -ForegroundColor Yellow

    $listed = Invoke-Runner @('-Root', $fixtureRoot, '-ListOnly')
    Assert-That 'D1 a suite placed by convention is discovered with no edit to the runner' `
        ($listed.Code -eq 0 -and $listed.Text -match '4 suite\(s\) selected of 4 discovered') `
        "exit $($listed.Code); text: $($listed.Text)"

    Assert-That 'D2 discovery never reaches the repository suites when -Root is a fixture' `
        ($listed.Text -notmatch 'scripts/post-change\.tests') `
        "fixture listing named a real repository suite: $($listed.Text)"

    Write-Host 'Neighbour selection' -ForegroundColor Yellow

    $alpha = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/alpha.ps1")
    Assert-That 'S1 a changed script selects its sibling suite and passes' `
        ($alpha.Code -eq 0 -and $alpha.Text -match 'alpha\.tests' -and $alpha.Text -notmatch 'beta\.tests') `
        "exit $($alpha.Code); text: $($alpha.Text)"

    $unrelated = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', 'app_v2/src/main/AndroidManifest.xml')
    Assert-That 'S2 an unrelated changed file selects nothing and exits 0' `
        ($unrelated.Code -eq 0 -and $unrelated.Text -match 'no suite has a subject in the changed set') `
        "exit $($unrelated.Code); text: $($unrelated.Text)"

    $selfEdit = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/alpha.tests/Run-Tests.ps1")
    Assert-That 'S3 editing a suite runs that suite' `
        ($selfEdit.Code -eq 0 -and $selfEdit.Text -match 'alpha\.tests') `
        "exit $($selfEdit.Code); text: $($selfEdit.Text)"

    $declared = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/lib/produces-notices.ps1")
    Assert-That 'S4 a suite whose subject only its own header names is still selected' `
        ($declared.Code -eq 0 -and $declared.Text -match 'oddly-named\.tests') `
        "exit $($declared.Code); text: $($declared.Text)"

    Write-Host 'Verdict classification' -ForegroundColor Yellow

    $gammaGate = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/gamma.ps1", '-Gate')
    Assert-That 'V1 a suite exiting 2 is "could not verify" and is fatal under -Gate' `
        ($gammaGate.Code -eq 2 -and $gammaGate.Text -match 'CANNOT-VERIFY') `
        "exit $($gammaGate.Code); text: $($gammaGate.Text)"

    $gammaAdvisory = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/gamma.ps1")
    Assert-That 'V2 the same suite is advisory without -Gate' `
        ($gammaAdvisory.Code -eq 0 -and $gammaAdvisory.Text -match 'advisory') `
        "exit $($gammaAdvisory.Code); text: $($gammaAdvisory.Text)"

    $betaRun = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/beta.ps1")
    Assert-That 'V3 a suite exiting 1 fails the run and is named' `
        ($betaRun.Code -eq 1 -and $betaRun.Text -match 'beta\.tests') `
        "exit $($betaRun.Code); text: $($betaRun.Text)"

    $sweep = Invoke-Runner @('-Root', $fixtureRoot, '-Gate')
    Assert-That 'V4 a failure outranks a cannot-verify in the aggregated verdict' `
        ($sweep.Code -eq 1) `
        "expected 1, got $($sweep.Code); text: $($sweep.Text)"

    Assert-That 'V5 the output of a failing suite is echoed so the caller need not re-run it' `
        ($betaRun.Text -match 'fixture suite beta reporting') `
        "output of the failing suite was not echoed: $($betaRun.Text)"

    Write-Host 'Pass cache (S3515)' -ForegroundColor Yellow

    Remove-Item -LiteralPath $env:FMS_SCRIPT_SUITE_CACHE -Force -ErrorAction SilentlyContinue
    $first = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/alpha.ps1")
    $second = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/alpha.ps1")
    Assert-That 'C1 a repeat run over unchanged files reports a cached PASS without executing' `
        ($first.Text -match 'fixture suite alpha reporting' -and $second.Code -eq 0 -and
        $second.Text -match 'cached PASS' -and $second.Text -notmatch 'fixture suite alpha reporting') `
        "first: $($first.Text) | second: exit $($second.Code); $($second.Text)"

    Add-Content -LiteralPath (Join-Path $fixtureRoot 'alpha.ps1') -Value '# edited' -Encoding utf8NoBOM
    $edited = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/alpha.ps1")
    Assert-That 'C2 a changed subject executes the suite again' `
        ($edited.Code -eq 0 -and $edited.Text -match 'fixture suite alpha reporting' -and $edited.Text -notmatch 'cached PASS') `
        "exit $($edited.Code); text: $($edited.Text)"

    $betaAgain = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/beta.ps1")
    Assert-That 'C3 a failing suite is never recorded and fails again' `
        ($betaAgain.Code -eq 1 -and $betaAgain.Text -notmatch 'cached PASS') `
        "exit $($betaAgain.Code); text: $($betaAgain.Text)"

    $noCache = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/alpha.ps1", '-NoCache')
    $releaseShape = Invoke-Runner @('-Root', $fixtureRoot)
    Assert-That 'C4 -NoCache and the release-scope sweep both execute the suite' `
        ($noCache.Text -match 'fixture suite alpha reporting' -and $noCache.Text -notmatch 'cached PASS' -and
        $releaseShape.Text -notmatch 'cached PASS') `
        "no-cache: $($noCache.Text) | release: $($releaseShape.Text)"

    # Created after D1 so the discovery count there stays four.
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'lib/epsilon-helper.ps1') -Value '# fixture library' -Encoding utf8NoBOM
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'epsilon.ps1') -Encoding utf8NoBOM `
        -Value ". (Join-Path `$PSScriptRoot 'lib/epsilon-helper.ps1')"
    New-Item -ItemType Directory -Path (Join-Path $fixtureRoot 'epsilon.tests') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'epsilon.tests/Run-Tests.ps1') -Encoding utf8NoBOM `
        -Value @("Write-Host 'fixture suite epsilon reporting'", 'exit 0')
    $null = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/epsilon.ps1")
    Add-Content -LiteralPath (Join-Path $fixtureRoot 'lib/epsilon-helper.ps1') -Value '# edited' -Encoding utf8NoBOM
    $libEdited = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/epsilon.ps1")
    Assert-That 'C5 an edited library the subject dot-sources executes the suite again' `
        ($libEdited.Code -eq 0 -and $libEdited.Text -match 'fixture suite epsilon reporting' -and $libEdited.Text -notmatch 'cached PASS') `
        "exit $($libEdited.Code); text: $($libEdited.Text)"

    Write-Host 'Campaign cache (S4413)' -ForegroundColor Yellow

    # Five fixture suites now: alpha, epsilon and oddly-named pass, beta fails, gamma cannot verify.
    $campaignStore = $env:FMS_SCRIPT_SUITE_CAMPAIGN_CACHE
    $resultJson = Join-Path $fixtureRoot 'campaign-results.json'
    $campaignRecord = Join-Path $campaignRoot 'release-campaign.json'
    function Set-FixtureCampaign([string]$Id, [string]$State) {
        Set-Content -LiteralPath $campaignRecord -Encoding utf8NoBOM -Value (
            [ordered]@{ schema = 1; id = $Id; package = 0; state = $State; startedAt = (Get-Date).ToString('o') } | ConvertTo-Json)
    }
    function Get-SourceOf([string]$SuiteName) {
        $rows = @(Get-Content -LiteralPath $resultJson -Raw | ConvertFrom-Json)
        return @($rows | Where-Object { $_.Suite -like "*/$SuiteName.tests/*" } | ForEach-Object { $_.Source }) -join ','
    }
    function Get-Sources {
        return @(@(Get-Content -LiteralPath $resultJson -Raw | ConvertFrom-Json) | ForEach-Object { $_.Source })
    }

    Set-FixtureCampaign -Id 'k-one' -State 'open'
    $k1 = Invoke-Runner @('-Root', $fixtureRoot, '-Json', $resultJson)
    $k1Sources = Get-Sources
    Assert-That 'K1 the first pass of an open campaign executes every suite (Source=run)' `
        ($k1.Text -match 'campaign cache: 0 reused, 5 executed' -and $k1Sources.Count -eq 5 -and
        @($k1Sources | Where-Object { $_ -ne 'run' }).Count -eq 0 -and (Test-Path -LiteralPath $campaignStore)) `
        "exit $($k1.Code); sources: $($k1Sources -join ','); text: $($k1.Text)"

    $k2 = Invoke-Runner @('-Root', $fixtureRoot, '-Json', $resultJson)
    Assert-That 'K2 a second pass in the same campaign reuses every passing suite and executes the failing ones again' `
        ($k2.Text -match 'campaign cache: 3 reused, 2 executed' -and (Get-SourceOf 'alpha') -eq 'campaign-cache' -and
        (Get-SourceOf 'beta') -eq 'run' -and (Get-SourceOf 'gamma') -eq 'run' -and $k2.Code -eq 1 -and
        $k2.Text -notmatch 'fixture suite alpha reporting') `
        "exit $($k2.Code); text: $($k2.Text)"

    Add-Content -LiteralPath (Join-Path $fixtureRoot 'alpha.ps1') -Value '# edited in campaign' -Encoding utf8NoBOM
    $k3 = Invoke-Runner @('-Root', $fixtureRoot, '-Json', $resultJson)
    Assert-That 'K3 editing one suite''s subject re-executes exactly that suite' `
        ($k3.Text -match 'campaign cache: 2 reused, 3 executed' -and (Get-SourceOf 'alpha') -eq 'run' -and
        (Get-SourceOf 'epsilon') -eq 'campaign-cache' -and (Get-SourceOf 'oddly-named') -eq 'campaign-cache') `
        "text: $($k3.Text)"

    $journal = Join-Path $campaignRoot 'release-campaign-phases.jsonl'
    $suiteRows = @(if (Test-Path -LiteralPath $journal) {
            Get-Content -LiteralPath $journal | ForEach-Object { $_ | ConvertFrom-Json } | Where-Object { $_.name -like 'suite:*' }
        })
    Assert-That 'K4 every suite is journalled into the campaign, cached ones with elapsedMs 0' `
        ($suiteRows.Count -eq 15 -and @($suiteRows | Where-Object { $_.detail -eq 'campaign-cache' -and $_.elapsedMs -eq 0 }).Count -eq 5 -and
        @($suiteRows | Where-Object { $_.campaignId -ne 'k-one' }).Count -eq 0) `
        "suite rows: $($suiteRows.Count); journal: $(if (Test-Path -LiteralPath $journal) { Get-Content -LiteralPath $journal -Raw })"

    $k5 = Invoke-Runner @('-Root', $fixtureRoot, '-CampaignId', 'k-two', '-Json', $resultJson)
    $storeCampaigns = @((Get-Content -LiteralPath $campaignStore -Raw | ConvertFrom-Json -AsHashtable).Values | ForEach-Object { $_.campaign } | Select-Object -Unique)
    Assert-That 'K5 a new campaign id executes everything again and drops the old campaign''s entries' `
        ($k5.Text -match 'campaign cache: 0 reused, 5 executed' -and ($storeCampaigns -join ',') -eq 'k-two') `
        "store campaigns: $($storeCampaigns -join ','); text: $($k5.Text)"

    Remove-Item -LiteralPath $campaignStore -Force
    $ticketInCampaign = Invoke-Runner @('-Root', $fixtureRoot, '-ChangedFiles', "$fixtureRel/alpha.ps1")
    Set-FixtureCampaign -Id 'k-one' -State 'closed'
    $k6 = Invoke-Runner @('-Root', $fixtureRoot)
    Assert-That 'K6 a changed set or no open campaign neither reads nor writes the campaign store' `
        ($ticketInCampaign.Code -eq 0 -and $k6.Text -notmatch 'campaign cache:' -and $k6.Text -notmatch 'campaign-cached' -and
        -not (Test-Path -LiteralPath $campaignStore)) `
        "store present: $(Test-Path -LiteralPath $campaignStore); ticket: $($ticketInCampaign.Text) | sweep: $($k6.Text)"

    Write-Host 'Worker pool (S4413)' -ForegroundColor Yellow

    # Created after the campaign cases so their counts stay five. Two serial suites stamp their own
    # start and end into one shared file while holding it long enough for an overlap to show; a
    # third suite reports the re-entry guard as its own environment sees it.
    $windowLog = Join-Path $fixtureRoot 'serial-windows.log'
    # Assembled, not written whole: a grep for the declaration over the real suites must count the
    # suites that declare it, not this fixture's source.
    $serialDeclaration = '# Parallel:' + ' serial'
    foreach ($serialName in @('serial-one', 'serial-two')) {
        Set-Content -LiteralPath (Join-Path $fixtureRoot "$serialName.ps1") -Value "# fixture subject $serialName" -Encoding utf8NoBOM
        New-Item -ItemType Directory -Path (Join-Path $fixtureRoot "$serialName.tests") -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $fixtureRoot "$serialName.tests/Run-Tests.ps1") -Encoding utf8NoBOM -Value @(
            $serialDeclaration,
            '$start = [DateTime]::UtcNow.Ticks',
            'Start-Sleep -Milliseconds 1500',
            '$end = [DateTime]::UtcNow.Ticks',
            "Add-Content -LiteralPath '$windowLog' -Value ('$serialName {0} {1}' -f `$start, `$end)",
            "Write-Host 'fixture suite $serialName reporting'",
            'exit 0'
        )
    }
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'guard-echo.ps1') -Value '# fixture subject guard-echo' -Encoding utf8NoBOM
    New-Item -ItemType Directory -Path (Join-Path $fixtureRoot 'guard-echo.tests') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $fixtureRoot 'guard-echo.tests/Run-Tests.ps1') -Encoding utf8NoBOM -Value @(
        'Write-Host "fixture suite guard-echo sees runner guard [$env:FMS_SCRIPT_SUITE_RUNNER]"',
        'exit 0'
    )

    function Get-VerdictSet([string]$Path) {
        return (@(@(Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json) | ForEach-Object { "$($_.Suite)=$($_.Verdict)" }) -join ';')
    }
    $w1Json = Join-Path $fixtureRoot 'w1.json'
    $w3Json = Join-Path $fixtureRoot 'w3.json'
    $w1 = Invoke-Runner @('-Root', $fixtureRoot, '-Workers', '1', '-Json', $w1Json)
    Remove-Item -LiteralPath $windowLog -Force -ErrorAction SilentlyContinue
    $w3 = Invoke-Runner @('-Root', $fixtureRoot, '-Workers', '3', '-Json', $w3Json)
    $w1Set = Get-VerdictSet $w1Json
    $w3Set = Get-VerdictSet $w3Json
    Assert-That 'P1 -Workers 3 yields the same per-suite verdict set as -Workers 1, in the same order' `
        ($w1Set -eq $w3Set -and $w1.Code -eq $w3.Code -and $w3.Text -match '8 suite\(s\)' -and $w3.Text -match 'with 3 worker\(s\)') `
        "w1: exit $($w1.Code) $w1Set | w3: exit $($w3.Code) $w3Set"

    $windows = @(Get-Content -LiteralPath $windowLog -ErrorAction SilentlyContinue | ForEach-Object {
            $parts = $_ -split ' '
            [pscustomobject]@{ Name = $parts[0]; Start = [long]$parts[1]; End = [long]$parts[2] }
        })
    $overlap = ($windows.Count -eq 2) -and ($windows[0].Start -lt $windows[1].End) -and ($windows[1].Start -lt $windows[0].End)
    Assert-That 'P2 two suites declaring the serial header line never run at the same time under the pool' `
        ($windows.Count -eq 2 -and -not $overlap) `
        "windows: $(@($windows | ForEach-Object { "$($_.Name) $($_.Start)-$($_.End)" }) -join '; ')"

    Assert-That 'P3 a failing suite under the pool still fails the run and its output is echoed' `
        ($w3.Code -eq 1 -and $w3.Text -match 'FAIL\s+[\d.]+s\s+\S*beta\.tests' -and $w3.Text -match 'fixture suite beta reporting') `
        "exit $($w3.Code); text: $($w3.Text)"

    # In-process on purpose: only a run inside this process can show what it leaves in the parent's
    # environment. The script's own `exit` ends the script, not this host.
    $null = & $runner -Root $fixtureRoot -Workers 3 *>&1
    $guardAfterPool = $env:FMS_SCRIPT_SUITE_RUNNER
    $null = & $runner -Root $fixtureRoot -Workers 1 *>&1
    $guardAfterInline = $env:FMS_SCRIPT_SUITE_RUNNER
    Assert-That 'P4 the child sees the re-entry guard and the parent has none after either run' `
        ($w3.Text -match 'guard-echo sees runner guard \[1\]' -and $w1.Text -match 'guard-echo sees runner guard \[1\]' -and
        [string]::IsNullOrEmpty($guardAfterPool) -and [string]::IsNullOrEmpty($guardAfterInline)) `
        "after pool: '$guardAfterPool'; after inline: '$guardAfterInline'; w3 text: $($w3.Text)"
}
finally {
    $env:FMS_SCRIPT_SUITE_RUNNER = $inheritedGuard
    $env:FMS_SCRIPT_SUITE_CACHE = $inheritedCache
    $env:FMS_RELEASE_CAMPAIGN_ROOT = $inheritedCampaignRoot
    $env:FMS_SCRIPT_SUITE_CAMPAIGN_CACHE = $inheritedCampaignCache
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ''
Write-Host "run-script-suites tests: $(if ($script:fail -gt 0) { 'FAIL' } else { 'PASS' }) (run=$($script:pass + $script:fail) skipped=0)"

if ($script:fail -gt 0) {
    Write-Error "run-script-suites tests: $($script:fail) case(s) failed." -ErrorAction Continue
    exit 1
}
exit 0
