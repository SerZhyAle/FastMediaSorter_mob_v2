#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Builds a markdown research dossier in temp/ for repeatable investigation loops.

.DESCRIPTION
    Collects the first-pass research context for a topic across the documented routing stack,
    class catalogue, Activity catalogue, spec catalogue, and docs/dev markdown files.

.PARAMETER Topic
    Research topic, feature name, error string, or question fragment.

.PARAMETER Module
    Target module: app_v2, wear, or all. Default: all.

.PARAMETER Flavor
    Optional flavor note to keep build-specific context visible in the dossier.

.PARAMETER OutFile
    Optional output path. Must stay inside temp/.

.EXAMPLE
    pwsh -File scripts/utils/build-research-dossier.ps1 -Topic "player startup" -Module app_v2

.EXAMPLE
    pwsh -File scripts/utils/build-research-dossier.ps1 -Topic "cloud auth" -Module all -OutFile "temp/research/cloud_auth.md"
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Topic,

    [ValidateSet('app_v2', 'wear', 'all')]
    [string]$Module = 'all',

    [string]$Flavor,

    [string]$OutFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$TempRoot = Join-Path $Root 'temp'

if (-not (Test-Path $TempRoot)) {
    New-Item -ItemType Directory -Path $TempRoot -Force | Out-Null
}

function Get-TopicTokens {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    $tokens = [regex]::Matches($Value.ToLowerInvariant(), '[\p{L}\p{Nd}]+') |
    ForEach-Object { $_.Value } |
    Where-Object { $_.Length -ge 3 } |
    Select-Object -Unique -First 6

    if (@($tokens).Count -gt 0) {
        return @($tokens)
    }

    $trimmed = $Value.Trim().ToLowerInvariant()
    if ($trimmed) {
        return @($trimmed)
    }

    return @()
}

function Resolve-DossierOutputPath {
    param(
        [string]$RequestedPath,
        [string]$TopicValue
    )

    if ([string]::IsNullOrWhiteSpace($RequestedPath)) {
        $slug = ($TopicValue.ToLowerInvariant() -replace '[^a-z0-9]+', '-') -replace '(^-+|-+$)', ''
        if (-not $slug) {
            $slug = 'topic'
        }
        return Join-Path $TempRoot ("research_dossier_{0}_{1}.md" -f $slug, (Get-Date -Format 'yyyyMMdd_HHmmss'))
    }

    $candidate = if ([System.IO.Path]::IsPathRooted($RequestedPath)) {
        $RequestedPath
    }
    else {
        Join-Path $Root $RequestedPath
    }

    $resolved = [System.IO.Path]::GetFullPath($candidate)
    $allowedRoot = [System.IO.Path]::GetFullPath($TempRoot)
    if (-not $resolved.StartsWith($allowedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "OutFile must stay inside temp/: $resolved"
    }

    $parent = Split-Path -Path $resolved -Parent
    if ($parent -and -not (Test-Path $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }

    return $resolved
}

function Convert-JsonLinesOutput {
    param(
        [AllowNull()]
        [object[]]$OutputLines
    )

    if ($null -eq $OutputLines) {
        return @()
    }

    $items = New-Object System.Collections.Generic.List[object]
    foreach ($line in $OutputLines) {
        $text = [string]$line
        if ([string]::IsNullOrWhiteSpace($text)) {
            continue
        }
        $trimmed = $text.Trim()
        if (-not $trimmed.StartsWith('{')) {
            continue
        }
        try {
            $items.Add(($trimmed | ConvertFrom-Json -Depth 20))
        }
        catch {
        }
    }

    return $items.ToArray()
}

function Convert-JsonDocumentOutput {
    param(
        [AllowEmptyString()]
        [string]$RawText
    )

    $raw = $RawText.Trim()
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return @()
    }

    if (-not ($raw.StartsWith('{') -or $raw.StartsWith('['))) {
        return @()
    }

    try {
        $parsed = $raw | ConvertFrom-Json -Depth 20
    }
    catch {
        return @()
    }

    if ($parsed -is [System.Array]) {
        return @($parsed)
    }

    return , $parsed
}

function Get-TextMatchScore {
    param(
        [string]$Text,
        [string]$TopicValue,
        [string[]]$Tokens
    )

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return 0
    }

    $score = 0
    if ($Text -imatch [regex]::Escape($TopicValue)) {
        $score += 40
    }
    foreach ($token in $Tokens) {
        if ($Text -imatch [regex]::Escape($token)) {
            $score += 8
        }
    }

    return $score
}

