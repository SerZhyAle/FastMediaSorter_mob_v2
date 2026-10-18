#requires -Version 7.0
<#
.SYNOPSIS
    Generates the offline in-app help pages (CONTEXT-HELP 0.1) and their portal copies from one source per surface.

.DESCRIPTION
    Input, all read-only:
    - the help map (app_v2/src/main/assets/help/help-map.json): every row with form `page` or `both` names a help
      id, and a Settings-style row adds one help id per entry of its `pages[]`; ids are de-duplicated in map order;
    - one source per help id, <SourceRoot>/<help-id>.json, plus <SourceRoot>/index.json for the help index page;
    - the UI strings (app_v2/src/main/res/values[-<locale>]/strings*.xml): an element's name is the value of
      @string/<labelKey>, so a renamed button is renamed in its help by the same edit (rule 13);
    - docs/docs-pages-manifest.jsonl for `pageId` links into the site;
    - app_v2/src/main/assets/help/img/<help-id>-<locale>-light.webp (optional -dark.webp): a screenshot section is
      written only for a page whose light image exists, and the image is copied to the portal.

    Source schema (one file per help id; the index file carries only `help`, `locales.<l>.title`, `locales.<l>.purpose`):
        { "help": "main", "surface": "MainActivity",
          "elements": [ { "id": "filter", "viewId": "btnFilter", "labelKey": "filter" } ],
          "related":  [ { "help": "settings-general" } | { "pageId": "<page_id>" }
                        | { "url": "https://..", "label": { "en": "..", "ru": "..", "uk": ".." } } ],
          "locales":  { "en": { "title", "purpose", "steps": [ "text, may name a marker as (3)" ],
                                "orderNote": "used when there are no steps",
                                "groups": [ { "title", "elements": [ "<element id>" ] } ],
                                "elements": { "<element id>": { "summary", "details", "undo", "shortcut" } } },
                        "ru": { .. }, "uk": { .. } } }
    - Marker numbers are the 1-based position in the root `elements` array, in every locale.
    - `steps` holds at most seven items; a "(N)" in a step becomes a link to marker N.
    - With `groups`, every element belongs to exactly one group and the entries are listed per group under an <h3>.
    - `shortcut` is a key combination written with `+` ("Ctrl+Shift+F"); alternatives are separated by " / ".
    - An element without `labelKey` may carry `name` in the locale entry instead; a labelKey is the normal case.
    - `details` and `purpose` are plain text; a blank line starts a new paragraph. All text is HTML-escaped.

    Output, written only when every input is valid (all problems are listed first, nothing is half-written):
    - <OutAssets>/<locale>/<help-id>.html and <OutAssets>/<locale>/index.html (page links are <help-id>.html,
      css is ../help.css, images are ../img/..);
    - <OutAssets>/help.css, copied from the help.css beside this script;
    - <OutPortal>/<help-id>.html (en), <help-id>-<locale>.html (other locales) and the index the same way
      (page links carry the locale suffix, css is help.css, images are img/..), plus help.css and img/;
    - docs/DOCUMENT_REGISTRY.jsonl: one sitemap_exclude row per <OutPortal>/*.html under the docs-corpus-pages
      record, because a portal copy is not a site page (CONTEXT-HELP rule 10) and carries no permalink. Skipped
      when the registry is absent or -OutPortal is not the default folder.
    Files are UTF-8 without BOM, LF line endings, and are rewritten only when their content changes. A rerun with
    unchanged inputs and the same -Date is byte-identical. Pages carry no script, no remote font and no URL
    outside an <a href>; the generator checks that on every page it renders.

.PARAMETER RepoRoot
    Repository root. Default: three levels above this script's folder.

.PARAMETER Date
    Generation date (yyyy-MM-dd) printed in every footer. Default: today. Pin it to reproduce a run byte for byte.

.PARAMETER Locales
    Locales to generate. Default: en, ru, uk. Every source needs a block for each; a string missing from a
    non-English values folder falls back to English. Comma-separated values are accepted.

.PARAMETER SourceRoot
    Folder holding <help-id>.json for every mapped help id and index.json. Default: <RepoRoot>/docs/content/help.

.PARAMETER MapPath
    The help map. Default: <RepoRoot>/app_v2/src/main/assets/help/help-map.json.

.PARAMETER ImageRoot
    Folder searched for the screenshots. Default: <RepoRoot>/app_v2/src/main/assets/help/img.

.PARAMETER OutAssets
    Folder receiving the in-app pages and help.css. Default: <RepoRoot>/app_v2/src/main/assets/help.

.PARAMETER OutPortal
    Folder receiving the portal copies, help.css and img/. Default: <RepoRoot>/documentation/help.

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/help/generate-help-pages.ps1

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/help/generate-help-pages.ps1 -Date 2026-10-16 -Locales en,ru,uk

.NOTES
    Exit codes:
      0 - pages written (or already up to date)
      1 - source error: a mapped help id has no source file, a source is invalid or lacks a locale, a labelKey is
          missing from the English strings, a related target does not resolve, or a rendered page breaks the
          offline rules
      2 - input not found: the help map, the page manifest, the English string folder or help.css
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = (Join-Path $PSScriptRoot '../../..'),
    [ValidatePattern('^\d{4}-\d{2}-\d{2}$')]
    [string]$Date = (Get-Date -Format 'yyyy-MM-dd'),
    [string[]]$Locales = @('en', 'ru', 'uk'),
    [string]$SourceRoot = '',
    [string]$MapPath = '',
    [string]$ImageRoot = '',
    [string]$OutAssets = '',
    [string]$OutPortal = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '../lib/site-addresses.ps1')

$script:SourceLocale = 'en'
$script:SiteBase = 'https://serzhyale.github.io/FastMediaSorter_mob_v2/'
$script:SupportUrl = 'https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues'
$script:RtlLocales = @('ar', 'ur')
$script:MaxSteps = 7

# Section headings and small labels. A locale without a row falls back to English (rule 13).
$script:Ui = @{
    en = @{
        Help = 'Help'; Purpose = 'What this is for'; Order = 'In this order'; Elements = 'The elements'
        Related = 'Related'; Index = 'Help index'; Undo = 'Undo'; Shortcut = 'Shortcut'
        Support = 'Ask a question or report a problem'
        KindScreen = 'Screen'; KindGuide = 'Guide'; KindLink = 'Link'
    }
    ru = @{
        Help = 'Справка'; Purpose = 'Для чего этот экран'; Order = 'Порядок действий'; Elements = 'Элементы'
        Related = 'Связанное'; Index = 'Оглавление справки'; Undo = 'Отмена'; Shortcut = 'Сочетание клавиш'
        Support = 'Задать вопрос или сообщить о проблеме'
        KindScreen = 'Экран'; KindGuide = 'Руководство'; KindLink = 'Ссылка'
    }
    uk = @{
        Help = 'Довідка'; Purpose = 'Для чого цей екран'; Order = 'Порядок дій'; Elements = 'Елементи'
        Related = 'Пов''язане'; Index = 'Зміст довідки'; Undo = 'Скасування'; Shortcut = 'Сполучення клавіш'
        Support = 'Поставити запитання або повідомити про проблему'
        KindScreen = 'Екран'; KindGuide = 'Посібник'; KindLink = 'Посилання'
    }
}

$script:Problems = [System.Collections.Generic.List[string]]::new()
$script:ProblemSeen = [System.Collections.Generic.HashSet[string]]::new()
$script:Notes = [System.Collections.Generic.SortedSet[string]]::new()
$script:StringCache = @{}
$script:Titles = @{}
$script:PageIds = @()
$script:Manifest = @{}
$script:ImageCopies = [ordered]@{}
$script:Cfg = $null

# ---------- small helpers ----------

function Add-Problem {
    param([Parameter(Mandatory)][string]$Message)
    if ($script:ProblemSeen.Add($Message)) { $script:Problems.Add($Message) }
}

function Write-Problem {
    param([Parameter(Mandatory)][string]$Message)
    [Console]::Error.WriteLine("ERROR: $Message")
}

function Get-Prop {
    # Returns nothing (not $null) for an absent member, so @(Get-Prop ..) is an empty array.
    param($Object, [string]$Name)
    if ($null -eq $Object) { return }
    $member = $Object.PSObject.Properties[$Name]
    if ($null -eq $member) { return }
    return $member.Value
}

function ConvertTo-HtmlText {
    param([string]$Text)
    return $Text.Replace('&', '&amp;').Replace('<', '&lt;').Replace('>', '&gt;').Replace('"', '&quot;')
}

function ConvertTo-OneLine {
    param([string]$Text)
    return ([regex]::Replace($Text, '\s+', ' ')).Trim()
}

function ConvertTo-ParagraphsHtml {
    param([string]$Text, [string]$Class = '')
    $open = if ($Class) { "<p class=`"$Class`">" } else { '<p>' }
    $parts = [regex]::Split($Text.Trim(), '\r?\n[ \t]*\r?\n')
    foreach ($part in $parts) {
        $line = ConvertTo-OneLine $part
        if ($line) { $open + (ConvertTo-HtmlText $line) + '</p>' }
    }
}

function ConvertTo-StepHtml {
    param([string]$Text, [string[]]$MarkerIds)
    $html = ConvertTo-HtmlText (ConvertTo-OneLine $Text)
    return [regex]::Replace($html, '\((\d{1,2})\)', {
            param($match)
            $n = [int]$match.Groups[1].Value
            if ($n -ge 1 -and $n -le $MarkerIds.Count) {
                return "<a class=`"ref`" href=`"#$($MarkerIds[$n - 1])`">$n</a>"
            }
            return $match.Value
        })
}

function ConvertTo-ShortcutHtml {
    param([string]$Text)
    $alternatives = foreach ($alt in [regex]::Split($Text.Trim(), '\s+/\s+')) {
        $keys = foreach ($key in [regex]::Split($alt, '\s*\+\s*')) {
            if ($key) { '<kbd>' + (ConvertTo-HtmlText $key) + '</kbd>' }
        }
        $keys -join ' + '
    }
    return $alternatives -join ' / '
}

function Read-JsonFile {
    param([Parameter(Mandatory)][string]$Path)
    try {
        $value = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
    } catch {
        Add-Problem "cannot read ${Path}: $($_.Exception.Message)"
        return
    }
    if ($null -eq $value) { Add-Problem "empty JSON: $Path"; return }
    return $value
}

function Write-TextFile {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$Text)
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    $bytes = $utf8.GetBytes($Text)
    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        $existing = [System.IO.File]::ReadAllBytes($Path)
        if ([Convert]::ToBase64String($existing) -ceq [Convert]::ToBase64String($bytes)) { return $false }
    }
    $dir = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $dir)) { $null = New-Item -ItemType Directory -Path $dir -Force }
    [System.IO.File]::WriteAllBytes($Path, $bytes)
    return $true
}

function Copy-FileIfChanged {
    param([Parameter(Mandatory)][string]$Source, [Parameter(Mandatory)][string]$Destination)
    if (Test-Path -LiteralPath $Destination -PathType Leaf) {
        if ((Get-FileHash -LiteralPath $Source).Hash -eq (Get-FileHash -LiteralPath $Destination).Hash) { return $false }
    }
    $dir = Split-Path -Parent $Destination
    if (-not (Test-Path -LiteralPath $dir)) { $null = New-Item -ItemType Directory -Path $dir -Force }
    Copy-Item -LiteralPath $Source -Destination $Destination -Force
    return $true
}

# ---------- configuration and inputs ----------

function Initialize-Config {
    $root = [System.IO.Path]::GetFullPath($RepoRoot)
    $pick = { param($given, $default) if ($given) { [System.IO.Path]::GetFullPath($given) } else { [System.IO.Path]::GetFullPath($default) } }
    $tags = foreach ($entry in $Locales) { foreach ($tag in ($entry -split ',')) { $tag.Trim() } }
    $tags = @($tags | Where-Object { $_ } | Select-Object -Unique)
    foreach ($tag in $tags) {
        if ($tag -notmatch '^[a-z]{2,3}(-[A-Za-z0-9]+)*$') { throw "invalid locale tag '$tag'" }
    }
    $script:Cfg = @{
        Root         = $root
        Locales      = $tags
        SourceRoot   = & $pick $SourceRoot (Join-Path $root 'docs/content/help')
        MapPath      = & $pick $MapPath (Join-Path $root 'app_v2/src/main/assets/help/help-map.json')
        ImageRoot    = & $pick $ImageRoot (Join-Path $root 'app_v2/src/main/assets/help/img')
        OutAssets    = & $pick $OutAssets (Join-Path $root 'app_v2/src/main/assets/help')
        OutPortal    = & $pick $OutPortal (Join-Path $root 'documentation/help')
        ResRoot      = Join-Path $root 'app_v2/src/main/res'
        Gradle       = Join-Path $root 'app_v2/build.gradle.kts'
        PageManifest = Join-Path $root 'docs/docs-pages-manifest.jsonl'
        CssSource    = Join-Path $PSScriptRoot 'help.css'
    }
}

function Get-ValuesFolderName {
    param([string]$Locale)
    if ($Locale -eq $script:SourceLocale) { return 'values' }
    if ($Locale.Contains('-')) { return 'values-b+' + $Locale.Replace('-', '+') }
    return "values-$Locale"
}

function ConvertFrom-AndroidString {
    # What aapt does to a <string> body: a quoted value keeps its spaces, an unquoted one collapses them,
    # and a backslash escapes the next character (\' \" \n \t \uXXXX \@ \?).
    param([string]$Raw)
    $value = $Raw.Trim()
    if ($value.Length -ge 2 -and $value.StartsWith('"') -and $value.EndsWith('"')) {
        $value = $value.Substring(1, $value.Length - 2)
    } else {
        $value = ConvertTo-OneLine $value
    }
    return [regex]::Replace($value, '\\(u[0-9a-fA-F]{4}|.)', {
            param($match)
            $token = $match.Groups[1].Value
            if ($token.Length -eq 5) { return [string][char][Convert]::ToInt32($token.Substring(1), 16) }
            if ($token -ceq 'n') { return "`n" }
            if ($token -ceq 't') { return "`t" }
            return $token
        })
}

function Get-StringTable {
    param([Parameter(Mandatory)][string]$Locale)
    if ($script:StringCache.ContainsKey($Locale)) { return $script:StringCache[$Locale] }
    $table = @{}
    $folder = Join-Path $script:Cfg.ResRoot (Get-ValuesFolderName $Locale)
    if (Test-Path -LiteralPath $folder -PathType Container) {
        foreach ($file in Get-ChildItem -LiteralPath $folder -Filter 'strings*.xml' -File | Sort-Object Name) {
            $doc = [System.Xml.XmlDocument]::new()
            try { $doc.Load($file.FullName) } catch {
                Add-Problem "cannot parse $($file.FullName): $($_.Exception.Message)"
                continue
            }
            foreach ($node in $doc.SelectNodes('/resources/string')) {
                $table[$node.GetAttribute('name')] = ConvertFrom-AndroidString $node.InnerText
            }
        }
    } else {
        $null = $script:Notes.Add("no string folder for '$Locale' ($folder): every name falls back to $($script:SourceLocale)")
    }
    $script:StringCache[$Locale] = $table
    return $table
}

function Get-AppVersionName {
    # A literal versionName only. This repo computes it (override, stamp, sentinel), so the footer then carries the date alone.
    if (-not (Test-Path -LiteralPath $script:Cfg.Gradle -PathType Leaf)) { return }
    $text = Get-Content -LiteralPath $script:Cfg.Gradle -Raw -Encoding UTF8
    $match = [regex]::Match($text, '(?m)^\s*versionName\s*=\s*"([^"$]+)"')
    if ($match.Success) { return $match.Groups[1].Value }
}

function Get-HelpMapPages {
    param($Map)
    $ids = [System.Collections.Generic.List[string]]::new()
    $take = {
        param($row, $where)
        $id = Get-Prop $row 'help'
        if (-not $id) { Add-Problem "help map: $where has form page but no help id"; return }
        if ($id -notmatch '^[a-z0-9][a-z0-9-]*$') { Add-Problem "help map: $where has an invalid help id '$id'"; return }
        if ($id -eq 'index') { Add-Problem "help map: $where uses the reserved help id 'index'"; return }
        if (-not $ids.Contains($id)) { $ids.Add($id) }
    }
    foreach ($surface in @(Get-Prop $Map 'surfaces')) {
        $form = Get-Prop $surface 'form'
        if ($form -notin 'page', 'both') { continue }
        $sid = Get-Prop $surface 'id'
        & $take $surface "surface '$sid'"
        foreach ($page in @(Get-Prop $surface 'pages')) {
            $pageForm = Get-Prop $page 'form'
            if ($pageForm -and $pageForm -notin 'page', 'both') { continue }
            & $take $page "page '$(Get-Prop $page 'id')' of '$sid'"
        }
    }
    return , $ids.ToArray()
}

function Import-PageManifest {
    foreach ($line in Get-Content -LiteralPath $script:Cfg.PageManifest -Encoding UTF8) {
        if (-not $line.Trim()) { continue }
        $row = $line | ConvertFrom-Json
        $script:Manifest[[string](Get-Prop $row 'page_id')] = $row
    }
}

# ---------- source validation ----------

function Test-SourceLocale {
    param([string]$Path, $Json, [string]$Locale, [string[]]$ElementIds, [bool]$IsIndex)
    $where = "$Path [$Locale]"
    $block = Get-Prop (Get-Prop $Json 'locales') $Locale
    if ($null -eq $block) { Add-Problem "${where}: locale block is missing"; return }
    foreach ($field in 'title', 'purpose') {
        if (-not (Get-Prop $block $field)) { Add-Problem "${where}: '$field' is missing or empty" }
    }
    if ($IsIndex) { return }

    $steps = @(Get-Prop $block 'steps')
    if ($steps.Count -eq 0 -and -not (Get-Prop $block 'orderNote')) {
        Add-Problem "${where}: needs 'steps' or 'orderNote' (anatomy item 4)"
    }
    if ($steps.Count -gt $script:MaxSteps) {
        Add-Problem "${where}: $($steps.Count) steps, at most $($script:MaxSteps) allowed (rule 8)"
    }

    $entries = Get-Prop $block 'elements'
    foreach ($id in $ElementIds) {
        $entry = Get-Prop $entries $id
        if ($null -eq $entry) { Add-Problem "${where}: no entry for element '$id'"; continue }
        foreach ($field in 'summary', 'details') {
            if (-not (Get-Prop $entry $field)) { Add-Problem "${where}: element '$id' has no '$field'" }
        }
    }

    $groups = @(Get-Prop $block 'groups')
    if ($groups.Count -gt 0) {
        $placed = [System.Collections.Generic.List[string]]::new()
        foreach ($group in $groups) {
            if (-not (Get-Prop $group 'title')) { Add-Problem "${where}: a group has no title" }
            foreach ($member in @(Get-Prop $group 'elements')) {
                if ($member -notin $ElementIds) { Add-Problem "${where}: group member '$member' is not in the root elements"; continue }
                if ($placed.Contains($member)) { Add-Problem "${where}: element '$member' is in two groups"; continue }
                $placed.Add($member)
            }
        }
        foreach ($id in $ElementIds) {
            if (-not $placed.Contains($id)) { Add-Problem "${where}: element '$id' is in no group" }
        }
    }
}

function Read-HelpSource {
    param([Parameter(Mandatory)][string]$Id, [switch]$IsIndex)
    $path = Join-Path $script:Cfg.SourceRoot "$Id.json"
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Add-Problem "missing source for help id '$Id': $path"
        return
    }
    $json = Read-JsonFile $path
    if ($null -eq $json) { return }
    if ((Get-Prop $json 'help') -ne $Id) {
        Add-Problem "${path}: 'help' is '$(Get-Prop $json 'help')', expected '$Id'"
    }
    $ids = [System.Collections.Generic.List[string]]::new()
    if (-not $IsIndex) {
        foreach ($element in @(Get-Prop $json 'elements')) {
            $eid = [string](Get-Prop $element 'id')
            if ($eid -notmatch '^[A-Za-z][A-Za-z0-9_.-]*$') { Add-Problem "${path}: invalid element id '$eid'"; continue }
            if ($ids.Contains($eid)) { Add-Problem "${path}: duplicate element id '$eid'"; continue }
            $ids.Add($eid)
        }
    }
    $script:Titles[$Id] = @{}
    foreach ($locale in $script:Cfg.Locales) {
        Test-SourceLocale -Path $path -Json $json -Locale $locale -ElementIds $ids.ToArray() -IsIndex $IsIndex.IsPresent
        $title = Get-Prop (Get-Prop (Get-Prop $json 'locales') $locale) 'title'
        if ($title) { $script:Titles[$Id][$locale] = ConvertTo-OneLine $title }
    }
    return $json
}

# ---------- hrefs and ui text ----------

function Get-PageTitle {
    param([string]$Id, [string]$Locale)
    if ($script:Titles.ContainsKey($Id) -and $script:Titles[$Id].ContainsKey($Locale)) { return $script:Titles[$Id][$Locale] }
}

function Get-Ui {
    param([string]$Locale)
    if ($script:Ui.ContainsKey($Locale)) { return $script:Ui[$Locale] }
    $null = $script:Notes.Add("no heading table for '$Locale': headings fall back to $($script:SourceLocale)")
    return $script:Ui[$script:SourceLocale]
}

function Get-PageHref {
    param([string]$Id, [string]$Locale, [string]$Target)
    if ($Target -eq 'portal' -and $Locale -ne $script:SourceLocale) { return "$Id-$($Locale.ToLowerInvariant()).html" }
    return "$Id.html"
}

function Get-CssHref {
    param([string]$Target)
    if ($Target -eq 'assets') { return '../help.css' }
    return 'help.css'
}

function Get-ImageHref {
    param([string]$Target, [string]$File)
    if ($Target -eq 'assets') { return "../img/$File" }
    return "img/$File"
}

function Get-SitePageHref {
    param([string]$CanonicalPath, [string]$Locale)
    $path = $CanonicalPath.TrimStart('/')
    if ($Locale -ne $script:SourceLocale) {
        $path = [regex]::Replace($path, '\.html$', "-$($Locale.ToLowerInvariant()).html")
    }
    return $script:SiteBase + $path
}

function Get-OutputPath {
    param([string]$Id, [string]$Locale, [string]$Target)
    if ($Target -eq 'assets') { return Join-Path (Join-Path $script:Cfg.OutAssets $Locale) "$Id.html" }
    return Join-Path $script:Cfg.OutPortal (Get-PageHref $Id $Locale 'portal')
}

# ---------- rendering ----------

function Resolve-ElementName {
    param($Element, [string]$HelpId, [string]$Locale, $Entry)
    $key = [string](Get-Prop $Element 'labelKey')
    if (-not $key) {
        $name = Get-Prop $Entry 'name'
        if ($name) { return ConvertTo-OneLine $name }
        Add-Problem "help '$HelpId': element '$(Get-Prop $Element 'id')' has neither labelKey nor a localized name"
        return ''
    }
    $english = (Get-StringTable $script:SourceLocale)[$key]
    if ($null -eq $english) {
        Add-Problem "help '$HelpId': element '$(Get-Prop $Element 'id')': labelKey '$key' is not in values/strings*.xml"
        return ''
    }
    if ($Locale -eq $script:SourceLocale) { return $english }
    $local = (Get-StringTable $Locale)[$key]
    if ($null -eq $local) {
        $null = $script:Notes.Add("labelKey '$key' is missing in ${Locale}: the English name is used")
        return $english
    }
    return $local
}

function New-ElementHtml {
    param([string]$Id, [int]$Number, [string]$Name, $Entry, $Ui)
    $lines = [System.Collections.Generic.List[string]]::new()
    $summary = ConvertTo-HtmlText (ConvertTo-OneLine ([string](Get-Prop $Entry 'summary')))
    $lines.Add("<details class=`"el`" id=`"$Id`">")
    $lines.Add("<summary><span class=`"mk`">$Number</span><span><span class=`"name`">$(ConvertTo-HtmlText $Name)</span><span class=`"one`">$summary</span></span></summary>")
    $lines.Add('<div class="full">')
    foreach ($paragraph in @(ConvertTo-ParagraphsHtml ([string](Get-Prop $Entry 'details')))) { $lines.Add($paragraph) }
    $undo = [string](Get-Prop $Entry 'undo')
    $shortcut = [string](Get-Prop $Entry 'shortcut')
    if ($undo -or $shortcut) {
        $lines.Add('<dl class="facts">')
        if ($undo) { $lines.Add("<dt>$($Ui.Undo)</dt><dd>$(ConvertTo-HtmlText (ConvertTo-OneLine $undo))</dd>") }
        if ($shortcut) { $lines.Add("<dt>$($Ui.Shortcut)</dt><dd>$(ConvertTo-ShortcutHtml $shortcut)</dd>") }
        $lines.Add('</dl>')
    }
    $lines.Add('</div>')
    $lines.Add('</details>')
    return $lines.ToArray()
}

function New-RelatedHtml {
    param($Source, [string]$HelpId, [string]$Locale, [string]$Target, $Ui)
    $items = [System.Collections.Generic.List[string]]::new()
    foreach ($rel in @(Get-Prop $Source 'related')) {
        $helpRef = Get-Prop $rel 'help'
        $pageRef = Get-Prop $rel 'pageId'
        $urlRef = Get-Prop $rel 'url'
        if ($helpRef) {
            $title = if ($helpRef -eq 'index') { $Ui.Index } else { Get-PageTitle $helpRef $Locale }
            if ($helpRef -ne 'index' -and $helpRef -notin $script:PageIds) {
                Add-Problem "help '$HelpId': related help '$helpRef' is not a page of the help map"; continue
            }
            if (-not $title) { Add-Problem "help '$HelpId': related help '$helpRef' has no title in $Locale"; continue }
            $href = Get-PageHref $helpRef $Locale $Target
            $items.Add("<li><a href=`"$href`"><b>$(ConvertTo-HtmlText $title)</b><span class=`"pill`">$($Ui.KindScreen)</span></a></li>")
        } elseif ($pageRef) {
            $row = $script:Manifest[[string]$pageRef]
            if ($null -eq $row) { Add-Problem "help '$HelpId': related pageId '$pageRef' is not in docs-pages-manifest.jsonl"; continue }
            if ((Get-Prop $row 'is_published') -eq $false) { Add-Problem "help '$HelpId': related pageId '$pageRef' is not published"; continue }
            $href = Get-SitePageHref ([string](Get-Prop $row 'canonical_path')) $Locale
            $items.Add("<li><a href=`"$href`"><b>$(ConvertTo-HtmlText ([string](Get-Prop $row 'title')))</b><span class=`"pill gold`">$($Ui.KindGuide)</span></a></li>")
        } elseif ($urlRef) {
            if ($urlRef -notmatch '^https://') { Add-Problem "help '$HelpId': related url '$urlRef' is not https"; continue }
            $labels = Get-Prop $rel 'label'
            $label = Get-Prop $labels $Locale
            if (-not $label) { $label = Get-Prop $labels $script:SourceLocale }
            if (-not $label) { Add-Problem "help '$HelpId': related url '$urlRef' has no label"; continue }
            $items.Add("<li><a href=`"$(ConvertTo-HtmlText $urlRef)`"><b>$(ConvertTo-HtmlText ([string]$label))</b><span class=`"pill gold`">$($Ui.KindLink)</span></a></li>")
        } else {
            Add-Problem "help '$HelpId': a related entry has none of help, pageId, url"
        }
    }
    $items.Add("<li><a href=`"$(Get-PageHref 'index' $Locale $Target)`"><b>$(ConvertTo-HtmlText $Ui.Index)</b></a></li>")
    return @('<ul class="related">') + $items.ToArray() + @('</ul>')
}

function New-FooterHtml {
    param([string]$Locale, [string]$Target, $Ui, [string]$ProductName, [string]$Version)
    $stamp = $ProductName
    if ($Version) { $stamp += " $Version" }
    $stamp += ", $Date"
    return @(
        '<footer class="help-foot">',
        "<span>$(ConvertTo-HtmlText $stamp)</span>",
        "<a href=`"$(Get-PageHref 'index' $Locale $Target)`">$(ConvertTo-HtmlText $Ui.Index)</a>",
        "<a href=`"$($script:SupportUrl)`">$(ConvertTo-HtmlText $Ui.Support)</a>",
        '</footer>'
    )
}

function New-ScreenshotHtml {
    param([string]$HelpId, [string]$Locale, [string]$Target, [string]$Title)
    $light = "$HelpId-$Locale-light.webp"
    $dark = "$HelpId-$Locale-dark.webp"
    $lightPath = Join-Path $script:Cfg.ImageRoot $light
    if (-not (Test-Path -LiteralPath $lightPath -PathType Leaf)) { return @() }
    $alt = ConvertTo-HtmlText $Title
    $hasDark = Test-Path -LiteralPath (Join-Path $script:Cfg.ImageRoot $dark) -PathType Leaf
    if ($Target -eq 'portal') {
        $script:ImageCopies[(Join-Path (Join-Path $script:Cfg.OutPortal 'img') $light)] = $lightPath
        if ($hasDark) {
            $script:ImageCopies[(Join-Path (Join-Path $script:Cfg.OutPortal 'img') $dark)] = Join-Path $script:Cfg.ImageRoot $dark
        }
    }
    $lines = @('<figure class="shot">')
    if ($hasDark) {
        $lines += "<img class=`"on-light`" src=`"$(Get-ImageHref $Target $light)`" alt=`"$alt`">"
        $lines += "<img class=`"on-dark`" src=`"$(Get-ImageHref $Target $dark)`" alt=`"$alt`">"
    } else {
        $lines += "<img src=`"$(Get-ImageHref $Target $light)`" alt=`"$alt`">"
    }
    return $lines + @('</figure>')
}

function New-HelpPageBody {
    param($Source, [string]$HelpId, [string]$Locale, [string]$Target, [string]$ProductName, [string]$Version)
    $ui = Get-Ui $Locale
    $block = Get-Prop (Get-Prop $Source 'locales') $Locale
    $title = ConvertTo-OneLine ([string](Get-Prop $block 'title'))
    $roots = @(Get-Prop $Source 'elements')
    $markerIds = @($roots | ForEach-Object { [string](Get-Prop $_ 'id') })
    $entries = Get-Prop $block 'elements'

    $body = [System.Collections.Generic.List[string]]::new()
    $indexHref = Get-PageHref 'index' $Locale $Target
    $body.Add("<nav class=`"help-crumbs`" aria-label=`"$(ConvertTo-HtmlText $ui.Index)`">")
    $body.Add("<a href=`"$indexHref`">$(ConvertTo-HtmlText $ui.Index)</a><span class=`"sep`" aria-hidden=`"true`">/</span><span>$(ConvertTo-HtmlText $title)</span>")
    $body.Add('</nav>')
    $body.Add('<header class="hero">')
    $body.Add("<h1>$(ConvertTo-HtmlText $title)</h1>")
    $body.Add('</header>')

    $body.Add("<h2>$($ui.Purpose)</h2>")
    foreach ($paragraph in @(ConvertTo-ParagraphsHtml ([string](Get-Prop $block 'purpose')) 'lead')) { $body.Add($paragraph) }

    foreach ($line in @(New-ScreenshotHtml $HelpId $Locale $Target $title)) { $body.Add($line) }

    $body.Add("<h2>$($ui.Order)</h2>")
    $steps = @(Get-Prop $block 'steps')
    if ($steps.Count -gt 0) {
        $body.Add('<ol class="order">')
        foreach ($step in $steps) { $body.Add('<li>' + (ConvertTo-StepHtml ([string]$step) $markerIds) + '</li>') }
        $body.Add('</ol>')
    } else {
        $body.Add('<p class="order-note">' + (ConvertTo-HtmlText (ConvertTo-OneLine ([string](Get-Prop $block 'orderNote')))) + '</p>')
    }

    if ($roots.Count -gt 0) {
        $body.Add("<h2>$($ui.Elements)</h2>")
        $body.Add('<div class="elements">')
        $rendered = @{}
        foreach ($root in $roots) {
            $id = [string](Get-Prop $root 'id')
            $entry = Get-Prop $entries $id
            if ($null -eq $entry) { continue }
            $number = [array]::IndexOf($markerIds, $id) + 1
            $name = Resolve-ElementName -Element $root -HelpId $HelpId -Locale $Locale -Entry $entry
            $rendered[$id] = New-ElementHtml -Id $id -Number $number -Name $name -Entry $entry -Ui $ui
        }
        $groups = @(Get-Prop $block 'groups')
        if ($groups.Count -gt 0) {
            $shown = [System.Collections.Generic.List[string]]::new()
            foreach ($group in $groups) {
                $body.Add("<h3>$(ConvertTo-HtmlText (ConvertTo-OneLine ([string](Get-Prop $group 'title'))))</h3>")
                foreach ($member in @(Get-Prop $group 'elements')) {
                    if ($rendered.ContainsKey($member)) { foreach ($line in $rendered[$member]) { $body.Add($line) } }
                    $shown.Add([string]$member)
                }
            }
            if (($shown -join ',') -cne ($markerIds -join ',')) {
                $null = $script:Notes.Add("help '$HelpId' [$Locale]: group order differs from marker order, so the marker numbers on the page do not run 1..n")
            }
        } else {
            foreach ($id in $markerIds) {
                if ($rendered.ContainsKey($id)) { foreach ($line in $rendered[$id]) { $body.Add($line) } }
            }
        }
        $body.Add('</div>')
    }

    $body.Add("<h2>$($ui.Related)</h2>")
    foreach ($line in (New-RelatedHtml -Source $Source -HelpId $HelpId -Locale $Locale -Target $Target -Ui $ui)) { $body.Add($line) }
    foreach ($line in (New-FooterHtml -Locale $Locale -Target $Target -Ui $ui -ProductName $ProductName -Version $Version)) { $body.Add($line) }
    return $body.ToArray()
}

function New-IndexPageBody {
    param($Source, [string]$Locale, [string]$Target, [string]$ProductName, [string]$Version)
    $ui = Get-Ui $Locale
    $block = Get-Prop (Get-Prop $Source 'locales') $Locale
    $title = ConvertTo-OneLine ([string](Get-Prop $block 'title'))
    $body = [System.Collections.Generic.List[string]]::new()
    $body.Add('<header class="hero">')
    $body.Add("<h1>$(ConvertTo-HtmlText $title)</h1>")
    $body.Add('</header>')
    foreach ($paragraph in @(ConvertTo-ParagraphsHtml ([string](Get-Prop $block 'purpose')) 'lead')) { $body.Add($paragraph) }
    $body.Add('<ul class="related">')
    foreach ($id in $script:PageIds) {
        $pageTitle = Get-PageTitle $id $Locale
        if (-not $pageTitle) { Add-Problem "help index: page '$id' has no title in $Locale"; continue }
        $body.Add("<li><a href=`"$(Get-PageHref $id $Locale $Target)`"><b>$(ConvertTo-HtmlText $pageTitle)</b></a></li>")
    }
    $body.Add('</ul>')
    foreach ($line in (New-FooterHtml -Locale $Locale -Target $Target -Ui $ui -ProductName $ProductName -Version $Version)) { $body.Add($line) }
    return $body.ToArray()
}

function New-PageHtml {
    param([string]$Locale, [string]$Target, [string]$DocTitle, [string[]]$Main)
    $dir = if ($script:RtlLocales -contains $Locale) { 'rtl' } else { 'ltr' }
    $head = @(
        '<!doctype html>',
        "<html lang=`"$Locale`" dir=`"$dir`">",
        '<head>',
        '<meta charset="utf-8">',
        '<meta name="viewport" content="width=device-width, initial-scale=1">',
        '<meta name="referrer" content="no-referrer">',
        '<meta name="color-scheme" content="light dark">',
        "<title>$(ConvertTo-HtmlText $DocTitle)</title>",
        "<link rel=`"stylesheet`" href=`"$(Get-CssHref $Target)`">",
        '</head>',
        '<body class="help">',
        '<main class="container">'
    )
    return (($head + $Main + @('</main>', '</body>', '</html>')) -join "`n") + "`n"
}

function Test-PageRules {
    # CONTEXT-HELP rule 12, checked on what is about to be written: no script, no URL outside an <a href>.
    param([string]$Path, [string]$Html)
    if ($Html -match '(?i)<script') { Add-Problem "${Path}: contains a <script>" }
    $stripped = [regex]::Replace($Html, 'href="[^"]*"', 'href=""')
    if ($stripped -match 'https?://') { Add-Problem "${Path}: a URL outside an <a href> (put links in 'related')" }
}

# ---------- registry ----------

function Sync-RegistryHelpExclusions {
    # The portal copies sit under the registry's documentation/*/*.html glob but are not site pages
    # (CONTEXT-HELP rule 10), so each needs a sitemap_exclude row. The producer writes them, because a regeneration
    # that adds a page would otherwise leave the registry gate red until another script is remembered.
    # Returns the number of rows now derived, or -1 when there is nothing to sync.
    $defaultPortal = [System.IO.Path]::GetFullPath((Join-Path $script:Cfg.Root 'documentation/help'))
    if (-not $script:Cfg.OutPortal.TrimEnd('\', '/').Equals($defaultPortal.TrimEnd('\', '/'), [StringComparison]::OrdinalIgnoreCase)) { return -1 }
    $registryPath = Join-Path $script:Cfg.Root 'docs/DOCUMENT_REGISTRY.jsonl'
    if (-not (Test-Path -LiteralPath $registryPath -PathType Leaf)) { return -1 }
    $records = Read-JsonlRecords -Path (Join-Path $script:Cfg.Root 'docs/site-redirects.jsonl')
    $helpPaths = Get-HelpPagePaths -SiteRoot $script:Cfg.Root
    $text = [System.IO.File]::ReadAllText($registryPath)
    $synced = Get-RegistryWithDerivedExclusions -RegistryText $text -Records $records -HelpPagePaths $helpPaths
    if ($synced -cne $text) { [System.IO.File]::WriteAllText($registryPath, $synced, [System.Text.UTF8Encoding]::new($false)) }
    return $helpPaths.Count
}

# ---------- main ----------

function Invoke-Main {
    Initialize-Config
    $missing = @($script:Cfg.MapPath, $script:Cfg.PageManifest, $script:Cfg.CssSource,
        (Join-Path $script:Cfg.ResRoot 'values')) | Where-Object { -not (Test-Path -LiteralPath $_) }
    if ($missing) {
        foreach ($path in $missing) { Write-Problem "input not found: $path" }
        return 2
    }

    $map = Read-JsonFile $script:Cfg.MapPath
    if ($null -eq $map) { $script:Problems | ForEach-Object { Write-Problem $_ }; return 1 }
    $script:PageIds = Get-HelpMapPages $map
    Import-PageManifest

    $sources = [ordered]@{}
    foreach ($id in $script:PageIds) { $sources[$id] = Read-HelpSource -Id $id }
    $indexSource = Read-HelpSource -Id 'index' -IsIndex
    if ($script:Problems.Count -gt 0) {
        foreach ($problem in $script:Problems) { Write-Problem $problem }
        return 1
    }

    $productName = [string](Get-Prop $map 'product')
    if (-not $productName) { $productName = 'Fast Media Sorter' }
    $version = Get-AppVersionName
    $outputs = [System.Collections.Generic.List[object]]::new()
    foreach ($locale in $script:Cfg.Locales) {
        $ui = Get-Ui $locale
        foreach ($target in 'assets', 'portal') {
            foreach ($id in $script:PageIds) {
                $body = New-HelpPageBody -Source $sources[$id] -HelpId $id -Locale $locale -Target $target -ProductName $productName -Version $version
                $docTitle = "$($ui.Help) - $(Get-PageTitle $id $locale)"
                $path = Get-OutputPath $id $locale $target
                $html = New-PageHtml -Locale $locale -Target $target -DocTitle $docTitle -Main $body
                Test-PageRules -Path $path -Html $html
                $outputs.Add([pscustomobject]@{ Path = $path; Text = $html })
            }
            $body = New-IndexPageBody -Source $indexSource -Locale $locale -Target $target -ProductName $productName -Version $version
            $path = Get-OutputPath 'index' $locale $target
            $html = New-PageHtml -Locale $locale -Target $target -DocTitle (Get-PageTitle 'index' $locale) -Main $body
            Test-PageRules -Path $path -Html $html
            $outputs.Add([pscustomobject]@{ Path = $path; Text = $html })
        }
    }
    if ($script:Problems.Count -gt 0) {
        foreach ($problem in $script:Problems) { Write-Problem $problem }
        return 1
    }

    $css = (Get-Content -LiteralPath $script:Cfg.CssSource -Raw -Encoding UTF8).Replace("`r`n", "`n").TrimEnd() + "`n"
    $outputs.Add([pscustomobject]@{ Path = Join-Path $script:Cfg.OutAssets 'help.css'; Text = $css })
    $outputs.Add([pscustomobject]@{ Path = Join-Path $script:Cfg.OutPortal 'help.css'; Text = $css })

    $written = 0
    foreach ($output in $outputs) { if (Write-TextFile -Path $output.Path -Text $output.Text) { $written++ } }
    foreach ($destination in $script:ImageCopies.Keys) {
        if (Copy-FileIfChanged -Source $script:ImageCopies[$destination] -Destination $destination) { $written++ }
    }
    $excluded = Sync-RegistryHelpExclusions
    foreach ($note in $script:Notes) { Write-Host "note: $note" }
    $total = $outputs.Count + $script:ImageCopies.Count
    $registryNote = if ($excluded -ge 0) { " registry-exclusions=$excluded" } else { '' }
    Write-Host "generate-help-pages: PASS (files=$total written=$written pages=$($script:PageIds.Count) locales=$($script:Cfg.Locales -join ',') date=$Date$registryNote)"
    return 0
}

try {
    $code = Invoke-Main
} catch {
    Write-Problem $_.Exception.Message
    $code = 1
}
exit $code
