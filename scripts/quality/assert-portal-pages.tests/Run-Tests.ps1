<#
.SYNOPSIS
    S4256 - test suite for scripts/quality/assert-portal-pages.ps1.

.DESCRIPTION
    # Subject: scripts/docs/portal-sections.json

    Each case builds a small portal in a temporary directory (two sections in three languages, one picture in
    three languages, a manifest, and a stand-in renderer whose exit code the case chooses), breaks it the way a
    writer could, and runs the gate. The negative cases assert that the gate refuses: a gate whose refusal was
    never seen may be green forever.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-portal-pages.tests/Run-Tests.ps1

.NOTES
    Exit codes:
      0  every assertion passed
      1  at least one assertion failed
      2  could not verify: the gate script is missing
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
)

$ErrorActionPreference = 'Stop'
$gate = Join-Path $RepoRoot 'scripts/quality/assert-portal-pages.ps1'
if (-not (Test-Path -LiteralPath $gate)) { Write-Error "missing: $gate" -ErrorAction Continue; exit 2 }

$script:failures = 0
function Assert-That([bool]$Condition, [string]$Name) {
    if ($Condition) { Write-Host "  ok   $Name" -ForegroundColor Green }
    else { Write-Host "  FAIL $Name" -ForegroundColor Red; $script:failures++ }
}

function Invoke-Gate([string]$Root, [string]$Dimension = 'all') {
    $output = & pwsh -NoProfile -File $gate -RepoRoot $Root -Dimension $Dimension 2>&1 | Out-String
    return [pscustomobject]@{ Exit = $LASTEXITCODE; Output = $output }
}

function Write-Text([string]$Root, [string]$Relative, [string]$Text) {
    $path = Join-Path $Root $Relative
    $null = New-Item -ItemType Directory -Force (Split-Path -Parent $path)
    [IO.File]::WriteAllText($path, $Text, [Text.UTF8Encoding]::new($false))
}

function Write-Manifest([string]$Root, [string]$Sections) {
    Write-Text $Root 'scripts/docs/portal-sections.json' ('{"portal":"docs/portal","publisher":"fms-android","languages":["en","ru","uk"],' +
        '"programs":{"fms-android":"phone","fms-windows":"pc"},"sections":[' + $Sections + ']}')
}

$defaultSections = '{"page":"index","owner":"fms-android","common":true},{"page":"windows-servers","owner":"fms-windows","common":false}'

function Write-Renderer([string]$Root, [int]$Code) {
    Write-Text $Root 'scripts/docs/render-usage-schemes.ps1' "Write-Host 'stand-in renderer exit $Code'`nexit $Code`n"
}

