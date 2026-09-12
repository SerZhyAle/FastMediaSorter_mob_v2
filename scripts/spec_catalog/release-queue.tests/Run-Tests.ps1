$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$queueScript = Join-Path $repoRoot 'scripts\spec_catalog\release-queue.ps1'
$queuePath = Join-Path $repoRoot 'PLAN\RELEASE_QUEUE.md'
# S1698: the subject ticket and its package are read from the live queue, never hardcoded. The
# original pair (S1183, release 32) shipped, `-List -Release $fixtureRelease` went empty, and the lease case
# failed for a reason that had nothing to do with leases - a test that expires on every release.
# S2852: -List prints the package as a section heading, so the subject's package is the last
# heading seen above its row rather than a column on it.
$listing = @(& pwsh -NoProfile -File $queueScript -List)
$fixtureRelease = $null
$fixtureId = $null
foreach ($listed in $listing) {
    if ($listed -match '^\s*(\d+|--)\s*$') { $fixtureRelease = $Matches[1]; continue }
    if ($listed -match '^\s*(S(\d{4}))_' -and $fixtureRelease) { $fixtureId = $Matches[1]; break }
}
if (-not $fixtureId) { throw 'PLAN/RELEASE_QUEUE.md holds no ticket line to test against.' }
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

function New-MarkedQueueFixture {
    # A two-line release file in temp/, so the round trip is exercised without touching PLAN/.
    # The prose line is load-bearing: with a one-line fixture that line IS the ticket line, so
    # `$parsed.Count -eq 1` counts lines in the file rather than parsed tickets and passes even
    # when the ticket filter never filtered anything (S2420).
    param([Parameter(Mandatory)][string] $Line)
    $path = Join-Path $fixtureDirectory "release-queue-marked-$PID.md"
    [System.IO.File]::WriteAllLines($path, @('# sandbox marked queue', '40', $Line))
    return $path
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
    # The marker is the point of -WithLeases: occupancy has to be readable on the ticket's own
    # row, not only in the block below it. The fixture carries no `mine` property on purpose -
    # ticket-lease emits one, so a row rendered from the fixture also proves the marker survives
    # StrictMode when that property is absent.
    Assert-Condition ($projectionText -match "$fixtureId.*\[taken 1\.5m, S1518 release queue test fixture, session s1518-r") 'Inline lease marker missing on the taken row.'
    Assert-Condition (($defaultOutput -join "`n") -notmatch '\[taken ') 'Default output grew a lease marker.'
    Assert-Condition ([System.IO.File]::ReadAllText($queuePath) -eq $before) 'Lease projection rewrote RELEASE_QUEUE.md.'

    # ── S1698: -Reconcile must collapse duplicate ticket lines ──────────────────────────────
    # Hermetic: _lib.ps1 is dot-sourced so the release paths can be pointed at a sandbox, and
    # Sync-ReleaseQueue is called in-process against synthetic records. Nothing here reads or
    # writes the real PLAN/ files - the regression is about what reconcile does to a FILE, and
    # reproducing it against the live queue would mean duplicating an owner-ordered line.
    . (Join-Path $repoRoot 'scripts\spec_catalog\_lib.ps1')

    # ── The marker written INTO the release files (owner ruling 2026-09-01) ─────────────────
    # The file is the surface the owner reads, so occupancy is rendered there too - which makes
    # the round trip load-bearing: a marker must be stripped before the line is parsed, or it is
    # read as part of the status column and drifts the whole file against the catalog. A stale
    # marker (no live lease behind it) must disappear on the next write, since that is the whole
    # "the process died, the ticket is free again" signal.
    $markedLine = (Format-ReleaseQueueLine -Ticket 'S9004_delta' -Changed '26-08-01 09:00' -Status 'In Progress') +
        '   [taken 15:42, /spec-all, be08adb0]'
    # Read-ReleaseFile returns through `return ,` against unrolling, so a BARE call piped straight
    # into a filter hands that filter the whole List as ONE object - `$_.Kind` then unrolls over
    # the members, the comparison against a non-empty array is always true, and the filter passes
    # everything through unfiltered. Measured 2026-09-03 (S2420, temp/S2420/repro4.ps1): moving the
    # `@(..)` from the call onto the pipeline does NOT fix it either, because the pipeline still
    # enumerates only the outer wrapper. What fixes it is the assignment below - an assignment
    # unrolls the `,` wrapper, leaving the List, and piping a List enumerates its members. The
    # `@(..)` on the filtered result stays: it is what protects `.Count` from $null on no match.
    $parsedLines = Read-ReleaseFile -Path (New-MarkedQueueFixture -Line $markedLine)
    $parsed = @($parsedLines | Where-Object { $_.Kind -eq 'ticket' })
    Assert-Condition ($parsed.Count -eq 1) 'A marked ticket line stopped parsing as a ticket.'
    Assert-Condition ($parsed[0].Status -eq 'In Progress') "Marker leaked into the status column: $($parsed[0].Status)"
    Assert-Condition ($parsed[0].Id -eq 'S9004') 'Marked line parsed the wrong id.'


    $sandbox = Join-Path $repoRoot "temp\S1698\queue-sandbox-$PID"
    New-Item -ItemType Directory -Path $sandbox -Force | Out-Null
    $script:ReleaseQueuePath = Join-Path $sandbox 'RELEASE_QUEUE.md'
    $script:ReleaseReadyPath = Join-Path $sandbox 'RELEASE_READY.md'

    $sandboxQueue = @(
        '# sandbox queue',
        '',
        'current-next-release: 40',
        '',
        'ticket                                                         changed         status',
        '40',
        (Format-ReleaseQueueLine -Ticket 'S9001_alpha' -Changed '26-08-01 09:00' -Status 'Draft'),
        (Format-ReleaseQueueLine -Ticket 'S9001_alpha' -Changed '26-08-01 09:00' -Status 'Draft'),
        '41',
        (Format-ReleaseQueueLine -Ticket 'S9003_gamma' -Changed '26-08-01 09:00' -Status 'Draft')
    )
    $sandboxReady = @(
        '# sandbox ready',
        '',
        '40',
        (Format-ReleaseQueueLine -Ticket 'S9002_beta' -Changed '26-08-02 09:00' -Status 'Verified'),
        (Format-ReleaseQueueLine -Ticket 'S9002_beta' -Changed '26-08-02 09:00' -Status 'Verified'),
        '41',
        (Format-ReleaseQueueLine -Ticket 'S9003_gamma' -Changed '26-08-02 09:00' -Status 'Verified')
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
        @($lines | Where-Object { $_ -match "^$id`_" }).Count
    }

    Assert-Condition ((& $countIn $queueAfter 'S9001') -eq 1) 'Duplicate queue line survived reconcile.'
    Assert-Condition ((& $countIn $readyAfter 'S9002') -eq 1) 'Duplicate ready line survived reconcile.'
    # Listed in BOTH files: exactly one line total, and it belongs to the ready side by status.
    Assert-Condition ((& $countIn $queueAfter 'S9003') -eq 0) 'Cross-file duplicate left a queue line.'
    Assert-Condition ((& $countIn $readyAfter 'S9003') -eq 1) 'Cross-file duplicate is not a single ready line.'
    Assert-Condition ((Get-ReleaseQueueDuplicatesDropped) -eq 3) 'Dropped-duplicate count is wrong.'
    # The owner's package assignment and prose survive the repair.
    Assert-Condition (($queueAfter -join "`n") -match 'current-next-release: 40') 'Reconcile ate a verbatim line.'
    # S2852: the package is the heading above the row, so "the assignment survived" is asserted on
    # the section a row sits under, not on a column. A row re-homed into another package is exactly
    # the failure the rel-column assertion used to catch, and it is now invisible on the row itself.
    $sectionOf = {
        param($lines, $id)
        $seen = $null
        foreach ($l in $lines) {
            if ($l -match '^\s*(\d+|--)\s*$') { $seen = $Matches[1]; continue }
            if ($l -match "^$id`_") { return $seen }
        }
        return $null
    }
    Assert-Condition ((& $sectionOf $queueAfter 'S9001') -eq '40') 'Reconcile moved a ticket out of its package section.'
    Assert-Condition ((& $sectionOf $readyAfter 'S9003') -eq '41') 'A ticket crossing to the ready file lost its package.'
    Assert-Condition (@($queueAfter | Where-Object { $_ -match '^S9001_alpha\s+\d{2}-\d{2}-\d{2} \d{2}:\d{2}\s' }).Count -eq 1) 'The changed column lost its minute precision.'

    # Idempotent: a second pass over the repaired files finds nothing left to drop.
    Sync-ReleaseQueue -Records ([object[]]$records)
    Assert-Condition ((Get-ReleaseQueueDuplicatesDropped) -eq 0) 'Second reconcile still reported duplicates.'

    # ── S2921: where a brand-new row lands ──────────────────────────────────────────────────
    # Two placements, one root cause. A package ends with an owner-gated boundary ticket that is
    # its release line, and the writer used to append every new row BELOW it - the heading above
    # the row then said one package while the owner reads everything under that line as the next.
    # A package holding a heading but no rows yet was indistinguishable from a package the file
    # does not carry at all, so its first row went to the very END of the file and grew a SECOND
    # heading with the same number.
    #
    # Hermetic in both directions: the release paths point at the sandbox as above, and
    # $script:SpecsDirPath does too, so the owner-gate predicate reads spec bodies this test wrote
    # rather than live tickets. Keying the case on the real boundary ticket would have expired it
    # at the next release, which is the trap the S1698 fixture above already names.
    if (-not (Get-Command Find-ReleaseBlockInsertIndex -ErrorAction SilentlyContinue)) {
        Write-Output 'release-queue tests: SKIP S2921 placement - the resolved harness predates the fix (claude plugin update sza@sza-unified-rules).'
    } else {
        $priorSpecsDir = $script:SpecsDirPath
        try {
            $script:SpecsDirPath = $sandbox
            Clear-OwnerGateCache
            # The marker phrase is the owner directive itself, not a test token: the predicate has
            # to match what a real boundary spec says, or the case passes on a shape nothing writes.
            [System.IO.File]::WriteAllLines((Join-Path $sandbox 'S9101_ordinary.md'), @('# ordinary ticket'))
            [System.IO.File]::WriteAllLines((Join-Path $sandbox 'S9102_boundary.md'),
                @('# boundary ticket', '', '**Автоматическая передача отключена** - запускает владелец.'))

            $placementQueue = @(
                '# sandbox queue',
                '',
                'current-next-release: 50',
                '',
                '50',
                '# the open package',
                (Format-ReleaseQueueLine -Ticket 'S9101_ordinary' -Changed '26-08-01 09:00' -Status 'Draft'),
                (Format-ReleaseQueueLine -Ticket 'S9102_boundary' -Changed '26-08-01 09:00' -Status 'Draft'),
                '',
                '51',
                '# the next package',
                (Format-ReleaseQueueLine -Ticket 'S9103_later' -Changed '26-08-01 09:00' -Status 'Draft')
            )
            [System.IO.File]::WriteAllLines($script:ReleaseQueuePath, $placementQueue)
            [System.IO.File]::WriteAllLines($script:ReleaseReadyPath, @('# sandbox ready', '', '50'))

            $placementRecords = @(
                [pscustomobject]@{ id = 'S9101'; status = 'Draft'; file = 'PLAN/S9101_ordinary.md'; updated = '2026-08-01 10:00' },
                [pscustomobject]@{ id = 'S9102'; status = 'Draft'; file = 'PLAN/S9102_boundary.md'; updated = '2026-08-01 10:00' },
                [pscustomobject]@{ id = 'S9103'; status = 'Draft'; file = 'PLAN/S9103_later.md';    updated = '2026-08-01 10:00' },
                [pscustomobject]@{ id = 'S9104'; status = 'Draft'; file = 'PLAN/S9104_fresh.md';    updated = '2026-09-11 10:00' }
            )
            Sync-ReleaseQueue -Records ([object[]]$placementRecords)
            $placed = @(Get-Content -LiteralPath $script:ReleaseQueuePath)

            $indexOf = {
                param($lines, $id)
                for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -match "^$id`_") { return $i } }
                return -1
            }
            $freshAt = & $indexOf $placed 'S9104'
            Assert-Condition ($freshAt -ge 0) 'The new ticket did not reach the queue at all.'
            Assert-Condition ((& $sectionOf $placed 'S9104') -eq '50') 'The new ticket landed outside the open package.'
            Assert-Condition ($freshAt -lt (& $indexOf $placed 'S9102')) 'The new ticket landed below the package boundary row.'
            Assert-Condition ($freshAt -gt (& $indexOf $placed 'S9101')) 'The new ticket jumped above work the owner already ordered.'
            Assert-Condition (@($placed | Where-Object { $_ -match '^\s*50\s*$' }).Count -eq 1) 'The reconcile grew a second heading for the open package.'

            # The same placement with the open package holding a heading and no rows: the row goes
            # inside that block, not past the end of the file.
            $emptyQueue = @(
                '# sandbox queue',
                '',
                'current-next-release: 50',
                '',
                '50',
                '# the open package - no rows yet',
                '',
                '51',
                (Format-ReleaseQueueLine -Ticket 'S9103_later' -Changed '26-08-01 09:00' -Status 'Draft')
            )
            [System.IO.File]::WriteAllLines($script:ReleaseQueuePath, $emptyQueue)
            [System.IO.File]::WriteAllLines($script:ReleaseReadyPath, @('# sandbox ready', '', '50'))
            Sync-ReleaseQueue -Records ([object[]]@(
                    [pscustomobject]@{ id = 'S9103'; status = 'Draft'; file = 'PLAN/S9103_later.md'; updated = '2026-08-01 10:00' },
                    [pscustomobject]@{ id = 'S9104'; status = 'Draft'; file = 'PLAN/S9104_fresh.md'; updated = '2026-09-11 10:00' }
                ))
            $emptyAfter = @(Get-Content -LiteralPath $script:ReleaseQueuePath)
            Assert-Condition ((& $sectionOf $emptyAfter 'S9104') -eq '50') 'A row added to an empty package left its block.'
            Assert-Condition ((& $indexOf $emptyAfter 'S9104') -lt (& $indexOf $emptyAfter 'S9103')) 'A row added to an empty package landed past the next package.'
            Assert-Condition (@($emptyAfter | Where-Object { $_ -match '^\s*50\s*$' }).Count -eq 1) 'An empty package gained a second heading.'
        } finally {
            $script:SpecsDirPath = $priorSpecsDir
            Clear-OwnerGateCache
        }
    }

    Write-Output 'release-queue tests: PASS'
}
finally {
    if ($sandbox -and (Test-Path -LiteralPath $sandbox)) {
        Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $fixturePath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath (Join-Path $fixtureDirectory "release-queue-marked-$PID.md") -Force -ErrorAction SilentlyContinue
    if ([string]::IsNullOrWhiteSpace($priorFixturePath)) {
        Remove-Item Env:\FMS_TICKET_LEASE_STATUS_FIXTURE -ErrorAction SilentlyContinue
    } else {
        $env:FMS_TICKET_LEASE_STATUS_FIXTURE = $priorFixturePath
    }
}
