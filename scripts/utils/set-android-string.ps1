<#
.SYNOPSIS
    Updates a single Android <string> resource by key for one locale.

.DESCRIPTION
    Safe targeted editor for values/strings.xml, values-ru/strings.xml, or values-uk/strings.xml.
    Preserves the surrounding file text, supports an ExpectedOldValue guard, and can append
    a missing key before </resources> when -CreateIfMissing is supplied.

.PARAMETER Module
    Module path relative to repo root, for example: app_v2, wear, or temp\string_tool_test.

.PARAMETER Locale
    Target locale file: en -> values, ru -> values-ru, uk -> values-uk.

.PARAMETER Key
    Android string resource key.

.PARAMETER Value
    New string body. Pass the intended Android string text.

.PARAMETER ExpectedOldValue
    Optional safety guard. If provided and the current decoded value differs, the script aborts.

.PARAMETER CreateIfMissing
    Appends a new <string> entry before </resources> if the key does not exist.

.PARAMETER DryRun
    Prints the planned change without writing the file.

.EXAMPLE
    pwsh -File scripts/utils/set-android-string.ps1 -Module app_v2 -Locale en -Key "cloud_check_failed" -Value "Could not check the cloud connection. Try again."

.EXAMPLE
    pwsh -File scripts/utils/set-android-string.ps1 -Module app_v2 -Locale ru -Key "cloud_check_failed" -ExpectedOldValue "Не удалось проверить подключение к облаку. Повторите попытку." -Value "Не удалось проверить подключение к облаку. Попробуйте ещё раз."

.EXAMPLE
    pwsh -File scripts/utils/set-android-string.ps1 -Module app_v2 -Locale uk -Key "new_key" -Value "New string value" -CreateIfMissing
#>
[CmdletBinding()]
param(
    [string]$Module = "app_v2",

    [Parameter(Mandatory)]
    [ValidateSet('en', 'ru', 'uk')]
    [string]$Locale,

    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string]$Key,

    [Parameter(Mandatory)]
    [AllowEmptyString()]
    [string]$Value,

    [string]$ExpectedOldValue,
    [switch]$CreateIfMissing,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Key -notmatch '^[A-Za-z0-9_.]+$') {
    throw "Invalid Android string key '$Key'. Use letters, digits, underscore, or dot only."
}

$repoRoot = Split-Path -Parent $PSScriptRoot | Split-Path -Parent
$localeDirByTag = @{
    en = 'values'
    ru = 'values-ru'
    uk = 'values-uk'
}

$filePath = Join-Path $repoRoot (Join-Path $Module "src/main/res/$($localeDirByTag[$Locale])/strings.xml")
if (-not (Test-Path $filePath)) {
    throw "strings.xml not found for module '$Module' locale '$Locale': $filePath"
}

function Get-FileEncodingForWrite {
    param([Parameter(Mandatory)][string]$Path)

    $bytes = [System.IO.File]::ReadAllBytes($Path)
    $hasBom =
    $bytes.Length -ge 3 -and
    $bytes[0] -eq 0xEF -and
    $bytes[1] -eq 0xBB -and
    $bytes[2] -eq 0xBF

    return [System.Text.UTF8Encoding]::new($hasBom)
}

function ConvertTo-XmlText {
    param([AllowEmptyString()][string]$Text)

    $escaped = [System.Security.SecurityElement]::Escape($Text)
    if ($null -eq $escaped) {
        return ''
    }

    return $escaped.Replace('&apos;', "\'")
}

function ConvertFrom-XmlText {
    param([AllowEmptyString()][string]$Text)

    return ([System.Net.WebUtility]::HtmlDecode($Text)).Replace("\'", "'")
}

$content = [System.IO.File]::ReadAllText($filePath)
$newline = if ($content.Contains("`r`n")) { "`r`n" } else { "`n" }
$keyRegex = [regex]::Escape($Key)
$pattern = '<string\b(?=[^>]*\bname\s*=\s*"' + $keyRegex + '")(?:[^>]*)>(?<value>.*?)</string>'
$stringEntries = @()
$currentEntry = [regex]::Match($content, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)
while ($currentEntry.Success) {
    $stringEntries += $currentEntry
    $currentEntry = $currentEntry.NextMatch()
}

if ($stringEntries.Count -gt 1) {
    throw "Key '$Key' appears $($stringEntries.Count) times in $filePath. Refuse to guess which entry to update."
}

$escapedValue = ConvertTo-XmlText $Value
$updatedContent = $content
$action = ''
$oldDecodedValue = $null

if ($stringEntries.Count -eq 1) {
    $match = $stringEntries[0]
    $currentRawValue = $match.Groups['value'].Value
    $oldDecodedValue = ConvertFrom-XmlText $currentRawValue

    if ($PSBoundParameters.ContainsKey('ExpectedOldValue') -and $oldDecodedValue -ne $ExpectedOldValue) {
        throw "ExpectedOldValue mismatch for key '$Key' in $filePath.`nExpected: $ExpectedOldValue`nActual:   $oldDecodedValue"
    }

    $tagText = $match.Value
    $openTagEnd = $tagText.IndexOf('>')
    $closeTagStart = $tagText.LastIndexOf('</string>', [System.StringComparison]::Ordinal)
    if ($openTagEnd -lt 0 -or $closeTagStart -lt 0) {
        throw "Could not rewrite key '$Key' in $filePath."
    }

    $newTagText = $tagText.Substring(0, $openTagEnd + 1) + $escapedValue + $tagText.Substring($closeTagStart)
    $updatedContent = $content.Substring(0, $match.Index) + $newTagText + $content.Substring($match.Index + $match.Length)
    $action = 'update'
}
else {
    if (-not $CreateIfMissing) {
        throw "Key '$Key' not found in $filePath. Use -CreateIfMissing to append it."
    }

    if ($PSBoundParameters.ContainsKey('ExpectedOldValue')) {
        throw "ExpectedOldValue cannot be used together with -CreateIfMissing for a missing key."
    }

    $closingTag = '</resources>'
    $closingIndex = $content.LastIndexOf($closingTag, [System.StringComparison]::Ordinal)
    if ($closingIndex -lt 0) {
        throw "Could not find </resources> in $filePath."
    }

    $prefix = $content.Substring(0, $closingIndex)
    $separator = if ($prefix.EndsWith("`n") -or $prefix.EndsWith("`r")) { '' } else { $newline }
    $newEntry = '    <string name="' + $Key + '">' + $escapedValue + '</string>' + $newline

    $updatedContent = $prefix + $separator + $newEntry + $content.Substring($closingIndex)
    $action = 'create'
}

if ($updatedContent -eq $content) {
    Write-Host "[no change] ${Locale}:$Key in $filePath already matches the requested value." -ForegroundColor Yellow
    exit 0
}

if ($DryRun) {
    Write-Host "[dry-run] $action ${Locale}:$Key in $filePath" -ForegroundColor Cyan
    if ($null -ne $oldDecodedValue) {
        Write-Host "Old: $oldDecodedValue" -ForegroundColor DarkGray
    }
    Write-Host "New: $Value" -ForegroundColor Green
    exit 0
}

$encoding = Get-FileEncodingForWrite -Path $filePath
[System.IO.File]::WriteAllText($filePath, $updatedContent, $encoding)

Write-Host "[done] $action ${Locale}:$Key in $filePath" -ForegroundColor Green
if ($null -ne $oldDecodedValue) {
    Write-Host "Old: $oldDecodedValue" -ForegroundColor DarkGray
}
Write-Host "New: $Value" -ForegroundColor Green