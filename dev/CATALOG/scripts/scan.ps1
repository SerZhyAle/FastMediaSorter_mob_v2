# Scans a module's Kotlin sources and produces/updates a JSONL catalogue.
# Manual fields (role, status, noFlavors, function descriptions) are preserved on re-run.
#
# Usage:
#   pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
#   pwsh -File dev/CATALOG/scripts/scan.ps1 -Module wear
#
# Exit codes:
#   0 - success.
#   1 - failure: a throw under $ErrorActionPreference = 'Stop' ends the process.

param(
    [Parameter(Mandatory=$true)]
    [string]$Module,
    [string]$Root,
    [string]$OutFile,
    # S0848 incremental mode. When provided, git `lastTouched` is recomputed only for
    # these files; every other file reuses the value already stored in the existing JSONL
    # (unchanged file -> unchanged last-touched date). Omitted -> full rebuild: git for
    # every file, exactly as before (release/CI / `/catalog` full-refresh path).
    [string[]]$ChangedFiles
)

$ErrorActionPreference = "Stop"

if (-not $Root) {
    $Root = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
}
$Root = (Resolve-Path $Root).Path

# Normalize the changed-files signal to a set of absolute, forward-slashed paths for
# O(1) membership tests against each scanned file's full path. Missing/deleted entries
# are harmless: they simply never match a file that is actually present in the tree.
$changedSet = $null
if ($ChangedFiles -and $ChangedFiles.Count -gt 0) {
    $changedSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($cf in $ChangedFiles) {
        if ([string]::IsNullOrWhiteSpace($cf)) { continue }
        $full = if ([System.IO.Path]::IsPathRooted($cf)) { $cf } else { Join-Path $Root $cf }
        $normFull = ([System.IO.Path]::GetFullPath($full)) -replace '\\', '/'
        [void]$changedSet.Add($normFull)
    }
}

if (-not $OutFile) {
    $OutFile = Join-Path $Root "dev\CATALOG\$Module.jsonl"
}

$srcRoots = @(
    (Join-Path $Root "$Module\src\main\java"),
    (Join-Path $Root "$Module\src\standard\java"),
    (Join-Path $Root "$Module\src\lite\java"),
    (Join-Path $Root "$Module\src\photos\java"),
    (Join-Path $Root "$Module\src\legacy\java"),
    (Join-Path $Root "$Module\src\vr\java"),
    (Join-Path $Root "$Module\src\vrStub\java"),
    (Join-Path $Root "$Module\src\noLegal\java"),
    (Join-Path $Root "$Module\src\streamingEnabled\java"),
    (Join-Path $Root "$Module\src\translationEnabled\java"),
    (Join-Path $Root "$Module\src\translationDynamicFeature\java"),
    (Join-Path $Root "$Module\src\translationMlKit\java"),
    (Join-Path $Root "$Module\src\vrOnly\java"),
    (Join-Path $Root "$Module\src\cloudEnabled\java"),
    (Join-Path $Root "$Module\src\cloudDisabled\java"),
    (Join-Path $Root "$Module\src\ocrEnabled\java"),
    (Join-Path $Root "$Module\src\ocrDisabled\java"),
    (Join-Path $Root "$Module\src\screenCapture\java"),
    (Join-Path $Root "$Module\src\standardScreenCapture\java"),
    (Join-Path $Root "$Module\src\standardEdgeTile\java"),
    # S0404: launcher-mode capability source sets. Added 2026-07-17
    (Join-Path $Root "$Module\src\launcherEnabled\java"),
    (Join-Path $Root "$Module\src\launcherDisabled\java"),
    # S1558: the Cast seam implementations. Added 2026-08-14
    (Join-Path $Root "$Module\src\castEnabled\java"),
    (Join-Path $Root "$Module\src\castDisabled\java"),
    # S1802: the Wear Data Layer seam. Added 2026-08-18
    (Join-Path $Root "$Module\src\wearGms\java"),
    (Join-Path $Root "$Module\src\wearStub\java")
) | Where-Object { Test-Path -LiteralPath $_ }
if (-not $srcRoots -or $srcRoots.Count -eq 0) {
    throw "No supported source roots found for module '$Module'"
}

