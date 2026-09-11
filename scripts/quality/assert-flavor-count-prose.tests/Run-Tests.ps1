# Run-Tests.ps1 (S2919) - regression suite for assert-flavor-count-prose.ps1.
#
# The live tree is clean, so it can only ever show the gate green - and the design risk of the
# labelled-list shape S2919 added is the opposite one: a subset label ("Flavor scope:") judged as if
# it claimed the whole set, which is what gets a gate switched off. Every case therefore runs the
# gate against a synthetic tree under temp/scratch carrying a seven-flavor matrix and one document,
# removed in a finally block.
#
# Usage:  pwsh -NoProfile -File scripts/quality/assert-flavor-count-prose.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$gateScript = Join-Path $repoRoot 'scripts/quality/assert-flavor-count-prose.ps1'
$pwshExe = (Get-Process -Id $PID).Path

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    }
    else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

$matrixJson = '{ "flavors": ["standard", "noLegal", "lite", "photos", "legacy", "vr", "foss"] }'

# Each case: one line of a document under dev/, and the exit code the gate must return with -Gate.
$cases = @(
    @{ Name = 'labelled four-name list fails (the S2919 index line)'
       Line = '- Flavors (main app): `standard`, `lite`, `photos`, `legacy`'; Exit = 1 }
    @{ Name = 'labelled Flavor(s) slash list fails (the researcher agent line)'
       Line = '- Flavor(s): standard / lite / photos / legacy'; Exit = 1 }
    @{ Name = 'labelled Russian list fails'
       Line = '- Флейворы: standard, lite, photos, legacy'; Exit = 1 }
    @{ Name = 'complete labelled list followed by a sentence passes'
       Line = '- **Flavors**: standard, noLegal, lite, photos, legacy, vr, foss. Gated via `BuildConfig` fields.'; Exit = 0 }
    @{ Name = 'complete labelled list joined by a conjunction passes'
       Line = '- Flavors: standard, noLegal, lite, photos, legacy, vr and foss'; Exit = 0 }
    @{ Name = 'Flavor scope subset label is not judged'
       Line = '- **Flavor scope**: standard/legacy/noLegal/vr - HLS; lite/photos - absent.'; Exit = 0 }
    @{ Name = 'Flavor gate label is not judged'
       Line = '**Flavor gate:** `noLegal` only'; Exit = 0 }
    @{ Name = 'watch-qualified label is not judged'
       Line = '- Flavors (wear): standard, noLegal, lite'; Exit = 0 }
    @{ Name = 'a label longer than the noun is not judged'
       Line = '- Flavor and device branching: Standard / Lite / Photos / Legacy plus XR source sets.'; Exit = 0 }
    @{ Name = 'pre-S2919 all-quantifier count still fails'
       Line = 'The build ships in all six flavors.'; Exit = 1 }
)

# The scan roots are part of the contract too: a stale list under .github/agents - the non-Claude
# mirror of the agent definitions - went unread until S2919 added that root.
$rootCases = @(
    @{ Name = '.github/agents is scanned'; Dir = '.github/agents'; File = 'mirror.agent.md' }
    @{ Name = '.claude/agents is scanned'; Dir = '.claude/agents'; File = 'agent.md' }
)

$scratchRoot = Join-Path $repoRoot 'temp/scratch'
if (-not (Test-Path -LiteralPath $scratchRoot)) { New-Item -ItemType Directory -Path $scratchRoot | Out-Null }

Write-Host "assert-flavor-count-prose suite: $($cases.Count) case(s)"
foreach ($case in $cases) {
    $tree = Join-Path $scratchRoot ("flavor-prose-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
    try {
        New-Item -ItemType Directory -Path (Join-Path $tree 'docs/flavors') -Force | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $tree 'dev') -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $tree 'docs/flavors/flavor-matrix.json') -Value $matrixJson -Encoding utf8
        Set-Content -LiteralPath (Join-Path $tree 'dev/CASE.md') -Value @('# Case', '', $case.Line) -Encoding utf8

        $output = & $pwshExe -NoProfile -File $gateScript -Gate -Quiet -RepoRoot $tree 2>&1 | Out-String
        $code = $LASTEXITCODE
        Assert-That $case.Name ($code -eq $case.Exit) "expected: exit $($case.Exit) | actual: exit $code`n$output"
    }
    finally {
        if (Test-Path -LiteralPath $tree) { Remove-Item -LiteralPath $tree -Recurse -Force }
    }
}

foreach ($case in $rootCases) {
    $tree = Join-Path $scratchRoot ("flavor-prose-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
    try {
        New-Item -ItemType Directory -Path (Join-Path $tree 'docs/flavors') -Force | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $tree $case.Dir) -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $tree 'docs/flavors/flavor-matrix.json') -Value $matrixJson -Encoding utf8
        Set-Content -LiteralPath (Join-Path $tree (Join-Path $case.Dir $case.File)) `
            -Value @('# Agent', '', '- Flavor(s): standard / lite / photos / legacy') -Encoding utf8

        $output = & $pwshExe -NoProfile -File $gateScript -Gate -Quiet -RepoRoot $tree 2>&1 | Out-String
        $code = $LASTEXITCODE
        Assert-That $case.Name ($code -eq 1) "expected: exit 1 | actual: exit $code`n$output"
    }
    finally {
        if (Test-Path -LiteralPath $tree) { Remove-Item -LiteralPath $tree -Recurse -Force }
    }
}

Write-Host "expected: 0 failed | actual: $script:fail failed, $script:pass passed"
if ($script:fail -gt 0) { exit 1 }
exit 0
