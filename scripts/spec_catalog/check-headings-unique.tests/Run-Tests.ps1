# Run-Tests.ps1 (S2357) - regression suite for scripts/spec_catalog/check-headings-unique.ps1,
# the gate that refuses a gated transition when a spec file carries the same section twice.
#
# What broke: a spec file can hold one section twice, and nothing could see it. The shared parser
# Get-SpecSectionLines stops at the next '## ' once it has entered a section, so all four
# body-reading gates read the FIRST copy. The duplicate therefore does not add noise - it can
# INVERT another gate's verdict. Measured 2026-09-02, S1884's `## Last Audit` block had been split
# in two and check-audit-recorded.ps1 answered PASS on the resulting three-line stub carrying no
# `**Outcome:**`, while the real block with its counts and eight device checks sat below, unread.
#
# Both halves are asserted, because a gate that only ever refuses is as useless as one that only
# ever passes. The false-positive half matters more than usual here: the obvious rule - "the same
# '## ' twice in a file" - is WRONG, because a compact spec holds several `# Phase NN` blocks that
# each legitimately own `## Steps` and its neighbours. On the tree that rule reported six offenders
# of which three were legitimate compact specs, so case B anchors that shape in LIVE data.
#
# Cases A-B run against real spec files, found by SEARCHING the catalog rather than by hardcoding
# an id: a suite pinned to today's ticket starts lying the day that ticket is archived. Cases C-F
# need spec bodies no live ticket has - and must not, since the gate's whole job is that none does
# - so they are fixtures under temp/scratch/ named by a snapshot journal, never in PLAN/ (junk spec
# files) and never in the production journal (burning real spec ids - the S1490 / S1534 leak).
# $env:FMS_SPEC_CATALOG_DIR is SCHEMA.md's supported switch for that.
#
# Nothing here mutates a real ticket: the gate is read-only, and every write lands in the sandbox.
#
# Usage:  pwsh -NoProfile -File scripts/spec_catalog/check-headings-unique.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.
#   2   the fixtures could not be prepared (no live spec on disk to anchor cases A-B).

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

$gatePs1   = Join-Path $repoRoot 'scripts/spec_catalog/check-headings-unique.ps1'
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
# Anchor cases A-B in live data.
#   A - an ordinary spec: one '# ' block, every '## ' distinct.
#   B - a COMPACT spec: several '# Phase NN' blocks repeating '## ' headings across them. This is
#       the shape the naive per-file rule breaks on, so it is anchored live rather than fixtured.
# ---------------------------------------------------------------------------
$records = @(& $selectPs1 -Format json | ConvertFrom-Json)
$plainId = $null
$compactId = $null
foreach ($r in $records) {
    if ($plainId -and $compactId) { break }
    if (-not $r.file) { continue }
    $abs = Join-Path $repoRoot ($r.file -replace '/', [IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path -LiteralPath $abs -PathType Leaf)) { continue }

    $lines = @(Get-Content -LiteralPath $abs -Encoding UTF8)
    $h1 = @($lines | Where-Object { $_ -match '^#\s+\S' }).Count
    $h2 = @($lines | Where-Object { $_ -match '^##\s+\S' } | ForEach-Object { $_.Trim() })
    $h2unique = @($h2 | Select-Object -Unique).Count

    # A tactical folder would drag extra files into the verdict, so anchor on lone spec files only.
    $folder = [System.IO.Path]::ChangeExtension($abs, $null).TrimEnd('.')
    if (Test-Path -LiteralPath $folder -PathType Container) { continue }

    if (-not $compactId -and $h1 -gt 1 -and $h2.Count -gt $h2unique) { $compactId = $r.id; continue }
    if (-not $plainId -and $h1 -eq 1 -and $h2.Count -eq $h2unique -and $h2.Count -ge 3) { $plainId = $r.id }
}
if (-not $plainId -or -not $compactId) {
    Write-Host "Cannot anchor the live cases (plain=$plainId compact=$compactId)." -ForegroundColor Yellow
    Write-Host "The catalog no longer holds one spec of each shape - re-point this suite." -ForegroundColor Yellow
    exit 2
}

Write-Host "check-headings-unique regression suite" -ForegroundColor Cyan

$a = Invoke-Gate $plainId
Assert-That "A. ordinary spec, all headings distinct, passes ($plainId)" ($a.Code -eq 0) "exit $($a.Code): $($a.Text)"

$b = Invoke-Gate $compactId
Assert-That "B. compact spec repeating '## ' across '# Phase NN' passes ($compactId)" ($b.Code -eq 0) "exit $($b.Code): $($b.Text)"

