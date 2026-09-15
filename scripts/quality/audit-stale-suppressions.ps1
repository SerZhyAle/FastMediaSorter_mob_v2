<#
.SYNOPSIS
    Finds compiler names inside @Suppress that no longer hide any warning, and removes them (S3152).

.DESCRIPTION
    Kotlin reports nothing for an unnecessary suppression, so a stale DEPRECATION stays forever and
    silently hides the next deprecated call written inside its scope. The only mechanical answer is a
    compile with the names masked. Four verbs, run in this order:

      Mask     Rename every compiler name inside a @Suppress to AUDIT_MASKED_<NAME> under -Roots. The
               line count does not change, so a warning's line number still points at the same code.
               Writes manifest.json (annotation, scope, post-mask hash) and a backup of every original.
      Analyze  Read the compile logs of the masked tree (-Logs) and decide, per annotation, which names
               hide nothing. Writes report.json.
      Apply    Drop the stale masked names, unmask the rest, delete an annotation left empty.
      Restore  Unmask every name and remove nothing - the rollback path.

    Apply and Restore rewrite the CURRENT file content by marker and never copy the backup back: a
    sibling session may have edited a masked file in the meantime, and a restore from the copy would
    erase that edit. A file whose hash changed since Mask keeps every name.

    Scope is estimated from indentation (the sources are ktlint-formatted), from the annotation line
    to the end of the annotated element. An over-estimate can only keep a name, never drop one.

.PARAMETER Roots
    Comma-separated source roots relative to -RepoRoot (Mask). `pwsh -File` cannot pass an array.

.PARAMETER Logs
    Comma-separated compile logs and assert-detekt logs of the masked tree (Analyze). Each must carry
    at least one `w: file:///` or `assert-detekt:` finding line; a compile log without one most likely
    came from an UP-TO-DATE compile task and would make every name look stale.

