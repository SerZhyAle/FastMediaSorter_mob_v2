#requires -Version 7.0
<#
.SYNOPSIS
    S3371 ratchet gate: an interactive element must carry an accessible name, a custom
    interactive view must expose role and state, and a touch target must reach the platform floor.

.DESCRIPTION
    Phase 07 of the quality-rules expansion. The canon names semantic action roles as a portable
    candidate; this repository never instantiated it, so accessibility was enforced only where it
    happened to overlap another rule - focus indication, RTL attributes, string liveness. Nothing
    read an element the way a screen reader does.

    The demand is CONTEXTUAL, not a blanket contentDescription requirement. Three findings:

      icon-only-no-name        An element that is interactive (android:clickable="true",
                               android:focusable="true", android:onClick, or an inherently
                               clickable image widget) and carries an ICON ONLY - an image source
                               or an app:icon, with no android:text or android:hint of its own -
                               must declare a non-empty android:contentDescription. The exception
                               is explicit, never inferred: the element marks itself invisible to
                               accessibility services (android:importantForAccessibility="no" or
                               "noHideDescendants", or contentDescription="@null") AND carries a
                               row in the decorative allowlist. A marked element with no row is
                               still a finding - that is the difference between a decision and a
                               silence.

      custom-view-no-a11y      A custom View or ViewGroup under a ui/ tree that handles touch
                               itself - onTouchEvent, a touch/click listener on itself, a gesture
                               detector, performClick - must expose role and state through an
                               accessibility delegate: AccessibilityNodeInfo(Compat), an
                               AccessibilityDelegate, ExploreByTouchHelper, or a replaced
                               AccessibilityAction. A contentDescription alone gives the node a
                               NAME and leaves its role and state unspoken, so it does not clear
                               this rule.

      touch-target-undersized  An interactive element whose resolvable width or height falls below
                               the 48dp platform touch-target floor. Only a literal dp value or a
                               @dimen reference this repository defines is judged; wrap_content,
                               match_parent and a value that cannot be resolved are skipped rather
                               than guessed at, because a padded 24dp icon can be a 48dp target
                               and a gate that cannot tell would be reporting noise.

    The tree does not reach zero and was never going to: the allowlist covers the elements a
    survey proved decorative, and the rest is real debt. The baseline freezes it at the first full
    run and may only shrink, so an existing finding is debt with a ceiling rather than permission
    to add another. Calling an interactive control decorative to lower the number is the one
    cure this gate does not accept.

    Baseline lives in scripts/quality/a11y-semantics-baseline.txt (single int).
    Allowlist lives in scripts/quality/a11y-decorative-allowlist.txt, one row per decorative
    element as `<layout-file>.xml:<resource-id>  # <reason>`; a row without a reason is refused,
    because a reason nobody wrote is one nobody can re-judge.

    Modes:
      (default)         Report current count vs baseline.
      -Gate             Exit 1 if current > baseline; with -ChangedFiles, if the changed files
                        introduced a finding relative to their HEAD copies.
      -UpdateBaseline   Ratchet DOWN only (also seeds the file when missing).
      -List             Print every finding as path:line <rule> <detail>.

.PARAMETER RepoRoot
    The tree to scan. Overridable so the contract suite runs the real refusal paths against a
    fixture tree instead of against the live one every concurrent session is editing.

.PARAMETER BaselinePath
    The baseline to read or write. Overridable for the same reason as -RepoRoot.

