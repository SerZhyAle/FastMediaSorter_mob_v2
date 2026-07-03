<#
.SYNOPSIS
    S0440 Phase 03 - render the "Settings: what is what" reference pages.

.DESCRIPTION
    Merges docs/settings/settings-manifest.json (structure + titles) with
    docs/settings/settings-annotations.json (descriptions) and the per-flavor
    SupportedMediaSection contributions parsed from the
    *SettingsSearchAvailabilityModule.kt source sets, then emits one Markdown
    reference per locale:
      - published: docs/SETTINGS_REFERENCE.md / _RU.md / _UK.md (standard family)
      - noLegal:   docs/SETTINGS_REFERENCE_noLegal.md (all-inclusive, gitignored)
    Output is deterministic (fixed section order, manifest order within a
    section, LF newlines, UTF-8 no BOM) so the Phase 04 gate can re-render and
    byte-diff to detect drift. Generated artifact - never hand-edited.
#>
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string] $OutDir
)

$ErrorActionPreference = 'Stop'
if (-not $OutDir) { $OutDir = Join-Path $RepoRoot 'docs' }

$manifestPath = Join-Path $RepoRoot 'docs/settings/settings-manifest.json'
$annotPath    = Join-Path $RepoRoot 'docs/settings/settings-annotations.json'
$flavorDir    = Join-Path $RepoRoot 'app_v2/src'

$entries = (Get-Content $manifestPath -Raw | ConvertFrom-Json).entries
$annot   = Get-Content $annotPath -Raw | ConvertFrom-Json

# S0889: per-section leading icon (owner decision - every section carries one; the 3
# groupings without a settings-header icon get a nearest app drawable). Assets generated
# by export-doc-icon-pngs.ps1 under docs/icons/doc/.
$docIconMap = Get-Content (Join-Path $RepoRoot 'docs/icons/doc-icon-map.json') -Raw | ConvertFrom-Json
$sectionIcon = @{}
foreach ($p in $docIconMap.settingsSections.PSObject.Properties) { $sectionIcon[$p.Name] = $p.Value }

# --- Parse per-flavor SupportedMediaSection contributions from source ----------
function Get-FlavorSections([string] $flavor) {
    $file = Join-Path $flavorDir "$flavor/java/com/sza/fastmediasorter/di/${flavor}SettingsSearchAvailabilityModule.kt"
    # source-set folder is camelCase (noLegal), class prefix is PascalCase
    if (-not (Test-Path $file)) {
        $cap = $flavor.Substring(0,1).ToUpper() + $flavor.Substring(1)
        $file = Join-Path $flavorDir "$flavor/java/com/sza/fastmediasorter/di/${cap}SettingsSearchAvailabilityModule.kt"
    }
    if (-not (Test-Path $file)) { return @() }
    $txt = Get-Content $file -Raw
    [regex]::Matches($txt, '@SupportedMediaSection\s+fun\s+\w+\(\)\s*:\s*String\s*=\s*"([^"]+)"') |
        ForEach-Object { $_.Groups[1].Value }
}

$flavorMedia = [ordered]@{}
foreach ($f in @('standard','lite','photos','legacy','vr','noLegal')) {
    $flavorMedia[$f] = @(Get-FlavorSections $f)
}
$mediaSections = @('images','video','audio','documents')
$publishedFlavors = @('standard','lite','photos','legacy')

# --- Localized labels ----------------------------------------------------------
$locales = @('en','ru','uk')
$sectionOrder = @('general','images','video','audio','documents','streams','other','playback','destinations')
$sectionLabel = @{
    general      = @{ en='General';      ru='Общие';            uk='Загальні' }
    images       = @{ en='Images';       ru='Изображения';      uk='Зображення' }
    video        = @{ en='Video';        ru='Видео';            uk='Відео' }
    audio        = @{ en='Audio';        ru='Аудио';            uk='Аудіо' }
    documents    = @{ en='Documents';    ru='Документы';        uk='Документи' }
    streams      = @{ en='Streams';      ru='Трансляции';       uk='Трансляції' }
    other        = @{ en='Other';        ru='Прочее';           uk='Інше' }
    playback     = @{ en='Playback';     ru='Воспроизведение';  uk='Відтворення' }
    destinations = @{ en='Destinations'; ru='Назначения';       uk='Призначення' }
}
$pageTitle = @{ en='Settings reference - what is what'; ru='Справочник настроек - что к чему'; uk='Довідник налаштувань - що до чого' }
$genNote   = @{ en='_Generated from the app. Do not edit by hand._'; ru='_Сгенерировано из приложения. Не редактируйте вручную._'; uk='_Згенеровано з застосунку. Не редагуйте вручну._' }
$colSetting= @{ en='Setting'; ru='Настройка'; uk='Налаштування' }
$colDoes   = @{ en='What it does'; ru='Что делает'; uk='Що робить' }
$availLead = @{ en='Available in:'; ru='Доступно в:'; uk='Доступно у:' }
$flavorName= @{ standard='Standard'; lite='Lite'; photos='Photos'; legacy='Legacy'; noLegal='noLegal' }

