#requires -Version 7.0
<#
.SYNOPSIS
    S3451 conformance gate for contract INSTALL-TRUST 1.0 rules 1, 6 and 7.

.DESCRIPTION
    The trust page (docs/INSTALL_TRUST*.md) answers the warning Android shows for a sideloaded APK.
    Its conformance was established by reading, twice, and nothing noticed a new download surface
    shipped without the link: the published docs/README*.md handed the APK out through IzzyOnDroid
    and the Drive mirror with no link for as long as the page existed, and both manual audits missed
    it because they walked the surfaces they already knew. This gate walks the tree instead.

    Everything it judges is declared in scripts/quality/install-trust.psd1. Five finding kinds:
      ORDER      - a locale page is missing, or its four rule-1 headings are absent or out of order.
                   A missing page fails rather than skips: rule 8 says the page is updated, never
                   deleted, once the builds become recognized.
      SURFACE    - a declared hand-out surface is missing or does not link its locale page.
      UNDECLARED - a .md or .html file carries a hand-out marker and has no Surfaces row and no
                   Exempt prefix. Declare it, which then demands the link.
      CLAIM      - a declared claim phrase is absent from the page's "never does" section, or its
                   counterpart is absent from the same locale's privacy policy, or the section does
                   not link that policy.
      CODE       - an app_v2 manifest declares a forbidden permission (the advertising id) without
                   tools:node="remove", or a dependency file names a forbidden ad/analytics artifact.

    Not judged: rule 2 (the quoted dialogs), rule 3 (the reason) and rule 4 (no weakening advice) are
    prose a pattern cannot tell from its negation - the page itself says "nothing on this page asks
    you to switch it off". The credential-encryption and permission-on-use claims are the same kind.

.PARAMETER Root
    Tree to judge. Defaults to the repository root; the contract suite passes a fixture tree.

.PARAMETER Declaration
    Declaration file. Defaults to scripts/quality/install-trust.psd1 under the script's own
    repository, so a fixture tree is judged against the real declaration unless one is passed.

.PARAMETER Gate
    Accepted for the release-scope runner's uniform call shape; the gate is fail-closed either way.

.PARAMETER Quiet
    Print only the subject and the verdict line.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-install-trust.ps1

.NOTES
    Scope class (CLAUDE.md Rule 33): RELEASE. The subject is the whole published site tree, and the
    defect it exists for - a new page handing out the APK - is a file no per-ticket trigger can name
    in advance. Runs from assert-release-scope-gates.ps1; placement row in gate-placement.jsonl.

    Exit codes (CLAUDE.md Rule 7):
      0 - every rule-1, rule-6 and rule-7 check passed.
      1 - at least one ORDER, SURFACE, UNDECLARED, CLAIM or CODE finding.
      2 - cannot verify: the root or the declaration is missing or unreadable.
#>
[CmdletBinding()]
param(
    [string]$Root,
    [string]$Declaration,
    [switch]$Gate,
    [switch]$Quiet,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help $PSCommandPath
    exit 0
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $Root) { $Root = $repoRoot }
if (-not $Declaration) { $Declaration = Join-Path $PSScriptRoot 'install-trust.psd1' }

if (-not (Test-Path -LiteralPath $Root -PathType Container)) {
    Write-Host "assert-install-trust: CANNOT VERIFY - root not found: $Root" -ForegroundColor Yellow
    exit 2
}
try {
    $decl = Import-PowerShellDataFile -LiteralPath $Declaration
    foreach ($key in 'Pages', 'Surfaces', 'Markers', 'Exempt', 'Claims', 'ForbiddenPermissions', 'ManifestGlob', 'DependencyFiles', 'ForbiddenArtifacts') {
        if (-not $decl.ContainsKey($key)) { throw "key '$key' is missing" }
    }
}
catch {
    Write-Host "assert-install-trust: CANNOT VERIFY - declaration $Declaration is unreadable: $($_.Exception.Message)" -ForegroundColor Yellow
    exit 2
}
$Root = (Resolve-Path -LiteralPath $Root).Path

