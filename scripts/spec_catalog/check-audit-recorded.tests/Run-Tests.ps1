# Run-Tests.ps1 (S2298) - regression suite for scripts/spec_catalog/check-audit-recorded.ps1,
# the closing gate that refuses a transition into Verified when the spec carries no audit verdict.
#
# What broke: nothing checked that `Verified` - which means "the audit passed" - could show the
# audit. Measured 2026-09-01 across every live Verified spec: 7 of 129 carry no `## Last Audit`
# block. The measurement that first surfaced it undercounted, because it searched for the literal
# string `## Last Audit` and live specs number their headings: S2226 carries `## 6. Last Audit` and
# was reported as an unaudited closure it is not. preview.ps1 had the same literal test, so its
# `last_audit_present` flag - which reaches /spec-all step 0a-drift - said false for a spec holding
# the block.
#
# Both halves are asserted, because a gate that only ever refuses is as useless as one that only
# ever passes:
#   * a spec with a plain `## Last Audit` passes,
#   * a spec with the NUMBERED `## 6. Last Audit` passes - the form that defeated the old test,
#   * a spec with no block at all fails,
#   * a heading with an empty body fails - an empty block is the same absence of evidence,
#   * a heading holding only a horizontal rule fails - layout is not a verdict,
#   * a malformed id and an unknown id both exit 2, so "could not look" stays distinct from
#     "looked and refused".
#
# Cases A-C run against REAL spec files, so the suite cannot pass by agreeing with a fixture the
# author wrote to match the code. Cases D-E need spec bodies no live ticket has, so they are
# fixtures under temp/scratch/ named by a snapshot journal - never in PLAN/, which would leave
# junk spec files behind, and never in the production journal, which would burn real spec ids
# (the S1490 / S1534 leak). $env:FMS_SPEC_CATALOG_DIR is SCHEMA.md's supported switch for that.
#
# Nothing here mutates a real ticket: the gate is read-only, and every write lands in the sandbox.
#
# Usage:  pwsh -NoProfile -File scripts/spec_catalog/check-audit-recorded.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.
#   2   the fixtures could not be prepared (no real spec on disk to anchor cases A-C).

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

$gatePs1 = Join-Path $repoRoot 'scripts/spec_catalog/check-audit-recorded.ps1'
$selectPs1 = Join-Path $repoRoot 'scripts/spec_catalog/select.ps1'

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

# ---------------------------------------------------------------------------
# Anchor cases A-C in live data. Each is resolved by SEARCHING the catalog rather than by
# hardcoding an id: a suite pinned to S2226 starts lying the day that ticket is archived.
# ---------------------------------------------------------------------------
$verified = @(& $selectPs1 -Status Verified -Format json | ConvertFrom-Json)
$plainId = $null
$numberedId = $null
$absentId = $null
foreach ($r in $verified) {
    $abs = Join-Path $repoRoot ($r.file -replace '/', [IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path -LiteralPath $abs -PathType Leaf)) { continue }
    $text = Get-Content -LiteralPath $abs -Raw -Encoding UTF8
    if (-not $plainId -and $text -match '(?m)^##\s+Last\s+Audit\b') { $plainId = $r.id; continue }
    if (-not $numberedId -and $text -match '(?m)^##\s+\d+\.\s*Last\s+Audit\b') { $numberedId = $r.id; continue }
    if (-not $absentId -and $text -notmatch '(?im)^#{2,3}\s*(\d+\.\s*)?Last\s+Audit\b') { $absentId = $r.id }
}
if (-not $plainId -or -not $numberedId -or -not $absentId) {
    Write-Host "Cannot anchor the live cases (plain=$plainId numbered=$numberedId absent=$absentId)." -ForegroundColor Yellow
    Write-Host "The catalog no longer holds one Verified spec of each shape - re-point this suite." -ForegroundColor Yellow
    exit 2
}

Write-Host "check-audit-recorded regression suite" -ForegroundColor Cyan

$a = Invoke-Gate $plainId
Assert-That "A. plain '## Last Audit' passes ($plainId)" ($a.Code -eq 0) "exit $($a.Code): $($a.Text)"

