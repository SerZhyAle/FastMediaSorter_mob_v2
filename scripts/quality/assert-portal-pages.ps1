#requires -Version 7.0
<#
.SYNOPSIS
    S4256 - holds the shared media exchange portal to its section manifest, its three-language page set and
    its rendered schemes.

.DESCRIPTION
    Contract DOC-EXTERNAL-QUALITY 0.11 section 9, items F, H and I, over scripts/docs/portal-sections.json and
    the portal directory it names. Dimensions, each judging one subject:

      sections  item H: the manifest names a publisher and the programs; every section names an owner among
                them, once; every portal page belongs to a listed section and every listed section has pages,
                so a defect found on a page has an addressee.
      variants  item F: every section has a page in each declared language (<base>.md for English,
                <base>-<lang>.md for the rest); every diagrams/<name>-<lang>.svg exists in all of them; a page
                embeds no diagrams/ picture - Markdown image, src or srcset - of another language and none
                that is missing. Only diagrams/ is judged; the Russian-only from-windows/ cards it once
                skipped were removed by S4415.
      schemes   item I: scripts/docs/render-usage-schemes.ps1 -Check finds every picture current, so no scheme
                is hand-edited or left behind its label files.

    PER-TICKET by Rule 33: jekyll-gh-pages.yml publishes the portal with no Android release in between, and
    the writer who added a page, a variant or a label knows which set it belongs to.

.PARAMETER Dimension
    sections | variants | schemes | all (default).

.PARAMETER RepoRoot
    Repository root; overridable so the tests can judge a synthetic portal.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-portal-pages.ps1

.NOTES
    Exit codes:
      0  every requested dimension passed
      1  at least one finding (each is named)
      2  could not verify: the manifest is missing or malformed, the portal or the renderer is missing, or
         the renderer refused its own input