function New-Portal {
    $root = Join-Path ([IO.Path]::GetTempPath()) ("portal-pages-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
    $null = New-Item -ItemType Directory -Force $root
    foreach ($lang in 'en', 'ru', 'uk') {
        $suffix = if ($lang -eq 'en') { '' } else { "-$lang" }
        Write-Text $root "docs/portal/index$suffix.md" "# Home`n`n![scheme](diagrams/three-ways-$lang.svg)`n`n![card](from-windows/quick-scheme-1.svg)`n"
        Write-Text $root "docs/portal/windows-servers$suffix.md" "# Windows`n`n<img src=`"diagrams/three-ways-$lang.svg`" alt=`"x`">`n"
        Write-Text $root "docs/portal/diagrams/three-ways-$lang.svg" "<svg/>`n"
    }
    Write-Text $root 'docs/portal/README.md' "# picture brief`n"
    Write-Text $root 'docs/portal/from-windows/quick-scheme-1.svg' "<svg/>`n"
    Write-Manifest $root $defaultSections
    Write-Renderer $root 0
    return $root
}

$sites = [System.Collections.Generic.List[string]]::new()
try {
    Write-Host 'case: a complete portal'
    $site = New-Portal; $sites.Add($site)
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 0) 'the gate passes a complete portal, the README brief and a from-windows card left alone'

    Write-Host 'case: a page outside the manifest'
    $site = New-Portal; $sites.Add($site)
    foreach ($suffix in '', '-ru', '-uk') { Write-Text $site "docs/portal/clouds$suffix.md" "# Clouds`n" }
    $r = Invoke-Gate $site 'sections'
    Assert-That ($r.Exit -eq 1 -and $r.Output -match "clouds\.md belongs to no section") 'the gate refuses a page no section lists'

    Write-Host 'case: an owner that is not a program'
    $site = New-Portal; $sites.Add($site)
    Write-Manifest $site '{"page":"index","owner":"fms-android","common":true},{"page":"windows-servers","owner":"someone","common":false}'
    $r = Invoke-Gate $site 'sections'
    Assert-That ($r.Exit -eq 1 -and $r.Output -match "page 'windows-servers' names owner 'someone'") 'the gate refuses an owner outside the programs'

    Write-Host 'case: a listed section without pages, listed twice'
    $site = New-Portal; $sites.Add($site)
    Write-Manifest $site ($defaultSections + ',{"page":"clouds","owner":"fms-android","common":false},{"page":"clouds","owner":"fms-android","common":false}')
    $r = Invoke-Gate $site 'sections'
    Assert-That ($r.Exit -eq 1 -and $r.Output -match "section 'clouds' has no page" -and $r.Output -match "page 'clouds' is listed twice") 'the gate refuses an empty and a repeated section'

    Write-Host 'case: a missing language variant'
    $site = New-Portal; $sites.Add($site)
    Remove-Item -LiteralPath (Join-Path $site 'docs/portal/windows-servers-uk.md')
    $r = Invoke-Gate $site 'variants'
    Assert-That ($r.Exit -eq 1 -and $r.Output -match "section 'windows-servers' lacks its uk variant") 'the gate refuses a section published in two languages'

    Write-Host 'case: a picture in two languages'
    $site = New-Portal; $sites.Add($site)
    Remove-Item -LiteralPath (Join-Path $site 'docs/portal/diagrams/three-ways-ru.svg')
    $r = Invoke-Gate $site 'variants'
    Assert-That ($r.Exit -eq 1 -and $r.Output -match 'diagrams/three-ways-ru\.svg is missing') 'the gate refuses a picture missing one language'

    Write-Host 'case: a page embedding a picture of another language'
    $site = New-Portal; $sites.Add($site)
    Write-Text $site 'docs/portal/index-uk.md' "# Home`n`n![scheme](diagrams/three-ways-ru.svg)`n"
    $r = Invoke-Gate $site 'variants'
    Assert-That ($r.Exit -eq 1 -and $r.Output -match 'index-uk\.md is the uk variant but embeds diagrams/three-ways-ru\.svg') 'the gate refuses a foreign-language picture'

    Write-Host 'case: a srcset source naming a picture of another language'
    $site = New-Portal; $sites.Add($site)
    Write-Text $site 'docs/portal/index-uk.md' "# Home`n`n<picture><source media=`"(max-width: 768px)`" srcset=`"diagrams/three-ways-ru.svg`"><img src=`"diagrams/three-ways-uk.svg`" alt=`"x`"></picture>`n"
    $r = Invoke-Gate $site 'variants'
    Assert-That ($r.Exit -eq 1 -and $r.Output -match 'index-uk\.md is the uk variant but embeds diagrams/three-ways-ru\.svg') 'the gate refuses a foreign-language srcset picture'

    Write-Host 'case: a srcset source naming a missing picture'
    $site = New-Portal; $sites.Add($site)
    Write-Text $site 'docs/portal/index-uk.md' "# Home`n`n<picture><source media=`"(max-width: 768px)`" srcset=`"diagrams/three-ways-phone-uk.svg`"><img src=`"diagrams/three-ways-uk.svg`" alt=`"x`"></picture>`n"
    $r = Invoke-Gate $site 'variants'
    Assert-That ($r.Exit -eq 1 -and $r.Output -match 'embeds diagrams/three-ways-phone-uk\.svg, which does not exist') 'the gate refuses a srcset picture that does not exist'

    Write-Host 'case: the renderer finds a stale picture'
    $site = New-Portal; $sites.Add($site)
    Write-Renderer $site 1
    $r = Invoke-Gate $site 'schemes'
    Assert-That ($r.Exit -eq 1 -and $r.Output -match 'schemes: a rendered picture is missing or stale') 'the gate maps the renderer exit 1 to a finding'

    Write-Host 'case: the renderer refuses its input'
    $site = New-Portal; $sites.Add($site)
    Write-Renderer $site 2
    $r = Invoke-Gate $site 'schemes'
    Assert-That ($r.Exit -eq 2 -and $r.Output -match 'COULD NOT VERIFY') 'the gate maps the renderer exit 2 to could-not-verify'

    Write-Host 'case: the manifest is missing'
    $site = New-Portal; $sites.Add($site)
    Remove-Item -LiteralPath (Join-Path $site 'scripts/docs/portal-sections.json')
    $r = Invoke-Gate $site
    Assert-That ($r.Exit -eq 2 -and $r.Output -match 'COULD NOT VERIFY') 'the gate exits 2 when the manifest is missing'
} finally {
    foreach ($s in $sites) { Remove-Item -LiteralPath $s -Recurse -Force -ErrorAction SilentlyContinue }
}

if ($script:failures -gt 0) { Write-Host "assert-portal-pages tests: FAIL ($script:failures)" -ForegroundColor Red; exit 1 }
Write-Host 'assert-portal-pages tests: PASS' -ForegroundColor Green
exit 0
