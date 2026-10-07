<#
.SYNOPSIS
    The site's product facts - public editions, their count and the Android version of a minSdk -
    read from their sources, never typed (SITE-REPRESENTATION 0.1 rules 3 and 4, S4100).

.DESCRIPTION
    Dot-sourced by scripts/docs/generate-glossary.ps1 and scripts/quality/assert-site-facts.ps1, so a
    generator prints the same number the gate compares a page against. It stores no count and no
    version of its own:

      - which variants exist and each one's minSdk: docs/flavors/flavor-matrix.json (generated from
        productFlavors);
      - which of them are public, and under which name: docs/flavors/public-editions.psd1 (a decision,
        so it is a declaration and not derived).

    The only constant here is the table of how a number is spelled in en, ru and uk, because that is
    language, not a product fact.

    Sourced, never executed directly, so it declares no exit codes of its own.
#>

# Spellings of a count, every grammatical case the corpus uses, per language. Lower case.
$script:SiteFactsNumberWords = @{
    5  = @{ en = @('five'); ru = @('пять', 'пяти', 'пятью'); uk = @('п''ять', 'п''яти', 'п''ятьма') }
    6  = @{ en = @('six'); ru = @('шесть', 'шести', 'шестью'); uk = @('шість', 'шести', 'шістьма') }
    7  = @{ en = @('seven'); ru = @('семь', 'семи', 'семью'); uk = @('сім', 'семи', 'сімома', 'сімох') }
    8  = @{ en = @('eight'); ru = @('восемь', 'восьми', 'восемью'); uk = @('вісім', 'восьми', 'вісьмома', 'вісьмох') }
    9  = @{ en = @('nine'); ru = @('девять', 'девяти', 'девятью'); uk = @('дев''ять', 'дев''яти', 'дев''ятьма') }
    10 = @{ en = @('ten'); ru = @('десять', 'десяти', 'десятью'); uk = @('десять', 'десяти', 'десятьма') }
    11 = @{ en = @('eleven'); ru = @('одиннадцать', 'одиннадцати'); uk = @('одинадцять', 'одинадцяти') }
    12 = @{ en = @('twelve'); ru = @('двенадцать', 'двенадцати'); uk = @('дванадцять', 'дванадцяти') }
}

# Android release a minSdk corresponds to, as the site writes it. Unknown levels are an error, not a guess.
$script:SiteFactsAndroidByApi = @{
    21 = '5.0'; 22 = '5.1'; 23 = '6.0'; 24 = '7.0'; 25 = '7.1'; 26 = '8.0'; 27 = '8.1'; 28 = '9.0'
    29 = '10.0'; 30 = '11.0'; 31 = '12.0'; 32 = '12.1'; 33 = '13.0'; 34 = '14.0'; 35 = '15.0'; 36 = '16.0'
}

function Get-SiteFactsNumberWords {
    <# Number -> @{ en = @(..); ru = @(..); uk = @(..) } for 5..12; the gate judges 6..12. #>
    return $script:SiteFactsNumberWords
}

function ConvertTo-SiteFactsAndroidVersion {
    <# API level -> [version] ('8.0' for 26). #>
    param([Parameter(Mandatory)][int]$MinSdk)
    if (-not $script:SiteFactsAndroidByApi.ContainsKey($MinSdk)) {
        throw "site-facts: no Android release recorded for API level $MinSdk - extend SiteFactsAndroidByApi."
    }
    return [version]$script:SiteFactsAndroidByApi[$MinSdk]
}

function Get-SiteFacts {
    <#
    .OUTPUTS
        PSCustomObject: PublicFlavors, PublicNames (flavor -> display name), NotPublic (flavor -> reason),
        Count, CountWords (en/ru/uk spellings of Count, empty when the table has none), MatrixFlavors,
        Mainline, MainlineVersion ([version]), MinimumVersions ([version[]] of every public edition),
        Companions, CompanionNames (companion -> display name), Surfaces, Landing, LabelScript (as declared), MatrixPath, DeclarationPath.
    #>
    param([Parameter(Mandatory)][string]$RepoRoot)

    $matrixPath = Join-Path $RepoRoot 'docs/flavors/flavor-matrix.json'
    $declPath = Join-Path $RepoRoot 'docs/flavors/public-editions.psd1'
    if (-not (Test-Path -LiteralPath $matrixPath)) { throw "site-facts: docs/flavors/flavor-matrix.json is absent - run scripts/docs/generate-flavor-matrix.ps1." }
    if (-not (Test-Path -LiteralPath $declPath)) { throw "site-facts: docs/flavors/public-editions.psd1 is absent." }

    $matrix = Get-Content -LiteralPath $matrixPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $decl = Import-PowerShellDataFile -LiteralPath $declPath

    $names = [ordered]@{}
    foreach ($e in @($decl.Editions)) { $names[[string]$e.Flavor] = [string]$e.Name }
    $notPublic = [ordered]@{}
    foreach ($e in @($decl.NotPublic)) { $notPublic[[string]$e.Flavor] = [string]$e.Reason }

    $minSdk = @{}
    foreach ($p in $matrix.minSdk.PSObject.Properties) { $minSdk[$p.Name] = [int]$p.Value }

    $minimums = @()
    foreach ($f in $names.Keys) {
        if ($minSdk.ContainsKey($f)) { $minimums += ConvertTo-SiteFactsAndroidVersion -MinSdk $minSdk[$f] }
    }
    $mainline = [string]$decl.Mainline
    $mainlineVersion = $null
    if ($mainline -and $minSdk.ContainsKey($mainline)) { $mainlineVersion = ConvertTo-SiteFactsAndroidVersion -MinSdk $minSdk[$mainline] }

    $companionNames = [ordered]@{}
    if ($decl.CompanionNames) {
        foreach ($k in @($decl.CompanionNames.Keys | Sort-Object)) { $companionNames[[string]$k] = [string]$decl.CompanionNames[$k] }
    }

    $count = @($names.Keys).Count
    $countWords = if ($script:SiteFactsNumberWords.ContainsKey($count)) { $script:SiteFactsNumberWords[$count] } else { @{ en = @(); ru = @(); uk = @() } }

    return [pscustomobject]@{
        PublicFlavors   = @($names.Keys)
        PublicNames     = $names
        NotPublic       = $notPublic
        Count           = $count
        CountWords      = $countWords
        MatrixFlavors   = @($matrix.flavors)
        MinSdk          = $minSdk
        Mainline        = $mainline
        MainlineVersion = $mainlineVersion
        MinimumVersions = @($minimums | Sort-Object -Unique)
        Companions      = @($decl.Companions)
        CompanionNames  = $companionNames
        Surfaces        = @($decl.Surfaces)
        Landing         = @($decl.Landing)
        LabelScript     = [string]$decl.LabelScript
        MatrixPath      = $matrixPath
        DeclarationPath = $declPath
    }
}
