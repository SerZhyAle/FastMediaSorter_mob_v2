<#
.SYNOPSIS
    A function page's availability marker, derived from the build (SITE-REPRESENTATION 0.1 rule 9, S4107).

.DESCRIPTION
    Dot-sourced by scripts/docs/generate-docs-pages.ps1, which renders the marker, and by
    scripts/quality/assert-site-facts.ps1 (dimension `availability`), which compares every published
    badge with it, so both print the same words for the same page.

    An English recipe declares what its function depends on in `availability:`, a comma-separated list
    of terms; the page exists wherever any of them holds (the union). A term is:

      all              no edition gate
      FLAG_NAME        a boolean flag of docs/flavors/flavor-matrix.json; the editions where it is true
      sourceSetName    a main source set of app_v2/build.gradle.kts; the editions that mount it, read by
                       scripts/quality/lib/flavor-source-map.ps1, because some features are gated by a
                       mounted source set and by no flag
      companion:<id>   a companion of docs/flavors/public-editions.psd1 (an app that is not an edition)

    Atoms joined with '+' form one term that holds where all of them hold (SUPPORT_CAST+SUPPORT_VIDEO).

    `devices:` names the device classes a function needs, from a fixed list. Translated recipes carry
    neither key: their marker is the English recipe's, looked up by page_id.

    The words are the only constants here, because they are language and not a product fact; the
    editions, their names and their order come from Get-SiteFacts.

    Sourced, never executed directly, so it declares no exit codes of its own.
#>

. (Join-Path $PSScriptRoot 'site-facts.ps1')
. (Join-Path $PSScriptRoot '../../quality/lib/flavor-source-map.ps1')

$script:PageAvailabilityWords = @{
    en = @{ All = 'All editions'; And = 'and' }
    ru = @{ All = 'Все редакции'; And = 'и' }
    uk = @{ All = 'Усі редакції'; And = 'і' }
}

# Device class -> label per language, portal glyph and badge modifier.
$script:PageAvailabilityDevices = [ordered]@{
    phone   = @{ en = 'Phone'; ru = 'Телефон'; uk = 'Телефон'; Glyph = 'device.phone'; Modifier = '' }
    tv      = @{ en = 'TV'; ru = 'ТВ'; uk = 'ТБ'; Glyph = 'device.tv'; Modifier = 'doc-device-badge--tv' }
    watch   = @{ en = 'Watch'; ru = 'Часы'; uk = 'Годинник'; Glyph = 'source.watch'; Modifier = 'doc-device-badge--wear' }
    headset = @{ en = 'VR headset'; ru = 'VR-гарнитура'; uk = 'VR-гарнітура'; Glyph = 'device.vr-headset'; Modifier = 'doc-device-badge--vr' }
}

function Get-PageAvailabilityDeviceVocabulary {
    return @($script:PageAvailabilityDevices.Keys)
}

function Get-PageAvailabilityContext {
    <#
    .OUTPUTS
        PSCustomObject: Facts (Get-SiteFacts), Flags (flag -> flavors where true), Sets (source set ->
        mounting flavors). Throws when a source cannot be read.
    #>
    param([Parameter(Mandatory)][string]$RepoRoot)

    $facts = Get-SiteFacts -RepoRoot $RepoRoot
    $matrix = Get-Content -LiteralPath $facts.MatrixPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $flags = @{}
    foreach ($flag in @($matrix.flags)) {
        $flags[[string]$flag] = @(foreach ($f in @($matrix.flavors)) { if ($matrix.matrix.$f.$flag -eq $true) { [string]$f } })
    }

    $map = Get-FlavorSourceMap -RepoRoot $RepoRoot
    if (@($map.Unparsed).Count -gt 0) { throw "page-availability: the source-set map of app_v2/build.gradle.kts has unparsed lines." }
    $sets = @{}
    foreach ($k in @($map.MainSetMounts.Keys)) { $sets[[string]$k] = @($map.MainSetMounts[$k]) }

    return [pscustomobject]@{ Facts = $facts; Flags = $flags; Sets = $sets }
}

