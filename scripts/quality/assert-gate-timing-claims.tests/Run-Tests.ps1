#requires -Version 7.0
# Subject: scripts/quality/assert-gate-timing-claims.ps1
<#
.SYNOPSIS
    Regression suite for assert-gate-timing-claims.ps1 (S2453).

.DESCRIPTION
    The gate's whole value is in the branch it takes, and the branches disagree about what the same
    numbers mean: a drift is fatal, a pessimistic figure is advisory, a reworded row is "could not
    verify" and an absent journal is not a finding at all. So every branch is executed here against
    a fixture document and a fixture journal, and each case asserts the EXIT CODE, not the wording.

    Fixtures live under temp/scratch/ and are removed afterwards. The real journal is never read:
    a suite that judged live telemetry would pass or fail by whatever ran on the host that hour,
    which is the opposite of a contract.

.NOTES
    Exit codes:
      0   all cases pass.
      1   at least one case failed.
      2   the fixtures could not be prepared.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else { 'pwsh' }

$gatePs1 = Join-Path $repoRoot 'scripts/quality/assert-gate-timing-claims.ps1'

$script:pass = 0
$script:fail = 0

function Assert-That([string]$Name, [bool]$Ok, [string]$Detail) {
    if ($Ok) {
        Write-Host "  PASS  $Name" -ForegroundColor Green
        $script:pass++
    }
    else {
        Write-Host "  FAIL  $Name" -ForegroundColor Red
        if ($Detail) { Write-Host "        $Detail" -ForegroundColor DarkGray }
        $script:fail++
    }
}

function Invoke-Gate([string[]]$Arguments) {
    $out = & $pwshExe -NoProfile -File $gatePs1 @Arguments 2>&1
    return [pscustomobject]@{
        Code = [int]$LASTEXITCODE
        Text = (($out | ForEach-Object { [string]$_ }) -join "`n")
    }
}

# Every fixture claim points at the same one-row document, so a case is defined by the seconds in
# the row and by the journal beside it - the two inputs the gate actually compares.
function New-FixtureDoc([string]$Dir, [string]$Seconds, [string]$Verdict) {
    $path = Join-Path $Dir 'FIXTURE.md'
    Set-Content -LiteralPath $path -Encoding utf8NoBOM -Value @(
        '| Target | Wall clock | Verdict |',
        '| --- | ---: | --- |',
        ('| `a.ps1 fg` (fixture) | {0} s | {1} |' -f $Seconds, $Verdict)
    )
    return $path
}

function New-FixtureClaims([string]$Dir) {
    $path = Join-Path $Dir 'claims.json'
    $claims = [ordered]@{
        claims = @(
            [ordered]@{
                id      = 'fixture-batch'
                doc     = 'FIXTURE.md'
                pattern = '^\|\s*`a\.ps1 fg`[^|]*\|\s*([0-9.]+)\s*s\s*\|\s*(foreground|background)\s*\|'
                runner  = 'fixture-runner'
                gate    = '(batch)'
                note    = 'fixture'
            }
        )
    }
    Set-Content -LiteralPath $path -Encoding utf8NoBOM -Value ($claims | ConvertTo-Json -Depth 5)
    return $path
}

# A prose sentence rather than a table row: the number is there, the foreground/background word is
# not - which is the shape of CLAUDE.md Rule 6, the copy an agent reads most often.
function New-SentenceClaims([string]$Dir, [string]$Name, [bool]$WithVerdict) {
    $path = Join-Path $Dir $Name
    $claim = [ordered]@{
        id      = 'fixture-sentence'
        doc     = 'SENTENCE.md'
        pattern = '`a\.ps1 fg` measures ([0-9.]+) s'
        runner  = 'fixture-runner'
        gate    = '(batch)'
    }
    if ($WithVerdict) { $claim['verdict'] = 'foreground' }
    Set-Content -LiteralPath $path -Encoding utf8NoBOM -Value (([ordered]@{ claims = @($claim) }) | ConvertTo-Json -Depth 5)
    return $path
}

function New-FixtureJournal([string]$Dir, [string]$Name, [int[]]$ElapsedMs, [int]$AgeDays) {
    $path = Join-Path $Dir $Name
    $lines = foreach ($ms in $ElapsedMs) {
        [ordered]@{
            timestampUtc = [DateTime]::UtcNow.AddDays(-$AgeDays).ToString('o')
            runner       = 'fixture-runner'
            gate         = '(batch)'
            status       = 'PASS'
            exitCode     = 0
            elapsedMs    = $ms
        } | ConvertTo-Json -Compress
    }
    Set-Content -LiteralPath $path -Encoding utf8NoBOM -Value $lines
    return $path
}

