<#
.SYNOPSIS
    S1190: single reader for a module's declared interface locales.

.DESCRIPTION
    The set of supported languages is declared once per module, in
    <module>/src/main/res/xml/locales_config.xml - the same file Android itself reads for the system
    per-app language picker. Every script that needs to know which locales exist dot-sources this
    file instead of restating the list, so adding a language stays a one-line data edit.

    S2054: the module is a parameter, defaulting to app_v2. Both app_v2 and wear ship their own
    declaration and the pre-release content gate already runs per module, so pinning this reader to
    app_v2 left wear judged against the phone's list.

    Two strictness levels exist and are deliberately different (strategic ADR-6):
      - STRICT  (en, ru, uk) - authored by the owner, must stay complete; a missing key is an error.
      - DECLARED (all thirteen) - machine-assisted translations, best-effort; a missing key is a
        reported count, not a failure.
    Strictness is a policy about which locales must be authored per ticket, so it is the same for
    every module and takes no module argument.

    Exposed functions:
      Get-SupportedLocales      - every declared tag of a module, in declaration order.
      Get-StrictLocales         - the subset that must stay complete.
      Get-LocaleSetConfigPath   - the declaration file belonging to a module.
      Get-LocaleResourceDir     - the res/ directory a tag maps to (values, values-ru, values-b+zh+Hans).
      Test-StrictLocale         - true when a tag belongs to the strict subset.

.EXAMPLE
    . ./scripts/utils/locale-set.ps1
    (Get-SupportedLocales).Count                  # 13 - app_v2 by default
    (Get-SupportedLocales -Module wear).Count     # 13 - read from wear's own declaration
    Get-LocaleResourceDir -Tag 'zh-Hans'          # values-b+zh+Hans

.OUTPUTS
    Exit codes (only when the declaration cannot be read; dot-sourcing otherwise never exits):
      0 - not used; the script defines functions and returns.
      3 - locales_config.xml missing or declaring no locale.
#>

$script:LocaleSetRepoRoot = Join-Path $PSScriptRoot '..\..'
$script:LocaleSetStrictTags = @('en', 'ru', 'uk')
$script:LocaleSetCache = @{}

function Get-LocaleSetConfigPath {
    <# The declaration belonging to a module. Each shipped module declares its own languages. #>
    param([string]$Module = 'app_v2')

    return (Join-Path $script:LocaleSetRepoRoot "$Module\src\main\res\xml\locales_config.xml")
}

function Get-SupportedLocales {
    <#
        Declared interface locales of a module, in declaration order. Parsed once per module per session.

        S2054: the module is a parameter rather than a pin to app_v2. Both shipped modules declare their
        own languages and the pre-release gate already judges both, so a fixed path made the wear
        declaration decorative - wear was judged against the phone's list and the two agreeing was a
        coincidence nothing checked.
    #>
    param([string]$Module = 'app_v2')

    if ($script:LocaleSetCache.ContainsKey($Module)) { return $script:LocaleSetCache[$Module] }

    $configPath = Get-LocaleSetConfigPath -Module $Module
    if (-not (Test-Path $configPath)) {
        Write-Error "locale-set: declaration not found: $configPath" -ErrorAction Continue
        exit 3
    }

    [xml]$xml = Get-Content $configPath -Encoding UTF8
    $tags = @($xml.'locale-config'.locale | ForEach-Object { $_.name } | Where-Object { $_ })
    if ($tags.Count -eq 0) {
        Write-Error "locale-set: no <locale> declared in $configPath" -ErrorAction Continue
        exit 3
    }

    $script:LocaleSetCache[$Module] = $tags
    return $tags
}

function Get-StrictLocales {
    <# The owner-authored locales that must stay complete. #>
    return $script:LocaleSetStrictTags
}

function Test-StrictLocale {
    param([Parameter(Mandatory)][string]$Tag)
    return $script:LocaleSetStrictTags -contains $Tag.Trim().ToLowerInvariant()
}

function Get-LocaleResourceDir {
    <#
        The res/ directory holding a tag's strings. The base language needs no qualifier, and a tag
        carrying a script subtag needs the BCP-47 form (values-b+zh+Hans) - values-zh-Hans is not a
        directory name Android accepts.
    #>
    param([Parameter(Mandatory)][string]$Tag)

    $normalized = $Tag.Trim()
    if ($normalized -ieq 'en') { return 'values' }
    if ($normalized -match '-') { return 'values-b+' + ($normalized -replace '-', '+') }
    return "values-$($normalized.ToLowerInvariant())"
}
