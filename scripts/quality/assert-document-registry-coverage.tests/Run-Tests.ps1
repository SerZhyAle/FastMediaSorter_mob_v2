#requires -Version 7.0
# Subject: scripts/quality/assert-document-registry-coverage.ps1
<#
.SYNOPSIS
    Regression suite for assert-document-registry-coverage.ps1 (S2618).

.DESCRIPTION
    The gate's whole value is the refusal, so the refusal is what is asserted here. A gate proven
    only against the live tree passes for as long as that tree happens to be clean and says nothing
    about the rule it claims to enforce - which is the same unobserved green verdict the gate exists
    to prevent.

    Asserted:
      * a document directory no record glob reaches fails, and the directory is named,
      * the same directory named in the baseline passes,
      * a `/**` baseline row excuses the subtree beneath it as well as the directory itself,
      * a baseline reason under four words fails even though the directory is listed,
      * a baseline row naming a directory that holds no documents fails as stale,
      * a directory a record glob reaches passes without a baseline row,
      * a parent record does NOT cover a child directory - the S2607 gap was exactly a covered
        parent with an unreached child, so prefix coverage would pass the case the gate exists for,
      * an excluded tree (node_modules, a .tests fixture directory) is not reported at all,
      * a missing registry exits 2 - "could not look" must never be spelled like "looked and refused".

    Every case builds its own tree under temp/scratch/ and passes it as -RepoRoot. The live
    repository is never judged: an assertion that added or removed a record would edit the state it
    is judging.

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

$gatePs1 = Join-Path $repoRoot 'scripts/quality/assert-document-registry-coverage.ps1'
if (-not (Test-Path -LiteralPath $gatePs1)) {
    Write-Host "cannot prepare fixtures - gate not found: $gatePs1" -ForegroundColor Yellow
    exit 2
}

$sandboxRoot = Join-Path $repoRoot 'temp/scratch/S2618-coverage-tests'
if (Test-Path -LiteralPath $sandboxRoot) { Remove-Item -LiteralPath $sandboxRoot -Recurse -Force }
[void](New-Item -ItemType Directory -Path $sandboxRoot -Force)

$script:pass = 0
$script:fail = 0
$script:caseIndex = 0

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

function New-Fixture {
    <#
        Build one tree: the registry records, the baseline rows, and the document files. Records are
        given as JSON-ready hashtables so a case reads as what it declares, not as escaped text.
    #>
    param(
        [object[]] $Records = @(),
        [string[]] $BaselineRows = $null,
        [string[]] $Documents = @()
    )

    $script:caseIndex++
    $fixture = Join-Path $sandboxRoot "case$($script:caseIndex)"
    [void](New-Item -ItemType Directory -Path (Join-Path $fixture 'docs') -Force)

    $lines = foreach ($record in $Records) { $record | ConvertTo-Json -Compress -Depth 6 }
    Set-Content -LiteralPath (Join-Path $fixture 'docs/DOCUMENT_REGISTRY.jsonl') -Value $lines -Encoding utf8

    foreach ($document in $Documents) {
        $full = Join-Path $fixture $document
        [void](New-Item -ItemType Directory -Path (Split-Path $full -Parent) -Force)
        Set-Content -LiteralPath $full -Value '# fixture document' -Encoding utf8
    }

    if ($null -ne $BaselineRows) {
        $baselineDir = Join-Path $fixture 'scripts/quality'
        [void](New-Item -ItemType Directory -Path $baselineDir -Force)
        Set-Content -LiteralPath (Join-Path $baselineDir 'document-registry-coverage-baseline.txt') `
            -Value (@('# fixture baseline') + $BaselineRows) -Encoding utf8
    }

    return $fixture
}

function New-Record {
    param([string] $Id, [string[]] $Paths)
    return @{
        id              = $Id
        title           = 'Fixture'
        category        = 'process'
        audience        = 'developer'
        paths           = $Paths
        published       = $false
        indexable       = $false
        product_areas   = @('workflow')
        update_triggers = @('documentation')
        generated       = $false
    }
}

function Invoke-Gate([string]$FixtureRoot, [string[]]$ExtraArguments = @()) {
    $out = & $pwshExe -NoProfile -File $gatePs1 -RepoRoot $FixtureRoot @ExtraArguments 2>&1
    return [pscustomobject]@{
        Code = [int]$LASTEXITCODE
        Text = (($out | ForEach-Object { [string]$_ }) -join "`n")
    }
}

Write-Host 'assert-document-registry-coverage.tests' -ForegroundColor Cyan

# --- 1. an unreached document directory fails and is named -------------------------------------
$fixture = New-Fixture -Records @((New-Record -Id 'guides' -Paths @('docs/GUIDE.md'))) `
    -Documents @('docs/GUIDE.md', 'dev/notes/DESIGN.md')
$result = Invoke-Gate $fixture
Assert-That 'unreached document directory fails' ($result.Code -eq 1) "exit $($result.Code)"
Assert-That 'the refusal names the directory' ($result.Text -match 'dev/notes') $result.Text
Assert-That 'the refusal names both exits' `
    (($result.Text -match 'DOCUMENT_REGISTRY\.jsonl') -and ($result.Text -match 'baseline')) $result.Text

# --- 2. the same directory named in the baseline passes -----------------------------------------
$fixture = New-Fixture -Records @((New-Record -Id 'guides' -Paths @('docs/GUIDE.md'))) `
    -BaselineRows @('dev/notes | scratch design notes kept beside the tool') `
    -Documents @('docs/GUIDE.md', 'dev/notes/DESIGN.md')
