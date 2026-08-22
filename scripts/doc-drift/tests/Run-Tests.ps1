# Run-Tests.ps1 - regression coverage for documentation drift pins (S1381).
#
# Exit codes:
#   0 = all scenarios passed
#   1 = one or more scenarios failed

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$driftDir = Join-Path $repoRoot 'scripts\doc-drift'
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('fms-doc-drift-' + [guid]::NewGuid().ToString('N'))

. (Join-Path $driftDir 'GradleParser.ps1')
. (Join-Path $driftDir 'DocParser.ps1')
. (Join-Path $driftDir 'Comparator.ps1')

function Assert-Scenario {
    param(
        [Parameter(Mandatory)][string] $Name,
        [Parameter(Mandatory)][scriptblock] $Predicate
    )
    if (-not (& $Predicate)) {
        throw "Scenario failed: $Name"
    }
    Write-Output "PASS | $Name"
}

try {
    New-Item -ItemType Directory -Path (Join-Path $tempRoot 'docs') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $tempRoot 'dev') -Force | Out-Null
    Copy-Item (Join-Path $repoRoot 'docs\TECH_STACK.md') (Join-Path $tempRoot 'docs\TECH_STACK.md')
    Copy-Item (Join-Path $repoRoot 'docs\DEV_OPS.md') (Join-Path $tempRoot 'docs\DEV_OPS.md')
    Copy-Item (Join-Path $repoRoot 'dev\TECH_REQUIREMENTS.md') (Join-Path $tempRoot 'dev\TECH_REQUIREMENTS.md')

    $manifest = Import-PowerShellDataFile (Join-Path $driftDir 'pins.psd1')
    $pins = @($manifest.Pins | Where-Object { $_.name -in @('compile-sdk', 'target-sdk', 'room-schema-version') })
    $testManifest = @{ Pins = $pins }
    $roomSchemaPin = @($pins | Where-Object { $_.name -eq 'room-schema-version' })[0]
    $gradlePins = Get-GradlePins -RepoRoot $repoRoot

    function Get-ScenarioRecords {
        $mentions = Get-DocMentions -Manifest $testManifest -RepoRoot $tempRoot
        return @(Compare-PinsToDocs -GradlePins $gradlePins -DocMentions $mentions)
    }

    # S1852: a scenario mutates a copy of the tree to provoke a known FAIL. When the value it edits was
    # written as a literal, the edit silently became a no-op the moment the project moved past it - the
    # fixtures said 50 while the schema reached 53, the replace matched nothing, no FAIL record appeared,
    # and the scenario failed while reporting nothing about the code under test. Worse, the suite stops at
    # the first failed scenario, so the three after it had not run for as long as the drift existed.
    #
    # Every mutation now derives its value from the same gradle pins the audit itself reads, and asserts
    # that the text actually changed. A fixture that stops matching now says so instead of going quiet.
    function Set-MutatedContent {
        param(
            [Parameter(Mandatory)][string] $Path,
            [Parameter(Mandatory)][string] $Text,
            [Parameter(Mandatory)][string] $Pattern,
            [Parameter(Mandatory)][string] $Replacement,
            [Parameter(Mandatory)][string] $What
        )
        $mutated = $Text -replace $Pattern, $Replacement
        if ($mutated -eq $Text) {
            throw "fixture no longer matches the tree: $What (pattern '$Pattern' changed nothing)"
        }
        Set-Content -LiteralPath $Path -Value $mutated
    }

    # Current values, read from the same source the audit compares against.
    $compileSdkNow = [int]$gradlePins['compile-sdk']
    $targetSdkNow  = [int]$gradlePins['target-sdk']
    $roomNow       = [int]$gradlePins['room-schema-version']

    Assert-Scenario 'baseline' {
        @(Get-ScenarioRecords | Where-Object { $_.Status -in @('FAIL', 'INCONSISTENT', 'MISSING') }).Count -eq 0
    }

    $techPath = Join-Path $tempRoot 'docs\TECH_STACK.md'
    $techText = Get-Content -LiteralPath $techPath -Raw
    Set-MutatedContent -Path $techPath -Text $techText -What 'compile-sdk in TECH_STACK.md' `
        -Pattern ('compileSdk` / `targetSdk`: `' + $compileSdkNow + '`') `
        -Replacement ('compileSdk` / `targetSdk`: `' + ($compileSdkNow - 1) + '`')
    Assert-Scenario 'compile-sdk-mismatch' {
        @(Get-ScenarioRecords | Where-Object { $_.Pin -eq 'compile-sdk' -and $_.Status -eq 'FAIL' }).Count -eq 1
    }
    Set-Content -LiteralPath $techPath -Value $techText

    $requirementsPath = Join-Path $tempRoot 'dev\TECH_REQUIREMENTS.md'
    $requirementsText = Get-Content -LiteralPath $requirementsPath -Raw
    Set-MutatedContent -Path $requirementsPath -Text $requirementsText -What 'target-sdk in TECH_REQUIREMENTS.md' `
        -Pattern ("(?m)^(\| targetSdk\s+\| ){0}" -f $targetSdkNow) `         -Replacement ('${1}' + ($targetSdkNow - 1))
    Assert-Scenario 'target-sdk-mismatch' {
        @(Get-ScenarioRecords | Where-Object { $_.Pin -eq 'target-sdk' -and $_.Status -eq 'FAIL' }).Count -eq 1
    }
    Set-Content -LiteralPath $requirementsPath -Value $requirementsText

    Set-MutatedContent -Path $requirementsPath -Text $requirementsText -What 'room-schema-version in TECH_REQUIREMENTS.md' `
        -Pattern ("(?m)^(\| Room DB version\s+\| ){0}" -f $roomNow) `         -Replacement ('${1}' + ($roomNow - 1))
    Assert-Scenario 'room-schema-version-mismatch' {
        @(Get-ScenarioRecords | Where-Object { $_.Pin -eq 'room-schema-version' -and $_.Status -eq 'FAIL' }).Count -eq 1
    }
    Set-Content -LiteralPath $requirementsPath -Value $requirementsText

    $devOpsPath = Join-Path $tempRoot 'docs\DEV_OPS.md'
    $devOpsText = Get-Content -LiteralPath $devOpsPath -Raw
    Set-MutatedContent -Path $devOpsPath -Text $devOpsText -What 'room-schema-version in DEV_OPS.md' `
        -Pattern ("Room schema version: {0}" -f $roomNow) `         -Replacement ("Room schema version: {0}" -f ($roomNow - 1))
    Assert-Scenario 'room-schema-version-dev-ops-mismatch' {
        @(Get-ScenarioRecords | Where-Object { $_.Pin -eq 'room-schema-version' -and $_.Status -eq 'FAIL' }).Count -eq 1
    }
    Set-Content -LiteralPath $devOpsPath -Value $devOpsText

    Assert-Scenario 'room-schema-history-excluded' {
        $roomSchemaPin.docs.Keys.Count -eq 2 -and
            $roomSchemaPin.docs.ContainsKey('dev/TECH_REQUIREMENTS.md') -and
            $roomSchemaPin.docs.ContainsKey('docs/DEV_OPS.md')
    }

    Write-Output 'doc-drift tests: PASS'
    exit 0
} catch {
    Write-Error "doc-drift tests: FAIL - $($_.Exception.Message)" -ErrorAction Continue
    exit 1
} finally {
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force
    }
}
