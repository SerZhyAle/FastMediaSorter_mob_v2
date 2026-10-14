<#
.SYNOPSIS
    Contract tests for assert-site-origins.ps1.
.DESCRIPTION
Run-Tests.ps1 - contract tests for assert-site-origins.ps1 (S4098).
# Subject: scripts/quality/site-origins.psd1

Every case builds a throwaway site under the system temp directory with its own declarations and
_config.yml and runs the gate against it, so no case depends on what the live site loads this minute.
The decisive cases are the refusals - an undeclared host, a stale record, a privacy page that does not
name a host, an advertisement on a trust page - and the non-loads that must stay silent: a link the
visitor clicks, an excluded directory, an example inside a code fence or an HTML comment.

Exit codes (CLAUDE.md Rule 7):
  0  every case passed
  1  at least one case failed
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$Gate = (Resolve-Path (Join-Path $PSScriptRoot '..\assert-site-origins.ps1')).Path
$passed = 0
$failed = 0
$fixtures = [System.Collections.Generic.List[string]]::new()

$originsText = @'
@{
    Origins = @(
        @{ Host = 'ads.example.com'; Purpose = 'ads'; Receives = 'ip'; Groups = 'landing'; Advertising = $true }
        @{ Host = 'fonts.example.com'; Purpose = 'fonts'; Receives = 'ip'; Groups = 'every page'; Advertising = $false }
        @{ Host = 'api.example.com'; Purpose = 'releases'; Receives = 'ip'; Groups = 'landing'; Advertising = $false }
        @{ Host = 'badges.example.com'; Purpose = 'badges'; Receives = 'ip'; Groups = 'readme'; Advertising = $false }
        @{ Host = 'themefont.example.com'; Purpose = 'theme font'; Receives = 'ip'; Groups = 'docs'; Advertising = $false; Theme = 'test-theme' }
    )
    AdvertisingPages = @('index.html', 'index-*.html')
    FirstParty = @('site.example.com')
}
'@

$trustText = @'
@{
    Pages = @(
        @{ Locale = 'en'; Path = 'docs/TRUST.md'; Privacy = 'docs/PRIVACY.md'; PrivacyLink = 'PRIVACY.md'; Headings = @('A') }
    )
}
'@

$privacyAll = "# Privacy`n`nThe site loads ads.example.com, fonts.example.com, api.example.com, badges.example.com and themefont.example.com.`n"

function New-Fixture {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ('s4098-' + [guid]::NewGuid().ToString('N'))
    $fixtures.Add($root)
    foreach ($dir in 'docs', 'assets', '_layouts', 'private') {
        New-Item -ItemType Directory -Path (Join-Path $root $dir) -Force | Out-Null
    }
    $files = [ordered]@{
        'origins.psd1'        = $originsText
        'trust.psd1'          = $trustText
        '_config.yml'         = "theme: test-theme`nexclude:`n  - private/`n  - origins.psd1`n"
        'index.html'          = "<html><head><link rel=`"stylesheet`" href=`"https://fonts.example.com/css`">`n<link rel=`"canonical`" href=`"https://site.example.com/`">`n<script async src=`"https://ads.example.com/ad.js`"></script>`n<script src=`"assets/app.js`"></script></head>`n<body><a href=`"https://github.example.com/repo`">code</a></body></html>`n"
        'assets/app.js'       = "fetchWithTimeout('https://api.example.com/repos/x/releases', {});`nvar page = 'https://github.example.com/releases';`n"
        '_layouts/default.html' = "<html><body>{{ content }}</body></html>`n"
        'docs/TRUST.md'       = "---`nlayout: default`n---`n# Trust`n`nNothing loads here.`n"
        'docs/PRIVACY.md'     = $privacyAll
        'docs/README.md'      = "# Readme`n`n<img src=`"https://badges.example.com/v.svg`" alt=`"version`">`n`n[source](https://github.example.com/repo)`n"
        'private/secret.html' = "<script src=`"https://hidden.example.com/x.js`"></script>`n"
    }
    foreach ($name in $files.Keys) {
        Set-Content -LiteralPath (Join-Path $root $name) -Value $files[$name] -Encoding utf8 -NoNewline
    }
    return $root
}

