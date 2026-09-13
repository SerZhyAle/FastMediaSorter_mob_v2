# play-vitals-verdict.ps1 (S2917) - turn a Play vitals snapshot into coloured findings. Dot-source it.
#
# Pure: no file, network or process I/O. The input is the schema-1 snapshot printed by
# scripts/release/read-play-vitals.ps1 -Json (as parsed by ConvertFrom-Json), the PlayVitals and
# PlayMemory blocks of scripts/devtest/prerelease.config.psd1; the output is one object:
#
#   Verdict      - red | yellow | green | insufficient-data
#   Findings[]   - Key, Color, Metric, Scope, Value, Threshold, Window, VersionCodes, DistinctUsers, Detail
#   Window       - "YYYY-MM-DD..YYYY-MM-DD America/Los_Angeles"
#   VersionCodes - the versionCodes that carried users in the window
#
# The finding Key is what the filer deduplicates on (one open ticket per key), so it must stay
# stable across runs: it is built from the metric and the scope only, never from a value or a date.
#
# Colours: red / yellow / green are judged against the bands; insufficient-data means too few users
# for the value to mean anything; unit-unconfirmed and not-judged are recorded but never coloured.
# Only red is ever filed as a ticket - that decision belongs to the caller.

Set-StrictMode -Version Latest

function Get-VitalsProp {
    param($Object, [string] $Name)
    if ($null -eq $Object) { return $null }
    if ($Object -is [System.Collections.IDictionary]) {
        if ($Object.Contains($Name)) { return $Object[$Name] }
        return $null
    }
    $prop = $Object.PSObject.Properties[$Name]
    if ($null -eq $prop) { return $null }
    return $prop.Value
}

function Get-VitalsFreshestRow {
    param($Rows)
    return @($Rows | Where-Object { $null -ne $_ }) | Sort-Object -Property { [string](Get-VitalsProp $_ 'date') } |
        Select-Object -Last 1
}

function Get-VitalsMetric {
    param($Row, [string] $Metric)
    $value = Get-VitalsProp (Get-VitalsProp $Row 'metrics') $Metric
    if ($null -eq $value) { return $null }
    return [double] $value
}

function Assert-VitalsRateUnit {
    param([double] $Value, [string] $Metric, [string] $RateUnit)
    if ($RateUnit -eq 'fraction' -and $Value -gt 1) {
        throw "play-vitals: rate unit mismatch - $Metric = $Value cannot be a fraction; the API returns percentages, so set PlayVitals.RateUnit to 'percent' (S2917 research 6)."
    }
}

function ConvertTo-VitalsFraction {
    param([double] $Value, [string] $RateUnit)
    if ($RateUnit -eq 'percent') { return $Value / 100.0 }
    return $Value
}

function Get-VitalsColor {
    param([double] $Value, [double] $Red, [double] $YellowShare)
    if ($Value -ge $Red) { return 'red' }
    if ($Value -ge ($Red * $YellowShare)) { return 'yellow' }
    return 'green'
}

function New-VitalsFinding {
    param(
        [string] $Key, [string] $Color, [string] $Metric, [string] $Scope,
        $Value, $Threshold, [string] $Window, [string[]] $VersionCodes, $DistinctUsers, [string] $Detail = ''
    )
    return [pscustomobject] @{
        Key           = $Key
        Color         = $Color
        Metric        = $Metric
        Scope         = $Scope
        Value         = $Value
        Threshold     = $Threshold
        Window        = $Window
        VersionCodes  = @($VersionCodes)
        DistinctUsers = $DistinctUsers
        Detail        = $Detail
    }
}

function Get-VitalsMemoryLimitKb {
    param([hashtable] $Memory, [string] $SetKey, [double] $RamBucketMb, [string] $State)
    if ($SetKey -eq 'bitmapMemory') {
        $row = $Memory.BitmapMemoryKb
        if ($row.ContainsKey($State)) { return [double] $row[$State] }
        return $null
    }
    $bucketGb = [math]::Floor($RamBucketMb / 1024)
    $keys = @($Memory.AnonMemoryKb.Keys | ForEach-Object { [int] $_ } | Where-Object { $_ -le $bucketGb } | Sort-Object)
    if ($keys.Count -eq 0) { return $null }
    $grid = $Memory.AnonMemoryKb[[string] $keys[-1]]
    if ($grid.ContainsKey($State)) { return [double] $grid[$State] }
    return $null
}

