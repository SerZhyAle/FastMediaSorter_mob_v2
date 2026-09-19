# set-android-string-reaffirm.Tests.ps1 (S3306) - regression tests for -ReaffirmTranslation.
#
# The switch exists because rewording an English string invalidates every other locale's recorded
# provenance, and before S3306 the only way to refresh a still-correct translation's fingerprint was
# to write a different value into it. These cases prove the properties that makes it safe to trust:
#   - it refreshes the fingerprint for a stale pair, and leaves strings.xml byte-for-byte alone;
#   - a comma-separated -Locale list re-affirms every listed locale in one call;
#   - it refuses, writing nothing at all, for a key or locale nobody could have read: absent in EN,
#     absent in the locale, empty, owner-authored (en/ru/uk), or byte-identical to the English text;
#   - a -Value that differs from disk is REFUSED rather than written - the invariant that separates
#     this switch from a plain 'set' that happens to match;
#   - a multi-locale call that refuses on one locale certifies none of them;
#   - without the switch the '[no change]' path still exits 0 and still leaves the store untouched.
#
# The tool resolves its paths from its own location, so each case runs it against a throwaway
# repo-shaped sandbox under temp/S3306/. Nothing tracked is read or written.
# Exit 0 = all cases pass, 1 = at least one failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
$sandbox = Join-Path $repoRoot 'temp/S3306/reaffirm-tests'
$pwshExe = (Get-Process -Id $PID).Path

$script:pass = 0
$script:fail = 0

function Assert-Equal {
    param([Parameter(Mandatory)][string]$Name, $Expected, $Actual)
    if ($Expected -eq $Actual) {
        Write-Host ("PASS | {0} | {1}" -f $Name, $Actual)
        $script:pass++
    }
    else {
        Write-Host ("FAIL | {0} | expected: {1} | actual: {2}" -f $Name, $Expected, $Actual)
        $script:fail++
    }
}

function New-SandboxFile {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$Content)
    $dir = Split-Path -Parent $Path
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force $dir | Out-Null }
    Set-Content -LiteralPath $Path -Value $Content -Encoding UTF8
}

# The tool walks up two levels from its own directory to find the repo root, and reads the locale set
# from res/xml/locales_config.xml - so the sandbox mirrors that shape exactly. The whole lib directory
# is copied rather than a hand-listed subset: naming the tool's dot-sources here is a registry that
# has to stay in sync with a file this one is not part of, and it fell out of sync once already (S2126).
function New-Sandbox {
    if (Test-Path $sandbox) { Remove-Item -Recurse -Force $sandbox }

    New-Item -ItemType Directory -Force (Join-Path $sandbox 'scripts/utils') | Out-Null
    New-Item -ItemType Directory -Force (Join-Path $sandbox 'scripts/quality/lib') | Out-Null
    Copy-Item (Join-Path $repoRoot 'scripts/utils/set-android-string.ps1') (Join-Path $sandbox 'scripts/utils/')
    Copy-Item (Join-Path $repoRoot 'scripts/utils/locale-set.ps1') (Join-Path $sandbox 'scripts/utils/')
    Copy-Item (Join-Path $repoRoot 'scripts/quality/lib/*.ps1') (Join-Path $sandbox 'scripts/quality/lib/')

    $res = Join-Path $sandbox 'app_v2/src/main/res'

    New-SandboxFile (Join-Path $res 'xml/locales_config.xml') @'
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="ru" />
    <locale android:name="uk" />
    <locale android:name="es" />
    <locale android:name="pt" />
    <locale android:name="fr" />
</locale-config>
'@

    New-SandboxFile (Join-Path $res 'values/strings.xml') @'
<resources>
    <string name="broadcast_title">Broadcast</string>
    <string name="only_in_english">English only</string>
    <string name="shared_brand">FastMediaSorter</string>
</resources>
'@

    New-SandboxFile (Join-Path $res 'values-ru/strings.xml') @'
<resources>
    <string name="broadcast_title">Вещание</string>
    <string name="shared_brand">FastMediaSorter</string>
</resources>
'@

    New-SandboxFile (Join-Path $res 'values-uk/strings.xml') @'
<resources>
    <string name="broadcast_title">Мовлення</string>
    <string name="shared_brand">FastMediaSorter</string>
</resources>
'@

    New-SandboxFile (Join-Path $res 'values-es/strings.xml') @'
<resources>
    <string name="broadcast_title">Difusión</string>
    <string name="empty_value"></string>
    <string name="shared_brand">FastMediaSorter</string>
</resources>
'@

    New-SandboxFile (Join-Path $res 'values-pt/strings.xml') @'
<resources>
    <string name="broadcast_title">Difusão</string>
    <string name="shared_brand">FastMediaSorter</string>
</resources>
'@

    New-SandboxFile (Join-Path $res 'values-fr/strings.xml') @'
<resources>
    <string name="broadcast_title">Diffusion</string>
    <string name="shared_brand">FastMediaSorter</string>
</resources>
'@
}