.PARAMETER AllowlistPath
    The decorative allowlist to read. Overridable for the same reason as -RepoRoot.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - pass: at or below baseline, no growth in the named files, a report/list run, or a
          completed baseline write.
      1 - fail: the count rose above the baseline, a changed file introduced a finding,
          the allowlist holds a malformed or reasonless row, or -UpdateBaseline was asked to
          RAISE the baseline.
      2 - cannot verify: the scan root is absent, or a named changed file does not exist.
      4 - Code.Scripts is held by another session, so no baseline was written. The queue place is
          held - wait for the turn in the background and rerun.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-a11y-semantics.ps1
    pwsh -NoProfile -File scripts/quality/assert-a11y-semantics.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-a11y-semantics.ps1 -Gate -ChangedFiles "app_v2/src/main/res/layout/activity_main.xml"
    pwsh -NoProfile -File scripts/quality/assert-a11y-semantics.ps1 -List
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$UpdateBaseline,
    [switch]$List,
    [AllowEmptyCollection()][AllowNull()][string[]]$ChangedFiles,
    [string]$RepoRoot,
    [string]$BaselinePath,
    [string]$AllowlistPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = if ($RepoRoot) { (Resolve-Path -LiteralPath $RepoRoot).Path } else { Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
. (Join-Path $PSScriptRoot 'lib/changed-files-delta.ps1')
. (Join-Path $PSScriptRoot '../utils/code-lock-scope.ps1')

$baselineFile = if ($BaselinePath) { $BaselinePath } else { Join-Path $PSScriptRoot 'a11y-semantics-baseline.txt' }
$allowlistFile = if ($AllowlistPath) { $AllowlistPath } else { Join-Path $PSScriptRoot 'a11y-decorative-allowlist.txt' }

$TouchTargetFloorDp = 48

# Element open-tag matcher, shared with assert-focus-highlight.ps1: <Tag ..> and <Tag ../>, never
# a closing tag, a comment or the XML declaration.
$rxElement = [regex]'(?s)<([A-Za-z][\w.]*)\b([^>]*?)/?>'

# Widgets whose whole purpose is a tappable icon - interactive with no attribute saying so.
$inherentlyInteractive = @(
    'ImageButton', 'AppCompatImageButton', 'FloatingActionButton', 'ExtendedFloatingActionButton'
)
# Structural tags that hold no accessibility node of their own.
$structuralTags = @('merge', 'include', 'ViewStub', 'requestFocus', 'layout', 'data', 'variable', 'import')
# Traversal containers: focusable so a D-pad can enter them, not an action target.
$traversalTags = @(
    'RecyclerView', 'ScrollView', 'NestedScrollView', 'HorizontalScrollView',
    'ViewPager', 'ViewPager2', 'WebView', 'SurfaceView', 'GLSurfaceView', 'TextureView'
)

$rxDp = [regex]'^\s*([0-9]+(?:\.[0-9]+)?)\s*(dp|dip)\s*$'
$rxDimenRef = [regex]'^\s*@dimen/([A-Za-z0-9_]+)\s*$'

# ---------------------------------------------------------------------------
# Decorative allowlist.
# ---------------------------------------------------------------------------

function Read-DecorativeAllowlist {
    <# Rows as `<layout-file>.xml:<resource-id>  # <reason>`. Returns the accepted key set plus
       the malformed rows, so the caller can refuse them by name rather than ignore them. #>
    param([string]$Path)
    $keys = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $malformed = [System.Collections.Generic.List[string]]::new()
    if (-not (Test-Path -LiteralPath $Path)) { return [pscustomobject]@{ Keys = $keys; Malformed = $malformed } }
    $lineNo = 0
    foreach ($line in (Get-Content -LiteralPath $Path)) {
        $lineNo++
        $trimmed = $line.Trim()
        if (-not $trimmed) { continue }
        if ($trimmed.StartsWith('#')) { continue }
        $hashAt = $trimmed.IndexOf('#')
        if ($hashAt -lt 0) {
            $malformed.Add(("{0}:{1} no reason - a row nobody explained is one nobody can re-judge" -f (Split-Path -Leaf $Path), $lineNo))
            continue
        }
        $key = $trimmed.Substring(0, $hashAt).Trim()
        $reason = $trimmed.Substring($hashAt + 1).Trim()
        if (-not $reason) {
            $malformed.Add(("{0}:{1} empty reason after '#'" -f (Split-Path -Leaf $Path), $lineNo))
            continue
        }
        if ($key -notmatch '^[A-Za-z0-9_]+\.xml:[A-Za-z0-9_]+$') {
            $malformed.Add(("{0}:{1} '{2}' is not '<layout-file>.xml:<resource-id>'" -f (Split-Path -Leaf $Path), $lineNo, $key))
            continue
        }
        [void]$keys.Add($key)
    }
    return [pscustomobject]@{ Keys = $keys; Malformed = $malformed }
}

$allowlist = Read-DecorativeAllowlist -Path $allowlistFile
$script:decorativeKeys = $allowlist.Keys

# ---------------------------------------------------------------------------
# Dimension resolution for the touch-target floor.
# ---------------------------------------------------------------------------

function Read-DimenMap {
    <# name -> dp for every <dimen> whose value is a literal dp. A dimen defined in sp, px or a
       qualified directory is left out on purpose: the gate judges only what it can prove. #>
    param([string]$Root)
    $map = @{}
    $dimensFile = Join-Path $Root 'app_v2/src/main/res/values/dimens.xml'
    if (-not (Test-Path -LiteralPath $dimensFile)) { return $map }
    $text = Get-Content -LiteralPath $dimensFile -Raw
    if ([string]::IsNullOrEmpty($text)) { return $map }
    foreach ($m in ([regex]'<dimen\s+name="([A-Za-z0-9_]+)"\s*>([^<]*)</dimen>').Matches($text)) {
        $dp = $rxDp.Match($m.Groups[2].Value)
        if ($dp.Success) { $map[$m.Groups[1].Value] = [double]$dp.Groups[1].Value }
    }
    return $map
}

$script:dimenMap = Read-DimenMap -Root $repoRoot

function Resolve-DimensionDp {
    <# The dp value of one attribute, or $null when it is wrap_content, match_parent, an
       unresolvable reference, or absent. #>
    param([string]$Attrs, [string]$Name)
    if ($Attrs -notmatch ('android:{0}\s*=\s*"([^"]*)"' -f $Name)) { return $null }
    $raw = $Matches[1]
    $dp = $rxDp.Match($raw)
    if ($dp.Success) { return [double]$dp.Groups[1].Value }
    $ref = $rxDimenRef.Match($raw)
    if ($ref.Success -and $script:dimenMap.ContainsKey($ref.Groups[1].Value)) { return [double]$script:dimenMap[$ref.Groups[1].Value] }
    return $null
}

# ---------------------------------------------------------------------------
# Layout findings.
# ---------------------------------------------------------------------------

function Get-LayoutA11yFindings {
    <# The one place a layout element is judged. Both the full scan and the changed-set delta
       call it, so a per-file count is byte-for-byte what the tree scan would have counted. #>
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text, [string]$FileName)
    $findings = [System.Collections.Generic.List[object]]::new()
    if ([string]::IsNullOrEmpty($Text)) { return $findings }

    foreach ($m in $rxElement.Matches($Text)) {
        $tag = $m.Groups[1].Value
        $attrs = $m.Groups[2].Value
        $shortTag = ($tag -split '\.')[-1]
        if ($structuralTags -contains $shortTag) { continue }

        $interactive = ($attrs -match 'android:clickable\s*=\s*"true"') -or
                       ($attrs -match 'android:focusable\s*=\s*"true"') -or
                       ($attrs -match 'android:onClick\s*=') -or
                       ($inherentlyInteractive -contains $shortTag)
        if (-not $interactive) { continue }
        if ($traversalTags -contains $shortTag) { continue }

        $id = if ($attrs -match 'android:id\s*=\s*"@\+?id/([A-Za-z0-9_]+)"') { $Matches[1] } else { '' }
        $lineNo = ($Text.Substring(0, $m.Index) -split "`n").Count

        $contentDescription = if ($attrs -match 'android:contentDescription\s*=\s*"([^"]*)"') { $Matches[1] } else { $null }
        $hasName = $contentDescription -and $contentDescription.Trim() -and $contentDescription.Trim() -ne '@null'
        $ownText = if ($attrs -match 'android:text\s*=\s*"([^"]*)"') { $Matches[1] } else { '' }
        $ownHint = if ($attrs -match 'android:hint\s*=\s*"([^"]*)"') { $Matches[1] } else { '' }
        $hasOwnText = ($ownText.Trim() -ne '') -or ($ownHint.Trim() -ne '')

        $hasIcon = ($attrs -match 'android:src\s*=') -or ($attrs -match 'app:srcCompat\s*=') -or
                   ($attrs -match 'app:icon\s*=') -or ($shortTag -match '(ImageView|ImageButton)$') -or
                   ($inherentlyInteractive -contains $shortTag)

        if ($hasIcon -and -not $hasOwnText -and -not $hasName) {
            $markedInvisible = ($attrs -match 'android:importantForAccessibility\s*=\s*"(no|noHideDescendants)"') -or
                               ($contentDescription -eq '@null')
            $allowed = $markedInvisible -and $id -and $script:decorativeKeys.Contains(("{0}:{1}" -f $FileName, $id))
            if (-not $allowed) {
                $detail = if ($markedInvisible) {
                    "marked not-accessible but absent from the decorative allowlist"
                } else {
                    "icon-only interactive element with no android:contentDescription"
                }
                $findings.Add([pscustomobject]@{
                    Line = $lineNo; Rule = 'icon-only-no-name'
                    Detail = ("<{0}{1}> {2}" -f $tag, $(if ($id) { " id=$id" } else { '' }), $detail)
                })
            }
        }

        $widthDp = Resolve-DimensionDp -Attrs $attrs -Name 'layout_width'
        $heightDp = Resolve-DimensionDp -Attrs $attrs -Name 'layout_height'
        $minWidthDp = Resolve-DimensionDp -Attrs $attrs -Name 'minWidth'
        $minHeightDp = Resolve-DimensionDp -Attrs $attrs -Name 'minHeight'
        $effectiveWidth = if ($null -ne $widthDp -and $null -ne $minWidthDp) { [Math]::Max($widthDp, $minWidthDp) } elseif ($null -ne $widthDp) { $widthDp } else { $null }
        $effectiveHeight = if ($null -ne $heightDp -and $null -ne $minHeightDp) { [Math]::Max($heightDp, $minHeightDp) } elseif ($null -ne $heightDp) { $heightDp } else { $null }
        # 0dp is a ConstraintLayout match-constraint directive, not a size: the view expands to its
        # constraints, so judging it as a zero-dp target would report every stretched row.
        $undersized = ($null -ne $effectiveWidth -and $effectiveWidth -gt 0 -and $effectiveWidth -lt $TouchTargetFloorDp) -or
                      ($null -ne $effectiveHeight -and $effectiveHeight -gt 0 -and $effectiveHeight -lt $TouchTargetFloorDp)
        if ($undersized) {
            $findings.Add([pscustomobject]@{
                Line = $lineNo; Rule = 'touch-target-undersized'
                Detail = ("<{0}{1}> {2}x{3} dp is under the {4}dp floor" -f $tag,
                    $(if ($id) { " id=$id" } else { '' }),
                    $(if ($null -ne $effectiveWidth) { $effectiveWidth } else { '?' }),
                    $(if ($null -ne $effectiveHeight) { $effectiveHeight } else { '?' }),
                    $TouchTargetFloorDp)
            })
        }
    }
    return $findings
}

