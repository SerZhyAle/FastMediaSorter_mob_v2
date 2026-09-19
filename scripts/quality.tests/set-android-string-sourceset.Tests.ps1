# set-android-string-sourceset.Tests.ps1 (S3315) - regression tests for -SourceSet addressing.
#
# The setter is the only byte-preserving string writer the rules allow, and until S3314/S3315 every
# path it built went through the hard-coded 'main' source set - so the ~946 (key, locale) pairs under
# app_v2/src/vr/res and app_v2/src/noLegal/res were unreachable, and the refusal for one of them
# advised -CreateIfMissing, which would have appended a second declaration into main shipping in every
# flavor. These cases prove the four properties that make the new addressing trustworthy:
#   - 'get' finds a flavor-only key under -SourceSet and does not find it without the flag;
#   - 'set -SourceSet vr' writes the flavor file and leaves main byte-for-byte alone;
#   - the fingerprint recorded for that write carries an 'app_v2|vr|' identity, not 'app_v2|main|';
#   - a refusal names the source set it searched, and points at the set that really holds the key
#     instead of advising a second declaration.
#
# The tool resolves its paths from its own location, so each case runs it against a throwaway
# repo-shaped sandbox under temp/S3315/. Nothing tracked is read or written.
# Exit 0 = all cases pass, 1 = at least one failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
$sandbox = Join-Path $repoRoot 'temp/S3315/sourceset-tests'
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
# from src/main/res/xml/locales_config.xml - module-level whatever -SourceSet says, which is itself one
# of the invariants under test here. The whole lib directory is copied rather than a hand-listed subset
# (S2126).
function New-Sandbox {
    if (Test-Path $sandbox) { Remove-Item -Recurse -Force $sandbox }

    New-Item -ItemType Directory -Force (Join-Path $sandbox 'scripts/utils') | Out-Null
    New-Item -ItemType Directory -Force (Join-Path $sandbox 'scripts/quality/lib') | Out-Null
    Copy-Item (Join-Path $repoRoot 'scripts/utils/set-android-string.ps1') (Join-Path $sandbox 'scripts/utils/')
    Copy-Item (Join-Path $repoRoot 'scripts/utils/locale-set.ps1') (Join-Path $sandbox 'scripts/utils/')
    Copy-Item (Join-Path $repoRoot 'scripts/quality/lib/*.ps1') (Join-Path $sandbox 'scripts/quality/lib/')

    $main = Join-Path $sandbox 'app_v2/src/main/res'
    $vr = Join-Path $sandbox 'app_v2/src/vr/res'

    New-SandboxFile (Join-Path $main 'xml/locales_config.xml') @'
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="ru" />
    <locale android:name="uk" />
    <locale android:name="ur" />
</locale-config>
'@

    New-SandboxFile (Join-Path $main 'values/strings.xml') @'
<resources>
    <string name="app_name">FastMediaSorter</string>
</resources>
'@

    New-SandboxFile (Join-Path $main 'values-ru/strings.xml') @'
<resources>
    <string name="app_name">FastMediaSorter</string>
</resources>
'@

    New-SandboxFile (Join-Path $main 'values-uk/strings.xml') @'
<resources>
    <string name="app_name">FastMediaSorter</string>
</resources>
'@

    # The locale directory exists in main but the flavor key does not - the exact shape that produced
    # the misleading refusal S3311 met.
    New-SandboxFile (Join-Path $main 'values-ur/strings.xml') @'
<resources>
    <string name="app_name">FastMediaSorter</string>
</resources>
'@

    New-SandboxFile (Join-Path $vr 'values/strings.xml') @'
<resources>
    <string name="vr_hud_prev">Previous</string>
</resources>
'@

    New-SandboxFile (Join-Path $vr 'values-ru/strings.xml') @'
<resources>
    <string name="vr_hud_prev">Назад</string>
</resources>
'@

    New-SandboxFile (Join-Path $vr 'values-uk/strings.xml') @'
<resources>
    <string name="vr_hud_prev">Назад</string>
</resources>
'@

    New-SandboxFile (Join-Path $vr 'values-ur/strings.xml') @'
<resources>
    <string name="vr_hud_prev">پچھلا</string>
</resources>
'@
}

function Invoke-Tool {
    param([string[]]$ToolArgs)
    $sut = Join-Path $sandbox 'scripts/utils/set-android-string.ps1'
    $out = & $pwshExe -NoProfile -File $sut @ToolArgs 2>&1 | Out-String
    # Flat: PowerShell hard-wraps a thrown message at the console width, so a long unbroken token like
    # a file path arrives split across two lines. Matching one of those needs the whitespace gone - and
    # the '|' gutter the error view prefixes each continuation line with, which otherwise lands inside
    # the very token being matched ("Use|-CreateIfMissing").
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output   = $out
        Flat     = (($out -replace '\s+', '') -replace '\|', '')
    }
}

function Get-SandboxText {
    param([Parameter(Mandatory)][string]$RelativePath)
    $path = Join-Path $sandbox $RelativePath
    if (-not (Test-Path $path)) { return '' }
    return [System.IO.File]::ReadAllText($path)
}

$storePath = Join-Path $sandbox 'scripts/quality/locale-source-fingerprints.json'

