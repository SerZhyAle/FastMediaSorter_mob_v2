#requires -Version 7.0
<#
.SYNOPSIS
    Deterministic Kotlin import sorter based on [string]::CompareOrdinal.

.DESCRIPTION
    Sorts Kotlin file imports according to Detekt/Ktlint ImportOrdering rules:
      1. Default imports (everything not java, javax, kotlin, or alias)
      2. java.* (without alias)
      3. javax.* (without alias)
      4. kotlin.* (without alias; kotlinx.* stays in default group 1)
      5. Aliases (any import with ' as ') at the very end.

    Within each group, lines are sorted strictly using [string]::CompareOrdinal (ASCII).
    Eliminates duplicate imports.

    NOTE ON COMMENTS: Detekt/Ktlint ImportOrdering strictly forbids comments inside
    the import block ("no autocorrection due to comments in the import list").
    If comments are detected in the import block, this script refuses to modify the file
    and instructs the developer to move comments above package or into code.

.PARAMETER FilePath
    Path to a single .kt file to format.

.PARAMETER Files
    Array of .kt file paths or comma-separated string.

.PARAMETER Check
    When set, does not modify files; exits 1 if any file needs reordering or has forbidden comments, 0 if clean.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/format-kotlin-imports.ps1 -FilePath "app_v2/src/main/.../Foo.kt"
    pwsh -NoProfile -File scripts/utils/format-kotlin-imports.ps1 -Files "a.kt,b.kt" -Check

.OUTPUTS
    Exit 0 - every file is clean or was rewritten.
    Exit 1 - -Check only: at least one file needs formatting or carries a forbidden comment.
    Exit 2 - write mode: at least one file was refused (comment inside the import list); nothing
             was rewritten in those files.
#>
[CmdletBinding()]
param(
    [Alias('File')]
    [string] $FilePath,
    [string[]] $Files,
    [switch] $Check
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:refusedCount = 0

function Get-ImportGroup {
    param([string] $ImportLine)
    
    $cleanLine = $ImportLine.Trim()
    
    # Alias group: any import with ' as ' goes to group 5 (aliases at end)
    if ($cleanLine -match '\s+as\s+') {
        return 5
    }
    
    $pkg = if ($cleanLine.StartsWith('import ')) {
        $cleanLine.Substring(7).Trim()
    } else {
        $cleanLine
    }
    
    if ($pkg -like 'java.*' -and $pkg -notlike 'javax.*') {
        return 2
    }
    elseif ($pkg -like 'javax.*') {
        return 3
    }
    elseif ($pkg -like 'kotlin.*' -and $pkg -notlike 'kotlinx.*') {
        return 4
    }
    else {
        # Group 1: all other imports including kotlinx, android, androidx, com, org, etc.
        return 1
    }
}

function Sort-ImportLines {
    param([string[]] $Lines)
    
    $items = [System.Collections.Generic.List[string]]::new()
    
    foreach ($l in $Lines) {
        $trimmed = $l.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed)) {
            continue
        }
        if ($trimmed.StartsWith('import ')) {
            $items.Add($trimmed)
        }
    }
    
    if ($items.Count -le 1) {
        return $items.ToArray()
    }
    
    # Deduplicate imports strictly
    $distinct = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $uniqueList = [System.Collections.Generic.List[string]]::new()
    foreach ($item in $items) {
        if ($distinct.Add($item)) {
            $uniqueList.Add($item)
        }
    }
    
    $group1 = [System.Collections.Generic.List[string]]::new()
    $group2 = [System.Collections.Generic.List[string]]::new()
    $group3 = [System.Collections.Generic.List[string]]::new()
    $group4 = [System.Collections.Generic.List[string]]::new()
    $group5 = [System.Collections.Generic.List[string]]::new()
    
    foreach ($imp in $uniqueList) {
        $g = Get-ImportGroup -ImportLine $imp
        switch ($g) {
            1 { $group1.Add($imp) }
            2 { $group2.Add($imp) }
            3 { $group3.Add($imp) }
            4 { $group4.Add($imp) }
            5 { $group5.Add($imp) }
        }
    }
    
    # Sort each group strictly with [string]::CompareOrdinal
    $comparison = [System.Comparison[string]]{
        param($a, $b)
        return [string]::CompareOrdinal($a, $b)
    }
    
    $group1.Sort($comparison)
    $group2.Sort($comparison)
    $group3.Sort($comparison)
    $group4.Sort($comparison)
    $group5.Sort($comparison)
    
    $result = [System.Collections.Generic.List[string]]::new()
    $groups = @($group1, $group2, $group3, $group4, $group5)
    
    foreach ($grp in $groups) {
        foreach ($item in $grp) {
            $result.Add($item)
        }
    }
    
    return $result.ToArray()
}