# Pre-compiled Regexes for performance across 2500+ files
$regexTopClasses    = [regex]::new('(?m)^(?:(?:abstract|open|sealed|data|inner|internal|private|public|final|annotation)\s+)*(?:class|object|interface|enum\s+class)\s+([A-Z][A-Za-z0-9_]*)')
$regexFunctions     = [regex]::new('(?m)^\s*(?:(?:override|private|protected|internal|public|suspend|inline|operator|abstract|open|final|tailrec|infix)\s+)*fun\s+(?:<[^>]+>\s+)?(?:[A-Za-z_][A-Za-z0-9_.<>,\s?]*\.)?([A-Za-z_][A-Za-z0-9_]*)\s*(\([^\)]*\))(\s*:\s*[^\{=\n]+)?')
$regexInject        = [regex]::new('(?ms)@Inject\s+constructor\s*\(([^)]*)\)')
$regexTypeParams    = [regex]::new('(?:@\w+\s+)*(?:private\s+|internal\s+|val\s+|var\s+)*\w+\s*:\s*([A-Z][A-Za-z0-9_]*)')
$regexExplicitConst = [regex]::new('(?ms)\bconstructor\s*\(([^)]*)\)')
$regexHeaderConst   = [regex]::new('(?ms)^(?:(?:abstract|open|sealed|data|inner|internal|private|public|final)\s+)*class\s+[A-Z][A-Za-z0-9_]*(?:\s*<[^>]+>)?\s*\(([^)]*)\)')

function Get-Layer([string]$relPath) {
    if ($relPath -match '(^|/)(ui)/')      { return 'ui' }
    if ($relPath -match '(^|/)(domain)/')  { return 'domain' }
    if ($relPath -match '(^|/)(data)/')    { return 'data' }
    if ($relPath -match '(^|/)(di)/')      { return 'di' }
    if ($relPath -match '(^|/)(core)/')    { return 'core' }
    if ($relPath -match '(^|/)(utils?)/')  { return 'utils' }
    if ($relPath -match '(^|/)(worker)/')  { return 'worker' }
    if ($relPath -match '(^|/)(widget)/')  { return 'widget' }
    if ($relPath -match '(^|/)(service)/') { return 'service' }
    if ($relPath -match '(^|/)(vr)/')      { return 'vr' }
    return 'other'
}

function Get-TopLevelClasses([string]$content) {
    $result = New-Object System.Collections.Generic.List[PSCustomObject]
    foreach ($m in $regexTopClasses.Matches($content)) {
        $result.Add([PSCustomObject]@{ Name = $m.Groups[1].Value; DeclIndex = $m.Index })
    }
    return ,$result
}

function Get-Functions([string]$content) {
    $result = New-Object System.Collections.Generic.List[PSCustomObject]
    if (-not $content.Contains('fun')) { return ,$result }
    foreach ($m in $regexFunctions.Matches($content)) {
        $sig = ($m.Value -replace '\s+', ' ').Trim()
        $result.Add([PSCustomObject][ordered]@{
            name = $m.Groups[1].Value
            signature = $sig
            description = ''
        })
    }
    return ,$result
}

function Get-Injected([string]$content) {
    $injected = New-Object System.Collections.Generic.List[string]
    if (-not $content.Contains('@Inject')) { return ,$injected }
    $m = $regexInject.Match($content)
    if (-not $m.Success) { return ,$injected }
    $params = $m.Groups[1].Value
    foreach ($pm in $regexTypeParams.Matches($params)) {
        $t = $pm.Groups[1].Value
        if ($t -and (-not $injected.Contains($t))) { $injected.Add($t) }
    }
    return ,$injected
}

function Get-ConstructorDeps([string]$content) {
    $deps = New-Object System.Collections.Generic.List[string]
    $m = $regexExplicitConst.Match($content)
    if (-not $m.Success) {
        $m = $regexHeaderConst.Match($content)
    }
    if (-not $m.Success) { return ,$deps }
    $params = $m.Groups[1].Value
    foreach ($pm in $regexTypeParams.Matches($params)) {
        $t = $pm.Groups[1].Value
        if ($t -and (-not $deps.Contains($t))) { $deps.Add($t) }
    }
    return ,$deps
}

function Get-SideEffects([string]$content) {
    $se = New-Object System.Collections.Generic.List[string]
    if ($content -match 'androidx\.room|\bRoomDatabase\b|@Dao\b|@Entity\b') { $se.Add('db') }
    if ($content -match '\bretrofit2\b|\bOkHttpClient\b|\bHttpURLConnection\b|\bSocket\b|\bSmbClient\b|\bSftpClient\b') { $se.Add('network') }
    if ($content -match '\bFile\s*\(|\bFileInputStream\b|\bFileOutputStream\b|\bFiles\.|\bContentResolver\b') { $se.Add('disk') }
    if ($content -match '\bSharedPreferences\b|\bDataStore\b|\bPreferenceManager\b') { $se.Add('prefs') }
    return ,$se
}