# ---------------------------------------------------------------------------
# Custom-view findings.
# ---------------------------------------------------------------------------

# A class declaration whose first supertype is constructed - `class Foo(..) : Bar(..)`. The base's
# short name decides whether it is a view at all.
$rxClassDeclaration = [regex]'(?m)^\s*(?:@\w+\s+)*(?:public\s+|internal\s+|private\s+|open\s+|abstract\s+|sealed\s+)*class\s+(\w+)[\s\S]{0,400}?:\s*(?:[A-Za-z0-9_.]+\s*,\s*)*([A-Za-z0-9_.]+)\s*\('
# Structural view bases: they draw and lay out, and carry no role or state of their own, so a
# subclass that handles touch is a control the accessibility tree cannot describe. A framework
# CONTROL base is deliberately absent - a VerticalSeekBar inherits SeekBar's node, role and range,
# and demanding a delegate of it would be demanding a second copy of what already works. A base
# this repository declares itself is absent for the mirror reason: the node belongs to that base,
# so its subclasses are fixed by fixing it once.
$structuralViewBases = @(
    'View', 'ViewGroup', 'FrameLayout', 'LinearLayout', 'LinearLayoutCompat', 'RelativeLayout',
    'ConstraintLayout', 'MotionLayout', 'CoordinatorLayout', 'GridLayout', 'TableLayout', 'TableRow',
    'ImageView', 'AppCompatImageView', 'ShapeableImageView',
    'TextView', 'AppCompatTextView', 'MaterialTextView',
    'CardView', 'MaterialCardView', 'Toolbar', 'MaterialToolbar',
    'SurfaceView', 'TextureView', 'GLSurfaceView', 'FlexboxLayout'
)
$rxTouchHandling = [regex]'\boverride\s+fun\s+onTouchEvent\b|\bsetOnTouchListener\b|\bsetOnClickListener\b|\bisClickable\s*=\s*true\b|\bperformClick\b|\bGestureDetector\b|\bsetOnLongClickListener\b'
$rxA11ySeam = [regex]'AccessibilityNodeInfo|AccessibilityDelegate|ExploreByTouchHelper|AccessibilityAction|replaceAccessibilityAction'

