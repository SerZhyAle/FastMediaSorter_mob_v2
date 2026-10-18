<#
.SYNOPSIS
    Regression suite for scripts/spec_catalog/archive.ps1 - the one-process batch path (S2400).
.DESCRIPTION
    Pins the contract the release step 12c and every single-id caller rely on:
      A. three ids in one call -> exit 0, three archive rows added, none left active, files moved,
         `ARCHIVED: 3 of 3` printed.
      B. one unknown id among three -> the two good ids archive, exit 1, the bad id is named after
         `FAILED:`.
      C. a single-id call keeps its historical one-line output and prints no summary line.
      D. re-archiving an id already in the archive journal (leftover file) replaces its row - the
         row count does not grow.
      E. after the sandbox is gone the fixture ids are absent from the production catalog.
      F. an id whose strategic artefact is absent -> exit 1, named after `FAILED:`, still active,
         no archive row written (S2617).
      G. the same id with -AllowMissingArtefact -> exit 0, archived, named after `NO ARTEFACT:`.
      H. a batch of one healthy and one artefact-less id -> `ARCHIVED: 1 of 2`, exit 1.
      I. an already-Archived record with no strategic file still carries its leftover tactical
         folder across - the S2617 refusal must not strand a partial move.
      J. S4270 - with probes.removeOnExit true a record archived out of BlockNeedUserTest loses its
         probes under a Code lock, a record in any other status keeps them, a failing remover
         refuses that id; with the key false no probe is touched.

    Cases F-I need the S2617 refusal in the RESOLVED harness, which is the plugin cache unless
    $env:SZA_HARNESS_ROOT points elsewhere. While a canon edit sits undeployed the four SKIP with a
    named reason instead of failing: only the owner can deploy, so a red verdict here would paint
    every parallel session over an action none of them can perform.

    Hermetic where it can be: both journals live under temp/S2400/archive-tests/<run>/ through
    $env:FMS_SPEC_CATALOG_DIR, and the archive FOLDER resolves beside the archive journal, so
    nothing lands in PLAN/archive/; $env:FMS_SKIP_RELEASE_QUEUE keeps the release files unwritten.
    The fixture .md files themselves sit in the real PLAN/ for the seconds the suite runs, because
    Assert-Record only accepts a `PLAN/` file path and _lib.ps1 deliberately does not redirect the
    repo root (S1534) - every one is removed in the finally block. Ids S9968-S9979 are never live.

    Every fixture is consumed by exactly one case: a case that archives an id burns it, so a later
    case cannot reuse it as its healthy partner - it would meet `already Archived` instead. Case H
    got S9973, which case B had already archived, and read a correct refusal as a product defect.

    Exit codes:
      0  all cases pass.
      1  at least one case failed.
      2  the fixtures could not be prepared.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }
$archivePs1 = Join-Path $repoRoot 'scripts/spec_catalog/archive.ps1'
$selectPs1  = Join-Path $repoRoot 'scripts/spec_catalog/select.ps1'

$script:pass = 0
$script:fail = 0
$script:skip = 0
function Assert-That([string]$name, [bool]$condition, [string]$detail = '') {
    if ($condition) { $script:pass++; Write-Host "  PASS  $name" -ForegroundColor Green }
    else { $script:fail++; Write-Host "  FAIL  $name`n        $detail" -ForegroundColor Red }
}

function Invoke-Archive([string[]]$idArgs, [string[]]$extra = @()) {
    $argv = @('-NoProfile', '-File', $archivePs1, '-Id', ($idArgs -join ',')) + $extra
    $out = & $pwshExe @argv 2>&1 | Out-String
    return [pscustomobject]@{ Code = $LASTEXITCODE; Text = $out }
}

# S2617: ask the RESOLVED harness whether it carries the switch, rather than restating the
# forwarder's search order. A binding refusal names the parameter, and that message is the same
# whichever candidate the forwarder picked - so this probes the file that will actually run.
function Test-RefusalDeployed {
    $probe = Invoke-Archive @('S9999') @('-AllowMissingArtefact')
    return ($probe.Text -notmatch "parameter name '?AllowMissingArtefact'?")
}