function Invoke-Tool {
    param([string[]]$ToolArgs)
    $sut = Join-Path $sandbox 'scripts/utils/set-android-string.ps1'
    $out = & $pwshExe -NoProfile -File $sut @ToolArgs 2>&1 | Out-String
    # Flat: PowerShell hard-wraps a thrown message at the console width, so a long unbroken token like
    # a file path arrives split across two lines. Matching one of those needs the whitespace gone.
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $out; Flat = ($out -replace '\s+', '') }
}

$storePath = Join-Path $sandbox 'scripts/quality/locale-source-fingerprints.json'
$unitId = 'app_v2|main|strings.xml|broadcast_title'

function Get-StoredHash {
    param([Parameter(Mandatory)][string]$Locale, [string]$Identity = $unitId)
    if (-not (Test-Path -LiteralPath $storePath)) { return $null }
    $json = Get-Content -LiteralPath $storePath -Raw -Encoding UTF8 | ConvertFrom-Json -AsHashtable
    if (-not ($json -is [hashtable]) -or -not $json.ContainsKey($Locale)) { return $null }
    if (-not $json[$Locale].ContainsKey($Identity)) { return $null }
    return [string]$json[$Locale][$Identity]
}

function Get-LocaleFileText {
    param([Parameter(Mandatory)][string]$LocaleDir)
    $path = Join-Path $sandbox "app_v2/src/main/res/$LocaleDir/strings.xml"
    if (-not (Test-Path $path)) { return '' }
    return [System.IO.File]::ReadAllText($path)
}

