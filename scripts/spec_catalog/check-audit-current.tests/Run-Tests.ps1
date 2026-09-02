<#
.SYNOPSIS
    Regression suite for check-audit-current.ps1 and the task fingerprint it reads (S2367).

.DESCRIPTION
    The gate refuses a transition into Verified / BlockNeedUserTest when the recorded audit
    judged a different task text than the file carries now. Its whole value depends on the
    fingerprint being blind to what the PIPELINE writes and sensitive to what the OWNER
    writes - a gate that fires on the status flip it guards would be turned off within a day,
    and one that ignores a rewritten goal would guard nothing.

    So both directions are asserted, and the blind cases outnumber the sensitive one:
      * a stamp matching the task passes,
      * rewriting the `**Status:**` / `**Status note:**` / `**Priority:**` header still passes,
        because Sync-SpecHeaderStatus rewrites those on the very transition being gated,
      * rewriting the audit block's own content still passes - it is the audit's output,
      * the `---` rule introducing that block still passes - it arrives with the block, and
        counting it made writing the verdict invalidate the stamp the verdict carries,
      * editing a GOAL fails, which is the case the gate exists for,
      * a block with no stamp fails, and names the value to write,
      * a malformed id and an unknown id exit 2, keeping "could not look" apart from "looked
        and refused".

    Fixtures live under temp/scratch/ with ids from the reserved block, addressed through
    $env:FMS_SPEC_CATALOG_DIR - never PLAN/, which would leave junk spec files behind, and
    never the production journal, which would burn real ids (the S1490 / S1534 leak).

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
} else { 'pwsh' }

$gatePs1 = Join-Path $repoRoot 'scripts/spec_catalog/check-audit-current.ps1'
$stampPs1 = Join-Path $repoRoot 'scripts/spec_catalog/task-fingerprint.ps1'

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name" -ForegroundColor Red
        if ($detail) { Write-Host "        $detail" -ForegroundColor DarkGray }
        $script:fail++
    }
}

function Invoke-Gate([string]$id) {
    $out = & $pwshExe -NoProfile -File $gatePs1 -Id $id 2>&1
    return [pscustomobject]@{ Code = $LASTEXITCODE; Text = ($out -join "`n") }
}

# A spec body in the shape the gate meets in production: header block, task sections, then the
# audit block at the bottom. `$Stamp` is spliced in as /spec-check writes it.
function New-SpecBody {
    param(
        [string] $Goal = 'Ship the widget.',
        [string] $Status = 'Implemented',
        [string] $StatusNote = 'awaiting device pass',
        [string] $Priority = '50',
        [string] $AuditTail = 'PASS 4 - WARN 0 - FAIL 0',
        [string] $Stamp = $null,
        [switch] $Separator
    )
    $lines = @(
        '# Fixture spec',
        '',
        "**Status:** $Status",
        "**Status note:** $StatusNote",
        "**Priority:** $Priority",
        '',
        '## 2. Goals',
        '',
        "1. $Goal",
        '',
        '## 11. Criteria',
        '',
        '1. The widget ships.',
        ''
    )
    if ($Separator) { $lines += @('---', '') }
    $lines += @(
        '## Last Audit',
        '',
        '**Outcome:** Implemented'
    )
    if ($Stamp) { $lines += "**Task fingerprint:** $Stamp" }
    $lines += @('', $AuditTail, '')
    return ($lines -join "`n")
}

