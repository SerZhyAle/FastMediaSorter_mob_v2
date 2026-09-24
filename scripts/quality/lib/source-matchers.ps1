#requires -Version 7.0
<#
.SYNOPSIS
    S1338: the one definition of every lexical source rule in the neuroslop family.

.DESCRIPTION
    Each rule used to live in its own script, which owned BOTH the full-project scan and the
    changed-files delta - two copies of the same predicate, kept in step by hand. This file
    holds the predicate once. `assert-source-gates.ps1` runs them all over a single walk
    (lib/source-scan.ps1), and each historical `assert-<rule>.ps1` delegates here, so the
    full scan and the delta can no longer disagree about what a violation is.

    A rule is:
      Name         gate name in output and in -Only
      Extensions   which files it reads
      Roots        repo-relative walk roots for the full scan
      PathFilter   regex the repo-relative path must match (tighter than the extension)
      Baseline     the committed integer baseline file, ratcheted DOWN only
      CountInText  param([string]$text) -> [int], the single definition of the violation
      FailMessage  what the operator should do about it

    Dot-source it:  . (Join-Path $PSScriptRoot 'lib/source-matchers.ps1')
                    $rules = Get-SourceRules
#>

. (Join-Path $PSScriptRoot 'source-scan.ps1')

# --- rule predicates -------------------------------------------------------------------
# Kept as named functions rather than inline lambdas so the two multi-step heuristics
# (trivial comments, unsafe collect) read the same way they did in their own scripts.

$script:TrivialVerbs = 'Get|Set|Initialize|Init|Create|Update|Check|Handle|Setup|Set up|Show|Hide|Load|Save|Return|Add|Remove|Clear|Start|Stop|Reset|Apply|Configure|Build|Bind|Observe|Enable|Disable|Register|Unregister|Notify|Refresh|Toggle|Cancel'
$script:TrivialRx = [regex]"^\s*//\s*($script:TrivialVerbs)\b"
# A comment carrying an explanatory connective says WHY and is kept, even when it opens
# with a verb. A digit or colon names a specific value, version or id - also kept.
$script:TrivialConnectiveRx = [regex]'(?i)\b(to|so|for|because|since|while|when|if|via|using|avoid|prevent|ensure|keep|otherwise|limit|note|already|only|first|before|after|null|stale|crash|leak|race|workaround|hack|fallback|instead|due|unless|until|safe|deprecated)\b'
$script:TrivialMaxWords = 4

function Test-TrivialCommentLine([string]$line) {
    if ($line -match '//\s*(noinspection|TODO|FIXME)') { return $false }
    if ($line -match '//\s*https?:') { return $false }
    if (-not $script:TrivialRx.IsMatch($line)) { return $false }
    $body = ($line -replace '^\s*//\s*', '').Trim()
    if ($script:TrivialConnectiveRx.IsMatch($body)) { return $false }
    if ($body -match '[\d:]') { return $false }
    $wordCount = @($body -split '\s+' | Where-Object { $_ -ne '' }).Count
    if ($wordCount -gt $script:TrivialMaxWords) { return $false }
    return $true
}

$script:LaunchRx = [regex]'lifecycleScope\.launch\s*\{'
$script:CollectRx = [regex]'\.collect\b'
$script:RepeatOnLifecycleRx = [regex]'repeatOnLifecycle\s*\([^)]*\)\s*\{'
$script:FlowWithLifecycleRx = [regex]'\.flowWithLifecycle\s*\('

$script:MutableFlowRx = [regex]'Mutable(StateFlow|LiveData|SharedFlow)\b'

# A hit is a val/var declaration naming a Mutable* reactive type without a `private` modifier.
# Line-oriented rather than regex-over-whole-text, because the modifier and the type sit on
# the same declaration line and a whole-text match would pair them across declarations.
function Measure-PublicMutableFlowText([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return 0 }
    $count = 0
    foreach ($line in ($Text -split "`n")) {
        if ($line -match '\b(val|var)\b' -and $script:MutableFlowRx.IsMatch($line) -and $line -notmatch '\bprivate\b') {
            $count++
        }
    }
    return $count
}

# Brace-match the launch body rather than bounding a regex, so an operator chain carrying
# its own lambda braces (`.filter { .. }.collect`) cannot evade detection. Braces inside
# comments and strings are a known and accepted approximation.
function Find-MatchingBrace([string]$text, [int]$openBrace) {
    $depth = 0
    for ($i = $openBrace; $i -lt $text.Length; $i++) {
        $c = $text[$i]
        if ($c -eq '{') { $depth++ }
        elseif ($c -eq '}') {
            $depth--
            if ($depth -eq 0) { return $i }
        }
    }
    return -1
}

function Test-CollectIsLifecycleAware([string]$body, [int]$collectIndex) {
    foreach ($repeatMatch in $script:RepeatOnLifecycleRx.Matches($body)) {
        $openBrace = $repeatMatch.Index + $repeatMatch.Length - 1
        $end = Find-MatchingBrace $body $openBrace
        if ($end -ge 0 -and $collectIndex -gt $openBrace -and $collectIndex -lt $end) {
            return $true
        }
    }
    $statementStart = [Math]::Max($body.LastIndexOf("`n", $collectIndex), $body.LastIndexOf(';', $collectIndex))
    $prefix = $body.Substring($statementStart + 1, $collectIndex - $statementStart - 1)
    return $script:FlowWithLifecycleRx.IsMatch($prefix)
}

function Test-UnsafeLaunchBody([string]$text, [int]$openBrace) {
    $end = Find-MatchingBrace $text $openBrace
    if ($end -lt 0) { return $false }
    $body = $text.Substring($openBrace + 1, $end - $openBrace - 1)
    foreach ($collectMatch in $script:CollectRx.Matches($body)) {
        if (-not (Test-CollectIsLifecycleAware $body $collectMatch.Index)) { return $true }
    }
    return $false
}

# S2326: a literal Windows drive path binds a script to one machine's disk layout, so moving the
# tree to another drive letter or directory name silently breaks it. Two project roots declared
# `c:\GIT\FastMediaSorter_mob_v2` while the tree lived on `P:` and nobody noticed, because nothing
# looked. Resolve through scripts/utils/project-paths.ps1 instead.
#
# The lookbehind spares three shapes that are not drives:
#   - a word character, so a URL scheme (`https://`) and `$env:LOCALAPPDATA\..` do not read as one;
#   - a dot, so `foo.C:/bar` does not;
#   - a BACKSLASH, which is the one that actually bit. A regex character class written
#     `[\s:\-|]` puts a letter, a colon and a separator side by side, and two live gate scripts
#     carry exactly that - they were reported as hardcoded paths by the first draft of this rule.
# The character after the separator excludes `-` for the same reason: a real path segment does not
# begin with a hyphen, but the escaped `\-` inside a character class does.
$script:DrivePathRx = [regex]'(?<![\w$.\\])[A-Za-z]:[\\/][\w.$]'

<#
.SYNOPSIS
    Code lines of a script, with comment text blanked out but line numbers preserved.
.DESCRIPTION
    A comment cannot bind a path, so it cannot make a script non-portable - and judging comments
    would force deleting legitimate prose, such as clean-user-temp.ps1 naming `C:\Windows\Temp` as
    an example of a directory it REFUSES to touch. Rewriting that to satisfy a gate makes the
    document worse while changing no behaviour.

    Only a whole-line comment is blanked, never a trailing one: `$dst = "d:\out"  # sink` must stay
    judged, and dropping everything after the first `#` would also drop a literal that a string on
    the same line legitimately contains.
#>
function Get-DrivePathCodeLines([string]$Text) {
    $lines = $Text -split "`r?`n"
    $out = New-Object 'string[]' $lines.Count
    $inBlock = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($inBlock) {
            if ($line -match '#>') { $inBlock = $false; $line = $line -replace '^.*?#>', '' }
            else { $out[$i] = ''; continue }
        }
        if ($line -match '<#') {
            if ($line -match '<#.*?#>') { $line = $line -replace '<#.*?#>', '' }
            else { $inBlock = $true; $line = $line -replace '<#.*$', '' }
        }
        if ($line -match '^\s*#') { $line = '' }
        $out[$i] = $line
    }
    return $out
}

function Measure-HardcodedDrivePathText([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return 0 }
    $count = 0
    foreach ($line in (Get-DrivePathCodeLines $Text)) {
        if ($line) { $count += $script:DrivePathRx.Matches($line).Count }
    }
    return $count
}

# Line numbers of the offending literals, so -List and the failure output name the site.
function Find-HardcodedDrivePathLines([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return @() }
    $codeLines = Get-DrivePathCodeLines $Text
    $hits = @()
    for ($i = 0; $i -lt $codeLines.Count; $i++) {
        if ($codeLines[$i] -and $script:DrivePathRx.IsMatch($codeLines[$i])) { $hits += ($i + 1) }
    }
    return $hits
}

# S2332: the three calls a builder stops needing the moment it delegates delivery. Judged rather than
# the whole block's shape because a hand-written copy is recognisable by what it reaches for, not by
# how it is worded - the 26 occurrences this rule was written against carried six different textual
# forms of one behaviour, and a shape-matching rule would have missed the three worded differently.
#
# Only the two DELIVERY sinks. Other sinks stay unjudged: build-with-version.ps1 legitimately resolves
# `Kind Apk` for a distribution folder that is not part of this block, and flagging it would push a
# correct caller into an exemption list, which is how exemption lists start.
$script:InlineDeliveryRx =
    [regex]'Get-ArtifactSink\s+-Kind\s+(Drive|Commander)\b|Get-ToolPath\s+-Tool\s+SevenZip\b'

function Measure-InlineDeliveryBlockText([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return 0 }
    $count = 0
    foreach ($line in (Get-DrivePathCodeLines $Text)) {
        if ($line) { $count += $script:InlineDeliveryRx.Matches($line).Count }
    }
    return $count
}

function Find-InlineDeliveryBlockLines([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return @() }
    $codeLines = Get-DrivePathCodeLines $Text
    $hits = @()
    for ($i = 0; $i -lt $codeLines.Count; $i++) {
        if ($codeLines[$i] -and $script:InlineDeliveryRx.IsMatch($codeLines[$i])) { $hits += ($i + 1) }
    }
    return $hits
}

$script:InsetsListenerRx = [regex]'ViewCompat\.setOnApplyWindowInsetsListener\s*\('
$script:InsetsCutoutRx = [regex]'displayCutout\s*\(\)'
# The one compliant helper in the repo (utils/ViewExtensions.kt). It already takes
# maxOf(systemBars, displayCutout) per edge, so a file that delegates to it is compliant
# without naming displayCutout itself.
$script:InsetsHelperRx = [regex]'applySystemBarInsetPadding\s*\('
# Declaring the window edge-to-edge / full-screen is what makes safe bounds the caller's
# problem: the system stops insetting the decor view and every edge becomes reachable.
$script:InsetsEdgeToEdgeRx = [regex]'setDecorFitsSystemWindows\s*\([^)]*,\s*false\s*\)'

# Rule 17: UI stays inside systemBars + displayCutout safe bounds in BOTH orientations.
# A file is judged only when it owns a safe-bounds surface - it registers an inset listener,
# or it turns decor fitting off. A cutout is not a system bar: on a notched or punch-hole
# device, padding for systemBars alone still puts content under the cutout in landscape,
# which is the defect that reached the owner twice. Counting per registration rather than
# per file keeps the delta mode meaningful - adding a second uncovered listener to an
# already-listed file is new debt, not existing debt.
function Measure-WindowInsetsText([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return 0 }
    $listeners = $script:InsetsListenerRx.Matches($Text).Count
    $edgeToEdge = $script:InsetsEdgeToEdgeRx.Matches($Text).Count
    if ($listeners -eq 0 -and $edgeToEdge -eq 0) { return 0 }
    if ($script:InsetsCutoutRx.IsMatch($Text)) { return 0 }
    if ($script:InsetsHelperRx.IsMatch($Text)) { return 0 }
    # An edge-to-edge surface with no listener at all is one uncovered surface, not zero.
    if ($listeners -eq 0) { return $edgeToEdge }
    return $listeners
}

# Line numbers of the offending registrations, so -List and the gate's failure output name
# the site instead of only the file.
function Find-WindowInsetsLines([string]$Text) {
    if ((Measure-WindowInsetsText $Text) -le 0) { return @() }
    $lines = $Text -split "`r?`n"
    # Mirror the count exactly: listeners are the sites when there are any, and the
    # edge-to-edge call is the site only when the file registers no listener at all.
    # Listing both would print more lines than the rule counted.
    $rx = if ($script:InsetsListenerRx.IsMatch($Text)) { $script:InsetsListenerRx } else { $script:InsetsEdgeToEdgeRx }
    $hits = @()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($rx.IsMatch($lines[$i])) { $hits += ($i + 1) }
    }
    return $hits
}

$script:WearListStartRx = [regex]'\b(?:ScalingLazyColumn|rememberScalingLazyListState)\s*\('

function Measure-WearListStartText([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return 0 }
    return $script:WearListStartRx.Matches($Text).Count
}

function Find-WearListStartLines([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return @() }
    $lines = $Text -split "`r?`n"
    $hits = @()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($script:WearListStartRx.IsMatch($lines[$i])) { $hits += ($i + 1) }
    }
    return $hits
}

