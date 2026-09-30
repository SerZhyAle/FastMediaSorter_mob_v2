#requires -Version 7.0

# S3985: extracted verbatim from source-matchers.ps1 to keep it under the 2000-line script ceiling.
# Dot-sourced by source-matchers.ps1, so these predicates share its script scope.

# S3816: a whole AppSettings snapshot read with `val x = <repo>.getSettings().first()` and written back as
# `updateSettings(x.copy(..))` bypasses SettingsRepository.updateSettings(transform), the one overload that
# holds a mutex across read + write. Between the two calls a concurrent writer can commit another field,
# and the stale snapshot then overwrites it. The write must be looked for below the read, not on the next
# line: callers routinely compute a value between the two (S0613 caches, derived flags).
$script:SettingsSnapshotReadRx = [regex]'(?m)^[ \t]*val\s+(\w+)\s*=\s*[\w.]+\.getSettings\(\)\.first\(\)'
$script:SettingsRmwWindowLines = 25

function Find-SettingsReadModifyWriteLines([string]$Text) {
    if ([string]::IsNullOrEmpty($Text) -or -not $Text.Contains('getSettings().first()')) { return @() }
    $hits = @()
    foreach ($m in $script:SettingsSnapshotReadRx.Matches($Text)) {
        $after = $Text.Substring($m.Index + $m.Length)
        $window = (($after -split "`r?`n") | Select-Object -First $script:SettingsRmwWindowLines) -join "`n"
        $writeRx = 'updateSettings\(\s*' + [regex]::Escape($m.Groups[1].Value) + '\.copy\('
        if ($window -match $writeRx) {
            $hits += ($Text.Substring(0, $m.Index) -split "`n").Count
        }
    }
    return $hits
}

function Measure-SettingsReadModifyWriteText([string]$Text) {
    return @(Find-SettingsReadModifyWriteLines $Text).Count
}

# S3819: the view-side twin of the rule above. A settings screen that passes a whole AppSettings built from
# the snapshot it is rendering - `updateSettings(current.copy(..))`, `updateSettings(vm.settings.value..)` -
# writes back every field it did not touch, so a field another component committed after the render is rolled
# back. The one legitimate whole-object write (a settings import) passes a plain name, never `.copy(` or
# `settings.value`, so the shape alone separates the two.
$script:SettingsSnapshotWriteRx = [regex]'updateSettings\(\s*(?:[A-Za-z_][\w.]*\.copy\(|[\w.]*settings\.value\b)'

function Find-SettingsSnapshotWriteLines([string]$Text) {
    if ([string]::IsNullOrEmpty($Text) -or -not $Text.Contains('updateSettings(')) { return @() }
    $hits = @()
    foreach ($m in $script:SettingsSnapshotWriteRx.Matches($Text)) {
        $hits += ($Text.Substring(0, $m.Index) -split "`n").Count
    }
    return $hits
}

function Measure-SettingsSnapshotWriteText([string]$Text) {
    return @(Find-SettingsSnapshotWriteLines $Text).Count
}
