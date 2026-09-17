# Run-Tests.ps1 (S1063) - regression suite for scripts/spec_catalog/close-and-log.ps1.
#
# Guards the invariants that keep a malformed call from half-closing a ticket, or from closing it
# with a false record:
#   * a bad call shape is rejected BEFORE the first mutation (bind time or pre-flight),
#   * a good call still applies every step,
#   * (S1072) a capability record carries what the caller stated - never a derived guess.
#
# Regression origin: passing a multi-element PowerShell array to -DevLogs through `pwsh -File`
# left the extra elements as positional args. They silently bound to -FeatId and only surfaced
# at the all-features step - after the status flip and first dev-log had already been written,
# leaving the ticket closed but the inventory unsynced (observed closing S1062, 2026-07-15).
# The same call shape failed LOUDLY back on S0082 (2026-06-03): the failure mode degraded from
# hard error to silent mis-bind when the -Feat* params were added (2026-06-17), because each
# new optional [string] param opens another positional slot. Hence PositionalBinding = $false
# rather than a point-check on -FeatId: it cannot rot as the param block grows.
#
# What this runner touches (it is NOT hermetic - unlike scripts/guard.tests):
#   * dev/CHANGELOG.md and docs/ALL_FEATURES.jsonl: the happy-path cases genuinely write to
#     both, and the finally block undoes exactly those writes - dev-log rows by the probe
#     target in their target column, inventory records by id through all_features/remove.ps1.
#     Neither file is ever snapshotted and copied back whole (S1521): both are appended by
#     every parallel session (S1437), so a whole-file restore silently reverts a sibling's
#     row. Cleanup keys off the probe markers rather than this run's own writes, so a run
#     also clears residue left by a predecessor killed before its finally block - but only
#     residue OLDER than $staleProbeAfter, because the marker does not distinguish a dead
#     predecessor from a live second instance, and deleting a live one's rows is what S3022
#     reproduced as the captured 'B2 delta=0'. Every rewrite of the changelog here is held
#     under the canon writer's mutex (scripts/utils/devlog-mutex.ps1), which is the only
#     thing serialising that file against the closures of every other session.
#   * The subject ticket's `updated` timestamp moves, because the happy-path cases run a real
#     status write. Every case passes -Status <the ticket's CURRENT status> -StatusOnly, so no
#     lifecycle transition ever happens and the suite is idempotent across repeated runs.
#   * -SkipCatalogSync everywhere: no scan/render, nothing under dev/CATALOG is touched.
#
# Usage:  pwsh -NoProfile -File scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1
#         pwsh -NoProfile -File .../Run-Tests.ps1 -SubjectId S0223   # any live catalog id
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.
#   2   could not verify - another instance held the suite lock past its timeout (S3022).

