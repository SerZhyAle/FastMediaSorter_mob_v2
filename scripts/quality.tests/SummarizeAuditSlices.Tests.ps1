#requires -Version 7.0
<#
.SYNOPSIS
    S3556: contract suite for scripts/quality/summarize-audit-slices.ps1.

.DESCRIPTION
    The roll-up's exit code is what the campaign's umbrella ticket closes on, so it is demonstrated
    on a fixture catalog with slices in different statuses and an uncovered file seeded on purpose:
    an open campaign exits 3 with the right table, totals and coverage; a P1 whose action names a
    ticket the catalog does not know is listed as "without action"; the Markdown and JSON forms
    carry the same numbers; a foreign parent is a cannot-verify; and once every slice is Verified,
    the tree is covered and the P1 has a real ticket, the same manifest exits 0.

    The fixture holds the repository's profile and the catalog forwarders so the real harness
    allocates ids; child specs are written by hand and one journal row is flipped in place, as the
    capture-draft suite does - the transition scripts are not part of this fixture.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every test passed.
      1  at least one test failed.
      2  cannot verify - the subject script or a fixture source is missing.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:pass = 0
$script:fail = 0

function Test-Case([string]$Name, [scriptblock]$Body) {
    try {
        & $Body
        $script:pass++
        Write-Host "  PASS  $Name" -ForegroundColor Green
    }
    catch {
        $script:fail++
        Write-Host "  FAIL  $Name - $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Assert-Equal($Expected, $Actual, [string]$What) {
    if ($Expected -ne $Actual) { throw "$What - expected: $Expected | actual: $Actual" }
}

function Assert-True([bool]$Condition, [string]$What) {
    if (-not $Condition) { throw "$What - expected: true | actual: false" }
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$subject = Join-Path $repoRoot 'scripts/quality/summarize-audit-slices.ps1'
$sources = @(
    '.sza-profile.json',
    'scripts/spec_catalog/_lib.ps1',
    'scripts/spec_catalog/insert.ps1',
    'scripts/spec_catalog/select.ps1'
)
foreach ($rel in @('scripts/quality/summarize-audit-slices.ps1') + $sources) {
    if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $rel))) { Write-Host "cannot verify: $rel is missing."; exit 2 }
}
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
$utf8 = [System.Text.UTF8Encoding]::new($false)

$fixture = Join-Path $repoRoot "temp/scratch/summarize-audit-slices-tests-$PID"
$journal = Join-Path $fixture 'PLAN/spec-catalog.jsonl'
$manifestPath = Join-Path $fixture 'manifest.json'
$parent = 'S0100'
$pkg = 'app_v2/src/main/java/com/sza/fastmediasorter/x'
$sliceA = 'audit-slice-01-app-x-a'
$sliceB = 'audit-slice-02-app-x-b'

function Set-FixtureFile([string]$Rel, [string[]]$Lines) {
    $full = Join-Path $fixture ($Rel -replace '/', [IO.Path]::DirectorySeparatorChar)
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $full) | Out-Null
    [IO.File]::WriteAllText($full, (($Lines -join "`n") + "`n"), $utf8)
}

function Invoke-Insert([string]$Name) {
    $out = & $pwshExe -NoProfile -NonInteractive -File (Join-Path $fixture 'scripts/spec_catalog/insert.ps1') -Name $Name -Slug $Name -Status Tactical -Tier 3 -Priority 50 2>&1 | Out-String
    $id = [regex]::Match($out, 'S\d{4}(?=\s*$)').Value
    if ($LASTEXITCODE -ne 0 -or -not $id) { throw "fixture insert of $Name failed: $($out.Trim())" }
    return $id
}