function Get-OptionalPropertyValue {
    param(
        [AllowNull()]
        [object]$InputObject,

        [Parameter(Mandatory = $true)]
        [string]$PropertyName
    )

    if ($null -eq $InputObject) {
        return $null
    }

    if ($InputObject.PSObject.Properties.Name -contains $PropertyName) {
        return $InputObject.$PropertyName
    }

    return $null
}

function Resolve-RelativePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AbsolutePath
    )

    return [System.IO.Path]::GetRelativePath($Root, $AbsolutePath) -replace '\\', '/'
}

function Add-UniquePath {
    param(
        [AllowEmptyCollection()]
        [System.Collections.Generic.List[string]]$List,

        [string]$Path
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return
    }

    if (-not $List.Contains($Path)) {
        $List.Add($Path)
    }
}

function Get-CatalogMatches {
    param(
        [string]$TopicValue,
        [string[]]$Tokens,
        [string[]]$ModuleNames
    )

    $queryScript = Join-Path $Root 'dev/CATALOG/scripts/query.ps1'
    $byPath = @{}
    $patterns = @($TopicValue) + $Tokens | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique

    foreach ($moduleName in $ModuleNames) {
        foreach ($pattern in $patterns) {
            $wildcard = "*$pattern*"
            foreach ($flag in @('ClassMatches', 'PathMatches')) {
                $queryOutput = if ($flag -eq 'ClassMatches') {
                    & pwsh -NoProfile -File $queryScript -Module $moduleName -ClassMatches $wildcard -Json 2>$null
                }
                else {
                    & pwsh -NoProfile -File $queryScript -Module $moduleName -PathMatches $wildcard -Json 2>$null
                }
                foreach ($item in (Convert-JsonLinesOutput -OutputLines $queryOutput)) {
                    $item | Add-Member -NotePropertyName module -NotePropertyValue $moduleName -Force
                    $byPath["$moduleName::$($item.path)"] = $item
                }
            }
        }
    }

    return @(
        $byPath.Values |
        Sort-Object -Descending -Property @(
            @{ Expression = { Get-TextMatchScore -Text ("$($_.class) $($_.path) $($_.role)") -TopicValue $TopicValue -Tokens $Tokens } },
            @{ Expression = { $_.path } }
        ) |
        Select-Object -First 12
    )
}

function Get-ActivityMatches {
    param(
        [string]$TopicValue,
        [string[]]$Tokens,
        [string]$ModuleName
    )

    $queryScript = Join-Path $Root 'dev/ACTIVITY_CATALOG/scripts/query.ps1'
    $byKey = @{}
    $patterns = @($TopicValue) + $Tokens | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique

    foreach ($pattern in $patterns) {
        $queryRaw = (& pwsh -NoProfile -File $queryScript -Module $ModuleName -Search $pattern -Json 2>$null | Out-String)
        foreach ($item in (Convert-JsonDocumentOutput -RawText $queryRaw)) {
            $byKey["$($item.module)::$($item.package)::$($item.class)"] = $item
        }
    }

    return @(
        $byKey.Values |
        Sort-Object -Descending -Property @(
            @{ Expression = {
                    $tags = Get-OptionalPropertyValue -InputObject $_ -PropertyName 'tags'
                    $className = Get-OptionalPropertyValue -InputObject $_ -PropertyName 'class'
                    $role = Get-OptionalPropertyValue -InputObject $_ -PropertyName 'role'
                    $package = Get-OptionalPropertyValue -InputObject $_ -PropertyName 'package'
                    Get-TextMatchScore -Text ("$className $role $package $(($tags -join ' '))") -TopicValue $TopicValue -Tokens $Tokens
                } 
            },
            @{ Expression = { Get-OptionalPropertyValue -InputObject $_ -PropertyName 'class' } }
        ) |
        Select-Object -First 10
    )
}

