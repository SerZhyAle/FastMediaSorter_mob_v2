# GradleParser.ps1 - extracts canonical pin versions from Gradle sources.
#
# Public entry: Get-GradlePins -RepoRoot <string>
# Returns: [ordered] hashtable pinName -> versionString
#
# Contract: PLAN/S0271_truth_drift_detection/DECISIONS.md (Variant A manifest consumes this output)
# Strategic spec: PLAN/S0271_truth_drift_detection.md
#
# No external module dependencies. PowerShell 7+. -NoProfile safe.

# --- Private regex constants (Class-1 build tools) ---------------------------

$script:RxWrapper       = 'distributionUrl=.*gradle-(?<v>[\d\.]+)-(bin|all)\.zip'
$script:RxAgpPlugin     = 'id\("com\.android\.application"\)\s+version\s+"(?<v>[\d\.]+)"'
$script:RxAgpClasspath  = 'com\.android\.tools\.build:gradle:(?<v>[\d\.]+)'
$script:RxKotlin        = 'kotlin-gradle-plugin:(?<v>[\d\.]+)'
$script:RxKsp           = 'id\("com\.google\.devtools\.ksp"\)\s+version\s+"(?<v>[\d\.]+)"'
$script:RxHiltPlugin    = 'id\("com\.google\.dagger\.hilt\.android"\)\s+version\s+"(?<v>[\d\.]+)"'
$script:RxComposePlugin = 'id\("org\.jetbrains\.kotlin\.plugin\.compose"\)\s+version\s+"(?<v>[\d\.]+)"'
$script:RxNavSafeArgs   = 'androidx\.navigation:navigation-safe-args-gradle-plugin:(?<v>[\d\.]+)'
$script:RxChaquopy      = 'id\("com\.chaquo\.python"\)\s+version\s+"(?<v>[\d\.]+)"'

# --- Private regex constants (Class-2 SDK pins) ------------------------------

$script:RxCompileSdk    = 'compileSdk\s*=\s*(?<v>\d+)'
$script:RxTargetSdk     = 'targetSdk\s*=\s*(?<v>\d+)'
$script:RxNdkVersion    = 'ndkVersion\s*=\s*"(?<v>[\d\.]+)"'
$script:RxJvmTarget     = 'jvmTarget\s*=\s*(?:org\.jetbrains\.kotlin\.gradle\.dsl\.JvmTarget\.JVM_)?(?:")?(?<v>[\d\.]+)(?:")?'
$script:RxSourceCompat  = 'sourceCompatibility\s*=\s*JavaVersion\.VERSION_(?<v>\d+)'
$script:RxDefaultMinSdk = 'minSdk\s*=\s*(?<v>\d+)'
$script:RxRoomSchemaVersion = '@Database\s*\([\s\S]*?\bversion\s*=\s*(?<v>\d+)'

# Per-flavor minSdk override lives inside create("<flavor>") { ... minSdk = N ... }
# We approximate by scanning create("<flavor>") blocks for an in-scope minSdk; absence
# falls back to defaultConfig.minSdk.
$script:KnownFlavors = @('standard','lite','photos','legacy','vr','noLegal')

# --- Private regex constants (Class-3 library coordinates) -------------------

# Generic Maven coordinate matcher used across implementation/api/kapt/ksp/coreLibraryDesugaring.
$script:RxMavenCoord = '(?:implementation|api|kapt|ksp|coreLibraryDesugaring)(?:Platform)?\(\s*"(?<group>[\w\.\-]+):(?<artifact>[\w\-]+):(?<v>[\d\.\-A-Za-z]+)"\s*\)'

# --- Private helpers ---------------------------------------------------------

function script:Read-RequiredText {
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][string] $PinHint
    )
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "GradleParser: source file not found for pin '$PinHint': $Path"
    }
    return Get-Content -LiteralPath $Path -Raw -Encoding UTF8
}

function script:Find-FirstCapture {
    param(
        [Parameter(Mandatory)][string] $Text,
        [Parameter(Mandatory)][string] $Pattern
    )
    $m = [regex]::Match($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)
    if ($m.Success -and $m.Groups['v'].Success) {
        return $m.Groups['v'].Value
    }
    return $null
}

function script:Get-FlavorMinSdk {
    param(
        [Parameter(Mandatory)][string] $Text,
        [Parameter(Mandatory)][string] $Flavor,
        [Parameter(Mandatory)][string] $DefaultMinSdk
    )
    # Locate the create("<flavor>") block and scan its body until the matching close brace.
    # We use a simple brace counter rather than a full Kotlin parser.
    $startPattern = 'create\("' + [regex]::Escape($Flavor) + '"\)\s*\{'
    $startMatch = [regex]::Match($Text, $startPattern)
    if (-not $startMatch.Success) { return $DefaultMinSdk }

    $start = $startMatch.Index + $startMatch.Length
    $depth = 1
    $i = $start
    while ($i -lt $Text.Length -and $depth -gt 0) {
        $c = $Text[$i]
        if ($c -eq '{') { $depth++ }
        elseif ($c -eq '}') { $depth-- }
        $i++
    }
    if ($depth -ne 0) { return $DefaultMinSdk }
    $body = $Text.Substring($start, $i - $start - 1)
    $cap = Find-FirstCapture -Text $body -Pattern $script:RxDefaultMinSdk
    if ($null -ne $cap) { return $cap }
    return $DefaultMinSdk
}

