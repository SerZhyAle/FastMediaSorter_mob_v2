# Scans a module's Kotlin sources and produces/updates a JSONL catalogue.
# Manual fields (role, status, noFlavors, function descriptions) are preserved on re-run.
#
# Usage:
#   pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
#   pwsh -File dev/CATALOG/scripts/scan.ps1 -Module wear

param(
    [Parameter(Mandatory=$true)]
    [string]$Module,
    [string]$Root,
    [string]$OutFile
)

$ErrorActionPreference = "Stop"

if (-not $Root) {
    $Root = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
}
$Root = (Resolve-Path $Root).Path

if (-not $OutFile) {
    $OutFile = Join-Path $Root "dev\CATALOG\$Module.jsonl"
}

$srcRoots = @(
    (Join-Path $Root "$Module\src\main\java"),
    (Join-Path $Root "$Module\src\vr\java"),
    (Join-Path $Root "$Module\src\vrStub\java"),
    (Join-Path $Root "$Module\src\noLegal\java"),
    (Join-Path $Root "$Module\src\streamingEnabled\java"),
    (Join-Path $Root "$Module\src\cloudEnabled\java"),
    (Join-Path $Root "$Module\src\cloudDisabled\java")
) | Where-Object { Test-Path $_ }
if (-not $srcRoots -or $srcRoots.Count -eq 0) {
    throw "No supported source roots found for module '$Module'"
}

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
    # Returns ordered list of @{ Name; DeclIndex } for each top-level (column 0)
    # class/object/interface/enum declaration. The leading '^' in multiline mode
    # forbids leading whitespace, so nested/indented declarations are excluded
    # and naturally remain inside their enclosing class's scope.
    $result = New-Object System.Collections.ArrayList
    $pattern = '(?m)^(?:(?:abstract|open|sealed|data|inner|internal|private|public|final|annotation)\s+)*(?:class|object|interface|enum\s+class)\s+([A-Z][A-Za-z0-9_]*)'
    foreach ($m in [regex]::Matches($content, $pattern)) {
        [void]$result.Add([PSCustomObject]@{ Name = $m.Groups[1].Value; DeclIndex = $m.Index })
    }
    return ,$result
}

function Get-Functions([string]$content) {
    $result = @()
    $pattern = '(?m)^\s*(?:(?:override|private|protected|internal|public|suspend|inline|operator|abstract|open|final|tailrec|infix)\s+)*fun\s+(?:<[^>]+>\s+)?(?:[A-Za-z_][A-Za-z0-9_.<>,\s?]*\.)?([A-Za-z_][A-Za-z0-9_]*)\s*(\([^\)]*\))(\s*:\s*[^\{=\n]+)?'
    foreach ($m in [regex]::Matches($content, $pattern)) {
        $sig = ($m.Value -replace '\s+', ' ').Trim()
        $result += [ordered]@{
            name = $m.Groups[1].Value
            signature = $sig
            description = ''
        }
    }
    return ,$result
}

function Get-Injected([string]$content) {
    $injected = @()
    $m = [regex]::Match($content, '(?ms)@Inject\s+constructor\s*\(([^)]*)\)')
    if (-not $m.Success) { return ,$injected }
    $params = $m.Groups[1].Value
    foreach ($pm in [regex]::Matches($params, '(?:@\w+\s+)*(?:private\s+|internal\s+|val\s+|var\s+)*\w+\s*:\s*([A-Z][A-Za-z0-9_]*)')) {
        $t = $pm.Groups[1].Value
        if ($t -and ($injected -notcontains $t)) { $injected += $t }
    }
    return ,$injected
}

