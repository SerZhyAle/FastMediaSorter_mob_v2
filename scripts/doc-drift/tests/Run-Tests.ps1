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

    Assert-Scenario 'baseline' {
        @(Get-ScenarioRecords | Where-Object { $_.Status -in @('FAIL', 'INCONSISTENT', 'MISSING') }).Count -eq 0
    }

    $techPath = Join-Path $tempRoot 'docs\TECH_STACK.md'
    $techText = Get-Content -LiteralPath $techPath -Raw
    Set-Content -LiteralPath $techPath -Value ($techText -replace 'compileSdk` / `targetSdk`: `36`', 'compileSdk` / `targetSdk`: `35`')
    Assert-Scenario 'compile-sdk-mismatch' {
        @(Get-ScenarioRecords | Where-Object { $_.Pin -eq 'compile-sdk' -and $_.Status -eq 'FAIL' }).Count -eq 1
    }
    Set-Content -LiteralPath $techPath -Value $techText

    $requirementsPath = Join-Path $tempRoot 'dev\TECH_REQUIREMENTS.md'
    $requirementsText = Get-Content -LiteralPath $requirementsPath -Raw
    Set-Content -LiteralPath $requirementsPath -Value ($requirementsText -replace '(?m)^(\| targetSdk\s+\| )36', '${1}35')
    Assert-Scenario 'target-sdk-mismatch' {
        @(Get-ScenarioRecords | Where-Object { $_.Pin -eq 'target-sdk' -and $_.Status -eq 'FAIL' }).Count -eq 1
    }
    Set-Content -LiteralPath $requirementsPath -Value $requirementsText

    Set-Content -LiteralPath $requirementsPath -Value ($requirementsText -replace '(?m)^(\| Room DB version\s+\| )50', '${1}49')
    Assert-Scenario 'room-schema-version-mismatch' {
        @(Get-ScenarioRecords | Where-Object { $_.Pin -eq 'room-schema-version' -and $_.Status -eq 'FAIL' }).Count -eq 1
    }
    Set-Content -LiteralPath $requirementsPath -Value $requirementsText

    $devOpsPath = Join-Path $tempRoot 'docs\DEV_OPS.md'
    $devOpsText = Get-Content -LiteralPath $devOpsPath -Raw
    Set-Content -LiteralPath $devOpsPath -Value ($devOpsText -replace 'Room schema version: 50', 'Room schema version: 49')
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
