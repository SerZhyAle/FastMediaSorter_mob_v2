#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: a dialog/bottom-sheet cancel button must use the unified DialogCancel style (target: 0 one-offs).

.DESCRIPTION
    Part of S0684 (builds on S0538). Every confirm/cancel pair in a custom dialog layout, action-pair
    bottom sheet, or other inflated dialog surface must apply the named styles - confirm =
    Widget.FastMediaSorter.Button.DialogConfirm (green, wide), cancel =
    Widget.FastMediaSorter.Button.DialogCancel (soft-pink tonal, shorter + narrower), destructive confirm =
    Widget.FastMediaSorter.Button.DialogDestructive (red). New dialogs keep hand-rolling a one-off cancel
    button (plain style or none), which is exactly the drift S0684 fixes.

    This detector scans res/layout + res/layout-land for dialog_*.xml / bottom_sheet_*.xml and flags every
    <MaterialButton> that looks like a cancel/negative action (id or text says cancel/skip/dismiss) but does
    NOT carry style="@style/Widget.FastMediaSorter.Button.DialogCancel". Confirm/destructive buttons are
    excluded (a cancel signal is required, and the confirm/destructive styles short-circuit). Builder dialogs
    (MaterialAlertDialogBuilder) have no XML buttons - they inherit DialogCancel via materialAlertDialogTheme -
    so they are out of scope by construction.

    Baseline lives in scripts/quality/dialog-cancel-style-baseline.txt (single int).

    Modes:
      (default)        Report current count vs baseline.
      -Gate            Exit 1 if current > baseline (fail-closed on growth).
      -UpdateBaseline  Ratchet DOWN only (also seeds the file when missing).
      -List            Print every offending file:line.
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
$scanRoots = @(
    (Join-Path $repoRoot 'app_v2/src/main/res/layout'),
    (Join-Path $repoRoot 'app_v2/src/main/res/layout-land'),
    # S1932: the alternate layout roots were outside every layout rule but one. Safe to add here by
    # measurement rather than by hope - none of the five files in them is a dialog_* or
    # bottom_sheet_*, so the finding count cannot change today, and a dialog added there later is
    # now seen instead of silently exempt.
    (Join-Path $repoRoot 'app_v2/src/main/res/layout-sw480dp'),
    (Join-Path $repoRoot 'app_v2/src/main/res/layout-sw720dp'),
    (Join-Path $repoRoot 'app_v2/src/main/res/layout-w600dp')
)
$baselineFile = Join-Path $PSScriptRoot 'dialog-cancel-style-baseline.txt'

$cancelStyle = '@style/Widget.FastMediaSorter.Button.DialogCancel'

# Surfaces deliberately exempt from the dialog action pair (S0538 "Known-exempt" + S0567 selection pickers):
# icon-only filter dialogs, the generic Clear/Cancel selection picker, and the network-discovery scan control.
# These are not OK/Cancel pairs, so the pink tonal cancel slot does not apply.
$exemptFiles = @(
    'dialog_filter.xml',
    'dialog_filter_resource.xml',
    'dialog_list_selection.xml',
    'dialog_network_discovery.xml'
)

# Element opener (attribute order varies; FQCN or bare class are both accepted).
$rxButton = [regex]'<(?:com\.google\.android\.material\.button\.)?MaterialButton\b'
$rxId     = [regex]'android:id\s*=\s*"([^"]*)"'
$rxText   = [regex]'android:text\s*=\s*"([^"]*)"'
$rxStyle  = [regex]'\bstyle\s*=\s*"([^"]*)"'
# Cancel signal (id or text). "negative" is intentionally NOT a signal - it collides with image-effect
# buttons (e.g. dialog_image_edit btnNegative = colour-negative filter, not a cancel). Confirm-ish words
# are guarded separately below.
$rxCancelSignal  = [regex]'(?i)cancel|skip|dismiss|not_?now|no_thanks'
# Affirmative / destructive guard - if the id says this, it is never a cancel violation.
$rxConfirmGuard  = [regex]'(?i)ok|apply|confirm|save|grant|yes|continue|done|delete|remove|clear|select|download|positive'