$sandboxDir = Join-Path $repoRoot ('temp/scratch/check-audit-current-sandbox-{0}' -f $PID)
$fixtureDir = Join-Path $sandboxDir 'specs'
$prepared = $false
try {
    New-Item -ItemType Directory -Force -Path $fixtureDir | Out-Null
    Copy-Item (Join-Path $repoRoot 'PLAN/spec-catalog.jsonl') (Join-Path $sandboxDir 'spec-catalog.jsonl') -Force
    Copy-Item (Join-Path $repoRoot 'PLAN/spec-catalog-archive.jsonl') (Join-Path $sandboxDir 'spec-catalog-archive.jsonl') -Force

    $ids = @('S9989', 'S9990', 'S9991', 'S9992', 'S9993', 'S9994')
    $rows = New-Object System.Collections.Generic.List[string]
    foreach ($id in $ids) {
        $rel = 'temp/scratch/check-audit-current-sandbox-{0}/specs/{1}_fixture.md' -f $PID, $id
        Set-Content -LiteralPath (Join-Path $fixtureDir ("{0}_fixture.md" -f $id)) -Value (New-SpecBody) -Encoding UTF8
        $rows.Add((@{
            id = $id; name = 'fixture'; status = 'Implemented'; priority = 50
            file = $rel; created = '2026-09-02'; updated = '2026-09-02 00:00'
        } | ConvertTo-Json -Compress))
    }
    Add-Content -LiteralPath (Join-Path $sandboxDir 'spec-catalog.jsonl') -Value $rows -Encoding UTF8
    $prepared = $true

    $env:FMS_SPEC_CATALOG_DIR = $sandboxDir
    $env:FMS_SKIP_RELEASE_QUEUE = '1'

    Write-Host "check-audit-current regression suite" -ForegroundColor Cyan

    function Set-Fixture([string]$id, [string]$body) {
        Set-Content -LiteralPath (Join-Path $fixtureDir ("{0}_fixture.md" -f $id)) -Value $body -Encoding UTF8
    }
    function Get-Stamp([string]$id) {
        $p = Join-Path $fixtureDir ("{0}_fixture.md" -f $id)
        $out = & $pwshExe -NoProfile -File $stampPs1 -Path $p
        return ("$out").Trim()
    }

    # A. stamped against its own task text -> pass.
    $stampA = Get-Stamp 'S9990'
    Set-Fixture 'S9990' (New-SpecBody -Stamp $stampA)
    $a = Invoke-Gate 'S9990'
    Assert-That "A. audit stamping the current task passes" ($a.Code -eq 0) "exit $($a.Code): $($a.Text)"

    # B. the status header moved, which is exactly what the gated transition itself does.
    $stampB = Get-Stamp 'S9991'
    Set-Fixture 'S9991' (New-SpecBody -Stamp $stampB -Status 'Verified' -StatusNote '' -Priority '80')
    $b = Invoke-Gate 'S9991'
    Assert-That "B. status/priority header rewrite does not fire the gate" ($b.Code -eq 0) "exit $($b.Code): $($b.Text)"

    # C. the audit block's own body changed - it is the audit's output, not the task.
    $stampC = Get-Stamp 'S9992'
    Set-Fixture 'S9992' (New-SpecBody -Stamp $stampC -AuditTail 'PASS 9 - WARN 1 - FAIL 0 - rewritten')
    $c = Invoke-Gate 'S9992'
    Assert-That "C. rewriting the audit block does not fire the gate" ($c.Code -eq 0) "exit $($c.Code): $($c.Text)"

    # D. the owner rewrote a goal after the audit - the case the gate exists for.
    $stampD = Get-Stamp 'S9993'
    Set-Fixture 'S9993' (New-SpecBody -Stamp $stampD -Goal 'Ship the widget AND the sidebar.')
    $d = Invoke-Gate 'S9993'
    Assert-That "D. an edited goal fails" ($d.Code -eq 1) "exit $($d.Code): $($d.Text)"
    Assert-That "D2. the refusal names /spec-check as the fix" ($d.Text -match 'spec-check') $d.Text

    # C2. the `---` rule a writer puts between the body and the block arrives WITH the block, so a
    # stamp taken before writing either must survive it. Found live: the first stamp of S2367
    # itself was invalidated by the separator that introduced the very block carrying it.
    $stampF = Get-Stamp 'S9989'
    Set-Fixture 'S9989' (New-SpecBody -Stamp $stampF -Separator)
    $f = Invoke-Gate 'S9989'
    Assert-That "C2. a separator added with the block does not fire the gate" ($f.Code -eq 0) "exit $($f.Code): $($f.Text)"

    # E. block present, never stamped.
    $e = Invoke-Gate 'S9994'
    Assert-That "E. an unstamped audit block fails" ($e.Code -eq 1) "exit $($e.Code): $($e.Text)"
    Assert-That "E2. the refusal prints the value to write" ($e.Text -match 'Task fingerprint:\*{0,2}\s+[0-9a-f]{12}') $e.Text
}
finally {
    Remove-Item Env:\FMS_SPEC_CATALOG_DIR -ErrorAction SilentlyContinue
    Remove-Item Env:\FMS_SKIP_RELEASE_QUEUE -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $sandboxDir) { Remove-Item -LiteralPath $sandboxDir -Recurse -Force }
}

if (-not $prepared) {
    Write-Host "Fixtures could not be prepared." -ForegroundColor Yellow
    exit 2
}

# The sandbox is gone, so this runs against production again - the fixture rows must not have
# reached the real journal.
$leak = Invoke-Gate 'S9990'
Assert-That "F. fixture ids absent from the production catalog" ($leak.Code -eq 2) "exit $($leak.Code): $($leak.Text)"

$g = Invoke-Gate 'NOPE'
Assert-That "G. malformed id exits 2, not 1" ($g.Code -eq 2) "exit $($g.Code): $($g.Text)"

Write-Host ""
Write-Host ("passed: {0}  failed: {1}" -f $script:pass, $script:fail) -ForegroundColor Cyan
if ($script:fail -gt 0) { exit 1 }
exit 0
