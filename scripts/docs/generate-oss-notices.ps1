<#
.SYNOPSIS
    S1495 - OSS notice generator (single source of truth renderer).

.DESCRIPTION
    Derives the third-party notice list from the build files and emits four artifacts:

      docs/legal/oss-notices.json  machine-readable snapshot (gate input)
      docs/OPEN_SOURCE.md          published page, English
      docs/OPEN_SOURCE.ru.md       published page, Russian
      docs/OPEN_SOURCE.uk.md       published page, Ukrainian

    The build files stay the only source of truth for WHICH artifacts ship
    (S1495 ADR-1); scripts/docs/oss-licenses.psd1 is the only source for WHICH
    LICENCE each one carries (ADR-2), because a Gradle declaration does not
    carry a licence and resolving the POM would need the gradle daemon that
    ADR-4 rules out.

    A shipping coordinate with no manifest entry aborts with exit 2 naming the
    coordinate (ADR-3). That refusal is the mechanism: it is what stops a newly
    added dependency from going unlisted, which is how the page came to name 2
    libraries out of 97.

    Library names, SPDX identifiers, coordinates and URLs are never translated;
    only the framing prose differs per locale (ADR-5), so all three pages render
    from one snapshot and cannot disagree about a licence.

.PARAMETER Check
    Render into memory and compare against the files on disk. Exit 1 on drift,
    print the differing artifact. Does not write.

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/generate-oss-notices.ps1
    pwsh -NoProfile -File scripts/docs/generate-oss-notices.ps1 -Check

.NOTES
    Exit codes:
      0  artifacts written, or -Check found them current
      1  -Check found drift (regenerate without -Check)
      2  could not verify: parser or manifest missing, a shipping coordinate
         absent from the manifest, or a manifest entry missing a required field
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string] $Manifest,
    [switch] $Check,
    [switch] $Quiet
)

$ErrorActionPreference = 'Stop'

$parserPath = Join-Path $PSScriptRoot 'OssDependencyParser.ps1'
if (-not (Test-Path -LiteralPath $parserPath)) {
    Write-Error "parser not found: $parserPath" -ErrorAction Continue
    exit 2
}
. $parserPath

if (-not $Manifest) { $Manifest = Join-Path $PSScriptRoot 'oss-licenses.psd1' }
if (-not (Test-Path -LiteralPath $Manifest)) {
    Write-Error "licence manifest not found: $Manifest" -ErrorAction Continue
    exit 2
}

$jsonPath = Join-Path $RepoRoot 'docs/legal/oss-notices.json'
$pages = @{
    en = Join-Path $RepoRoot 'docs/OPEN_SOURCE.md'
    ru = Join-Path $RepoRoot 'docs/OPEN_SOURCE.ru.md'
    uk = Join-Path $RepoRoot 'docs/OPEN_SOURCE.uk.md'
}
$appRawPaths = @{}
foreach ($flavor in @('standard', 'noLegal', 'lite', 'photos', 'legacy', 'vr')) {
    $rawName = "oss_notices_$($flavor.ToLowerInvariant())"
    $appRawPaths[$flavor] = Join-Path $RepoRoot "app_v2/src/main/res/raw/$rawName.json"
}

