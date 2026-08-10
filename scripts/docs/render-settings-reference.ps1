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
# S1392: `vr` is a published Store build, so it belongs in the family the public reference
# enumerates - leaving it out made every "Available in:" line silently four-flavor.
$publishedFlavors = @('standard','lite','photos','legacy','vr')

# --- Localized labels ----------------------------------------------------------
$locales = @('en','ru','uk')
# S1313: the documentation-only sections (SettingsDocScopeCatalog) are appended after the
# nine settings-screen sections, so the published page still opens with the main tabs.
$sectionOrder = @('general','images','video','audio','documents','streams','other','playback','destinations','launcher','gestures','defaultApps','camera','translation','networkMonitor')
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
    launcher     = @{ en='Launcher';     ru='Лаунчер';          uk='Лаунчер' }
    gestures     = @{ en='Edge gestures';ru='Жесты по краю';    uk='Жести біля краю' }
    defaultApps  = @{ en='Default apps'; ru='Приложения по умолчанию'; uk='Застосунки за замовчуванням' }
    camera       = @{ en='Camera capture'; ru='Съёмка камерой'; uk='Зйомка камерою' }
    translation  = @{ en='On-screen translation'; ru='Перевод на экране'; uk='Переклад на екрані' }
    networkMonitor = @{ en='Network Monitor'; ru='Монитор сети'; uk='Монітор мережі' }
}
$pageTitle = @{ en='Settings reference - what is what'; ru='Справочник настроек - что к чему'; uk='Довідник налаштувань - що до чого' }
$genNote   = @{ en='_Generated from the app. Do not edit by hand._'; ru='_Сгенерировано из приложения. Не редактируйте вручную._'; uk='_Згенеровано з застосунку. Не редагуйте вручну._' }
$colSetting= @{ en='Setting'; ru='Настройка'; uk='Налаштування' }
$colDoes   = @{ en='What it does'; ru='Что делает'; uk='Що робить' }
$availLead = @{ en='Available in:'; ru='Доступно в:'; uk='Доступно у:' }
$flavorName= @{ standard='Standard'; lite='Lite'; photos='Photos'; legacy='Legacy'; vr='VR'; noLegal='noLegal' }

# S1313: "where to find it" line for each documentation-only section - a settings-screen path
# for a surface with a real entry point (SettingsDocScopeCatalog.hostKey non-empty), or a plain
# statement of which non-Settings UI hosts it when hostKey is empty. Keyed by sectionId, not
# derived from hostKey at render time, so the text stays human-authored and reviewable.
$docScopeSections = @('launcher','gestures','defaultApps','camera','translation','networkMonitor')
$docScopePath = @{
    launcher    = @{ en='Settings -> General -> System launcher settings'; ru='Настройки -> Общие -> Настройки системного лаунчера'; uk='Налаштування -> Загальні -> Налаштування системного лаунчера' }
    gestures    = @{ en='Settings -> Destinations -> Configure gestures'; ru='Настройки -> Назначения -> Настроить жесты'; uk='Налаштування -> Призначення -> Налаштувати жести' }
    defaultApps = @{ en='Settings -> Destinations -> Set as default'; ru='Настройки -> Назначения -> Задать по-умолчанию'; uk='Налаштування -> Призначення -> Задати за замовчуванням' }
    camera      = @{ en='Reached from the camera capture screen, not from Settings.'; ru='Доступно с экрана съёмки камерой, не из Настроек.'; uk='Доступно з екрана зйомки камерою, не з Налаштувань.' }
    translation = @{ en='Reached from the on-screen translation overlay, not from Settings.'; ru='Доступно из панели перевода на экране, не из Настроек.'; uk='Доступно з панелі перекладу на екрані, не з Налаштувань.' }
    networkMonitor = @{ en='Reached from Network Monitor -> Satellites, not from Settings.'; ru='Доступно из Монитора сети -> Спутники, не из Настроек.'; uk='Доступно з Монітора мережі -> Супутники, не з Налаштувань.' }
}

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
        if ($sec -in $docScopeSections) {
            [void]$sb.Append("`n_$($docScopePath[$sec][$locale])_`n")
        }
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
