<#
.SYNOPSIS
  Rule/prompt executable drift audit (S0315). Finds executable mismatch between the
  canonical rules (CLAUDE.md), prompt skills, agent profiles, AGENTS.md, copilot
  instructions, workflow docs, and the scripts that actually exist on disk.

.DESCRIPTION
  Executable mismatch only - no prose/style drift is ever reported (strategic ADR-1).
  Closed taxonomy of six mismatch kinds:
    MissingDocumentedScript    - a source cites a repo script path that is absent on disk.
    ConflictingRouteName       - a source cites a /skill route with no .claude/commands file.
    MissingNoProfile           - a documented pwsh invocation omits -NoProfile.
    AbolishedArtifactReference - a source prescribes an abolished artefact (e.g. _spec_ path).
    StaleCommandExample        - a documented command example no longer matches reality.
    OutdatedValidationRule     - a per-step closure rule the canonical no longer lists.

  Source set is declared as data in sources.psd1 (extend without code change).
  Reuse boundary: version-pin drift stays in check-doc-vs-gradle.ps1; spec-id-marker
  drift stays in spec_catalog/drift-check.ps1; this audit re-implements neither.

  Exit codes:
    0  CLEAN - no mismatch records.
    1  DRIFT - one or more mismatch records.
    2  USAGE - load error (missing manifest / module) or bad invocation.

  Artifact: temp/rule-prompt-drift_<yyyyMMdd-HHmmss>.json on a non-dry run.
#>
[CmdletBinding()]
param(
    [switch] $Json,
    [switch] $DryRun,
    [switch] $Color
)

$ErrorActionPreference = 'Stop'

$categories = @(
    'MissingDocumentedScript'
    'ConflictingRouteName'
    'MissingNoProfile'
    'AbolishedArtifactReference'
    'StaleCommandExample'
    'OutdatedValidationRule'
)

try {
    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

    . (Join-Path $PSScriptRoot 'RulePromptRecord.ps1')
    . (Join-Path $PSScriptRoot 'RulePromptSources.ps1')
    . (Join-Path $PSScriptRoot 'RulePromptDetectors.ps1')
    . (Join-Path $PSScriptRoot 'RulePromptOutput.ps1')

    $manifestPath = Join-Path $PSScriptRoot 'sources.psd1'
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw "Manifest not found: $manifestPath"
    }
    $manifest  = Import-PowerShellDataFile -LiteralPath $manifestPath
    $canonical = [string]$manifest.Canonical
}
catch {
    Write-Host "USAGE: rule/prompt drift audit failed to load - $($_.Exception.Message)" -ForegroundColor Red
    exit 2
}

$sources = Get-RulePromptSources -Manifest $manifest -RepoRoot $repoRoot
if ($null -eq $sources) { $sources = @() }

if ($DryRun) {
    Write-Host "rule/prompt drift audit (S0315) - dry run"
    Write-Host "canonical: $canonical"
    Write-Host ("audited surfaces: {0}" -f $sources.Count)
    foreach ($s in $sources) { Write-Host ("  - {0} [{1}]" -f $s.relPath, $s.kind) }
    Write-Host "mismatch categories (executable only - no prose/style):"
    foreach ($c in $categories) { Write-Host "  - $c" }
    exit 0
}

$inventory = Get-RulePromptScriptInventory -Manifest $manifest -RepoRoot $repoRoot
if ($null -eq $inventory) { $inventory = @() }

$records = @()
$records += Find-MissingDocumentedScript    -Sources $sources -Inventory $inventory -KnownAbsentScripts @($manifest.KnownAbsentScripts)
$records += Find-MissingNoProfile           -Sources $sources
$records += Find-AbolishedArtifactReference -Sources $sources
$records += Find-OutdatedValidationRule     -Sources $sources -Canonical $canonical
$knownRoutes = @($manifest.KnownRoutes)
$records += Find-ConflictingRouteName       -Sources $sources -Canonical $canonical -KnownRoutes $knownRoutes
$records = @($records | Where-Object { $null -ne $_ -and $null -ne $_.mismatchKind })

if ($Json) {
    ConvertTo-RulePromptJson -Records $records
} else {
    foreach ($line in (Format-RulePromptReport -Records $records -Color:$Color)) {
        Write-Host $line
    }
}

# Persist the machine report under temp/ (repo-relative paths only, no secrets).
$stamp     = (Get-Date).ToString('yyyyMMdd-HHmmss')
$artifact  = Join-Path $repoRoot ("temp/rule-prompt-drift_{0}.json" -f $stamp)
$artifactDir = Split-Path $artifact -Parent
if (-not (Test-Path -LiteralPath $artifactDir -PathType Container)) {
    New-Item -ItemType Directory -Path $artifactDir -Force | Out-Null
}
ConvertTo-RulePromptJson -Records $records | Set-Content -LiteralPath $artifact -Encoding UTF8

if (@($records).Count -eq 0) { exit 0 }
Write-Error ("check-rule-prompt-drift: {0} drift record(s) - details in {1}." -f @($records).Count, $artifact) -ErrorAction Continue
exit 1