function Get-CustomViewA11yFindings {
    <# One finding per interactive custom view class with no role/state seam. Judged per file,
       because the delegate a class installs can sit in any of its members. #>
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text, [string]$Relative)
    $findings = [System.Collections.Generic.List[object]]::new()
    if ([string]::IsNullOrEmpty($Text)) { return $findings }
    if ($Relative -and $Relative -notmatch '/ui/') { return $findings }
    if (-not $rxTouchHandling.IsMatch($Text)) { return $findings }
    if ($rxA11ySeam.IsMatch($Text)) { return $findings }

    foreach ($m in $rxClassDeclaration.Matches($Text)) {
        $baseShort = ($m.Groups[2].Value -split '\.')[-1]
        if ($structuralViewBases -notcontains $baseShort) { continue }
        $lineNo = ($Text.Substring(0, $m.Index) -split "`n").Count
        $findings.Add([pscustomobject]@{
            Line = $lineNo; Rule = 'custom-view-no-a11y'
            Detail = ("class {0} : {1} handles touch but exposes no accessibility role or state" -f $m.Groups[1].Value, $baseShort)
        })
    }
    return $findings
}

# ---------------------------------------------------------------------------
# Shared counting callback: one predicate for both scan paths.
# ---------------------------------------------------------------------------