#>
[CmdletBinding()]
param(
    [ValidateSet('sections', 'variants', 'schemes', 'all')]
    [string] $Dimension = 'all',
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
Write-CheckSubject -Axes ([ordered]@{ module = 'site'; scope = "portal-pages:$Dimension"; files = 'scripts/docs/portal-sections.json,docs/media-exchange-portal' })

function Stop-CouldNotVerify([string]$Message) {
    Write-Host "assert-portal-pages: COULD NOT VERIFY - $Message" -ForegroundColor Red
    exit 2
}

$manifestPath = Join-Path $RepoRoot 'scripts/docs/portal-sections.json'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { Stop-CouldNotVerify 'scripts/docs/portal-sections.json not found' }
try { $manifest = Get-Content -Raw -Encoding utf8 -LiteralPath $manifestPath | ConvertFrom-Json }
catch { Stop-CouldNotVerify "scripts/docs/portal-sections.json is not valid JSON - $($_.Exception.Message)" }
foreach ($field in 'portal', 'publisher', 'languages', 'programs', 'sections') {
    if ($manifest.PSObject.Properties.Name -notcontains $field) { Stop-CouldNotVerify "scripts/docs/portal-sections.json lacks '$field'" }
}
$portalDir = Join-Path $RepoRoot $manifest.portal
if (-not (Test-Path -LiteralPath $portalDir -PathType Container)) { Stop-CouldNotVerify "portal directory $($manifest.portal) not found" }
$languages = @($manifest.languages)
if ($languages.Count -eq 0 -or $languages[0] -ne 'en') { Stop-CouldNotVerify "languages must start with 'en', the original (item G)" }
$translated = @($languages | Select-Object -Skip 1)

$findings = [System.Collections.Generic.List[string]]::new()
$programs = @($manifest.programs.PSObject.Properties.Name)
$sectionPages = @($manifest.sections | ForEach-Object { [string]$_.page })

# A page file's section and language: '<base>-<lang>.md' for a translated language, '<base>.md' for English.
# README.md is the picture brief, excluded from the site by _config.yml.
$pageFiles = @(Get-ChildItem -LiteralPath $portalDir -Filter *.md -File | Where-Object { $_.Name -ne 'README.md' })
$langPattern = if ($translated.Count -gt 0) { '^(?<base>.+?)-(?<lang>' + (($translated | ForEach-Object { [regex]::Escape($_) }) -join '|') + ')\.md$' } else { '^$' }
function Get-PageKey([string]$FileName) {
    if ($FileName -match $langPattern) { return [pscustomobject]@{ Base = $Matches.base; Lang = $Matches.lang } }
    return [pscustomobject]@{ Base = [IO.Path]::GetFileNameWithoutExtension($FileName); Lang = 'en' }
}

function Test-Sections {
    if ($programs -notcontains [string]$manifest.publisher) {
        $findings.Add("sections: publisher '$($manifest.publisher)' is not one of the programs ($($programs -join ', ')).")
    }
    $seen = @{}
    foreach ($section in $manifest.sections) {
        $page = [string]$section.page
        if (-not $page) { $findings.Add('sections: a section has no page name.'); continue }
        if ($seen.ContainsKey($page)) { $findings.Add("sections: page '$page' is listed twice.") }
        $seen[$page] = $true
        if ($section.PSObject.Properties.Name -notcontains 'owner' -or $programs -notcontains [string]$section.owner) {
            $findings.Add("sections: page '$page' names owner '$($section.owner)', which is not one of the programs ($($programs -join ', ')).")
        }
        if ($section.PSObject.Properties.Name -notcontains 'common' -or $section.common -isnot [bool]) {
            $findings.Add("sections: page '$page' must state common true or false.")
        }
    }
    $bases = @{}
    foreach ($file in $pageFiles) {
        $key = Get-PageKey $file.Name
        $bases[$key.Base] = $true
        if ($sectionPages -notcontains $key.Base) {
            $findings.Add("sections: $($manifest.portal)/$($file.Name) belongs to no section of scripts/docs/portal-sections.json; add '$($key.Base)' with its owning program.")
        }
    }
    foreach ($page in $sectionPages) {
        if (-not $bases.ContainsKey($page)) { $findings.Add("sections: section '$page' has no page under $($manifest.portal).") }
    }
}

function Test-Variants {
    foreach ($page in $sectionPages) {
        foreach ($lang in $languages) {
            $name = if ($lang -eq 'en') { "$page.md" } else { "$page-$lang.md" }
            if (-not (Test-Path -LiteralPath (Join-Path $portalDir $name))) {
                $findings.Add("variants: section '$page' lacks its $lang variant $($manifest.portal)/$name; the three variants are added and retired together (item F).")
            }
        }
    }
    $diagramDir = Join-Path $portalDir 'diagrams'
    $pictures = @{}
    if (Test-Path -LiteralPath $diagramDir) {
        foreach ($svg in Get-ChildItem -LiteralPath $diagramDir -Filter *.svg -File) {
            if ($svg.Name -notmatch ('^(?<name>.+)-(?<lang>' + (($languages | ForEach-Object { [regex]::Escape($_) }) -join '|') + ')\.svg$')) {
                $findings.Add("variants: diagrams/$($svg.Name) carries no language suffix; one picture exists per language (item I).")
                continue
            }
            if (-not $pictures.ContainsKey($Matches.name)) { $pictures[$Matches.name] = @{} }
            $pictures[$Matches.name][$Matches.lang] = $true
        }
    }
    foreach ($name in ($pictures.Keys | Sort-Object)) {
        foreach ($lang in $languages) {
            if (-not $pictures[$name].ContainsKey($lang)) { $findings.Add("variants: diagrams/$name-$lang.svg is missing; the picture exists in $((@($pictures[$name].Keys) | Sort-Object) -join ', ') only.") }
        }
    }
    foreach ($file in $pageFiles) {
        $key = Get-PageKey $file.Name
        $text = [IO.File]::ReadAllText($file.FullName)
        foreach ($m in [regex]::Matches($text, '(?:\]\(|(?:srcset|src)\s*=\s*["''])(?:\./)?diagrams/([^)"''\s]+)\.svg')) {
            $ref = $m.Groups[1].Value
            if ($ref -notmatch '^(?<name>.+)-(?<lang>[a-z]{2})$') { $findings.Add("variants: $($file.Name) embeds diagrams/$ref.svg, which carries no language suffix."); continue }
            if ($Matches.lang -ne $key.Lang) {
                $findings.Add("variants: $($file.Name) is the $($key.Lang) variant but embeds diagrams/$ref.svg; embed diagrams/$($Matches.name)-$($key.Lang).svg (item I).")
            }
            elseif (-not (Test-Path -LiteralPath (Join-Path $diagramDir "$ref.svg"))) {
                $findings.Add("variants: $($file.Name) embeds diagrams/$ref.svg, which does not exist.")
            }
        }
    }
}

function Test-Schemes {
    $renderer = Join-Path $RepoRoot 'scripts/docs/render-usage-schemes.ps1'
    if (-not (Test-Path -LiteralPath $renderer)) { Stop-CouldNotVerify 'scripts/docs/render-usage-schemes.ps1 not found' }
    $output = & pwsh -NoProfile -File $renderer -Check 2>&1 | Out-String
    $code = $LASTEXITCODE
    if ($code -eq 0) { return }
    $detail = (($output -split "\r?\n") | Where-Object { $_.Trim() } | Select-Object -First 8) -join ' | '
    if ($code -eq 1) {
        $findings.Add("schemes: a rendered picture is missing or stale; re-render with pwsh -NoProfile -File scripts/docs/render-usage-schemes.ps1 and never edit an SVG under diagrams/ by hand ($detail).")
        return
    }
    Stop-CouldNotVerify "render-usage-schemes.ps1 -Check exited $code - $detail"
}

$all = $Dimension -eq 'all'
if ($all -or $Dimension -eq 'sections') { Test-Sections }
if ($all -or $Dimension -eq 'variants') { Test-Variants }
if ($all -or $Dimension -eq 'schemes') { Test-Schemes }

if ($findings.Count -gt 0) {
    Write-Host "assert-portal-pages: FAIL ($($findings.Count) finding(s)):" -ForegroundColor Red
    foreach ($finding in $findings) { Write-Host "  - $finding" }
    exit 1
}
Write-Host "assert-portal-pages: PASS (dimension: $Dimension, $($sectionPages.Count) section(s) x $($languages.Count) language(s))" -ForegroundColor Green
exit 0