function Invoke-Case {
    param(
        [string]$Name,
        [scriptblock]$Mutate,
        [int]$ExpectedExit,
        [string]$ExpectedText
    )
    $root = New-Fixture
    if ($Mutate) { & $Mutate $root }
    $output = & pwsh -NoProfile -File $Gate -Root $root -Declaration (Join-Path $root 'origins.psd1') -TrustDeclaration (Join-Path $root 'trust.psd1') 2>&1 | Out-String
    $exitCode = $LASTEXITCODE
    $ok = ($exitCode -eq $ExpectedExit) -and (-not $ExpectedText -or $output.Contains($ExpectedText))
    if ($ok) {
        $script:passed++
        Write-Host "  PASS  $Name"
    }
    else {
        $script:failed++
        Write-Host "  FAIL  $Name - expected: exit $ExpectedExit '$ExpectedText' | actual: exit $exitCode" -ForegroundColor Red
        Write-Host $output
    }
}

function Add-Text([string]$Root, [string]$Relative, [string]$Text) {
    Set-Content -LiteralPath (Join-Path $Root $Relative) -Value $Text -Encoding utf8 -NoNewline
}

try {
    Invoke-Case 'clean site passes' $null 0 'PASS'

    Invoke-Case 'undeclared script host fails' {
        param($r) Add-Text $r 'docs/page.html' '<script src="https://tracker.example.com/t.js"></script>'
    } 1 '[UNDECLARED] docs/page.html: loads tracker.example.com'

    Invoke-Case 'undeclared preconnect link fails' {
        param($r) Add-Text $r 'docs/page.html' '<link rel="preconnect" href="https://cdn.example.com">'
    } 1 '[UNDECLARED] docs/page.html: loads cdn.example.com'

    Invoke-Case 'undeclared markdown image fails' {
        param($r) Add-Text $r 'docs/guide.md' "# Guide`n`n![shot](https://images.example.com/a.png)`n"
    } 1 '[UNDECLARED] docs/guide.md: loads images.example.com'

    Invoke-Case 'undeclared css import fails' {
        param($r) Add-Text $r 'styles.css' "@import url('https://fonts2.example.com/css');`n"
    } 1 '[UNDECLARED] styles.css: loads fonts2.example.com'

    Invoke-Case 'undeclared fetch in a script file fails' {
        param($r) Add-Text $r 'assets/more.js' "fetch(`"https://beacon.example.com/hit`");`n"
    } 1 '[UNDECLARED] assets/more.js: loads beacon.example.com'

    Invoke-Case 'undeclared fetch in an inline script fails' {
        param($r) Add-Text $r 'docs/page.html' "<script>`nfetch('https://beacon.example.com/hit');`n</script>`n"
    } 1 '[UNDECLARED] docs/page.html: loads beacon.example.com'

    Invoke-Case 'declared host nobody loads fails as stale' {
        param($r) Add-Text $r 'docs/README.md' "# Readme`n"
    } 1 '[STALE] badges.example.com is declared'

    Invoke-Case 'theme host with no file passes while the theme is configured' $null 0 'PASS'

    Invoke-Case 'theme host fails as stale once the theme changes' {
        param($r) Add-Text $r '_config.yml' "theme: other-theme`nexclude:`n  - private/`n"
    } 1 '[STALE] themefont.example.com is declared'

    Invoke-Case 'privacy page not naming a host fails' {
        param($r) Add-Text $r 'docs/PRIVACY.md' "# Privacy`n`nThe site loads ads.example.com, fonts.example.com, api.example.com and themefont.example.com.`n"
    } 1 '[PRIVACY] docs/PRIVACY.md: does not name badges.example.com'

    Invoke-Case 'missing privacy page fails' {
        param($r) Remove-Item -LiteralPath (Join-Path $r 'docs/PRIVACY.md')
    } 1 '[PRIVACY] docs/PRIVACY.md is missing'

    Invoke-Case 'advertisement on a trust page fails' {
        param($r) Add-Text $r 'docs/TRUST.md' "---`nlayout: default`n---`n<script async src=`"https://ads.example.com/ad.js`"></script>`n"
    } 1 '[ADVERTISING] docs/TRUST.md: a trust or privacy page loads ads.example.com'

    Invoke-Case 'advertisement in the layout of a trust page fails' {
        param($r) Add-Text $r '_layouts/default.html' "<html><head><script async src=`"https://ads.example.com/ad.js`"></script></head><body>{{ content }}</body></html>`n"
    } 1 '[ADVERTISING] docs/TRUST.md: a trust or privacy page renders through _layouts/default.html'

    Invoke-Case 'advertisement on a page outside the landing fails' {
        param($r) Add-Text $r 'nolegal.html' '<script async src="https://ads.example.com/ad.js"></script>'
    } 1 '[ADVERTISING] nolegal.html: a page whose role carries no advertising'

    Invoke-Case 'advertisement on a landing locale page passes' {
        param($r) Add-Text $r 'index-ru.html' '<script async src="https://ads.example.com/ad.js"></script>'
    } 0 'PASS'

    Invoke-Case 'a link the visitor clicks does not count' {
        param($r) Add-Text $r 'docs/page.html' '<a href="https://elsewhere.example.com/">elsewhere</a> <link rel="alternate" hreflang="ru" href="https://mirror.example.com/ru/">'
    } 0 'PASS'

    Invoke-Case 'an excluded directory does not count' {
        param($r) Add-Text $r 'private/other.html' '<script src="https://hidden2.example.com/x.js"></script>'
    } 0 'PASS'

    Invoke-Case 'an underscore directory other than layouts and includes does not count' {
        param($r)
        New-Item -ItemType Directory -Path (Join-Path $r '_drafts') -Force | Out-Null
        Add-Text $r '_drafts/x.html' '<script src="https://draft.example.com/x.js"></script>'
    } 0 'PASS'

    Invoke-Case 'an example inside a markdown code fence does not count' {
        param($r) Add-Text $r 'docs/howto.md' "# How`n`n``````html`n<script src=`"https://example-cdn.example.com/lib.js`"></script>`n```````n`nInline ``<img src=`"https://inline.example.com/a.png`">`` too.`n"
    } 0 'PASS'

    Invoke-Case 'a commented-out script does not count' {
        param($r) Add-Text $r 'docs/page.html' '<!-- <script src="https://old.example.com/x.js"></script> -->'
    } 0 'PASS'

    $withRuntime = {
        param([string]$Root, [string]$Loader)
        $text = [IO.File]::ReadAllText((Join-Path $Root 'origins.psd1'))
        $record = "    RuntimeOrigins = @(@{ Host = 'quality.example.com'; LoadedBy = '$Loader'; Purpose = 'ad quality'; Receives = 'ip'; Seen = 'test' })`n    AdvertisingPages"
        Add-Text $Root 'origins.psd1' $text.Replace('    AdvertisingPages', $record)
    }
    $privacyRuntime = "# Privacy`n`nThe site loads ads.example.com, fonts.example.com, api.example.com, badges.example.com, themefont.example.com and quality.example.com.`n"

    Invoke-Case 'run-time host named on the privacy page passes and is never stale' {
        param($r) & $withRuntime $r 'ads.example.com'; Add-Text $r 'docs/PRIVACY.md' $privacyRuntime
    } 0 'PASS'

    Invoke-Case 'run-time host the privacy page does not name fails' {
        param($r) & $withRuntime $r 'ads.example.com'
    } 1 '[PRIVACY] docs/PRIVACY.md: does not name quality.example.com'

    Invoke-Case 'run-time host whose loader is undeclared fails' {
        param($r) & $withRuntime $r 'nowhere.example.com'; Add-Text $r 'docs/PRIVACY.md' $privacyRuntime
    } 1 "[RUNTIME] quality.example.com (LoadedBy 'nowhere.example.com')"

    Invoke-Case 'run-time host of an advertising loader on a non-landing page fails' {
        param($r)
        & $withRuntime $r 'ads.example.com'
        Add-Text $r 'docs/PRIVACY.md' $privacyRuntime
        Add-Text $r 'docs/page.html' '<script src="https://quality.example.com/q.js"></script>'
    } 1 '[ADVERTISING] docs/page.html: a page whose role carries no advertising loads quality.example.com'

    Invoke-Case 'unreadable declaration cannot verify' {
        param($r) Add-Text $r 'origins.psd1' '@{ Origins = '
    } 2 'COULD NOT VERIFY'

    Invoke-Case 'missing _config.yml cannot verify' {
        param($r) Remove-Item -LiteralPath (Join-Path $r '_config.yml')
    } 2 'COULD NOT VERIFY'
}
finally {
    foreach ($f in $fixtures) { Remove-Item -LiteralPath $f -Recurse -Force -ErrorAction SilentlyContinue }
}

Write-Host "assert-site-origins tests: $passed passed, $failed failed"
if ($failed -gt 0) { exit 1 }
exit 0