function Get-SideEffects([string]$content) {
    $se = @()
    if ($content -match 'androidx\.room|\bRoomDatabase\b|@Dao\b|@Entity\b') { $se += 'db' }
    if ($content -match '\bretrofit2\b|\bOkHttpClient\b|\bHttpURLConnection\b|\bSocket\b|\bSmbClient\b|\bSftpClient\b') { $se += 'network' }
    if ($content -match '\bFile\s*\(|\bFileInputStream\b|\bFileOutputStream\b|\bFiles\.|\bContentResolver\b') { $se += 'disk' }
    if ($content -match '\bSharedPreferences\b|\bDataStore\b|\bPreferenceManager\b') { $se += 'prefs' }
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

function Get-LastTouched([string]$fullPath, [string]$root) {
    try {
        Push-Location $root
        $d = git log -1 --format=%ad --date=short -- $fullPath 2>$null
        return ($d | Out-String).Trim()
    } catch { return '' }
    finally { Pop-Location }
}

function Test-HasTests([string]$fullPath) {
    $base = [System.IO.Path]::GetFileNameWithoutExtension($fullPath)
    foreach ($scope in @('test', 'androidTest')) {
        $candidate = $fullPath -replace 'src\\main\\', "src\$scope\"
        $candidateTest = ($candidate -replace '\.kt$', 'Test.kt')
        if (Test-Path $candidateTest) { return $true }
    }
    return $false
}

$existing = @{}
if (Test-Path $OutFile) {
    foreach ($line in (Get-Content -Path $OutFile -Encoding UTF8)) {
        if (-not $line) { continue }
        try {
            $obj = $line | ConvertFrom-Json -AsHashtable
            $key = "$($obj.path)::$($obj.class)"
            $existing[$key] = $obj
        } catch {}
    }
}

$ktFiles = @()
foreach ($srcRoot in $srcRoots) {
    $ktFiles += Get-ChildItem -Path $srcRoot -Recurse -Filter '*.kt' -File
}
$records = New-Object System.Collections.ArrayList

foreach ($file in $ktFiles) {
    $content = Get-Content -Path $file.FullName -Raw -Encoding UTF8
    if (-not $content) { continue }
    $loc = ([regex]::Matches($content, "`n")).Count + 1

    $srcRoot = $srcRoots | Where-Object { $file.FullName.StartsWith($_, [System.StringComparison]::OrdinalIgnoreCase) } | Select-Object -First 1
    if (-not $srcRoot) { continue }
    $rel = $file.FullName.Substring($srcRoot.Length + 1) -replace '\\', '/'
    $layer = Get-Layer $rel
    $lastTouched = Get-LastTouched $file.FullName $Root
    $hasTests = Test-HasTests $file.FullName

    # One catalogue record per top-level class. Without this split, files that
    # declare multiple top-level classes (e.g. a lightweight exception + a
    # primary strategy/manager) collapse into a single record whose `class`
    # field names the first detected class but whose `functions` list belongs
    # to all of them. That made manual role/status updates unsafe and gave
    # consumers misleading class ownership.
    $topClasses = Get-TopLevelClasses $content
    if ($topClasses.Count -eq 0) {
        $topClasses = @([PSCustomObject]@{
            Name = [System.IO.Path]::GetFileNameWithoutExtension($file.Name)
            DeclIndex = 0
        })
    }

    for ($ci = 0; $ci -lt $topClasses.Count; $ci++) {
        $className = $topClasses[$ci].Name
        $scopeStart = $topClasses[$ci].DeclIndex
        $scopeEnd = if ($ci + 1 -lt $topClasses.Count) { $topClasses[$ci + 1].DeclIndex } else { $content.Length }
        $scope = $content.Substring($scopeStart, $scopeEnd - $scopeStart)

        $funcs = Get-Functions $scope
        $injected = Get-Injected $scope
        $sideEffects = Get-SideEffects $scope

        $record = [ordered]@{
            path = $rel
            class = $className
            layer = $layer
            loc = $loc
            lastTouched = $lastTouched
            noFlavors = @()
            injected = $injected
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
            if ($old.noFlavors) { $record.noFlavors = $old.noFlavors }
            if ($old.status)    { $record.status    = $old.status }
            if ($old.role)      { $record.role      = $old.role }
            $oldDescs = @{}
            if ($old.functions) {
                foreach ($of in $old.functions) {
                    if ($of.description) { $oldDescs[$of.name] = $of.description }
                }
            }
            for ($i = 0; $i -lt $record.functions.Count; $i++) {
                $fn = $record.functions[$i].name
                if ($oldDescs.ContainsKey($fn)) {
                    $record.functions[$i].description = $oldDescs[$fn]
                }
            }
        }

        [void]$records.Add($record)
    }
}

$sorted = $records | Sort-Object -Property @{Expression={$_.layer}}, @{Expression={$_.path}}

$lines = New-Object System.Collections.ArrayList
foreach ($r in $sorted) {
    [void]$lines.Add(($r | ConvertTo-Json -Depth 10 -Compress))
}

$outDir = Split-Path $OutFile -Parent
if (-not (Test-Path $outDir)) { New-Item -Path $outDir -ItemType Directory -Force | Out-Null }
$lines | Set-Content -Path $OutFile -Encoding UTF8

Write-Host "Scanned module '$Module': $($ktFiles.Count) files, $($records.Count) records -> $OutFile"
