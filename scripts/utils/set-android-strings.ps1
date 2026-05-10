<#
.SYNOPSIS
    Updates one Android string key in EN/RU/UK with a single command.

.DESCRIPTION
    Thin batch wrapper around set-android-string.ps1. Applies the same key update to
    values/strings.xml, values-ru/strings.xml, and values-uk/strings.xml in sequence.

.PARAMETER Module
    Module path relative to repo root, for example: app_v2 or wear.

.PARAMETER Key
    Android string resource key.

.PARAMETER EnValue
    New value for values/strings.xml.

.PARAMETER RuValue
    New value for values-ru/strings.xml.

.PARAMETER UkValue
    New value for values-uk/strings.xml.

.PARAMETER ExpectedOldEnValue
    Optional safety guard for the current EN value.

.PARAMETER ExpectedOldRuValue
    Optional safety guard for the current RU value.

.PARAMETER ExpectedOldUkValue
    Optional safety guard for the current UK value.

.PARAMETER CreateIfMissing
    Appends the key in all three locale files when it does not exist.

.PARAMETER DryRun
    Prints the planned changes without writing files.

.EXAMPLE
    pwsh -File scripts/utils/set-android-strings.ps1 -Module app_v2 -Key "cloud_check_failed" -EnValue "Could not check the cloud connection. Try again." -RuValue "Не удалось проверить подключение к облаку. Попробуйте ещё раз." -UkValue "Не вдалося перевірити підключення до хмари. Спробуйте ще раз."

.EXAMPLE
    pwsh -File scripts/utils/set-android-strings.ps1 -Module app_v2 -Key "new_key" -EnValue "New text" -RuValue "Новый текст" -UkValue "Новий текст" -CreateIfMissing -DryRun
#>
[CmdletBinding()]
param(
    [string]$Module = "app_v2",

    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string]$Key,

    [Parameter(Mandatory)]
    [AllowEmptyString()]
    [string]$EnValue,

    [Parameter(Mandatory)]
    [AllowEmptyString()]
    [string]$RuValue,

    [Parameter(Mandatory)]
    [AllowEmptyString()]
    [string]$UkValue,

    [string]$ExpectedOldEnValue,
    [string]$ExpectedOldRuValue,
    [string]$ExpectedOldUkValue,
    [switch]$CreateIfMissing,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$singleScriptPath = Join-Path $PSScriptRoot 'set-android-string.ps1'
if (-not (Test-Path $singleScriptPath)) {
    throw "Required helper script not found: $singleScriptPath"
}

$updates = @(
    @{
        Locale           = 'en'
        Value            = $EnValue
        ExpectedOldValue = $ExpectedOldEnValue
    },
    @{
        Locale           = 'ru'
        Value            = $RuValue
        ExpectedOldValue = $ExpectedOldRuValue
    },
    @{
        Locale           = 'uk'
        Value            = $UkValue
        ExpectedOldValue = $ExpectedOldUkValue
    }
)

foreach ($update in $updates) {
    $singleParams = @{
        Module = $Module
        Locale = $update.Locale
        Key    = $Key
        Value  = $update.Value
    }

    if ($CreateIfMissing) {
        $singleParams.CreateIfMissing = $true
    }

    if ($DryRun) {
        $singleParams.DryRun = $true
    }

    if (-not [string]::IsNullOrEmpty($update.ExpectedOldValue)) {
        $singleParams.ExpectedOldValue = $update.ExpectedOldValue
    }

    & $singleScriptPath @singleParams
}