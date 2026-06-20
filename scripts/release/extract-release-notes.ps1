#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Extract the release-notes block for a given version from docs/WHATS_NEW.md.

.DESCRIPTION
    Spec S0214 - github-store-publication, Phase 03 Step 03.1.

    Reads docs/WHATS_NEW.md and emits to stdout the section corresponding
    to -Version. Two markers are recognised, per DECISIONS.md
    "## Release notes source":

      1. Current release marker (bold line):
             **Current release: X.YM.MDDH.Hmm**
      2. Previous release heading (H2):
             ## Previous Release: X.YM.MDDH.Hmm

    The extractor reads from the first non-empty content line AFTER the
    matched marker until (excluding) the next "## Previous Release:"
    heading or EOF. The immediately-following "> Changes since .."
    blockquote line is skipped - it duplicates info GitHub already
    surfaces via compare-since-previous-tag.

    Exit codes:
      0 - match found, stdout contains the block (non-empty).
      2 - no matching marker for the supplied version.

.PARAMETER Version
    Version string in the project's format Y.YM.MDDH.Hmm
    (e.g. 2.62.0501.151). Matched against both markers verbatim.

.PARAMETER WhatsNewPath
    Optional override for docs/WHATS_NEW.md location. Defaults to the
    repo's canonical path resolved relative to this script.

.EXAMPLE
    pwsh -File scripts/release/extract-release-notes.ps1 -Version 2.60.5150.150
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $Version,
    [string] $WhatsNewPath
)

$ErrorActionPreference = "Stop"

if (-not $WhatsNewPath) {
    $repoRoot     = Resolve-Path (Join-Path $PSScriptRoot "..\..")
    $WhatsNewPath = Join-Path $repoRoot "docs/WHATS_NEW.md"
}

if (-not (Test-Path -LiteralPath $WhatsNewPath)) {
    [Console]::Error.WriteLine("extract-release-notes: WHATS_NEW.md not found at $WhatsNewPath")
    exit 2
}

$escapedVersion = [regex]::Escape($Version)
$lines = Get-Content -LiteralPath $WhatsNewPath

# Locate the marker line index (zero-based).
$markerIndex = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]
    if ($line -match "^\*\*Current release:\s*$escapedVersion\*\*") {
        $markerIndex = $i
        break
    }
    if ($line -match "^## Previous Release:\s*$escapedVersion(\s|$)") {
        $markerIndex = $i
        break
    }
}

if ($markerIndex -lt 0) {
    [Console]::Error.WriteLine("extract-release-notes: no marker for version '$Version' in $WhatsNewPath")
    exit 2
}

# Read body: start at the first non-empty content line AFTER the marker,
# skip an optional immediately-following "> Changes since .." blockquote,
# stop at the next "## Previous Release:" heading or EOF.
$body = [System.Collections.Generic.List[string]]::new()
$started = $false
for ($i = $markerIndex + 1; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]

    if (-not $started) {
        if ($line.Trim() -eq '') { continue }
        if ($line -match '^> Changes since\b') { continue }
        if ($line -match '^---\s*$') { continue }
        $started = $true
    }

    if ($line -match '^## Previous Release:') {
        break
    }

    $body.Add($line)
}

# Trim trailing empty lines and trailing horizontal rule.
while ($body.Count -gt 0 -and ($body[$body.Count - 1].Trim() -eq '' -or $body[$body.Count - 1] -match '^---\s*$')) {
    $body.RemoveAt($body.Count - 1)
}

if ($body.Count -eq 0) {
    [Console]::Error.WriteLine("extract-release-notes: matched marker for '$Version' but body is empty")
    exit 2
}

$body -join "`n" | Write-Output
exit 0
