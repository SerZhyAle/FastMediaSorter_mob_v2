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
$script:UnpolicedAnimationRx = [regex]'\boverridePendingTransition\b|\boverrideActivityTransition\b|\bbeginDelayedTransition\b|\bLayoutTransition\b|\bsetPageTransformer\b|\bObjectAnimator\b|\bValueAnimator\b|\bAnimatorSet\b|\bAnimationUtils\.loadAnimation\b|\bwithCrossFade\s*\(\s*(?!0(?:\.0+)?(?:[fFdD])?\s*[,)])|\bAnimatedVisibility\b'

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
                -FailMessage 'new Compose island in app_v2 (CLAUDE.md Rule 32). app_v2 is View-based: build the screen in XML + ViewBinding. Removing an island lowers this baseline; raising it is a boundary decision, not a build fix.'),
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
                -Pattern ([regex]'[–—―]') `
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
                -FailMessage 'new raw toggle (ToggleChip, Checkbox, Switch) in wear/src/main outside ui/common (S2133). Use WearSettingsToggleCell from ui/common instead.'),
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
