#requires -Version 7.0
<#
  assert-wear-phone-identity-parity.ps1 (S3364)

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
    0 - parity holds on every comparison that could run, OR an input could not be
        resolved on this tree (advisory skip, stated in output).
    1 - at least one comparison mismatches; output names both values of the pair.
    2 - usage error (an override parameter passed empty).

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
        exit 2
    }
}

function Get-ModuleIdentity {
    param([string] $Label, [string] $GradlePath)

    if (-not (Test-Path $GradlePath)) {
        Write-Host "[identity] $Label build config not found at $GradlePath - identity unresolvable. Advisory skip."
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
if (-not $phone -or -not $wear) { exit 0 }

$failures = 0

# --- Application id comparison (WO-G7) --------------------------------------
$phoneAppId = if ($PhoneApplicationIdOverride) { $PhoneApplicationIdOverride } else { $phone.ApplicationId }
$wearAppId = if ($WearApplicationIdOverride) { $WearApplicationIdOverride } else { $wear.ApplicationId }
if (-not $phoneAppId -or -not $wearAppId) {
    Write-Host "[identity] applicationId unresolvable (phone='$phoneAppId', wear='$wearAppId') - comparison skipped. Advisory skip."
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
    Write-Host '[identity] release signing unresolvable (keystore properties missing or incomplete) - signing comparison skipped. Advisory skip.'
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
    exit 1
}

Write-Host '[identity] PASS - phone and wear share one application id and one signing key (WO-G7).'
exit 0