function script:Get-LibraryCoords {
    param([Parameter(Mandatory)][string] $Text)
    $result = [ordered]@{}
    $matches = [regex]::Matches($Text, $script:RxMavenCoord)
    foreach ($m in $matches) {
        $key = 'lib.' + $m.Groups['group'].Value + ':' + $m.Groups['artifact'].Value
        if (-not $result.Contains($key)) {
            $result[$key] = $m.Groups['v'].Value
        }
    }
    return $result
}

function script:Get-SharedModulePin {
    param(
        [Parameter(Mandatory)][string] $AppText,
        [Parameter(Mandatory)][string] $WearText,
        [Parameter(Mandatory)][string] $Pattern,
        [Parameter(Mandatory)][string] $PinHint
    )
    $appValue = Find-FirstCapture -Text $AppText -Pattern $Pattern
    $wearValue = Find-FirstCapture -Text $WearText -Pattern $Pattern
    if ($null -eq $appValue -or $null -eq $wearValue) { return $null }
    if ($appValue -ne $wearValue) {
        throw "GradleParser: $PinHint differs between app_v2 ($appValue) and wear ($wearValue)"
    }
    return $appValue
}

# --- Public entry ------------------------------------------------------------

function Get-GradlePins {
    [CmdletBinding()]
    param(
        [string] $RepoRoot = (Get-Location).Path
    )

    $wrapperPath = Join-Path $RepoRoot 'gradle/wrapper/gradle-wrapper.properties'
    $rootBuildPath = Join-Path $RepoRoot 'build.gradle.kts'
    $appBuildPath = Join-Path $RepoRoot 'app_v2/build.gradle.kts'
    $wearBuildPath = Join-Path $RepoRoot 'wear/build.gradle.kts'
    $databasePath = Join-Path $RepoRoot 'app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt'

    $wrapperText = Read-RequiredText -Path $wrapperPath -PinHint 'gradle.wrapper'
    $rootText    = Read-RequiredText -Path $rootBuildPath -PinHint 'agp'
    $appText     = Read-RequiredText -Path $appBuildPath -PinHint 'compile-sdk'
    $wearText    = Read-RequiredText -Path $wearBuildPath -PinHint 'compile-sdk'
    $databaseText = Read-RequiredText -Path $databasePath -PinHint 'room-schema-version'

    $pins = [ordered]@{}

    # --- Class-1 build tools -------------------------------------------------
    $pins['gradle.wrapper']      = Find-FirstCapture -Text $wrapperText -Pattern $script:RxWrapper
    $agp = Find-FirstCapture -Text $rootText -Pattern $script:RxAgpPlugin
    if ($null -eq $agp) { $agp = Find-FirstCapture -Text $rootText -Pattern $script:RxAgpClasspath }
    $pins['agp']                 = $agp
    $pins['kotlin']              = Find-FirstCapture -Text $rootText -Pattern $script:RxKotlin
    $pins['ksp']                 = Find-FirstCapture -Text $rootText -Pattern $script:RxKsp
    $pins['hilt-plugin']         = Find-FirstCapture -Text $rootText -Pattern $script:RxHiltPlugin
    $pins['compose-plugin']      = Find-FirstCapture -Text $rootText -Pattern $script:RxComposePlugin
    $pins['navigation-safe-args']= Find-FirstCapture -Text $rootText -Pattern $script:RxNavSafeArgs
    $pins['chaquopy']            = Find-FirstCapture -Text $rootText -Pattern $script:RxChaquopy

    # --- Class-2 SDK pins ----------------------------------------------------
    $pins['compile-sdk']  = Get-SharedModulePin -AppText $appText -WearText $wearText -Pattern $script:RxCompileSdk -PinHint 'compile-sdk'
    $pins['target-sdk']   = Get-SharedModulePin -AppText $appText -WearText $wearText -Pattern $script:RxTargetSdk -PinHint 'target-sdk'
    $pins['ndk-version']  = Find-FirstCapture -Text $appText -Pattern $script:RxNdkVersion
    $pins['jvm-target']   = Find-FirstCapture -Text $appText -Pattern $script:RxJvmTarget
    $pins['source-compat']= Find-FirstCapture -Text $appText -Pattern $script:RxSourceCompat
    $pins['room-schema-version'] = Find-FirstCapture -Text $databaseText -Pattern $script:RxRoomSchemaVersion

    # defaultConfig.minSdk: take first match of minSdk = N (defaultConfig appears before any flavor block)
    $defaultMin = Find-FirstCapture -Text $appText -Pattern $script:RxDefaultMinSdk
    foreach ($flavor in $script:KnownFlavors) {
        $key = 'min-sdk.' + $flavor
        $pins[$key] = Get-FlavorMinSdk -Text $appText -Flavor $flavor -DefaultMinSdk $defaultMin
    }

    # --- Class-3 library coordinates ----------------------------------------
    $libs = Get-LibraryCoords -Text $appText
    foreach ($k in ($libs.Keys | Sort-Object)) {
        $pins[$k] = $libs[$k]
    }

    return $pins
}
