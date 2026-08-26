#requires -Version 7.0
<#
.SYNOPSIS
    S0507 ratchet gate: interactive views without a visible focus indication must never grow.

.DESCRIPTION
    CLAUDE.md Rule 16 (multimodal parity): every focusable/clickable control must show
    where the focus is, and not by colour alone, so TV / D-pad / keyboard users can see
    the current target. This gate counts interactive views in every app_v2/src/main/res/layout*
    directory that carry NO recognised focus indication.

    An element is INTERACTIVE when it has `android:clickable="true"`,
    `android:focusable="true"`, or an `android:onClick`.

    It is considered COVERED (not a gap) when any of these hold:
      - a focus-state drawable is applied via foreground/background:
        `@drawable/focus_button_background`, `@drawable/focus_tab_background`,
        `@drawable/item_focus_selector`, or any `@drawable/*focus*`;
      - a themed ripple is applied: `?attr/selectableItemBackground[Borderless]`
        or `?attr/actionBarItemBackground` (ripple carries the focus state);
      - the element is a framework / Material widget with intrinsic focus rendering
        (Button, MaterialButton, FAB, Chip, EditText, Switch, CheckBox, RadioButton,
        Spinner, SeekBar, Slider, RatingBar, Tab, ToggleButton, CheckedTextView);
      - the element is a scroll / nav container that is focusable only for traversal,
        not a control (RecyclerView, *ScrollView, ViewPager(2), WebView, *SurfaceView).

    Everything else interactive-but-uncovered is a gap. The heuristic is deliberately
    conservative (a recognised style/ripple/focus-drawable clears the element) to keep
    false positives low; the committed baseline absorbs current debt and may only go
    DOWN. A NEW clickable container/image without any focus affordance raises the count
    and fails the gate - that is the regression this prevents at source.

    Baseline lives in scripts/quality/focus-highlight-baseline.txt.

    S2069 - the scan root is app_v2 and cannot be anything else, so a caller that names its
    changed files gets judged on those files instead of on the tree. Without it the gate scored
    the whole of app_v2 on every closure, and a wear-only ticket was failed by another session's
    in-flight app_v2 edit - CLAUDE.md Rule 33's exact class. Files outside app_v2 are not this
    gate's subject and are skipped rather than failed; a named set that contains none of its
    subject passes without scanning anything, and never silently widens back to the full tree.

    Modes:
      (default)        Report current count vs baseline.
      -Gate            Exit 1 if current > baseline (fail-closed on growth).
      -ChangedFiles    Judge only the named files: a gap PRESENT in the working copy and ABSENT
                       from its HEAD version fails under -Gate. Pre-existing (already committed)
                       gaps in the same file do not, so the integer baseline is left alone and
                       another ticket's WIP elsewhere in the tree cannot fail this closure.
      -UpdateBaseline  Ratchet DOWN only (also seeds the file when missing).
      -List            Print every gap as file:line  <tag>  (proposal list for cleanup).

.NOTES
    Exit codes:
      0 - pass: at or below baseline, no growth in the named files, or a non-gate mode.
      1 - fail: the count rose above the baseline, a named file introduced a gap, or a
          MaterialCardView overwrites android:foreground during construction.
      2 - cannot verify: the scan root is absent, or a named changed file does not exist.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-focus-highlight.ps1
    pwsh -NoProfile -File scripts/quality/assert-focus-highlight.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-focus-highlight.ps1 -Gate -ChangedFiles "app_v2/src/main/res/layout/activity_main.xml"
    pwsh -NoProfile -File scripts/quality/assert-focus-highlight.ps1 -UpdateBaseline
    pwsh -NoProfile -File scripts/quality/assert-focus-highlight.ps1 -List
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$UpdateBaseline,
    [switch]$List,
    [string[]]$ChangedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$resRoot = Join-Path $repoRoot 'app_v2/src/main/res'
$baselineFile = Join-Path $PSScriptRoot 'focus-highlight-baseline.txt'

. (Join-Path $PSScriptRoot 'lib/changed-files-delta.ps1')

# Element open-tag matcher: <Tag ...>  and  <Tag .../>  (skips </close>, <!--comment-->, <?xml?>).
$rxElement = [regex]'(?s)<([A-Za-z][\w.]*)\b([^>]*?)/?>'

