#requires -Version 7.0
<#
.SYNOPSIS
    Rewrite in-repo references to localized documentation filenames onto the hyphen suffix (S1211).

.DESCRIPTION
    Companion to rename-doc-locale-suffix.ps1. That script moves the files; this one repoints every
    link, generator output path, gate input path and prose mention that named the old form.

    The `.md` extension is what anchors every rule. Published addresses live in `permalink:` lines and
    end in `.html`, and S1211 keeps them frozen forever - so a rule that matched the bare `_RU` token
    would rewrite the addresses the whole rename was designed not to touch.

.PARAMETER RepoRoot
    Repository root.

.PARAMETER Scope
    Which domain's files to rewrite. `Scripts` covers docs/, scripts/, .claude/, .github/, the root
    site files and .gitignore; `Phone` covers app_v2/. They are separate because the repository's
    concurrency locks are split the same way and a single run would have to hold both.

.PARAMETER WhatIf
    Report every file that would change and write nothing.

Exit codes: 0 rewritten or nothing to do, 2 the repository root is not a valid checkout.
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$RepoRoot,
    [ValidateSet('Scripts', 'Phone')]
    [string]$Scope = 'Scripts'
)

$ErrorActionPreference = 'Stop'

if (-not $RepoRoot) { $RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
. (Join-Path $PSScriptRoot 'code-lock-scope.ps1')
if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot 'docs') -PathType Container)) {
    # -ErrorAction Continue keeps the exit line reachable under the script-wide 'Stop' preference.
    Write-Error "Not a valid checkout - docs/ not found under '$RepoRoot'." -ErrorAction Continue
    exit 2
}

$roots = if ($Scope -eq 'Phone') {
    @('app_v2/src', 'app_v2/build.gradle.kts')
} else {
    @('docs', 'scripts', 'dev', '.claude/commands', '.claude/reference', '.claude/templates', '.github')
}

# Only text formats a reference can live in. Without this the Phone scope walks app_v2/build/, which
# holds tens of thousands of generated artifacts and turns a two-file edit into a minutes-long scan.
$textExtensions = @('.md', '.html', '.ps1', '.psd1', '.psm1', '.jsonl', '.json', '.yml', '.yaml',
                    '.kt', '.kts', '.xml', '.txt', '.css')

# dev/CHANGELOG.md is a journal of what was true when each row was written; rewriting it would edit
# history rather than fix a link, and CLAUDE.md forbids touching it outside add_to_dev_log.ps1.
# Agent memory is corrected by observation, not by sweep. PLAN/ and the archives record past state.
$excluded = @(
    'dev/CHANGELOG.md'
    '.claude/agent-memory'
    'PLAN/'
    'temp/'
    'dev/archive/'
    'V1/'
    'v2_6/'
    'spec_v2/'
    'node_modules/'
)

$rules = @(
    @{ From = '(?<stem>[A-Za-z0-9_.\-]+)_RU\.md'; To = '${stem}-ru.md' }
    @{ From = '(?<stem>[A-Za-z0-9_.\-]+)_UK\.md'; To = '${stem}-uk.md' }
    @{ From = '(?<stem>[A-Za-z0-9_\-]+)\.ru\.md';  To = '${stem}-ru.md' }
    @{ From = '(?<stem>[A-Za-z0-9_\-]+)\.uk\.md';  To = '${stem}-uk.md' }
    @{ From = 'DOWNLOADS_EN\.md';                  To = 'DOWNLOADS.md'  }
)

$targets = [System.Collections.Generic.List[string]]::new()
foreach ($root in $roots) {
    $full = Join-Path $RepoRoot $root
    if (-not (Test-Path -LiteralPath $full)) { continue }
    if (Test-Path -LiteralPath $full -PathType Leaf) { $targets.Add($full); continue }
    Get-ChildItem -LiteralPath $full -Recurse -File |
        Where-Object { $textExtensions -contains $_.Extension.ToLowerInvariant() } |
        ForEach-Object { $targets.Add($_.FullName) }
}
if ($Scope -eq 'Scripts') {
    foreach ($loose in @('index.html', 'index-ru.html', 'index-uk.html', 'nolegal.html',
                         'nolegal-ru.html', 'nolegal-uk.html', 'README.md', '.gitignore')) {
        $p = Join-Path $RepoRoot $loose
        if (Test-Path -LiteralPath $p) { $targets.Add($p) }
    }
}

$changed = 0
$pendingWrites = [System.Collections.Generic.List[object]]::new()
foreach ($path in $targets) {
    $rel = $path.Substring($RepoRoot.Length).TrimStart('\', '/') -replace '\\', '/'
    if ($excluded | Where-Object { $rel -like "$_*" -or $rel -eq $_ }) { continue }

    # Read as raw bytes-to-text without re-encoding: these files carry Cyrillic, Ukrainian and CRLF
    # seams, and a rewrite that normalises either one turns a one-token fix into a whole-file diff.
    $text = [System.IO.File]::ReadAllText($path)
    $updated = $text
    foreach ($rule in $rules) { $updated = [regex]::Replace($updated, $rule.From, $rule.To) }
    if ($updated -eq $text) { continue }

    $changed++
    Write-Host "  $rel"
    if ($PSCmdlet.ShouldProcess($rel, 'repoint localized doc references')) {
        $pendingWrites.Add([pscustomobject]@{ Path = $path; Relative = $rel; Text = $updated })
    }
}

if ($pendingWrites.Count -gt 0) {
    $scope = $null
    try {
        $scope = Enter-CodeLockOrExit -Path @($pendingWrites | ForEach-Object { $_.Relative }) `
            -Reason "repoint-doc-locale-references.ps1 -Scope $Scope"
        foreach ($write in $pendingWrites) {
            [System.IO.File]::WriteAllText($write.Path, $write.Text)
        }
    }
    finally { Exit-CodeLockScope -Scope $scope }
}

Write-Host "repoint-doc-locale-references [$Scope]: $changed file(s)."
exit 0