function Test-UserFeedback([string]$content) {
    return [bool]($content -match '\bToast\.makeText\b|\bSnackbar\.make\b|\bAlertDialog\b|\bMaterialAlertDialogBuilder\b|\bNotificationCompat\b|\bshowMessage\b')
}

function Test-Coroutines([string]$content) {
    return [bool]($content -match '\bsuspend\b|\blaunch\s*[\({]|\bFlow\s*<|\bflow\s*\{|\bCoroutineScope\b|\bwithContext\s*\(')
}

function Test-Timber([string]$content) {
    return [bool]($content -match '\bTimber\.')
}

# Bulk git last-touched cache for fast O(1) date resolution in 1 git process (~300ms)
$script:bulkLastTouchedMap = $null

function Init-BulkLastTouchedMap([string]$module, [string]$root) {
    $map = @{}
    try {
        $moduleDir = Join-Path $root $module
        $gitLines = & git --no-pager log --name-only --format="DATE:%ad" --date=short -- $moduleDir 2>$null
        $curDate = $null
        foreach ($line in $gitLines) {
            if (-not $line) { continue }
            if ($line.StartsWith('DATE:')) {
                $curDate = $line.Substring(5)
            }
            elseif ($curDate) {
                $norm = $line -replace '\\', '/'
                if (-not $map.ContainsKey($norm)) {
                    $map[$norm] = $curDate
                }
            }
        }
    } catch {}
    $script:bulkLastTouchedMap = $map
}

function Get-LastTouched([string]$fullPath, [string]$root) {
    if ($script:bulkLastTouchedMap) {
        $repoRelPath = ($fullPath.Substring($root.Length + 1)) -replace '\\', '/'
        if ($script:bulkLastTouchedMap.ContainsKey($repoRelPath)) {
            return $script:bulkLastTouchedMap[$repoRelPath]
        }
    }
    try {
        Push-Location $root
        $d = git --no-pager log -1 --format=%ad --date=short -- $fullPath 2>$null
        return ($d | Out-String).Trim()
    } catch { return '' }
    finally { Pop-Location }
}

function Test-HasTests([string]$fullPath) {
    if ($fullPath -notmatch 'src\\[^\\]+\\') { return $false }
    foreach ($scope in @('test', 'androidTest')) {
        $candidate = $fullPath -replace 'src\\[^\\]+\\', "src\$scope\"
        $candidateConvention = ($candidate -replace '\.kt$', 'Test.kt')
        if ([System.IO.File]::Exists($candidateConvention)) { return $true }
        if ([System.IO.File]::Exists($candidate))           { return $true }
    }
    return $false
}

$existing = @{}
$ltByPath = @{}
$ltAmbiguousPaths = [System.Collections.Generic.HashSet[string]]::new()
if (Test-Path -LiteralPath $OutFile) {
    foreach ($line in (Get-Content -Path $OutFile -Encoding UTF8)) {
        if (-not $line) { continue }
        try {
            $obj = $line | ConvertFrom-Json
            $key = "$($obj.path)::$($obj.class)"
            $existing[$key] = $obj
            if ($obj.path -and $obj.lastTouched) {
                if ($ltByPath.ContainsKey($obj.path)) {
                    if ($ltByPath[$obj.path] -ne $obj.lastTouched) { [void]$ltAmbiguousPaths.Add($obj.path) }
                }
                else {
                    $ltByPath[$obj.path] = $obj.lastTouched
                }
            }
        } catch {}
    }
}

$ktFiles = New-Object System.Collections.Generic.List[System.IO.FileInfo]
foreach ($srcRoot in $srcRoots) {
    if (Test-Path -LiteralPath $srcRoot) {
        foreach ($f in (Get-ChildItem -Path $srcRoot -Recurse -Filter '*.kt' -File)) {
            $ktFiles.Add($f)
        }
    }
}

$records = New-Object System.Collections.Generic.List[PSCustomObject]