# S1363: a broad `catch (e: Exception)` in coroutine code also catches CancellationException.
# Cancelling a job then reads as a failure: it is logged at error level, converted into a
# domain failure result, and never rethrown, so the parent job believes the child completed
# normally. Leaving a screen mid-scan produced E-level noise that had to be filtered out by
# hand during remote-log triage, which is what surfaced the class.
$script:BroadCatchRx = [regex]'^\s*(?:\}\s*)?catch\s*\(\s*(?:@\w+(?:\([^)]*\))?\s+)?\w+\s*:\s*(?:[\w.]+\.)?(?:Exception|Throwable)\s*\)'
$script:CancelCatchRx = [regex]'catch\s*\(\s*(?:@\w+(?:\([^)]*\))?\s+)?\w+\s*:\s*(?:[\w.]+\.)?CancellationException\s*\)'
# S1889: CancellationException is a typealias for java.util.concurrent.CancellationException, which
# extends IllegalStateException. An arm naming a supertype therefore takes the cancellation without
# ever naming it and, being earlier in the chain, leaves a cured broad arm below it unreachable. The
# guard existed and this rule still read the file as clean - which is how CloudMediaScanner shipped.
$script:CancelSupertypeCatchRx = [regex]'^\s*(?:\}\s*)?catch\s*\(\s*(?:@\w+(?:\([^)]*\))?\s+)?\w+\s*:\s*(?:[\w.]+\.)?(?:IllegalStateException|RuntimeException)\s*\)'
$script:TryOpenRx = [regex]'(?:^|\W)try\s*\{'
$script:FunDeclRx = [regex]'\bfun\b'
$script:SuspendFunRx = [regex]'\bsuspend\s+(?:inline\s+)?fun\b'
# Entering any of these means the code below runs in a coroutine even when the enclosing
# function is not itself `suspend` - the lambda body is.
$script:CoroutineCtxRx = [regex]'\b(?:withContext|coroutineScope|supervisorScope|runBlocking|flow|channelFlow|callbackFlow|produce|launch|async)\s*[({]|\bsuspendCancellableCoroutine\b'
# core/util/CoroutineExt.kt offers the second sanctioned form of the same fix, as a FAMILY rather
# than a single function: `rethrowIfCancellation()` plus every `<verb>UnlessCancellation(..)` member,
# each of which re-throws the cancellation before doing any error-path work. Matched by name shape so
# a new member needs no paired edit here - an enumeration forgotten costs a whole batch its count
# (S2104 ADR-3). Every member's KDoc requires the call to be the FIRST statement of the block, so a
# call sitting after another statement has already run error-path work and is still a violation.
$script:RethrowHelperRx = [regex]'\b(?:rethrowIfCancellation|\w+UnlessCancellation)\s*\('

function Get-LineIndent([string]$line) {
    return [regex]::Match($line, '^[ \t]*').Length
}

# Line-oriented rather than regex-over-whole-text: the rule is about the relationship between
# a catch arm and the arms that precede it in the same chain, which a single pattern cannot
# express. Indentation anchors the chain because the tree is ktlint-formatted, so `try {` and
# every `} catch (` of one chain share a column.
function Find-SwallowedCancellationLines([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return @() }
    $lines = $Text -split "`r?`n"
    $hits = @()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $isCandidate = $script:BroadCatchRx.IsMatch($lines[$i]) -or $script:CancelSupertypeCatchRx.IsMatch($lines[$i])
        if (-not $isCandidate) { continue }
        if ($script:CancelCatchRx.IsMatch($lines[$i])) { continue }
        $indent = Get-LineIndent $lines[$i]

        # Walk up the chain to its `try`. An earlier arm naming CancellationException already
        # rethrows it, so the broad arm below can no longer see it and is not a violation.
        $tryLine = -1
        $covered = $false
        for ($j = $i - 1; $j -ge 0; $j--) {
            $cand = $lines[$j]
            if ($cand.Trim().Length -eq 0) { continue }
            $candIndent = Get-LineIndent $cand
            if ($candIndent -gt $indent) { continue }
            if ($candIndent -lt $indent) { break }
            if ($script:CancelCatchRx.IsMatch($cand)) { $covered = $true; break }
            if ($script:TryOpenRx.IsMatch($cand)) { $tryLine = $j; break }
        }
        if ($covered -or $tryLine -lt 0) { continue }

        # The block may instead open with the helper, which rethrows before any error-path work.
        # A one-line block carries that first statement on the catch line itself, after the opening
        # brace, so seeding the scan from the next line alone would read the closing `}` and call a
        # cured site a violation. That case is not rare: fitting the guard on one line is the entire
        # reason the helper family exists (S1890/S2104). The brace is located at paren depth 0 so an
        # annotated arm - `catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {` - resolves
        # to the block brace rather than to the annotation's own parentheses.
        $firstStatement = ''
        $braceIdx = -1
        $depth = 0
        for ($c = 0; $c -lt $lines[$i].Length; $c++) {
            $ch = $lines[$i][$c]
            if ($ch -eq '(') { $depth++ }
            elseif ($ch -eq ')') { $depth-- }
            elseif ($ch -eq '{' -and $depth -le 0) { $braceIdx = $c; break }
        }
        if ($braceIdx -ge 0) {
            $inlineTail = $lines[$i].Substring($braceIdx + 1).Trim()
            if ($inlineTail.Length -gt 0 -and $inlineTail -notmatch '^(//|/\*)') { $firstStatement = $inlineTail }
        }
        if ($firstStatement.Length -eq 0) {
            for ($j = $i + 1; $j -lt $lines.Count; $j++) {
                $cand = $lines[$j]
                if ($cand.Trim().Length -eq 0) { continue }
                if ($cand.Trim() -match '^(//|/\*|\*)') { continue }
                $firstStatement = $cand
                break
            }
        }
        if ($script:RethrowHelperRx.IsMatch($firstStatement)) { continue }

        # Only coroutine-reachable catches matter: a blocking helper cannot be cancelled this
        # way. The nearest enclosing construct decides - a builder lambda first, otherwise the
        # function declaration itself.
        $tryIndent = Get-LineIndent $lines[$tryLine]
        $inCoroutine = $false
        for ($j = $tryLine - 1; $j -ge 0; $j--) {
            $cand = $lines[$j]
            if ($cand.Trim().Length -eq 0) { continue }
            if ((Get-LineIndent $cand) -ge $tryIndent) { continue }
            if ($script:CoroutineCtxRx.IsMatch($cand)) { $inCoroutine = $true; break }
            if ($script:FunDeclRx.IsMatch($cand)) {
                $inCoroutine = $script:SuspendFunRx.IsMatch($cand)
                break
            }
        }
        if ($inCoroutine) { $hits += ($i + 1) }
    }
    return $hits
}

function Measure-SwallowedCancellationText([string]$Text) {
    return @(Find-SwallowedCancellationLines $Text).Count
}

# S1329: CLAUDE.md Rule 3 - an Activity is a host, not a place for domain wiring. The rule is the
# lint detector's own (lint-rules/../ActivityLogicDetector.kt): an @Inject field in a *Activity class
# whose declared type names a Repository, UseCase, DataSource, Dao or Database. It is mirrored here
# because app_v2/lint-baseline.xml is regenerated only by a full build, so nothing stopped the count
# growing between builds - which is how it reached 78 unnoticed.
$script:ActivityClassRx = [regex]'\bclass\s+\w*Activity\b'
# Modifiers and extra annotations sit between @Inject and `var`, and a long declaration wraps before
# its type. Both shapes are real here - PlayerActivity carries wrapped declarations and `internal`
# ones - and a line-oriented scan silently undercounts every one of them.
$script:ActivityInjectFieldRx = [regex]'@Inject\s+(?:(?:@[\w.]+(?:\([^)]*\))?|internal|private|protected|public|open|final|lateinit)\s+)*var\s+\w+\s*:\s*([A-Za-z0-9_.<>?, ]+)'
# Case-SENSITIVE by construction - [regex] does not fold case the way PowerShell's -match does.
# BrowseActivity's FaviconAtlasStore sits in a `data.repository.streams` package and is NOT a
# violation; folding case would over-count it.
$script:ActivityDomainTypeRx = [regex]'Repository|UseCase|DataSource|Dao|Database'

function Find-ActivityLogicLines([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return @() }
    if (-not $script:ActivityClassRx.IsMatch($Text)) { return @() }
    $hits = @()
    foreach ($m in $script:ActivityInjectFieldRx.Matches($Text)) {
        if (-not $script:ActivityDomainTypeRx.IsMatch($m.Groups[1].Value)) { continue }
        $hits += ($Text.Substring(0, $m.Index) -split "`n").Count
    }
    return $hits
}

function Measure-ActivityLogicText([string]$Text) {
    return @(Find-ActivityLogicLines $Text).Count
}

# S1456: a dialog shown with a bare `.show()` throws the returned AlertDialog away, so nothing can
# dismiss it once the host dies and the window outlives the destroyed Fragment and Activity (S1447).
# util/LifecycleDialogExt.kt carries the cure on both receivers - the builder and an already-created
# dialog - because two shapes reach a bare show(): the fluent chain ending in `.show()`, and the
# builder assigned to a name whose `.create()` result is shown a few lines further down.
$script:DialogBuilderRx = [regex]'(?:MaterialAlertDialogBuilder|AlertDialog\.Builder)\s*\('
$script:DialogAssignRx = [regex]'(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?::[^=]+)?=\s*$'

# Walk forward from the construction with paren and brace depth counters and return the names of the
# calls made at chain level - the identifier after every `.` seen at depth zero. A newline at depth
# zero ends the chain unless the next non-space character is a `.`, which is what keeps a multi-line
# builder chain in one piece instead of cutting it at the first `.setTitle(..)` line.
#
# Depth matters: searching the statement text for `.show()` counts a `Toast.makeText(..).show()`
# written inside a `setItems` lambda as the chain's terminator, which called two compliant sites
# violations. Braces and parens inside comments and strings are a known, accepted approximation.
function Get-DialogChainCalls([string]$Text, [int]$Start) {
    $calls = [System.Collections.Generic.List[string]]::new()
    $depthParen = 0
    $depthBrace = 0
    $i = $Start
    $len = $Text.Length
    while ($i -lt $len) {
        $c = $Text[$i]
        if ($c -eq '(') { $depthParen++ }
        elseif ($c -eq ')') { $depthParen--; if ($depthParen -lt 0) { break } }
        elseif ($c -eq '{') { $depthBrace++ }
        elseif ($c -eq '}') { $depthBrace--; if ($depthBrace -lt 0) { break } }
        elseif ($c -eq '.' -and $depthParen -eq 0 -and $depthBrace -eq 0) {
            $j = $i + 1
            $name = ''
            while ($j -lt $len -and $Text[$j] -match '[A-Za-z0-9_]') { $name += $Text[$j]; $j++ }
            if ($name) { $calls.Add($name) }
        }
        elseif ($c -eq "`n" -and $depthParen -le 0 -and $depthBrace -le 0) {
            $j = $i + 1
            while ($j -lt $len -and ($Text[$j] -eq ' ' -or $Text[$j] -eq "`t" -or $Text[$j] -eq "`r")) { $j++ }
            if ($j -ge $len -or $Text[$j] -ne '.') { break }
        }
        $i++
    }
    return $calls
}

function Find-UntrackedDialogLines([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return @() }
    if (-not $script:DialogBuilderRx.IsMatch($Text)) { return @() }
    $hits = @()
    foreach ($m in $script:DialogBuilderRx.Matches($Text)) {
        $calls = Get-DialogChainCalls $Text $m.Index
        if ($calls -match '^showBoundTo') { continue }
        if ($calls -contains 'show') {
            $hits += ($Text.Substring(0, $m.Index) -split "`n").Count
            continue
        }
        $lineStart = $Text.LastIndexOf("`n", [Math]::Max($m.Index - 1, 0)) + 1
        $declaration = $Text.Substring($lineStart, $m.Index - $lineStart).TrimEnd()
        $assign = $script:DialogAssignRx.Match($declaration)
        if (-not $assign.Success) { continue }
        $held = [regex]::Escape($assign.Groups[1].Value)
        foreach ($use in ([regex]"\b$held\.show\s*\(\s*\)").Matches($Text)) {
            $hits += ($Text.Substring(0, $use.Index) -split "`n").Count
        }
    }
    # One site is reachable twice when a file assigns two builders to the same name, so the rule
    # counts distinct lines - counting matches would report the square of the real number.
    return @($hits | Sort-Object -Unique)
}

function Measure-UntrackedDialogText([string]$Text) {
    return @(Find-UntrackedDialogLines $Text).Count
}

# S3255: docs/ui/PHONE_UI_COMPONENT_PATTERNS.md section 4 - a dialog class reaches safe bounds only
# transitively: the AppDialog factory calls Dialog.applyDialogInsets(), a sheet inherits the
# unconditional bottom inset of BaseAppBottomSheet, and a hand-wired surface calls the
# applySystemBarInsetPadding helper itself. A DialogFragment / BottomSheetDialogFragment subclass
# in a file naming none of those seams registers no inset listener at all, which is the measured
# gap this dimension ratchets out. Counted per declaring class line so the delta mode stays
# meaningful; the file-level seam test keeps a shared base from charging the classes that reach it.
# `\r?$` in the lookahead is load-bearing - these files are CRLF, and a bare `$` never matches
# before the newline when a carriage return sits ahead of it.
$script:DialogClassDeclRx = [regex]'(?m)^\s*(?:(?:abstract|open|final|internal|public|private)\s+)*class\s+\w+[^:{]*:\s*[^:{]*\b(?:BottomSheet)?DialogFragment\b\s*(?=\(|,|\r?$|\{)'
$script:DialogInsetSeamRx = [regex]'BaseAppBottomSheet|applyDialogInsets|applySystemBarInsetPadding|\bAppDialog\b'

function Measure-UnwiredDialogInsetText([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return 0 }
    if (-not $script:DialogClassDeclRx.IsMatch($Text)) { return 0 }
    if ($script:DialogInsetSeamRx.IsMatch($Text)) { return 0 }
    return $script:DialogClassDeclRx.Matches($Text).Count
}

# S1567: a double quote inside a string resource survives the build only when a backslash precedes it
# after XML decoding. Both the bare " and the &quot; entity are dropped by AAPT2's quoting pass - the
# entity because the XML parser decodes it first - so both spellings silently delete the character.
#
# The tag name is captured and closed by a backreference: <string-array name="a"> satisfies <string\b
# and would otherwise pair with the first </item> inside it. (?<!/) drops self-closing elements, which
# would otherwise open a body running to the next closing tag.
$script:ResourceBodyRx = [regex]'(?s)<(string|item)((?:\s[^>]*)?)(?<!/)>(.*?)</\1>'

# A body wrapped in a quote pair with whitespace just inside it is Android's whitespace-preservation
# form, not a visible quote, so its outer pair is exempt. The whitespace test is load-bearing:
# "%1$s" -> folder "%2$s" opens and closes with a quote only because a placeholder sits at each end,
# and exempting its outer pair would leave two of its four quotes invisible.
function Get-ResourceQuoteBodyInner([string]$Body) {
    if ($Body.Length -ge 2 -and $Body[0] -eq '"' -and $Body[-1] -eq '"' -and $Body[-2] -ne '\') {
        $candidate = $Body.Substring(1, $Body.Length - 2)
        if ($candidate -match '^\s' -or $candidate -match '\s$') { return $candidate }
    }
    return $Body
}