function Get-SpecMatches {
    param(
        [string]$TopicValue,
        [string[]]$Tokens
    )

    $searchScript = Join-Path $Root 'scripts/spec_catalog/search.ps1'
    $byKey = @{}
    $patterns = @($TopicValue) + $Tokens | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique

    foreach ($pattern in $patterns) {
        $searchRaw = (& pwsh -NoProfile -File $searchScript -Query $pattern -Format json 2>$null | Out-String)
        foreach ($item in (Convert-JsonDocumentOutput -RawText $searchRaw)) {
            $id = Get-OptionalPropertyValue -InputObject $item -PropertyName 'id'
            $file = Get-OptionalPropertyValue -InputObject $item -PropertyName 'file'
            $name = Get-OptionalPropertyValue -InputObject $item -PropertyName 'name'
            $key = if ($id) { $id } elseif ($file) { $file } else { $name }
            $byKey[$key] = $item
        }
    }

    return @(
        $byKey.Values |
        Sort-Object -Descending -Property @(
            @{ Expression = {
                    $id = Get-OptionalPropertyValue -InputObject $_ -PropertyName 'id'
                    $name = Get-OptionalPropertyValue -InputObject $_ -PropertyName 'name'
                    $title = Get-OptionalPropertyValue -InputObject $_ -PropertyName 'title'
                    $file = Get-OptionalPropertyValue -InputObject $_ -PropertyName 'file'
                    Get-TextMatchScore -Text ("$id $name $title $file") -TopicValue $TopicValue -Tokens $Tokens
                } 
            },
            @{ Expression = {
                    $priority = Get-OptionalPropertyValue -InputObject $_ -PropertyName 'priority'
                    if ($null -eq $priority) { return 0 }
                    return [int]$priority
                } 
            }
        ) |
        Select-Object -First 8
    )
}

function Get-ScoredFileMatches {
    param(
        [string[]]$Directories,
        [string[]]$Extensions,
        [string]$TopicValue,
        [string[]]$Tokens,
        [int]$MaxResults = 12
    )

    $results = New-Object System.Collections.Generic.List[object]

    foreach ($directory in $Directories) {
        if (-not (Test-Path $directory)) {
            continue
        }

        foreach ($file in Get-ChildItem -Path $directory -Recurse -File) {
            if ($file.Extension -notin $Extensions) {
                continue
            }

            $score = Get-TextMatchScore -Text $file.Name -TopicValue $TopicValue -Tokens $Tokens

            try {
                $content = Get-Content -Path $file.FullName -Raw -Encoding UTF8
            }
            catch {
                $content = ''
            }

            if ($content) {
                $score += [Math]::Min((Get-TextMatchScore -Text $content -TopicValue $TopicValue -Tokens $Tokens), 24)
            }

            if ($score -le 0) {
                continue
            }

            $results.Add([PSCustomObject]@{
                    path  = Resolve-RelativePath -AbsolutePath $file.FullName
                    score = $score
                })
        }
    }

    return @(
        $results |
        Sort-Object -Descending -Property score, path |
        Group-Object path |
        ForEach-Object { $_.Group[0] } |
        Select-Object -First $MaxResults
    )
}

function Resolve-CatalogSourcePath {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Record
    )

    $moduleRoot = if ($Record.module -eq 'wear') {
        'wear/src/main/java'
    }
    else {
        'app_v2/src/main/java'
    }

    $candidate = Join-Path $Root (Join-Path $moduleRoot $Record.path)
    if (Test-Path $candidate) {
        return Resolve-RelativePath -AbsolutePath $candidate
    }

    return $null
}

function Resolve-ActivitySourcePath {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Record
    )

    $moduleRoot = if ($Record.module -eq 'wear') {
        'wear/src/main/java'
    }
    else {
        'app_v2/src/main/java'
    }

    $packagePath = ($Record.package -replace '\.', '/')
    $candidate = Join-Path $Root (Join-Path $moduleRoot (Join-Path $packagePath ($Record.class + '.kt')))
    if (Test-Path $candidate) {
        return Resolve-RelativePath -AbsolutePath $candidate
    }

    return $null
}