[CmdletBinding()]
param(
    # Any id present in the catalog. Its status is read and echoed straight back, so the choice
    # is inert - it defaults to the ticket that introduced these guarantees.
    [string]$SubjectId = 'S1063'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

$facade = Join-Path $repoRoot 'scripts/spec_catalog/close-and-log.ps1'
$selectPs1 = Join-Path $repoRoot 'scripts/spec_catalog/select.ps1'
$changelog = Join-Path $repoRoot 'dev/CHANGELOG.md'
$features = Join-Path $repoRoot 'docs/ALL_FEATURES.jsonl'

# S3022: every whole-file rewrite of the changelog below runs under the canon writer's own mutex.
# Without it the exclusive WriteAllLines collides with a live closure's Add-Content and kills its
# dev-log step, and the read-filter-write around it drops that closure's row with no error anywhere.
. (Join-Path $repoRoot 'scripts/utils/devlog-mutex.ps1')

# S3022: how old a foreign probe row must be before the residue sweep may drop it. One run measures
# 35.6 s alone and 49.1 s against a concurrent writer, so ten minutes is an order of magnitude above
# any live instance while still clearing what a killed predecessor left behind hours ago.
$staleProbeAfter = [TimeSpan]::FromMinutes(10)
$rowTimestampPattern = '^\|\s*(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\s*\|'

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

function Get-SpecField([string]$field) {
    $json = & $pwshExe -NoProfile -File $selectPs1 -Id $SubjectId -Format json | ConvertFrom-Json
    return $json.$field
}

# S3022: the ids of every inventory record belonging to the subject ticket. J3 used to count the
# WHOLE file, which is the same arithmetic B2 was wrongly accused of and the same file shape: every
# parallel session appends to docs/ALL_FEATURES.jsonl, and a second live instance of this suite
# writing its own case D or I record read as "inventory grew" (measured 2026-09-12, two instances,
# the second failing on nothing of its own). Filtering by the subject id drops every stranger's
# record, and filtering out the known probe ids drops a sibling INSTANCE's, leaving only a record
# this call could have invented.
function Get-SubjectRecordIds {
    if (-not (Test-Path $features)) { return @() }
    $needle = '"spec":"' + $SubjectId + '"'
    $ids = @()
    foreach ($line in [System.IO.File]::ReadAllLines($features)) {
        if ($line.Contains($needle) -and $line -match '"id":"([^"]+)"') { $ids += $Matches[1] }
    }
    return $ids
}

function Get-ProbeLineCount([string]$path) {
    if (Test-Path $path) { return @(Get-Content -LiteralPath $path | Where-Object { $_ -match $probeRowPattern }).Count }
    return 0
}

# S3022: this suite is not safe to run twice at once, and no scoping can make it so. The five
# rejected-call cases (A3, E3, F3, G3, H3) prove "nothing was mutated" by reading the subject
# ticket's `updated` stamp before and after, while the happy-path cases of a SECOND instance write
# that same ticket for real - measured 2026-09-12, an instance failing E3 on a write its own
# rejected call never made. The subject is one catalog record shared by both runs, so the fix is to
# not overlap rather than to weaken the assertion. Per checkout, like the changelog mutex, and it
# dies with its holder so a killed run cannot wedge the next one.
$suiteMutexHash = [System.BitConverter]::ToString(
    [System.Security.Cryptography.MD5]::HashData([System.Text.Encoding]::UTF8.GetBytes($repoRoot.ToLowerInvariant()))
).Replace('-', '')
$suiteMutex = New-Object System.Threading.Mutex($false, "Global\FMS-CloseAndLogTests-$suiteMutexHash")
$suiteMutexHeld = $false
try { $suiteMutexHeld = $suiteMutex.WaitOne([TimeSpan]::FromMinutes(10)) }
catch [System.Threading.AbandonedMutexException] { $suiteMutexHeld = $true }
if (-not $suiteMutexHeld) {
    # Ten minutes is more than a dozen runs (35.6 s alone, 49.1 s contended), so a timeout means
    # something is wedged rather than busy. That is an environment this suite cannot judge in, not
    # a failure of the facade it tests.
    Write-Host "close-and-log tests: COULD NOT VERIFY - another instance held the suite lock for 10 minutes" -ForegroundColor Yellow
    $suiteMutex.Dispose()
    exit 2
}

# Echo the ticket's own status back at it: a write that cannot transition anything.
$subjectStatus = Get-SpecField 'status'
if (-not $subjectStatus) { Write-Host "Cannot resolve status of $SubjectId" -ForegroundColor Red; exit 1 }
Write-Host "subject: $SubjectId (status '$subjectStatus', echoed back via -StatusOnly)" -ForegroundColor DarkGray

# Everything this run can write carries one of two markers, and cleanup keys off exactly
# those: dev-log rows by $probeTarget in their target column, inventory records by id.
$runId = [Guid]::NewGuid().ToString().Substring(0,8)
$probeTarget = "s1063-tests-$runId"
$probeRowPattern = '\|\s*`' + [regex]::Escape($probeTarget) + '`\s*\|'
$anyProbeRowPattern = '\|\s*`s1063-tests.*`\s*\|'

# S3022: the sweep that clears a killed predecessor's residue used to drop every row matching
# 's1063-tests.*' - the '.*' spans the run id, so it matched a LIVE second instance's rows too, and
# deleting those between their append and their read-back is what produced the captured
# 'B2 delta=0'. Age is what separates the two: a predecessor's residue is old, a live instance's
# rows are seconds old. A row whose timestamp will not parse is kept rather than guessed at - it
# cannot reach any assertion here, since every one of them counts THIS run's marker only.
function Test-StaleProbeRow([string]$row) {
    if ($row -notmatch $anyProbeRowPattern) { return $false }
    if ($row -notmatch $rowTimestampPattern) { return $false }
    $stamp = [datetime]::MinValue
    if (-not [datetime]::TryParseExact($Matches[1], 'yyyy-MM-dd HH:mm:ss', $null, 'None', [ref]$stamp)) { return $false }
    return ((Get-Date) - $stamp) -gt $staleProbeAfter
}

if (Test-Path $changelog) {
    Enter-DevLogWriteLock -RepoRoot $repoRoot
    try {
        $rows = [System.IO.File]::ReadAllLines($changelog)
        $keptRows = @($rows | Where-Object { -not (Test-StaleProbeRow $_) })
        if ($rows.Count -ne $keptRows.Count) {
            [System.IO.File]::WriteAllLines($changelog, $keptRows, (New-Object System.Text.UTF8Encoding($false)))
        }
    }
    finally { Exit-DevLogWriteLock }
}

# The ids the happy-path cases produce. B and I let close-and-log.ps1 derive one from
# -FeatArea + -FeatName; D states one outright. A change to that derivation fails I2 before
# it could orphan a record here, so this list cannot silently drift out of date.
$probeFeatureIds = @(
    'diagnostics.sandbox-capability-two', # case B, derived from "Sandbox capability two"
    'diagnostics.sandbox_probe',          # case D, passed verbatim as -FeatId
    'diagnostics.stated-name-wins'        # case I, derived from "Stated name wins"
)

function New-DevLogJson([string]$file, [string]$desc) {
    return ([ordered]@{ file = $file; target = $probeTarget; desc = "$desc ($runId)" } | ConvertTo-Json -Compress)
}

function Test-ProbeRecord([string]$recordId) {
    if (-not (Test-Path $features)) { return $false }
    $needle = '"id":"' + $recordId + '"'
    foreach ($line in (Get-Content -LiteralPath $features -Encoding UTF8)) {
        if ($line.Contains($needle)) { return $true }
    }
    return $false
}

# One desc per writing case, never shared: add_to_dev_log.ps1 skips a row whose
# file|target|desc signature repeats within the last eight rows (a guard added after S1181),
# so the single entry B, C and J used to share made the guard swallow C's and J's row and
# both cases failed on a zero delta (S1521). A, E and G are rejected before any write, so
# their entries never reach the journal and may reuse $j1/$j2 freely.
$j1 = New-DevLogJson 'PLAN/S1063_bugfix-close-and-log-funcop-id-mapping.md' 'sandbox entry one'
$j2 = New-DevLogJson 'scripts/spec_catalog/close-and-log.ps1' 'sandbox entry two'
$j3 = New-DevLogJson 'PLAN/S1063_bugfix-close-and-log-funcop-id-mapping.md' 'sandbox entry three'
$j4 = New-DevLogJson 'scripts/spec_catalog/close-and-log.ps1' 'sandbox entry four'

try {
    # --- A: the regression itself. Multi-element array via -File must die at bind time. ---
    Write-Host "A: multi-element -DevLogs through -File is rejected at bind time" -ForegroundColor Yellow
    $updBefore = Get-SpecField 'updated'
    $clBefore = Get-ProbeLineCount $changelog
    $outA = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
        -DevLogs $j1 $j2 -FuncOp ADD -FuncDesc "sandbox capability" 2>&1 | Out-String
    $exitA = $LASTEXITCODE
    Assert-That "A1 non-zero exit" ($exitA -ne 0) "exit=$exitA"
    Assert-That "A2 error names the stray positional arg" ($outA -match 'positional parameter cannot be found') "out=$($outA.Trim())"
    Assert-That "A3 status not mutated" ((Get-SpecField 'updated') -eq $updBefore) "updated moved from $updBefore"
    Assert-That "A4 no dev-log written" ((Get-ProbeLineCount $changelog) -eq $clBefore) "changelog grew"

    # --- B: the documented transport form applies every step. ---
    Write-Host "B: single JSON-array string -DevLogs + -FuncOp applies fully" -ForegroundColor Yellow
    $clBefore = Get-ProbeLineCount $changelog
    $outB = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
        -DevLogs "[$j1,$j2]" -FuncOp ADD -FuncDesc "sandbox capability two" `
        -FeatArea "Diagnostics" -FeatName "Sandbox capability two" -FeatFlavors "standard" 2>&1 | Out-String
    $exitB = $LASTEXITCODE
    Assert-That "B1 exit 0" ($exitB -eq 0) "exit=$exitB out=$($outB.Trim())"
    Assert-That "B2 both dev-logs written" ((Get-ProbeLineCount $changelog) -eq ($clBefore + 2)) "delta=$((Get-ProbeLineCount $changelog) - $clBefore)"
    $recB = @(Get-Content -LiteralPath $features | Where-Object { $_ -match "`"spec`":`"$SubjectId`"" })
    Assert-That "B3 capability recorded" ($recB.Count -ge 1) "records=$($recB.Count)"

    # --- C: single-element -DevLogs (the shape that always survived -File). ---
    Write-Host "C: single -DevLogs entry still works" -ForegroundColor Yellow
    $clBefore = Get-ProbeLineCount $changelog
    $outC = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
        -DevLogs $j3 2>&1 | Out-String
    $exitC = $LASTEXITCODE
    Assert-That "C1 exit 0" ($exitC -eq 0) "exit=$exitC out=$($outC.Trim())"
    Assert-That "C2 one dev-log written" ((Get-ProbeLineCount $changelog) -eq ($clBefore + 1)) "delta=$((Get-ProbeLineCount $changelog) - $clBefore)"

    # --- D: an explicit well-formed -FeatId still overrides the derived id. ---
    Write-Host "D: valid kebab -FeatId is honoured" -ForegroundColor Yellow
    $outD = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
        -FuncOp FIX -FuncDesc "sandbox explicit id" -FeatId "diagnostics.sandbox_probe" `
        -FeatArea "Diagnostics" -FeatName "Sandbox probe" -FeatFlavors "standard" 2>&1 | Out-String
    $exitD = $LASTEXITCODE
    Assert-That "D1 exit 0" ($exitD -eq 0) "exit=$exitD out=$($outD.Trim())"
    $recD = @(Get-Content -LiteralPath $features | Where-Object { $_ -match '"id":"diagnostics\.sandbox_probe"' })
    Assert-That "D2 explicit id used verbatim" ($recD.Count -eq 1) "records=$($recD.Count)"

    # --- E: a malformed -FeatId dies in pre-flight, not three mutations later. ---
    # The message assertion matters since S1072: several pre-flight rules now answer with exit 2, so
    # a bare code check could pass while the -FeatId rule itself is broken.
    Write-Host "E: malformed -FeatId rejected before any mutation" -ForegroundColor Yellow
    $updBefore = Get-SpecField 'updated'
    $clBefore = Get-ProbeLineCount $changelog
    $outE = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
        -DevLogs $j1 -FuncOp ADD -FuncDesc "sandbox bad id" -FeatId $j2 2>&1 | Out-String
    $exitE = $LASTEXITCODE
    Assert-That "E1 exit 2 (bad arguments)" ($exitE -eq 2) "exit=$exitE out=$($outE.Trim())"
    Assert-That "E2 rejected for the -FeatId shape, not another rule" ($outE -match 'Invalid -FeatId') "out=$($outE.Trim())"
    Assert-That "E3 status not mutated" ((Get-SpecField 'updated') -eq $updBefore) "updated moved from $updBefore"
    Assert-That "E4 no dev-log written" ((Get-ProbeLineCount $changelog) -eq $clBefore) "changelog grew"

    # --- F: -FuncOp and -FuncDesc must arrive together or not at all. ---
    Write-Host "F: -FuncOp without -FuncDesc rejected" -ForegroundColor Yellow
    $updBefore = Get-SpecField 'updated'
    $outF = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
        -FuncOp ADD 2>&1 | Out-String
    $exitF = $LASTEXITCODE
    Assert-That "F1 exit 2 (bad arguments)" ($exitF -eq 2) "exit=$exitF out=$($outF.Trim())"
    Assert-That "F2 rejected for the xor rule, not the S1072 Feat* rule" ($outF -match 'must be supplied together') "out=$($outF.Trim())"
    Assert-That "F3 status not mutated" ((Get-SpecField 'updated') -eq $updBefore) "updated moved from $updBefore"

    # --- G: a DevLogs entry missing a field dies in pre-flight too. ---
    Write-Host "G: malformed -DevLogs entry rejected before any mutation" -ForegroundColor Yellow
    $updBefore = Get-SpecField 'updated'
    $outG = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
        -DevLogs '{"file":"x.kt","target":"t"}' 2>&1 | Out-String
    $exitG = $LASTEXITCODE
    Assert-That "G1 exit 2 (bad arguments)" ($exitG -eq 2) "exit=$exitG out=$($outG.Trim())"
    Assert-That "G2 status not mutated" ((Get-SpecField 'updated') -eq $updBefore) "updated moved from $updBefore"

    # --- H (S1072): recording a capability without stating its facts is rejected, one field at a
    # time. Each of the three used to be silently invented ('General' / an 80-char cut of -FuncDesc /
    # 'standard'), producing a valid-looking but false record.
    $featCases = @(
        @{ Label = '-FeatArea'; Args = @('-FeatName', 'Sandbox H', '-FeatFlavors', 'standard') },
        @{ Label = '-FeatName'; Args = @('-FeatArea', 'Spec Tooling', '-FeatFlavors', 'standard') },
        @{ Label = '-FeatFlavors'; Args = @('-FeatArea', 'Spec Tooling', '-FeatName', 'Sandbox H') }
    )
    foreach ($case in $featCases) {
        Write-Host "H: -FuncOp without $($case.Label) rejected before any mutation" -ForegroundColor Yellow
        $updBefore = Get-SpecField 'updated'
        $clBefore = Get-ProbeLineCount $changelog
        $outH = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
            -FuncOp ADD -FuncDesc "sandbox missing $($case.Label)" @($case.Args) 2>&1 | Out-String
        $exitH = $LASTEXITCODE
        Assert-That "H1 $($case.Label): exit 2" ($exitH -eq 2) "exit=$exitH out=$($outH.Trim())"
        Assert-That "H2 $($case.Label): message names the missing field" ($outH -match [regex]::Escape($case.Label)) "out=$($outH.Trim())"
        Assert-That "H3 $($case.Label): status not mutated" ((Get-SpecField 'updated') -eq $updBefore) "updated moved"
        Assert-That "H4 $($case.Label): no dev-log written" ((Get-ProbeLineCount $changelog) -eq $clBefore) "changelog grew"
    }

    # --- I (S1072): a full call records exactly what it was told - no derivation anywhere. ---
    Write-Host "I: stated area/name/flavors land verbatim in the record" -ForegroundColor Yellow
    $longDesc = 'This description is deliberately far longer than eighty characters so that the old ' +
    'Substring(0,80) derivation would visibly slice it mid-word and land in the name field.'
    $outI = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
        -FuncOp ADD -FuncDesc $longDesc `
        -FeatArea "Diagnostics" -FeatName "Stated name wins" -FeatFlavors "standard,legacy,vr" 2>&1 | Out-String
    $exitI = $LASTEXITCODE
    Assert-That "I1 exit 0" ($exitI -eq 0) "exit=$exitI out=$($outI.Trim())"
    $recI = @(Get-Content -LiteralPath $features | Where-Object { $_ -match '"id":"diagnostics\.stated-name-wins"' })
    Assert-That "I2 id derived from area + stated name" ($recI.Count -eq 1) "records=$($recI.Count)"
    if ($recI.Count -eq 1) {
        $objI = $recI[0] | ConvertFrom-Json
        Assert-That "I3 name is verbatim, not a cut of the description" ($objI.name -eq 'Stated name wins') "name=$($objI.name)"
        Assert-That "I4 area is verbatim, not 'General'" ($objI.area -eq 'Diagnostics') "area=$($objI.area)"
        Assert-That "I5 flavors are verbatim, not ['standard']" (($objI.flavors -join ',') -eq 'standard,legacy,vr') "flavors=$($objI.flavors -join ',')"
    }

    # --- J (S1072): the escape hatch still works - a change that ships no capability needs no facts. ---
    Write-Host "J: -SkipFuncLog still closes without any -Feat* field" -ForegroundColor Yellow
    $clBefore = Get-ProbeLineCount $changelog
    $strayBefore = @(Get-SubjectRecordIds | Where-Object { $_ -notin $probeFeatureIds })
    $outJ = & $pwshExe -NoProfile -File $facade -Id $SubjectId -Status $subjectStatus -StatusOnly -SkipCatalogSync `
        -SkipFuncLog -DevLogs $j4 -FuncOp ADD -FuncDesc "sandbox skipped" 2>&1 | Out-String
    $exitJ = $LASTEXITCODE
    Assert-That "J1 exit 0" ($exitJ -eq 0) "exit=$exitJ out=$($outJ.Trim())"
    Assert-That "J2 dev-log still written" ((Get-ProbeLineCount $changelog) -eq ($clBefore + 1)) "delta=$((Get-ProbeLineCount $changelog) - $clBefore)"
    # The record this call would have invented carries the subject's spec id and an id no probe
    # owns - 'general.<cut of the description>' in the shape S1072 removed.
    $strayAfter = @(Get-SubjectRecordIds | Where-Object { $_ -notin $probeFeatureIds })
    $strayNew = @($strayAfter | Where-Object { $_ -notin $strayBefore })
    Assert-That "J3 no capability recorded" ($strayNew.Count -eq 0) "invented record(s): $($strayNew -join ', ')"
}
finally {
    # Per record, never the whole file (S1521): both files are appended by every parallel
    # session (S1437), so copying a snapshot back would revert whatever a sibling wrote while
    # this suite ran. Read and write sit back to back so the window is one operation wide
    # instead of one run long, and the file is only rewritten when there is a row to drop.
    $rowsRemoved = 0
    if (Test-Path $changelog) {
        # S3022: the lock spans the read AND the write. Holding it for the write alone leaves the
        # same lost update with a smaller window - a row a sibling appends after this snapshot is
        # taken is gone the moment the snapshot is written back.
        Enter-DevLogWriteLock -RepoRoot $repoRoot
        try {
            $rows = [System.IO.File]::ReadAllLines($changelog)
            $keptRows = @($rows | Where-Object { $_ -notmatch $probeRowPattern })
            $rowsRemoved = $rows.Count - $keptRows.Count
            if ($rowsRemoved -gt 0) {
                [System.IO.File]::WriteAllLines($changelog, $keptRows, (New-Object System.Text.UTF8Encoding($false)))
            }
        }
        finally { Exit-DevLogWriteLock }
    }

    $removePs1 = Join-Path $repoRoot 'scripts/all_features/remove.ps1'
    $recordsBefore = @($probeFeatureIds | Where-Object { Test-ProbeRecord $_ })
    foreach ($fid in $recordsBefore) {
        try { & $pwshExe -NoProfile -File $removePs1 -Id $fid -Confirm -Quiet | Out-Null }
        catch { Write-Host "  cleanup: remove.ps1 threw for '$fid' - $_" -ForegroundColor DarkYellow }
    }
    $recordsLeft = @($recordsBefore | Where-Object { Test-ProbeRecord $_ })

    # The whole-file restore was the only thing that hid a failed cleanup, and it announced
    # success either way. A surviving probe sits in the inventory that feeds the release
    # notes, so it has to be loud and carry the command that clears it.
    $residue = @()
    if (Test-Path $changelog) {
        $left = @([System.IO.File]::ReadAllLines($changelog) | Where-Object { $_ -match $probeRowPattern })
        if ($left.Count -gt 0) {
            $residue += "$($left.Count) dev-log row(s) with target '$probeTarget' left in dev/CHANGELOG.md - delete them by hand"
        }
    }
    foreach ($fid in $recordsLeft) {
        $residue += "inventory record '$fid' survived - pwsh -NoProfile -File scripts/all_features/remove.ps1 -Id $fid -Confirm"
    }
    foreach ($line in $residue) { Write-Host "  PROBE RESIDUE  $line" -ForegroundColor Red }
    if ($residue.Count -gt 0) { $script:fail++ }

    $recordsGone = $recordsBefore.Count - $recordsLeft.Count
    Write-Host "probes removed per record: $rowsRemoved dev-log row(s), $recordsGone inventory record(s)" -ForegroundColor DarkGray

    # Released only after cleanup: a waiting instance must not start its own prologue while this
    # run's probe rows and inventory records are still in the files it is about to read.
    if ($suiteMutexHeld) { try { $suiteMutex.ReleaseMutex() } catch { } }
    $suiteMutex.Dispose()
}

Write-Host ""
if ($script:fail -eq 0) {
    Write-Host "close-and-log tests: $script:pass passed" -ForegroundColor Green
    exit 0
}
Write-Host "close-and-log tests: $script:pass passed, $script:fail FAILED" -ForegroundColor Red
exit 1