# Short tag names whose focus is rendered by the framework / Material itself.
$intrinsicFocus = @(
    'Button', 'ImageButton', 'MaterialButton', 'ExtendedFloatingActionButton',
    'FloatingActionButton', 'Chip', 'ChipGroup',
    'EditText', 'TextInputEditText', 'AutoCompleteTextView', 'MultiAutoCompleteTextView',
    'CheckBox', 'MaterialCheckBox', 'RadioButton', 'MaterialRadioButton',
    'Switch', 'SwitchCompat', 'SwitchMaterial', 'ToggleButton', 'CheckedTextView',
    'Spinner', 'SeekBar', 'Slider', 'RangeSlider', 'RatingBar',
    'TabItem', 'BottomNavigationView', 'NavigationView'
)
# Scroll / nav containers: focusable for traversal, not a control surface.
$containerSkip = @(
    'RecyclerView', 'ScrollView', 'NestedScrollView', 'HorizontalScrollView',
    'ViewPager', 'ViewPager2', 'WebView', 'SurfaceView', 'GLSurfaceView',
    'TextureView', 'ViewStub', 'merge', 'include', 'TabLayout'
)
# Custom views that render their own focus affordance in code (verified in their Kotlin source),
# so an XML clickable/focusable attr is not a real gap:
#   SettingsToggleRow      - sets android.R.attr.selectableItemBackground in its constructor.
#   TranslationOverlayView - custom interactive canvas overlay (own onDraw), not a discrete control.
$customFocusViews = @('SettingsToggleRow', 'TranslationOverlayView', 'FocusMaterialCardView')

# Confirmed non-targets, whitelisted by android:id. These are clickable/focusable to block touch
# passthrough or to drive marquee scroll, NOT discrete D-pad-activatable controls; a focus stroke
# around a full-screen scrim or a centred title is meaningless. Exact-id only, so a NEW clickable
# container is still flagged.
$nonTargetIds = @(
    'layoutControls',               # browse control-bar container (importantForAccessibility=no)
    'layoutLoading',                # camera-OCR full-screen loading scrim
    'extensionsManagerRoot',        # extensions screen root container
    'audioInfoOverlay',             # player audio-info overlay panel
    'firstRunHintOverlay',          # player first-run hint modal (tap-to-dismiss)
    'lyricsViewerContainer',        # player lyrics overlay (passthrough block)
    'pdfFullscreenOverlay',         # player PDF full-screen overlay (passthrough block)
    'translationOverlay',           # player translation card overlay (passthrough block)
    'translationOverlayBackground', # player translation dismiss scrim
    'nowPlayingTitle'               # bottom-sheet title, focusable only to drive marquee scroll
)

$materialCardForegroundPattern = [regex]'(?s)<com\.google\.android\.material\.card\.MaterialCardView\b(?<attrs>[^>]*?)>'

function Test-Covered([string]$attrs, [string]$shortTag) {
    if ($intrinsicFocus -contains $shortTag) { return $true }
    if ($customFocusViews -contains $shortTag) { return $true }
    # Custom subclasses of intrinsically-focusable widgets (e.g. VerticalSeekBar, a
    # *Switch) inherit the framework thumb/track focus rendering.
    if ($shortTag -match '(SeekBar|Switch)$') { return $true }
    # Focus-state drawable on foreground/background.
    if ($attrs -match '@drawable/(focus_button_background|focus_tab_background|item_focus_selector)') { return $true }
    if ($attrs -match '@drawable/[a-z0-9_]*focus[a-z0-9_]*') { return $true }
    # Themed ripple carries the focus state (app-theme or android-framework namespace).
    if ($attrs -match '\?(android:)?attr/selectableItemBackground') { return $true }
    if ($attrs -match '\?(android:)?attr/actionBarItemBackground') { return $true }
    # Material/Button/Chip/Tab style implies built-in focus rendering.
    if ($attrs -match 'style\s*=\s*"[^"]*(Button|Chip|Tab|Fab|FAB)[^"]*"') { return $true }
    return $false
}

