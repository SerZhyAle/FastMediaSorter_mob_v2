# RulePromptSources.ps1 - source discovery for the rule/prompt executable drift audit (S0315).
#
# Reuse boundary: Rule/prompt executable drift only - version-pin drift -> check-doc-vs-gradle.ps1; spec-id-marker drift -> spec_catalog/drift-check.ps1 (S0278/S0279 own doc-vs-gradle wear/PR-gate scope).
#
# Public entries:
#   Get-RulePromptSources -Manifest <hashtable> -RepoRoot <string>
#       -> flat list of audited file objects: path (absolute), relPath (repo-relative,
#          forward-slash), kind. Surface globs are expanded. CLAUDE.md is included with
#          kind 'canonical'.
#   Get-RulePromptScriptInventory -Manifest <hashtable> -RepoRoot <string>
#       -> on-disk *.ps1 files under ScriptRoots as objects with relPath (repo-relative,
#          forward-slash) so a later detector can ask "is this documented script present".
#
# Both functions skip paths under temp/, DOWNLOADS/, .venv/, logs/, .kotlin/, node_modules.
# Neither reads file bodies for content - discovery only; body parsing belongs to Phase 02.
#
# No external module dependencies. PowerShell 7+. -NoProfile safe.

# --- Private helpers ---------------------------------------------------------

$script:RulePromptExcludeSegments = @(
    'temp/'
    'DOWNLOADS/'
    '.venv/'
    'logs/'
    '.kotlin/'
    'node_modules'
)

function script:Convert-ToRelPath {
    param(
        [Parameter(Mandatory)][string] $AbsolutePath,
        [Parameter(Mandatory)][string] $RepoRoot
    )
    $rootFull = [System.IO.Path]::GetFullPath($RepoRoot).TrimEnd('\', '/')
    $full     = [System.IO.Path]::GetFullPath($AbsolutePath)
    if ($full.StartsWith($rootFull, [System.StringComparison]::OrdinalIgnoreCase)) {
        $rel = $full.Substring($rootFull.Length).TrimStart('\', '/')
    } else {
        $rel = $full
    }
    return ($rel -replace '\\', '/')
}

function script:Test-RulePromptExcluded {
    param([Parameter(Mandatory)][string] $RelPath)
    $normalized = $RelPath -replace '\\', '/'
    foreach ($seg in $script:RulePromptExcludeSegments) {
        if ($normalized -like ("*" + $seg + "*")) { return $true }
    }
    return $false
}

# --- Public entry: audited surfaces -----------------------------------------

function Get-RulePromptSources {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][hashtable] $Manifest,
        [string] $RepoRoot = (Get-Location).Path
    )

    $results = @()

    # Canonical authority first.
    $canonicalRel = [string]$Manifest.Canonical
    if (-not [string]::IsNullOrWhiteSpace($canonicalRel)) {
        $canonicalAbs = Join-Path $RepoRoot $canonicalRel
        if (Test-Path -LiteralPath $canonicalAbs -PathType Leaf) {
            $results += [pscustomobject]@{
                path    = (Resolve-Path -LiteralPath $canonicalAbs).Path
                relPath = (Convert-ToRelPath -AbsolutePath $canonicalAbs -RepoRoot $RepoRoot)
                kind    = 'canonical'
            }
        }
    }

    foreach ($surface in $Manifest.Surfaces) {
        if (-not [bool]$surface.audited) { continue }
        $surfacePath = [string]$surface.path
        $surfaceKind = [string]$surface.kind
        $absPattern  = Join-Path $RepoRoot $surfacePath

        if ($surfacePath -match '[\*\?]') {
            # Glob - expand to concrete files.
            $matches = @(Get-ChildItem -Path $absPattern -File -ErrorAction SilentlyContinue)
        } elseif (Test-Path -LiteralPath $absPattern -PathType Leaf) {
            $matches = @(Get-Item -LiteralPath $absPattern)
        } else {
            $matches = @()
        }

        foreach ($f in $matches) {
            $rel = Convert-ToRelPath -AbsolutePath $f.FullName -RepoRoot $RepoRoot
            if (Test-RulePromptExcluded -RelPath $rel) { continue }
            $results += [pscustomobject]@{
                path    = $f.FullName
                relPath = $rel
                kind    = $surfaceKind
            }
        }
    }

    # De-duplicate by relPath, preserve first-seen order (canonical wins).
    $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    $unique = @()
    foreach ($r in $results) {
        if ($seen.Add($r.relPath)) { $unique += $r }
    }
    return ,$unique
}

# --- Public entry: on-disk script inventory ---------------------------------

function Get-RulePromptScriptInventory {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][hashtable] $Manifest,
        [string] $RepoRoot = (Get-Location).Path
    )

    $results = @()
    $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)

    foreach ($root in $Manifest.ScriptRoots) {
        $absRoot = Join-Path $RepoRoot ([string]$root)
        if (-not (Test-Path -LiteralPath $absRoot -PathType Container)) { continue }
        $files = @(Get-ChildItem -Path $absRoot -Recurse -File -Filter '*.ps1' -ErrorAction SilentlyContinue)
        foreach ($f in $files) {
            $rel = Convert-ToRelPath -AbsolutePath $f.FullName -RepoRoot $RepoRoot
            if (Test-RulePromptExcluded -RelPath $rel) { continue }
            if ($seen.Add($rel)) {
                $results += [pscustomobject]@{
                    path    = $f.FullName
                    relPath = $rel
                }
            }
        }
    }
    return ,$results
}
