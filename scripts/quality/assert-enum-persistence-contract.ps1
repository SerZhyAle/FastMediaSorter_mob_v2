#requires -Version 7.0
<#
.SYNOPSIS
    S1674: prevent R8 from renaming enum members persisted as durable strings.

.DESCRIPTION
    Room converters, DataStore and SharedPreferences retain values across application updates.
    An enum written with `name` and restored with `valueOf` needs a base release rule preserving
    its fields, otherwise a later R8 mapping can make the persisted value unreadable.

    S2364 rebuilt how a name is derived, because both halves of the old derivation were wrong in a
    way a green run could not show:

      - a nested enum's real name under R8 is `Outer$Inner`, and the gate built `package + simple
        name`, so the rule it demanded named a class that does not exist and R8 ignored silently;
      - the receiver of `.name` was matched against enum simple names case-insensitively, so a
        variable called `mode` was credited to an enum called `Mode`. That produced a demand for an
        enum nobody persists and, at the same time, hid the enum that is actually persisted there.

    A declaration is therefore parsed with a brace walk over text whose comments and string literals
    have been blanked, and a `.name` receiver is credited only when its declared type resolves to a
    known enum. A receiver that cannot be resolved is credited to nothing: guessing is what produced
    both a false red and a false green.

    Every `-keepclassmembernames enum` rule in the base ProGuard file is also matched back against a
    real declaration. A rule naming no class protects nothing while reading as satisfied.

    With -Mapping, the check goes past the rule text and reads a real R8 mapping: rule wording that
    looks right still proves nothing about what the optimizer did, so a minified artifact is the only
    evidence that a persisted member name survived.

.EXIT CODES
    0 - every durable enum-name path has a matching base rule (and, with -Mapping, kept its name).
    1 - a durable enum-name path is unpinned, a base rule names no declared class, or the mapping
        shows a renamed member.
    2 - source, the base ProGuard file or the named mapping could not be inspected.
#>
[CmdletBinding()]
param(
    [string]$Mapping,
    [string]$RepoRoot,
    [switch]$Gate,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
}
$durableRoot = Join-Path $RepoRoot 'app_v2/src/main/java'
$proguardPath = Join-Path $RepoRoot 'app_v2/proguard-rules.pro'

if (-not (Test-Path -LiteralPath $durableRoot) -or -not (Test-Path -LiteralPath $proguardPath)) {
    Write-Error 'assert-enum-persistence-contract: app source or base ProGuard rules are missing.' -ErrorAction Continue
    exit 2
}

# Comments and string literals are blanked rather than removed so every offset stays valid for the
# brace walk below. Kotlin templates are tracked properly: `"${ f("x") }"` carries a nested string,
# and a scanner that stopped at the first closing quote would leak that string's braces into the
# walk and mis-parent every declaration after it.
function Remove-KotlinNoise {
    param([string]$Text)

    $chars = $Text.ToCharArray()
    $out = $Text.ToCharArray()
    $n = $chars.Length
    $stack = [System.Collections.Generic.List[hashtable]]::new()
    $stack.Add(@{ Kind = 'code'; IsTemplate = $false; Brace = 0 })

    function Test-Newline([char]$c) { $c -eq "`n" -or $c -eq "`r" }

    $i = 0
    while ($i -lt $n) {
        $top = $stack[$stack.Count - 1]
        $c = $chars[$i]
        $c1 = if ($i + 1 -lt $n) { $chars[$i + 1] } else { [char]0 }
        $c2 = if ($i + 2 -lt $n) { $chars[$i + 2] } else { [char]0 }

        if ($top.Kind -eq 'code') {
            if ($c -eq '/' -and $c1 -eq '/') {
                while ($i -lt $n -and -not (Test-Newline $chars[$i])) { $out[$i] = ' '; $i++ }
                continue
            }
            if ($c -eq '/' -and $c1 -eq '*') {
                $out[$i] = ' '; $out[$i + 1] = ' '; $i += 2
                while ($i -lt $n) {
                    if ($chars[$i] -eq '*' -and $i + 1 -lt $n -and $chars[$i + 1] -eq '/') {
                        $out[$i] = ' '; $out[$i + 1] = ' '; $i += 2; break
                    }
                    if (-not (Test-Newline $chars[$i])) { $out[$i] = ' ' }
                    $i++
                }
                continue
            }
            if ($c -eq '"' -and $c1 -eq '"' -and $c2 -eq '"') {
                $out[$i] = ' '; $out[$i + 1] = ' '; $out[$i + 2] = ' '; $i += 3
                $stack.Add(@{ Kind = 'tstr'; IsTemplate = $false; Brace = 0 })
                continue
            }
            if ($c -eq '"') {
                $out[$i] = ' '; $i++
                $stack.Add(@{ Kind = 'str'; IsTemplate = $false; Brace = 0 })
                continue
            }
            if ($c -eq "'") {
                $out[$i] = ' '; $i++
                while ($i -lt $n -and $chars[$i] -ne "'") {
                    if ($chars[$i] -eq '\' -and $i + 1 -lt $n) { $out[$i] = ' '; $out[$i + 1] = ' '; $i += 2; continue }
                    $out[$i] = ' '; $i++
                }
                if ($i -lt $n) { $out[$i] = ' '; $i++ }
                continue
            }
            # Code inside a `${..}` template is kept, not blanked: a persisted preference key is
            # routinely assembled as "$PREFIX${value.name}", and blanking the template would hide
            # the one `.name` the gate exists to find. Its inner braces balance among themselves, so
            # the walk stays sound; only the brace closing the template is blanked, because the
            # `${` that opened it was.
            if ($top.IsTemplate) {
                if ($c -eq '{') { $top.Brace++; $i++; continue }
                if ($c -eq '}') {
                    if ($top.Brace -eq 0) { $out[$i] = ' '; $i++; [void]$stack.RemoveAt($stack.Count - 1); continue }
                    $top.Brace--
                }
                $i++
                continue
            }
            $i++
            continue
        }

        if ($top.Kind -eq 'str') {
            if ($c -eq '\' -and $i + 1 -lt $n) { $out[$i] = ' '; $out[$i + 1] = ' '; $i += 2; continue }
            if ($c -eq '$' -and $c1 -eq '{') {
                $out[$i] = ' '; $out[$i + 1] = ' '; $i += 2
                $stack.Add(@{ Kind = 'code'; IsTemplate = $true; Brace = 0 })
                continue
            }
            if ($c -eq '"') { $out[$i] = ' '; $i++; [void]$stack.RemoveAt($stack.Count - 1); continue }
            # A single-quoted Kotlin string cannot span a line; recovering here keeps one unbalanced
            # quote from blanking the rest of the file.
            if (Test-Newline $c) { $i++; [void]$stack.RemoveAt($stack.Count - 1); continue }
            $out[$i] = ' '; $i++
            continue
        }

        # tstr
        if ($c -eq '"' -and $c1 -eq '"' -and $c2 -eq '"') {
            $out[$i] = ' '; $out[$i + 1] = ' '; $out[$i + 2] = ' '; $i += 3
            [void]$stack.RemoveAt($stack.Count - 1)
            continue
        }
        if ($c -eq '$' -and $c1 -eq '{') {
            $out[$i] = ' '; $out[$i + 1] = ' '; $i += 2
            $stack.Add(@{ Kind = 'code'; IsTemplate = $true; Brace = 0 })
            continue
        }
        if (-not (Test-Newline $c)) { $out[$i] = ' ' }
        $i++
    }

    return [string]::new($out)
}

# True when the text between a declaration's name and a `{` is that declaration's own header rather
# than the gap before an unrelated body. `class Foo(val x: Int)` has no body, so the next `{` in the
# file belongs to something else and adopting it would parent every nested declaration wrongly.
function Test-DeclarationHeader {
    param([string]$Between)

    if ($Between -match '[;}]') { return $false }
    $reduced = $Between
    for ($pass = 0; $pass -lt 12; $pass++) {
        $next = [regex]::Replace($reduced, '\([^()]*\)', ' ')
        $next = [regex]::Replace($next, '<[^<>]*>', ' ')
        if ($next -eq $reduced) { break }
        $reduced = $next
    }
    return $reduced -notmatch '\b(fun|val|var|init|companion|class|object|interface|enum)\b'
}

function Get-DeclaredEnums {
    param([string]$PackageName, [string]$Stripped)

    $found = [System.Collections.Generic.List[object]]::new()
    $decls = [regex]::Matches($Stripped, '\b(enum\s+class|class|interface|object)\s+(\w+)')
    if ($decls.Count -eq 0) { return $found }

    $declAt = @{}
    foreach ($d in $decls) { $declAt[$d.Index] = $d }

    $names = [System.Collections.Generic.List[string]]::new()
    $depths = [System.Collections.Generic.List[int]]::new()
    $depth = 0
    $pending = $null

    for ($i = 0; $i -lt $Stripped.Length; $i++) {
        if ($declAt.ContainsKey($i)) {
            $d = $declAt[$i]
            $pending = [pscustomobject]@{
                Name   = $d.Groups[2].Value
                IsEnum = $d.Groups[1].Value.StartsWith('enum')
                End    = $d.Index + $d.Length
            }
            continue
        }
        $c = $Stripped[$i]
        if ($c -eq '{') {
            if ($null -ne $pending -and (Test-DeclarationHeader $Stripped.Substring($pending.End, $i - $pending.End))) {
                $names.Add($pending.Name)
                $depths.Add($depth)
                if ($pending.IsEnum) {
                    $found.Add([pscustomobject]@{
                            Fqn     = "$PackageName." + ($names -join '$')
                            Package = $PackageName
                            Simple  = $pending.Name
                        })
                }
            }
            $pending = $null
            $depth++
        }
        elseif ($c -eq '}') {
            $pending = $null
            if ($depth -gt 0) { $depth-- }
            if ($names.Count -gt 0 -and $depths[$depths.Count - 1] -eq $depth) {
                $names.RemoveAt($names.Count - 1)
                $depths.RemoveAt($depths.Count - 1)
            }
        }
    }
    return $found
}

# Declarations are collected across every source set, not just main: a rule may legitimately pin an
# enum that only a flavour declares, and judging such a rule dead would be a false red.
$declarationRoots = @()
$srcRoot = Join-Path $RepoRoot 'app_v2/src'
if (Test-Path -LiteralPath $srcRoot) {
    foreach ($set in Get-ChildItem -LiteralPath $srcRoot -Directory) {
        foreach ($lang in @('java', 'kotlin')) {
            $candidate = Join-Path $set.FullName $lang
            if (Test-Path -LiteralPath $candidate) { $declarationRoots += $candidate }
        }
    }
}
if ($declarationRoots.Count -eq 0) { $declarationRoots = @($durableRoot) }

$allEnumFqns = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$enumsBySimple = [System.Collections.Generic.Dictionary[string, System.Collections.Generic.List[object]]]::new([System.StringComparer]::Ordinal)

foreach ($root in $declarationRoots) {
    foreach ($file in Get-ChildItem -LiteralPath $root -Filter '*.kt' -Recurse) {
        $text = Get-Content -LiteralPath $file.FullName -Raw
        if ([string]::IsNullOrEmpty($text)) { continue }
        # The brace walk is the expensive half; only a file that declares an enum at all can
        # contribute one, so the rest of the tree never pays for it.
        if ($text -notmatch '\benum\s+class\b') { continue }
        $packageMatch = [regex]::Match($text, '(?m)^package\s+([\w.]+)')
        if (-not $packageMatch.Success) { continue }
        foreach ($decl in (Get-DeclaredEnums -PackageName $packageMatch.Groups[1].Value -Stripped (Remove-KotlinNoise $text))) {
            if (-not $allEnumFqns.Add($decl.Fqn)) { continue }
            if (-not $enumsBySimple.ContainsKey($decl.Simple)) {
                $enumsBySimple[$decl.Simple] = [System.Collections.Generic.List[object]]::new()
            }
            $enumsBySimple[$decl.Simple].Add($decl)
        }
    }
}

# A simple name can be declared more than once in the tree. Prefer the declaration this very file
# makes, then an explicit import, then the file's own package; when none of those decides, demand a
# rule for every candidate. Closing over all of them is the only safe direction - picking one by
# enumeration order is exactly how S2364's dead rule was chosen.
function Resolve-EnumCandidates {
    param([string]$Simple, [string[]]$FileFqns, [string[]]$Imports, [string]$PackageName)

    if (-not $enumsBySimple.ContainsKey($Simple)) { return @() }
    $candidates = @($enumsBySimple[$Simple])
    if ($candidates.Count -eq 1) { return @($candidates[0].Fqn) }

    $inFile = @($candidates | Where-Object { $FileFqns -contains $_.Fqn })
    if ($inFile.Count -eq 1) { return @($inFile[0].Fqn) }

    $imported = @($candidates | Where-Object { $Imports -contains ($_.Fqn -replace '\$', '.') })
    if ($imported.Count -eq 1) { return @($imported[0].Fqn) }

    $samePackage = @($candidates | Where-Object { $_.Package -eq $PackageName })
    if ($samePackage.Count -eq 1) { return @($samePackage[0].Fqn) }

    return @($candidates | ForEach-Object { $_.Fqn })
}

$durableEnums = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
foreach ($file in Get-ChildItem -LiteralPath $durableRoot -Filter '*.kt' -Recurse) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    if ([string]::IsNullOrEmpty($text)) { continue }
    # Pre-filter on the raw text so the brace walk only runs for a file that can be durable at all.
    # A marker sitting in a comment survives this pass and is dropped by the stripped test below.
    if ($text -notmatch '@TypeConverter|DataStore<Preferences>|SharedPreferences|getSharedPreferences') { continue }
    $stripped = Remove-KotlinNoise $text
    if ($stripped -notmatch '@TypeConverter|DataStore<Preferences>|SharedPreferences|getSharedPreferences') { continue }

    $packageMatch = [regex]::Match($text, '(?m)^package\s+([\w.]+)')
    $packageName = if ($packageMatch.Success) { $packageMatch.Groups[1].Value } else { '' }
    $imports = @([regex]::Matches($text, '(?m)^import\s+([\w.]+)') | ForEach-Object { $_.Groups[1].Value })
    $fileFqns = @()
    if ($packageName -and $text -match '\benum\s+class\b') {
        $fileFqns = @(Get-DeclaredEnums -PackageName $packageName -Stripped $stripped | ForEach-Object { $_.Fqn })
    }

    # An identifier's declared type is the only sound way to read `receiver.name`. Both parameters
    # and explicitly typed properties use the same `name: Type` shape, so one pattern covers them.
    $identifierTypes = [System.Collections.Generic.Dictionary[string, System.Collections.Generic.HashSet[string]]]::new([System.StringComparer]::Ordinal)
    foreach ($m in [regex]::Matches($stripped, '\b(\w+)\s*:\s*([A-Za-z_]\w*)')) {
        $id = $m.Groups[1].Value
        if (-not $identifierTypes.ContainsKey($id)) {
            $identifierTypes[$id] = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        }
        [void]$identifierTypes[$id].Add($m.Groups[2].Value)
    }

    $typeNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    # `Type.valueOf(` is a reference to the type itself, so the captured token is the type.
    foreach ($m in [regex]::Matches($stripped, '\b(\w+)\.valueOf\s*\(')) { [void]$typeNames.Add($m.Groups[1].Value) }
    foreach ($m in [regex]::Matches($stripped, '\b(\w+)\.name\b')) {
        $receiver = $m.Groups[1].Value
        if ($enumsBySimple.ContainsKey($receiver)) { [void]$typeNames.Add($receiver); continue }
        if (-not $identifierTypes.ContainsKey($receiver)) { continue }
        foreach ($type in $identifierTypes[$receiver]) { [void]$typeNames.Add($type) }
    }

    foreach ($type in $typeNames) {
        foreach ($fqn in (Resolve-EnumCandidates -Simple $type -FileFqns $fileFqns -Imports $imports -PackageName $packageName)) {
            [void]$durableEnums.Add($fqn)
        }
    }
}

$rules = Get-Content -LiteralPath $proguardPath -Raw
$missing = [System.Collections.Generic.List[string]]::new()
foreach ($enum in ($durableEnums | Sort-Object)) {
    $rule = "(?s)-keepclassmembernames\s+enum\s+$([regex]::Escape($enum))\s*\{\s*<fields>;\s*\}"
    if ($rules -notmatch $rule) { $missing.Add($enum) }
}

# A rule naming no declared class is ignored by R8 in silence while reading as a satisfied contract.
# Wildcard rules are deliberately not captured here - they name a shape, not a class.
$dead = [System.Collections.Generic.List[string]]::new()
foreach ($m in [regex]::Matches($rules, '(?m)^\s*-keepclassmembernames\s+enum\s+([\w.$]+)')) {
    $named = $m.Groups[1].Value
    if (-not $allEnumFqns.Contains($named)) { $dead.Add($named) }
}

if ($durableEnums.Count -eq 0) {
    Write-Error 'assert-enum-persistence-contract: no durable enum-name path was discovered.' -ErrorAction Continue
    exit 2
}
if ($missing.Count -gt 0 -or $dead.Count -gt 0) {
    foreach ($enum in $missing) {
        Write-Error "assert-enum-persistence-contract: missing -keepclassmembernames enum rule for $enum." -ErrorAction Continue
    }
    foreach ($enum in $dead) {
        Write-Error "assert-enum-persistence-contract: -keepclassmembernames enum rule names no declared class - $enum." -ErrorAction Continue
    }
    exit 1
}
if (-not $Mapping) {
    if (-not ($Gate -or $Quiet)) {
        Write-Host "assert-enum-persistence-contract: PASS ($($durableEnums.Count) durable enum(s) pinned; $($allEnumFqns.Count) declared enum(s) scanned)."
    }
    exit 0
}

if (-not (Test-Path -LiteralPath $Mapping)) {
    Write-Error "assert-enum-persistence-contract: mapping file not found at $Mapping." -ErrorAction Continue
    exit 2
}

# An enum constant is a static field whose type is the enum itself, so its mapping line names the
# enum as the type and, when R8 left it alone, carries the same identifier on both sides of the arrow.
# Read in one streaming pass: a real standard-release mapping is ~170 MB, and loading it into an
# array costs minutes and gigabytes for a check that only ever needs the current line.
$targets = @{}
foreach ($enum in $durableEnums) { $targets[$enum] = $true }
$renamed = [System.Collections.Generic.List[string]]::new()
$inspected = 0
$currentEnum = $null

$reader = [System.IO.StreamReader]::new($Mapping)
try {
    while ($null -ne ($line = $reader.ReadLine())) {
        if ($line -notmatch '^\s') {
            $classLine = [regex]::Match($line, '^([\w.$]+)\s+->\s+')
            # Absent from the file means R8 removed the class outright - a reachability question,
            # not a rename, so it is not counted and not reported here.
            $currentEnum = if ($classLine.Success -and $targets.ContainsKey($classLine.Groups[1].Value)) {
                $inspected++
                $classLine.Groups[1].Value
            } else {
                $null
            }
            continue
        }
        if (-not $currentEnum) { continue }
        $member = [regex]::Match($line, '^\s+([\w.$]+)\s+(\w+)\s+->\s+(\w+)\s*$')
        if (-not $member.Success) { continue }
        if ($member.Groups[1].Value -ne $currentEnum) { continue }
        if ($member.Groups[2].Value -cne $member.Groups[3].Value) {
            $renamed.Add("$currentEnum.$($member.Groups[2].Value) -> $($member.Groups[3].Value)")
        }
    }
} finally {
    $reader.Dispose()
}

if ($renamed.Count -gt 0) {
    foreach ($entry in $renamed) {
        Write-Error "assert-enum-persistence-contract: persisted enum member renamed by R8 - $entry." -ErrorAction Continue
    }
    exit 1
}
if ($inspected -eq 0) {
    Write-Error "assert-enum-persistence-contract: no durable enum appears in $Mapping - nothing was proven." -ErrorAction Continue
    exit 2
}
if (-not ($Gate -or $Quiet)) {
    Write-Host "assert-enum-persistence-contract: PASS ($($durableEnums.Count) durable enum(s) pinned; $inspected verified against $Mapping)."
}
exit 0