function Get-PlayVitalsVerdict {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] $Snapshot,
        [Parameter(Mandatory)] [hashtable] $Bands,
        [Parameter(Mandatory)] [hashtable] $Memory
    )

    $rateUnit = [string] $Bands.RateUnit
    $share = [double] $Bands.YellowShareOfRed
    $spikeFactor = [double] $Bands.SpikeFactor
    $minUsers = [double] $Bands.MinDistinctUsers
    $minDeviceUsers = [double] $Bands.MinDistinctUsersPerDevice

    $win = Get-VitalsProp $Snapshot 'window'
    $window = '{0}..{1} {2}' -f (Get-VitalsProp $win 'startDate'), (Get-VitalsProp $win 'endDate'), (Get-VitalsProp $win 'timeZone')
    $sets = Get-VitalsProp $Snapshot 'sets'

    $versionCodes = @(
        @(Get-VitalsProp (Get-VitalsProp $sets 'crashRate') 'byVersionCode') |
            Where-Object { $null -ne $_ -and (Get-VitalsMetric $_ 'distinctUsers') -gt 0 } |
            ForEach-Object { [string] (Get-VitalsProp (Get-VitalsProp $_ 'dims') 'versionCode') } |
            Sort-Object -Unique
    )

    $findings = [System.Collections.Generic.List[object]]::new()

    foreach ($rate in @(@('crashRate', 'Crash', 'UserPerceivedCrashRate'), @('anrRate', 'Anr', 'UserPerceivedAnrRate'))) {
        $setKey, $stem, $bandKey = $rate
        $set = Get-VitalsProp $sets $setKey
        $metric28 = "userPerceived${stem}Rate28dUserWeighted"
        $metricDaily = "userPerceived${stem}Rate"
        $red = [double] $Bands.Red[$bandKey]

        $row = Get-VitalsFreshestRow -Rows (Get-VitalsProp $set 'overall')
        $users = Get-VitalsMetric $row 'distinctUsers'
        $value28 = Get-VitalsMetric $row $metric28
        if ($null -eq $row -or $null -eq $users -or $users -lt $minUsers -or $null -eq $value28) {
            $findings.Add((New-VitalsFinding -Key "play-vitals:${metricDaily}:overall" -Color 'insufficient-data' `
                -Metric $metric28 -Scope 'all devices' -Value $value28 -Threshold $red -Window $window `
                -VersionCodes $versionCodes -DistinctUsers $users `
                -Detail "fewer than $minUsers distinct users on the freshest day"))
        } else {
            Assert-VitalsRateUnit -Value $value28 -Metric $metric28 -RateUnit $rateUnit
            $fraction28 = ConvertTo-VitalsFraction -Value $value28 -RateUnit $rateUnit
            $color = Get-VitalsColor -Value $fraction28 -Red $red -YellowShare $share
            $findings.Add((New-VitalsFinding -Key "play-vitals:${metricDaily}:overall" -Color $color `
                -Metric $metric28 -Scope 'all devices' -Value $fraction28 -Threshold $red -Window $window `
                -VersionCodes $versionCodes -DistinctUsers $users -Detail "freshest day $(Get-VitalsProp $row 'date')"))

            $daily = Get-VitalsMetric $row $metricDaily
            if ($null -ne $daily) {
                Assert-VitalsRateUnit -Value $daily -Metric $metricDaily -RateUnit $rateUnit
                $dailyFraction = ConvertTo-VitalsFraction -Value $daily -RateUnit $rateUnit
                if ($dailyFraction -gt 0 -and $dailyFraction -gt ($fraction28 * $spikeFactor)) {
                    $spikeColor = if ($dailyFraction -ge $red) { 'red' } else { 'yellow' }
                    $findings.Add((New-VitalsFinding -Key "play-vitals:${metricDaily}:spike" -Color $spikeColor `
                        -Metric $metricDaily -Scope 'all devices, one day' -Value $dailyFraction -Threshold ($fraction28 * $spikeFactor) `
                        -Window $window -VersionCodes $versionCodes -DistinctUsers $users `
                        -Detail "daily value on $(Get-VitalsProp $row 'date') above $spikeFactor x the 28-day value"))
                }
            }
        }

        $byModel = @(@(Get-VitalsProp $set 'byDeviceModel') | Where-Object { $null -ne $_ } |
            Group-Object -Property { [string] (Get-VitalsProp (Get-VitalsProp $_ 'dims') 'deviceModel') })
        foreach ($group in $byModel) {
            $modelRow = Get-VitalsFreshestRow -Rows $group.Group
            $modelUsers = Get-VitalsMetric $modelRow 'distinctUsers'
            $modelValue = Get-VitalsMetric $modelRow $metric28
            if ($null -eq $modelUsers -or $modelUsers -lt $minDeviceUsers -or $null -eq $modelValue) { continue }
            Assert-VitalsRateUnit -Value $modelValue -Metric $metric28 -RateUnit $rateUnit
            $modelFraction = ConvertTo-VitalsFraction -Value $modelValue -RateUnit $rateUnit
            $perDevice = [double] $Bands.Red.PerDeviceModel
            if ($modelFraction -ge $perDevice) {
                $findings.Add((New-VitalsFinding -Key "play-vitals:${metricDaily}:device:$($group.Name)" -Color 'red' `
                    -Metric $metric28 -Scope "device model $($group.Name)" -Value $modelFraction -Threshold $perDevice `
                    -Window $window -VersionCodes $versionCodes -DistinctUsers $modelUsers -Detail 'single-device-model band'))
            }
        }
    }

    foreach ($anomaly in @(Get-VitalsProp $Snapshot 'anomalies')) {
        if ($null -eq $anomaly) { continue }
        $name = [string] (Get-VitalsProp $anomaly 'name')
        $id = ($name -split '/')[-1]
        $dims = Get-VitalsProp $anomaly 'dimensions'
        $dimText = if ($null -ne $dims) {
            (@($dims.PSObject.Properties | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join ', ')
        } else { '' }
        $scope = ('{0} {1}' -f (Get-VitalsProp $anomaly 'metricSet'), $dimText).Trim()
        $findings.Add((New-VitalsFinding -Key "play-vitals:anomaly:$id" -Color 'red' -Metric ([string] (Get-VitalsProp $anomaly 'metric')) `
            -Scope $scope -Value (Get-VitalsProp $anomaly 'value') -Threshold 'Google anomaly detection' -Window $window `
            -VersionCodes $versionCodes -DistinctUsers $null `
            -Detail ("active {0}..{1}" -f (Get-VitalsProp $anomaly 'startDate'), (Get-VitalsProp $anomaly 'endDate'))))
    }

    $memoryEnabled = [bool] $Bands.MemoryBandsEnabled
    $memoryUnit = [string] $Bands.MemoryUnit
    $divisor = @{ 'bytes' = 1024.0; 'kB' = 1.0; 'MB' = (1.0 / 1024.0) }
    if ($memoryEnabled -and -not $divisor.ContainsKey($memoryUnit)) {
        throw "play-vitals: MemoryBandsEnabled is on but PlayVitals.MemoryUnit is '$memoryUnit' - set 'bytes', 'kB' or 'MB' first (S2917 research 6)."
    }
    foreach ($mem in @(@('anonMemory', 'anonRssAndSwapMemoryUsageP90'), @('bitmapMemory', 'bitmapMemoryUsageP90'))) {
        $setKey, $metric = $mem
        # A set with no rows (too few users) must stay an array: under StrictMode a pipeline that emits
        # nothing yields $null, and $null.Count throws - the live API answered exactly that on 2026-09-13.
        $rows = @(@(Get-VitalsProp (Get-VitalsProp $sets $setKey) 'byRamBucketAppState') | Where-Object { $null -ne $_ })
        if (-not $memoryEnabled) {
            $values = @($rows | ForEach-Object { Get-VitalsMetric $_ $metric } | Where-Object { $null -ne $_ })
            $max = if ($values.Count -gt 0) { ($values | Measure-Object -Maximum).Maximum } else { $null }
            $findings.Add((New-VitalsFinding -Key "play-vitals:${metric}:memory" -Color 'unit-unconfirmed' -Metric $metric `
                -Scope "$($rows.Count) RAM bucket x app state row(s)" -Value $max -Threshold 'PlayMemory (not compared)' `
                -Window $window -VersionCodes $versionCodes -DistinctUsers $null `
                -Detail 'raw P90 maximum; the unit is not confirmed, so no colour is given (S2917 research 6)'))
            continue
        }
        foreach ($row in $rows) {
            $dims = Get-VitalsProp $row 'dims'
            $bucket = [double] (Get-VitalsProp $dims 'deviceRamBucket')
            $apiState = [string] (Get-VitalsProp $dims 'appState')
            $users = Get-VitalsMetric $row 'distinctUsers'
            $scope = "RAM bucket $bucket MB, $apiState"
            $key = "play-vitals:${metric}:ram-$([int] $bucket):$($apiState.ToLowerInvariant())"
            $state = if ($Bands.AppStateMap.ContainsKey($apiState)) { [string] $Bands.AppStateMap[$apiState] } else { $null }
            $limit = if ($state) { Get-VitalsMemoryLimitKb -Memory $Memory -SetKey $setKey -RamBucketMb $bucket -State $state } else { $null }
            $raw = Get-VitalsMetric $row $metric
            if ($null -eq $limit -or $null -eq $raw) {
                $findings.Add((New-VitalsFinding -Key $key -Color 'not-judged' -Metric $metric -Scope $scope -Value $raw -Threshold $null `
                    -Window $window -VersionCodes $versionCodes -DistinctUsers $users -Detail 'no Play limit for this RAM bucket and state'))
                continue
            }
            $kb = $raw / $divisor[$memoryUnit]
            $color = if ($null -ne $users -and $users -lt $minUsers) { 'insufficient-data' } else { Get-VitalsColor -Value $kb -Red $limit -YellowShare $share }
            $findings.Add((New-VitalsFinding -Key $key -Color $color -Metric $metric -Scope $scope -Value $kb -Threshold $limit `
                -Window $window -VersionCodes $versionCodes -DistinctUsers $users -Detail 'P90 in kB against PlayMemory'))
        }
    }

    $rank = @{ 'green' = 1; 'yellow' = 2; 'red' = 3 }
    $worst = 0
    foreach ($finding in $findings) {
        if ($rank.ContainsKey($finding.Color) -and $rank[$finding.Color] -gt $worst) { $worst = $rank[$finding.Color] }
    }
    $verdict = switch ($worst) { 3 { 'red' } 2 { 'yellow' } 1 { 'green' } default { 'insufficient-data' } }

    return [pscustomobject] @{
        Verdict      = $verdict
        Findings     = $findings.ToArray()
        Window       = $window
        VersionCodes = $versionCodes
    }
}