$result = Invoke-Gate $fixture
Assert-That 'a baseline row accounts for the directory' ($result.Code -eq 0) $result.Text

# --- 3. a /** row excuses the subtree, not only the directory itself ----------------------------
$fixture = New-Fixture -Records @((New-Record -Id 'guides' -Paths @('docs/GUIDE.md'))) `
    -BaselineRows @('.agents/** | private agent memory, not a maintained document') `
    -Documents @('docs/GUIDE.md', '.agents/MEMORY.md', '.agents/skills/one/SKILL.md')
$result = Invoke-Gate $fixture
Assert-That 'a /** row excuses the whole subtree' ($result.Code -eq 0) $result.Text

# --- 4. a reason under four words fails ---------------------------------------------------------
$fixture = New-Fixture -Records @((New-Record -Id 'guides' -Paths @('docs/GUIDE.md'))) `
    -BaselineRows @('dev/notes | internal') `
    -Documents @('docs/GUIDE.md', 'dev/notes/DESIGN.md')
$result = Invoke-Gate $fixture
Assert-That 'a reason under four words fails' ($result.Code -eq 1) "exit $($result.Code)"
Assert-That 'the thin reason is reported as such' ($result.Text -match 'too thin') $result.Text

# --- 5. a row naming a directory with no documents is stale ------------------------------------
$fixture = New-Fixture -Records @((New-Record -Id 'guides' -Paths @('docs/GUIDE.md'))) `
    -BaselineRows @('dev/deleted | a tree that was removed some releases ago') `
    -Documents @('docs/GUIDE.md')
$result = Invoke-Gate $fixture
Assert-That 'a stale baseline row fails' ($result.Code -eq 1) "exit $($result.Code)"
Assert-That 'the stale row is named' ($result.Text -match 'dev/deleted') $result.Text

# --- 6. a directory a glob reaches needs no baseline row ---------------------------------------
$fixture = New-Fixture -Records @((New-Record -Id 'guides' -Paths @('docs/*.md', 'dev/notes/*.md'))) `
    -Documents @('docs/GUIDE.md', 'dev/notes/DESIGN.md')
$result = Invoke-Gate $fixture
Assert-That 'a glob-reached directory passes' ($result.Code -eq 0) $result.Text

# --- 7. a covered PARENT does not cover an unreached CHILD (the S2607 shape) --------------------
$fixture = New-Fixture -Records @((New-Record -Id 'rules' -Paths @('.claude/agents/*.md'))) `
    -Documents @('.claude/agents/one.md', '.claude/rules/one.md')
$result = Invoke-Gate $fixture
Assert-That 'a covered parent does not cover its child' ($result.Code -eq 1) "exit $($result.Code)"
Assert-That 'the unreached child is named' ($result.Text -match '\.claude/rules') $result.Text

# --- 8. excluded trees are not reported at all --------------------------------------------------
$fixture = New-Fixture -Records @((New-Record -Id 'guides' -Paths @('docs/GUIDE.md'))) `
    -Documents @('docs/GUIDE.md',
                 'scripts/mcp/server/node_modules/dep/README.md',
                 'scripts/doc-drift.tests/fixtures/CLAUDE.md',
                 'PLAN/S0001_something.md',
                 'dev/archive/OLD.md')
$result = Invoke-Gate $fixture
Assert-That 'excluded trees are not reported' ($result.Code -eq 0) $result.Text

# --- 9. a missing registry is "could not look", not "refused" -----------------------------------
$fixture = New-Fixture -Records @((New-Record -Id 'guides' -Paths @('docs/GUIDE.md'))) `
    -Documents @('docs/GUIDE.md')
Remove-Item -LiteralPath (Join-Path $fixture 'docs/DOCUMENT_REGISTRY.jsonl') -Force
$result = Invoke-Gate $fixture
Assert-That 'a missing registry exits 2' ($result.Code -eq 2) "exit $($result.Code)"

# --- 10. an absent RepoRoot exits 2 -------------------------------------------------------------
$result = Invoke-Gate (Join-Path $sandboxRoot 'no-such-tree')
Assert-That 'an absent RepoRoot exits 2' ($result.Code -eq 2) "exit $($result.Code)"

# --- 11. the release-scope runner's own invocation shape ----------------------------------------
# assert-release-scope-gates.ps1 runs every gate as `-File <gate> -Gate <its args>`. A
# [CmdletBinding()] script that does not declare -Gate dies on a binding error before its first
# line, exits 1, and prints "A parameter cannot be found" into the runner's summary where a verdict
# belongs - permanently red, and red in a way that reads as a real coverage finding. Caught exactly
# this way while auditing S2618, so the invocation shape is asserted rather than assumed.
$fixture = New-Fixture -Records @((New-Record -Id 'guides' -Paths @('docs/GUIDE.md'))) `
    -Documents @('docs/GUIDE.md')
$result = Invoke-Gate $fixture @('-Gate', '-Quiet')
Assert-That 'the runner invocation shape (-Gate -Quiet) still runs the gate' ($result.Code -eq 0) $result.Text
Assert-That 'no parameter-binding error on the runner shape' `
    ($result.Text -notmatch 'parameter cannot be found') $result.Text

Remove-Item -LiteralPath $sandboxRoot -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ''
Write-Host ("assert-document-registry-coverage.tests: {0} passed, {1} failed" -f $script:pass, $script:fail) `
    -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
exit $(if ($script:fail -eq 0) { 0 } else { 1 })
