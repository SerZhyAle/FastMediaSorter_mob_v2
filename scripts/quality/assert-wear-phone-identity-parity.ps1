#requires -Version 7.0
<#
.SYNOPSIS
  Asserts the phone and wear modules share one applicationId and one signing key (S3364).

.DESCRIPTION
  WO-G7 check: the phone module and the wear module must share one application id
  and one signing key, or Play refuses the pair (Wear OS quality page, verified
  2026-09-21 - docs/STORE_PREPUBLISH_TOOLS_AND_REQUIREMENTS.md section 2).

  Reads the identity straight from the build configuration of both modules, never
  from a build: applicationId from each build.gradle.kts, and the release signing
  inputs (store file + key alias) from the keystore properties file each build file
  resolves via findRootSecretFile(...). No gradle run, no device needed.

  The *Override parameters replace the parsed values so the mismatch path is
  testable without editing real build files.

  Exit codes:
    0 - parity holds and every comparison ran (application id and release signing).
    1 - at least one comparison mismatches; output names both values of the pair. A mismatch that
        ran outranks a comparison that could not (CHECK-VERDICT rule 2.14 precedence).
    2 - could not verify (CHECK-VERDICT 0.12 item A.2): an override parameter passed empty, a module
        build config is missing, or a comparison's input is not on this tree - the keystore
        properties are secrets kept outside the repository, so a checkout without them has not
        proved the phone and wear pair shares one key. There is no opt-in flag: the only caller is
        the release-scope runner, and a release needs the signing inputs.

  The last stdout line is the verdict: `assert-wear-phone-identity-parity: PASS (..)`, `: FAIL (..)`
  or `: COULD NOT VERIFY (..)`.

  -Gate is accepted for uniformity with the other scripts/quality/assert-*.ps1 gates
  and is a no-op here: batch runners invoke every gate with -Gate, and a
  parameter-binding error must not read as a FAIL of the thing being audited.
#>
[CmdletBinding()]
param(
    [string] $PhoneApplicationIdOverride,
    [string] $WearApplicationIdOverride,
    [string] $PhoneStoreFileOverride,
    [string] $WearStoreFileOverride,
    [switch] $Gate
)

$ErrorActionPreference = 'Stop'

function Write-FailLine([string] $m) { Write-Error $m -ErrorAction Continue }

# scripts/quality -> scripts -> repo root
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

foreach ($name in @('PhoneApplicationIdOverride', 'WearApplicationIdOverride', 'PhoneStoreFileOverride', 'WearStoreFileOverride')) {
    if ($PSBoundParameters.ContainsKey($name) -and [string]::IsNullOrWhiteSpace($PSBoundParameters[$name])) {
        Write-FailLine "[identity] Usage error: -$name was passed empty."
        Write-Host "assert-wear-phone-identity-parity: COULD NOT VERIFY (-$name was passed empty)"
        exit 2
    }
}

function Get-ModuleIdentity {
    param([string] $Label, [string] $GradlePath)

    if (-not (Test-Path $GradlePath)) {
        Write-Host "[identity] $Label build config not found at $GradlePath - identity unresolvable."
        return $null
    }
    $text = Get-Content -Path $GradlePath -Raw

    # First `applicationId = "..."` is the base id; applicationIdSuffix lines never match this shape.
    $appId = $null
    if ($text -match 'applicationId\s*=\s*"([^"]+)"') { $appId = $Matches[1] }

    # Release signing inputs: the build file resolves its keystore properties via
    # findRootSecretFile("<primary>", "<fallback>") and reads keyAlias/storeFile from it.
    $storeFile = $null
    $keyAlias = $null
    if ($text -match 'findRootSecretFile\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)') {
        foreach ($rel in @($Matches[1], $Matches[2])) {
            $propsPath = Join-Path $repoRoot $rel
            if (-not (Test-Path $propsPath)) { continue }
            $props = @{}
            Get-Content -Path $propsPath | ForEach-Object {
                if ($_ -match '^\s*([A-Za-z0-9_.-]+)\s*=\s*(.+?)\s*$') { $props[$Matches[1]] = $Matches[2] }
            }
            if ($props.ContainsKey('storeFile') -and $props.ContainsKey('keyAlias')) {
                # Java .properties escaping: '\\' -> '\', '\:' -> ':'
                $raw = $props['storeFile'] -replace '\\\\', '\' -replace '\\:', ':'
                if (-not [System.IO.Path]::IsPathRooted($raw)) {
                    $raw = Join-Path (Split-Path -Parent $propsPath) $raw
                }
                $storeFile = [System.IO.Path]::GetFullPath($raw)
                $keyAlias = $props['keyAlias']
                break
            }
        }
    }

    return [pscustomobject]@{ ApplicationId = $appId; StoreFile = $storeFile; KeyAlias = $keyAlias }
}