function Find-InvisibleResourceQuoteLines([string]$Text) {
    $hits = @()
    if ([string]::IsNullOrEmpty($Text)) { return $hits }
    foreach ($m in $script:ResourceBodyRx.Matches($Text)) {
        $inner = Get-ResourceQuoteBodyInner $m.Groups[3].Value
        if ([string]::IsNullOrEmpty($inner)) { continue }
        $entities = ([regex]::Matches($inner, '&quot;')).Count
        $bares = ([regex]::Matches($inner, '(?<!\\)"')).Count
        if (($entities + $bares) -eq 0) { continue }
        $hits += ($Text.Substring(0, $m.Index) -split "`n").Count
    }
    return @($hits | Sort-Object -Unique)
}

function Measure-InvisibleResourceQuotes([string]$Text) {
    $n = 0
    if ([string]::IsNullOrEmpty($Text)) { return $n }
    foreach ($m in $script:ResourceBodyRx.Matches($Text)) {
        $inner = Get-ResourceQuoteBodyInner $m.Groups[3].Value
        if ([string]::IsNullOrEmpty($inner)) { continue }
        $n += ([regex]::Matches($inner, '&quot;')).Count
        $n += ([regex]::Matches($inner, '(?<!\\)"')).Count
    }
    return $n
}

# S1586: AAPT2 reads a backslash as an escape introducer, so one that introduces nothing it knows is
# consumed and the character never reaches the user - the same silent class of loss as the quote
# above, with no build warning either. The escape table must stay identical to ConvertTo-AaptBackslash
# in scripts/utils/set-android-string.ps1 and seed-locale-tranche.ps1, or the gate would flag exactly
# what those writers just produced. The optional group is what makes \\ count as one recognised unit
# instead of two lone slashes.
$script:LoneResourceBackslashRx = [regex]'\\(u[0-9a-fA-F]{4}|[nt''"\\])?'

function Find-LoneResourceBackslashLines([string]$Text) {
    $hits = @()
    if ([string]::IsNullOrEmpty($Text)) { return $hits }
    foreach ($m in $script:ResourceBodyRx.Matches($Text)) {
        $body = $m.Groups[3].Value
        if ([string]::IsNullOrEmpty($body)) { continue }
        $lone = @($script:LoneResourceBackslashRx.Matches($body) | Where-Object { -not $_.Groups[1].Success })
        if ($lone.Count -eq 0) { continue }
        $hits += ($Text.Substring(0, $m.Index) -split "`n").Count
    }
    return @($hits | Sort-Object -Unique)
}

function Measure-LoneResourceBackslashes([string]$Text) {
    $n = 0
    if ([string]::IsNullOrEmpty($Text)) { return $n }
    foreach ($m in $script:ResourceBodyRx.Matches($Text)) {
        $body = $m.Groups[3].Value
        if ([string]::IsNullOrEmpty($body)) { continue }
        $n += @($script:LoneResourceBackslashRx.Matches($body) | Where-Object { -not $_.Groups[1].Success }).Count
    }
    return $n
}

# S2250: a policy check can be hoisted or expressed as an early return, so a lexical gate cannot
# reliably prove that an individual animator consulted it. Count the animation vocabulary instead:
# adding any new primitive makes the review explicit, while the baseline never hides that growth.
#
# S2536: the vocabulary above is entirely NAMED animation APIs, and that is what the rule could not
# see. A hand-rolled per-frame loop - withFrameNanos advancing a clock and invalidating - is not one
# of them, so the largest animation in the watch module, measured at about 1.5 cores while playing,
# scored zero hits from a gate whose whole job is finding animation. The watch baseline of 2 came
# entirely from one already-gated call elsewhere. The gap was in the mechanism rather than in any one
# ticket's attention, so the fix is the pattern: withFrameNanos for the hand-rolled loop,
# rememberInfiniteTransition for the Compose form of an endless animator, and animateContentSize for
# the layout animation that declares itself in a modifier rather than at a call site.
$script:UnpolicedAnimationRx = [regex]'\boverridePendingTransition\b|\boverrideActivityTransition\b|\bbeginDelayedTransition\b|\bLayoutTransition\b|\bsetPageTransformer\b|\bObjectAnimator\b|\bValueAnimator\b|\bAnimatorSet\b|\bAnimationUtils\.loadAnimation\b|\bwithCrossFade\s*\(\s*(?!0(?:\.0+)?(?:[fFdD])?\s*[,)])|\bAnimatedVisibility\b|\bwithFrameNanos\b|\brememberInfiniteTransition\b|\banimateContentSize\b'

function Find-UnpolicedAnimationLines([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return @() }
    $hits = @()
    $lines = $Text -split "`r?`n"
    for ($i = 0; $i -lt $lines.Count; $i++) {
        foreach ($match in $script:UnpolicedAnimationRx.Matches($lines[$i])) {
            $hits += ($i + 1)
        }
    }
    return $hits
}

function Measure-UnpolicedAnimationText([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return 0 }
    return $script:UnpolicedAnimationRx.Matches($Text).Count
}

# S2748: a CoroutineScope built in a test source set on anything other than the test's own
# scheduler. Such a scope is not a child of `runTest`, so nothing joins it and its coroutine can
# outlive the test body on a real dispatcher - the path by which an exception reaches the global
# ExceptionCollector and is charged to the next unrelated test on the worker (S2746).
# Lexical by necessity: the link between a field scope and the `@After` that should join it cannot
# be established by a regex, so the rule counts CONSTRUCTIONS and the ratchet stops growth. The
# nine sites S2748 fixed stay counted - their cure is the teardown, not the construction.
$script:TestUnjoinedScopeRx = [regex]'\bCoroutineScope\s*\('
# Case-insensitive: a scheduler-bound scope reaches the dispatcher through a rule field as often as
# through the type name (`CoroutineScope(dispatcherRule.testDispatcher)`), and both are correct.
$script:TestScopeAllowRx = [regex]'(?i)testdispatcher|testscheduler|testscope'

function Find-TestUnjoinedScopeLines([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return @() }
    $hits = @()
    $lines = $Text -split "`r?`n"
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($script:TestScopeAllowRx.IsMatch($line)) { continue }
        foreach ($match in $script:TestUnjoinedScopeRx.Matches($line)) {
            $hits += ($i + 1)
        }
    }
    return $hits
}

function Measure-TestUnjoinedScopeText([string]$Text) {
    # @() around the call: a helper returning one line number unrolls to a bare int, and an empty
    # result to $null - both of which have no usable .Count here.
    return @(Find-TestUnjoinedScopeLines $Text).Count
}

# S2328: the caption/value split - a label that takes the row's free width while its value sits at
# the far edge. Structural, not lexical, and deliberately so: the reference settings row carries the
# SAME attributes as the defect (a weight, an end gravity) and differs only in WHERE they sit, so a
# regex cannot separate them. The discriminator is order - in the reference the weighted spacer comes
# AFTER the value, so the slack falls at the row's end instead of between the pair.
$script:CaptionValueControlRx = [regex]'(?:^|\.)(?:Switch|MaterialSwitch|SwitchCompat|SwitchMaterial|Button|MaterialButton|CheckBox|MaterialCheckBox|AppCompatCheckBox|Slider|SeekBar|RangeSlider|ImageButton|EditText|TextInputEditText|RadioButton|Spinner)$'
$script:CaptionValueTextRx = [regex]'(?:^|\.)(?:TextView|MaterialTextView|AppCompatTextView|Chronometer)$'

function Get-CaptionValueSimpleName([System.Xml.Linq.XElement]$Element) {
    $n = $Element.Name.LocalName
    $i = $n.LastIndexOf('.')
    if ($i -ge 0) { $n = $n.Substring($i + 1) }
    return $n
}

# Namespace-agnostic on purpose: `layout_constraint*` arrives in the res-auto namespace and
# `layout_weight` in the android one, and no layout attribute shares a local name across the two.
function Get-CaptionValueAttr([System.Xml.Linq.XElement]$Element, [string]$LocalName) {
    foreach ($a in $Element.Attributes()) {
        if ($a.Name.LocalName -eq $LocalName) { return $a.Value }
    }
    return $null
}

function Test-CaptionValueGravityEnd([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return $false }
    foreach ($part in ($Value -split '\|')) {
        if ($part.Trim() -in @('end', 'right')) { return $true }
    }
    return $false
}

function Test-CaptionValueIsText([System.Xml.Linq.XElement]$Element) {
    $script:CaptionValueTextRx.IsMatch((Get-CaptionValueSimpleName $Element))
}

function Test-CaptionValueIsControl([System.Xml.Linq.XElement]$Element) {
    $script:CaptionValueControlRx.IsMatch((Get-CaptionValueSimpleName $Element))
}

# A value is "text-like" when it is a TextView, or a wrapper carrying text and no control. The
# wrapper case is what makes a primary+secondary value column count; the control case is what keeps
# the reference settings row - caption, then a switch or a chevron at the end - passing.
function Test-CaptionValueTextLike([System.Xml.Linq.XElement]$Element) {
    if (Test-CaptionValueIsControl $Element) { return $false }
    if (Test-CaptionValueIsText $Element) { return $true }
    $desc = @($Element.Descendants())
    if ($desc.Count -eq 0) { return $false }
    foreach ($d in $desc) { if (Test-CaptionValueIsControl $d) { return $false } }
    foreach ($d in $desc) { if (Test-CaptionValueIsText $d) { return $true } }
    return $false
}

function Test-CaptionValueHorizontalRow([System.Xml.Linq.XElement]$Element) {
    if ((Get-CaptionValueSimpleName $Element) -ne 'LinearLayout') { return $false }
    $o = Get-CaptionValueAttr $Element 'orientation'
    return ([string]::IsNullOrWhiteSpace($o) -or $o -eq 'horizontal')
}

# The one definition of the violation. Measure- and Find- both read it, so the count the gate
# enforces and the lines `-List` prints can never disagree (S1621).
function Get-CaptionValueSplitHits([string]$Text) {
    $hits = @()
    if ([string]::IsNullOrEmpty($Text)) { return $hits }
    # Cheap text gate before the parse: most layout files carry none of this vocabulary, and the
    # XML parse is the expensive half of the rule.
    if ($Text -notmatch 'layout_weight|layout_constraintEnd_toEndOf|gravity') { return $hits }

    $doc = $null
    try {
        $doc = [System.Xml.Linq.XDocument]::Parse($Text, [System.Xml.Linq.LoadOptions]::SetLineInfo)
    }
    catch {
        # A malformed file is the XML parser's finding, not this rule's - turning it into a
        # violation count would blame the wrong gate for the wrong defect.
        return $hits
    }
    if ($null -eq $doc -or $null -eq $doc.Root) { return $hits }

    foreach ($el in $doc.Descendants()) {
        $name = Get-CaptionValueSimpleName $el
        $line = ([System.Xml.IXmlLineInfo]$el).LineNumber

        # Form 1 - weighted caption in a horizontal row with the value after it.
        if (Test-CaptionValueHorizontalRow $el) {
            $kids = @($el.Elements())
            for ($i = 0; $i -lt $kids.Count; $i++) {
                $kid = $kids[$i]
                if (-not (Test-CaptionValueIsText $kid)) { continue }
                $wv = 0.0
                if (-not [double]::TryParse((Get-CaptionValueAttr $kid 'layout_weight'), [ref]$wv)) { continue }
                if ($wv -le 0) { continue }
                for ($j = $i + 1; $j -lt $kids.Count; $j++) {
                    if (Test-CaptionValueTextLike $kids[$j]) {
                        $hits += [pscustomobject]@{ Line = ([System.Xml.IXmlLineInfo]$kid).LineNumber; Form = 'weighted-caption' }
                        break
                    }
                }
            }
        }

        # Form 2 - the value pushed to the row's far end by its own gravity.
        if ((Test-CaptionValueIsText $el) -and $null -ne $el.Parent -and (Test-CaptionValueHorizontalRow $el.Parent)) {
            $g = Get-CaptionValueAttr $el 'gravity'
            $lg = Get-CaptionValueAttr $el 'layout_gravity'
            $ta = Get-CaptionValueAttr $el 'textAlignment'
            if ((Test-CaptionValueGravityEnd $g) -or (Test-CaptionValueGravityEnd $lg) -or ($ta -eq 'viewEnd')) {
                $prior = $false
                foreach ($sib in $el.ElementsBeforeSelf()) { if (Test-CaptionValueIsText $sib) { $prior = $true } }
                if ($prior) { $hits += [pscustomobject]@{ Line = $line; Form = 'end-aligned-value' } }
            }
        }

        # Form 3 - the split declared in a style, which hands it to every consumer at once. This is
        # the form that reached seven network monitor screens from two style blocks.
        if ($name -eq 'style') {
            $hasWeight = $false
            $endGravity = $false
            foreach ($item in $el.Elements()) {
                if ((Get-CaptionValueSimpleName $item) -ne 'item') { continue }
                $itemName = Get-CaptionValueAttr $item 'name'
                if ($itemName -eq 'android:layout_weight') { $hasWeight = $true }
                if ($itemName -eq 'android:gravity' -and (Test-CaptionValueGravityEnd $item.Value)) { $endGravity = $true }
            }
            if ($hasWeight -and $endGravity) { $hits += [pscustomobject]@{ Line = $line; Form = 'style-declared-split' } }
        }

        # Form 4 - the constraint spelling: value pinned to the parent's end and anchored to a
        # sibling's top, with nothing tying its start to the caption, so the gap is the screen.
        if (Test-CaptionValueIsText $el) {
            if ((Get-CaptionValueAttr $el 'layout_constraintEnd_toEndOf') -eq 'parent') {
                $hasStart = $false
                foreach ($a in $el.Attributes()) {
                    if ($a.Name.LocalName -like 'layout_constraintStart_*') { $hasStart = $true }
                }
                $topTo = Get-CaptionValueAttr $el 'layout_constraintTop_toTopOf'
                if (-not $hasStart -and -not [string]::IsNullOrWhiteSpace($topTo) -and $topTo -ne 'parent') {
                    $hits += [pscustomobject]@{ Line = $line; Form = 'unanchored-end-constraint' }
                }
            }
        }
    }

    return $hits
}