Write-Host "subject: root=$Root declaration=$Declaration"

$failures = [System.Collections.Generic.List[string]]::new()
function Add-Finding([string]$kind, [string]$message) {
    $script:failures.Add(('{0,-10} {1}' -f $kind, $message))
    if (-not $Quiet) { Write-Host "  FAIL [$kind] $message" -ForegroundColor Red }
}

function Read-RootFile([string]$relative) {
    $full = Join-Path $Root $relative
    if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { return $null }
    return Get-Content -LiteralPath $full -Raw -Encoding utf8
}

function Get-PlainText([string]$text) {
    # Emphasis markers split a phrase ("**no servers**" reads "no servers" to the user).
    return ($text -replace '\*\*|__', '')
}

function Get-SectionBody([string]$text, [string]$heading) {
    $pattern = '(?ms)^##[ \t]+' + [regex]::Escape($heading) + '[ \t]*\r?$(.*?)(?=^##[ \t]|\z)'
    $m = [regex]::Match($text, $pattern)
    if ($m.Success) { return $m.Groups[1].Value }
    return $null
}

# ORDER + CLAIM, one locale page at a time.
$pageText = @{}
foreach ($page in $decl.Pages) {
    $text = Read-RootFile $page.Path
    if ($null -eq $text) {
        Add-Finding 'ORDER' "$($page.Path) ($($page.Locale)) is missing - rule 8 keeps the page; update it, never delete it"
        continue
    }
    $pageText[$page.Locale] = $text
    $found = @([regex]::Matches($text, '(?m)^##[ \t]+(.+?)[ \t]*\r?$') | ForEach-Object { $_.Groups[1].Value })
    $last = -1
    foreach ($heading in $page.Headings) {
        $index = [array]::IndexOf($found, $heading)
        if ($index -lt 0) {
            Add-Finding 'ORDER' "$($page.Path): rule-1 section '## $heading' is absent"
        }
        elseif ($index -le $last) {
            Add-Finding 'ORDER' "$($page.Path): section '## $heading' stands before the section that must precede it"
        }
        else {
            $last = $index
        }
    }
}

