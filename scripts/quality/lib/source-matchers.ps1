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
function Test-UnsafeLaunchBody([string]$text, [int]$openBrace) {
    $depth = 0
    $end = -1
    for ($i = $openBrace; $i -lt $text.Length; $i++) {
        $c = $text[$i]
        if ($c -eq '{') { $depth++ }
        elseif ($c -eq '}') { $depth--; if ($depth -eq 0) { $end = $i; break } }
    }
    if ($end -lt 0) { return $false }
    $body = $text.Substring($openBrace + 1, $end - $openBrace - 1)
    if ($body -notmatch '\.collect') { return $false }
    if ($body -match '(repeatOnLifecycle|flowWithLifecycle)') { return $false }
    return $true
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
        if (-not $script:BroadCatchRx.IsMatch($lines[$i])) { continue }
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
        (New-RegexRule -Name 'empty-catch' `
                -Pattern ([regex]'catch\s*\([^)]*\)\s*\{\s*(?:(?://[^\r\n]*)|(?:/\*[\s\S]*?\*/))?\s*\}') `
                -FailMessage 'new empty catch block introduced. Recover, use a safe default, or log at the correct level.'),
        (New-RegexRule -Name 'layout-hardcoded-colors' `
                -Pattern ([regex]'="#[0-9a-fA-F]{3,8}"') `
                -Extensions @('.xml') `
                -Roots @('app_v2/src/main/res/layout', 'app_v2/src/main/res/layout-land') `
                -PathFilter 'app_v2/src/main/res/layout(-land)?/' `
                -FailMessage 'new hardcoded layout color introduced. Reference a theme attr or named color.'),
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
            FailMessage  = 'new broad catch in coroutine code that swallows CancellationException. Add `catch (e: CancellationException) { throw e }` as the first arm of the chain (S1363).'
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

