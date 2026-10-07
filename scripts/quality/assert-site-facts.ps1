#requires -Version 7.0
<#
.SYNOPSIS
    Holds the site's product facts against their sources: the public editions are declared, the number
    of editions and the minimum Android version a page states are the ones the declaration and the
    build matrix give, and an edition is spelled one way everywhere (S4100).

.DESCRIPTION
    Contract SITE-REPRESENTATION 0.1, rules 3 and 4. The sources are docs/flavors/flavor-matrix.json
    (the variants and each minSdk, generated from productFlavors) and docs/flavors/public-editions.psd1
    (which variants are public, their one display name each). Five dimensions, each judging one subject:

      declaration  every variant of the matrix is declared exactly once, public or not (UNDECLARED);
                   every declared variant is in the matrix (STALE); display names are unique and the
                   mainline edition is public and has a minSdk
      count        a number of 6 to 12, as a digit or as a word in en, ru or uk, standing next to the
                   noun "editions" (редакций, изданий, редакцій, видань) in a site surface equals the
                   number of public editions. Smaller numbers name a subset on purpose and are not
                   judged, and "вариантов" is not a noun this gate reads: "eight variants" of a colour
                   theme is not a claim about editions
      android-min  an "Android N+" or "Android N.M+" claim names the Android release of a public
                   edition's minSdk. On a landing page every such claim is judged; elsewhere only the
                   dotted form is, because "Android 13+" is a feature requirement and "Android 8.0+" is
                   how the site writes the product minimum
      names        a key of the download-button labels and a data-download-edition value is a public
                   edition or a companion; a label spells the edition as declared; an edition named on a
                   recipe's `availability_note:` line is spelled as declared ("noLegal", never
                   "NoLegal"); a variant declared not public is not named as an edition on any surface
      availability rule 9 (S4107): every English recipe declares `availability:` terms that resolve
                   against the build (scripts/docs/lib/page-availability.ps1) and `devices:` from
                   the fixed list; no recipe keeps the retired free-text `flavor:`; a translation
                   carries neither key; and the edition and device badges of every published recipe
                   page, in its own language, are the ones the build gives. A page not yet generated
                   is not judged here - generate-docs-pages.ps1 -Check reports it

    A surface is a site-facing text only (the `Surfaces` of the declaration): developer documents state
    the matrix's own count and belong to assert-flavor-count-prose.ps1. The generated search index is
    not scanned; it is derived from the pages. HTML is read as text, tags replaced by a space.

    PER-TICKET by Rule 33, wired beside assert-site-origins.ps1: a count typed into a page is wrong the
    day an edition is added, and the author who added it knows why.

.PARAMETER Dimension
    declaration | count | android-min | names | availability | all (default).

.PARAMETER Root
    Repository root; overridable so the tests can judge a synthetic site.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-site-facts.ps1 -Dimension count

.NOTES
    Exit codes:
      0  every requested dimension passed
      1  at least one finding
      2  could not verify: the matrix or the declaration is missing or unreadable, the root is absent, or
         (availability) the source-set map of app_v2/build.gradle.kts cannot be read
