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
# core/util/CoroutineExt.kt offers the second sanctioned form of the same fix. Its KDoc requires
# the call to be the FIRST statement of the block, so anything after a statement has already run
# error-path work on a cancellation and is still a violation.
$script:RethrowHelperRx = [regex]'\brethrowIfCancellation\s*\('

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
        $firstStatement = ''
        for ($j = $i + 1; $j -lt $lines.Count; $j++) {
            $cand = $lines[$j]
            if ($cand.Trim().Length -eq 0) { continue }
            if ($cand.Trim() -match '^(//|/\*|\*)') { continue }
            $firstStatement = $cand
            break
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
            PathFilter   = '[\\/](domain[\\/]usecase|data[\\/]repository)[\\/]'
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