# Framing prose per locale. Everything a reader needs in their own language; nothing that
# names a library, a licence or a URL, because translating those would make the three pages
# disagree about a legal fact.
$LocaleText = @{
    en = @{
        Permalink      = '/docs/OPEN_SOURCE.html'
        Title          = 'Open Source Notices'
        Intro          = 'FastMediaSorter is built on the open source components listed below. We are grateful to their authors. The list is generated from the build files, so it covers everything shipped in a build rather than a selection.'
        ShippedInAll   = 'all builds'
        OssHeading     = 'Open source components'
        VendorHeading  = 'Components under vendor terms'
        VendorIntro    = 'These components are not open source. They are distributed under their vendor terms and are listed here because they ship inside the app.'
        LicenceHeading = 'Licence notes'
        ColLibrary     = 'Library'
        ColCoordinate  = 'Coordinate'
        ColLicense     = 'License'
        ColShippedIn   = 'Shipped in'
        ColSource      = 'Source'
        ViaLabel       = 'via'
        BundledLabel   = 'bundled binary'
        BackLink       = 'Back to Terms'
        HomeLink       = 'Back to Home'
    }
    ru = @{
        Permalink      = '/docs/OPEN_SOURCE.ru.html'
        Title          = 'Уведомления об открытом ПО'
        Intro          = 'FastMediaSorter собран на перечисленных ниже компонентах с открытым исходным кодом. Мы благодарны их авторам. Перечень формируется из файлов сборки, поэтому он покрывает всё, что входит в сборку, а не выборку.'
        ShippedInAll   = 'все сборки'
        OssHeading     = 'Компоненты с открытым исходным кодом'
        VendorHeading  = 'Компоненты на условиях поставщика'
        VendorIntro    = 'Эти компоненты не являются открытым ПО. Они распространяются на условиях своих поставщиков и перечислены здесь потому, что входят в приложение.'
        LicenceHeading = 'Примечания к лицензиям'
        ColLibrary     = 'Библиотека'
        ColCoordinate  = 'Координата'
        ColLicense     = 'Лицензия'
        ColShippedIn   = 'В каких сборках'
        ColSource      = 'Исходники'
        ViaLabel       = 'через'
        BundledLabel   = 'встроенный бинарник'
        BackLink       = 'К условиям использования'
        HomeLink       = 'На главную'
    }
    uk = @{
        Permalink      = '/docs/OPEN_SOURCE.uk.html'
        Title          = 'Повідомлення про відкрите ПЗ'
        Intro          = 'FastMediaSorter побудований на перелічених нижче компонентах з відкритим кодом. Ми вдячні їхнім авторам. Перелік формується з файлів збірки, тому він охоплює все, що входить у збірку, а не вибірку.'
        ShippedInAll   = 'усі збірки'
        OssHeading     = 'Компоненти з відкритим кодом'
        VendorHeading  = 'Компоненти на умовах постачальника'
        VendorIntro    = 'Ці компоненти не є відкритим ПЗ. Вони розповсюджуються на умовах своїх постачальників і перелічені тут тому, що входять до застосунку.'
        LicenceHeading = 'Примітки до ліцензій'
        ColLibrary     = 'Бібліотека'
        ColCoordinate  = 'Координата'
        ColLicense     = 'Ліцензія'
        ColShippedIn   = 'У яких збірках'
        ColSource      = 'Джерела'
        ViaLabel       = 'через'
        BundledLabel   = 'вбудований бінарник'
        BackLink       = 'До умов використання'
        HomeLink       = 'На головну'
    }
}

function New-NoticeSnapshot {
    [CmdletBinding()]
    param([string] $Root, [string] $ManifestPath)

    $licences = Import-PowerShellDataFile -LiteralPath $ManifestPath
    $flavors = Get-OssFlavorNames -Root $Root

    $declared = @()
    $declared += Get-OssDependencies -GradleFile (Join-Path $Root 'app_v2/build.gradle.kts') -Module 'app_v2' -Flavors $flavors
    $declared += Get-OssDependencies -GradleFile (Join-Path $Root 'wear/build.gradle.kts') -Module 'wear' -Flavors $flavors

    $shipping = $declared | Where-Object { $_.Shipping }
    $entries = [System.Collections.Generic.List[object]]::new()
    $modulesByCoordinate = @{}
    $missing = [System.Collections.Generic.List[string]]::new()

    foreach ($group in ($shipping | Group-Object { "$($_.Group):$($_.Artifact)" } | Sort-Object Name)) {
        $coordinate = $group.Name
        if (-not $licences.ContainsKey($coordinate)) {
            $missing.Add($coordinate)
            continue
        }

        # A plain implementation reaches every flavor; a flavor-scoped configuration reaches
        # only its own. Collapsing the two would tell a standard user to read the terms of a
        # component their build never contained.
        $shippedIn = @()
        foreach ($declaration in $group.Group) {
            if ($declaration.Configuration -in @('implementation', 'api', 'coreLibraryDesugaring', 'releaseImplementation')) {
                $shippedIn = $flavors
                break
            }
            foreach ($flavor in $flavors) {
                if ($declaration.Configuration -eq "${flavor}Implementation" -or $declaration.Configuration -eq "${flavor}Api") {
                    $shippedIn += $flavor
                }
            }
        }
        $shippedIn = @($shippedIn | Sort-Object -Unique)

        $modules = @($group.Group | ForEach-Object Module | Sort-Object -Unique)
        $modulesByCoordinate[$coordinate] = $modules
        $entries.Add((New-NoticeEntry -Coordinate $coordinate -Licence $licences[$coordinate] -ShippedIn $shippedIn -Flavors $flavors -Modules $modules))
    }

    if ($missing.Count -gt 0) {
        foreach ($coordinate in $missing) {
            Write-Error "shipping coordinate absent from the licence manifest: $coordinate - add it to $ManifestPath" -ErrorAction Continue
        }
        exit 2
    }

    # Entries no declaration names: transitive artifacts and bundled binaries.
    foreach ($coordinate in ($licences.Keys | Sort-Object)) {
        $entry = $licences[$coordinate]
        if (-not ($entry.Transitive -or $entry.Bundled)) { continue }
        $shippedIn = if ($entry.ShippedInOverride) { @($entry.ShippedInOverride) } else { $flavors }
        $modules = if ($entry.Bundled) {
            @('app_v2')
        }
        elseif ($entry.Transitive -and $entry.Via -and $modulesByCoordinate.ContainsKey($entry.Via)) {
            $modulesByCoordinate[$entry.Via]
        }
        else {
            @('app_v2', 'wear')
        }
        $entries.Add((New-NoticeEntry -Coordinate $coordinate -Licence $entry -ShippedIn $shippedIn -Flavors $flavors -Modules $modules))
    }

    return [pscustomobject]@{
        generatedBy = 'scripts/docs/generate-oss-notices.ps1'
        spec        = 'S1495'
        sources     = @('app_v2/build.gradle.kts', 'wear/build.gradle.kts', 'scripts/docs/oss-licenses.psd1')
        doNotEdit   = 'Generated artifact. Change the build files or the licence manifest, then regenerate.'
        flavors     = $flavors
        entries     = @($entries | Sort-Object -Property @{ Expression = { -not $_.oss } }, Coordinate)
    }
}