function Get-StoredIdentities {
    param([Parameter(Mandatory)][string]$Locale)
    if (-not (Test-Path -LiteralPath $storePath)) { return @() }
    $json = Get-Content -LiteralPath $storePath -Raw -Encoding UTF8 | ConvertFrom-Json -AsHashtable
    if (-not ($json -is [hashtable]) -or -not $json.ContainsKey($Locale)) { return @() }
    return @($json[$Locale].Keys)
}

# --- case 1: 'get' reaches the flavor key only with -SourceSet ------------------------------------
New-Sandbox

$vrGet = Invoke-Tool @('-Action', 'get', '-Key', 'vr_hud_prev', '-SourceSet', 'vr')
Assert-Equal 'get under -SourceSet vr exits 0'              0     $vrGet.ExitCode
Assert-Equal 'and prints the English value'                 $true ($vrGet.Output -match 'Previous')
# The value itself is asserted from the file, not from the console: a non-Latin string survives the
# write byte-for-byte but arrives at this process through the host's code page.
Assert-Equal 'and reaches the Urdu locale of that set'      $true ($vrGet.Output -match '\[UR\]')

$mainGet = Invoke-Tool @('-Action', 'get', '-Key', 'vr_hud_prev')
Assert-Equal 'get without the flag does not find it'        1     $mainGet.ExitCode
Assert-Equal 'and reports the hand-authored locales missing' $true ($mainGet.Output -match 'MISSING')

# --- case 2: 'set -SourceSet vr' writes the flavor file and nothing else --------------------------
New-Sandbox
$mainUrBefore = Get-SandboxText 'app_v2/src/main/res/values-ur/strings.xml'
$vrEnBefore = Get-SandboxText 'app_v2/src/vr/res/values/strings.xml'

$write = Invoke-Tool @('-SourceSet', 'vr', '-Locale', 'ur', '-Key', 'vr_hud_prev', '-Value', 'سابقہ')
Assert-Equal 'set into the vr source set exits 0'           0     $write.ExitCode
Assert-Equal 'the vr Urdu file carries the new value'       $true ((Get-SandboxText 'app_v2/src/vr/res/values-ur/strings.xml') -match 'سابقہ')
Assert-Equal 'the main Urdu file is untouched'              $true ($mainUrBefore -ceq (Get-SandboxText 'app_v2/src/main/res/values-ur/strings.xml'))
Assert-Equal 'no second declaration appeared in main'       $false ((Get-SandboxText 'app_v2/src/main/res/values-ur/strings.xml') -match 'vr_hud_prev')
Assert-Equal 'the vr English source is untouched'           $true ($vrEnBefore -ceq (Get-SandboxText 'app_v2/src/vr/res/values/strings.xml'))

# --- case 3: the fingerprint identity carries the source set, not 'main' --------------------------
$identities = Get-StoredIdentities 'ur'
Assert-Equal 'fingerprint recorded under the vr identity'   $true  ($identities -contains 'app_v2|vr|strings.xml|vr_hud_prev')
Assert-Equal 'and never under the main identity'            $false ($identities -contains 'app_v2|main|strings.xml|vr_hud_prev')

# --- case 4: refusals name the source set they searched -------------------------------------------
New-Sandbox
$wrongSet = Invoke-Tool @('-Locale', 'ur', '-Key', 'vr_hud_prev', '-Value', 'سابقہ')
Assert-Equal 'a key living in another set is refused'       $true  ($wrongSet.ExitCode -ne 0)
Assert-Equal 'the refusal names the set it searched'        $true  ($wrongSet.Flat -match "sourceset'main'")
Assert-Equal 'and names the set that really holds the key'  $true  ($wrongSet.Flat -match '-SourceSetvr')
Assert-Equal 'and does NOT advise -CreateIfMissing'         $false ($wrongSet.Flat -match 'Use-CreateIfMissingtoappendit')
Assert-Equal 'and nothing was written'                      $false ((Get-SandboxText 'app_v2/src/main/res/values-ur/strings.xml') -match 'vr_hud_prev')

$genuinelyAbsent = Invoke-Tool @('-SourceSet', 'vr', '-Locale', 'ur', '-Key', 'vr_hud_nowhere', '-Value', 'X')
Assert-Equal 'a key absent everywhere is refused'           $true ($genuinelyAbsent.ExitCode -ne 0)
Assert-Equal 'the refusal names the searched set'           $true ($genuinelyAbsent.Flat -match "sourceset'vr'")
Assert-Equal 'and keeps the -CreateIfMissing advice'        $true ($genuinelyAbsent.Flat -match 'Use-CreateIfMissingtoappendit')

# --- case 5: a bad source set is refused by name, not by a path the caller has to decode -----------
$badSet = Invoke-Tool @('-SourceSet', 'nosuchflavor', '-Action', 'get', '-Key', 'app_name')
Assert-Equal 'an unknown source set is refused'             $true ($badSet.ExitCode -ne 0)
Assert-Equal 'and the refusal names it'                     $true ($badSet.Flat -match "sourceset'nosuchflavor'")

Remove-Item -Recurse -Force $sandbox

Write-Host ("RESULT | pass: {0} | fail: {1}" -f $script:pass, $script:fail)
if ($script:fail -eq 0) { exit 0 } else { exit 1 }
