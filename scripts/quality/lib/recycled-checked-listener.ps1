#requires -Version 7.0

# A bound holder retains its previous listener until bind removes it. Track each checkbox
# separately so detaching one view cannot excuse an assignment to its neighbor.
function Get-RecycledCheckedListenerLines([string]$Text) {
    if (-not $Text.Contains('setOnCheckedChangeListener')) { return @() }

    $methods = [regex]'\bfun\s+(?:onBindViewHolder|bind[A-Za-z0-9_]*)\s*\('
    $listenerCalls = [regex]'\b(?<view>(?:[A-Za-z_][A-Za-z0-9_]*\.)*[A-Za-z_][A-Za-z0-9_]*)\.setOnCheckedChangeListener\s*(?:\{|\()'
    $events = [regex]'\b(?<view>(?:[A-Za-z_][A-Za-z0-9_]*\.)*[A-Za-z_][A-Za-z0-9_]*)\.(?<action>setOnCheckedChangeListener\s*\(\s*null\s*\)|setOnCheckedChangeListener\s*(?:\{|\()|isChecked\s*=)'
    $lines = [System.Collections.Generic.HashSet[int]]::new()
    $listenedViews = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($call in $listenerCalls.Matches($Text)) {
        [void]$listenedViews.Add($call.Groups['view'].Value)
    }

    foreach ($method in $methods.Matches($Text)) {
        $open = $Text.IndexOf('{', $method.Index + $method.Length)
        if ($open -lt 0 -or $open - $method.Index -gt 500) { continue }
        $close = Find-MatchingBrace $Text $open
        if ($close -lt 0) { continue }

        $body = $Text.Substring($open + 1, $close - $open - 1)
        $detached = @{}
        foreach ($event in $events.Matches($body)) {
            $view = $event.Groups['view'].Value
            $action = $event.Groups['action'].Value
            if ($action -match '^setOnCheckedChangeListener\s*\(\s*null\s*\)$') {
                $detached[$view] = $true
            } elseif ($action -match '^setOnCheckedChangeListener') {
                $detached[$view] = $false
            } elseif ($listenedViews.Contains($view) -and -not $detached[$view]) {
                $index = $open + 1 + $event.Index
                [void]$lines.Add(($Text.Substring(0, $index) -split "`n").Count)
            }
        }
    }

    return @($lines | Sort-Object)
}

function Measure-RecycledCheckedListenerText([string]$Text) {
    return @(Get-RecycledCheckedListenerLines $Text).Count
}