function New-NoticeEntry {
    [CmdletBinding()]
    param(
        [string] $Coordinate,
        [hashtable] $Licence,
        [string[]] $ShippedIn,
        [string[]] $Flavors,
        [string[]] $Modules
    )

    foreach ($field in @('Name', 'Spdx', 'LicenseUrl', 'SourceUrl')) {
        if (-not $Licence[$field]) {
            Write-Error "manifest entry $Coordinate is missing the required field $field" -ErrorAction Continue
            exit 2
        }
    }

    return [pscustomobject]@{
        coordinate = $Coordinate
        modules    = @($Modules)
        name       = $Licence.Name
        spdx       = $Licence.Spdx
        licenseUrl = $Licence.LicenseUrl
        sourceUrl  = $Licence.SourceUrl
        oss        = ($Licence.Oss -ne $false)
        shippedIn  = @($ShippedIn)
        allFlavors = (@(Compare-Object $Flavors $ShippedIn).Count -eq 0)
        transitive = [bool] $Licence.Transitive
        via        = $Licence.Via
        bundled    = [bool] $Licence.Bundled
        note       = $Licence.Note
    }
}

function Format-ShippedIn {
    [CmdletBinding()]
    param([object] $Entry, [hashtable] $Text)

    if ($Entry.allFlavors) { return $Text.ShippedInAll }
    return ($Entry.shippedIn -join ', ')
}

