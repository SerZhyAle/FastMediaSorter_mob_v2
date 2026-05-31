# ChangeSource.ps1 (S0313) - exports Get-ChangedMainKotlin.
#
# Resolves the set of app_v2/src/main/java Kotlin files the guard should scan.
# Two sources:
#   - diff (default): `git diff --name-only` plus `--cached`, optionally against
#     a base -Ref, filtered to the main-source Kotlin root.
#   - explicit: an -Path string array (caller asserted intent), filtered to the
#     same root.
# Flavor source sets (src/<flavor>/java) are never returned - isolation there is
# correct by construction. This module reads no file contents.

Set-StrictMode -Version Latest

# The only source root the guard ever scans. Stored with forward slashes; all
# comparisons normalise '\' to '/' first so Windows and git paths both match.
$script:MainJavaRoot = 'app_v2/src/main/java'

function Test-IsMainKotlin {
    # True when a repo-relative path is a .kt file under the main-source Java root.
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string] $RelPath)

    if ([string]::IsNullOrWhiteSpace($RelPath)) { return $false }
    $norm = $RelPath -replace '\\', '/'
    $norm = $norm.TrimStart('./')
    return ($norm -like "$script:MainJavaRoot/*") -and ($norm -match '\.kt$')
}

function ConvertTo-RepoRelative {
    # Normalises a path to a repo-relative, forward-slash form. git already emits
    # repo-relative paths; an explicit -Path may be absolute or use backslashes.
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $InputPath,
        [Parameter(Mandatory)][string] $RepoRoot
    )
    $p = $InputPath -replace '\\', '/'
    $rootNorm = ($RepoRoot -replace '\\', '/').TrimEnd('/')
    if ($p -like "$rootNorm/*") {
        $p = $p.Substring($rootNorm.Length + 1)
    }
    return $p.TrimStart('./')
}

function Get-ChangedMainKotlin {
    # Returns one record per main-source Kotlin file to scan:
    #   file   - repo-relative, forward-slash path
    #   source - 'explicit' when supplied via -Path, else 'diff'
    #
    # -Path  explicit file list; bypasses git entirely.
    # -Ref   base ref to diff against (e.g. 'origin/main', 'HEAD~1'); default is
    #        the working-tree view: unstaged + staged changes.
    [CmdletBinding()]
    param(
        [string[]] $Path,
        [string] $Ref
    )

    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path

    # --- Explicit path mode -------------------------------------------------
    if ($PSBoundParameters.ContainsKey('Path')) {
        $out = @()
        foreach ($raw in @($Path)) {
            if ([string]::IsNullOrWhiteSpace($raw)) { continue }
            $rel = ConvertTo-RepoRelative -InputPath $raw -RepoRoot $repoRoot
            if (Test-IsMainKotlin -RelPath $rel) {
                $out += [pscustomobject]@{ file = ($rel -replace '\\', '/'); source = 'explicit' }
            } else {
                # Outside the main-source Kotlin root: record so the driver can
                # treat an explicit out-of-root path as a usage error (exit 2).
                $out += [pscustomobject]@{ file = ($rel -replace '\\', '/'); source = 'explicit-rejected' }
            }
        }
        return $out
    }

    # --- Diff mode ----------------------------------------------------------
    $names = @()
    if ([string]::IsNullOrWhiteSpace($Ref)) {
        # Unstaged + staged working-tree changes.
        $names += (git -C $repoRoot diff --name-only) 2>$null
        $names += (git -C $repoRoot diff --name-only --cached) 2>$null
    } else {
        # Everything that differs from the base ref, including the working tree.
        $names += (git -C $repoRoot diff --name-only $Ref) 2>$null
    }

    $seen = [System.Collections.Generic.HashSet[string]]::new()
    $out = @()
    foreach ($n in $names) {
        if ([string]::IsNullOrWhiteSpace($n)) { continue }
        $rel = ($n -replace '\\', '/').TrimStart('./')
        if (-not (Test-IsMainKotlin -RelPath $rel)) { continue }
        if ($seen.Add($rel)) {
            $out += [pscustomobject]@{ file = $rel; source = 'diff' }
        }
    }
    return $out
}