# The one place an element is judged. Both modes call it, so the per-file count under
# -ChangedFiles is byte-for-byte what the full scan would have counted for that file.
function Get-FocusGapsInText {
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
    $gaps = [System.Collections.Generic.List[object]]::new()
    foreach ($m in $rxElement.Matches($Text)) {
        $tag = $m.Groups[1].Value
        $attrs = $m.Groups[2].Value
        $shortTag = ($tag -split '\.')[-1]

        $interactive = ($attrs -match 'android:clickable\s*=\s*"true"') -or
                       ($attrs -match 'android:focusable\s*=\s*"true"') -or
                       ($attrs -match 'android:onClick\s*=')
        if (-not $interactive) { continue }
        if ($containerSkip -contains $shortTag) { continue }
        # Whitelisted non-target (scrim / passthrough-blocker / marquee-driver), keyed by android:id.
        if (($attrs -match 'android:id\s*=\s*"@\+?id/([^"]+)"') -and ($nonTargetIds -contains $Matches[1])) { continue }
        if (Test-Covered $attrs $shortTag) { continue }

        # Line number of the element start (count newlines up to the match index).
        $lineNo = ($Text.Substring(0, $m.Index) -split "`n").Count
        $gaps.Add([pscustomobject]@{ Line = $lineNo; Tag = $tag })
    }
    return $gaps
}

function Get-MaterialCardForegroundLines {
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
    $lines = [System.Collections.Generic.List[int]]::new()
    foreach ($materialCard in $materialCardForegroundPattern.Matches($Text)) {
        if ($materialCard.Groups['attrs'].Value -notmatch 'android:foreground\s*=') { continue }
        $lines.Add(($Text.Substring(0, $materialCard.Index) -split "`n").Count)
    }
    return $lines
}

function ConvertTo-RepoRelativePath {
    param([Parameter(Mandatory)][string]$FullPath)
    return ($FullPath.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/')
}

# ---------------------------------------------------------------------------------------------
# Scoped mode (S2069): judge the named files, never the tree.
# ---------------------------------------------------------------------------------------------
# pwsh -File binds a quoted CSV as ONE array element, so re-split every bound element.
$named = @($ChangedFiles | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } |
    Where-Object { $_ })

if ($named.Count -gt 0) {
    $subject = @($named | ForEach-Object { $_ -replace '\\', '/' } |
        Where-Object { $_ -match '\.xml$' -and $_ -match '^app_v2/src/' })

    foreach ($rel in $subject) {
        $full = if ([System.IO.Path]::IsPathRooted($rel)) { $rel } else { Join-Path $repoRoot $rel }
        if (-not (Test-Path -LiteralPath $full)) {
            Write-Error "assert-focus-highlight: changed file not found - $rel" -ErrorAction Continue
            exit 2
        }
    }

    # A MaterialCardView that carries android:foreground loses it during construction, so the
    # focus ring never renders. Absolute, not a ratchet: a hit in a file this change touched
    # belongs to this change.
    $cardHits = [System.Collections.Generic.List[string]]::new()
    foreach ($rel in $subject) {
        $full = if ([System.IO.Path]::IsPathRooted($rel)) { $rel } else { Join-Path $repoRoot $rel }
        $text = Get-Content -LiteralPath $full -Raw
        if ($null -eq $text) { continue }
        foreach ($line in @(Get-MaterialCardForegroundLines $text)) {
            $cardHits.Add(("{0}:{1}" -f (ConvertTo-RepoRelativePath (Resolve-Path -LiteralPath $full).Path), $line))
        }
    }
    if ($cardHits.Count -gt 0) {
        Write-Host 'FAIL: MaterialCardView overwrites android:foreground during construction.'
        $cardHits | ForEach-Object { Write-Host "  $_" }
        exit 1
    }

    # Only a layout* directory can hold a focus gap - the rest of app_v2/src/**.xml carries no
    # interactive element to judge.
    $layouts = @($subject | Where-Object { $_ -match '/res/layout[^/]*/' })
    if ($layouts.Count -eq 0) {
        Write-Host 'focus-highlight [scoped]: no app_v2 layout in the changed set - nothing to judge.'
        Write-Host 'assert-focus-highlight: PASS (scoped).'
        exit 0
    }

    $growth = Measure-ChangedFileGrowth `
        -ChangedFiles $layouts `
        -RepoRoot $repoRoot `
        -Extensions @('.xml') `
        -CountInText { param([string]$t) @(Get-FocusGapsInText $t).Count }

    Write-Host ("focus-highlight [scoped to {0} changed layout(s)]: new gaps {1}" -f $layouts.Count, $growth.Growth)
    if ($List) {
        foreach ($rel in $layouts) {
            $full = if ([System.IO.Path]::IsPathRooted($rel)) { $rel } else { Join-Path $repoRoot $rel }
            $text = Get-Content -LiteralPath $full -Raw
            if ($null -eq $text) { continue }
            foreach ($gap in @(Get-FocusGapsInText $text)) {
                Write-Host ("  {0}:{1}  <{2}>" -f $rel, $gap.Line, $gap.Tag)
            }
        }
    }
    if ($Gate -and $growth.Growth -gt 0) {
        foreach ($row in $growth.PerFile) {
            if ($row.New -gt 0) { Write-Host ("  {0}: HEAD {1} -> working {2}" -f $row.Path, $row.Head, $row.Work) }
        }
        Write-Host ('FAIL: a changed layout added ' + $growth.Growth + ' interactive view(s) without a visible focus indication (Rule 16). Add a focus foreground/background, a ?attr/selectableItemBackground ripple, or use a Material/framework control.')
        exit 1
    }
    Write-Host 'assert-focus-highlight: PASS (no new focus gap in the changed layouts).'
    exit 0
}