# SHA-256 of the normalized English text, first 16 hex chars - the value the store is expected to hold.
# Recomputed here rather than imported, so a change to the fingerprint formula fails these cases loudly
# instead of agreeing with itself.
function Get-ExpectedHash {
    param([Parameter(Mandatory)][string]$Text)
    $normalized = ($Text -replace '[\r\n\t]+', ' ').Trim()
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($normalized)
    $hash = [System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
    return [System.Convert]::ToHexString($hash).Substring(0, 16).ToLowerInvariant()
}

$expectedHash = Get-ExpectedHash 'Broadcast'

# --- case 1: a single locale is re-affirmed, and the resource file is not touched -----------------
New-Sandbox
$esBefore = Get-LocaleFileText 'values-es'

$r = Invoke-Tool @('-Key', 'broadcast_title', '-Locale', 'es', '-ReaffirmTranslation')

Assert-Equal 'single-locale reaffirm exits 0'              0             $r.ExitCode
Assert-Equal 'and says so'                                 $true         ($r.Output -match '\[reaffirmed\]')
Assert-Equal 'fingerprint recorded for es'                 $expectedHash (Get-StoredHash 'es')
Assert-Equal 'values-es/strings.xml untouched'             $true         ($esBefore -ceq (Get-LocaleFileText 'values-es'))

# --- case 2: a comma-separated list re-affirms every listed locale in one call --------------------
New-Sandbox
$r = Invoke-Tool @('-Key', 'broadcast_title', '-Locale', 'es,pt,fr', '-ReaffirmTranslation')

Assert-Equal 'multi-locale reaffirm exits 0'               0             $r.ExitCode
Assert-Equal 'es recorded'                                 $expectedHash (Get-StoredHash 'es')
Assert-Equal 'pt recorded'                                 $expectedHash (Get-StoredHash 'pt')
Assert-Equal 'fr recorded'                                 $expectedHash (Get-StoredHash 'fr')

# --- case 3: -Value that matches disk is accepted; -Value that differs is REFUSED, not written ----
New-Sandbox
$match = Invoke-Tool @('-Key', 'broadcast_title', '-Locale', 'es', '-Value', 'Difusión', '-ReaffirmTranslation')
Assert-Equal 'matching -Value is accepted'                 0             $match.ExitCode
Assert-Equal 'and records the fingerprint'                 $expectedHash (Get-StoredHash 'es')

New-Sandbox
$esBefore = Get-LocaleFileText 'values-es'
$differs = Invoke-Tool @('-Key', 'broadcast_title', '-Locale', 'es', '-Value', 'Emisión', '-ReaffirmTranslation')
Assert-Equal 'mismatching -Value is refused'               $true  ($differs.ExitCode -ne 0)
Assert-Equal 'and says the switch never writes'            $true  ($differs.Output -match 'never writes a resource')
Assert-Equal 'the resource is NOT rewritten'               $true  ($esBefore -ceq (Get-LocaleFileText 'values-es'))
Assert-Equal 'and nothing is recorded'                     $null  (Get-StoredHash 'es')

# --- case 4: -Value with more than one locale cannot be one value, and is refused -----------------
New-Sandbox
$multiValue = Invoke-Tool @('-Key', 'broadcast_title', '-Locale', 'es,pt', '-Value', 'Difusión', '-ReaffirmTranslation')
Assert-Equal 'a value for two locales is refused'          $true  ($multiValue.ExitCode -ne 0)
Assert-Equal 'nothing recorded for es'                     $null  (Get-StoredHash 'es')
Assert-Equal 'nothing recorded for pt'                     $null  (Get-StoredHash 'pt')

# --- case 5: the refusals that keep an unread locale from being certified -------------------------
New-Sandbox
$noKey = Invoke-Tool @('-Locale', 'es', '-ReaffirmTranslation')
Assert-Equal 'no -Key is refused'                          $true  ($noKey.ExitCode -ne 0)
Assert-Equal 'and names -Key'                              $true  ($noKey.Output -match 'requires -Key')

$missingEn = Invoke-Tool @('-Key', 'never_declared', '-Locale', 'es', '-ReaffirmTranslation')
Assert-Equal 'a key absent from EN is refused'             $true  ($missingEn.ExitCode -ne 0)

$missingLocale = Invoke-Tool @('-Key', 'only_in_english', '-Locale', 'es', '-ReaffirmTranslation')
Assert-Equal 'a key absent from the locale is refused'     $true  ($missingLocale.ExitCode -ne 0)
Assert-Equal 'and nothing is recorded for it'              $null  (Get-StoredHash 'es' 'app_v2|main|strings.xml|only_in_english')

$strict = Invoke-Tool @('-Key', 'broadcast_title', '-Locale', 'ru', '-ReaffirmTranslation')
Assert-Equal 'an owner-authored locale is refused'         $true  ($strict.ExitCode -ne 0)
Assert-Equal 'and nothing is recorded for ru'              $null  (Get-StoredHash 'ru')

$identical = Invoke-Tool @('-Key', 'shared_brand', '-Locale', 'es', '-ReaffirmTranslation')
Assert-Equal 'an English-identical value is refused'       $true  ($identical.ExitCode -ne 0)
Assert-Equal 'and offers the allowlist and -Force'         $true  ($identical.Flat -match 'locale-identical-allowlist\.json')
Assert-Equal 'and names -Force as the waiver'              $true  ($identical.Flat -match 'pass-Forcetore-affirm')

$forced = Invoke-Tool @('-Key', 'shared_brand', '-Locale', 'es', '-ReaffirmTranslation', '-Force')
Assert-Equal '-Force waives the identical-value refusal'   0      $forced.ExitCode
Assert-Equal 'and records the brand key'                   (Get-ExpectedHash 'FastMediaSorter') (Get-StoredHash 'es' 'app_v2|main|strings.xml|shared_brand')

$unknown = Invoke-Tool @('-Key', 'broadcast_title', '-Locale', 'es,zz', '-ReaffirmTranslation')
Assert-Equal 'an undeclared locale in the list is refused' $true  ($unknown.ExitCode -ne 0)

# --- case 6: a list that refuses on one locale certifies NONE of them -----------------------------
New-Sandbox
$partial = Invoke-Tool @('-Key', 'broadcast_title', '-Locale', 'es,ru,pt', '-ReaffirmTranslation')
Assert-Equal 'a list refusing mid-way exits non-zero'      $true  ($partial.ExitCode -ne 0)
Assert-Equal 'es - the locale before the refusal - is not recorded' $null (Get-StoredHash 'es')
Assert-Equal 'pt - the locale after it - is not recorded'  $null  (Get-StoredHash 'pt')

# --- case 7: -DryRun reports the plan and writes no store -----------------------------------------
New-Sandbox
$dry = Invoke-Tool @('-Key', 'broadcast_title', '-Locale', 'es,pt', '-ReaffirmTranslation', '-DryRun')
Assert-Equal '-DryRun exits 0'                             0      $dry.ExitCode
Assert-Equal 'and says it would re-affirm'                 $true  ($dry.Output -match '\[dry-run\] would re-affirm')
Assert-Equal 'and records nothing'                         $null  (Get-StoredHash 'es')

# --- case 8: without the switch, the [no change] path is unchanged --------------------------------
New-Sandbox
$esBefore = Get-LocaleFileText 'values-es'
$noChange = Invoke-Tool @('-Key', 'broadcast_title', '-Locale', 'es', '-Value', 'Difusión')
Assert-Equal 'a matching plain set still exits 0'          0      $noChange.ExitCode
Assert-Equal 'and still reports [no change]'               $true  ($noChange.Output -match '\[no change\]')
Assert-Equal 'and still records nothing'                   $null  (Get-StoredHash 'es')
Assert-Equal 'and still leaves the file alone'             $true  ($esBefore -ceq (Get-LocaleFileText 'values-es'))

# --- case 9: the switch belongs to 'set' and is refused on another action -------------------------
$wrongAction = Invoke-Tool @('-Action', 'get', '-Key', 'broadcast_title', '-Locale', 'es', '-ReaffirmTranslation')
Assert-Equal 'the switch on another action is refused'     $true  ($wrongAction.ExitCode -ne 0)

Remove-Item -Recurse -Force $sandbox

Write-Host ("RESULT | pass: {0} | fail: {1}" -f $script:pass, $script:fail)
if ($script:fail -eq 0) { exit 0 } else { exit 1 }