function Resolve-PageAvailability {
    <#
    .OUTPUTS
        PSCustomObject: Editions (public flavors in declaration order), All (bool), Companions (ids),
        Errors (one line per term that does not resolve).
    #>
    param(
        [Parameter(Mandatory)]$Context,
        [AllowEmptyString()][string]$Terms
    )

    $errors = [System.Collections.Generic.List[string]]::new()
    $flavors = [System.Collections.Generic.HashSet[string]]::new()
    $companions = [System.Collections.Generic.List[string]]::new()
    $list = @(([string]$Terms).Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    if ($list.Count -eq 0) { $errors.Add('no availability term') }

    foreach ($term in $list) {
        if ($term -match '^companion:(?<id>.+)$') {
            $id = $Matches['id']
            if ($Context.Facts.CompanionNames.Contains($id)) { $companions.Add($id) }
            else { $errors.Add("'$term' names no companion with a display name in docs/flavors/public-editions.psd1") }
            continue
        }
        # A term joined with '+' holds where every atom holds: casting from the video player needs
        # both the cast stack and the video player, and no single flag says so.
        $termFlavors = $null
        foreach ($atom in @($term.Split('+') | ForEach-Object { $_.Trim() })) {
            $atomFlavors = Resolve-PageAvailabilityAtom -Context $Context -Atom $atom
            if ($null -eq $atomFlavors) {
                $errors.Add("'$atom' is not 'all', a flag of docs/flavors/flavor-matrix.json, a main source set of app_v2/build.gradle.kts or companion:<id>")
                $termFlavors = @()
                break
            }
            $termFlavors = if ($null -eq $termFlavors) { @($atomFlavors) } else { @($termFlavors | Where-Object { $_ -in $atomFlavors }) }
        }
        foreach ($f in @($termFlavors)) { $null = $flavors.Add($f) }
    }

    $editions = @($Context.Facts.PublicFlavors | Where-Object { $flavors.Contains($_) })
    if ($errors.Count -eq 0 -and $editions.Count -eq 0 -and $companions.Count -eq 0) {
        $errors.Add("'$Terms' resolves to no public edition and no companion")
    }
    return [pscustomobject]@{
        Editions   = $editions
        All        = ($editions.Count -eq @($Context.Facts.PublicFlavors).Count)
        Companions = @($companions)
        Errors     = @($errors)
    }
}

function Resolve-PageAvailabilityAtom {
    # One atom -> the flavors it holds in (comma-wrapped so an empty set is not $null), or $null when
    # it names nothing the build declares.
    param([Parameter(Mandatory)]$Context, [AllowEmptyString()][string]$Atom)
    if ($Atom -ceq 'all') { return ,@($Context.Facts.MatrixFlavors) }
    if ($Context.Flags.ContainsKey($Atom)) { return ,@($Context.Flags[$Atom]) }
    if ($Context.Sets.ContainsKey($Atom)) { return ,@($Context.Sets[$Atom]) }
    return $null
}

function Join-PageAvailabilityNames {
    param([string[]]$Names, [string]$And)
    if ($Names.Count -le 1) { return [string]::Join('', $Names) }
    return [string]::Join(', ', $Names[0..($Names.Count - 2)]) + " $And " + $Names[-1]
}

function Format-PageAvailabilityMarker {
    <# Resolved availability -> the badge text in en, ru or uk. #>
    param(
        [Parameter(Mandatory)]$Context,
        [Parameter(Mandatory)]$Resolved,
        [ValidateSet('en', 'ru', 'uk')][string]$Lang = 'en'
    )
    $words = $script:PageAvailabilityWords[$Lang]
    $parts = [System.Collections.Generic.List[string]]::new()
    if ($Resolved.All) {
        $parts.Add($words.All)
    } elseif (@($Resolved.Editions).Count -gt 0) {
        $parts.Add((Join-PageAvailabilityNames -Names @($Resolved.Editions | ForEach-Object { $Context.Facts.PublicNames[$_] }) -And $words.And))
    }
    foreach ($c in $Resolved.Companions) { $parts.Add($Context.Facts.CompanionNames[$c]) }
    return [string]::Join(', ', $parts)
}

function Get-PageAvailabilityDevice {
    <# Device class -> @{ Label; Glyph; Modifier }, or $null for a value outside the vocabulary. #>
    param([Parameter(Mandatory)][string]$Device, [ValidateSet('en', 'ru', 'uk')][string]$Lang = 'en')
    if (-not $script:PageAvailabilityDevices.Contains($Device)) { return $null }
    $d = $script:PageAvailabilityDevices[$Device]
    return [pscustomobject]@{ Label = $d[$Lang]; Glyph = $d.Glyph; Modifier = $d.Modifier }
}

function Split-PageAvailabilityDevices {
    param([AllowEmptyString()][string]$Devices)
    return @(([string]$Devices).Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

function Get-RecipeAvailabilityMap {
    <#
    .OUTPUTS
        Hashtable page_id -> @{ Availability; Devices; File } from the English recipes. Reads the front
        matter keys as plain scalars, which is the only shape the two keys take.
    #>
    param([Parameter(Mandatory)][string]$RepoRoot)
    $out = @{}
    $dir = Join-Path $RepoRoot 'docs/content/recipes'
    if (-not (Test-Path -LiteralPath $dir)) { return $out }
    foreach ($f in Get-ChildItem -LiteralPath $dir -Filter *.md -File) {
        $fm = Get-RecipeFrontMatterText -Path $f.FullName
        $pageId = Get-RecipeFrontMatterScalar -FrontMatter $fm -Key 'page_id'
        if (-not $pageId) { continue }
        $out[$pageId] = @{
            Availability = Get-RecipeFrontMatterScalar -FrontMatter $fm -Key 'availability'
            Devices      = Get-RecipeFrontMatterScalar -FrontMatter $fm -Key 'devices'
            File         = $f.FullName
        }
    }
    return $out
}

function Get-RecipeFrontMatterText {
    param([Parameter(Mandatory)][string]$Path)
    $raw = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    $m = [regex]::Match($raw, '\A﻿?---\r?\n(?<fm>.*?)\r?\n---', [System.Text.RegularExpressions.RegexOptions]::Singleline)
    if ($m.Success) { return $m.Groups['fm'].Value }
    return ''
}

function Get-RecipeFrontMatterScalar {
    param([AllowEmptyString()][string]$FrontMatter, [Parameter(Mandatory)][string]$Key)
    $m = [regex]::Match($FrontMatter, "(?m)^$([regex]::Escape($Key)):[ \t]*(?<v>[^\r\n]*)$")
    if (-not $m.Success) { return $null }
    $v = $m.Groups['v'].Value.Trim()
    if ($v.Length -ge 2 -and (($v[0] -eq '"' -and $v[-1] -eq '"') -or ($v[0] -eq "'" -and $v[-1] -eq "'"))) { $v = $v.Substring(1, $v.Length - 2) }
    return $v
}