foreach ($page in $decl.Pages) {
    if (-not $pageText.ContainsKey($page.Locale)) { continue }
    $neverHeading = $page.Headings[-1]
    $section = Get-SectionBody $pageText[$page.Locale] $neverHeading
    if ($null -eq $section) { continue }
    if (-not $section.Contains($page.PrivacyLink)) {
        Add-Finding 'CLAIM' "$($page.Path): '## $neverHeading' does not link $($page.PrivacyLink)"
    }
    $sectionPlain = Get-PlainText $section
    $privacyText = Read-RootFile $page.Privacy
    $privacyPlain = if ($null -eq $privacyText) { $null } else { Get-PlainText $privacyText }
    if ($null -eq $privacyPlain) {
        Add-Finding 'CLAIM' "$($page.Privacy) is missing - the '$neverHeading' facts have nothing to agree with"
    }
    foreach ($claim in @($decl.Claims | Where-Object { $_.Locale -eq $page.Locale })) {
        if ($sectionPlain.IndexOf($claim.Trust, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
            Add-Finding 'CLAIM' "$($page.Path): claim '$($claim.Id)' phrase '$($claim.Trust)' not found under '## $neverHeading' - reword back or update the declaration"
        }
        if ($null -ne $privacyPlain -and $privacyPlain.IndexOf($claim.Privacy, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
            Add-Finding 'CLAIM' "$($page.Privacy): counterpart '$($claim.Privacy)' of claim '$($claim.Id)' not found - the trust page states what the privacy policy does not"
        }
    }
}

# SURFACE - declared surfaces link their locale page.
$declared = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($surface in $decl.Surfaces) {
    [void]$declared.Add($surface.Path)
    $text = Read-RootFile $surface.Path
    if ($null -eq $text) {
        Add-Finding 'SURFACE' "$($surface.Path) is declared as a hand-out surface but does not exist - remove its row or restore the file"
    }
    elseif (-not $text.Contains($surface.Link)) {
        Add-Finding 'SURFACE' "$($surface.Path) hands out the APK but does not contain '$($surface.Link)'"
    }
}

# UNDECLARED - any other page carrying a hand-out marker.
$exempt = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($entry in $decl.Exempt) { [void]$exempt.Add($entry.Prefix) }
$candidates = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
foreach ($item in Get-ChildItem -LiteralPath $Root -Force) {
    if ($exempt.Contains($item.Name)) { continue }
    if ($item.PSIsContainer) {
        foreach ($file in Get-ChildItem -LiteralPath $item.FullName -Recurse -File -Include '*.md', '*.html' -ErrorAction SilentlyContinue) {
            if ($file.FullName -notmatch '[\\/]node_modules[\\/]') { $candidates.Add($file) }
        }
    }
    elseif ($item.Extension -in '.md', '.html') {
        $candidates.Add($item)
    }
}
$scanned = 0
foreach ($file in $candidates) {
    $relative = [IO.Path]::GetRelativePath($Root, $file.FullName) -replace '\\', '/'
    if ($declared.Contains($relative)) { continue }
    $scanned++
    $text = Get-Content -LiteralPath $file.FullName -Raw -Encoding utf8
    if ([string]::IsNullOrEmpty($text)) { continue }
    $marker = $decl.Markers | Where-Object { $text.Contains($_) } | Select-Object -First 1
    if ($marker) {
        Add-Finding 'UNDECLARED' "$relative hands out the APK ('$marker') but has no Surfaces row in install-trust.psd1 - declare it with the locale trust link it must carry"
    }
}

# CODE - the manifest and the dependency set agree with the "never does" claims.
$manifestDir = Join-Path $Root (Split-Path -Parent (Split-Path -Parent $decl.ManifestGlob))
$manifests = @()
if (Test-Path -LiteralPath $manifestDir) {
    $manifests = @(Get-ChildItem -Path (Join-Path $Root $decl.ManifestGlob) -File -ErrorAction SilentlyContinue)
}
foreach ($manifest in $manifests) {
    $text = Get-Content -LiteralPath $manifest.FullName -Raw -Encoding utf8
    $relative = [IO.Path]::GetRelativePath($Root, $manifest.FullName) -replace '\\', '/'
    foreach ($element in [regex]::Matches($text, '(?s)<uses-permission[^>]*>')) {
        foreach ($permission in $decl.ForbiddenPermissions) {
            if ($element.Value.Contains("`"$permission`"") -and $element.Value -notmatch 'tools:node\s*=\s*"remove"') {
                Add-Finding 'CODE' "$relative declares $permission - the trust page and the privacy policy say the app carries no advertising"
            }
        }
    }
}
foreach ($depFile in $decl.DependencyFiles) {
    $text = Read-RootFile $depFile
    if ($null -eq $text) { continue }
    foreach ($artifact in $decl.ForbiddenArtifacts) {
        if ($text -match ('(?<![\w-])' + [regex]::Escape($artifact) + '(?![\w-])')) {
            Add-Finding 'CODE' "$depFile names '$artifact' - the trust page says the app has no analytics and no advertising"
        }
    }
}

$summary = "pages=$($decl.Pages.Count) surfaces=$($decl.Surfaces.Count) scanned=$scanned manifests=$($manifests.Count)"
if ($failures.Count -eq 0) {
    Write-Host "assert-install-trust: PASS ($summary)" -ForegroundColor Green
    exit 0
}
Write-Host "assert-install-trust: FAIL - $($failures.Count) finding(s) ($summary)" -ForegroundColor Red
if ($Quiet) { $failures | ForEach-Object { Write-Host "  $_" } }
exit 1
