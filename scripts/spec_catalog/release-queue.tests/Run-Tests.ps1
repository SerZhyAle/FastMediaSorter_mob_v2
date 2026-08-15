$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$queueScript = Join-Path $repoRoot 'scripts\spec_catalog\release-queue.ps1'
$queuePath = Join-Path $repoRoot 'PLAN\RELEASE_QUEUE.md'
$fixtureId = 'S1183'
$fixtureSessionId = "s1518-release-queue-test-$PID"
$fixtureDirectory = Join-Path $repoRoot 'temp\S1518'
$fixturePath = Join-Path $fixtureDirectory "release-queue-lease-fixture-$PID.json"
$priorFixturePath = $env:FMS_TICKET_LEASE_STATUS_FIXTURE
$before = [System.IO.File]::ReadAllText($queuePath)

function Assert-Condition {
    param([bool] $Condition, [string] $Message)
    if (-not $Condition) { throw $Message }
}

try {
    New-Item -ItemType Directory -Path $fixtureDirectory -Force | Out-Null
    $fixture = @(
        [pscustomobject]@{
            id = $fixtureId
            sessionId = $fixtureSessionId
            ageMinutes = 1.5
            liveness = 'foreign-live'
            reason = 'S1518 release queue test fixture'
        }
    ) | ConvertTo-Json -Compress
    [System.IO.File]::WriteAllText($fixturePath, $fixture)
    $env:FMS_TICKET_LEASE_STATUS_FIXTURE = $fixturePath

    $defaultOutput = & pwsh -NoProfile -File $queueScript -List -Release 32
    Assert-Condition ($LASTEXITCODE -eq 0) 'Default release-queue list failed.'
    Assert-Condition (($defaultOutput -join "`n") -notmatch 'active ticket leases') 'Default output changed.'

    $projection = & pwsh -NoProfile -File $queueScript -List -Release 32 -WithLeases
    Assert-Condition ($LASTEXITCODE -eq 0) 'Lease projection failed.'
    $projectionText = $projection -join "`n"
    Assert-Condition ($projectionText -match 'active ticket leases') 'Lease projection header missing.'
    Assert-Condition ($projectionText -match "\[lease\] $fixtureId session=$fixtureSessionId") 'Fixture lease metadata missing.'
    Assert-Condition ([System.IO.File]::ReadAllText($queuePath) -eq $before) 'Lease projection rewrote RELEASE_QUEUE.md.'

    Write-Output 'release-queue tests: PASS'
}
finally {
    Remove-Item -LiteralPath $fixturePath -Force -ErrorAction SilentlyContinue
    if ([string]::IsNullOrWhiteSpace($priorFixturePath)) {
        Remove-Item Env:\FMS_TICKET_LEASE_STATUS_FIXTURE -ErrorAction SilentlyContinue
    } else {
        $env:FMS_TICKET_LEASE_STATUS_FIXTURE = $priorFixturePath
    }
}
