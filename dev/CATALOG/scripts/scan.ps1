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
    # S0404: launcher-mode capability source sets. Added 2026-07-17 - without them the whole launcher
    # feature was invisible to the catalogue while catalog-sync still reported PASS, because this list
    # is the only definition of "the code" and a missing root looks identical to an empty one.
    (Join-Path $Root "$Module\src\launcherEnabled\java"),
    (Join-Path $Root "$Module\src\launcherDisabled\java"),
    # S1558: the Cast seam implementations. Added 2026-08-14 for the same reason as the launcher pair
    # above - CastMediaManagerImpl, LocalCastProxyServer and CastStereoCropTranscoder were invisible to
    # the catalogue while catalog-sync reported PASS, because this list is the only definition of "the
    # code" and an unlisted root is indistinguishable from an empty one.
    (Join-Path $Root "$Module\src\castEnabled\java"),
    (Join-Path $Root "$Module\src\castDisabled\java"),
    # S1802: the Wear Data Layer seam. Added 2026-08-18 for the third time this list has cost a
    # subsystem its visibility - PhoneWearListenerService, WearableDataLayerRepositoryImpl, the
    # flavor-local WearModule and WearLogReportReceiver were all absent from the catalogue while
    # catalog-sync reported PASS, because an unlisted root is indistinguishable from an empty one.
    (Join-Path $Root "$Module\src\wearGms\java"),
    (Join-Path $Root "$Module\src\wearStub\java")
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

function Get-ConstructorDeps([string]$content) {
    # All primary-constructor parameter types for the class scope, ordered and
    # de-duplicated. Superset of Get-Injected: captures every constructor param
    # type, not only @Inject-annotated ones, so non-Hilt and @Inject-free classes
    # still expose their collaborators. Imports are intentionally not parsed
    # (BLK-02 default). Reuses the Get-Injected parameter-type regex style.
    $deps = @()
    # Prefer an explicit `constructor(..)` (covers `@Inject constructor(..)` and
    # plain `constructor(..)`); otherwise fall back to the primary-constructor
    # parenthesis in the class header `class Name<..>(..)`.
    $m = [regex]::Match($content, '(?ms)\bconstructor\s*\(([^)]*)\)')
    if (-not $m.Success) {
        $m = [regex]::Match($content, '(?ms)^(?:(?:abstract|open|sealed|data|inner|internal|private|public|final)\s+)*class\s+[A-Z][A-Za-z0-9_]*(?:\s*<[^>]+>)?\s*\(([^)]*)\)')
    }
    if (-not $m.Success) { return ,$deps }
    $params = $m.Groups[1].Value
    foreach ($pm in [regex]::Matches($params, '(?:@\w+\s+)*(?:private\s+|internal\s+|val\s+|var\s+)*\w+\s*:\s*([A-Z][A-Za-z0-9_]*)')) {
        $t = $pm.Groups[1].Value
        if ($t -and ($deps -notcontains $t)) { $deps += $t }
    }
    return ,$deps
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
    # Resolve the test source root from the file's OWN source root rather than a
    # hard-coded production root, so flavor-root classes (vr, noLegal, lite,
    # photos, legacy, ..) are eligible for a test match. A file under src\<root>\ is
    # considered tested when EITHER convention exists in src\test\ or
    # src\androidTest\: the <ClassName>Test.kt sibling, or a same-relative-path
    # file mirrored into the test tree.
    if ($fullPath -notmatch 'src\\[^\\]+\\') { return $false }
    foreach ($scope in @('test', 'androidTest')) {
        $candidate = $fullPath -replace 'src\\[^\\]+\\', "src\$scope\"
        $candidateConvention = ($candidate -replace '\.kt$', 'Test.kt')
        if (Test-Path $candidateConvention) { return $true }
        if (Test-Path $candidate)           { return $true }
    }
    return $false
}

$existing = @{}
# S0848: per-file last-touched carried from the previous scan, keyed by the relative
# `path`, for reuse in incremental mode. A path is reusable ONLY when it maps to a single
# distinct date: flavor-variant files (e.g. ScreenCaptureModule, TranslationFlavorModule)
# share one relative path across source roots but have independent git histories, so a path
# with conflicting dates is marked ambiguous and falls back to git per physical file.
$ltByPath = @{}
$ltAmbiguousPaths = [System.Collections.Generic.HashSet[string]]::new()
if (Test-Path $OutFile) {
    foreach ($line in (Get-Content -Path $OutFile -Encoding UTF8)) {
        if (-not $line) { continue }
        try {
            $obj = $line | ConvertFrom-Json -AsHashtable
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
    # S0848 incremental: recompute git last-touched only for changed files (or on a full
    # rebuild, when no changed-set was given). An unchanged file with an unambiguous prior
    # date reuses it; anything else (changed, new, or ambiguous flavor-variant path) hits git.
    $fileNorm = $file.FullName -replace '\\', '/'
    $lastTouched = $null
    if ($changedSet -and -not $changedSet.Contains($fileNorm) -and
        $ltByPath.ContainsKey($rel) -and -not $ltAmbiguousPaths.Contains($rel)) {
        $lastTouched = $ltByPath[$rel]
    }
    if ([string]::IsNullOrEmpty($lastTouched)) {
        $lastTouched = Get-LastTouched $file.FullName $Root
    }
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
        $constructorDeps = Get-ConstructorDeps $scope
        $sideEffects = Get-SideEffects $scope

        $record = [ordered]@{
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

exit 0
