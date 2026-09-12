#requires -Version 7.0
<#
.SYNOPSIS
    Rename localized documentation files to the single lowercase hyphen locale suffix (S1211).

.DESCRIPTION
    Three locale-suffix conventions coexisted in docs/: the uppercase underscore form (47 files), the
    dotted lowercase form (4 files) and an explicit English suffix on the downloads page. S1211 picked
    the lowercase hyphen form because the site pages and the 22 files under docs/howto/ already use it.

    Only the FILE NAME changes. The body is never touched, which matters more than it looks:
    every published page declares its own `permalink:` and the address is what external links
    use, so a rename that leaves the front matter alone breaks nothing (S1211 strategic section 6
    item 2, measured over 81 documents).

.PARAMETER RepoRoot
    Repository root. Defaults to the parent of the scripts/ directory this file lives in.

.PARAMETER WhatIf
    List every planned rename and write nothing.

Exit codes: 0 renamed or nothing to do, 1 a target name already exists, 2 the repository root is
not a valid checkout.
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$RepoRoot
)

$ErrorActionPreference = 'Stop'

if (-not $RepoRoot) {
    $RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
}
$docsRoot = Join-Path $RepoRoot 'docs'
if (-not (Test-Path -LiteralPath $docsRoot -PathType Container)) {
    # -ErrorAction Continue keeps the exit line reachable: the script-wide 'Stop' preference would
    # otherwise terminate here and the declared code 2 would never be returned.
    Write-Error "Not a valid checkout - docs/ not found under '$RepoRoot'." -ErrorAction Continue
    exit 2
}

# Ordered deliberately: the explicit DOWNLOADS_EN rule must be tried before the generic `_RU`/`_UK`
# rules, and the dotted form before the underscore form, so a name matching two patterns takes the
# most specific one rather than whichever enumerated first.
$rules = @(
    @{ Pattern = '^DOWNLOADS_EN\.md$';        Replacement = 'DOWNLOADS.md' }
    @{ Pattern = '^(?<stem>.+)\.ru\.md$';     Replacement = '${stem}-ru.md' }
    @{ Pattern = '^(?<stem>.+)\.uk\.md$';     Replacement = '${stem}-uk.md' }
    @{ Pattern = '^(?<stem>.+)_RU\.md$';      Replacement = '${stem}-ru.md' }
    @{ Pattern = '^(?<stem>.+)_UK\.md$';      Replacement = '${stem}-uk.md' }
)

$planned = @()
foreach ($file in Get-ChildItem -LiteralPath $docsRoot -Filter '*.md' -File) {
    foreach ($rule in $rules) {
        if ($file.Name -match $rule.Pattern) {
            $newName = [regex]::Replace($file.Name, $rule.Pattern, $rule.Replacement)
            $planned += [pscustomobject]@{
                From    = $file.FullName
                To      = Join-Path $file.DirectoryName $newName
                OldName = $file.Name
                NewName = $newName
            }
            break
        }
    }
}

if (-not $planned) {
    Write-Host 'rename-doc-locale-suffix: nothing to rename - the convention is already single.'
    exit 0
}

$collisions = @($planned | Where-Object { Test-Path -LiteralPath $_.To })
if ($collisions) {
    foreach ($c in $collisions) {
        Write-Error "Target already exists: $($c.OldName) -> $($c.NewName)" -ErrorAction Continue
    }
    exit 1
}

Write-Host "rename-doc-locale-suffix: $($planned.Count) file(s) to rename."
foreach ($p in $planned) {
    Write-Host ("  {0} -> {1}" -f $p.OldName, $p.NewName)
    if ($PSCmdlet.ShouldProcess($p.OldName, "rename to $($p.NewName)")) {
        # git mv keeps the rename visible as a rename in the index; a tracked file moved with
        # Move-Item shows up as a delete plus an add and the review loses the "content unchanged"
        # guarantee this whole phase rests on.
        $tracked = (& git -C $RepoRoot ls-files --error-unmatch -- $p.From 2>$null)
        if ($LASTEXITCODE -eq 0 -and $tracked) {
            & git -C $RepoRoot mv -- $p.From $p.To
        } else {
            Move-Item -LiteralPath $p.From -Destination $p.To
        }
    }
}

exit 0