function Write-Manifest([string[]]$FilesA, [string[]]$FilesB) {
    $toFiles = { param($paths) @($paths | ForEach-Object { [ordered]@{ path = $_; loc = 10; signals = 0; lint = 0; detekt = 0 } }) }
    $manifest = [ordered]@{
        schema = 'audit-slices/1'; parent = $parent; generatedAt = '2026-09-25T00:00:00Z'
        params = [ordered]@{ maxFiles = 40; maxLoc = 8000; includeTests = $false; includeDebug = $false; fileList = '' }
        tree = [ordered]@{ files = ($FilesA.Count + $FilesB.Count); loc = 10 * ($FilesA.Count + $FilesB.Count) }
        slices = @(
            [ordered]@{ index = 1; name = $sliceA; title = 'app_v2/main x'; module = 'app_v2'; sourceSet = 'main'; packages = @('com/sza/fastmediasorter/x'); fileCount = $FilesA.Count; loc = 10 * $FilesA.Count; signals = [ordered]@{}; risk = 1.0; lint = 0; detekt = 0; detektAmbiguous = $false; siblings = @($sliceB); files = (& $toFiles $FilesA) },
            [ordered]@{ index = 2; name = $sliceB; title = 'app_v2/main x'; module = 'app_v2'; sourceSet = 'main'; packages = @('com/sza/fastmediasorter/x'); fileCount = $FilesB.Count; loc = 10 * $FilesB.Count; signals = [ordered]@{}; risk = 0.5; lint = 0; detekt = 0; detektAmbiguous = $false; siblings = @($sliceA); files = (& $toFiles $FilesB) }
        )
    }
    [IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 8), $utf8)
}

