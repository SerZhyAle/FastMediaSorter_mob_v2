#requires -Version 7.0
<#
.SYNOPSIS
    S0507 ratchet gate: interactive views without a visible focus indication must never grow.

.DESCRIPTION
    CLAUDE.md Rule 16 (multimodal parity): every focusable/clickable control must show
    where the focus is, and not by colour alone, so TV / D-pad / keyboard users can see
    the current target. This gate counts interactive views in app_v2/src/main/res/layout
    and /res/layout-land that carry NO recognised focus indication.

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

    Modes:
      (default)        Report current count vs baseline.
      -Gate            Exit 1 if current > baseline (fail-closed on growth).
      -UpdateBaseline  Ratchet DOWN only (also seeds the file when missing).
      -List            Print every gap as file:line  <tag>  (proposal list for cleanup).

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-focus-highlight.ps1
    pwsh -NoProfile -File scripts/quality/assert-focus-highlight.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-focus-highlight.ps1 -UpdateBaseline
    pwsh -NoProfile -File scripts/quality/assert-focus-highlight.ps1 -List
#>
[CmdletBinding(DefaultParameterSetName = 'Report')]
param(
    [Parameter(ParameterSetName = 'Gate')][switch]$Gate,
    [Parameter(ParameterSetName = 'Update')][switch]$UpdateBaseline,
    [Parameter(ParameterSetName = 'Report')][switch]$List
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$resRoot = Join-Path $repoRoot 'app_v2/src/main/res'
$baselineFile = Join-Path $PSScriptRoot 'focus-highlight-baseline.txt'

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

$layoutDirs = @('layout', 'layout-land') | ForEach-Object { Join-Path $resRoot $_ } | Where-Object { Test-Path $_ }

$current = 0
$hits = [System.Collections.Generic.List[string]]::new()
$materialCardForegroundHits = [System.Collections.Generic.List[string]]::new()
$materialCardForegroundPattern = [regex]'(?s)<com\.google\.android\.material\.card\.MaterialCardView\b(?<attrs>[^>]*?)>'
$allResourceXml = Get-ChildItem -LiteralPath (Join-Path $repoRoot 'app_v2/src') -Recurse -File -Filter '*.xml'
foreach ($resourceXml in $allResourceXml) {
    $resourceText = Get-Content -LiteralPath $resourceXml.FullName -Raw
    foreach ($materialCard in $materialCardForegroundPattern.Matches($resourceText)) {
        if ($materialCard.Groups['attrs'].Value -notmatch 'android:foreground\s*=') { continue }
        $lineNo = ($resourceText.Substring(0, $materialCard.Index) -split "`n").Count
        $relativePath = $resourceXml.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/'
        $materialCardForegroundHits.Add(("{0}:{1}" -f $relativePath, $lineNo))
    }
}
foreach ($dir in $layoutDirs) {
    $files = Get-ChildItem -LiteralPath $dir -Recurse -File -Filter '*.xml' -ErrorAction SilentlyContinue
    foreach ($file in $files) {
        $text = Get-Content -LiteralPath $file.FullName -Raw
        foreach ($m in $rxElement.Matches($text)) {
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

            $current++
            if ($List) {
                # Line number of the element start (count newlines up to the match index).
                $lineNo = ($text.Substring(0, $m.Index) -split "`n").Count
                $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/'
                $hits.Add(("{0}:{1}  <{2}>" -f $rel, $lineNo, $tag))
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

if ($PSCmdlet.ParameterSetName -eq 'Update') {
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
        Write-Error "Refusing to RAISE baseline ($baseline -> $current). New interactive view(s) lack a focus indication - add a focus-state foreground/background, a ?attr/selectableItemBackground ripple, or use a Material/framework control."
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