$current = 0
$hits = [System.Collections.Generic.List[string]]::new()

foreach ($root in $scanRoots) {
    if (-not (Test-Path $root)) { continue }
    $files = Get-ChildItem -LiteralPath $root -File -Filter '*.xml' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^(dialog_|bottom_sheet_).*\.xml$' -and $_.Name -notin $exemptFiles }
    foreach ($file in $files) {
        $text = Get-Content -LiteralPath $file.FullName -Raw
        if ([string]::IsNullOrEmpty($text)) { continue }
        foreach ($bm in $rxButton.Matches($text)) {
            # Slice the open tag: from the element start to the first '>' (attribute values never contain '>').
            $end = $text.IndexOf('>', $bm.Index)
            if ($end -lt 0) { continue }
            $element = $text.Substring($bm.Index, $end - $bm.Index + 1)

            $idMatch = $rxId.Match($element)
            $id = if ($idMatch.Success) { $idMatch.Groups[1].Value } else { '' }
            $textMatch = $rxText.Match($element)
            $btnText = if ($textMatch.Success) { $textMatch.Groups[1].Value } else { '' }
            $styleMatch = $rxStyle.Match($element)
            $style = if ($styleMatch.Success) { $styleMatch.Groups[1].Value } else { '' }

            # 1) Must look like a cancel/negative button (id or text), else not our concern.
            $isCancelSignal = $rxCancelSignal.IsMatch($id) -or $rxCancelSignal.IsMatch($btnText)
            if (-not $isCancelSignal) { continue }

            # 2) Affirmative/destructive guard: never flag a confirm/destructive slot.
            if ($style -eq '@style/Widget.FastMediaSorter.Button.DialogConfirm' -or
                $style -eq '@style/Widget.FastMediaSorter.Button.DialogDestructive') { continue }
            if ($rxConfirmGuard.IsMatch($id)) { continue }

            # 3) Compliant cancel already uses the unified style -> pass.
            if ($style -eq $cancelStyle) { continue }

            # Violation: a one-off cancel button.
            $current++
            if ($List) {
                $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/'
                $lineNo = ($text.Substring(0, $bm.Index) -split "`n").Count
                $label = if ($id) { $id } else { '(no id)' }
                $hits.Add(("{0}:{1} [{2}]" -f $rel, $lineNo, $label))
            }
        }
    }
}

if ($List) {
    foreach ($h in $hits) { Write-Host $h }
    Write-Host ''
}

if ($PSCmdlet.ParameterSetName -eq 'Update') {
    if (-not (Test-Path $baselineFile)) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "dialog-cancel-style baseline SEEDED: $current"
        exit 0
    }
    $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
    if ($current -lt $baseline) {
        Set-Content -LiteralPath $baselineFile -Value "$current"
        Write-Host "dialog-cancel-style baseline ratcheted DOWN: $baseline -> $current"
    }
    elseif ($current -eq $baseline) {
        Write-Host "dialog-cancel-style baseline unchanged ($baseline)"
    }
    else {
        Write-Error "Refusing to RAISE baseline ($baseline -> $current). Apply style=`"$cancelStyle`" to the cancel button - see docs/ARCHITECTURE.md `"Button Taxonomy`"."
        exit 1
    }
    exit 0
}

if (-not (Test-Path $baselineFile)) {
    Write-Host "dialog-cancel-style: NO BASELINE yet | actual $current - run -UpdateBaseline to seed."
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("dialog-cancel-style in dialog/bottom-sheet layouts: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
if ($Gate -and $current -gt $baseline) {
    Write-Host "FAIL: a dialog/bottom-sheet cancel button is not using @style/Widget.FastMediaSorter.Button.DialogCancel. Apply the S0538/S0684 cancel slot style (soft-pink tonal, shorter + narrower) - see docs/ARCHITECTURE.md `"Button Taxonomy`"."
    exit 1
}
if ($current -lt $baseline) {
    Write-Host "Note: count is below baseline - run -UpdateBaseline to ratchet the cap down."
}
exit 0