Write-Host 'assert-gate-timing-claims regression suite' -ForegroundColor Cyan

$sandbox = Join-Path $repoRoot ('temp/scratch/assert-gate-timing-claims-sandbox-{0}' -f $PID)
$prepared = $false
try {
    New-Item -ItemType Directory -Force -Path $sandbox | Out-Null
    $claimsPath = New-FixtureClaims -Dir $sandbox
    # Six runs a little above and below 20 000 ms: median 20 000 ms against the claims below.
    $journalOnTime = New-FixtureJournal -Dir $sandbox -Name 'ontime.jsonl' -ElapsedMs @(18000, 19000, 20000, 20000, 21000, 22000) -AgeDays 1
    $journalStale = New-FixtureJournal -Dir $sandbox -Name 'stale.jsonl' -ElapsedMs @(18000, 19000, 20000, 20000, 21000, 22000) -AgeDays 90
    $journalSlow = New-FixtureJournal -Dir $sandbox -Name 'slow.jsonl' -ElapsedMs @(138000, 140000, 142000, 143000, 145000, 150000) -AgeDays 1
    $prepared = $true

    $base = @('-Gate', '-Claims', $claimsPath, '-DocRoot', $sandbox)

    # A. the prose matches the median -> green.
    [void](New-FixtureDoc -Dir $sandbox -Seconds '20' -Verdict 'foreground')
    $a = Invoke-Gate ($base + @('-TelemetryPath', $journalOnTime))
    Assert-That 'A. a claim matching its telemetry passes' ($a.Code -eq 0) "exit $($a.Code): $($a.Text)"

    # B. the S2453 defect itself: prose ~7x below the measurement.
    [void](New-FixtureDoc -Dir $sandbox -Seconds '3' -Verdict 'foreground')
    $b = Invoke-Gate ($base + @('-TelemetryPath', $journalOnTime))
    Assert-That 'B. a sevenfold drift fails' ($b.Code -eq 1) "exit $($b.Code): $($b.Text)"

    # C. within the 2x tolerance -> still green. The tolerance must be a real band, not decoration.
    [void](New-FixtureDoc -Dir $sandbox -Seconds '12' -Verdict 'foreground')
    $c = Invoke-Gate ($base + @('-TelemetryPath', $journalOnTime))
    Assert-That 'C. drift inside the 2x tolerance passes' ($c.Code -eq 0) "exit $($c.Code): $($c.Text)"

    # D. past the 120 s threshold while the prose still promises foreground. Fatal on its own -
    # the ratio here is only 1.6x, inside the tolerance, and this is the case Rule 6 turns on.
    [void](New-FixtureDoc -Dir $sandbox -Seconds '90' -Verdict 'foreground')
    $d = Invoke-Gate ($base + @('-TelemetryPath', $journalSlow))
    Assert-That 'D. a foreground promise past 120 s fails inside the tolerance' ($d.Code -eq 1) "exit $($d.Code): $($d.Text)"

    # D2. the same measurement against a row that promises background -> not this gate's finding.
    [void](New-FixtureDoc -Dir $sandbox -Seconds '90' -Verdict 'background')
    $d2 = Invoke-Gate ($base + @('-TelemetryPath', $journalSlow))
    Assert-That 'D2. the same runs under a background promise pass' ($d2.Code -eq 0) "exit $($d2.Code): $($d2.Text)"

    # E. the prose is pessimistic: advisory, never fatal.
    [void](New-FixtureDoc -Dir $sandbox -Seconds '90' -Verdict 'background')
    $e = Invoke-Gate ($base + @('-TelemetryPath', $journalOnTime))
    Assert-That 'E. a pessimistic figure is advisory, not fatal' (
        $e.Code -eq 0 -and $e.Text -match 'ADVISORY'
    ) "exit $($e.Code): $($e.Text)"

    # F. every record older than the window -> below the sample floor, so no verdict at all.
    [void](New-FixtureDoc -Dir $sandbox -Seconds '3' -Verdict 'foreground')
    $f = Invoke-Gate ($base + @('-TelemetryPath', $journalStale))
    Assert-That 'F. records outside the window render no verdict' (
        $f.Code -eq 0 -and $f.Text -match 'NO-TELEMETRY'
    ) "exit $($f.Code): $($f.Text)"

    # G. no journal at all - the normal state of a fresh clone, and not a defect.
    $g = Invoke-Gate ($base + @('-TelemetryPath', (Join-Path $sandbox 'absent.jsonl')))
    Assert-That 'G. an absent journal passes and says so' (
        $g.Code -eq 0 -and $g.Text -match 'NO-TELEMETRY'
    ) "exit $($g.Code): $($g.Text)"

    # H. the row was reworded - the anchor is gone, so the gate did not look.
    Set-Content -LiteralPath (Join-Path $sandbox 'FIXTURE.md') -Encoding utf8NoBOM -Value @(
        '| Target | Wall clock | Verdict |',
        '| `a.ps1 fg` (fixture) | quick enough | foreground |'
    )
    $h = Invoke-Gate ($base + @('-TelemetryPath', $journalOnTime))
    Assert-That 'H. a broken anchor exits 2, not 0 or 1' ($h.Code -eq 2) "exit $($h.Code): $($h.Text)"

    # H2. two rows matching one anchor is equally unverifiable - the gate must not pick one.
    Set-Content -LiteralPath (Join-Path $sandbox 'FIXTURE.md') -Encoding utf8NoBOM -Value @(
        '| `a.ps1 fg` (fixture) | 20 s | foreground |',
        '| `a.ps1 fg` (fixture) | 90 s | foreground |'
    )
    $h2 = Invoke-Gate ($base + @('-TelemetryPath', $journalOnTime))
    Assert-That 'H2. an ambiguous anchor exits 2' ($h2.Code -eq 2) "exit $($h2.Code): $($h2.Text)"

    # I. without -Gate the drift of case B is reported and exits 0.
    [void](New-FixtureDoc -Dir $sandbox -Seconds '3' -Verdict 'foreground')
    $i = Invoke-Gate @('-Claims', $claimsPath, '-DocRoot', $sandbox, '-TelemetryPath', $journalOnTime)
    Assert-That 'I. the same drift without -Gate reports and exits 0' (
        $i.Code -eq 0 -and $i.Text -match 'drifted claim'
    ) "exit $($i.Code): $($i.Text)"

    # J. an unreadable claims map is "could not look", never green.
    $brokenClaims = Join-Path $sandbox 'broken.json'
    Set-Content -LiteralPath $brokenClaims -Encoding utf8NoBOM -Value '{ not json'
    $j = Invoke-Gate @('-Gate', '-Claims', $brokenClaims, '-DocRoot', $sandbox, '-TelemetryPath', $journalOnTime)
    Assert-That 'J. an unreadable claims map exits 2' ($j.Code -eq 2) "exit $($j.Code): $($j.Text)"

    # L. a sentence whose verdict is declared on the claim, judged past 120 s -> fatal. The seconds
    # still come out of the prose; only the binary is declared.
    Set-Content -LiteralPath (Join-Path $sandbox 'SENTENCE.md') -Encoding utf8NoBOM -Value @(
        'Below the threshold, do not background: `a.ps1 fg` measures 90 s on a warm daemon.'
    )
    $sentenceClaims = New-SentenceClaims -Dir $sandbox -Name 'sentence.json' -WithVerdict $true
    $l = Invoke-Gate @('-Gate', '-Claims', $sentenceClaims, '-DocRoot', $sandbox, '-TelemetryPath', $journalSlow)
    Assert-That 'L. a declared verdict is honoured past the threshold' ($l.Code -eq 1) "exit $($l.Code): $($l.Text)"

    # M. the same sentence with no verdict anywhere - unjudgeable, so exit 2 rather than a guess.
    $verdictlessClaims = New-SentenceClaims -Dir $sandbox -Name 'verdictless.json' -WithVerdict $false
    $m = Invoke-Gate @('-Gate', '-Claims', $verdictlessClaims, '-DocRoot', $sandbox, '-TelemetryPath', $journalSlow)
    Assert-That 'M. a claim stating no verdict at all exits 2' ($m.Code -eq 2) "exit $($m.Code): $($m.Text)"

    # K. the live claims map anchors on the live documents. This is the one case that reads the
    # repository, and it is why a reworded row cannot land unnoticed: it asserts the anchor, not a
    # timing, so the host's own load never enters the verdict.
    $k = Invoke-Gate @('-TelemetryPath', (Join-Path $sandbox 'absent.jsonl'))
    Assert-That 'K. the shipped claims map still anchors on its documents' ($k.Code -eq 0) "exit $($k.Code): $($k.Text)"
}
catch {
    Write-Host "  fixture error: $($_.Exception.Message)" -ForegroundColor Yellow
}
finally {
    if (Test-Path -LiteralPath $sandbox) { Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue }
}

if (-not $prepared) {
    Write-Host 'Fixtures could not be prepared.' -ForegroundColor Yellow
    exit 2
}

Write-Host ''
Write-Host ("passed: {0}  failed: {1}" -f $script:pass, $script:fail) -ForegroundColor Cyan
if ($script:fail -gt 0) {
    Write-Host 'assert-gate-timing-claims suite: FAIL' -ForegroundColor Red
    exit 1
}
exit 0