foreach ($file in $ktFiles) {
    $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    if (-not $content) { continue }
    $loc = ([regex]::Matches($content, "`n")).Count + 1

    $srcRoot = $null
    foreach ($sr in $srcRoots) {
        if ($file.FullName.StartsWith($sr, [System.StringComparison]::OrdinalIgnoreCase)) {
            $srcRoot = $sr
            break
        }
    }
    if (-not $srcRoot) { continue }
    $rel = $file.FullName.Substring($srcRoot.Length + 1) -replace '\\', '/'
    $layer = Get-Layer $rel

    $fileNorm = $file.FullName -replace '\\', '/'
    $lastTouched = $null
    if ($changedSet -and -not $changedSet.Contains($fileNorm) -and
        $ltByPath.ContainsKey($rel) -and -not $ltAmbiguousPaths.Contains($rel)) {
        $lastTouched = $ltByPath[$rel]
    }
    if ([string]::IsNullOrEmpty($lastTouched)) {
        if (-not $script:bulkLastTouchedMap) {
            Init-BulkLastTouchedMap $Module $Root
        }
        $lastTouched = Get-LastTouched $file.FullName $Root
    }
    $hasTests = Test-HasTests $file.FullName

    $topClasses = Get-TopLevelClasses $content
    $fileRecordName = [System.IO.Path]::GetFileNameWithoutExtension($file.Name)
    $topLevelComposable = [regex]::IsMatch(
        $content,
        "(?m)^\s*@Composable\s*(?:\r?\n\s*)*(?:public\s+)?fun\s+$([regex]::Escape($fileRecordName))\s*\("
    )
    $hasFileNamedType = $false
    foreach ($tc in $topClasses) {
        if ($tc.Name -eq $fileRecordName) { $hasFileNamedType = $true; break }
    }
    if ($topClasses.Count -eq 0 -or ($topLevelComposable -and -not $hasFileNamedType)) {
        $topClasses = @([PSCustomObject]@{
            Name = $fileRecordName
            DeclIndex = 0
        }) + $topClasses
    }

    for ($ci = 0; $ci -lt $topClasses.Count; $ci++) {
        $className = $topClasses[$ci].Name
        $scopeStart = $topClasses[$ci].DeclIndex
        $scopeEnd = if ($ci + 1 -lt $topClasses.Count) { $topClasses[$ci + 1].DeclIndex } else { $content.Length }
        $scope = $content.Substring($scopeStart, $scopeEnd - $scopeStart)

        $funcs = Get-Functions $scope
        $injected = Get-Injected $scope
        $constructorDeps = Get-ConstructorDeps $scope
        $sideEffects = Get-SideEffects $scope

        $recordObj = [ordered]@{
            path = $rel
            class = $className
            layer = $layer
            loc = $loc
            lastTouched = $lastTouched
            noFlavors = @()
            injected = $injected
            constructorDeps = $constructorDeps
            hasTests = $hasTests
            coroutines = Test-Coroutines $scope
            usesTimber = Test-Timber $scope
            sideEffects = $sideEffects
            userFeedback = Test-UserFeedback $scope
            status = 'unknown'
            role = ''
            functions = $funcs
        }

        $key = "$rel::$className"
        if ($existing.ContainsKey($key)) {
            $old = $existing[$key]
            if ($old.noFlavors) { $recordObj.noFlavors = $old.noFlavors }
            if ($old.status)    { $recordObj.status    = $old.status }
            if ($old.role)      { $recordObj.role      = $old.role }
            $oldDescs = @{}
            if ($old.functions) {
                foreach ($of in $old.functions) {
                    if ($of.description) { $oldDescs[$of.name] = $of.description }
                }
            }
            for ($i = 0; $i -lt $recordObj.functions.Count; $i++) {
                $fn = $recordObj.functions[$i].name
                if ($oldDescs.ContainsKey($fn)) {
                    $recordObj.functions[$i].description = $oldDescs[$fn]
                }
            }
        }

        [void]$records.Add([PSCustomObject]$recordObj)
    }
}

$sorted = $records | Sort-Object -Property layer, path

$lines = New-Object System.Collections.Generic.List[string]
foreach ($r in $sorted) {
    [void]$lines.Add(($r | ConvertTo-Json -Depth 10 -Compress))
}

$outDir = Split-Path $OutFile -Parent
if (-not (Test-Path -LiteralPath $outDir)) { New-Item -Path $outDir -ItemType Directory -Force | Out-Null }
$lines | Set-Content -Path $OutFile -Encoding UTF8

Write-Host "Scanned module '$Module': $($ktFiles.Count) files, $($records.Count) records -> $OutFile"

exit 0