function Format-NoticeRow {
    [CmdletBinding()]
    param([object] $Entry, [hashtable] $Text)

    $name = $Entry.name
    if ($Entry.transitive -and $Entry.via) { $name = "$name ($($Text.ViaLabel) ``$($Entry.via)``)" }
    if ($Entry.bundled) { $name = "$name ($($Text.BundledLabel))" }

    return '| {0} | `{1}` | [{2}]({3}) | {4} | [link]({5}) |' -f `
        $name, $Entry.coordinate, $Entry.spdx, $Entry.licenseUrl, (Format-ShippedIn -Entry $Entry -Text $Text), $Entry.sourceUrl
}

function Format-NoticePage {
    [CmdletBinding()]
    param([object] $Snapshot, [string] $Locale)

    $text = $LocaleText[$Locale]
    $lines = [System.Collections.Generic.List[string]]::new()

    $lines.Add('---')
    $lines.Add('layout: default')
    $lines.Add('title: "{0}"' -f $text.Title)
    $lines.Add('permalink: {0}' -f $text.Permalink)
    $lines.Add('---')
    $lines.Add('')
    $lines.Add('<!-- Generated by scripts/docs/generate-oss-notices.ps1 (S1495). Do not edit by hand - change')
    $lines.Add('     app_v2/build.gradle.kts, wear/build.gradle.kts or scripts/docs/oss-licenses.psd1, then regenerate. -->')
    $lines.Add('')
    $lines.Add('# {0}' -f $text.Title)
    $lines.Add('')
    $lines.Add($text.Intro)
    $lines.Add('')

    $header = '| {0} | {1} | {2} | {3} | {4} |' -f $text.ColLibrary, $text.ColCoordinate, $text.ColLicense, $text.ColShippedIn, $text.ColSource
    $rule = '|---|---|---|---|---|'

    $oss = @($Snapshot.entries | Where-Object { $_.oss })
    $lines.Add('## {0}' -f $text.OssHeading)
    $lines.Add('')
    $lines.Add($header)
    $lines.Add($rule)
    foreach ($entry in $oss) { $lines.Add((Format-NoticeRow -Entry $entry -Text $text)) }
    $lines.Add('')

    $vendor = @($Snapshot.entries | Where-Object { -not $_.oss })
    if ($vendor.Count -gt 0) {
        $lines.Add('## {0}' -f $text.VendorHeading)
        $lines.Add('')
        $lines.Add($text.VendorIntro)
        $lines.Add('')
        $lines.Add($header)
        $lines.Add($rule)
        foreach ($entry in $vendor) { $lines.Add((Format-NoticeRow -Entry $entry -Text $text)) }
        $lines.Add('')
    }

    $noted = @($Snapshot.entries | Where-Object { $_.note })
    if ($noted.Count -gt 0) {
        $lines.Add('## {0}' -f $text.LicenceHeading)
        $lines.Add('')
        foreach ($entry in $noted) {
            # Outer parentheses are load-bearing: inside a method call the commas of a -f
            # argument list are read as further method arguments without them.
            $lines.Add(('- **{0}** (`{1}`) - {2}' -f $entry.name, $entry.spdx, $entry.note))
        }
        $lines.Add('')
    }

    $lines.Add('---')
    $lines.Add(('[{0}](TERMS_OF_SERVICE.md) | [{1}](../index.html)' -f $text.BackLink, $text.HomeLink))
    $lines.Add('')

    return ($lines -join "`n")
}

function New-AppNoticePayload {
    [CmdletBinding()]
    param([object] $Snapshot, [string] $Flavor)

    return @(
        $Snapshot.entries |
            Where-Object { $_.modules -contains 'app_v2' -and $_.shippedIn -contains $Flavor } |
            Sort-Object -Property Coordinate
    )
}

function Write-IfChanged {
    [CmdletBinding()]
    param([string] $Path, [string] $Content, [switch] $CheckOnly)

    $current = if (Test-Path -LiteralPath $Path) { Get-Content -LiteralPath $Path -Raw } else { $null }
    $normalizedCurrent = if ($null -ne $current) { $current -replace "`r`n", "`n" } else { $null }
    $normalizedNew = $Content -replace "`r`n", "`n"

    if ($normalizedCurrent -eq $normalizedNew) { return $false }

    if ($CheckOnly) { return $true }

    $dir = Split-Path -Parent $Path
    if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    Set-Content -LiteralPath $Path -Value $normalizedNew -Encoding utf8 -NoNewline
    return $true
}

$snapshot = New-NoticeSnapshot -Root $RepoRoot -ManifestPath $Manifest
$rendered = @{ $jsonPath = (($snapshot | ConvertTo-Json -Depth 6) + "`n") }
foreach ($code in @('en', 'ru', 'uk')) { $rendered[$pages[$code]] = (Format-NoticePage -Snapshot $snapshot -Locale $code) }
foreach ($flavor in $appRawPaths.Keys) {
    $rendered[$appRawPaths[$flavor]] = ((New-AppNoticePayload -Snapshot $snapshot -Flavor $flavor | ConvertTo-Json -Depth 6) + "`n")
}

$drifted = [System.Collections.Generic.List[string]]::new()
foreach ($path in ($rendered.Keys | Sort-Object)) {
    if (Write-IfChanged -Path $path -Content $rendered[$path] -CheckOnly:$Check) {
        $drifted.Add((Resolve-Path -LiteralPath $path -ErrorAction SilentlyContinue)?.Path ?? $path)
    }
}

if ($Check) {
    if ($drifted.Count -gt 0) {
        foreach ($path in $drifted) { Write-Error "out of date: $path" -ErrorAction Continue }
        Write-Error 'oss-notices: drift found - run scripts/docs/generate-oss-notices.ps1' -ErrorAction Continue
        exit 1
    }
    if (-not $Quiet) { Write-Host "oss-notices: current ($($snapshot.entries.Count) entries)" }
    exit 0
}

if (-not $Quiet) {
    Write-Host "oss-notices: $($snapshot.entries.Count) entries, $($drifted.Count) artifact(s) written"
}
exit 0
