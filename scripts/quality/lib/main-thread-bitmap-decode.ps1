#requires -Version 7.0

# Dot-sourced by source-matchers.ps1, so these predicates share its script scope and its
# Find-MatchingBrace helper. Split out to keep that file under the 2000-line script ceiling.

# S3988: a bitmap decode written inside a Compose effect or remembered-value body runs on the main
# thread - `remember` and `derivedStateOf` run inside composition, and `LaunchedEffect`/`produceState`
# start on the composition's Main dispatcher. The watch wallpaper decoded a full-resolution frame this
# way on first composition of every screen (S3700, slice 140). A nested `withContext(..) { }` inside
# the body moves the decode off Main and is not a hit. Lexical by necessity: a decode reached through
# a helper called from the body is invisible here, so the ratchet stops the written-inline shape only.
$script:ComposeMainScopeRx = [regex]'\b(?:remember|rememberSaveable|derivedStateOf|LaunchedEffect|produceState)\b[^{}\r\n]*\{'
$script:BitmapDecodeCallRx = [regex]'\bBitmapFactory\.decode\w*\s*\(|\bImageDecoder\.decodeBitmap\s*\('
$script:WithContextBlockRx = [regex]'\bwithContext\s*\([^{}\r\n]*\)\s*\{'

function Find-MainThreadBitmapDecodeLines([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return @() }
    if (-not $script:BitmapDecodeCallRx.IsMatch($Text)) { return @() }
    $decodes = @($script:BitmapDecodeCallRx.Matches($Text))
    # A set, because a decode inside `remember` nested in `LaunchedEffect` is reached from both scopes.
    $hitIndexes = [System.Collections.Generic.SortedSet[int]]::new()
    foreach ($scope in $script:ComposeMainScopeRx.Matches($Text)) {
        $open = $scope.Index + $scope.Length - 1
        $close = Find-MatchingBrace $Text $open
        if ($close -lt 0) { continue }
        $offMain = [System.Collections.Generic.List[int[]]]::new()
        foreach ($wc in $script:WithContextBlockRx.Matches($Text, $open)) {
            if ($wc.Index -ge $close) { break }
            $wcOpen = $wc.Index + $wc.Length - 1
            $wcClose = Find-MatchingBrace $Text $wcOpen
            if ($wcClose -ge 0) { $offMain.Add([int[]]@($wcOpen, $wcClose)) }
        }
        foreach ($d in $decodes) {
            if ($d.Index -le $open -or $d.Index -ge $close) { continue }
            $shielded = $false
            foreach ($range in $offMain) {
                if ($d.Index -gt $range[0] -and $d.Index -lt $range[1]) { $shielded = $true; break }
            }
            if (-not $shielded) { [void]$hitIndexes.Add($d.Index) }
        }
    }
    foreach ($idx in $hitIndexes) {
        $Text.Substring(0, $idx).Split("`n").Count
    }
}

function Measure-MainThreadBitmapDecodeText([string]$Text) {
    return @(Find-MainThreadBitmapDecodeLines $Text).Count
}
