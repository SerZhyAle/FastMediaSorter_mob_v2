#requires -Version 7.0
<#
.SYNOPSIS
    Counting core of the listener-symmetry gate (S1559).

.DESCRIPTION
    Dot-sourced by scripts/quality/assert-listener-symmetry.ps1 and by its regression suite. It exists
    as a library because the gate itself cannot be loaded for testing - it runs a repository scan on
    load - and because the delta mode's only entry point needs git plus a path inside the repository,
    so a sandbox fixture can never reach it. Everything that decides a number lives here; the gate
    keeps scope, baseline and reporting.

    Categories counted, each as add-versus-remove:
      registerContentObserver / unregisterContentObserver
      registerReceiver        / unregisterReceiver
      add*Listener            / remove*Listener
      add*Callback            / remove*Callback
      add*Observer            / remove*Observer

    Discounts - forms that register nothing, or register something the framework releases on its own:
      registerReceiver(null, ..)              a query for a sticky broadcast, not a registration
      onBackPressedDispatcher.addCallback(..) dropped by the lifecycle it is handed (S1433)
      lifecycle.addObserver(..)               dropped by the lifecycle it is handed (S1559)
      addEventListener(..)                    a DOM call inside an HTML string literal (S1559)
      any `import` line                       an import is not a call at all (S1559)
      any whole-line comment                  `//`, `/*` and KDoc `*` lines name calls, they do not
                                              make them (S1815). Only a line whose FIRST non-space
                                              characters are a comment marker is dropped, so a code
                                              line carrying a `"http://.."` literal keeps its call.

    A discount only ever SHRINKS an imbalance in the leak direction. Subtracting it from the add count
    and then taking the absolute difference does the opposite on a file that pairs its call: the add
    falls to zero, the remove is still counted, and a balanced file reports an imbalance of one.
    Measured on LauncherStatusStripManager.kt, the file S1501 had just fixed.
#>

$regContentObserver = [regex]'\bregisterContentObserver\b'
$unregContentObserver = [regex]'\bunregisterContentObserver\b'

$regReceiver = [regex]'\bregisterReceiver\b'
$unregReceiver = [regex]'\bunregisterReceiver\b'
$regReceiverNull = [regex]'\bregisterReceiver\s*\(\s*null\s*,'

$addListener = [regex]'\badd[A-Za-z0-9_]*Listener\b'
$removeListener = [regex]'\bremove[A-Za-z0-9_]*Listener\b'
$addListenerDom = [regex]'\baddEventListener\b'

$addCallback = [regex]'\badd[A-Za-z0-9_]*Callback\b'
$removeCallback = [regex]'\bremove[A-Za-z0-9_]*Callback\b'
$addCallbackLifecycle = [regex]'\bonBackPressedDispatcher\s*(?:\r?\n\s*)?\.\s*addCallback\b'

$addObserver = [regex]'\badd[A-Za-z0-9_]*Observer\b'
$removeObserver = [regex]'\bremove[A-Za-z0-9_]*Observer\b'
$addObserverLifecycle = [regex]'\blifecycle\s*(?:\r?\n\s*)?\.\s*addObserver\b'

function Remove-CommentLines([string]$text) {
    if ([string]::IsNullOrEmpty($text)) { return $text }
    return (($text -split "`n") | Where-Object {
        $t = $_.TrimStart()
        -not ($t.StartsWith('//') -or $t.StartsWith('/*') -or $t.StartsWith('*'))
    }) -join "`n"
}

function Remove-ImportLines([string]$text) {
    if ([string]::IsNullOrEmpty($text)) { return $text }
    return (($text -split "`n") | Where-Object { $_ -notmatch '^\s*import\s' }) -join "`n"
}

function Get-CategoryImbalance([int]$add, [int]$remove, [int]$discount) {
    $raw = $add - $remove
    if ($raw -le 0) { return [Math]::Abs($raw) }
    return [Math]::Max(0, $raw - $discount)
}

function Get-FileImbalance([string]$text) {
    if ([string]::IsNullOrEmpty($text)) { return 0 }
    $text = Remove-CommentLines (Remove-ImportLines $text)
    $dObs = Get-CategoryImbalance $regContentObserver.Matches($text).Count $unregContentObserver.Matches($text).Count 0
    $dRec = Get-CategoryImbalance $regReceiver.Matches($text).Count $unregReceiver.Matches($text).Count $regReceiverNull.Matches($text).Count
    $dList = Get-CategoryImbalance $addListener.Matches($text).Count $removeListener.Matches($text).Count $addListenerDom.Matches($text).Count
    $dCb = Get-CategoryImbalance $addCallback.Matches($text).Count $removeCallback.Matches($text).Count $addCallbackLifecycle.Matches($text).Count
    $dObs2 = Get-CategoryImbalance $addObserver.Matches($text).Count $removeObserver.Matches($text).Count $addObserverLifecycle.Matches($text).Count
    return $dObs + $dRec + $dList + $dCb + $dObs2
}

# The detail line must explain the imbalance printed beside it, so it counts the same text and shows
# add counts net of their discounts. Printing raw counts gave "Callback: 2 vs 0" next to
# "imbalance: 1", which reads as a broken gate rather than as a discounted call.
function Get-FileImbalanceDetail([string]$text) {
    if ([string]::IsNullOrEmpty($text)) { return '' }
    $text = Remove-CommentLines (Remove-ImportLines $text)
    $parts = [System.Collections.Generic.List[string]]::new()

    $a = $regContentObserver.Matches($text).Count; $b = $unregContentObserver.Matches($text).Count
    if ($a -ne $b) { $parts.Add("ContentObserver: $a vs $b") }
    $a = $regReceiver.Matches($text).Count - $regReceiverNull.Matches($text).Count; $b = $unregReceiver.Matches($text).Count
    if ($a -ne $b) { $parts.Add("Receiver: $a vs $b") }
    $a = $addListener.Matches($text).Count - $addListenerDom.Matches($text).Count; $b = $removeListener.Matches($text).Count
    if ($a -ne $b) { $parts.Add("Listener: $a vs $b") }
    $a = $addCallback.Matches($text).Count - $addCallbackLifecycle.Matches($text).Count; $b = $removeCallback.Matches($text).Count
    if ($a -ne $b) { $parts.Add("Callback: $a vs $b") }
    $a = $addObserver.Matches($text).Count - $addObserverLifecycle.Matches($text).Count; $b = $removeObserver.Matches($text).Count
    if ($a -ne $b) { $parts.Add("Observer: $a vs $b") }

    return ($parts -join ', ')
}