function Get-A11yFindingsForPath {
    <# Route one file's text to the rule set its path selects, so the two scan paths cannot drift. #>
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text, [string]$Relative)
    $relative = ($Relative ?? '') -replace '\\', '/'
    if ($relative -match '\.kt$') { return Get-CustomViewA11yFindings -Text $Text -Relative $relative }
    if ($relative -match '/res/layout[^/]*/') { return Get-LayoutA11yFindings -Text $Text -FileName (Split-Path -Leaf $relative) }
    return [System.Collections.Generic.List[object]]::new()
}

$countInText = {
    param([string]$text, [string]$rel)
    # @( ) is load-bearing: PowerShell unrolls a returned List, so a file with no finding returns
    # $null and .Count on it throws under Set-StrictMode.
    @(Get-A11yFindingsForPath -Text $text -Relative $rel).Count
}

# ---------------------------------------------------------------------------
# The allowlist is judged before anything else: a malformed row makes every verdict below
# meaningless, because an element could be silently un-exempted by a typo.
# ---------------------------------------------------------------------------

if (@($allowlist.Malformed).Count -gt 0) {
    Write-Host 'FAIL: the decorative allowlist holds a row this gate cannot read.'
    foreach ($row in $allowlist.Malformed) { Write-Host "  $row" }
    Write-Host "Each row is '<layout-file>.xml:<resource-id>  # <reason>'; the reason is what makes the exception re-judgeable."
    exit 1
}