# ---------------------------------------------------------------------------
# Fixture cases C-F. Sandbox journal + fixture spec bodies under temp/scratch/.
# ---------------------------------------------------------------------------
$sandboxDir = Join-Path $repoRoot ('temp/scratch/check-headings-unique-sandbox-{0}' -f $PID)
$fixtureDir = Join-Path $sandboxDir 'specs'
try {
    New-Item -ItemType Directory -Force -Path $fixtureDir | Out-Null
    Copy-Item (Join-Path $repoRoot 'PLAN/spec-catalog.jsonl') (Join-Path $sandboxDir 'spec-catalog.jsonl') -Force
    Copy-Item (Join-Path $repoRoot 'PLAN/spec-catalog-archive.jsonl') (Join-Path $sandboxDir 'spec-catalog-archive.jsonl') -Force

    # Ids from the FIXED reserved block far above the live maximum, never from next-id.ps1: a
    # generated id can collide with one a sibling session is allocating right now (S1490).
    $fence = [string][char]0x60 * 3
    $fixtures = @(
        @{ Id = 'S9990'; Slug = 'headings-duplicate-in-one-block'
           Body = "# Fixture`n`n**Status:** Implemented`n`n## 7. Risks`n`nfirst`n`n## 8. Other`n`nx`n`n## 7. Risks`n`nsecond`n" }
        @{ Id = 'S9991'; Slug = 'headings-repeat-across-phase-blocks'
           Body = "# Fixture`n`n**Status:** Implemented`n`n## Goal`n`nx`n`n# Phase 01 - one`n`n## Steps`n`na`n`n## Phase Done Criteria`n`nb`n`n# Phase 02 - two`n`n## Steps`n`nc`n`n## Phase Done Criteria`n`nd`n" }
        @{ Id = 'S9992'; Slug = 'headings-duplicate-inside-code-fence'
           Body = "# Fixture`n`n**Status:** Implemented`n`n## 7. Risks`n`nreal`n`n## 8. Quoted`n`n$fence`n## 7. Risks`n## 7. Risks`n$fence`n`ntail`n" }
    )

    $rows = New-Object System.Collections.Generic.List[string]
    foreach ($f in $fixtures) {
        $relFile = 'temp/scratch/check-headings-unique-sandbox-{0}/specs/{1}_{2}.md' -f $PID, $f.Id, $f.Slug
        Set-Content -LiteralPath (Join-Path $fixtureDir ("{0}_{1}.md" -f $f.Id, $f.Slug)) -Value $f.Body -Encoding UTF8 -NoNewline
        $rows.Add((@{
            id = $f.Id; name = $f.Slug; status = 'Implemented'; priority = 50
            file = $relFile; created = '2026-09-02'; updated = '2026-09-02 00:00'
        } | ConvertTo-Json -Compress))
    }

    # F needs the duplicate to live in a PHASE file inside the tactical folder, not in the spec
    # itself: S1955's real one did, and a gate reading only the strategic file would have missed it.
    $folderSpecId = 'S9993'
    $folderSlug = 'headings-duplicate-in-phase-file'
    $folderRel = 'temp/scratch/check-headings-unique-sandbox-{0}/specs/{1}_{2}.md' -f $PID, $folderSpecId, $folderSlug
    Set-Content -LiteralPath (Join-Path $fixtureDir ("{0}_{1}.md" -f $folderSpecId, $folderSlug)) `
        -Value "# Fixture`n`n**Status:** Implemented`n`n## Goal`n`nclean strategic file`n" -Encoding UTF8 -NoNewline
    $phaseDir = Join-Path $fixtureDir ("{0}_{1}" -f $folderSpecId, $folderSlug)
    New-Item -ItemType Directory -Force -Path $phaseDir | Out-Null
    Set-Content -LiteralPath (Join-Path $phaseDir 'PHASE_01__x.md') `
        -Value "# Phase 01 - x`n`n## Phase Done Criteria`n`n- [x] a`n`n## Steps`n`ns`n`n## Phase Done Criteria`n`n- [ ] a`n" -Encoding UTF8 -NoNewline
    $rows.Add((@{
        id = $folderSpecId; name = $folderSlug; status = 'Implemented'; priority = 50
        file = $folderRel; created = '2026-09-02'; updated = '2026-09-02 00:00'
    } | ConvertTo-Json -Compress))

    Add-Content -LiteralPath (Join-Path $sandboxDir 'spec-catalog.jsonl') -Value $rows -Encoding UTF8

    $env:FMS_SPEC_CATALOG_DIR = $sandboxDir
    $env:FMS_SKIP_RELEASE_QUEUE = '1'

    $c = Invoke-Gate 'S9990'
    Assert-That "C. same heading twice in one '# ' block fails" ($c.Code -eq 1) "exit $($c.Code): $($c.Text)"
    # The fixture puts the first '## 7. Risks' on line 5 and its repeat on line 13; the refusal is
    # only actionable if it names both, since the owner has to decide which copy to keep.
    Assert-That "C2. the refusal names both line numbers" `
        ($c.Text -match ':13:' -and $c.Text -match 'line 5') "text: $($c.Text)"

    $d = Invoke-Gate 'S9991'
    Assert-That "D. heading repeated across two '# Phase NN' blocks passes" ($d.Code -eq 0) "exit $($d.Code): $($d.Text)"

    $e = Invoke-Gate 'S9992'
    Assert-That "E. duplicate inside a fenced code block is ignored" ($e.Code -eq 0) "exit $($e.Code): $($e.Text)"

    $f = Invoke-Gate 'S9993'
    Assert-That "F. duplicate in a phase file inside the tactical folder fails" ($f.Code -eq 1) "exit $($f.Code): $($f.Text)"
    Assert-That "F2. the refusal names the phase file, not the strategic spec" `
        ($f.Text -match 'PHASE_01__x\.md') "text: $($f.Text)"
}
finally {
    Remove-Item Env:\FMS_SPEC_CATALOG_DIR -ErrorAction SilentlyContinue
    Remove-Item Env:\FMS_SKIP_RELEASE_QUEUE -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $sandboxDir) { Remove-Item -LiteralPath $sandboxDir -Recurse -Force }
}

# The sandbox is gone, so these run against production again - which is the point: the fixture
# rows must not have reached the real journal.
$leak = Invoke-Gate 'S9990'
Assert-That "G. fixture ids absent from the production catalog" ($leak.Code -eq 2) "exit $($leak.Code): $($leak.Text)"

$h = Invoke-Gate 'NOPE'
Assert-That "H. malformed id exits 2, not 1" ($h.Code -eq 2) "exit $($h.Code): $($h.Text)"

Write-Host ""
Write-Host ("passed: {0}  failed: {1}" -f $script:pass, $script:fail) -ForegroundColor Cyan
if ($script:fail -gt 0) { exit 1 }
exit 0