.NOTES
    Exit codes:
      0 - the verb completed.
      2 - could not verify: missing argument, manifest, report or log; a log with no warning line; a
          tree that already carries markers; a marker left behind after Apply/Restore.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Mask', 'Analyze', 'Apply', 'Restore')]
    [string]$Verb,
    [string]$Roots,
    [string]$Logs,
    [string]$OutDir = 'temp/scratch/suppression-audit',
    [string]$RepoRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $RepoRoot) { $RepoRoot = Join-Path $PSScriptRoot '..' '..' }
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path.TrimEnd('\', '/')
if (-not [System.IO.Path]::IsPathRooted($OutDir)) { $OutDir = Join-Path $RepoRoot $OutDir }
$manifestPath = Join-Path $OutDir 'manifest.json'
$reportPath = Join-Path $OutDir 'report.json'

$script:Marker = 'AUDIT_MASKED_'
# First match wins, and an override warning also contains the word "deprecated", so it is listed
# first. Names = every suppression name that can silence the warning, all of which it keeps alive.
$script:Kinds = @(
    @{ Pattern = 'overrides (a )?deprecated member'; Names = @('OVERRIDE_DEPRECATION', 'DEPRECATION') }
    @{ Pattern = 'is deprecated|Deprecated in Java'; Names = @('DEPRECATION') }
    @{ Pattern = '^Unchecked cast'; Names = @('UNCHECKED_CAST') }
    @{ Pattern = "^Parameter '.+' is never used"; Names = @('UNUSED_PARAMETER') }
    @{ Pattern = "^Variable '.+' is (never used|assigned but never accessed)"; Names = @('UNUSED_VARIABLE') }
    @{ Pattern = '^Unreachable code'; Names = @('UNREACHABLE_CODE') }
    @{ Pattern = "^Condition( '.+')? is always '(true|false)'|senseless"; Names = @('SENSELESS_COMPARISON') }
    @{ Pattern = 'Elvis operator \(\?:\) always returns the left operand'; Names = @('USELESS_ELVIS') }
)
$script:Names = @($script:Kinds | ForEach-Object { $_.Names } | Select-Object -Unique)
$script:AnnotationRegex = [regex]'@(?<prefix>(?:file:)?Suppress(?:Warnings)?)\s*\((?<args>[^()]*)\)'
$script:TokenRegex = [regex]('"(?<name>' + ($script:Names -join '|') + ')"')
$script:WarningRegex = [regex]'^w: file:///(?<path>.+?\.kts?):(?<line>\d+):\d+ (?<message>.*)$'
# detekt honours a compiler name as an alias of its own rule, and K2 no longer reports UNUSED_PARAMETER
# at all, so without these every such name looks stale while detekt still needs it. Rules not listed
# are ignored: their findings say nothing about a compiler name.
$script:DetektRegex = [regex]'^assert-detekt:\s+(?<path>\S+?\.kts?):(?<line>\d+):\d+ - (?<rule>\w+) - '
$script:DetektAliases = @{
    UnusedParameter     = @('UNUSED_PARAMETER')
    UnusedPrivateMember = @('UNUSED_PARAMETER')
    UnusedVariable      = @('UNUSED_VARIABLE')
    UnreachableCode     = @('UNREACHABLE_CODE')
    UnsafeCast          = @('UNCHECKED_CAST')
    Deprecation         = @('DEPRECATION')
}

function Exit-CouldNotVerify([string]$reason) {
    Write-Host "audit-stale-suppressions: COULD NOT VERIFY - $reason" -ForegroundColor Red
    exit 2
}

function Split-List([string]$value) {
    if (-not $value) { return @() }
    return @($value.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

function Read-SourceFile([string]$path) {
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
    $offset = if ($hasBom) { 3 } else { 0 }
    return @{ Text = [System.Text.Encoding]::UTF8.GetString($bytes, $offset, $bytes.Length - $offset); Bom = $hasBom }
}

function Write-SourceFile([string]$path, [string]$text, [bool]$bom) {
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($bom)))
}

function Get-Sha([string]$path) {
    return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
}

function Get-LineStart([string]$text, [int]$offset) {
    if ($offset -le 0) { return 0 }
    return $text.LastIndexOf("`n", $offset - 1) + 1
}

function Get-LineEnd([string]$text, [int]$offset) {
    $newline = $text.IndexOf("`n", $offset)
    if ($newline -lt 0) { return $text.Length }
    return $newline
}

function Get-LineIndex([string]$text, [int]$offset) {
    $count = 0
    $next = $text.IndexOf("`n")
    while ($next -ge 0 -and $next -lt $offset) {
        $count++
        $next = $text.IndexOf("`n", $next + 1)
    }
    return $count
}

function Get-Indent([string]$line) {
    return $line.Length - $line.TrimStart(' ', "`t").Length
}

function Test-CommentText([string]$trimmed) {
    return $trimmed.StartsWith('//') -or $trimmed.StartsWith('/*') -or $trimmed.StartsWith('*')
}

function Get-SuppressMatches([string]$text, [string]$argsFilter) {
    $found = foreach ($match in $script:AnnotationRegex.Matches($text)) {
        $lineStart = Get-LineStart $text $match.Index
        $before = $text.Substring($lineStart, $match.Index - $lineStart)
        if ($before.Contains('//') -or (Test-CommentText $before.TrimStart())) { continue }
        if ($match.Groups['args'].Value -notmatch $argsFilter) { continue }
        $match
    }
    return @($found)
}

# Returns 1-based inclusive [start, end]. The header is the first code line after the annotation at
# the annotation's own indent, skipping stacked annotations, their indented arguments and comments.
function Get-AnnotationScope([string]$text, [System.Text.RegularExpressions.Match]$match, [string[]]$lines) {
    if ($match.Groups['prefix'].Value.StartsWith('file:')) { return @(1, $lines.Count) }
    $startIdx = Get-LineIndex $text $match.Index
    $endOffset = $match.Index + $match.Length
    $endIdx = Get-LineIndex $text ($endOffset - 1)
    $after = $text.Substring($endOffset, (Get-LineEnd $text $endOffset) - $endOffset)
    $after = ($after -replace '//.*$', '').Trim()
    $base = Get-Indent $lines[$startIdx]

    $header = $endIdx
    if (-not $after) {
        for ($j = $endIdx + 1; $j -lt $lines.Count; $j++) {
            $trimmed = $lines[$j].Trim()
            if (-not $trimmed -or $trimmed.StartsWith('@') -or (Test-CommentText $trimmed)) { continue }
            $indent = Get-Indent $lines[$j]
            if ($indent -gt $base -or $trimmed -match '^[\}\)\]]') { continue }
            if ($indent -eq $base) { $header = $j }
            break
        }
    }

    $end = $header
    for ($j = $header + 1; $j -lt $lines.Count; $j++) {
        $trimmed = $lines[$j].Trim()
        if (-not $trimmed) { continue }
        $indent = Get-Indent $lines[$j]
        if ($indent -gt $base -or ($indent -eq $base -and $trimmed -match '^[\}\)\]]')) {
            $end = $j
        } else {
            break
        }
    }
    return @(($startIdx + 1), ($end + 1))
}

function Get-WarningNames([string]$message) {
    foreach ($kind in $script:Kinds) {
        if ($message -match $kind.Pattern) { return , $kind.Names }
    }
    return $null
}

function Invoke-Mask {
    $rootList = @(Split-List $Roots)
    if ($rootList.Count -eq 0) { Exit-CouldNotVerify '-Roots is required for Mask' }

    $candidates = [System.Collections.Generic.List[string]]::new()
    foreach ($root in $rootList) {
        $rootPath = Join-Path $RepoRoot $root
        if (-not (Test-Path -LiteralPath $rootPath)) { Exit-CouldNotVerify "root not found: $root" }
        foreach ($file in Get-ChildItem -LiteralPath $rootPath -Recurse -File -Filter '*.kt') {
            $raw = [System.IO.File]::ReadAllText($file.FullName)
            if ($raw.Contains($script:Marker)) {
                Exit-CouldNotVerify "marker already present in $($file.FullName) - run -Verb Restore first"
            }
            if ($raw.Contains('Suppress')) { $candidates.Add($file.FullName) }
        }
    }

    $namePattern = '"(' + ($script:Names -join '|') + ')"'
    $fileRecords = [System.Collections.Generic.List[object]]::new()
    $entries = [System.Collections.Generic.List[object]]::new()
    $nameCount = 0
    foreach ($path in $candidates) {
        $source = Read-SourceFile $path
        $text = $source.Text
        $found = @(Get-SuppressMatches $text $namePattern)
        if ($found.Count -eq 0) { continue }

        $relative = [System.IO.Path]::GetRelativePath($RepoRoot, $path).Replace('\', '/')
        $lines = $text -split "`n"
        $ordinal = 0
        foreach ($match in $found) {
            $scope = Get-AnnotationScope $text $match $lines
            $tokens = @($script:TokenRegex.Matches($match.Groups['args'].Value) | ForEach-Object { $_.Groups['name'].Value })
            $nameCount += $tokens.Count
            $entries.Add([ordered]@{
                    file       = $relative
                    ordinal    = $ordinal
                    line       = (Get-LineIndex $text $match.Index) + 1
                    tokens     = $tokens
                    scopeStart = $scope[0]
                    scopeEnd   = $scope[1]
                })
            $ordinal++
        }

        $masked = $text
        for ($i = $found.Count - 1; $i -ge 0; $i--) {
            $group = $found[$i].Groups['args']
            $newArgs = $script:TokenRegex.Replace($group.Value, ('"' + $script:Marker + '${name}"'))
            $masked = $masked.Substring(0, $group.Index) + $newArgs + $masked.Substring($group.Index + $group.Length)
        }

        $backupPath = Join-Path (Join-Path $OutDir 'backup') $relative
        New-Item -ItemType Directory -Force -Path (Split-Path $backupPath) | Out-Null
        Copy-Item -LiteralPath $path -Destination $backupPath -Force
        Write-SourceFile $path $masked $source.Bom
        $fileRecords.Add([ordered]@{ file = $relative; hash = (Get-Sha $path) })
    }

    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
    $manifest = [ordered]@{
        created = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
        roots   = $rootList
        files   = $fileRecords.ToArray()
        entries = $entries.ToArray()
    }
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manifestPath -Encoding utf8
    Write-Host "audit-stale-suppressions: masked $nameCount name(s) in $($entries.Count) annotation(s) across $($fileRecords.Count) file(s); manifest $manifestPath"
}

function Read-Manifest {
    if (-not (Test-Path -LiteralPath $manifestPath)) { Exit-CouldNotVerify "manifest not found: $manifestPath - run -Verb Mask first" }
    return Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json -AsHashtable
}

function Invoke-Analyze {
    $manifest = Read-Manifest
    $logList = @(Split-List $Logs)
    if ($logList.Count -eq 0) { Exit-CouldNotVerify '-Logs is required for Analyze' }

    $rootForward = $RepoRoot.Replace('\', '/') + '/'
    $warnings = @{}
    foreach ($log in $logList) {
        $logPath = if ([System.IO.Path]::IsPathRooted($log)) { $log } else { Join-Path $RepoRoot $log }
        if (-not (Test-Path -LiteralPath $logPath)) { Exit-CouldNotVerify "log not found: $log" }
        $seen = 0
        foreach ($line in [System.IO.File]::ReadLines($logPath)) {
            $match = $script:WarningRegex.Match($line)
            if ($match.Success) {
                $names = Get-WarningNames $match.Groups['message'].Value
            } else {
                $match = $script:DetektRegex.Match($line)
                if (-not $match.Success) { continue }
                if (-not $script:DetektAliases.ContainsKey($match.Groups['rule'].Value)) {
                    $seen++
                    continue
                }
                $names = $script:DetektAliases[$match.Groups['rule'].Value]
            }
            $seen++
            $path = $match.Groups['path'].Value
            if (-not $path.StartsWith($rootForward, [System.StringComparison]::OrdinalIgnoreCase)) { continue }
            $key = $path.Substring($rootForward.Length).ToLowerInvariant()
            if (-not $warnings.ContainsKey($key)) { $warnings[$key] = [System.Collections.Generic.List[object]]::new() }
            $warnings[$key].Add(@{ Line = [int]$match.Groups['line'].Value; Names = $names })
        }
        if ($seen -eq 0) {
            Exit-CouldNotVerify "no 'w: file:///' line in $log - the compile task most likely did not run, and every name would look stale"
        }
    }

    $changed = @{}
    foreach ($record in $manifest.files) {
        $path = Join-Path $RepoRoot $record.file
        if (-not (Test-Path -LiteralPath $path) -or (Get-Sha $path) -ne $record.hash) { $changed[$record.file] = $true }
    }

    $kept = @{}
    $keepAll = @{}
    $byFile = $manifest.entries | Group-Object { $_.file }
    foreach ($group in $byFile) {
        $key = $group.Name.ToLowerInvariant()
        if (-not $warnings.ContainsKey($key)) { continue }
        foreach ($warning in $warnings[$key]) {
            $containing = @($group.Group | Where-Object { $_.scopeStart -le $warning.Line -and $warning.Line -le $_.scopeEnd })
            if ($containing.Count -eq 0) { continue }
            if ($null -eq $warning.Names) {
                foreach ($entry in $containing) { $keepAll["$($entry.file)|$($entry.ordinal)"] = $true }
                continue
            }
            foreach ($name in $warning.Names) {
                # The innermost annotation carrying the name is the one silencing the warning.
                $owner = $containing | Where-Object { $_.tokens -contains $name } |
                    Sort-Object @{ Expression = { $_.scopeEnd - $_.scopeStart } }, @{ Expression = { $_.scopeStart }; Descending = $true } |
                    Select-Object -First 1
                if ($owner) { $kept["$($owner.file)|$($owner.ordinal)|$name"] = $true }
            }
        }
    }

    $reportEntries = [System.Collections.Generic.List[object]]::new()
    $summary = [ordered]@{}
    foreach ($entry in $manifest.entries) {
        $id = "$($entry.file)|$($entry.ordinal)"
        $reason = if ($changed.ContainsKey($entry.file)) { 'file-changed' } elseif ($keepAll.ContainsKey($id)) { 'unclassified-warning' } else { 'compiled' }
        $stale = @()
        if ($reason -eq 'compiled') {
            $stale = @($entry.tokens | Where-Object { -not $kept.ContainsKey("$id|$_") })
        }
        foreach ($name in $entry.tokens) {
            if (-not $summary.Contains($name)) { $summary[$name] = [ordered]@{ total = 0; stale = 0 } }
            $summary[$name].total++
            if ($stale -contains $name) { $summary[$name].stale++ }
        }
        $reportEntries.Add([ordered]@{
                file    = $entry.file
                ordinal = $entry.ordinal
                line    = $entry.line
                tokens  = @($entry.tokens)
                stale   = $stale
                reason  = $reason
            })
    }

    $report = [ordered]@{
        created = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
        logs    = $logList
        summary = $summary
        entries = $reportEntries.ToArray()
    }
    $report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding utf8
    foreach ($name in $summary.Keys) {
        Write-Host ("  {0,-22} total {1,4}  stale {2,4}" -f $name, $summary[$name].total, $summary[$name].stale)
    }
    Write-Host "audit-stale-suppressions: report $reportPath ($($changed.Count) file(s) changed since Mask keep every name)"
}

function Invoke-Unmask([bool]$dropStale) {
    $manifest = Read-Manifest
    $staleById = @{}
    if ($dropStale) {
        if (-not (Test-Path -LiteralPath $reportPath)) { Exit-CouldNotVerify "report not found: $reportPath - run -Verb Analyze first" }
        $report = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json -AsHashtable
        foreach ($entry in $report.entries) { $staleById["$($entry.file)|$($entry.ordinal)"] = @($entry.stale) }
    }

    $markerPattern = [regex]::Escape($script:Marker)
    $removedNames = 0
    $removedAnnotations = 0
    $touched = [System.Collections.Generic.List[string]]::new()
    $leftovers = [System.Collections.Generic.List[string]]::new()
    foreach ($record in $manifest.files) {
        $path = Join-Path $RepoRoot $record.file
        if (-not (Test-Path -LiteralPath $path)) {
            Write-Warning "masked file no longer exists: $($record.file)"
            continue
        }
        $unchanged = (Get-Sha $path) -eq $record.hash
        $source = Read-SourceFile $path
        $text = $source.Text
        $found = @(Get-SuppressMatches $text $markerPattern)
        $droppedHere = 0

        for ($i = $found.Count - 1; $i -ge 0; $i--) {
            $match = $found[$i]
            $stale = @()
            if ($dropStale -and $unchanged -and $staleById.ContainsKey("$($record.file)|$i")) {
                $stale = $staleById["$($record.file)|$i"]
            }

            $keptItems = [System.Collections.Generic.List[string]]::new()
            $dropped = 0
            foreach ($raw in $match.Groups['args'].Value.Split(',')) {
                $item = $raw.Trim()
                if (-not $item) { continue }
                if ($item -match ('^"' + $markerPattern + '(?<name>\w+)"$')) {
                    if ($stale -contains $Matches['name']) {
                        $dropped++
                        continue
                    }
                    $item = '"' + $Matches['name'] + '"'
                }
                $keptItems.Add($item)
            }

            $start = $match.Index
            $end = $match.Index + $match.Length
            if ($dropped -eq 0) {
                $replacement = $match.Value.Replace('"' + $script:Marker, '"')
                $text = $text.Substring(0, $start) + $replacement + $text.Substring($end)
                continue
            }

            $removedNames += $dropped
            $droppedHere += $dropped
            if ($keptItems.Count -gt 0) {
                $replacement = '@' + $match.Groups['prefix'].Value + '(' + ($keptItems -join ', ') + ')'
                $text = $text.Substring(0, $start) + $replacement + $text.Substring($end)
                continue
            }

            $removedAnnotations++
            $lineStart = Get-LineStart $text $start
            $lineEnd = Get-LineEnd $text $end
            $before = $text.Substring($lineStart, $start - $lineStart)
            $after = $text.Substring($end, $lineEnd - $end)
            if (-not $before.Trim() -and -not $after.Trim()) {
                $cut = if ($lineEnd -lt $text.Length) { $lineEnd + 1 } else { $lineEnd }
                $text = $text.Substring(0, $lineStart) + $text.Substring($cut)
            } else {
                while ($end -lt $text.Length -and $text[$end] -eq ' ') { $end++ }
                $text = $text.Substring(0, $start) + $text.Substring($end)
            }
        }

        if ($text -ne $source.Text) { Write-SourceFile $path $text $source.Bom }
        if ($droppedHere -gt 0) { $touched.Add($record.file) }
        if ($text.Contains($script:Marker)) { $leftovers.Add($record.file) }
    }

    if ($dropStale) {
        $touched | Set-Content -LiteralPath (Join-Path $OutDir 'applied-files.txt') -Encoding utf8
    }
    if ($leftovers.Count -gt 0) {
        Exit-CouldNotVerify "marker left behind in: $($leftovers -join ', ')"
    }
    Write-Host "audit-stale-suppressions: removed $removedNames name(s), deleted $removedAnnotations annotation(s), changed $($touched.Count) file(s)"
}

switch ($Verb) {
    'Mask' { Invoke-Mask }
    'Analyze' { Invoke-Analyze }
    'Apply' { Invoke-Unmask $true }
    'Restore' { Invoke-Unmask $false }
}
exit 0
