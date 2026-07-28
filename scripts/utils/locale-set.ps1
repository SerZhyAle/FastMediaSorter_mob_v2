<#
.SYNOPSIS
    S1190: single reader for the app's declared interface locales.

.DESCRIPTION
    The set of supported languages is declared once, in
    app_v2/src/main/res/xml/locales_config.xml - the same file Android itself reads for the system
    per-app language picker. Every script that needs to know which locales exist dot-sources this
    file instead of restating the list, so adding a language stays a one-line data edit.

    Two strictness levels exist and are deliberately different (strategic ADR-6):
      - STRICT  (en, ru, uk) - authored by the owner, must stay complete; a missing key is an error.
      - DECLARED (all thirteen) - machine-assisted translations, best-effort; a missing key is a
        reported count, not a failure.

    Exposed functions:
      Get-SupportedLocales      - every declared tag, in declaration order (e.g. en, zh-Hans, hi, ..).
      Get-StrictLocales         - the subset that must stay complete.
      Get-LocaleResourceDir     - the res/ directory a tag maps to (values, values-ru, values-b+zh+Hans).
      Test-StrictLocale         - true when a tag belongs to the strict subset.

.EXAMPLE
    . ./scripts/utils/locale-set.ps1
    (Get-SupportedLocales).Count          # 13
    Get-LocaleResourceDir -Tag 'zh-Hans'  # values-b+zh+Hans

.OUTPUTS
    Exit codes (only when the declaration cannot be read; dot-sourcing otherwise never exits):
      0 - not used; the script defines functions and returns.
      3 - locales_config.xml missing or declaring no locale.
#>

$script:LocaleSetConfigPath = Join-Path $PSScriptRoot '..\..\app_v2\src\main\res\xml\locales_config.xml'
$script:LocaleSetStrictTags = @('en', 'ru', 'uk')
$script:LocaleSetCache = $null

function Get-SupportedLocales {
    <# Declared interface locales, in declaration order. Parsed once per session. #>
    if ($null -ne $script:LocaleSetCache) { return $script:LocaleSetCache }

    if (-not (Test-Path $script:LocaleSetConfigPath)) {
        Write-Error "locale-set: declaration not found: $script:LocaleSetConfigPath" -ErrorAction Continue
        exit 3
    }

    [xml]$xml = Get-Content $script:LocaleSetConfigPath -Encoding UTF8
    $tags = @($xml.'locale-config'.locale | ForEach-Object { $_.name } | Where-Object { $_ })
    if ($tags.Count -eq 0) {
        Write-Error "locale-set: no <locale> declared in $script:LocaleSetConfigPath" -ErrorAction Continue
        exit 3
    }

    $script:LocaleSetCache = $tags
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
