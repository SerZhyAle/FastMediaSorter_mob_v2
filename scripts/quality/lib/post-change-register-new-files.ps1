#requires -Version 7.0
<#
.SYNOPSIS
    post-change.ps1 library: register the untracked files of the changed set with git before the gates.

.DESCRIPTION
    Dot-sourced by scripts/post-change.ps1, never invoked - the function reads $root and $changedFiles
    from the caller's scope.

    S3515: suite-tracked (S2411) and dotsource-tracked (S2616) refuse a closure whose new runner or
    dot-source target is absent from the git index, and print `git add -- <path>` as the remedy. The
    file is always one this ticket just created and named in -Files, so the agent ran the printed
    command and then the whole closure again - measured on S3510, the second run cost what the first
    did. Registering the file here makes the first run the last one.

    INTENT-TO-ADD, NOT A STAGED BLOB. `git add --intent-to-add` records the path with no content: the
    file becomes known to the index, which is exactly what both gates ask, while its content stays
    unstaged for `/git` to commit with everything else. Nothing is committed and no content is staged.

    ONLY THE NAMED SET. A path the caller did not name is never touched: another ticket's untracked
    work in the same tree is its own closure's business, and a gate still refuses a dot-source target
    this set does not carry. Gitignored paths and the gitignored PLAN/ and temp/ trees are skipped.

.NOTES
    Exit codes (CLAUDE.md Rule 7): none - this file is dot-sourced and defines a function. It never
    exits; a git failure is reported as one warning line and the gates judge the index as they find it.
#>

function Register-UntrackedChangedFiles {
    $rootPrefix = (($root -replace '\\', '/').TrimEnd('/')) + '/'
    $candidates = [System.Collections.Generic.List[string]]::new()
    foreach ($entry in $changedFiles) {
        $rel = ([string]$entry -replace '\\', '/') -replace '^\./', ''
        if ($rel.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) { $rel = $rel.Substring($rootPrefix.Length) }
        if ($rel -match '^(PLAN|temp)/' -or $rel -match '^[A-Za-z]:/' -or $rel.StartsWith('../')) { continue }
        if (-not (Test-Path -LiteralPath (Join-Path $root $rel) -PathType Leaf)) { continue }
        $candidates.Add($rel)
    }
    if ($candidates.Count -eq 0) { return }

    $untracked = @(& git -C $root ls-files --others --exclude-standard -- @($candidates) 2> $null |
            Where-Object { $_ })
    if ($LASTEXITCODE -ne 0) {
        Write-Host '  [register-new-files] WARN - git ls-files failed; the tracked gates judge the index as it is.' -ForegroundColor Yellow
        return
    }
    if ($untracked.Count -eq 0) { return }

    & git -C $root add --intent-to-add -- @($untracked) 2> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Host ('  [register-new-files] WARN - git add --intent-to-add failed for: {0}' -f ($untracked -join ', ')) -ForegroundColor Yellow
        return
    }
    Write-Host ('  [register-new-files] registered with git (intent-to-add): {0}' -f ($untracked -join ', ')) -ForegroundColor DarkGray
}
