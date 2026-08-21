#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: catches in coroutine code that swallow CancellationException must never grow.

.DESCRIPTION
    S1363: thin wrapper. The rule itself - what counts as a violation, which files it reads,
    which baseline it ratchets against - lives once in scripts/quality/lib/source-matchers.ps1
    and is executed by assert-source-gates.ps1, which applies every lexical rule over a SINGLE
    walk of the tree instead of one walk per gate.

    A violation is a catch reachable from a coroutine whose chain has no earlier
    `catch (e: CancellationException)` arm and whose block does not open with
    `e.rethrowIfCancellation()` (core/util/CoroutineExt.kt). Both cures are accepted; everything
    else turns normal cancellation into a logged error and a domain failure result.

    Two arm shapes count. A broad `catch (e: Exception)` / `catch (e: Throwable)` catches
    cancellation by being wide enough. S1889 added the second: `catch (e: IllegalStateException)`
    and `catch (e: RuntimeException)`, which catch it by being its SUPERTYPES - kotlinx's
    CancellationException is a typealias for java.util.concurrent's, which extends
    IllegalStateException. That shape also defeats a cure placed below it, because the earlier arm
    takes the cancellation first. CloudMediaScanner shipped exactly that: a live
    `rethrowIfCancellation()` in the broad arm, unreachable behind an authentication arm above it,
    and this gate read the file as clean the whole time.

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

$forward = @{ Only = 'swallowed-cancellation' }
if ($Gate) { $forward.Gate = $true }
if ($UpdateBaseline) { $forward.UpdateBaseline = $true }
if ($List) { $forward.List = $true }
if ($ChangedFiles) { $forward.ChangedFiles = $ChangedFiles }

& (Join-Path $PSScriptRoot 'assert-source-gates.ps1') @forward
exit $LASTEXITCODE