# ---------------------------------------------------------------------------------------------
# Full-tree mode: the release / CI verdict, unchanged by S2069.
# ---------------------------------------------------------------------------------------------
if (-not (Test-Path -LiteralPath $resRoot)) {
    Write-Error "assert-focus-highlight: scan root not found - $resRoot" -ErrorAction Continue
    exit 2
}

# Every layout directory, not the two phone ones: a qualifier-specific copy of a screen is exactly
# where a D-pad or keyboard is most likely (Rule 16), so it is the last place to leave unscanned.
# Globbed rather than enumerated so a new qualifier is covered the day it appears (S1935).
$layoutDirs = Get-ChildItem -LiteralPath $resRoot -Directory -Filter 'layout*' | Select-Object -ExpandProperty FullName | Sort-Object

$current = 0
$hits = [System.Collections.Generic.List[string]]::new()
$materialCardForegroundHits = [System.Collections.Generic.List[string]]::new()
$allResourceXml = Get-ChildItem -LiteralPath (Join-Path $repoRoot 'app_v2/src') -Recurse -File -Filter '*.xml'
foreach ($resourceXml in $allResourceXml) {
    $resourceText = Get-Content -LiteralPath $resourceXml.FullName -Raw
    if ($null -eq $resourceText) { continue }
    foreach ($line in @(Get-MaterialCardForegroundLines $resourceText)) {
        $materialCardForegroundHits.Add(("{0}:{1}" -f (ConvertTo-RepoRelativePath $resourceXml.FullName), $line))
    }
}
foreach ($dir in $layoutDirs) {
    $files = Get-ChildItem -LiteralPath $dir -Recurse -File -Filter '*.xml' -ErrorAction SilentlyContinue
    foreach ($file in $files) {
        $text = Get-Content -LiteralPath $file.FullName -Raw
        if ($null -eq $text) { continue }
        foreach ($gap in @(Get-FocusGapsInText $text)) {
            $current++
            if ($List) {
                $hits.Add(("{0}:{1}  <{2}>" -f (ConvertTo-RepoRelativePath $file.FullName), $gap.Line, $gap.Tag))
            }
        }
    }
}

if ($materialCardForegroundHits.Count -gt 0) {
    Write-Host 'FAIL: MaterialCardView overwrites android:foreground during construction.'
    $materialCardForegroundHits | ForEach-Object { Write-Host "  $_" }
    exit 1
}

if ($List) {
    foreach ($h in $hits) { Write-Host $h }
    Write-Host ''
}

if ($UpdateBaseline) {
    if (-not (Test-Path $baselineFile)) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "focus-highlight baseline SEEDED: $current"
        exit 0
    }
    $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
    if ($current -lt $baseline) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "focus-highlight baseline ratcheted DOWN: $baseline -> $current"
    }
    elseif ($current -eq $baseline) {
        Write-Host "focus-highlight baseline unchanged ($baseline)"
    }
    else {
        Write-Error "Refusing to RAISE baseline ($baseline -> $current). New interactive view(s) lack a focus indication - add a focus-state foreground/background, a ?attr/selectableItemBackground ripple, or use a Material/framework control." -ErrorAction Continue
        exit 1
    }
    exit 0
}

if (-not (Test-Path $baselineFile)) {
    Write-Host "focus-highlight: NO BASELINE yet | actual $current - run -UpdateBaseline to seed."
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("focus-highlight in src/main: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
if ($Gate -and $current -gt $baseline) {
    Write-Host "FAIL: interactive views without a visible focus indication grew above baseline (Rule 16). Add a focus foreground/background, a ?attr/selectableItemBackground ripple, or use a Material/framework control."
    exit 1
}
if ($current -lt $baseline) {
    Write-Host "Note: count is below baseline - run -UpdateBaseline to ratchet the cap down."
}
exit 0