#>
[CmdletBinding()]
param(
    [ValidateSet('declaration', 'count', 'android-min', 'names', 'availability', 'all')]
    [string] $Dimension = 'all',
    [string] $Root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
. (Join-Path $PSScriptRoot '../docs/lib/page-availability.ps1')

Write-CheckSubject -Axes ([ordered]@{ module = 'site'; scope = $Dimension; files = 'site-surfaces' })

function Exit-CouldNotVerify([string]$Why) {
    Write-Host "assert-site-facts: COULD NOT VERIFY - $Why" -ForegroundColor Yellow
    exit 2
}

if (-not (Test-Path -LiteralPath $Root -PathType Container)) { Exit-CouldNotVerify "root $Root does not exist" }
$Root = (Resolve-Path -LiteralPath $Root).Path

try { $facts = Get-SiteFacts -RepoRoot $Root }
catch { Exit-CouldNotVerify $_.Exception.Message }

$findings = [System.Collections.Generic.List[string]]::new()
$wants = @{
    declaration = ($Dimension -in 'declaration', 'all')
    count       = ($Dimension -in 'count', 'all')
    androidMin  = ($Dimension -in 'android-min', 'all')
    names       = ($Dimension -in 'names', 'all')
    availability = ($Dimension -in 'availability', 'all')
}

function Get-RelativePath([string]$FullPath) {
    return ($FullPath.Substring($Root.Length).TrimStart('\', '/') -replace '\\', '/')
}

function Get-SurfaceFiles([string[]]$Patterns) {
    <# `**` crosses directories, `*` does not; the part before the first wildcard segment is the walk's start. #>
    $found = [System.Collections.Generic.SortedSet[string]]::new([StringComparer]::Ordinal)
    foreach ($pattern in $Patterns) {
        $segs = $pattern -split '/'
        $baseSegs = @()
        foreach ($seg in $segs[0..($segs.Count - 2)]) { if ($seg -match '[*?]') { break } else { $baseSegs += $seg } }
        if ($segs.Count -eq 1) { $baseSegs = @() }
        $baseDir = if ($baseSegs.Count) { Join-Path $Root ($baseSegs -join '/') } else { $Root }
        if (-not (Test-Path -LiteralPath $baseDir -PathType Container)) { continue }
        $leaf = $segs[-1]
        $recurse = $pattern -match '\*\*'
        $params = @{ LiteralPath = $baseDir; File = $true; Filter = $leaf; ErrorAction = 'SilentlyContinue' }
        if ($recurse) { $params.Recurse = $true }
        foreach ($f in Get-ChildItem @params) { [void]$found.Add($f.FullName) }
    }
    return @($found)
}

$script:TextCache = @{}

function Get-FileTexts([string]$FullPath) {
    <# Read once per run: the three dimensions that scan files share one read and one tag strip. An HTML
       file yields two texts - as written, so a claim in a meta tag or in JSON-LD is seen, and with its
       single-line tags replaced by a space, so `<strong>7</strong> editions` reads as a claim. #>
    if (-not $script:TextCache.ContainsKey($FullPath)) {
        $raw = [IO.File]::ReadAllText($FullPath)
        $texts = @($raw)
        if ($FullPath -like '*.html') { $texts += [regex]::Replace($raw, '<[^>\r\n]+>', ' ') }
        $script:TextCache[$FullPath] = $texts
    }
    return , $script:TextCache[$FullPath]
}

function Find-Claims([string]$FullPath, [regex]$Regex) {
    <# Every match of $Regex in a file, with its line; a claim both texts show is reported once. #>
    $seen = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($text in (Get-FileTexts $FullPath)) {
        foreach ($m in $Regex.Matches($text)) {
            $line = $text.Substring(0, $m.Index).Split("`n").Length
            if ($seen.Add("$line|$($m.Value -replace '\s+', ' ')")) { [pscustomobject]@{ Line = $line; Match = $m } }
        }
    }
}

# ---------------------------------------------------------------- declaration
if ($wants.declaration) {
    $matrixFlavors = @($facts.MatrixFlavors)
    $publicKeys = @($facts.PublicFlavors)
    $notPublicKeys = @($facts.NotPublic.Keys)
    $declaredAll = @($publicKeys) + @($notPublicKeys)

    foreach ($v in $matrixFlavors) {
        if ($v -notin $declaredAll) {
            $findings.Add("declaration: UNDECLARED variant '$v' is in the matrix but in neither Editions nor NotPublic of docs/flavors/public-editions.psd1 - decide whether visitors can obtain it")
        }
    }
    foreach ($v in $declaredAll) {
        if ($v -notin $matrixFlavors) {
            $findings.Add("declaration: STALE variant '$v' is declared but absent from docs/flavors/flavor-matrix.json")
        }
    }
    foreach ($group in ($declaredAll | Group-Object | Where-Object { $_.Count -gt 1 })) {
        $findings.Add("declaration: variant '$($group.Name)' is declared $($group.Count) times (Editions and NotPublic together)")
    }
    foreach ($group in (@($facts.PublicNames.Values) | Group-Object { $_.ToLowerInvariant() } | Where-Object { $_.Count -gt 1 })) {
        $findings.Add("declaration: display name '$($group.Name)' is used by more than one edition")
    }
    foreach ($key in $publicKeys) {
        if ([string]::IsNullOrWhiteSpace($facts.PublicNames[$key])) { $findings.Add("declaration: edition '$key' has no display name") }
    }
    if ($facts.Mainline -notin $publicKeys) {
        $findings.Add("declaration: Mainline '$($facts.Mainline)' is not a public edition")
    }
    elseif ($null -eq $facts.MainlineVersion) {
        $findings.Add("declaration: Mainline '$($facts.Mainline)' has no minSdk in the matrix")
    }
}

# ---------------------------------------------------------------- count
$surfaceFiles = @()
if ($wants.count -or $wants.androidMin -or $wants.names) { $surfaceFiles = Get-SurfaceFiles @($facts.Surfaces) }

if ($wants.count) {
    $words = Get-SiteFactsNumberWords
    $forms = [System.Collections.Generic.List[string]]::new()
    $valueOf = @{}
    foreach ($n in 6..12) {
        if (-not $words.ContainsKey($n)) { continue }
        $forms.Add([string]$n); $valueOf[[string]$n] = $n
        foreach ($lang in 'en', 'ru', 'uk') {
            foreach ($w in $words[$n][$lang]) { $forms.Add([regex]::Escape($w)); $valueOf[$w] = $n }
        }
    }
    $numberPattern = ($forms | Sort-Object { $_.Length } -Descending) -join '|'
    $nounPattern = 'editions?|редакци\w*|редакці\w*|издани\w*|видан\w*'
    $countRegex = [regex]::new("(?<![\w.])(?<n>$numberPattern)\s+(?:(?:android|андроид|андроїд)\s+)?(?<noun>$nounPattern)(?![\w])",
        ([System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Compiled))
    $expected = $facts.Count

    foreach ($file in $surfaceFiles) {
        $rel = Get-RelativePath $file
        foreach ($claim in Find-Claims $file $countRegex) {
            $key = $claim.Match.Groups['n'].Value.ToLowerInvariant()
            if (-not $valueOf.ContainsKey($key)) { continue }
            $value = $valueOf[$key]
            if ($value -ne $expected) {
                $findings.Add("count: ${rel}:$($claim.Line): '$($claim.Match.Value -replace '\s+', ' ')' states $value editions, the declaration holds $expected public ones")
            }
        }
    }
}

# ---------------------------------------------------------------- android-min
if ($wants.androidMin) {
    $allowed = @($facts.MinimumVersions | ForEach-Object { $_.ToString() })
    $landingFiles = @(Get-SurfaceFiles @($facts.Landing))
    $landingSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$landingFiles, [StringComparer]::Ordinal)
    $androidRegex = [regex]::new('Android\s+(?<v>\d+(?:\.\d+)?)\s*\+')

    foreach ($file in $surfaceFiles) {
        $isLanding = $landingSet.Contains($file)
        $rel = Get-RelativePath $file
        foreach ($claim in Find-Claims $file $androidRegex) {
            $raw = $claim.Match.Groups['v'].Value
            if (-not $isLanding -and $raw -notmatch '\.') { continue }
            $version = [version]($(if ($raw -match '\.') { $raw } else { "$raw.0" }))
            if ($version.ToString() -notin $allowed) {
                $findings.Add("android-min: ${rel}:$($claim.Line): '$($claim.Match.Value -replace '\s+', ' ')' is no minimum of a public edition (the matrix gives $($allowed -join ', '))")
            }
        }
    }
}

# ---------------------------------------------------------------- names
if ($wants.names) {
    $publicKeys = @($facts.PublicFlavors)
    $allowedKeys = @($publicKeys) + @($facts.Companions)

    $labelScript = if ($facts.LabelScript) { Join-Path $Root $facts.LabelScript } else { $null }
    if ($labelScript -and (Test-Path -LiteralPath $labelScript -PathType Leaf)) {
        $text = [IO.File]::ReadAllText($labelScript)
        $block = [regex]::Match($text, '\blabels\s*=\s*\{(?<body>[^}]*)\}')
        if ($block.Success) {
            foreach ($entry in [regex]::Matches($block.Groups['body'].Value, "(?<k>\w+)\s*:\s*'(?<v>[^']*)'")) {
                $k = $entry.Groups['k'].Value
                $v = $entry.Groups['v'].Value
                if ($k -notin $allowedKeys) {
                    $findings.Add("names: $($facts.LabelScript): label key '$k' is neither a public edition nor a companion")
                }
                elseif ($k -in $publicKeys -and $v -cne $facts.PublicNames[$k]) {
                    $findings.Add("names: $($facts.LabelScript): label '$v' for '$k' - the declared name is '$($facts.PublicNames[$k])'")
                }
            }
        }
    }

    $landingFiles = @(Get-SurfaceFiles @($facts.Landing))
    foreach ($file in $landingFiles) {
        $rel = Get-RelativePath $file
        $lines = [IO.File]::ReadAllLines($file)
        for ($i = 0; $i -lt $lines.Count; $i++) {
            foreach ($m in [regex]::Matches($lines[$i], 'data-download-edition="(?<e>[^"]*)"')) {
                $e = $m.Groups['e'].Value
                if ($e -notin $allowedKeys) {
                    $findings.Add("names: ${rel}:$($i + 1): data-download-edition '$e' is neither a public edition nor a companion")
                }
            }
        }
    }

    $nameRegex = [regex]::new('\b(' + (($publicKeys | ForEach-Object { [regex]::Escape($_) }) -join '|') + ')\b',
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    $notPublicKeys = @($facts.NotPublic.Keys)
    $notPublicRegex = if ($notPublicKeys.Count) {
        [regex]::new('\b(' + (($notPublicKeys | ForEach-Object { [regex]::Escape($_) }) -join '|') + ')\s+(?:edition|flavou?r|variant|build)s?\b',
            [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    }
    else { $null }

    $flavorLineRegex = [regex]::new('(?m)^availability_note:[ \t]*(?<body>[^\r\n]*)')
    foreach ($file in $surfaceFiles) {
        $rel = Get-RelativePath $file
        if ($file -like '*.md') {
            foreach ($claim in Find-Claims $file $flavorLineRegex) {
                $body = $claim.Match.Groups['body'].Value
                foreach ($m in $nameRegex.Matches($body)) {
                    $declaredName = $facts.PublicNames[($publicKeys | Where-Object { $_ -ieq $m.Value } | Select-Object -First 1)]
                    if ($m.Value -cne $declaredName) {
                        $findings.Add("names: ${rel}:$($claim.Line): edition written '$($m.Value)', the declared name is '$declaredName'")
                    }
                }
                foreach ($k in $notPublicKeys) {
                    if ($body -match "\b$([regex]::Escape($k))\b") {
                        $findings.Add("names: ${rel}:$($claim.Line): '$k' is declared not public but is named in an availability note")
                    }
                }
            }
        }
        if ($notPublicRegex) {
            foreach ($claim in Find-Claims $file $notPublicRegex) {
                $findings.Add("names: ${rel}:$($claim.Line): '$($claim.Match.Value -replace '\s+', ' ')' names a variant declared not public")
            }
        }
    }
}

# ---------------------------------------------------------------- availability
if ($wants.availability) {
    try { $availabilityContext = Get-PageAvailabilityContext -RepoRoot $Root }
    catch { Exit-CouldNotVerify $_.Exception.Message }
    $deviceVocabulary = @(Get-PageAvailabilityDeviceVocabulary)
    $byPage = Get-RecipeAvailabilityMap -RepoRoot $Root
    $recipeDirs = [ordered]@{ en = 'docs/content/recipes'; ru = 'docs/content/recipes-ru'; uk = 'docs/content/recipes-uk' }
    $metaRowRegex = [regex]::new('<div class="doc-meta-row">(?<row>.*?)</div>', [System.Text.RegularExpressions.RegexOptions]::Singleline)
    $editionBadgeRegex = [regex]::new('<span class="doc-edition-badge">(?<t>[^<]*)</span>')
    $deviceBadgeRegex = [regex]::new('<span class="doc-device-badge[^"]*">(?:<svg\b.*?</svg>)?(?<t>[^<]*)</span>', [System.Text.RegularExpressions.RegexOptions]::Singleline)

    foreach ($lang in $recipeDirs.Keys) {
        $dir = Join-Path $Root $recipeDirs[$lang]
        if (-not (Test-Path -LiteralPath $dir -PathType Container)) { continue }
        foreach ($file in Get-ChildItem -LiteralPath $dir -Filter *.md -File | Sort-Object Name) {
            $rel = Get-RelativePath $file.FullName
            $fm = Get-RecipeFrontMatterText -Path $file.FullName
            $pageId = Get-RecipeFrontMatterScalar -FrontMatter $fm -Key 'page_id'
            if ($null -ne (Get-RecipeFrontMatterScalar -FrontMatter $fm -Key 'flavor')) {
                $findings.Add("availability: ${rel}: 'flavor:' is retired - declare 'availability:' in the English recipe and keep any detail as 'availability_note:'")
            }
            if (-not $pageId) { continue }
            if ($lang -ne 'en') {
                foreach ($key in 'availability', 'devices') {
                    if ($null -ne (Get-RecipeFrontMatterScalar -FrontMatter $fm -Key $key)) {
                        $findings.Add("availability: ${rel}: '${key}:' belongs to the English recipe only; a translation takes it by page_id")
                    }
                }
            }
            $entry = $byPage[$pageId]
            if (-not $entry -or [string]::IsNullOrWhiteSpace($entry.Availability)) {
                if ($lang -eq 'en') { $findings.Add("availability: ${rel}: no 'availability:' - name the flag, source set or companion the function depends on, or 'all'") }
                else { $findings.Add("availability: ${rel}: page_id '$pageId' has no English recipe declaring 'availability:'") }
                continue
            }
            $resolved = Resolve-PageAvailability -Context $availabilityContext -Terms $entry.Availability
            if ($resolved.Errors.Count -gt 0) {
                if ($lang -eq 'en') { foreach ($e in $resolved.Errors) { $findings.Add("availability: ${rel}: $e") } }
                continue
            }
            $devices = @(Split-PageAvailabilityDevices $entry.Devices)
            $badDevices = @($devices | Where-Object { $_ -notin $deviceVocabulary })
            if ($badDevices.Count -gt 0) {
                if ($lang -eq 'en') { $findings.Add("availability: ${rel}: devices '$($badDevices -join ', ')' - the vocabulary is $($deviceVocabulary -join ', ')") }
                continue
            }

            $canonical = Get-RecipeFrontMatterScalar -FrontMatter $fm -Key 'canonical_url'
            if (-not $canonical) { continue }
            $page = Join-Path $Root $canonical
            if (-not (Test-Path -LiteralPath $page -PathType Leaf)) { continue }
            $row = $metaRowRegex.Match([IO.File]::ReadAllText($page))
            $expected = Format-PageAvailabilityMarker -Context $availabilityContext -Resolved $resolved -Lang $lang
            $badges = @(if ($row.Success) { $editionBadgeRegex.Matches($row.Groups['row'].Value) | ForEach-Object { $_.Groups['t'].Value } })
            if ($badges.Count -ne 1 -or $badges[0] -cne $expected) {
                $actual = if ($badges.Count) { "'$($badges -join ''', ''')'" } else { 'no badge' }
                $findings.Add("availability: ${canonical}: the edition badge says $actual, the build gives '$expected' (regenerate with scripts/docs/generate-docs-pages.ps1)")
            }
            $expectedDevices = @($devices | ForEach-Object { (Get-PageAvailabilityDevice -Device $_ -Lang $lang).Label })
            $actualDevices = @(if ($row.Success) { $deviceBadgeRegex.Matches($row.Groups['row'].Value) | ForEach-Object { $_.Groups['t'].Value } })
            if (($expectedDevices -join '|') -cne ($actualDevices -join '|')) {
                $findings.Add("availability: ${canonical}: device badges '$($actualDevices -join ', ')', the recipe declares '$($expectedDevices -join ', ')'")
            }
        }
    }
}

if ($findings.Count -gt 0) {
    foreach ($f in $findings) { Write-Host "FAIL $f" -ForegroundColor Red }
    Write-Host "assert-site-facts: FAIL ($($findings.Count) finding(s))" -ForegroundColor Red
    exit 1
}

Write-Host "assert-site-facts: PASS ($($facts.Count) public editions, $(@($surfaceFiles).Count) surface files, dimension $Dimension)"
exit 0