function Measure-CaptionValueSplit([string]$Text) {
    return @(Get-CaptionValueSplitHits $Text).Count
}

# S3249: an id that names a strip of controls rather than a control. The rule below counts a raw
# ImageButton only inside one of these, because an ImageButton elsewhere - a row's trailing action,
# a dialog's single glyph - is not the defect: the defect is two icon-button idioms inside one bar,
# differing in touch target, ripple shape and disabled tint.
$script:BarContainerIdRx = [regex]'(?i)@\+?id/\w*(bar|panel|controls|operations|toolbar|strip)'

function Get-RawImageButtonInBarHits([string]$Text) {
    $hits = @()
    if ([string]::IsNullOrEmpty($Text)) { return $hits }
    # Cheap text gate before the parse - most layouts declare no ImageButton at all.
    if ($Text -notmatch '<ImageButton') { return $hits }

    $doc = $null
    try {
        $doc = [System.Xml.Linq.XDocument]::Parse($Text, [System.Xml.Linq.LoadOptions]::SetLineInfo)
    }
    catch {
        # A malformed file is the XML parser's finding, not this rule's.
        return $hits
    }
    if ($null -eq $doc -or $null -eq $doc.Root) { return $hits }

    foreach ($el in $doc.Descendants()) {
        if ((Get-CaptionValueSimpleName $el) -ne 'ImageButton') { continue }
        $parent = $el.Parent
        while ($null -ne $parent) {
            $id = Get-CaptionValueAttr $parent 'id'
            if ($null -ne $id -and $script:BarContainerIdRx.IsMatch($id)) {
                $hits += [pscustomobject]@{ Line = ([System.Xml.IXmlLineInfo]$el).LineNumber }
                break
            }
            $parent = $parent.Parent
        }
    }
    return $hits
}

function Measure-RawImageButtonInBar([string]$Text) {
    return @(Get-RawImageButtonInBarHits $Text).Count
}

function Find-RawImageButtonInBarLines([string]$Text) {
    return @(Get-RawImageButtonInBarHits $Text | ForEach-Object { $_.Line } | Sort-Object -Unique)
}

function Find-CaptionValueSplitLines([string]$Text) {
    return @(Get-CaptionValueSplitHits $Text | ForEach-Object { $_.Line } | Sort-Object -Unique)
}

function New-RegexRule {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][regex]$Pattern,
        [Parameter(Mandatory)][string]$FailMessage,
        [string[]]$Extensions = @('.kt'),
        [string[]]$Roots = @('app_v2/src/main'),
        [string]$PathFilter = 'app_v2/src/main/',
        [string]$Baseline,
        # File names the rule must not judge - the compat seam a rule exists to route callers
        # towards is itself full of the pattern it bans. Dropping this exclusion silently made
        # deprecated-pm-flags report 8 against a baseline of 0.
        [string[]]$ExcludeNames = @()
    )
    $rx = $Pattern
    [pscustomobject]@{
        Name         = $Name
        Extensions   = $Extensions
        Roots        = $Roots
        PathFilter   = $PathFilter
        Baseline     = if ($Baseline) { $Baseline } else { "$Name-baseline.txt" }
        ExcludeNames = $ExcludeNames
        CountInText  = { param($t) $rx.Matches($t).Count }.GetNewClosure()
        FailMessage  = $FailMessage
    }
}

<#
.SYNOPSIS
    Every lexical source rule, in the order the neuroslop umbrella reported them.
