#requires -Version 7.0
<#
.SYNOPSIS
    S4337: matcher for loopback endpoint close() implementations that shut down the
    executor/listener without closing tracked active client/upstream connections.
    Also hosts Find-UnregisteredResultLauncherLines (S3886) to keep source-matchers.ps1
    below the 2000-line ceiling.
#>

function Find-UnregisteredResultLauncherLines([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return }
    if ($Text -notmatch 'activityResultRegistry') { return }
    if ($Text -match '\.unregister\s*\(') { return }
    # The second argument is the contract; the lifecycle overload puts its owner there instead.
    $rx = [regex]'\.register\s*\(\s*[^,()]+,\s*[\w.]*Contract'
    foreach ($m in $rx.Matches($Text)) {
        $Text.Substring(0, $m.Index).Split("`n").Count
    }
}

$script:LoopbackEndpointClassRx = [regex]'(?m)\bclass\s+\w+\s*[^{]*:\s*[^{]*\b(LoopbackEndpoint|WearExchangeRelayEndpoint)\b'
$script:OverrideCloseRx = [regex]'(?ms)\boverride\s+fun\s+close\s*\(\s*\)\s*\{'

function Find-LoopbackEndpointCloseLines([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return }
    if (-not $script:LoopbackEndpointClassRx.IsMatch($Text)) { return }
    if ($Text -notmatch '\bServerSocket\b') { return }
    foreach ($m in $script:OverrideCloseRx.Matches($Text)) {
        $braceStart = $m.Index + $m.Length - 1
        $depth = 0
        $endIdx = -1
        for ($i = $braceStart; $i -lt $Text.Length; $i++) {
            $ch = $Text[$i]
            if ($ch -eq '{') { $depth++ }
            elseif ($ch -eq '}') {
                $depth--
                if ($depth -eq 0) {
                    $endIdx = $i
                    break
                }
            }
        }
        if ($endIdx -lt 0) { continue }
        $body = $Text.Substring($braceStart, $endIdx - $braceStart + 1)
        # A ServerSocket-backed LoopbackEndpoint/WearExchangeRelayEndpoint close() must close
        # tracked active connections (via closeAndClearActiveConnections or a snapshot/forEach close)
        # rather than only closing the listening ServerSocket and calling shutdownNow().
        $closesActive = ($body -match 'closeAndClearActiveConnections\s*\(') -or
            ($body -match 'activeConnections') -or
            ($body -match '\.forEach\s*\{[^}]*\.close\s*\(')
        if (-not $closesActive) {
            $Text.Substring(0, $m.Index).Split("`n").Count
        }
    }
}

function Measure-LoopbackEndpointCloseText([string]$Text) {
    return @(Find-LoopbackEndpointCloseLines $Text).Count
}
