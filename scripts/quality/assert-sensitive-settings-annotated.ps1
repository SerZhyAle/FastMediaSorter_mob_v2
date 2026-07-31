#requires -Version 7.0
<#
.SYNOPSIS
    S1254: secret masking in the settings dump must survive R8 field renaming.

.DESCRIPTION
    The startup settings dump masks credentials by field-name hints, which silently dies when
    R8 renames AppSettings fields - a real password reached exported diagnostics twice before
    S1187 pinned the names. Defense is now layered and this gate keeps every layer present:

    1. Every AppSettings field whose name matches a secret hint (password/secret/token/apikey/
       credential) must carry `@field:SensitiveSetting` - the rename-proof mask trigger.
    2. FastMediaSorterApp must still consult `isAnnotationPresent(SensitiveSetting`.
    3. proguard-rules.pro must still carry the S1187 `-keepclassmembernames` rule for
       AppSettings fields and a `-keepattributes` covering runtime annotations - the rule
       already vanished once (b4106200) and the regression is only visible in an exported
       log from a stranger's device.

.OUTPUTS
    Exit 0 - all three layers present.
    Exit 1 - at least one layer missing (listed).

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-sensitive-settings-annotated.ps1 -Gate
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$appSettings = Join-Path $repoRoot 'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt'
$appClass = Join-Path $repoRoot 'app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt'
$proguard = Join-Path $repoRoot 'app_v2/proguard-rules.pro'

$hints = @('password', 'secret', 'token', 'apikey', 'credential')
$problems = [System.Collections.Generic.List[string]]::new()

$settingsLines = Get-Content $appSettings
for ($i = 0; $i -lt $settingsLines.Count; $i++) {
    $m = [regex]::Match($settingsLines[$i], '^\s*val\s+(\w+)\s*:')
    if (-not $m.Success) { continue }
    $name = $m.Groups[1].Value.ToLowerInvariant()
    $isSecret = $false
    foreach ($h in $hints) { if ($name.Contains($h)) { $isSecret = $true; break } }
    if (-not $isSecret) { continue }
    $prev1 = if ($i -ge 1) { $settingsLines[$i - 1] } else { '' }
    $prev2 = if ($i -ge 2) { $settingsLines[$i - 2] } else { '' }
    $annotated = $settingsLines[$i].Contains('@field:SensitiveSetting') -or
        $prev1.Contains('@field:SensitiveSetting') -or $prev2.Contains('@field:SensitiveSetting')
    if (-not $annotated) {
        $problems.Add("AppSettings.$($m.Groups[1].Value) matches a secret hint but lacks @field:SensitiveSetting")
    }
}

$appText = Get-Content $appClass -Raw
if ($appText -notmatch 'isAnnotationPresent\(SensitiveSetting') {
    $problems.Add('FastMediaSorterApp no longer checks isAnnotationPresent(SensitiveSetting..) in the dump')
}

$proText = Get-Content $proguard -Raw
if ($proText -notmatch '(?s)-keepclassmembernames\s+class\s+com\.sza\.fastmediasorter\.domain\.model\.AppSettings\s*\{\s*<fields>;') {
    $problems.Add('proguard-rules.pro lost the S1187 keepclassmembernames rule for AppSettings fields')
}
if ($proText -notmatch '-keepattributes\s+[^\r\n]*(\*Annotation\*|RuntimeVisibleAnnotations)') {
    $problems.Add('proguard-rules.pro lost the runtime-annotation keepattributes line')
}

if ($problems.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host 'assert-sensitive-settings-annotated: PASS (annotation, dump check and keep rules all present)'
    }
    exit 0
}

Write-Host 'assert-sensitive-settings-annotated: FAIL - settings-dump secret masking is no longer rename-proof:'
foreach ($p in $problems) { Write-Host "  $p" }
exit 1