#>
function Get-SourceRules {
    [CmdletBinding()]
    param()

    @(
        [pscustomobject]@{
            Name        = 'trivial-comments'
            Extensions  = @('.kt')
            Roots       = @('app_v2/src/main')
            PathFilter  = 'app_v2/src/main/'
            Baseline     = 'trivial-comments-baseline.txt'
            ExcludeNames = @()
            CountInText = {
                param($t)
                $n = 0
                foreach ($ln in ($t -split "`r?`n")) { if (Test-TrivialCommentLine $ln) { $n++ } }
                $n
            }
            FailMessage = 'new trivial comment introduced. Explain WHY, or delete the comment (CLAUDE.md Rule 9).'
        },
        # S1694: the boundary is app_v2 = View, wear = Compose. Roots deliberately stop at
        # app_v2/src/main, so the watch module - which is Compose end to end and has no XML layout at
        # all - is never judged by this dimension.
        (New-RegexRule -Name 'compose-island' `
                -Pattern ([regex]'setContent\s*\{') `
                -FailMessage ('new Compose island in app_v2 (CLAUDE.md Rule 32). app_v2 is View-based: build the screen in XML + ViewBinding. ' +
                    'Removing an island lowers this baseline; raising it is a boundary decision, not a build fix. ' +
                    'Why this is a gate and not taste (S2517 moved this off the always-loaded rules page): 404169 of app_v2''s lines are View, ' +
                    'and the sixth island appeared five days after an audit counted five, with nobody having decided to grow the set (S1694). ' +
                    'Islands leave OPPORTUNISTICALLY, when another ticket reaches them, never as a campaign. Removing Compose from ' +
                    'app_v2/build.gradle.kts altogether has one precondition recorded in docs/ARCHITECTURE.md: Icons.Default.Pause / SkipNext / ' +
                    'SkipPrevious exist only in the extended icon set and must become vector drawables first (S0385).')),
        # S3068: an anonymous TypeToken subclass reads its own generic superclass through the
        # `Signature` attribute, and R8 keeps that attribute only on classes it considers kept - so
        # the construct compiles, passes every debug test, and throws in the class initializer of the
        # shipped APK. It reached production twice from the same keep rules: S0722 caught it on the
        # minified benchmark variant, and S3068 found it in Play vitals on versionCode 260902195,
        # crashing the watch settings mirror for real users. Baseline is 0 and the cure is mechanical,
        # so this rule refuses growth outright rather than measuring it.
        (New-RegexRule -Name 'anonymous-typetoken' `
                -Pattern ([regex]'object\s*:\s*(?:com\.google\.gson\.reflect\.)?TypeToken\s*<') `
                -FailMessage ('anonymous Gson TypeToken subclass in app_v2 (S3068). Build the type from class literals instead: ' +
                    'TypeToken.getParameterized(Map::class.java, String::class.java, Long::class.javaObjectType).type - ' +
                    'that call reads no generic signature, so it survives any R8 configuration. Use Long::class.javaObjectType, ' +
                    'not Long::class.java: the latter is the primitive, which Gson has no adapter for. ' +
                    'Judged in app_v2 only - wear/proguard-rules.pro keeps the unweakened Gson rules and has never seen the crash.')),
        # S3270: media3 1.11.0 writes per-controller state back AFTER handing the callback out -
        # `MediaSessionImpl.dispatchOnPlayerInfoChanged` checks `isConnected` at the top of the loop
        # turn, calls `onPlayerInfoChanged`, then reads the same record through `checkNotNull` in
        # `ConnectedControllersManager.updateLastSentTimelineAndTracks`. Session and controller share
        # one process and one looper here, so a release taken from inside a Player.Listener removes the
        # record between those two points and the library throws a fatal NPE on the main thread. It
        # reached a remote tester twice from two different owners (S3164, then S3270), which is why the
        # remembered rule became this one. Baseline is 0 and the cure is one call, so growth is refused
        # outright rather than measured. Upstream androidx/media #3375, still open.
        (New-RegexRule -Name 'media3-raw-controller-release' `
                -Pattern ([regex]'MediaController\.releaseFuture\s*\(|\bmediaController\??\.release\s*\(\s*\)') `
                -ExcludeNames @('MediaControllerRelease.kt') `
                -FailMessage ('raw media3 MediaController teardown in app_v2 (S3270). Route it through ' +
                    'core/playback/MediaControllerRelease - release(controller) / releaseFuture(future, looper) - ' +
                    'which posts the teardown as a looper message so the session''s dispatch loop finishes first. ' +
                    'A release taken from inside a Player.Listener removes the controller''s record mid-dispatch and ' +
                    'media3 1.11.0 throws a fatal NPE there (androidx/media #3375). This baseline is 0 and is never raised.')),
        # S3401: deleting a `Timber.d("Sxxxx: ..")` probe that was an effect's only statement left the
        # effect behind with an empty body - seven such shells in wear on 2026-09-23, each launching a
        # coroutine that does nothing. detekt has no rule for an empty lambda argument, and
        # remove-ticket-probes.ps1 covers only the scripted removal, so a hand removal needs this gate.
        # Both modules share one entry: the baseline is 0, so no cleanup exists for a regression to hide behind.
        (New-RegexRule -Name 'empty-compose-effect' `
                -Pattern ([regex]'(?:\bLaunchedEffect\((?:[^()\r\n]|\([^()\r\n]*\))*\)|\bSideEffect)[\t ]*\{\s*\}') `
                -Roots @('app_v2/src', 'wear/src') `
                -PathFilter '^(app_v2|wear)/src/' `
                -FailMessage ('empty Compose effect (S3401) - a LaunchedEffect(..) {} or SideEffect {} with nothing inside, usually left ' +
                    'when a Timber.d("Sxxxx: ..") probe that was its only statement was deleted. Delete the effect block too, and its ' +
                    'import when the file no longer calls it; scripts/quality/remove-ticket-probes.ps1 does both. This baseline is 0 and is never raised.')),
        # S1693: growth stop for findViewById, not a placement rule. Whether one call is legitimate
        # (custom View, adapter, runtime-resolved layout, documented host-neutral helper) or legacy
        # is NOT lexically decidable - both shapes look identical - so this rule counts growth only.
        # Category-C files (raw-inflate, no binding) convert opportunistically when another ticket
        # touches them, the Rule 32 model: each conversion lowers the baseline on the next green
        # full run, and the baseline never rises without a boundary decision.
        (New-RegexRule -Name 'findviewbyid' `
                -Pattern ([regex]'\bfindViewById\s*[<(]') `
                -FailMessage 'new findViewById in app_v2/src/main (S1693). Use the layout''s generated binding field; if this file is genuinely a legitimate shape (custom View, adapter, runtime-resolved layout, documented host-neutral helper), justify the growth in review instead of raising the baseline.'),
        # S2103: the layering rule `UI -> ViewModel -> UseCase -> Repository -> DataSource`
        # (CLAUDE.md Rule 8, docs/ARCHITECTURE.md) was the last architectural rule in this repo with
        # no exit code behind it, and Rule 33's own measurement is that prose holds at 1-8% while an
        # exit code holds at 99%. Growth stops, not a migration order: measured 2026-08-27 the debt is
        # 403 / 47 / 36 / 2 and no campaign over the 164 files is scheduled - the Rule 32 model, same
        # as findviewbyid above.
        #
        # FOUR baselines rather than one aggregate, and the overlap of the last two with the first is
        # deliberate. The numbers span three orders of magnitude, so under a single counter a new DAO
        # in a fragment could be paid for by deleting one unused data.cloud import in the same change.
        # S1910 is the ticket where exactly that masking happened. The cost of the split is zero: the
        # root app_v2/src/main is already walked for every rule above, so each of these is one more
        # regex pass over text already in memory.
        #
        # `\r?$` is load-bearing on the last two - these files are CRLF, and in .NET multiline mode
        # `$` matches before the `\n` with the `\r` still ahead of it, so a bare `Entity$` would count
        # zero and ship a dead gate that reads green.
        (New-RegexRule -Name 'ui-imports-data' `
                -Pattern ([regex]'(?m)^import com\.sza\.fastmediasorter\.data\.') `
                -PathFilter 'app_v2/src/main/java/com/sza/fastmediasorter/ui/' `
                -FailMessage 'new data-layer import in a UI file (S2103). UI reaches data through a UseCase, not directly: inject the UseCase and let it own the repository call. The baseline falls when an import moves behind its layer; it is never raised.'),
        # Sharper than the rule above and worth driving to zero first: a Room DAO or entity in a
        # fragment means the persistence schema is now a UI dependency, so a migration cannot move
        # without touching screens.
        (New-RegexRule -Name 'ui-imports-room' `
                -Pattern ([regex]'(?m)^import com\.sza\.fastmediasorter\.data\.[A-Za-z0-9_.]*(Dao|Entity)\r?$') `
                -PathFilter 'app_v2/src/main/java/com/sza/fastmediasorter/ui/' `
                -FailMessage 'Room DAO or entity imported straight into UI (S2103). Map the entity to a domain model in the repository and let the UI see only that model. This is the sharpest of the four layer rules - its baseline is meant to reach zero.'),
        # Deliberately any *Impl under data., not only data.repository: today both hits live in
        # data.repository, so widening moves no baseline, but a data.cloud.SomethingImpl is the same
        # violation and the narrow pattern would have waved it through.
        (New-RegexRule -Name 'ui-imports-impl' `
                -Pattern ([regex]'(?m)^import com\.sza\.fastmediasorter\.data\.[A-Za-z0-9_.]*Impl\r?$') `
                -PathFilter 'app_v2/src/main/java/com/sza/fastmediasorter/ui/' `
                -FailMessage 'concrete data-layer implementation imported in UI (S2103). Depend on the interface the impl satisfies and let Hilt bind it, so the UI cannot be coupled to one implementation.'),
        # The UseCase layer skipped: 219 UseCase classes exist and 31 of the 44 ViewModels still reach
        # past them into domain.repository. PathFilter is the ui/ subtree because every *ViewModel.kt
        # in app_v2/src/main lives there - verified 2026-08-27, zero outside it.
        (New-RegexRule -Name 'viewmodel-imports-repository' `
                -Pattern ([regex]'(?m)^import com\.sza\.fastmediasorter\.domain\.repository\.') `
                -PathFilter 'app_v2/src/main/java/com/sza/fastmediasorter/ui/.*ViewModel\.kt$' `
                -FailMessage 'ViewModel imports a repository directly, skipping the UseCase layer (S2103). Put the operation in a VerbNounUseCase and inject that instead. The baseline falls when a call moves into a UseCase; it is never raised.'),
        # S2751: the same layer arrow read in the OTHER direction. The five rules above all catch an
        # upper layer reaching DOWN past its neighbour; none of them can see a lower layer reaching UP,
        # so a use case importing a screen type produced no red exit and appeared silently - measured
        # 2026-09-08, six such imports in two watch use cases, none of which any gate had ever reported.
        #
        # THREE rules rather than one, on the S2103 reasoning above: a new edge in one module-and-layer
        # pair must not be payable by deleting an unrelated edge in another. Baselines seeded at the
        # measured 0 / 2 / 8 after the two watch use cases were unpicked. The watch data-layer entry is
        # recorded rather than unpicked on purpose (strategic S2751 §6): its one offender writes into a
        # Compose snapshot-state holder, and moving that holder down would drag the Compose runtime into
        # the domain - a worse violation than the one it removes, and its own decision to make.
        (New-RegexRule -Name 'wear-domain-imports-ui' `
                -Pattern ([regex]'(?m)^import com\.sza\.fastmediasorter\.wear\.ui\.') `
                -Roots @('wear/src/main') `
                -PathFilter 'wear/src/main/java/com/sza/fastmediasorter/wear/domain/' `
                -FailMessage 'watch domain code imports a screen type (S2751), inverting UI -> ViewModel -> UseCase -> Repository -> DataSource. Answer with a domain value and let the navigation or tile branch map it to a route or a glyph; move a table that returns domain records into domain/catalog. This baseline is 0 and is never raised.'),
        (New-RegexRule -Name 'wear-data-imports-ui' `
                -Pattern ([regex]'(?m)^import com\.sza\.fastmediasorter\.wear\.ui\.') `
                -Roots @('wear/src/main') `
                -PathFilter 'wear/src/main/java/com/sza/fastmediasorter/wear/data/' `
                -FailMessage 'watch data code imports a screen type (S2751), inverting the layer arrow. Publish the value the screens need from the data layer and let the UI mirror it into its own state holder; the baseline records the power-policy writer that predates this rule and falls when it moves.'),
        (New-RegexRule -Name 'domain-imports-ui' `
                -Pattern ([regex]'(?m)^import com\.sza\.fastmediasorter\.ui\.') `
                -PathFilter 'app_v2/src/main/java/com/sza/fastmediasorter/domain/' `
                -FailMessage 'phone domain code imports a screen type (S2751), inverting the layer arrow. Answer with a domain value and let the UI map it; an Activity class named in a use case belongs behind a navigation contract. Growth stop only - no campaign over the existing sites is scheduled, and the baseline falls when one moves.'),
        (New-RegexRule -Name 'empty-catch' `
                -Pattern ([regex]'catch\s*\([^)]*\)\s*\{\s*(?:(?://[^\r\n]*)|(?:/\*[\s\S]*?\*/))?\s*\}') `
                -FailMessage 'new empty catch block introduced. Recover, use a safe default, or log at the correct level.'),
        # S1932: all five layout directories, not the two this rule was declared with. A colour
        # hardcoded in layout-sw480dp, layout-sw720dp or layout-w600dp was forbidden by Rule 19 and
        # counted by nobody. Widening cannot move the baseline: measured 2026-08-21, those three
        # directories hold five files between them and zero hardcoded colours, while layout (29) and
        # layout-land (59) sum to exactly the baseline of 88.
        (New-RegexRule -Name 'layout-hardcoded-colors' `
                -Pattern ([regex]'="#[0-9a-fA-F]{3,8}"') `
                -Extensions @('.xml') `
                -Roots @('app_v2/src/main/res/layout', 'app_v2/src/main/res/layout-land',
                         'app_v2/src/main/res/layout-sw480dp', 'app_v2/src/main/res/layout-sw720dp',
                         'app_v2/src/main/res/layout-w600dp') `
                -PathFilter 'app_v2/src/main/res/layout(-land|-sw480dp|-sw720dp|-w600dp)?/' `
                -FailMessage 'new hardcoded layout color introduced. Reference a theme attr or named color.'),
        # S1922: growth stop for dimension literals, on the Rule 32 / findviewbyid model above -
        # literals convert when another ticket reaches the file, each conversion lowers the baseline
        # on the next green full run, and no campaign over the 331 layout files is scheduled.
        #
        # '0dp' is excluded deliberately, and it is not a rounding decision: measured 2026-08-21,
        # 1561 of the 3454 literals in these directories are "0dp", which is 45% of them. In a
        # ConstraintLayout '0dp' means "match constraints" - a structural keyword, not a size. It has
        # no value anyone could want to change in one place, and moving it into @dimen/ destroys the
        # idiom's readability. Counting it would demand ~1561 conversions that must not happen.
        #
        # Five roots, not the two the colour rule above uses: this module has five layout directories
        # and the ticket's measurement covered all of them. The colour rule's narrower scope is its
        # own gap and is tracked separately (S1932), not widened here - that would move its baseline.
        (New-RegexRule -Name 'layout-hardcoded-dimens' `
                -Pattern ([regex]'="(?!0dp")[0-9]+(\.[0-9]+)?(dp|sp)"') `
                -Extensions @('.xml') `
                -Roots @('app_v2/src/main/res/layout', 'app_v2/src/main/res/layout-land',
                         'app_v2/src/main/res/layout-sw480dp', 'app_v2/src/main/res/layout-sw720dp',
                         'app_v2/src/main/res/layout-w600dp') `
                -PathFilter 'app_v2/src/main/res/layout(-land|-sw480dp|-sw720dp|-w600dp)?/' `
                -FailMessage 'new hardcoded dimension literal in a layout (S1922). Move the value into @dimen/ and reference it, so the size can be changed in one place. Structural "0dp" (ConstraintLayout match-constraints) is NOT counted by this rule - if that is what you added, this is not the finding.'),
        # S2193: "one visual form per element role" (docs/ARCHITECTURE.md, right before the Trigger
        # Row patterns). SettingsToggleRow already owns the toggle-row role; a hand-rolled
        # MaterialSwitch + TextView + ImageButton triplet outside it is the same debt Pattern A's
        # own prose already calls out. Excluded: the wrapper's own layout (it legitimately embeds
        # the switch), and item_scheduled_operation.xml, the S0536 documented dense-list-item
        # exception where ARCHITECTURE.md explicitly allows a bare MaterialSwitch. Same five layout
        # roots as the colour/dimen rules above, on the same S1932 measurement basis.
        (New-RegexRule -Name 'view-raw-switch' `
                -Pattern ([regex]'<com\.google\.android\.material\.materialswitch\.MaterialSwitch\b') `
                -Extensions @('.xml') `
                -Roots @('app_v2/src/main/res/layout', 'app_v2/src/main/res/layout-land',
                         'app_v2/src/main/res/layout-sw480dp', 'app_v2/src/main/res/layout-sw720dp',
                         'app_v2/src/main/res/layout-w600dp') `
                -PathFilter 'app_v2/src/main/res/layout(-land|-sw480dp|-sw720dp|-w600dp)?/' `
                -ExcludeNames @('view_settings_toggle_row.xml', 'item_scheduled_operation.xml') `
                -FailMessage 'new hand-rolled MaterialSwitch row outside the canonical wrapper (S2193). Use com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow (docs/ARCHITECTURE.md Pattern A) instead of a private MaterialSwitch + TextView triplet.'),
        # S2193: same principle, checkbox side. FormCheckboxRow already owns the checkbox-row role
        # and its subtitle is optional (setSubtitle(null) hides it), so it is the canonical form
        # with or without a subtitle - a raw MaterialCheckBox outside it (e.g. a hand-rolled
        # media-type filter grid) is the same debt as the switch rule above. Only the wrapper's own
        # layout is excluded.
        (New-RegexRule -Name 'view-raw-checkbox' `
                -Pattern ([regex]'<com\.google\.android\.material\.checkbox\.MaterialCheckBox\b') `
                -Extensions @('.xml') `
                -Roots @('app_v2/src/main/res/layout', 'app_v2/src/main/res/layout-land',
                         'app_v2/src/main/res/layout-sw480dp', 'app_v2/src/main/res/layout-sw720dp',
                         'app_v2/src/main/res/layout-w600dp') `
                -PathFilter 'app_v2/src/main/res/layout(-land|-sw480dp|-sw720dp|-w600dp)?/' `
                -ExcludeNames @('view_form_checkbox_row.xml') `
                -FailMessage 'new raw MaterialCheckBox outside the canonical wrapper (S2193). Use com.sza.fastmediasorter.ui.common.widget.FormCheckboxRow (docs/ARCHITECTURE.md Pattern B - subtitle is optional) instead of a hand-rolled checkbox.'),
        # S3303: a user-visible label taken from a framework resource. @android:string/* and
        # android.R.string.* live in framework-res.apk, so they resolve against the system DISPLAY
        # locale, while the app - which declares android:localeConfig - resolves its own resources
        # from the user's ordered language list. On a device where those two differ the label arrives
        # in the wrong language, which is what the owner read on 2026-09-18: an English "Cancel" on a
        # fully Russian screen. Both baselines are 0 because the ticket's sweep emptied the tree
        # (28 layout attributes, 175 Kotlin references across both modules).
        #
        # Why a gate and not review: the defect is invisible wherever the display locale matches the
        # app locale, which is every development machine and every emulator this repo builds against.
        # Nobody can see it, so nothing but a rule can refuse it.
        (New-RegexRule -Name 'framework-label-string-xml' `
                -Pattern ([regex]'android:[A-Za-z]+="@android:string/') `
                -Extensions @('.xml') `
                -Roots @('app_v2/src/main/res/layout', 'app_v2/src/main/res/layout-land',
                         'app_v2/src/main/res/layout-sw480dp', 'app_v2/src/main/res/layout-sw720dp',
                         'app_v2/src/main/res/layout-w600dp') `
                -PathFilter 'app_v2/src/main/res/layout(-land|-sw480dp|-sw720dp|-w600dp)?/' `
                -FailMessage 'new user-visible label taken from a framework string resource (S3303). Use the app''s own key - @string/cancel, @string/ok, @string/back - because @android:string/* follows the system display locale and renders in the wrong language on a device whose app locale differs.'),
        # Same defect, code side, and the larger half of it: 168 of the 175 Kotlin references were
        # AlertDialog button labels. Both modules share one rule rather than splitting phone from
        # wear the way the animation and cancellation rules do, because the subject is a single
        # reference habit with one correct replacement in either module, and both baselines are 0 -
        # there is no per-module debt for a cleanup in one to hide behind.
        (New-RegexRule -Name 'framework-label-string-kt' `
                -Pattern ([regex]'android\.R\.string\.') `
                -Extensions @('.kt') `
                -Roots @('app_v2/src', 'wear/src') `
                -PathFilter '^(app_v2|wear)/src/' `
                -FailMessage 'new user-visible label taken from a framework string resource (S3303). Use the module''s own R.string key instead of android.R.string.*, which follows the system display locale and renders in the wrong language on a device whose app locale differs. The wear module carries its own copies - add the key there if it is missing, and classify the collision in scripts/quality/wear-mirrored-strings.psd1.'),
        # S2328: the caption/value split. The only structural rule in this family - see the four
        # forms in Get-CaptionValueSplitHits above and ADR-4 in PLAN/S2328 for why a regex cannot
        # do it. Roots add the values directory for the style form, and PathFilter pins that half to
        # themes.xml alone: applicability is tested before the file is read, so naming the file in
        # the filter keeps the rest of values/ out of the walk entirely rather than parsing and
        # discarding it. The baseline is NOT all one defect - it carries a known ambiguity class
        # (two co-equal data columns, e.g. a player's position|duration pair or a source -> target
        # row) that the four forms cannot tell from a caption and its value; those entries are named
        # in PLAN/S2328_bugfix-caption-value-opposite-edges/PHASE_05__caption-value-gate.md rather
        # than excluded by name, because excluding the file would also blind the rule to a real new
        # split appearing in it.
        [pscustomobject]@{
            Name         = 'caption-value-split'
            Extensions   = @('.xml')
            Roots        = @('app_v2/src/main/res/layout', 'app_v2/src/main/res/layout-land',
                             'app_v2/src/main/res/layout-sw480dp', 'app_v2/src/main/res/layout-sw720dp',
                             'app_v2/src/main/res/layout-w600dp', 'app_v2/src/main/res/values')
            PathFilter   = 'app_v2/src/main/res/(layout(-land|-sw480dp|-sw720dp|-w600dp)?/|values/themes\.xml$)'
            Baseline     = 'caption-value-split-baseline.txt'
            ExcludeNames = @()
            CountInText  = { param($t) Measure-CaptionValueSplit $t }
            LocateInText = { param($t) Find-CaptionValueSplitLines $t }
            FailMessage  = 'new caption/value split in a layout (S2328). The caption must hug its own text and carry no layout_weight; the value takes the remaining width and stays start-aligned, so the row''s slack falls after the value and never between the pair - see view_settings_selection_row.xml and docs/ARCHITECTURE.md "Caption and Value Proximity". A control at the row''s end (switch, chevron, icon button) is not a value and is not counted. If the new row is genuinely two co-equal data columns rather than a caption and its value, justify it in review instead of raising the baseline.'
        },
        # S3248: a toolbar declaration carries its visual decisions in a style, never inline. 27
        # declarations in 7 attribute fingerprints restated background, elevation and icon tint by
        # hand, and none of them named a style - so a theme change had to be applied 27 times and a
        # missed site looked exactly like a deliberate one. Both class names are judged: the app's
        # own StandardToolbar and the raw MaterialToolbar a new screen would otherwise reach for.
        # Walks the same layout roots as the caption rule above, so it costs one more regex pass
        # over text that is already loaded. Baseline 0 - the cure is one attribute.
        (New-RegexRule -Name 'styleless-toolbar' `
                -Pattern ([regex]'<(?:com\.google\.android\.material\.appbar\.MaterialToolbar|com\.sza\.fastmediasorter\.ui\.common\.widget\.StandardToolbar)\b(?:(?!style=)[^>])*>') `
                -Extensions @('.xml') `
                -Roots @('app_v2/src/main/res/layout', 'app_v2/src/main/res/layout-land',
                         'app_v2/src/main/res/layout-sw480dp', 'app_v2/src/main/res/layout-sw720dp',
                         'app_v2/src/main/res/layout-w600dp') `
                -PathFilter 'app_v2/src/main/res/layout(-land|-sw480dp|-sw720dp|-w600dp)?/' `
                -FailMessage ('toolbar declared without a style (CLAUDE.md Rule 19, S3248). Add ' +
                    'style="@style/Widget.FastMediaSorter.Toolbar.Primary" or ".Flat" and delete the inline ' +
                    'background, elevation, navigationIconTint and titleTextColor attributes it replaces - ' +
                    'docs/ui/PHONE_UI_COMPONENT_PATTERNS.md section 2.3 allows those two variants and no third.')),
        # S3249: the acceptance metric of the ActionBarView ticket. A bar's controls must all be
        # ActionBarView.Action records or MaterialButtons carrying the icon-button style; a raw
        # ImageButton inside a bar container is the second idiom that made one screen's touch
        # targets, ripples and disabled tints disagree with each other. Scoped by the enclosing
        # container's id (Get-RawImageButtonInBarHits) rather than by file, so a trailing action on
        # a list row is untouched by it.
        [pscustomobject]@{
            Name         = 'raw-imagebutton-in-bar'
            Extensions   = @('.xml')
            Roots        = @('app_v2/src/main/res/layout', 'app_v2/src/main/res/layout-land',
                             'app_v2/src/main/res/layout-sw480dp', 'app_v2/src/main/res/layout-sw720dp',
                             'app_v2/src/main/res/layout-w600dp')
            PathFilter   = 'app_v2/src/main/res/layout(-land|-sw480dp|-sw720dp|-w600dp)?/'
            Baseline     = 'raw-imagebutton-in-bar-baseline.txt'
            ExcludeNames = @()
            CountInText  = { param($t) Measure-RawImageButtonInBar $t }
            LocateInText = { param($t) Find-RawImageButtonInBarLines $t }
            FailMessage  = 'new raw <ImageButton> inside a bar container (S3249). A strip of controls is an ActionBarView - declare the control as an ActionBarView.Action record, or as a MaterialButton with style="@style/Widget.FastMediaSorter.Button.Icon" - so the bar owns the 48dp touch target, the ripple shape and the disabled tint once instead of each control restating them. See docs/ui/PHONE_UI_COMPONENT_PATTERNS.md section 2.3.'
        },
        [pscustomobject]@{
            Name        = 'unsafe-collect'
            Extensions  = @('.kt')
            Roots       = @('app_v2/src/main')
            PathFilter  = 'app_v2/src/main/'
            Baseline     = 'unsafe-collect-baseline.txt'
            ExcludeNames = @()
            CountInText = {
                param($t)
                $n = 0
                if (-not [string]::IsNullOrEmpty($t)) {
                    foreach ($m in $script:LaunchRx.Matches($t)) {
                        if (Test-UnsafeLaunchBody $t ($m.Index + $m.Length - 1)) { $n++ }
                    }
                }
                $n
            }
            FailMessage = 'new unsafe Flow collection introduced. Use collectOnLifecycle (utils/LifecycleExtensions.kt).'
        },
        (New-RegexRule -Name 'globalscope' `
                -Pattern ([regex]'\bGlobalScope\s*\.') `
                -FailMessage 'new GlobalScope usage introduced. Use viewModelScope, a lifecycle scope, or an injected CoroutineScope.'),
        (New-RegexRule -Name 'nontimber-log' `
                -Pattern ([regex]'\bLog\.(?:d|v|i|w|e|wtf)\s*\(|\bSystem\.(?:out|err)\b') `
                -FailMessage 'new non-Timber logging introduced. Use Timber.* (CLAUDE.md Rule 19).'),
        (New-RegexRule -Name 'stub-todo' `
                -Pattern ([regex]'\bTODO\s*\(|\bNotImplementedError\b') `
                -FailMessage 'new runtime stub introduced. A shipped TODO() throws at runtime - implement it or remove the path.'),
        (New-RegexRule -Name 'em-dash' `
                -Pattern ([regex]'[–-―]') `
                -FailMessage "new long dash introduced. Use a plain hyphen '-' instead."),
        (New-RegexRule -Name 'non-null-assertion' `
                -Pattern ([regex]'!!') `
                -FailMessage 'new !! assertion introduced. Use a safe call, a scope function, or an explicit null branch.'),
        # Three more count-ratchet gates that each walked the same tree on their own. They are
        # not part of the neuroslop umbrella, but they read the identical files with the
        # identical extension filter, so folding them costs one regex pass over loaded text.
        (New-RegexRule -Name 'deprecated-pm-flags' `
                -Pattern ([regex]'\b(getPackageInfo|getApplicationInfo|queryIntentActivities|resolveActivity)\s*\([^()\r\n]*,') `
                -Baseline 'deprecated-pm-flags-baseline.txt' `
                -ExcludeNames @('PackageManagerCompat.kt') `
                -FailMessage 'new raw-int PackageManager overload introduced. Use the *Compat helpers in util/PackageManagerCompat.kt (CLAUDE.md Rule 21).'),
        # S2094: the canonical toggle-row pattern (switch left, title, optional tooltip button,
        # subtitle) is a View-side composite element with no Compose counterpart. Wear companion's
        # WearWatchSettingsGroup.kt is the one place allowed to call Compose Switch directly - it IS
        # the canonical wrapper's Compose reproduction (SwitchRow) - so it is excluded the same way
        # PackageManagerCompat.kt is excluded above. Baseline seeded at 0: this file was the only raw
        # Compose Switch( call in app_v2 at authoring time, and this ticket brought it to canon.
        (New-RegexRule -Name 'compose-raw-switch' `
                -Pattern ([regex]'\bSwitch\s*\(') `
                -ExcludeNames @('WearWatchSettingsGroup.kt') `
                -FailMessage 'new raw Compose Switch( call outside the canonical row wrapper. Compose has no shared toggle-row element yet - wrap it the way WearWatchSettingsGroup.kt does (SwitchRow) or route through the View-side SettingsToggleRow pattern (CLAUDE.md Rule 33, S2094).'),
        # S1335: PermissionRegistryRepositoryImpl.resolveFlavorGate is the S0970 compile-time
        # whitelist map - the deliberate single place BuildConfig flavor reads are allowed in
        # src/main (reflection breaks under R8, see the function's own KDoc). Every optional,
        # flavor-gated PermissionEntry adds one arm here by design, so this file is excluded the
        # same way PackageManagerCompat.kt is excluded from deprecated-pm-flags above - matching
        # an existing precedent, not creating a new one.
        (New-RegexRule -Name 'flavor-flags' `
                -Pattern ([regex]'BuildConfig\.(?:SUPPORT_|ENABLE_|IS_)[A-Za-z0-9_]+') `
                -Roots @('app_v2/src/main/java') `
                -PathFilter 'app_v2/src/main/java/' `
                -Baseline 'flavor-flag-baseline.txt' `
                -ExcludeNames @('PermissionRegistryRepositoryImpl.kt') `
                -FailMessage 'new flavor flag read in src/main. Use an interface plus a flavor source set (CLAUDE.md Rule 14).'),
        # S1406: the player overflow menu reached PopupMenu's private mPopup field by reflection to
        # hang a long-press on the popup's internal ListView, inside a broad catch. Restricted-API
        # access that fails SILENTLY - an AppCompat update would drop the affordance with no signal,
        # and the catch guaranteed nobody would notice. Scoped to AppCompat menu internals on
        # purpose: DeliveredNativeLibraryLoader (BaseDexClassLoader) and the FastMediaSorterApp
        # settings dump reflect legitimately and must stay unflagged.
        (New-RegexRule -Name 'restricted-menu-reflection' `
                -Pattern ([regex]'(?:getDeclaredField|getDeclaredMethod)\s*\(\s*"(?:mPopup|mMenuItems|mMenuView|getListView)"|androidx\.appcompat\.view\.menu\.') `
                -Baseline 'restricted-menu-reflection-baseline.txt' `
                -FailMessage 'new reflection into AppCompat menu internals introduced. It breaks silently on an AppCompat update - model the affordance as a menu command instead (S1406).'),
        [pscustomobject]@{
            Name        = 'public-mutable-flow'
            Extensions  = @('.kt')
            Roots       = @('app_v2/src/main')
            PathFilter  = 'app_v2/src/main/'
            Baseline     = 'public-mutable-flow-baseline.txt'
            ExcludeNames = @()
            CountInText = { param($t) Measure-PublicMutableFlowText $t }
            FailMessage = 'new public mutable reactive state introduced. Keep the Mutable* backing field private and expose the read-only view.'
        },
        [pscustomobject]@{
            Name         = 'window-insets'
            Extensions   = @('.kt')
            Roots        = @('app_v2/src/main')
            PathFilter   = 'app_v2/src/main/'
            Baseline     = 'window-insets-baseline.txt'
            ExcludeNames = @()
            CountInText  = { param($t) Measure-WindowInsetsText $t }
            LocateInText = { param($t) Find-WindowInsetsLines $t }
            FailMessage  = 'new window-inset handling that ignores displayCutout. Pad for systemBars OR displayCutout, or call View.applySystemBarInsetPadding() from utils/ViewExtensions.kt (CLAUDE.md Rule 17).'
        },
        [pscustomobject]@{
            Name         = 'swallowed-cancellation'
            Extensions   = @('.kt')
            Roots        = @('app_v2/src/main')
            PathFilter   = 'app_v2/src/main/'
            Baseline     = 'swallowed-cancellation-baseline.txt'
            ExcludeNames = @()
            CountInText  = { param($t) Measure-SwallowedCancellationText $t }
            LocateInText = { param($t) Find-SwallowedCancellationLines $t }
            FailMessage  = 'new catch in coroutine code that swallows CancellationException - a broad arm, or an IllegalStateException/RuntimeException arm, both of which are its supertypes. Add `catch (e: CancellationException) { throw e }` as the first arm of the chain (S1363/S1889).'
        },
        # S1910: the watch module needs its OWN entry and its OWN baseline, not a wider Roots on the
        # rule above. One shared integer would let a regression in one module hide behind a cleanup in
        # the other and still read as at-or-below baseline, which is the one thing a ratchet exists to
        # prevent. Seeded at the measured 29 after the five reachable sites were fixed (34 before);
        # like every ratchet here it may fall and never rise.
        [pscustomobject]@{
            Name         = 'swallowed-cancellation-wear'
            Extensions   = @('.kt')
            Roots        = @('wear/src')
            PathFilter   = 'wear/src/'
            Baseline     = 'swallowed-cancellation-wear-baseline.txt'
            ExcludeNames = @()
            CountInText  = { param($t) Measure-SwallowedCancellationText $t }
            LocateInText = { param($t) Find-SwallowedCancellationLines $t }
            FailMessage  = 'new catch in wear coroutine code that swallows CancellationException - a broad arm, or an IllegalStateException/RuntimeException arm, both of which are its supertypes. Add `catch (e: CancellationException) { throw e }` as the first arm of the chain (S1363/S1889/S1910).'
        },
        # S2250: phone and Wear counts stay separate. A new animator in one module cannot hide
        # behind a cleanup in the other, and the fail message names the policy the new site must use.
        [pscustomobject]@{
            Name         = 'unpoliced-animation'
            Extensions   = @('.kt')
            Roots        = @('app_v2/src/main')
            PathFilter   = 'app_v2/src/main/'
            Baseline     = 'unpoliced-animation-baseline.txt'
            ExcludeNames = @()
            CountInText  = { param($t) Measure-UnpolicedAnimationText $t }
            LocateInText = { param($t) Find-UnpolicedAnimationLines $t }
            FailMessage  = 'new animation primitive in the phone app (S2250). Re-judge the site and consult AnimationPolicy before creating the transition or animator.'
        },
        [pscustomobject]@{
            Name         = 'unpoliced-animation-wear'
            Extensions   = @('.kt')
            Roots        = @('wear/src')
            PathFilter   = 'wear/src/'
            Baseline     = 'unpoliced-animation-wear-baseline.txt'
            ExcludeNames = @()
            CountInText  = { param($t) Measure-UnpolicedAnimationText $t }
            LocateInText = { param($t) Find-UnpolicedAnimationLines $t }
            FailMessage  = 'new animation primitive in Wear (S2250). Re-judge the site and consult VideoPlayerUiState.animationsDisabled before creating it.'
        },
        # S2748: the test source sets of BOTH modules share one entry, unlike the phone/wear split
        # above. That split exists because a shipped-code regression in one module must not hide
        # behind a cleanup in the other; here the subject is a test-authoring habit that travels
        # with whoever writes the test, and the wear side contributes two sites, so a second
        # baseline would carry more bookkeeping than signal.
        [pscustomobject]@{
            Name         = 'test-unjoined-scope'
            Extensions   = @('.kt')
            Roots        = @('app_v2/src/test', 'wear/src/test')
            PathFilter   = '^(?:app_v2|wear)/src/test/'
            Baseline     = 'test-unjoined-scope-baseline.txt'
            ExcludeNames = @()
            CountInText  = { param($t) Measure-TestUnjoinedScopeText $t }
            LocateInText = { param($t) Find-TestUnjoinedScopeLines $t }
            FailMessage  = 'new CoroutineScope in a test source set that is not on the test scheduler (S2748). Build it as CoroutineScope(UnconfinedTestDispatcher(testScheduler)) inside runTest, or join it in @After with runBlocking { scope.coroutineContext.job.cancelAndJoin() } - a scope neither joined nor scheduler-bound outlives the test body and charges its exception to an unrelated test (S2746).'
        },
        [pscustomobject]@{
            Name         = 'activity-logic'
            Extensions   = @('.kt')
            # Every source set, not just main - ScreenCaptureConsentActivity lives in
            # app_v2/src/screenCapture/. Test source sets are out: the rule judges shipped hosts.
            Roots        = @('app_v2/src')
            PathFilter   = '^app_v2/src/(?!androidTest/|test|benchmark/)'
            Baseline     = 'activity-logic-baseline.txt'
            ExcludeNames = @()
            CountInText  = { param($t) Measure-ActivityLogicText $t }
            LocateInText = { param($t) Find-ActivityLogicLines $t }
            FailMessage  = 'new domain-layer field injection in an Activity. Move the dependency into a ViewModel or a Manager the host delegates to (CLAUDE.md Rule 3).'
        },
        [pscustomobject]@{
            Name         = 'untracked-dialog'
            Extensions   = @('.kt')
            # Every shipped source set, like activity-logic above: the leak reaches launcherEnabled,
            # noLegal and screenCapture, and a src/main-only filter would call those three clean.
            Roots        = @('app_v2/src')
            PathFilter   = '^app_v2/src/(?!androidTest/|test|benchmark/)'
            Baseline     = 'untracked-dialog-baseline.txt'
            ExcludeNames = @()
            CountInText  = { param($t) Measure-UntrackedDialogText $t }
            LocateInText = { param($t) Find-UntrackedDialogLines $t }
            FailMessage  = 'new dialog shown with a bare .show(). Show it with showBoundTo(owner) from util/LifecycleDialogExt.kt so the host lifecycle dismisses it (S1456).'
        },
        # Reuses the app_v2/src root the rule above already walks, so this costs one regex pass over
        # text that is loaded anyway rather than a second walk of the resource tree.
        [pscustomobject]@{
            Name         = 'string-quote-escaping'
            Extensions   = @('.xml')
            Roots        = @('app_v2/src')
            PathFilter   = '^app_v2/src/[^/]+/res/values[^/]*/'
            Baseline     = 'string-quote-escaping-baseline.txt'
            ExcludeNames = @()
            CountInText  = { param($t) Measure-InvisibleResourceQuotes $t }
            LocateInText = { param($t) Find-InvisibleResourceQuoteLines $t }
            FailMessage  = 'new build-invisible double quote in a string resource. AAPT2 drops both a bare " and &quot; - write \" instead (S1567).'
        },
        # Same walk and same file set as the quote rule above - the second silent way a character is
        # deleted between the resource file and the screen.
        [pscustomobject]@{
            Name         = 'string-lone-backslash'
            Extensions   = @('.xml')
            Roots        = @('app_v2/src')
            PathFilter   = '^app_v2/src/[^/]+/res/values[^/]*/'
            Baseline     = 'string-lone-backslash-baseline.txt'
            ExcludeNames = @()
            CountInText  = { param($t) Measure-LoneResourceBackslashes $t }
            LocateInText = { param($t) Find-LoneResourceBackslashLines $t }
            FailMessage  = 'new lone backslash in a string resource. AAPT2 reads it as an escape introducer and drops the character - write \\ instead (S1586).'
        },
        # S1786: Rule 6 architectural class suffix naming ratchet
        [pscustomobject]@{
            Name         = 'class-architecture-naming'
            Extensions   = @('.kt')
            Roots        = @('app_v2/src', 'wear/src')
            # S1863: the S1742 excuse below spares the test CLASS but not the fixtures declared inside
            # it, so a fake named after the interface it stands in for - `FakeSenderRepository` in a
            # `domain/usecase` test - still failed the delta. That is the same defect one level down:
            # a fixture is named after what it fakes, and no architectural suffix fits it. The third
            # instance of this false positive in one rule (S1742, S1797, this one), so the whole test
            # source set leaves the rule rather than the exclusion list growing a third time.
            PathFilter   = '^(?!.*[\\/]src[\\/](?:test|androidTest)[\\/]).*[\\/](domain[\\/]usecase|data[\\/]repository)[\\/]'
            Baseline     = 'class-architecture-naming-baseline.txt'
            ExcludeNames = @()
            CountInText  = {
                param($t)
                if ([string]::IsNullOrWhiteSpace($t)) { return 0 }
                $isUseCase = $t -match 'package\s+.*\.domain\.usecase'
                $isRepo = $t -match 'package\s+.*\.data\.repository'
                if (-not $isUseCase -and -not $isRepo) { return 0 }
                
                $count = 0
                foreach ($line in ($t -split "`n")) {
                    $trimmed = $line.Trim()
                    if ($trimmed.StartsWith('//') -or $trimmed.StartsWith('/*') -or $trimmed.StartsWith('*')) { continue }
                    # S1884: Rule 6 governs the file's architectural type, not the result holders nested
                    # inside it. A nested `sealed interface Outcome` is a member of an already correctly
                    # named *UseCase - the shipped SendStreamToWatchUseCase.Outcome is the convention, and
                    # it passes only because the baseline absorbed it. Charging the next one is what
                    # produced `SendFileToWatchOutcomeUseCase.OpenedUseCase`: an architectural suffix
                    # stamped onto a type the rule was never about. Indentation is the discriminator - a
                    # top-level declaration starts at column 0. Third instance of this false positive in
                    # one rule (S1742, S1797, this one), so it is fixed generally rather than excused again.
                    if ($line -match '^\s') { continue }
                    if ($trimmed -match '^(?:public\s+|internal\s+|private\s+|open\s+|abstract\s+|sealed\s+|data\s+)*(?:class|interface)\s+([A-Za-z0-9_]+)') {
                        $name = $Matches[1]
                        # S1742: a test class is named after the thing it tests, so `FooUseCaseTest` is
                        # correct naming rather than a violation of it. Without this the rule taxed every
                        # new test in these two packages - the baseline had silently absorbed ~94 of them,
                        # so the next test file always failed the delta. A gate that charges for writing a
                        # test is worse than no gate.
                        if ($name -match '(Test|Tests)$') { continue }
                        if ($isUseCase -and $name -notmatch '(UseCase|UseCases|Factory|Contract)$') {
                            $count++
                        }
                        # S1797: `Values` is the read-result holder every `data/repository/settings/*Store`
                        # nests by contract, so the rule taxed the section-store pattern it should endorse -
                        # 12 of them sat absorbed in the baseline, which made the next new store fail the
                        # delta for following the convention. Same shape as the `Test` excuse above.
                        elseif ($isRepo -and $name -notmatch '(Repository|RepositoryImpl|Module|Factory|Source|Store|Mapper|Parser|Utils|Coordinator|Values)$') {
                            $count++
                        }
                    }
                }
                return $count
            }
            FailMessage  = 'new class or interface in domain/usecase or data/repository violates Rule 6 naming suffix (expected *UseCase or *Repository / *RepositoryImpl).'
        },
        # S2133: Wear single declared toggle form. Refuses ToggleChip, Checkbox and Switch anywhere in
        # wear/src/main outside ui/common.
        (New-RegexRule -Name 'wear-raw-toggle' `
                -Pattern ([regex]'\b(ToggleChip|Checkbox|Switch)\b') `
                -Roots @('wear/src/main') `
                -PathFilter '^wear/src/main/java/com/sza/fastmediasorter/wear/ui/(?!common/).*' `
                -Baseline 'wear-raw-toggle-baseline.txt' `
                -FailMessage 'new raw toggle (ToggleChip, Checkbox, Switch) in wear/src/main outside ui/common (S2133). Use StandardWearToggleChip from ui/common instead.'),
        # S2243: AppSettings field persistence completeness gate.
        # Compares every field in AppSettings.kt against the combined text of
        # data/repository/settings/*.kt and SettingsRepositoryImpl.kt.
        [pscustomobject]@{
            Name         = 'appsettings-persistence'
            Extensions   = @('.kt')
            Roots        = @('app_v2/src/main/java/com/sza/fastmediasorter/domain/model')
            PathFilter   = 'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings\.kt$'
            Baseline     = 'appsettings-persistence-baseline.txt'
            ExcludeNames = @()
            CountInText  = {
                param($t)
                if ([string]::IsNullOrWhiteSpace($t)) { return 0 }
                $fields = @()
                foreach ($line in ($t -split "`r?`n")) {
                    if ($line -match '^\s+val\s+(\w+)\s*:') {
                        $fields += $Matches[1]
                    }
                }
                if ($fields.Count -eq 0) { return 0 }
                
                $repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
                $storesDir = Join-Path $repoRoot 'app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings'
                $implFile = Join-Path $repoRoot 'app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt'
                
                $storeTexts = @()
                if (Test-Path $storesDir) {
                    $storeTexts += Get-ChildItem $storesDir -Filter *.kt | ForEach-Object { Get-Content $_.FullName -Raw }
                }
                if (Test-Path $implFile) {
                    $storeTexts += Get-Content $implFile -Raw
                }
                $combinedText = $storeTexts -join "`n"
                
                $missingCount = 0
                foreach ($f in $fields) {
                    if ($combinedText -notmatch [regex]::Escape($f)) {
                        $missingCount++
                    }
                }
                return $missingCount
            }
            FailMessage  = 'new field added to AppSettings without persistence in settings stores or SettingsRepositoryImpl (S2243).'
        },
        # S2326: the repository-script layer, judged for literal drive paths. This is the only rule
        # here that walks scripts rather than app sources, so it names its own roots and extensions.
        # Two files are spared by name rather than by the path filter:
        #   - project-paths.ps1 IS the resolver, and its sink table is the deliberate single place a
        #     machine default is written down - a default that lives nowhere would stop delivering
        #     artifacts on the machine that has those directories;
        #   - test-agent-lock-queue.ps1 feeds `Z:\no-such-transcript\missing.jsonl` to the liveness
        #     checker on purpose. Strategic section 2 lists synthetic fixtures as a non-goal: the
        #     path has to be literal, because what it tests is the handling of a path that is not
        #     there. It sits in scripts/utils/ rather than a *.tests/ directory, so the path filter
        #     below does not reach it.
        [pscustomobject]@{
            Name         = 'hardcoded-drive-path'
            Extensions   = @('.ps1', '.psm1', '.cmd', '.bat', '.sh')
            Roots        = @('scripts', 'maestro', 'dev', 'a.ps1')
            PathFilter   = '^(?!dev/archive/)(?!.*\.tests/)(?!.*/\.venv/)(?:scripts/|maestro/|dev/|a\.ps1$)'
            Baseline     = 'hardcoded-drive-path-baseline.txt'
            ExcludeNames = @('project-paths.ps1', 'test-agent-lock-queue.ps1')
            CountInText  = { param($t) Measure-HardcodedDrivePathText $t }
            LocateInText = { param($t) Find-HardcodedDrivePathLines $t }
            FailMessage  = 'new literal drive path in a repository script. It binds the script to one machine, so moving the tree to another drive letter or directory name breaks it silently. Dot-source scripts/utils/project-paths.ps1 and ask for the path by role - Get-ProjectRoot, Get-ProjectPath, Get-SiblingPath, Get-ToolPath, Get-ArtifactSink - or override through the matching FMS_* environment variable (S2326).'
        },
        # S2332: a build path that writes the delivery block itself instead of calling the script that
        # holds it. S1707 extracted that block into copy-to-drive.ps1 precisely so it would be written
        # once, and then nothing was converted: on 2026-09-02 it was still hand-written in 26 places
        # across 25 builders while the shared script had two callers. Without a gate the next builder
        # writes it again, which is what happened after S1707.
        #
        # scripts/utils is outside the path filter rather than listed in ExcludeNames: that directory is
        # where the shared implementation lives, and naming the two files by hand would let a THIRD
        # hand-written copy appear beside them unjudged. Test directories are filtered out for the same
        # reason project-paths.tests exercises Get-ArtifactSink on purpose.
        #
        # dev/ is in scope because the builder the owner actually runs lives there, not under
        # scripts/builders/ (S2337). While the scope was the two scripts/ directories alone, a zero
        # baseline meant "zero among the files walked" rather than "zero in the tree": dev/build-with-
        # version.ps1 kept the hand-written block through S2332's whole conversion, and the launcher
        # dev/build-with-version.bat invokes it. The directory is named rather than the file, for the
        # same reason scripts/utils is: a by-name list lets the next copy appear beside it unjudged.
        # dev/archive/ is excluded as a read-only zone, matching hardcoded-drive-path above.
        [pscustomobject]@{
            Name         = 'inline-delivery-block'
            Extensions   = @('.ps1', '.psm1')
            Roots        = @('scripts', 'dev')
            PathFilter   = '^(?!dev/archive/)(?!.*\.tests/)(?:scripts/(builders|release)/|dev/)'
            Baseline     = 'inline-delivery-block-baseline.txt'
            ExcludeNames = @()
            CountInText  = { param($t) Measure-InlineDeliveryBlockText $t }
            LocateInText = { param($t) Find-InlineDeliveryBlockLines $t }
            FailMessage  = 'a build path resolving a delivery sink or the archiver itself instead of calling the one script that holds the delivery block. Repeating it means the next change to delivery is either made 26 times or diverges - which is how the watch shipped while its Drive copy stayed a month stale and looked current (S1707). Call scripts/utils/publish-artifact.ps1 with -Path and -Name; it covers both sinks, takes several artifacts for one archive, and skips a sink it cannot reach without failing the build. Use -NoZip / -NoCommander for a path that legitimately delivers less (S2332).'
        },
        # S2466: direct ScalingLazyColumn or rememberScalingLazyListState in the wear module.
        # WearListColumn and rememberWearListState enforce consistent round-screen top-edge
        # placement (initialCenterItemIndex = 0, autoCentering = null) and content padding
        # across all wear screens, dialogs, sheets, and overlays.
        [pscustomobject]@{
            Name         = 'wear-list-start'
            Extensions   = @('.kt')
            Roots        = @('wear/src/main')
            PathFilter   = '^wear/src/main/'
            Baseline     = 'wear-list-start-baseline.txt'
            ExcludeNames = @('WearListColumn.kt')
            CountInText  = { param($t) Measure-WearListStartText $t }
            LocateInText = { param($t) Find-WearListStartLines $t }
            FailMessage  = 'direct ScalingLazyColumn or rememberScalingLazyListState in wear module. Use WearListColumn and rememberWearListState to enforce consistent round-screen top-edge placement and content padding (S2466).'
        },
        # S3257: a literal font size in the wear module. docs/ui/WEAR_UI_COMPONENT_PATTERNS.md section
        # 1.3 has forbidden inline `fontSize = ..sp` since S3232 and five screens carried 24 of them
        # anyway, because a written rule cannot refuse the sixth screen that copies its neighbour.
        #
        # The pattern requires DIGITS before `.sp`, which is what separates a literal from the three
        # legitimate uses that survive: WearCaptionText applies `.sp` to a computed step of its own
        # shrink scale, ThumbnailCell converts that scale's floor into dp, and CalculatorHistoryPage
        # applies the owner's stored history size. None of the three names a number at the call site.
        #
        # WearTypography.kt is excluded by name for the reason PackageManagerCompat is: it IS the seam
        # this rule routes callers towards, and the one place the app's own focal size is written down.
        (New-RegexRule -Name 'wear-inline-font-size' `
                -Pattern ([regex]'\b\d+(?:\.\d+)?f?\.sp\b') `
                -Roots @('wear/src/main') `
                -PathFilter '^wear/src/main/' `
                -Baseline 'wear-inline-font-size-baseline.txt' `
                -ExcludeNames @('WearTypography.kt') `
                -FailMessage 'literal font size (`NN.sp`) in wear/src/main. docs/ui/WEAR_UI_COMPONENT_PATTERNS.md section 1.3 requires text size to resolve through MaterialTheme.typography - take the nearest token from the table there, and if the screen genuinely needs a size the scale has no name for, re-size a token in ui/theme/WearTypography.kt instead of writing the number at the call site (S3257).'),
        # S3247: an adapter painting the surface a row's state-list drawable owns - the row root
        # itself, or the CardView that covers it. `item_focus_selector` carries the pressed, focused,
        # hovered and activated layers, and the focus ring is a stroke inside two of them, so a
        # per-bind colour swap switches the ring off for exactly the rows being selected. Selection is
        # `isSelected` + `isActivated` and nothing else (docs/ui/PHONE_UI_COMPONENT_PATTERNS.md 2.2).
        #
        # The pattern names the receiver rather than the method, because `setBackgroundColor` on a
        # thumbnail child is legitimate - AdapterThumbnailLoader letterboxes with it - and a rule that
        # counted the method alone would have to exclude the file that routes callers to it.
        (New-RegexRule -Name 'adapter-root-background' `
                -Pattern ([regex]'(?:binding\.root|itemView|rootView|\brootLayout|\bcvCard|binding\.cvCard)\.(?:setBackgroundColor|setBackgroundResource|setCardBackgroundColor)\(') `
                -PathFilter '^app_v2/src/main/java/.*Adapter\.kt$' `
                -Baseline 'adapter-root-background-baseline.txt' `
                -FailMessage 'an adapter painting the row root or its covering card. The row''s own state-list drawable (item_focus_selector, applied by Widget.FastMediaSorter.Item.Row) owns selection, focus, press and hover; a setBackgroundColor over it destroys the focus ring on the selected rows. Set isSelected and isActivated instead, and put a semantic tint (unavailable, error) on a child surface inside the row - docs/ui/PHONE_UI_COMPONENT_PATTERNS.md section 2.2, S3247.'),
        # S3243: a raw AlertDialog.Builder does not read materialAlertDialogTheme, so its buttons come
        # out as the stock AppCompat pair instead of the DialogConfirm/DialogCancel taxonomy - and the
        # construction site has no keyboard contract. Every call site migrated to AppDialog or
        # MaterialAlertDialogBuilder first; the gate landed last so it never went red. LifecycleDialogExt
        # is excluded: it is the showBoundTo/showBoundToHost seam the sanctioned builders route through.
        (New-RegexRule -Name 'alert-dialog-builder' `
                -Pattern ([regex]'AlertDialog\.Builder\s*\(') `
                -Baseline 'alert-dialog-builder-baseline.txt' `
                -ExcludeNames @('LifecycleDialogExt.kt') `
                -FailMessage 'a raw AlertDialog.Builder call site. Build the dialog through the AppDialog factory or MaterialAlertDialogBuilder so it reads materialAlertDialogTheme and carries the keyboard contract (S3243, docs/ui/PHONE_UI_COMPONENT_PATTERNS.md section 6).'),
        # S3255: docs/ui/PHONE_UI_COMPONENT_PATTERNS.md section 1.3 - a layout never carries
        # `android:textSize`. It carries android:textAppearance="@style/TextAppearance.FastMediaSorter.<Role>",
        # so one type-scale change reaches every surface. The dimension counts the attribute in every
        # form, because a @dimen reference still forks the scale the appearance system owns.
        (New-RegexRule -Name 'layout-text-size' `
                -Pattern ([regex]'android:textSize\s*=') `
                -Extensions @('.xml') `
                -Roots @('app_v2/src/main/res/layout', 'app_v2/src/main/res/layout-land',
                         'app_v2/src/main/res/layout-sw480dp', 'app_v2/src/main/res/layout-sw720dp',
                         'app_v2/src/main/res/layout-w600dp') `
                -PathFilter 'app_v2/src/main/res/layout(-land|-sw480dp|-sw720dp|-w600dp)?/' `
                -FailMessage 'new android:textSize in a layout (S3255). Use android:textAppearance="@style/TextAppearance.FastMediaSorter.<Role>" (docs/ui/PHONE_UI_COMPONENT_PATTERNS.md section 1.3) - the role is chosen by what the text means, not by how large it should look.'),
        # S3255: section 1.3 - the five Material 2 bridge attributes resolve to different metrics than
        # the Material 3 scale the module standardised on, so two rows meant to match do not. The
        # pattern pins the `?attr/` reference spelling a layout uses; a role name from the
        # TextAppearance.FastMediaSorter scale is the cure.
        (New-RegexRule -Name 'layout-m2-tokens' `
                -Pattern ([regex]'\?attr/textAppearance(?:Caption|Body1|Body2|Subtitle1|Subtitle2)\b') `
                -Extensions @('.xml') `
                -Roots @('app_v2/src/main/res/layout', 'app_v2/src/main/res/layout-land',
                         'app_v2/src/main/res/layout-sw480dp', 'app_v2/src/main/res/layout-sw720dp',
                         'app_v2/src/main/res/layout-w600dp') `
                -PathFilter 'app_v2/src/main/res/layout(-land|-sw480dp|-sw720dp|-w600dp)?/' `
                -FailMessage 'a Material 2 bridge textAppearance token in a layout (S3255). Pick the nearest TextAppearance.FastMediaSorter role (docs/ui/PHONE_UI_COMPONENT_PATTERNS.md section 1.3) - textAppearanceCaption and BodySmall resolve to different metrics and cannot share one visual family.'),
        # S3255: section 1.4 - elevation resolves through the @dimen/elevation_<role> token set; a raw
        # dp literal is a number nobody can change in one place. Structural "0dp" stays exempt the same
        # way layout-hardcoded-dimens spares it.
        (New-RegexRule -Name 'layout-elevation-literal' `
                -Pattern ([regex]'(?:android|app):elevation="-?(?!0dp")\d+(?:\.\d+)?dp"') `
                -Extensions @('.xml') `
                -Roots @('app_v2/src/main/res/layout', 'app_v2/src/main/res/layout-land',
                         'app_v2/src/main/res/layout-sw480dp', 'app_v2/src/main/res/layout-sw720dp',
                         'app_v2/src/main/res/layout-w600dp') `
                -PathFilter 'app_v2/src/main/res/layout(-land|-sw480dp|-sw720dp|-w600dp)?/' `
                -FailMessage 'a raw dp literal on an elevation attribute (S3255). Reference the @dimen/elevation_<role> token for that role (docs/ui/PHONE_UI_COMPONENT_PATTERNS.md section 1.4) - a new value needs a new role, not a new number.'),
        # S3255: section 2.1 window sizing - one DialogWindowSizer resolves the dialog window width
        # from @dimen/dialog_min_width and @dimen/dialog_max_width against the configuration; a
        # hand-rolled setLayout computes its own width and consults neither. The sizer itself is
        # excluded by name, the same way PackageManagerCompat is: it IS the seam this rule routes
        # callers towards.
        (New-RegexRule -Name 'dialog-window-layout' `
                -Pattern ([regex]'\bwindow\??\.setLayout\s*\(') `
                -Baseline 'dialog-window-layout-baseline.txt' `
                -ExcludeNames @('DialogWindowSizer.kt') `
                -FailMessage 'a hand-rolled window.setLayout sizing a dialog (S3255). Delegate to DialogWindowSizer.applyTo so the width resolves from @dimen/dialog_min_width / dialog_max_width against the current configuration (docs/ui/PHONE_UI_COMPONENT_PATTERNS.md section 2.1).'),
        # S3255: section 2.2 - adapter item thumbnails route through MediaItemThumbnailBinder, which
        # pins the override size, cache strategy, signature and placeholder per role. A raw Glide.with
        # in an adapter configures per call site instead of per role - the five-way
        # diskCacheStrategy split this section retired. The two sanctioned seams are excluded by name
        # even though neither matches the Adapter filename filter, so a rename cannot silently move a
        # raw call inside a gate-blind file.
        (New-RegexRule -Name 'glide-adapter-entry' `
                -Pattern ([regex]'\bGlide\.with\s*\(') `
                -PathFilter '^app_v2/src/main/java/.*/ui/.*Adapter\.kt$' `
                -Baseline 'glide-adapter-entry-baseline.txt' `
                -ExcludeNames @('MediaItemThumbnailBinder.kt', 'MediaItemView.kt') `
                -FailMessage 'a raw Glide.with in an adapter (S3255). Inject MediaItemThumbnailBinder and bind by ThumbnailRole, so size, cache strategy, signature and placeholder are decided per role rather than per call site (docs/ui/PHONE_UI_COMPONENT_PATTERNS.md section 2.2).'),
        # S3255: section 4 insets - the transitive check, last of the family because it is only
        # meaningful once AppDialog and BaseAppBottomSheet exist. Predicate in
        # Measure-UnwiredDialogInsetText above.
        [pscustomobject]@{
            Name         = 'transitive-dialog-insets'
            Extensions   = @('.kt')
            Roots        = @('app_v2/src/main')
            PathFilter   = 'app_v2/src/main/'
            Baseline     = 'transitive-dialog-insets-baseline.txt'
            ExcludeNames = @()
            CountInText  = { param($t) Measure-UnwiredDialogInsetText $t }
            FailMessage  = 'a DialogFragment / BottomSheetDialogFragment subclass with no inset seam in its file (S3255). Show it through AppDialog, inherit BaseAppBottomSheet, or call applyDialogInsets / applySystemBarInsetPadding - a cutout can cross a dialog positioned near the top edge (docs/ui/PHONE_UI_COMPONENT_PATTERNS.md section 4).'
        },
        # S3255: docs/ui/PHONE_UI_COMPONENT_PATTERNS.md section 5 - prefer having no counterpart to
        # having a stale one. A landscape layout whose portrait original is gone is a second file
        # every future edit must remember for nothing; the parity rule refuses to create one. The
        # count is a property of the FILE PAIR, not of the judged text alone, so this rule reads the
        # repo-relative path Invoke-SourceScan passes as a second CountInText argument and tests the
        # counterpart on disk.
        [pscustomobject]@{
            Name         = 'landscape-orphan-layout'
            Extensions   = @('.xml')
            Roots        = @('app_v2/src/main/res/layout-land')
            PathFilter   = 'app_v2/src/main/res/layout-land/'
            Baseline     = 'landscape-orphan-layout-baseline.txt'
            ExcludeNames = @()
            CountInText  = { param($t, $path)
                if ([string]::IsNullOrEmpty($t) -or [string]::IsNullOrEmpty($path)) { return 0 }
                $portrait = ($path -replace '/res/layout-land/', '/res/layout/')
                if ($portrait -eq $path) { return 0 }
                $repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
                if (Test-Path -LiteralPath (Join-Path $repoRoot $portrait)) { return 0 }
                return 1
            }
            FailMessage  = 'a landscape layout with no portrait counterpart (S3255). Delete the orphan or restore the original - docs/ui/PHONE_UI_COMPONENT_PATTERNS.md section 5.1 prefers no counterpart to a stale one, and a counterpart that lost its original is the stale case by definition.'
        },
        # S3255: section 3.2 - nextFocus* declared in res/layout/ is declared in res/layout-land/ too;
        # a D-pad chain that exists in portrait and breaks rotated is the regression the user meets by
        # turning the phone. Judged from the PORTRAIT file against its existing counterpart, counting
        # the attribute names the counterpart lacks, so each replication lowers the baseline by one.
        # A portrait file with no counterpart is the orphan rule's subject above, not this one's.
        [pscustomobject]@{
            Name         = 'landscape-focus-parity'
            Extensions   = @('.xml')
            Roots        = @('app_v2/src/main/res/layout')
            PathFilter   = 'app_v2/src/main/res/layout/'
            Baseline     = 'landscape-focus-parity-baseline.txt'
            ExcludeNames = @()
            CountInText  = { param($t, $path)
                if ([string]::IsNullOrEmpty($t) -or [string]::IsNullOrEmpty($path)) { return 0 }
                $focusRx = [regex]'(?:android:)?(nextFocus\w+)\s*='
                $portraitAttrs = @($focusRx.Matches($t) | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
                if ($portraitAttrs.Count -eq 0) { return 0 }
                $landPath = ($path -replace '/res/layout/', '/res/layout-land/')
                if ($landPath -eq $path) { return 0 }
                $repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
                $landFile = Join-Path $repoRoot $landPath
                if (-not (Test-Path -LiteralPath $landFile)) { return 0 }
                $landText = ''
                try { $landText = Get-Content -LiteralPath $landFile -Raw -ErrorAction Stop } catch { return 0 }
                $landAttrs = @($focusRx.Matches($landText) | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
                return @($portraitAttrs | Where-Object { $_ -notin $landAttrs }).Count
            }
            FailMessage  = 'a portrait layout declaring nextFocus* its landscape counterpart lacks (S3255). Replicate the focus chain in res/layout-land/ - docs/ui/PHONE_UI_COMPONENT_PATTERNS.md section 3.2: a chain that exists in portrait and not rotated stops D-pad navigation dead.'
        }
    )
}

<#
.SYNOPSIS
    Turn the rules into Invoke-SourceScan matchers.
#>
function ConvertTo-SourceMatchers {
    [CmdletBinding()]
    param([Parameter(Mandatory)][object[]]$Rules)
    foreach ($r in $Rules) {
        $args = @{
            Name         = $r.Name
            Extensions   = $r.Extensions
            CountInText  = $r.CountInText
            PathFilter   = $r.PathFilter
            ExcludeNames = $r.ExcludeNames
        }
        # Optional: a rule that can name its hit lines gets them printed under -List.
        # Without this the scan has no locator and -List reports the count only.
        if ($r.PSObject.Properties.Name -contains 'LocateInText' -and $r.LocateInText) {
            $args.LocateInText = $r.LocateInText
        }
        New-SourceMatcher @args
    }
}