$b = Invoke-Gate $numberedId
Assert-That "B. numbered '## N. Last Audit' passes ($numberedId)" ($b.Code -eq 0) "exit $($b.Code): $($b.Text)"

$c = Invoke-Gate $absentId
Assert-That "C. no audit block fails ($absentId)" ($c.Code -eq 1) "exit $($c.Code): $($c.Text)"

# ---------------------------------------------------------------------------
# Fixture cases D-E. Sandbox journal + fixture spec bodies under temp/scratch/.
# ---------------------------------------------------------------------------
$sandboxDir = Join-Path $repoRoot ('temp/scratch/check-audit-recorded-sandbox-{0}' -f $PID)
$fixtureDir = Join-Path $sandboxDir 'specs'
try {
    New-Item -ItemType Directory -Force -Path $fixtureDir | Out-Null
    Copy-Item (Join-Path $repoRoot 'PLAN/spec-catalog.jsonl') (Join-Path $sandboxDir 'spec-catalog.jsonl') -Force
    Copy-Item (Join-Path $repoRoot 'PLAN/spec-catalog-archive.jsonl') (Join-Path $sandboxDir 'spec-catalog-archive.jsonl') -Force

    # Ids from the FIXED reserved block far above the live maximum, never from next-id.ps1: a
    # generated id can collide with one a sibling session is allocating right now (S1490).
    $fixtures = @(
        @{ Id = 'S9995'; Slug = 'audit-heading-empty-body'; Body = "# Fixture`n`n**Status:** Implemented`n`n## Last Audit`n`n## Next section`n`ncontent`n" }
        @{ Id = 'S9996'; Slug = 'audit-heading-rule-only'; Body = "# Fixture`n`n**Status:** Implemented`n`n## Last Audit`n`n---`n" }
    )

    $rows = New-Object System.Collections.Generic.List[string]
    foreach ($f in $fixtures) {
        $relFile = 'temp/scratch/check-audit-recorded-sandbox-{0}/specs/{1}_{2}.md' -f $PID, $f.Id, $f.Slug
        Set-Content -LiteralPath (Join-Path $fixtureDir ("{0}_{1}.md" -f $f.Id, $f.Slug)) -Value $f.Body -Encoding UTF8 -NoNewline
        $rows.Add((@{
            id = $f.Id; name = $f.Slug; status = 'Implemented'; priority = 50
            file = $relFile; created = '2026-09-01'; updated = '2026-09-01 00:00'
        } | ConvertTo-Json -Compress))
    }
    Add-Content -LiteralPath (Join-Path $sandboxDir 'spec-catalog.jsonl') -Value $rows -Encoding UTF8

    $env:FMS_SPEC_CATALOG_DIR = $sandboxDir
    $env:FMS_SKIP_RELEASE_QUEUE = '1'

    $d = Invoke-Gate 'S9995'
    Assert-That "D. heading with empty body fails" ($d.Code -eq 1) "exit $($d.Code): $($d.Text)"

    $e = Invoke-Gate 'S9996'
    Assert-That "E. heading holding only a horizontal rule fails" ($e.Code -eq 1) "exit $($e.Code): $($e.Text)"
}
finally {
    Remove-Item Env:\FMS_SPEC_CATALOG_DIR -ErrorAction SilentlyContinue
    Remove-Item Env:\FMS_SKIP_RELEASE_QUEUE -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $sandboxDir) { Remove-Item -LiteralPath $sandboxDir -Recurse -Force }
}

# The sandbox is gone, so these run against production again - which is the point: the fixture
# rows must not have reached the real journal.
$leak = Invoke-Gate 'S9995'
Assert-That "F. fixture ids absent from the production catalog" ($leak.Code -eq 2) "exit $($leak.Code): $($leak.Text)"

$g = Invoke-Gate 'NOPE'
Assert-That "G. malformed id exits 2, not 1" ($g.Code -eq 2) "exit $($g.Code): $($g.Text)"

Write-Host ""
Write-Host ("passed: {0}  failed: {1}" -f $script:pass, $script:fail) -ForegroundColor Cyan
if ($script:fail -gt 0) { exit 1 }
exit 0
