# check-device-profile-presets.Tests.ps1 (S1216) - regression tests for the preset coverage gate.
#
# Proves the four rules the gate exists for, each in isolation:
#   - a registered non-presettable field absorbs a missing CSV row (no false "forgotten" report);
#   - a registered field that nevertheless carries a value is an error;
#   - a CSV row with neither an applier branch nor a registry entry is an error;
#   - a cell outside its Settings-screen range is an error.
#
# The gate resolves its inputs from `Split-Path $PSScriptRoot -Parent`, so each case copies the
# real script into a throwaway repo-shaped sandbox and feeds it four tiny fixtures. Nothing tracked
# is read or written; the sandbox lives under temp/S1216/ and is removed at the end.
# Exit 0 = all cases pass, 1 = at least one failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
$sut = Join-Path $repoRoot 'scripts/check_device_profile_presets.ps1'
$sandbox = Join-Path $repoRoot 'temp/S1216/preset-gate-tests'
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

function Assert-Match {
    param([Parameter(Mandatory)][string]$Name, [string]$Pattern, [string]$Text)
    if ($Text -match $Pattern) {
        Write-Host ("PASS | {0} | matched /{1}/" -f $Name, $Pattern)
        $script:pass++
    }
    else {
        Write-Host ("FAIL | {0} | no match for /{1}/ in: {2}" -f $Name, $Pattern, ($Text -replace '\s+', ' '))
        $script:fail++
    }
}

# Five fields, two profiles. `charlie` deliberately has no applier branch so a case can hand it a
# CSV row and trip the applier-gap rule without disturbing anything else.
$appSettingsSrc = @'
data class AppSettings(
    val alpha: Boolean = false,
    val bravo: Boolean = false,
    val charlie: Boolean = false,
    val defaultIconSize: Int = 96,
    val secretToken: String = "",
)
'@

$deviceProfileSrc = 'enum class DeviceProfileType { PHONE, TV }'

function New-Sandbox {
    param([string[]]$CsvLines, [string[]]$RegistryFields, [string[]]$Branches)

    if (Test-Path $sandbox) { Remove-Item -Recurse -Force $sandbox }
    $kt = Join-Path $sandbox 'app_v2/src/main/java/com/sza/fastmediasorter'
    foreach ($d in @(
            (Join-Path $sandbox 'scripts'),
            (Join-Path $sandbox 'docs/settings'),
            (Join-Path $sandbox 'app_v2/src/main/assets'),
            (Join-Path $kt 'domain/model'), (Join-Path $kt 'data/model'), (Join-Path $kt 'data/preset'))) {
        New-Item -ItemType Directory -Force $d | Out-Null
    }

    Set-Content -LiteralPath (Join-Path $kt 'domain/model/AppSettings.kt') -Value $appSettingsSrc
    Set-Content -LiteralPath (Join-Path $kt 'data/model/DeviceProfile.kt') -Value $deviceProfileSrc

    $branchLines = ($Branches | ForEach-Object { "        `"$_`" -> settings.copy($_ = raw.toBool())" }) -join "`n"
    $applierSrc = "fun applyOverride(settings: AppSettings, field: String, raw: String): AppSettings {`n" +
        "    return when (field) {`n$branchLines`n        else -> skip(field, raw, settings)`n    }`n}"
    Set-Content -LiteralPath (Join-Path $kt 'data/preset/DeviceProfilePresetApplier.kt') -Value $applierSrc

    Set-Content -LiteralPath (Join-Path $sandbox 'app_v2/src/main/assets/device_profile_presets.csv') -Value $CsvLines

    $entries = $RegistryFields | ForEach-Object { [pscustomobject]@{ field = $_; reason = 'test fixture' } }
    $json = [pscustomobject]@{ fields = @($entries) } | ConvertTo-Json -Depth 4
    Set-Content -LiteralPath (Join-Path $sandbox 'docs/settings/device-profile-nonpresettable.json') -Value $json

    $copied = Join-Path $sandbox 'scripts/check_device_profile_presets.ps1'
    Copy-Item -LiteralPath $sut -Destination $copied -Force
    return $copied
}

function Invoke-Gate {
    param([string[]]$CsvLines, [string[]]$RegistryFields, [string[]]$Branches)
    $script = New-Sandbox -CsvLines $CsvLines -RegistryFields $RegistryFields -Branches $Branches
    $out = & $pwshExe -NoProfile -File $script 2>&1 | Out-String
    return @{ Exit = $LASTEXITCODE; Out = $out }
}

$baselineCsv = @('"option","phone","tv"', '"alpha","true",""', '"defaultIconSize","96","152"')
$baselineRegistry = @('bravo', 'charlie', 'secretToken')
$baselineBranches = @('alpha', 'bravo', 'defaultIconSize')

# --- Case 1: the registry absorbs fields that have no CSV row --------------------------------
$r = Invoke-Gate -CsvLines $baselineCsv -RegistryFields $baselineRegistry -Branches $baselineBranches
Assert-Equal 'registry absorbs missing rows -> exit 0' 0 $r.Exit

# Same tree with `bravo` unregistered: the field is now genuinely forgotten.
$r = Invoke-Gate -CsvLines $baselineCsv -RegistryFields @('charlie', 'secretToken') -Branches $baselineBranches
Assert-Equal 'unregistered missing row -> exit 1' 1 $r.Exit
Assert-Match 'unregistered missing row names the field' 'MISSING from CSV rows.*bravo' $r.Out

# --- Case 2: a registered field carrying a value is an error ---------------------------------
$r = Invoke-Gate -CsvLines ($baselineCsv + '"bravo","true",""') -RegistryFields $baselineRegistry -Branches $baselineBranches
Assert-Equal 'registered field with a value -> exit 1' 1 $r.Exit
Assert-Match 'registered field with a value is named' 'CARRYING a value.*bravo' $r.Out

# --- Case 3: a CSV row with no applier branch and no registry entry is an error --------------
$r = Invoke-Gate -CsvLines ($baselineCsv + '"charlie","",""') -RegistryFields @('bravo', 'secretToken') -Branches $baselineBranches
Assert-Equal 'row without applier branch -> exit 1' 1 $r.Exit
Assert-Match 'row without applier branch is named' 'no applier branch.*charlie' $r.Out

# --- Case 4: a cell outside its Settings-screen range is an error ----------------------------
$offStep = @('"option","phone","tv"', '"alpha","true",""', '"defaultIconSize","96","100"')
$r = Invoke-Gate -CsvLines $offStep -RegistryFields $baselineRegistry -Branches $baselineBranches
Assert-Equal 'off-step defaultIconSize -> exit 1' 1 $r.Exit
Assert-Match 'off-step defaultIconSize is named' "defaultIconSize/tv='100'" $r.Out

# --- Case 5: the Other column must stay empty ------------------------------------------------
$withOther = @('"option","phone","tv","Other"', '"alpha","true","",""', '"defaultIconSize","96","152","128"')
$r = Invoke-Gate -CsvLines $withOther -RegistryFields $baselineRegistry -Branches $baselineBranches
Assert-Equal 'non-empty Other column -> exit 1' 1 $r.Exit
Assert-Match 'non-empty Other column is named' "'Other' column" $r.Out

if (Test-Path $sandbox) { Remove-Item -Recurse -Force $sandbox }

Write-Host ("RESULT | pass: {0} | fail: {1}" -f $script:pass, $script:fail)
if ($script:fail -eq 0) { exit 0 } else { exit 1 }
