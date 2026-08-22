$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$queueScript = Join-Path $repoRoot 'scripts\spec_catalog\release-queue.ps1'
$queuePath = Join-Path $repoRoot 'PLAN\RELEASE_QUEUE.md'
# S1698: the subject ticket and its package are read from the live queue, never hardcoded. The
# original pair (S1183, release 32) shipped, `-List -Release $fixtureRelease` went empty, and the lease case
# failed for a reason that had nothing to do with leases - a test that expires on every release.
$subjectLine = @(& pwsh -NoProfile -File $queueScript -List | Where-Object { $_ -match '^\s*(\d+|--)\s+S\d{4}_' })[0]
if (-not $subjectLine) { throw 'PLAN/RELEASE_QUEUE.md holds no ticket line to test against.' }
if ($subjectLine -notmatch '^\s*(\d+|--)\s+(S(\d{4}))_') { throw "Unparsable queue line: $subjectLine" }
$fixtureRelease = $Matches[1]
$fixtureId = $Matches[2]
$fixtureSessionId = "s1518-release-queue-test-$PID"
$fixtureDirectory = Join-Path $repoRoot 'temp\S1518'
$fixturePath = Join-Path $fixtureDirectory "release-queue-lease-fixture-$PID.json"
$priorFixturePath = $env:FMS_TICKET_LEASE_STATUS_FIXTURE
# Declared up front: the finally block runs under the StrictMode the dot-sourced _lib.ps1 turns on.
$sandbox = $null
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

    $defaultOutput = & pwsh -NoProfile -File $queueScript -List -Release $fixtureRelease
    Assert-Condition ($LASTEXITCODE -eq 0) 'Default release-queue list failed.'
    Assert-Condition (($defaultOutput -join "`n") -notmatch 'active ticket leases') 'Default output changed.'

    $projection = & pwsh -NoProfile -File $queueScript -List -Release $fixtureRelease -WithLeases
    Assert-Condition ($LASTEXITCODE -eq 0) 'Lease projection failed.'
    $projectionText = $projection -join "`n"
    Assert-Condition ($projectionText -match 'active ticket leases') 'Lease projection header missing.'
    Assert-Condition ($projectionText -match "\[lease\] $fixtureId session=$fixtureSessionId") 'Fixture lease metadata missing.'
    Assert-Condition ([System.IO.File]::ReadAllText($queuePath) -eq $before) 'Lease projection rewrote RELEASE_QUEUE.md.'

    # ── S1698: -Reconcile must collapse duplicate ticket lines ──────────────────────────────
    # Hermetic: _lib.ps1 is dot-sourced so the release paths can be pointed at a sandbox, and
    # Sync-ReleaseQueue is called in-process against synthetic records. Nothing here reads or
    # writes the real PLAN/ files - the regression is about what reconcile does to a FILE, and
    # reproducing it against the live queue would mean duplicating an owner-ordered line.
    . (Join-Path $repoRoot 'scripts\spec_catalog\_lib.ps1')

    $sandbox = Join-Path $repoRoot "temp\S1698\queue-sandbox-$PID"
    New-Item -ItemType Directory -Path $sandbox -Force | Out-Null
    $script:ReleaseQueuePath = Join-Path $sandbox 'RELEASE_QUEUE.md'
    $script:ReleaseReadyPath = Join-Path $sandbox 'RELEASE_READY.md'

    $sandboxQueue = @(
        '# sandbox queue',
        '',
        'current-next-release: 40',
        '',
        'rel  ticket                                                         changed     status',
        (Format-ReleaseQueueLine -Release '40' -Ticket 'S9001_alpha' -Changed '2026-08-01' -Status 'Draft'),
        (Format-ReleaseQueueLine -Release '40' -Ticket 'S9001_alpha' -Changed '2026-08-01' -Status 'Draft'),
        (Format-ReleaseQueueLine -Release '41' -Ticket 'S9003_gamma' -Changed '2026-08-01' -Status 'Draft')
    )
    $sandboxReady = @(
        '# sandbox ready',
        '',
        (Format-ReleaseQueueLine -Release '40' -Ticket 'S9002_beta' -Changed '2026-08-02' -Status 'Verified'),
        (Format-ReleaseQueueLine -Release '40' -Ticket 'S9002_beta' -Changed '2026-08-02' -Status 'Verified'),
        (Format-ReleaseQueueLine -Release '41' -Ticket 'S9003_gamma' -Changed '2026-08-02' -Status 'Verified')
    )
    [System.IO.File]::WriteAllLines($script:ReleaseQueuePath, $sandboxQueue)
    [System.IO.File]::WriteAllLines($script:ReleaseReadyPath, $sandboxReady)

    $records = @(
        [pscustomobject]@{ id = 'S9001'; status = 'Draft';    file = 'PLAN/S9001_alpha.md'; updated = '2026-08-01 10:00' },
        [pscustomobject]@{ id = 'S9002'; status = 'Verified'; file = 'PLAN/S9002_beta.md';  updated = '2026-08-02 10:00' },
        [pscustomobject]@{ id = 'S9003'; status = 'Verified'; file = 'PLAN/S9003_gamma.md'; updated = '2026-08-02 10:00' }
    )

    Sync-ReleaseQueue -Records ([object[]]$records)

    $queueAfter = @(Get-Content -LiteralPath $script:ReleaseQueuePath)
    $readyAfter = @(Get-Content -LiteralPath $script:ReleaseReadyPath)
    $countIn = {
        param($lines, $id)
        @($lines | Where-Object { $_ -match "\s$id`_" }).Count
    }

    Assert-Condition ((& $countIn $queueAfter 'S9001') -eq 1) 'Duplicate queue line survived reconcile.'
    Assert-Condition ((& $countIn $readyAfter 'S9002') -eq 1) 'Duplicate ready line survived reconcile.'
    # Listed in BOTH files: exactly one line total, and it belongs to the ready side by status.
    Assert-Condition ((& $countIn $queueAfter 'S9003') -eq 0) 'Cross-file duplicate left a queue line.'
    Assert-Condition ((& $countIn $readyAfter 'S9003') -eq 1) 'Cross-file duplicate is not a single ready line.'
    Assert-Condition ((Get-ReleaseQueueDuplicatesDropped) -eq 3) 'Dropped-duplicate count is wrong.'
    # The owner's package assignment and prose survive the repair.
    Assert-Condition (($queueAfter -join "`n") -match 'current-next-release: 40') 'Reconcile ate a verbatim line.'
    Assert-Condition (@($queueAfter | Where-Object { $_ -match '^40\s+S9001_alpha\s' }).Count -eq 1) 'Reconcile rewrote the rel column.'

    # Idempotent: a second pass over the repaired files finds nothing left to drop.
    Sync-ReleaseQueue -Records ([object[]]$records)
    Assert-Condition ((Get-ReleaseQueueDuplicatesDropped) -eq 0) 'Second reconcile still reported duplicates.'

    Write-Output 'release-queue tests: PASS'
}
finally {
    if ($sandbox -and (Test-Path -LiteralPath $sandbox)) {
        Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $fixturePath -Force -ErrorAction SilentlyContinue
    if ([string]::IsNullOrWhiteSpace($priorFixturePath)) {
        Remove-Item Env:\FMS_TICKET_LEASE_STATUS_FIXTURE -ErrorAction SilentlyContinue
    } else {
        $env:FMS_TICKET_LEASE_STATUS_FIXTURE = $priorFixturePath
    }
}