# ---------------------------------------------------------------------------
# Scoped run: judge what the changed files introduced against their HEAD copies.
# ---------------------------------------------------------------------------

if ($Gate -and $ChangedFiles -and (@(Expand-ChangedFiles -ChangedFiles $ChangedFiles).Count -gt 0)) {
    $named = @(Expand-ChangedFiles -ChangedFiles $ChangedFiles | ForEach-Object { $_ -replace '\\', '/' })
    $subject = @($named | Where-Object { ($_ -match '/res/layout[^/]*/.*\.xml$') -or ($_ -match '^app_v2/src/[^/]+/java/.*\.kt$') })
    foreach ($rel in $subject) {
        $full = if ([System.IO.Path]::IsPathRooted($rel)) { $rel } else { Join-Path $repoRoot $rel }
        if (-not (Test-Path -LiteralPath $full)) {
            Write-Host "assert-a11y-semantics: CANNOT VERIFY - named changed file not found: $rel"
            exit 2
        }
    }
    if ($subject.Count -eq 0) {
        Write-Host 'a11y-semantics [scoped]: no layout or Kotlin source in the changed set - nothing to judge.'
        Write-Host 'assert-a11y-semantics: PASS (scoped).'
        exit 0
    }

    $delta = Measure-ChangedFileGrowth -ChangedFiles $subject -RepoRoot $repoRoot -Extensions @('.xml', '.kt') -CountInText $countInText
    Write-Host ("a11y-semantics [scoped to {0} changed file(s)]: new finding(s) {1}" -f $subject.Count, $delta.Growth)
    if ($List) {
        foreach ($rel in $subject) {
            $full = if ([System.IO.Path]::IsPathRooted($rel)) { $rel } else { Join-Path $repoRoot $rel }
            $text = Get-Content -LiteralPath $full -Raw
            if ($null -eq $text) { continue }
            foreach ($finding in @(Get-A11yFindingsForPath -Text $text -Relative $rel)) {
                Write-Host ("  {0}:{1}  {2}  {3}" -f $rel, $finding.Line, $finding.Rule, $finding.Detail)
            }
        }
    }
    if ($delta.Growth -gt 0) {
        foreach ($row in $delta.PerFile | Where-Object { $_.New -gt 0 }) {
            Write-Host ("  {0}: HEAD {1} -> working {2}" -f $row.Path, $row.Head, $row.Work)
        }
        Write-Host 'FAIL: a changed file added an accessibility finding. Give the icon-only control an android:contentDescription, expose role and state from the custom view through an accessibility delegate, or raise the target to 48dp. A decorative image is exempted by marking it importantForAccessibility="no" AND adding a reasoned row to scripts/quality/a11y-decorative-allowlist.txt - never by calling an interactive control decorative.'
        exit 1
    }
    Write-Host 'assert-a11y-semantics: PASS (no new accessibility finding in the changed files).'
    exit 0
}

# ---------------------------------------------------------------------------
# Full scan.
# ---------------------------------------------------------------------------

