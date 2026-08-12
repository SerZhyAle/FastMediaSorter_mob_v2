#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: dialogs shown with a bare .show() must never grow.

.DESCRIPTION
    S1456: thin wrapper. The rule itself - what counts as a violation, which files it reads,
    which baseline it ratchets against - lives once in scripts/quality/lib/source-matchers.ps1
    and is executed by assert-source-gates.ps1, which applies every lexical rule over a SINGLE
    walk of the tree instead of one walk per gate.

    A violation is a `MaterialAlertDialogBuilder` / `AlertDialog.Builder` construction whose
    chain terminates in `.show()` rather than `.showBoundTo(owner)`, counted in two shapes: the
    fluent chain that ends in the call, and the builder assigned to a name whose `.create()`
    result is shown a few lines further down. A bare show() throws the returned dialog away, so
    nothing can dismiss it when the host dies and the window outlives the destroyed Fragment and
    Activity - the leak S1447 fixed inside ui/settings/helpers only. The cure on both receivers
    lives in app_v2/src/main/java/com/sza/fastmediasorter/util/LifecycleDialogExt.kt.

    Every shipped source set is judged, not only src/main: the leak reaches launcherEnabled,
    noLegal and screenCapture. Test source sets are out - the rule judges shipped hosts.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-untracked-dialogs.ps1
    pwsh -NoProfile -File scripts/quality/assert-untracked-dialogs.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-untracked-dialogs.ps1 -List

.NOTES
    Exit codes: 0 at or below baseline, 1 above baseline under -Gate, 2 cannot verify.
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

$forward = @{ Only = 'untracked-dialog' }
if ($Gate) { $forward.Gate = $true }
if ($UpdateBaseline) { $forward.UpdateBaseline = $true }
if ($List) { $forward.List = $true }
if ($ChangedFiles) { $forward.ChangedFiles = $ChangedFiles }

& (Join-Path $PSScriptRoot 'assert-source-gates.ps1') @forward
exit $LASTEXITCODE