function Escape-Cell([string] $s) { if ($null -eq $s) { return '' } $s -replace '\|', '\|' }

function Render-Doc([string] $locale, [string[]] $flavorScope, [bool] $isNoLegal, [string] $permalinkName) {
    $title = $pageTitle[$locale]
    if ($isNoLegal) { $title += ' (noLegal)' }
    $sb = [System.Text.StringBuilder]::new()
    # Jekyll front matter must be the very first bytes so GitHub Pages renders the
    # page as HTML at the permalink instead of serving raw markdown.
    [void]$sb.Append("---`n")
    [void]$sb.Append("layout: default`n")
    [void]$sb.Append("title: `"$title`"`n")
    [void]$sb.Append("permalink: /docs/$permalinkName.html`n")
    [void]$sb.Append("---`n")
    [void]$sb.Append("<!-- GENERATED by scripts/docs/render-settings-reference.ps1 from settings-manifest.json + settings-annotations.json. Do not edit by hand. -->`n")
    [void]$sb.Append("# $title")
    [void]$sb.Append("`n`n$($genNote[$locale])`n")

    foreach ($sec in $sectionOrder) {
        $rows = @($entries | Where-Object { $_.sectionId -eq $sec })
        if (-not $rows.Count) { continue }
        # In published scope, drop a media section unavailable across the whole family.
        if (-not $isNoLegal -and $sec -in $mediaSections) {
            $inFamily = @($flavorScope | Where-Object { $sec -in $flavorMedia[$_] })
            if (-not $inFamily.Count) { continue }
        }
        $secIconMd = ''
        if ($sectionIcon.ContainsKey($sec)) {
            $secIconMd = '<img src="icons/doc/' + $sectionIcon[$sec] + '.png" alt="" width="22" height="22" style="vertical-align:text-bottom"> '
        }
        [void]$sb.Append("`n## $secIconMd$($sectionLabel[$sec][$locale])`n")
        if ($sec -in $mediaSections) {
            if ($isNoLegal) {
                $names = @($flavorName['noLegal'])
            } else {
                $names = @($flavorScope | Where-Object { $sec -in $flavorMedia[$_] } | ForEach-Object { $flavorName[$_] })
            }
            [void]$sb.Append("`n_$($availLead[$locale]) $([string]::Join(', ', $names))_`n")
        }
        [void]$sb.Append("`n| $($colSetting[$locale]) | $($colDoes[$locale]) |`n")
        [void]$sb.Append("|---|---|`n")
        foreach ($e in $rows) {
            $title = switch ($locale) { 'en' { $e.titleEn } 'ru' { $e.titleRu } 'uk' { $e.titleUk } }
            $desc = ''
            $a = $annot.$($e.key)
            if ($a) { $desc = $a.$locale }
            [void]$sb.Append("| $(Escape-Cell $title) | $(Escape-Cell $desc) |`n")
        }
    }
    return $sb.ToString()
}

$utf8 = [System.Text.UTF8Encoding]::new($false)
function Write-Doc([string] $path, [string] $content) {
    [System.IO.File]::WriteAllText($path, $content, $utf8)
    Write-Host "rendered -> $path"
}

# Published (standard family) EN/RU/UK
Write-Doc (Join-Path $OutDir 'SETTINGS_REFERENCE.md')    (Render-Doc 'en' $publishedFlavors $false 'SETTINGS_REFERENCE')
Write-Doc (Join-Path $OutDir 'SETTINGS_REFERENCE_RU.md') (Render-Doc 'ru' $publishedFlavors $false 'SETTINGS_REFERENCE_RU')
Write-Doc (Join-Path $OutDir 'SETTINGS_REFERENCE_UK.md') (Render-Doc 'uk' $publishedFlavors $false 'SETTINGS_REFERENCE_UK')
# noLegal all-inclusive (gitignored)
Write-Doc (Join-Path $OutDir 'SETTINGS_REFERENCE_noLegal.md') (Render-Doc 'en' @('noLegal') $true 'SETTINGS_REFERENCE_noLegal')

exit 0