$srcRoot = Join-Path $repoRoot 'app_v2/src'
if (-not (Test-Path -LiteralPath $srcRoot)) {
    Write-Host "assert-a11y-semantics: CANNOT VERIFY - scan root not found: $srcRoot"
    exit 2
}

$current = 0
$hits = [System.Collections.Generic.List[string]]::new()

function ConvertTo-RepoRelativePath {
    param([Parameter(Mandatory)][string]$FullPath)
    return ($FullPath.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/')
}

# Every layout directory of every source set, globbed rather than enumerated so a new qualifier
# is covered the day it appears.
foreach ($dir in (Get-ChildItem -LiteralPath $srcRoot -Recurse -Directory -Filter 'layout*' -ErrorAction SilentlyContinue | Sort-Object FullName)) {
    foreach ($file in (Get-ChildItem -LiteralPath $dir.FullName -File -Filter '*.xml' -ErrorAction SilentlyContinue)) {
        $text = Get-Content -LiteralPath $file.FullName -Raw
        if ($null -eq $text) { continue }
        foreach ($finding in @(Get-LayoutA11yFindings -Text $text -FileName $file.Name)) {
            $current++
            $hits.Add(("{0}:{1}  {2}  {3}" -f (ConvertTo-RepoRelativePath $file.FullName), $finding.Line, $finding.Rule, $finding.Detail))
        }
    }
}

foreach ($file in (Get-ChildItem -LiteralPath $srcRoot -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue)) {
    $rel = ConvertTo-RepoRelativePath $file.FullName
    if ($rel -notmatch '/ui/') { continue }
    $text = Get-Content -LiteralPath $file.FullName -Raw
    if ($null -eq $text) { continue }
    foreach ($finding in @(Get-CustomViewA11yFindings -Text $text -Relative $rel)) {
        $current++
        $hits.Add(("{0}:{1}  {2}  {3}" -f $rel, $finding.Line, $finding.Rule, $finding.Detail))
    }
}

if ($List) {
    foreach ($h in $hits) { Write-Host $h }
    Write-Host ''
}

if ($UpdateBaseline) {
    $scope = $null
    try {
        $scope = Enter-CodeLockOrExit -Path $baselineFile -Reason 'assert-a11y-semantics.ps1 -UpdateBaseline'
        if (-not (Test-Path -LiteralPath $baselineFile)) {
            Set-Content -LiteralPath $baselineFile -Value "$current"
            Write-Host "a11y-semantics baseline SEEDED: $current"
            exit 0
        }
        $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
        if ($current -lt $baseline) {
            Set-Content -LiteralPath $baselineFile -Value "$current"
            Write-Host "a11y-semantics baseline ratcheted DOWN: $baseline -> $current"
        }
        elseif ($current -eq $baseline) {
            Write-Host "a11y-semantics baseline unchanged ($baseline)"
        }
        else {
            Write-Host "FAIL: refusing to RAISE the a11y-semantics baseline ($baseline -> $current). Name the icon, expose the role, or reach the 48dp floor."
            exit 1
        }
    }
    finally { Exit-CodeLockScope -Scope $scope }
    exit 0
}

if (-not (Test-Path -LiteralPath $baselineFile)) {
    Write-Host "a11y-semantics: NO BASELINE yet | actual $current - run -UpdateBaseline to seed."
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("accessibility findings in app_v2 layouts and custom views: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
if ($Gate -and $current -gt $baseline) {
    Write-Host 'FAIL: accessibility findings grew above the baseline. Give the icon-only control an android:contentDescription, expose role and state from the custom view through an accessibility delegate, or raise the target to 48dp. A decorative image is exempted by marking it importantForAccessibility="no" AND adding a reasoned row to scripts/quality/a11y-decorative-allowlist.txt.'
    exit 1
}
if ($current -lt $baseline) {
    Write-Host 'Note: count is below baseline - run -UpdateBaseline to ratchet the cap down.'
}
exit 0