function Get-RecommendedFirstDocs {
    param(
        [string]$TopicValue,
        [string[]]$Tokens,
        [string]$ModuleName,
        [string]$FlavorName,
        [object[]]$SpecMatches
    )

    $reads = New-Object System.Collections.Generic.List[string]
    $topicText = ("$TopicValue " + ($Tokens -join ' ')).ToLowerInvariant()

    Add-UniquePath -List $reads -Path 'dev/PROJECT_OPERATIONS_INDEX.md'
    Add-UniquePath -List $reads -Path 'docs/ARCHITECTURE.md'
    Add-UniquePath -List $reads -Path 'docs/DEV_OPS.md'
    Add-UniquePath -List $reads -Path 'docs/TECH_STACK.md'
    Add-UniquePath -List $reads -Path 'dev/TECH_REQUIREMENTS.md'

    if ($topicText -match 'network|smb|ftp|sftp|socket|connection|cloud|drive|dropbox|onedrive|auth') {
        Add-UniquePath -List $reads -Path 'dev/NETWORK_SPECS.md'
    }

    switch ($ModuleName) {
        'app_v2' {
            Add-UniquePath -List $reads -Path 'dev/CATALOG/app_v2.md'
            Add-UniquePath -List $reads -Path 'dev/ACTIVITY_CATALOG/app_v2.md'
            Add-UniquePath -List $reads -Path 'app_v2/build.gradle.kts'
        }
        'wear' {
            Add-UniquePath -List $reads -Path 'dev/CATALOG/wear.md'
            Add-UniquePath -List $reads -Path 'dev/ACTIVITY_CATALOG/wear.md'
            Add-UniquePath -List $reads -Path 'wear/build.gradle.kts'
        }
        default {
            Add-UniquePath -List $reads -Path 'dev/CATALOG/app_v2.md'
            Add-UniquePath -List $reads -Path 'dev/CATALOG/wear.md'
            Add-UniquePath -List $reads -Path 'dev/ACTIVITY_CATALOG/app_v2.md'
            Add-UniquePath -List $reads -Path 'dev/ACTIVITY_CATALOG/wear.md'
            Add-UniquePath -List $reads -Path 'app_v2/build.gradle.kts'
            Add-UniquePath -List $reads -Path 'wear/build.gradle.kts'
        }
    }

    if ($FlavorName) {
        Add-UniquePath -List $reads -Path 'app_v2/build.gradle.kts'
    }

    foreach ($spec in $SpecMatches | Select-Object -First 2) {
        $specFile = Get-OptionalPropertyValue -InputObject $spec -PropertyName 'file'
        if ($specFile) {
            Add-UniquePath -List $reads -Path ($specFile -replace '\\', '/')
        }
    }

    return @($reads)
}

$tokens = Get-TopicTokens -Value $Topic
$outputPath = Resolve-DossierOutputPath -RequestedPath $OutFile -TopicValue $Topic
$catalogModules = if ($Module -eq 'all') { @('app_v2', 'wear') } else { @($Module) }

$catalogMatches = Get-CatalogMatches -TopicValue $Topic -Tokens $tokens -ModuleNames $catalogModules
$activityMatches = Get-ActivityMatches -TopicValue $Topic -Tokens $tokens -ModuleName $Module
$specMatches = Get-SpecMatches -TopicValue $Topic -Tokens $tokens
$planFileMatches = Get-ScoredFileMatches -Directories @(Join-Path $Root 'PLAN') -Extensions @('.md') -TopicValue $Topic -Tokens $tokens -MaxResults 8
$docDevMatches = Get-ScoredFileMatches -Directories @(Join-Path $Root 'docs', Join-Path $Root 'dev') -Extensions @('.md') -TopicValue $Topic -Tokens $tokens -MaxResults 10
$recommendedReads = Get-RecommendedFirstDocs -TopicValue $Topic -Tokens $tokens -ModuleName $Module -FlavorName $Flavor -SpecMatches $specMatches