function Set-JournalStatus([string]$Id, [string]$Status) {
    $lines = @(Get-Content -LiteralPath $journal)
    $out = foreach ($l in $lines) { if ($l -match "`"id`":`"$Id`"") { $l -replace '"status":"[^"]+"', "`"status`":`"$Status`"" } else { $l } }
    [IO.File]::WriteAllText($journal, (($out -join "`n") + "`n"), $utf8)
}

function Invoke-Summarize([string[]]$Extra = @(), [string]$ParentArg = $parent) {
    $output = & $pwshExe -NoProfile -NonInteractive -File $subject -Manifest $manifestPath -Parent $ParentArg -RepoRoot $fixture @Extra 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = [int]$LASTEXITCODE; Output = $output }
}

function Reset-Fixture {
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
    foreach ($rel in $sources) {
        $dest = Join-Path $fixture $rel
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dest) | Out-Null
        Copy-Item -LiteralPath (Join-Path $repoRoot $rel) -Destination $dest
    }
    New-Item -ItemType Directory -Force -Path (Join-Path $fixture 'PLAN'), (Join-Path $fixture 'dev'), (Join-Path $fixture 'temp') | Out-Null
    Set-Content -LiteralPath $journal -Value '' -NoNewline
    Set-Content -LiteralPath (Join-Path $fixture 'PLAN/spec-catalog-archive.jsonl') -Value '' -NoNewline
    foreach ($n in @('A', 'B', 'C', 'D')) { Set-FixtureFile "$pkg/$n.kt" @('package fixture.x', "val $n = 1") }
    # The manifest covers A, B and C; D is the uncovered file until the closing case adds it.
    Write-Manifest @("$pkg/A.kt", "$pkg/B.kt") @("$pkg/C.kt")

    $script:idA = Invoke-Insert $sliceA
    $script:idB = Invoke-Insert $sliceB
    Set-FixtureFile "PLAN/${script:idA}_$sliceA.md" @(
        "# Спецификация (audit slice): $script:idA - test",
        '',
        "**Ticket:** $script:idA",
        '**Status:** Verified',
        '',
        '## Last Audit',
        '',
        '**Date:** 2026-09-25',
        '**Mode:** slice',
        '',
        '### Findings',
        '',
        "- P1 · $pkg/A.kt:12 · L2 · read-modify-write on a shared counter · evidence: static review · action: S9999",
        "- P2 · $pkg/A.kt:30 · L3 · Closeable without use · evidence: static review · action: inline",
        "- P2 · $pkg/B.kt:5 · L1 · boolean trap parameter · evidence: static review · action: note",
        "- P3 · $pkg/B.kt:9 · L1 · dead comment · evidence: static review · action: inline",
        '',
        '**Findings:** P0 0 · P1 1 · P2 2 · P3 3',
        '**Spawned:** S9999',
        '**Inline fixes:** 2'
    )
    Set-JournalStatus $script:idA 'Verified'
    Set-FixtureFile "PLAN/${script:idB}_$sliceB.md" @(
        "# Спецификация (audit slice): $script:idB - test",
        '',
        "**Ticket:** $script:idB",
        '**Status:** Tactical'
    )
}

try {
    Test-Case 'an open campaign exits 3 with the table, the totals and one uncovered file' {
        Reset-Fixture
        $r = Invoke-Summarize
        Assert-Equal 3 $r.ExitCode "exit code: $($r.Output.Trim())"
        Assert-True ($r.Output -match "\| 1 \| $script:idA \| $sliceA \| Verified \| 0 \| 1 \| 2 \| 3 \| 2 \| S9999 \|") 'row A'
        Assert-True ($r.Output -match "\| 2 \| $script:idB \| $sliceB \| Tactical \| 0 \| 0 \| 0 \| 0 \| 0 \| - \|") 'row B'
        Assert-True ($r.Output -match 'Slices: 2 - closed 1, open 1') 'slice totals'
        Assert-True ($r.Output -match 'Findings: P0 0 · P1 1 · P2 2 · P3 3 · inline fixes 2') 'finding totals'
        Assert-True ($r.Output -match 'Coverage: 1 uncovered, 0 removed, 0 duplicated') 'coverage line'
        Assert-True ($r.Output -match "uncovered: $pkg/D.kt") 'uncovered file named'
        Assert-True ($r.Output -match 'summarize-audit-slices: campaign OPEN') 'verdict word'
    }

    Test-Case 'a P1 whose action names a ticket the catalog does not know is listed without action' {
        $r = Invoke-Summarize
        Assert-True ($r.Output -match 'P0/P1 without action: 1') 'count line'
        Assert-True ($r.Output -match "$script:idA $pkg/A.kt:12 P1 action: S9999") 'the line is named'
        Assert-True ($r.Output -match 'Spawned tickets: 1 \(unknown 1\)') 'spawned status unknown'
    }

    Test-Case '-OutMarkdown writes the report under a campaign heading' {
        $md = Join-Path $fixture 'rollup.md'
        $r = Invoke-Summarize @('-OutMarkdown', $md, '-Quiet')
        Assert-Equal 3 $r.ExitCode 'exit code'
        $text = [IO.File]::ReadAllText($md, [System.Text.Encoding]::UTF8)
        Assert-True ($text.StartsWith("# Campaign roll-up - $parent")) 'heading'
        Assert-True ($text.Contains('Coverage: 1 uncovered, 0 removed, 0 duplicated')) 'coverage in markdown'
        Assert-Equal 0 ([regex]::Matches($r.Output, '(?m)^\|')).Count 'quiet output carries no table rows'
    }

    Test-Case '-Json carries the same numbers as the table' {
        $r = Invoke-Summarize @('-Json')
        Assert-Equal 3 $r.ExitCode 'exit code'
        $o = $r.Output | ConvertFrom-Json
        Assert-Equal 2 ([int]$o.slices) 'slices'
        Assert-Equal 1 ([int]$o.open) 'open'
        Assert-Equal 1 ([int]$o.closed) 'closed'
        Assert-Equal 1 ([int]$o.uncovered) 'uncovered'
        Assert-Equal 1 ([int]$o.p0p1WithoutAction) 'without action'
        Assert-Equal 'OPEN' ([string]$o.verdict) 'verdict'
    }

    Test-Case 'a parent that does not match the manifest is a cannot-verify' {
        $r = Invoke-Summarize -ParentArg 'S0200'
        Assert-Equal 2 $r.ExitCode "exit code: $($r.Output.Trim())"
        Assert-True ($r.Output -match 'does not match') 'refusal names the mismatch'
    }

    Test-Case 'every slice closed, the tree covered and the P1 ticketed - the campaign exits 0' {
        Set-JournalStatus $script:idB 'Verified'
        Write-Manifest @("$pkg/A.kt", "$pkg/B.kt") @("$pkg/C.kt", "$pkg/D.kt")
        $fixId = Invoke-Insert 'bugfix-shared-counter-race'
        $specA = Join-Path $fixture "PLAN/${script:idA}_$sliceA.md"
        $text = [IO.File]::ReadAllText($specA, [System.Text.Encoding]::UTF8).Replace('S9999', $fixId)
        [IO.File]::WriteAllText($specA, $text, $utf8)
        $r = Invoke-Summarize
        Assert-Equal 0 $r.ExitCode "exit code: $($r.Output.Trim())"
        Assert-True ($r.Output -match 'Slices: 2 - closed 2, open 0') 'slice totals'
        Assert-True ($r.Output -match 'Coverage: 0 uncovered, 0 removed, 0 duplicated') 'coverage line'
        Assert-True ($r.Output -match 'P0/P1 without action: none') 'no line without action'
        Assert-True ($r.Output -match 'Spawned tickets: 1 \(Tactical 1\)') 'spawned status resolved'
        Assert-True ($r.Output -match 'summarize-audit-slices: campaign CLOSED') 'verdict word'
    }
}
finally {
    Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ("SummarizeAuditSlices.Tests: {0} passed, {1} failed" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