function Format-SingleKotlinFile {
    param(
        [string] $Path,
        [switch] $CheckOnly
    )
    
    if (-not (Test-Path -Path $Path -PathType Leaf)) {
        return $false
    }
    if ($Path -notlike '*.kt') {
        return $false
    }
    
    $content = Get-Content -Path $Path -Raw -Encoding utf8
    $lines = $content -split "\r?\n"
    
    $firstImportIdx = -1
    $lastImportIdx = -1
    
    for ($i = 0; $i -lt $lines.Length; $i++) {
        $trimmed = $lines[$i].Trim()
        if ($trimmed.StartsWith('import ')) {
            if ($firstImportIdx -eq -1) {
                $firstImportIdx = $i
            }
            $lastImportIdx = $i
        }
        elseif ($firstImportIdx -ne -1 -and $trimmed -ne '' -and -not $trimmed.StartsWith('//') -and -not $trimmed.StartsWith('/*') -and -not $trimmed.StartsWith('*')) {
            # Reached code/class after imports block
            break
        }
    }
    
    if ($firstImportIdx -eq -1) {
        # No imports found
        return $false
    }
    
    # Check for comments inside the import block range
    for ($i = $firstImportIdx; $i -le $lastImportIdx; $i++) {
        $trimmed = $lines[$i].Trim()
        if ($trimmed.StartsWith('//') -or $trimmed.StartsWith('/*') -or $trimmed.StartsWith('*') -or ($trimmed -match '^import\s+.*//')) {
            Write-Host "REFUSED: $Path - comments detected in import block (line $($i + 1)). Move comments above package declaration or into code body." -ForegroundColor Red
            $script:refusedCount++
            return $true # Signals violation / error
        }
    }
    
    $rawImportBlock = $lines[$firstImportIdx..$lastImportIdx]
    $sortedImportLines = Sort-ImportLines -Lines $rawImportBlock
    
    $prefix = if ($firstImportIdx -gt 0) { $lines[0..($firstImportIdx - 1)] } else { @() }
    $suffix = if ($lastImportIdx + 1 -lt $lines.Length) { $lines[($lastImportIdx + 1)..($lines.Length - 1)] } else { @() }
    
    # Strip any trailing blank lines from prefix directly before imports
    while ($prefix.Length -gt 0 -and [string]::IsNullOrWhiteSpace($prefix[$prefix.Length - 1])) {
        $prefix = if ($prefix.Length -gt 1) { $prefix[0..($prefix.Length - 2)] } else { @() }
    }
    
    # Strip any leading blank lines from suffix directly after imports
    while ($suffix.Length -gt 0 -and [string]::IsNullOrWhiteSpace($suffix[0])) {
        $suffix = if ($suffix.Length -gt 1) { $suffix[1..($suffix.Length - 1)] } else { @() }
    }
    
    $newLines = [System.Collections.Generic.List[string]]::new()
    foreach ($p in $prefix) { $newLines.Add($p) }
    if ($prefix.Length -gt 0) {
        $newLines.Add('') # blank line after package
    }
    foreach ($imp in $sortedImportLines) { $newLines.Add($imp) }
    if ($suffix.Length -gt 0) {
        $newLines.Add('') # blank line before body
    }
    foreach ($s in $suffix) { $newLines.Add($s) }
    
    # Preserve original line endings (CRLF on Windows / LF)
    $delimiter = if ($content.Contains("`r`n")) { "`r`n" } else { "`n" }
    $newContent = ($newLines -join $delimiter)
    if ($content.EndsWith($delimiter) -and -not $newContent.EndsWith($delimiter)) {
        $newContent += $delimiter
    }
    
    if ($content -eq $newContent) {
        return $false
    }
    
    if ($CheckOnly) {
        Write-Host "NEEDS_FORMATTING: $Path" -ForegroundColor Yellow
        return $true
    }
    
    [System.IO.File]::WriteAllText($Path, $newContent, [System.Text.UTF8Encoding]::new($false))
    Write-Host "FORMATTED: $Path" -ForegroundColor Green
    return $true
}

# Resolve candidate files
$targetFiles = [System.Collections.Generic.List[string]]::new()
if ($FilePath) {
    $targetFiles.Add($FilePath)
}
if ($Files) {
    foreach ($f in $Files) {
        if ($f) {
            foreach ($part in ($f -split ',')) {
                $trimmed = $part.Trim()
                if ($trimmed) { $targetFiles.Add($trimmed) }
            }
        }
    }
}

if ($targetFiles.Count -eq 0) {
    Write-Host "No files provided to format-kotlin-imports.ps1" -ForegroundColor Gray
    exit 0
}

$anyModified = $false
foreach ($file in $targetFiles) {
    $resolved = if ([System.IO.Path]::IsPathRooted($file)) { $file } else { Join-Path (Get-Location) $file }
    $modified = Format-SingleKotlinFile -Path $resolved -CheckOnly:$Check
    if ($modified) {
        $anyModified = $true
    }
}

if ($anyModified) {
    if ($Check) {
        Write-Error "format-kotlin-imports: one or more Kotlin files require formatting or contain forbidden comments in imports." -ErrorAction Continue
        exit 1
    }
}

# A refusal that exits 0 is indistinguishable from "nothing to do", so a caller that formats a
# set and reads only the exit code would report success for a file it never touched. -Check keeps
# exit 1 for every not-clean reason; the write path reports the refusal on its own code.
if ($script:refusedCount -gt 0 -and -not $Check) {
    $refusalMessage = "format-kotlin-imports: $script:refusedCount file(s) refused - a comment inside " +
    "the import list makes ImportOrdering unsatisfiable (detekt says 'no autocorrection due to " +
    "comments in the import list'), so nothing was rewritten in them."
    Write-Error $refusalMessage -ErrorAction Continue
    exit 2
}

exit 0