$phone = Get-ModuleIdentity -Label 'phone (app_v2)' -GradlePath (Join-Path $repoRoot 'app_v2/build.gradle.kts')
$wear = Get-ModuleIdentity -Label 'wear' -GradlePath (Join-Path $repoRoot 'wear/build.gradle.kts')
if (-not $phone -or -not $wear) {
    Write-Host 'assert-wear-phone-identity-parity: COULD NOT VERIFY (a module build config was not found)'
    exit 2
}

$failures = 0
$skippedComparisons = [System.Collections.Generic.List[string]]::new()

# --- Application id comparison (WO-G7) --------------------------------------
$phoneAppId = if ($PhoneApplicationIdOverride) { $PhoneApplicationIdOverride } else { $phone.ApplicationId }
$wearAppId = if ($WearApplicationIdOverride) { $WearApplicationIdOverride } else { $wear.ApplicationId }
if (-not $phoneAppId -or -not $wearAppId) {
    Write-Host "[identity] applicationId unresolvable (phone='$phoneAppId', wear='$wearAppId') - comparison skipped."
    $skippedComparisons.Add('applicationId')
}
else {
    Write-Host "[identity] applicationId pair: phone='$phoneAppId' vs wear='$wearAppId'"
    if ($phoneAppId -ne $wearAppId) {
        $failures++
        Write-FailLine "[identity] FAIL applicationId mismatch: phone='$phoneAppId' vs wear='$wearAppId' - WO-G7 requires the same package id for both stores."
    }
}

# --- Release signing comparison (WO-G7) -------------------------------------
$phoneStore = if ($PhoneStoreFileOverride) { $PhoneStoreFileOverride } else { $phone.StoreFile }
$wearStore = if ($WearStoreFileOverride) { $WearStoreFileOverride } else { $wear.StoreFile }
if (-not $phoneStore -or -not $wearStore) {
    Write-Host '[identity] release signing unresolvable (keystore properties missing or incomplete) - signing comparison skipped.'
    $skippedComparisons.Add('release signing (keystore properties not on this tree)')
}
else {
    Write-Host "[identity] signing pair: phone storeFile='$phoneStore' (alias '$($phone.KeyAlias)') vs wear storeFile='$wearStore' (alias '$($wear.KeyAlias)')"
    if ($phoneStore -ne $wearStore) {
        $failures++
        Write-FailLine "[identity] FAIL signing storeFile mismatch: phone='$phoneStore' vs wear='$wearStore' - WO-G7 requires one signing key."
    }
    if ($phone.KeyAlias -ne $wear.KeyAlias) {
        $failures++
        Write-FailLine "[identity] FAIL signing keyAlias mismatch: phone='$($phone.KeyAlias)' vs wear='$($wear.KeyAlias)' - WO-G7 requires one signing key."
    }
}

if ($failures -gt 0) {
    Write-FailLine "[identity] $failures WO-G7 identity violation(s) - Play would refuse the phone/wear pair."
    Write-Host "assert-wear-phone-identity-parity: FAIL ($failures WO-G7 identity violation(s))"
    exit 1
}

if ($skippedComparisons.Count -gt 0) {
    # A comparison whose input is absent was requested and did not run (CHECK-VERDICT 0.12 item A.2):
    # the keystore properties are the declared input of the signing half, so a PASS here would say
    # the pair shares one key when nobody looked.
    $notCompared = $skippedComparisons -join ', '
    Write-FailLine "[identity] not compared: $notCompared - the pair's identity is unproven."
    Write-Host "assert-wear-phone-identity-parity: COULD NOT VERIFY (not compared: $notCompared)"
    exit 2
}

Write-Host 'assert-wear-phone-identity-parity: PASS (phone and wear share one application id and one signing key, WO-G7)'
exit 0