$runId = [guid]::NewGuid().ToString('N').Substring(0, 8)
$sandboxRel = "temp/S2400/archive-tests/$runId"
$sandboxDir = Join-Path $repoRoot $sandboxRel
$planDir = Join-Path $repoRoot 'PLAN'
$prepared = $false

function New-Fixture([string]$id, [string]$status) {
    $name = "s2400-fixture-$($id.ToLower())"
    $rel = "PLAN/${id}_$name.md"
    $body = "# $id fixture`n`n**Status:** $status`n`nBody.`n"
    Set-Content -LiteralPath (Join-Path $repoRoot $rel) -Value $body -Encoding UTF8
    return ('{{"id":"{0}","name":"{1}","status":"{2}","priority":50,"file":"{3}","created":"2026-09-02","updated":"2026-09-02 12:00"}}' -f $id, $name, $status, $rel)
}

function Read-Rows([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) { return @() }
    return @(Get-Content -LiteralPath $path -Encoding UTF8 | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json })
}

try {
    New-Item -ItemType Directory -Path $sandboxDir -Force | Out-Null
    $activePath  = Join-Path $sandboxDir 'spec-catalog.jsonl'
    $archivePath = Join-Path $sandboxDir 'spec-catalog-archive.jsonl'
    $rows = @(
        (New-Fixture 'S9970' 'Verified'),
        (New-Fixture 'S9971' 'Implemented'),
        (New-Fixture 'S9972' 'BlockNeedUserTest'),
        (New-Fixture 'S9973' 'Verified'),
        (New-Fixture 'S9974' 'Verified'),
        (New-Fixture 'S9975' 'Verified'),
        (New-Fixture 'S9976' 'Verified'),
        (New-Fixture 'S9977' 'Verified'),
        (New-Fixture 'S9978' 'Verified'),
        (New-Fixture 'S9968' 'Verified')
    )
    Set-Content -LiteralPath $activePath -Value $rows -Encoding UTF8
    # One unrelated archived row so "rows added" is judged against a non-empty journal, plus the
    # S2617 case-I fixture: already Archived, its archive-side file absent, one tactical folder left.
    Set-Content -LiteralPath $archivePath -Value @(
        '{"id":"S9969","name":"s2400-fixture-seed","status":"Archived","priority":0,"file":"PLAN/archive/S9969_s2400-fixture-seed.md","created":"2026-09-01","updated":"2026-09-01 12:00"}',
        '{"id":"S9979","name":"s2400-fixture-s9979","status":"Archived","priority":0,"file":"PLAN/archive/S9979_s2400-fixture-s9979.md","created":"2026-09-01","updated":"2026-09-01 12:00"}'
    ) -Encoding UTF8
    $case9979Dir = Join-Path $planDir 'S9979_s2400-fixture-s9979'
    New-Item -ItemType Directory -Path $case9979Dir -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $case9979Dir 'INDEX.md') -Value "# leftover tactical folder`n" -Encoding UTF8
    $prepared = $true

    $env:FMS_SPEC_CATALOG_DIR = $sandboxDir
    $env:FMS_SKIP_RELEASE_QUEUE = '1'

    Write-Host "archive.ps1 regression suite (sandbox $sandboxRel)" -ForegroundColor Cyan

    # A. three ids, one call.
    $a = Invoke-Archive @('S9970', 'S9971', 'S9972')
    Assert-That "A1. batch of three exits 0" ($a.Code -eq 0) "exit $($a.Code): $($a.Text)"
    Assert-That "A2. summary line names 3 of 3" ($a.Text -match 'ARCHIVED: 3 of 3') $a.Text
    $arch = Read-Rows $archivePath
    $act  = Read-Rows $activePath
    Assert-That "A3. archive journal gained exactly three rows" ($arch.Count -eq 5) "rows: $($arch.Count)"
    Assert-That "A4. archived rows carry Archived / priority 0" (@($arch | Where-Object { $_.id -in 'S9970','S9971','S9972' -and $_.status -eq 'Archived' -and $_.priority -eq 0 }).Count -eq 3) ($arch | ConvertTo-Json -Compress)
    Assert-That "A5. active journal no longer holds them" (@($act | Where-Object { $_.id -in 'S9970','S9971','S9972' }).Count -eq 0) ($act.id -join ',')
    Assert-That "A6. active journal keeps every untouched fixture" ($act.Count -eq 7) "rows: $($act.Count)"
    $movedOk = (Test-Path (Join-Path $sandboxDir 'archive/S9970_s2400-fixture-s9970.md')) -and -not (Test-Path (Join-Path $planDir 'S9970_s2400-fixture-s9970.md'))
    Assert-That "A7. spec file moved into the sandbox archive folder" $movedOk
    Assert-That "A8. nothing landed in the production PLAN/archive" (-not (Test-Path (Join-Path $repoRoot 'PLAN/archive/S9970_s2400-fixture-s9970.md')))
    $movedHeader = Get-Content -LiteralPath (Join-Path $sandboxDir 'archive/S9970_s2400-fixture-s9970.md') -Raw
    Assert-That "A9. moved file header reads Archived" ($movedHeader -match '\*\*Status:\*\* Archived') $movedHeader

    # B. one unknown id among three.
    $b = Invoke-Archive @('S9973', 'S9999', 'S9974')
    Assert-That "B1. a bad id makes the call exit 1" ($b.Code -eq 1) "exit $($b.Code): $($b.Text)"
    Assert-That "B2. the bad id is named after FAILED:" ($b.Text -match 'ARCHIVED: 2 of 3 \| FAILED: S9999') $b.Text
    $arch = Read-Rows $archivePath
    Assert-That "B3. the two good ids still archived" (@($arch | Where-Object { $_.id -in 'S9973','S9974' }).Count -eq 2) ($arch.id -join ',')

    # C. single id keeps its one-line output.
    $c = Invoke-Archive @('S9975')
    Assert-That "C1. single id exits 0" ($c.Code -eq 0) "exit $($c.Code): $($c.Text)"
    Assert-That "C2. historical line shape kept" ($c.Text -match 'S9975 archived \[priority -> 0\]\. Moved: S9975_s2400-fixture-s9975\.md ->') $c.Text
    Assert-That "C3. no summary line for a single id" ($c.Text -notmatch 'ARCHIVED:') $c.Text

    # D. re-archive an id whose row is already in the archive and whose file was left behind.
    $before = (Read-Rows $archivePath).Count
    $stray = Join-Path $planDir 'S9975_s2400-fixture-s9975.md'
    Set-Content -LiteralPath $stray -Value "# stray`n`n**Status:** Verified`n" -Encoding UTF8
    $d = Invoke-Archive @('S9975')
    $after = (Read-Rows $archivePath).Count
    Assert-That "D1. re-archive with a leftover file exits 0" ($d.Code -eq 0) "exit $($d.Code): $($d.Text)"
    Assert-That "D2. the archive row is replaced, not duplicated" ($after -eq $before) "before $before after $after"

    # D3. an id already archived with nothing left to move is a failure, not a silent pass.
    $d3 = Invoke-Archive @('S9975')
    Assert-That "D3. nothing-left-to-move exits 1 and says so" ($d3.Code -eq 1 -and $d3.Text -match 'already Archived') "exit $($d3.Code): $($d3.Text)"

    # F-I. S2617 - the missing-artefact refusal and its exit.
    # An if/else, never an early return: a return inside this try leaves the trailing `exit`
    # unreached, so a run that had already failed an earlier case would end in a silent exit 0.
    if (-not (Test-RefusalDeployed)) {
        $script:skip += 4
        Write-Host "  SKIP  S2617 missing-artefact refusal (cases F-I) - the resolved harness predates the fix; re-run with SZA_HARNESS_ROOT pointed at the canon checkout" -ForegroundColor Yellow
    }
    else {
        # The artefact is gone by the time archive.ps1 looks - exactly the production shape.
        Remove-Item -LiteralPath (Join-Path $planDir 'S9976_s2400-fixture-s9976.md') -Force
        Remove-Item -LiteralPath (Join-Path $planDir 'S9978_s2400-fixture-s9978.md') -Force

        # F. refuse, and leave the record exactly where it was.
        $rowsBeforeF = (Read-Rows $archivePath).Count
        $f = Invoke-Archive @('S9976')
        $actF = Read-Rows $activePath
        Assert-That "F1. a missing artefact makes the call exit 1" ($f.Code -eq 1) "exit $($f.Code): $($f.Text)"
        # The probed path is asserted literally, not just the sentence around it: the refusal exists
        # to tell the operator WHERE the text was looked for, and an assertion that reads only the
        # prefix stays green after an edit that drops the path. This fixture's record path and its
        # fallback are the same string, which archive.ps1 deduplicates on purpose.
        Assert-That "F2. the refusal names the probed path and all three exits" `
            ($f.Text -match 'strategic artefact not found' -and $f.Text -match [regex]::Escape("'PLAN/S9976_s2400-fixture-s9976.md'") `
                -and $f.Text -match 'update\.ps1' -and $f.Text -match 'delete\.ps1' -and $f.Text -match '-AllowMissingArtefact') $f.Text
        Assert-That "F3. the record stays in the active journal" (@($actF | Where-Object { $_.id -eq 'S9976' }).Count -eq 1) ($actF.id -join ',')
        Assert-That "F4. no archive row was written for it" ((Read-Rows $archivePath).Count -eq $rowsBeforeF) "before $rowsBeforeF after $((Read-Rows $archivePath).Count)"

        # G. the named exit archives it, and the summary says so out loud.
        $g = Invoke-Archive @('S9976', 'S9977') @('-AllowMissingArtefact')
        $archG = Read-Rows $archivePath
        Assert-That "G1. -AllowMissingArtefact exits 0" ($g.Code -eq 0) "exit $($g.Code): $($g.Text)"
        Assert-That "G2. the id is named after NO ARTEFACT:" ($g.Text -match 'ARCHIVED: 2 of 2 \| NO ARTEFACT: S9976') $g.Text
        Assert-That "G3. the archive row exists and keeps its original path" `
            (@($archG | Where-Object { $_.id -eq 'S9976' -and $_.file -eq 'PLAN/S9976_s2400-fixture-s9976.md' }).Count -eq 1) ($archG | ConvertTo-Json -Compress)

        # H. one bad id does not take the healthy one down with it. S9968 is untouched by every
        # earlier case: an id already archived would answer `already Archived`, which is a second
        # refusal and would make this case pass or fail for the wrong reason.
        $h = Invoke-Archive @('S9978', 'S9968')
        Assert-That "H1. the batch exits 1 and archives the healthy id" ($h.Code -eq 1 -and $h.Text -match 'ARCHIVED: 1 of 2') "exit $($h.Code): $($h.Text)"
        Assert-That "H2. only the artefact-less id is named after FAILED:" ($h.Text -match 'FAILED: S9978' -and $h.Text -notmatch 'FAILED:.*S9968') $h.Text

        # I. the refusal must not strand a partial move: already Archived, no strategic file, one
        # tactical folder still in PLAN/.
        $i = Invoke-Archive @('S9979')
        $movedDir = Join-Path $sandboxDir 'archive/S9979_s2400-fixture-s9979'
        Assert-That "I1. an already-Archived record with leftovers exits 0" ($i.Code -eq 0) "exit $($i.Code): $($i.Text)"
        Assert-That "I2. its tactical folder still moves" ((Test-Path -LiteralPath $movedDir) -and -not (Test-Path -LiteralPath $case9979Dir)) "moved: $(Test-Path -LiteralPath $movedDir)"
    }
}
finally {
    Remove-Item Env:\FMS_SPEC_CATALOG_DIR -ErrorAction SilentlyContinue
    Remove-Item Env:\FMS_SKIP_RELEASE_QUEUE -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $sandboxDir) { Remove-Item -LiteralPath $sandboxDir -Recurse -Force }
    # `S99??` and not `S997?`: the fixture range grew past S997x, and a sweep narrower than the
    # fixture set leaves a stray .md in the real PLAN/ that the next run inherits.
    Get-ChildItem -LiteralPath $planDir -Filter 'S99??_s2400-fixture-*.md' -File -ErrorAction SilentlyContinue |
        Remove-Item -Force -ErrorAction SilentlyContinue
    # Case I leaves a real directory in PLAN/ when it fails before the move - the file sweep above
    # would walk past it and the next run would inherit a half-set fixture.
    Get-ChildItem -LiteralPath $planDir -Filter 'S99??_s2400-fixture-*' -Directory -ErrorAction SilentlyContinue |
        Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
}

if (-not $prepared) {
    Write-Host "Fixtures could not be prepared." -ForegroundColor Yellow
    exit 2
}

# The sandbox is gone, so this runs against production again - the fixture ids must not have reached it.
$leak = & $pwshExe -NoProfile -File $selectPs1 -Id S9970 -Format json 2>&1 | Out-String
Assert-That "E. fixture id absent from the production catalog" ($LASTEXITCODE -ne 0 -or $leak -notmatch '"S9970"') $leak

# J. S4270. Not in the sandbox above: that one redirects only the journals, while the probe scan and
# the remover judge the PROJECT's source tree, which must never be the live one here. A throwaway
# project (SZA_PROJECT_ROOT) carries its own profile, catalog, sources, remover copy and lock
# directory, and is driven through the harness archive.ps1 directly - the forwarder writes its own
# repository into SZA_PROJECT_ROOT.
. (Join-Path $repoRoot 'scripts/spec_catalog/_status-sets.ps1')
$jArchive = $null
try { $jArchive = Get-SzaHarnessScript 'spec_catalog/archive.ps1' } catch { $jArchive = $null }
$jDeployed = $jArchive -and (Test-Path -LiteralPath $jArchive) -and
    ((Select-String -LiteralPath $jArchive -Pattern 'Invoke-ProbeRemovalOnExit' -SimpleMatch -Quiet) -eq $true)
if (-not $jDeployed) {
    $script:skip += 4
    Write-Host "  SKIP  S4270 probe removal on archive (cases J1-J4) - the resolved harness archive.ps1 predates it" -ForegroundColor Yellow
}
else {
    $jRoot = Join-Path $repoRoot "temp/S2400/archive-tests/$runId-probe"
    $jSrc = Join-Path $jRoot 'app_v2/src/main/java/p'
    $jUtf8 = [System.Text.UTF8Encoding]::new($false)
    $jProfilePath = Join-Path $repoRoot '.sza-profile.json'
    $jLiveRemover = [string]((Get-Content -LiteralPath $jProfilePath -Raw | ConvertFrom-Json).probes.removeCommand)
    # id, status, source file: two parked tickets with a probe each, and a Verified one whose
    # leftover probe archive.ps1 must leave alone - only a BlockNeedUserTest record is its business.
    $jFixtures = @(@('S9980', 'BlockNeedUserTest', 'A'), @('S9981', 'BlockNeedUserTest', 'B'), @('S9982', 'Verified', 'C'))

    function Reset-ProbeProject([bool]$removeOnExit, [string]$removeCommand) {
        if (Test-Path -LiteralPath $jRoot) { Remove-Item -LiteralPath $jRoot -Recurse -Force }
        New-Item -ItemType Directory -Force -Path (Join-Path $jRoot 'PLAN'), (Join-Path $jRoot 'temp'),
            (Join-Path $jRoot 'scripts/quality'), $jSrc | Out-Null
        $tree = Get-Content -LiteralPath $jProfilePath -Raw | ConvertFrom-Json -AsHashtable
        $tree['probes']['removeOnExit'] = $removeOnExit
        $tree['probes']['removeCommand'] = $removeCommand
        [System.IO.File]::WriteAllText((Join-Path $jRoot '.sza-profile.json'), ($tree | ConvertTo-Json -Depth 64), $jUtf8)
        # The remover resolves the repository from its own location, so this copy rewrites only the
        # fixture below it (S2925).
        Copy-Item -LiteralPath (Join-Path $repoRoot 'scripts/quality/remove-ticket-probes.ps1') -Destination (Join-Path $jRoot 'scripts/quality')
        [System.IO.File]::WriteAllText((Join-Path $jRoot 'CLAUDE.md'), "# sandbox`n", $jUtf8)
        $jRows = foreach ($f in $jFixtures) {
            $rel = "PLAN/$($f[0])_probe-fixture.md"
            [System.IO.File]::WriteAllText((Join-Path $jRoot $rel), "# $($f[0])`n`n**Status:** $($f[1])`n", $jUtf8)
            [System.IO.File]::WriteAllText((Join-Path $jSrc "$($f[2]).kt"),
                ("package p`n`nimport timber.log.Timber`n`nfun f$($f[2])() {`n    Timber.d(`"$($f[0]): fixture`")`n" +
                "    Timber.i(`"permanent`")`n}`n"), $jUtf8)
            '{{"id":"{0}","name":"probe-fixture","status":"{1}","priority":50,"file":"{2}","created":"2026-10-15","updated":"2026-10-15 12:00"}}' -f $f[0], $f[1], $rel
        }
        [System.IO.File]::WriteAllLines((Join-Path $jRoot 'PLAN/spec-catalog.jsonl'), [string[]]$jRows, $jUtf8)
    }

    function Invoke-ProbeArchive([string]$ids) {
        $out = @(& $pwshExe -NoProfile -File $jArchive -Id $ids 2>&1 6>&1 | ForEach-Object { [string]$_ }) -join "`n"
        return [pscustomobject]@{
            Code      = $LASTEXITCODE
            Text      = $out
            LocksLeft = @(Get-ChildItem -LiteralPath (Join-Path $jRoot 'temp') -Filter '*.LOCK' -File).Count
        }
    }

    function Test-ProbeLeft([string]$file, [string]$id) {
        $text = [System.IO.File]::ReadAllText((Join-Path $jSrc "$file.kt"))
        return $text.Contains(('Timber.d("{0}:' -f $id))
    }

    $jInheritedRoot = $env:SZA_PROJECT_ROOT
    try {
        $env:SZA_PROJECT_ROOT = $jRoot
        $env:FMS_SKIP_RELEASE_QUEUE = '1'

        Reset-ProbeProject $true $jLiveRemover
        $j1 = Invoke-ProbeArchive 'S9980,S9982'
        Assert-That "J1. key on: a parked record is archived with its probe removed under a Code lock it frees" `
            ($j1.Code -eq 0 -and $j1.Text -match 'ARCHIVED: 2 of 2' -and -not (Test-ProbeLeft 'A' 'S9980') -and
            $j1.Text -match 'Code domains acquired: Code\.Phone' -and $j1.LocksLeft -eq 0) "exit $($j1.Code); locks $($j1.LocksLeft): $($j1.Text)"
        Assert-That "J2. key on: a record in any other status keeps its probe" (Test-ProbeLeft 'C' 'S9982') $j1.Text

        Reset-ProbeProject $true 'pwsh -NoProfile -Command exit 7'
        $j3 = Invoke-ProbeArchive 'S9981'
        $j3Active = @(Read-Rows (Join-Path $jRoot 'PLAN/spec-catalog.jsonl') |
            Where-Object { $_.id -eq 'S9981' -and $_.status -eq 'BlockNeedUserTest' }).Count
        Assert-That "J3. a failing remover refuses that id: exit 1, still active, probe kept, lock freed" `
            ($j3.Code -eq 1 -and $j3.Text -match 'probe remover exited 7' -and $j3Active -eq 1 -and
            (Test-ProbeLeft 'B' 'S9981') -and $j3.LocksLeft -eq 0) "exit $($j3.Code); active $j3Active; $($j3.Text)"

        Reset-ProbeProject $false $jLiveRemover
        $j4 = Invoke-ProbeArchive 'S9981'
        Assert-That "J4. key off: the parked record is archived and its probe is not touched" `
            ($j4.Code -eq 0 -and (Test-ProbeLeft 'B' 'S9981')) "exit $($j4.Code); $($j4.Text)"
    }
    finally {
        $env:SZA_PROJECT_ROOT = $jInheritedRoot
        Remove-Item Env:\FMS_SKIP_RELEASE_QUEUE -ErrorAction SilentlyContinue
        if (Test-Path -LiteralPath $jRoot) { Remove-Item -LiteralPath $jRoot -Recurse -Force -ErrorAction SilentlyContinue }
    }
}

# The skip count belongs on the summary line: a run that quietly dropped four cases and a run that
# executed them both end in "0 failed", and only this number tells them apart.
Write-Host "archive.tests: $(if ($script:fail) { 'FAIL' } else { 'PASS' }) (run=$($script:pass + $script:fail) skipped=$script:skip)"
if ($script:fail -gt 0) { exit 1 }
exit 0