$nextReads = New-Object System.Collections.Generic.List[string]
foreach ($path in $recommendedReads) {
    Add-UniquePath -List $nextReads -Path $path
}
foreach ($record in $catalogMatches | Select-Object -First 4) {
    Add-UniquePath -List $nextReads -Path (Resolve-CatalogSourcePath -Record $record)
}
foreach ($record in $activityMatches | Select-Object -First 2) {
    Add-UniquePath -List $nextReads -Path (Resolve-ActivitySourcePath -Record $record)
}
foreach ($record in $docDevMatches | Select-Object -First 4) {
    Add-UniquePath -List $nextReads -Path $record.path
}
foreach ($record in $planFileMatches | Select-Object -First 2) {
    Add-UniquePath -List $nextReads -Path $record.path
}
foreach ($record in $specMatches | Select-Object -First 2) {
    $specFile = Get-OptionalPropertyValue -InputObject $record -PropertyName 'file'
    if ($specFile) {
        Add-UniquePath -List $nextReads -Path ($specFile -replace '\\', '/')
    }
}

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add('# Research Dossier')
$lines.Add('')
$lines.Add("- Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
$lines.Add("- Topic: $Topic")
$lines.Add("- Module: $Module")
$lines.Add("- Flavor: $(if ($Flavor) { $Flavor } else { 'n/a' })")
$lines.Add('')

$lines.Add('## Recommended First Docs')
if (@($recommendedReads).Count -gt 0) {
    foreach ($path in $recommendedReads) {
        $lines.Add("- $path")
    }
}
else {
    $lines.Add('- None')
}
$lines.Add('')

$lines.Add('## Matching CATALOG Classes')
if (@($catalogMatches).Count -gt 0) {
    foreach ($record in $catalogMatches) {
        $role = if ($record.role) { $record.role } else { 'n/a' }
        $lines.Add("- [$($record.module)] $($record.class) :: $($record.path) :: $role")
    }
}
else {
    $lines.Add('- None')
}
$lines.Add('')

$lines.Add('## Matching ACTIVITY_CATALOG Entries')
if (@($activityMatches).Count -gt 0) {
    foreach ($record in $activityMatches) {
        $roleValue = Get-OptionalPropertyValue -InputObject $record -PropertyName 'role'
        $tagsValue = Get-OptionalPropertyValue -InputObject $record -PropertyName 'tags'
        $moduleValue = Get-OptionalPropertyValue -InputObject $record -PropertyName 'module'
        $classValue = Get-OptionalPropertyValue -InputObject $record -PropertyName 'class'
        $role = if ($roleValue) { $roleValue } else { 'n/a' }
        $tags = if ($tagsValue) { ($tagsValue -join ', ') } else { 'n/a' }
        $lines.Add("- [$moduleValue] $classValue :: $role :: tags=$tags")
    }
}
else {
    $lines.Add('- None')
}
$lines.Add('')

$lines.Add('## Matching PLAN Specs and Files')
$lines.Add('### Spec Catalog')
if (@($specMatches).Count -gt 0) {
    foreach ($record in $specMatches) {
        $id = Get-OptionalPropertyValue -InputObject $record -PropertyName 'id'
        $status = Get-OptionalPropertyValue -InputObject $record -PropertyName 'status'
        $name = Get-OptionalPropertyValue -InputObject $record -PropertyName 'name'
        $fileValue = Get-OptionalPropertyValue -InputObject $record -PropertyName 'file'
        $file = if ($fileValue) { ($fileValue -replace '\\', '/') } else { 'n/a' }
        $lines.Add("- $id :: $status :: $name :: $file")
    }
}
else {
    $lines.Add('- None')
}
$lines.Add('')
$lines.Add('### PLAN Files')
if (@($planFileMatches).Count -gt 0) {
    foreach ($record in $planFileMatches) {
        $lines.Add("- $($record.path)")
    }
}
else {
    $lines.Add('- None')
}
$lines.Add('')

$lines.Add('## Matching Docs and Dev Files')
if (@($docDevMatches).Count -gt 0) {
    foreach ($record in $docDevMatches) {
        $lines.Add("- $($record.path)")
    }
}
else {
    $lines.Add('- None')
}
$lines.Add('')

$lines.Add('## Suggested Next Reads')
if (@($nextReads).Count -gt 0) {
    foreach ($path in ($nextReads | Select-Object -First 10)) {
        $lines.Add("- $path")
    }
}
else {
    $lines.Add('- None')
}

Set-Content -Path $outputPath -Value $lines -Encoding UTF8
Write-Host "Research dossier written: $outputPath" -ForegroundColor Green