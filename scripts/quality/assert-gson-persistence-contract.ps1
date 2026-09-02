<#
.SYNOPSIS
    S1639: inventory every Gson serialization point, classify whether its JSON outlives the process, and
    judge whether the model behind each durable point has its wire names pinned.

.DESCRIPTION
    A model serialized by Gson keeps its wire names only while one R8 mapping is in force. When the JSON
    outlives the process - a file, shared preferences, DataStore, the Wear data layer, a user-facing export -
    the writer and the reader can be two different builds, and the keys stop matching. The class of defect
    has reached users six times (S0719, S0737, S1630, S1631, S1632, S1638).

    Durability is decided by the sink rather than by a marking on the model, because a marking would need the
    same discipline that has already failed six times. A point whose sink cannot be identified counts as
    durable: an unnecessary entry costs one written justification, a missed model costs a user incident.

    A durable model is judged in three outcomes rather than two. Partial annotation is reported as its own
    kind because it reads as protected at a glance and therefore survives review while still being broken.

    A model reached only as a property of another durable model is durable too: its field names travel inside
    the same JSON and are renamed by the same mapping.

    The only suppression path is gson-persistence-exemptions-baseline.txt beside this script. Every entry
    carries a written justification, a line without one refuses the whole run, and the verdict line always
    states how many findings the registry is hiding and how many of those are live defects with an owner.

.PARAMETER Module
    Which module to scan. 'all' scans both.

.PARAMETER Format
    'text' prints a human report, 'json' emits the raw point and model lists for a caller.

.PARAMETER ChangedFiles
    Optional CSV of repo-relative paths. When supplied, only points in those files are reported.

.PARAMETER Gate
    Batch-runner mode: print only the findings the registry does not cover, plus the verdict line.

.PARAMETER Quiet
    Same output reduction as -Gate, for a caller that wants the terse report without the gate framing.

.PARAMETER RepoRoot
    Repository root to scan. Defaults to the root this script sits in. A test suite hands a synthetic tree
    here, which is the only way to exercise a shape the real repository does not currently contain.

.OUTPUTS
    Exit 0 - scan completed, no violation raised.
    Exit 1 - at least one durable model is unpinned, partially pinned, or has an unresolvable type.
    Exit 2 - could not verify: an exemption entry carries no justification, a module root is missing, or no
             source root could be read.
#>

[CmdletBinding()]
param(
    [ValidateSet('app_v2', 'wear', 'all')]
    [string]$Module = 'all',

    [ValidateSet('text', 'json')]
    [string]$Format = 'text',

    [string]$ChangedFiles,

    [switch]$Gate,

    [switch]$Quiet,

    [string]$RepoRoot
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# PowerShell variable names are case-insensitive, so the parameter above and the $repoRoot every function
# below reads are one variable. The default is therefore assigned in place rather than copied into a
# second name that would only look separate.
if (-not $repoRoot) {
    $repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
}
# Normalised because a relative or trailing-separator root handed in by a test would throw off the
# Substring that turns each scanned file into a repo-relative path.
$repoRoot = (Resolve-Path -LiteralPath $repoRoot).Path.TrimEnd('\', '/')

# Sink signatures live in one table so a new kind of storage is a data edit, not a logic edit (strategic
# S1639 section 5.3). Order matters only in that the first match wins and is reported as the reason.
$DurableSinks = @(
    [pscustomobject]@{ Name = 'private-file'; Pattern = 'filesDir|writeText\s*\(|readText\s*\(|FileOutputStream|FileWriter|\.outputStream\(\)' }
    [pscustomobject]@{ Name = 'encrypted-preferences'; Pattern = 'EncryptedSharedPreferences|MasterKey' }
    [pscustomobject]@{ Name = 'shared-preferences'; Pattern = 'getSharedPreferences|SharedPreferences|prefs\.edit\s*\(' }
    [pscustomobject]@{ Name = 'datastore'; Pattern = 'dataStore|PreferencesKey|stringPreferencesKey' }
    [pscustomobject]@{ Name = 'wear-data-layer'; Pattern = 'DataClient|MessageClient|putDataItem|sendMessage|PutDataRequest|DataMapItem' }
    [pscustomobject]@{ Name = 'user-export'; Pattern = 'contentResolver\.openOutputStream|contentResolver\.openInputStream|DocumentFile' }
)

# A sink that provably does not survive the process. Kept separate so the "unknown means durable" default
# below stays visible rather than hiding inside an else-branch.
$TransientSinks = @(
    [pscustomobject]@{ Name = 'worker-payload'; Pattern = 'workDataOf|Data\.Builder|setProgress\s*\(|outputData|inputData' }
    [pscustomobject]@{ Name = 'network-request'; Pattern = 'RequestBody|MediaType\.parse|okhttp3|retrofit2' }
)

$ClassDecl = '^\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:public |internal |private )?(?:abstract |open |sealed |final )?(data class|enum class|value class|annotation class|class|object|interface)\s+([A-Za-z_]\w*)'
# The type is deliberately not captured here. A type argument list carries commas of its own, so any
# character class that stops at ',' reads `Map<String, Foo>` as `Map<String` - which names no model, so the
# walk never enqueues Foo and the gate silently stops judging it (S2341). The match ends at the colon and
# Get-TypeExpression reads the type from there by bracket depth. The lookahead keeps the old requirement
# that some type actually follows.
$PropDecl = '(?:^|[,(])\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*(?:override |private |internal |public |protected )*va[lr]\s+([A-Za-z_]\w*)\s*:\s*(?=[^\s,=)])'
# Gson field reflection carries state, and in this codebase state is declared as a data class or an enum.
# A service reached through the same identifier chain - a repository, a use case - is not a model, and
# admitting one would drag its whole injected graph into the report as fictional durable models.
$ModelKinds = @('data class', 'enum class', 'value class')
$EnumConstantMessage = 'Gson writes an enum by the constant name itself, so pinning the containing model - by @SerializedName on its properties or by a keep rule on the model - does not cover these constants. Annotate the constants or keep the enum by its own rule.'
# Gson declares no nullary overload of either method - every one of them takes the value, the reader or
# the type. A `x.toJson()` is therefore always some other type's own writer, in this tree a model that
# hand-builds an org.json.JSONObject. Admitting it invented a serialization point whose type was the
# receiver rather than an argument, which the resolver could not read and reported as unresolvable; the
# exemption registry then carried the invented point instead of the gate declining it (S2325).
$GsonCall = '\.toJson\s*\(\s*(?!\))|\.fromJson\s*\(\s*(?!\))'
$GsonArgs = '\.(?:toJson|fromJson)\s*\('
# Container names carry no wire names of their own - only their element type does.
$Containers = '^(List|MutableList|ArrayList|Set|MutableSet|Collection|Array|Map|MutableMap|Iterable|Sequence|Pair)$'

function Get-SourceRoot {
    param([string]$ModuleName)

    $moduleRoot = Join-Path $repoRoot (Join-Path $ModuleName 'src')
    if (-not (Test-Path -LiteralPath $moduleRoot)) {
        return @()
    }
    # Every source set that can reach a release build. Test sets are excluded: their JSON never ships.
    Get-ChildItem -LiteralPath $moduleRoot -Directory |
        Where-Object { $_.Name -notmatch '^(test|androidTest)' } |
        ForEach-Object { $_.FullName }
}

function Get-SinkVerdict {
    param([string]$FileText)

    foreach ($sink in $DurableSinks) {
        if ($FileText -match $sink.Pattern) {
            return [pscustomobject]@{ Durable = $true; Sink = $sink.Name }
        }
    }
    foreach ($sink in $TransientSinks) {
        if ($FileText -match $sink.Pattern) {
            return [pscustomobject]@{ Durable = $false; Sink = $sink.Name }
        }
    }
    # Strategic S1639 ADR-3: an unidentified sink is treated as durable, never skipped.
    return [pscustomobject]@{ Durable = $true; Sink = 'unidentified' }
}

function Get-TypeName {
    param([string]$Text)

    # A declaration fragment can name several types (`List<MediaFile>`); every one of them is a candidate,
    # and the caller drops whatever is not a project declaration.
    [regex]::Matches($Text, '[A-Za-z_]\w*') |
        ForEach-Object { $_.Value } |
        Where-Object { $_ -notmatch $Containers }
}

function Get-Declaration {
    param([string]$ModuleName, [string]$Name)

    # Keyed by module, never by simple name alone: both modules declare their own WearEventEnvelope,
    # WearSyncPayload and the rest of the contract, in different packages and under different obfuscation
    # rules. A shared key lets one module's copy answer for the other's - which is the false green this
    # gate exists to prevent, and it appears only in the default 'all' run.
    $key = "${ModuleName}::${Name}"
    if (-not $declarations.ContainsKey($key)) { return $null }
    return $declarations[$key]
}

function Test-ModelName {
    param([string]$ModuleName, [string]$Name)

    $declaration = Get-Declaration -ModuleName $ModuleName -Name $Name
    return $null -ne $declaration -and $ModelKinds -contains $declaration.kind
}

function Get-TypeExpression {
    param([string]$Text, [int]$Start)

    # A generic type argument list carries commas of its own, so the type ends at the first ',', '=' or ')'
    # that sits OUTSIDE every angle bracket. Reading it with a character class instead returned `Map<String`
    # for `Map<String, WearStreamUsage>`: a fully pinned model was reported as a point with an unresolvable
    # type, and behind a model's property the same truncation dropped the value model from the walk without
    # saying anything (S2341). Nesting needs no recursion here - Get-TypeName lifts every identifier out of
    # whatever this returns, so `Map<String, List<Foo>>` yields Foo in the same single pass.
    #
    # Round brackets are deliberately not tracked: a function type reads as `(Foo` both before and after this
    # change, and Gson does not serialize a lambda, so widening the parser there would be risk without gain.
    $depth = 0
    $builder = [System.Text.StringBuilder]::new()
    for ($k = $Start; $k -lt $Text.Length; $k++) {
        $char = $Text[$k]
        if ($char -eq '<') { $depth++ }
        elseif ($char -eq '>') { if ($depth -gt 0) { $depth-- } }
        elseif ($depth -eq 0 -and ($char -eq ',' -or $char -eq '=' -or $char -eq ')')) { break }
        [void]$builder.Append($char)
    }
    return $builder.ToString().Trim()
}

function Resolve-Identifier {
    param([string[]]$FileLines, [string]$Name)

    foreach ($line in $FileLines) {
        if ($line -notmatch "(?:va[lr]\s+|[,(]\s*)$Name\s*(?::|=)") { continue }
        # A named Gson type token holds the element type the declared type (`Type`) hides.
        if ($line -match 'TypeToken\s*<(.+)>\s*\(') { return $Matches[1] }
        $declared = [regex]::Match($line, "$Name\s*:\s*")
        if ($declared.Success) {
            $type = Get-TypeExpression -Text $line -Start ($declared.Index + $declared.Length)
            # An empty result means the colon was the last thing on the line, so the constructor branch
            # below is still the better answer rather than a type of nothing.
            if ($type) { return $type }
        }
        if ($line -match "$Name\s*=\s*([A-Za-z_]\w*)\s*\(") { return $Matches[1] }
    }
    return $null
}

function ConvertTo-ClassPattern {
    param([string]$Spec)

    # ProGuard wildcards differ by width: '**' crosses the package separator, '*' stops at it.
    $pattern = [regex]::Escape($Spec)
    $pattern = $pattern -replace '\\\*\\\*', '<<ANY>>'
    $pattern = $pattern -replace '\\\*', '[^.]*'
    $pattern = $pattern -replace '<<ANY>>', '.*'
    $pattern = $pattern -replace '\\\?', '.'
    return "^$pattern$"
}

function Get-KeepRule {
    param([string]$ModuleName)

    # Only the module's base rules file. A flavor-scoped file (proguard-nolegal.pro) pins nothing in the
    # flavor that ships to Play, so honouring it would produce the false green this gate exists to prevent.
    $path = Join-Path $repoRoot (Join-Path $ModuleName 'proguard-rules.pro')
    $rules = [System.Collections.Generic.List[object]]::new()
    if (-not (Test-Path -LiteralPath $path)) { return $rules }

    $lines = Get-Content -LiteralPath $path
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $match = [regex]::Match($lines[$i], '^\s*-(keep[a-z]*)((?:,[a-z]+)*)\s+(?:public |final |abstract )*(?:class|interface|enum)\s+([^\s{]+)(.*)$')
        if (-not $match.Success) { continue }
        $tail = $match.Groups[4].Value
        # `class * implements X` selects by supertype, so a name match against it would be a fiction.
        if ($tail -match '\b(implements|extends)\b') { continue }

        $body = $tail
        for ($j = $i + 1; $j -lt [Math]::Min($lines.Count, $i + 12) -and $body -notmatch '\}'; $j++) {
            $body += ' ' + $lines[$j]
        }
        if ($body -notmatch '\{(.*)\}') { continue }
        $members = $Matches[1]
        # An annotation-qualified member spec pins only the fields already carrying the annotation, which
        # the annotation check judges on its own - reading it as a blanket pin greens every model in the
        # tree, because one such rule exists for Gson itself.
        if ($members -match '@') { continue }
        if ($members -notmatch '(^|;|\s)(\*|\*\*|<fields>)\s*;') { continue }
        # `allowobfuscation` hands the name back to R8; a *names directive pins names by definition.
        if ($match.Groups[2].Value -match 'allowobfuscation' -and $match.Groups[1].Value -notmatch 'names') { continue }

        $rules.Add([pscustomobject]@{
                file    = "$ModuleName/proguard-rules.pro"
                line    = $i + 1
                text    = $lines[$i].Trim()
                pattern = ConvertTo-ClassPattern -Spec $match.Groups[3].Value
            })
    }
    return $rules
}

function Get-CallArgument {
    param([string]$Text, [int]$Start)

    # A nested call inside the argument list holds its own parentheses, so the list has to be closed by
    # depth rather than by the first ')' - stopping at that one hides every argument after the first
    # `readText(..)`, which is exactly where the model type usually sits.
    $depth = 1
    $builder = [System.Text.StringBuilder]::new()
    for ($k = $Start; $k -lt $Text.Length; $k++) {
        $char = $Text[$k]
        if ($char -eq '(') { $depth++ }
        elseif ($char -eq ')') {
            $depth--
            if ($depth -eq 0) { break }
        }
        [void]$builder.Append($char)
    }
    return $builder.ToString()
}

function Get-EnclosingClass {
    param([string[]]$FileLines, [int]$Index)

    for ($j = $Index; $j -ge 0; $j--) {
        $match = [regex]::Match($FileLines[$j], $ClassDecl)
        if ($match.Success) { return $match.Groups[2].Value }
    }
    return $null
}

function Get-EnclosingFunction {
    param([string[]]$FileLines, [int]$Index)

    for ($j = $Index; $j -ge 0; $j--) {
        if ($FileLines[$j] -match '^\s*(?:private |internal |public |protected )*(?:suspend )?fun\s+(?:<[^>]*>\s*)?([A-Za-z_]\w*)') {
            return $Matches[1]
        }
    }
    return $null
}

function Resolve-CallModel {
    param([string[]]$FileLines, [int]$Index, [string]$CallPattern, [string]$ModuleName, [int]$Depth = 0)

    $names = [System.Collections.Generic.List[string]]::new()
    $last = [Math]::Min($FileLines.Count - 1, $Index + 6)
    $window = ($FileLines[$Index..$last]) -join ' '

    foreach ($match in [regex]::Matches($window, '([A-Za-z_]\w*)::class\.java')) {
        $names.Add($match.Groups[1].Value)
    }
    foreach ($match in [regex]::Matches($window, 'TypeToken\s*<(.+?)>\s*\(\s*\)')) {
        Get-TypeName $match.Groups[1].Value | ForEach-Object { $names.Add($_) }
    }
    foreach ($match in [regex]::Matches($window, $CallPattern)) {
        $argumentText = Get-CallArgument -Text $window -Start ($match.Index + $match.Length)
        foreach ($argument in ($argumentText -split ',')) {
            if ($argument.Trim() -notmatch '^(?:\w+\s*=\s*)?([A-Za-z_]\w*)') { continue }
            $token = $Matches[1]
            # A model that serializes itself names its type by its own declaration site.
            if ($token -eq 'this') {
                $self = Get-EnclosingClass -FileLines $FileLines -Index $Index
                if ($self) { $names.Add($self) }
                continue
            }
            # A model constructed inside the call - `toJson(Payload(items))` - names its type at the call
            # site, so there is no identifier anywhere to look up. Falling through to the declaration search
            # reported a fully annotated model as unresolvable, which is the same false red the receiver
            # case produced (S2325). Restricted to a declared model of this module: the constructor form is
            # the only shape where a bare token is the type itself rather than a value.
            if ($argument.Trim() -match "^(?:\w+\s*=\s*)?$token\s*\(" -and (Test-ModelName -ModuleName $ModuleName -Name $token)) {
                $names.Add($token)
                continue
            }
            $declared = Resolve-Identifier -FileLines $FileLines -Name $token
            if (-not $declared) { continue }
            $candidates = @(Get-TypeName $declared)
            $models = @($candidates | Where-Object { Test-ModelName -ModuleName $ModuleName -Name $_ })
            if ($models.Count -gt 0) {
                $models | ForEach-Object { $names.Add($_) }
                continue
            }
            # The value reached serialization through a generic helper, so the type lives at the call sites
            # of that helper rather than at the serialization line itself.
            if (-not ($candidates | Where-Object { $_ -match '^[A-Z]$' })) { continue }
            if ($Depth -ge 1) { continue }
            $enclosing = Get-EnclosingFunction -FileLines $FileLines -Index $Index
            if (-not $enclosing) { continue }
            for ($j = 0; $j -lt $FileLines.Count; $j++) {
                if ($FileLines[$j] -notmatch "(?<![\w.])$enclosing\s*\(") { continue }
                if ($FileLines[$j] -match "fun\s+(?:<[^>]*>\s*)?$enclosing\s*\(") { continue }
                Resolve-CallModel -FileLines $FileLines -Index $j -CallPattern "(?<![\w.])$enclosing\s*\(" -ModuleName $ModuleName -Depth 1 |
                    ForEach-Object { $names.Add($_) }
            }
        }
    }
    return $names
}

function Get-DeclarationBlock {
    param([object]$Declaration)

    $lines = [System.IO.File]::ReadAllLines((Join-Path $repoRoot $Declaration.file))
    $start = $Declaration.line - 1
    $block = [System.Collections.Generic.List[string]]::new()
    $paren = 0
    $brace = 0
    $opened = $false

    for ($j = $start; $j -lt [Math]::Min($lines.Count, $start + 300); $j++) {
        $text = $lines[$j]
        if ($j -gt $start -and $text -match $ClassDecl) { break }
        $block.Add($text)
        $paren += ([regex]::Matches($text, '\(')).Count - ([regex]::Matches($text, '\)')).Count
        $brace += ([regex]::Matches($text, '\{')).Count - ([regex]::Matches($text, '\}')).Count
        if ($j -gt $start -or $text -match '[({]') { $opened = $true }
        if ($opened -and $paren -le 0 -and $brace -le 0) { break }
    }
    return $block
}

function Get-EnumConstant {
    param([object]$Declaration)

    $constants = [System.Collections.Generic.List[object]]::new()
    $text = (Get-DeclarationBlock -Declaration $Declaration) -join "`n"
    # Doc prose routinely contains ';' and capitalised words, and both read as enum syntax - one KDoc
    # sentence ending in a semicolon would otherwise truncate the constant list at the entry before it.
    $text = [regex]::Replace($text, '(?s)/\*.*?\*/', ' ')
    $text = [regex]::Replace($text, '//[^\n]*', ' ')

    $open = $text.IndexOf('{')
    if ($open -lt 0) { return $constants }
    $body = $text.Substring($open + 1)
    # In an enum that carries a body, the constant list ends at the first ';'.
    $stop = $body.IndexOf(';')
    if ($stop -ge 0) { $body = $body.Substring(0, $stop) }

    foreach ($match in [regex]::Matches($body, '(?:^|,)\s*((?:@[\w.]+(?:\([^)]*\))?\s*)*)([A-Z][A-Za-z0-9_]*)\s*(?=[,(}]|$)')) {
        $constants.Add([pscustomobject]@{
                name      = $match.Groups[2].Value
                annotated = $match.Groups[1].Value -match '@SerializedName'
            })
    }
    return $constants
}

function Get-ModelProperty {
    param([object]$Declaration)

    $properties = [System.Collections.Generic.List[object]]::new()
    # An annotation may sit on its own line above the property it belongs to, which is how the wider
    # models in this tree are written. Carrying it forward is what separates "unannotated" from "annotated
    # on the previous line" - reading only the property's own line reports a pinned model as broken.
    $pending = ''
    foreach ($text in (Get-DeclarationBlock -Declaration $Declaration)) {
        # A compact declaration puts several properties on one line, so every match on the line counts, and
        # each match carries its own annotations - reading them from the whole line would credit one
        # property's annotation to its neighbour.
        $found = @([regex]::Matches($text, $PropDecl))
        if ($found.Count -eq 0) {
            if ($text -match '^\s*@[\w.]+') { $pending += $text }
            continue
        }
        foreach ($match in $found) {
            $carried = "$pending$($match.Value)"
            $pending = ''
            # Gson skips a transient field entirely, so its name never reaches the JSON.
            if ($carried -match '@Transient') { continue }
            $properties.Add([pscustomobject]@{
                    name      = $match.Groups[1].Value
                    type      = Get-TypeExpression -Text $text -Start ($match.Index + $match.Length)
                    annotated = $carried -match '@SerializedName'
                })
        }
        $pending = ''
    }
    return $properties
}

# The registry is read before the scan so a malformed line refuses in milliseconds instead of after a
# full tree walk, and so no run can report green off a file it failed to parse.
$exemptionPath = Join-Path $PSScriptRoot 'gson-persistence-exemptions-baseline.txt'
$exemptions = [ordered]@{}
if (Test-Path -LiteralPath $exemptionPath) {
    $lineNumber = 0
    foreach ($line in (Get-Content -LiteralPath $exemptionPath)) {
        $lineNumber++
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        if ($trimmed -notmatch '^(\S+)\s*::\s*(\S.*)$') {
            $msg = "assert-gson-persistence-contract: exemption line ${lineNumber} carries no justification - '$trimmed'."
            Write-Error $msg -ErrorAction Continue
            exit 2
        }
        $exemptions[$Matches[1]] = $Matches[2]
    }
}

$modules = if ($Module -eq 'all') { @('app_v2', 'wear') } else { @($Module) }
$changedSet = @()
if ($ChangedFiles) {
    $changedSet = @($ChangedFiles -split ',' | ForEach-Object { $_.Trim().Replace('\', '/') } | Where-Object { $_ })
}

$declarations = @{}
$gsonFiles = [System.Collections.Generic.List[object]]::new()
$rootsScanned = 0

foreach ($moduleName in $modules) {
    foreach ($root in (Get-SourceRoot -ModuleName $moduleName)) {
        $rootsScanned++
        Get-ChildItem -LiteralPath $root -Recurse -Filter '*.kt' -File | ForEach-Object {
            $relative = $_.FullName.Substring($repoRoot.Length + 1).Replace('\', '/')
            # ReadAllLines rather than Get-Content: the scan opens every Kotlin file in both trees, and
            # the cmdlet's per-line object pipeline is the difference between a gate the fast batch can
            # carry and one it cannot.
            $fileLines = [System.IO.File]::ReadAllLines($_.FullName)
            $package = ''
            foreach ($line in $fileLines) {
                if ($line -match '^\s*package\s+([\w.]+)') { $package = $Matches[1]; break }
            }
            for ($i = 0; $i -lt $fileLines.Count; $i++) {
                # A literal prefilter before the declaration regex. The scan reads every line of both
                # source trees, and the regex is the run's dominant cost; the keyword is present on well
                # under one line in fifty.
                $line = $fileLines[$i]
                if ($line -notlike '*class *' -and $line -notlike '*object *' -and $line -notlike '*interface *') { continue }
                $match = [regex]::Match($line, $ClassDecl)
                if (-not $match.Success) { continue }
                $name = $match.Groups[2].Value
                $key = "${moduleName}::${name}"
                if ($declarations.ContainsKey($key)) { continue }
                $declarations[$key] = [pscustomobject]@{
                    name   = $name
                    fqn    = if ($package) { "$package.$name" } else { $name }
                    kind   = $match.Groups[1].Value
                    module = $moduleName
                    file   = $relative
                    line   = $i + 1
                }
            }
            if (($fileLines -join "`n") -notmatch $GsonCall) { return }
            if ($changedSet.Count -gt 0 -and $changedSet -notcontains $relative) { return }
            $gsonFiles.Add([pscustomobject]@{ module = $moduleName; relative = $relative; lines = $fileLines })
        }
    }
}

if ($rootsScanned -eq 0) {
    $msg = "assert-gson-persistence-contract: no source root readable for module(s) $($modules -join ', ')."
    Write-Error $msg -ErrorAction Continue
    exit 2
}

$points = [System.Collections.Generic.List[object]]::new()
foreach ($file in $gsonFiles) {
    $verdict = Get-SinkVerdict -FileText ($file.lines -join "`n")
    for ($i = 0; $i -lt $file.lines.Count; $i++) {
        if ($file.lines[$i] -notmatch $GsonCall) { continue }
        $resolved = @(Resolve-CallModel -FileLines $file.lines -Index $i -CallPattern $GsonArgs -ModuleName $file.module |
                Sort-Object -Unique |
                Where-Object { Test-ModelName -ModuleName $file.module -Name $_ })
        $points.Add([pscustomobject]@{
                module   = $file.module
                file     = $file.relative
                line     = $i + 1
                models   = $resolved
                resolved = $resolved.Count -gt 0
                durable  = $verdict.Durable
                sink     = $verdict.Sink
            })
    }
}

# A durable model drags in every project type it holds: those names travel inside the same JSON blob.
$durableModels = @{}
$queue = [System.Collections.Generic.Queue[object]]::new()
foreach ($point in ($points | Where-Object { $_.durable })) {
    foreach ($name in $point.models) {
        $queue.Enqueue([pscustomobject]@{ module = $point.module; name = $name; sink = $point.sink; via = "$($point.file):$($point.line)" })
    }
}
while ($queue.Count -gt 0) {
    $item = $queue.Dequeue()
    $key = "$($item.module)::$($item.name)"
    if ($durableModels.ContainsKey($key)) {
        $durableModels[$key].reached += $item.via
        continue
    }
    $declaration = Get-Declaration -ModuleName $item.module -Name $item.name
    $properties = @(Get-ModelProperty -Declaration $declaration)
    $durableModels[$key] = [pscustomobject]@{
        declaration = $declaration
        sink        = $item.sink
        reached     = @($item.via)
        properties  = $properties
    }
    foreach ($property in $properties) {
        foreach ($name in (Get-TypeName $property.type)) {
            if (-not (Test-ModelName -ModuleName $item.module -Name $name)) { continue }
            if ($durableModels.ContainsKey("$($item.module)::$name")) { continue }
            $queue.Enqueue([pscustomobject]@{ module = $item.module; name = $name; sink = $item.sink; via = "$($item.name).$($property.name)" })
        }
    }
}

$keepRules = @{}
foreach ($moduleName in $modules) {
    $keepRules[$moduleName] = @(Get-KeepRule -ModuleName $moduleName)
}

function Find-KeepRule {
    param([object]$Declaration)

    foreach ($rule in $keepRules[$Declaration.module]) {
        if ($Declaration.fqn -match $rule.pattern) { return $rule }
    }
    return $null
}

$verdicts = [System.Collections.Generic.List[object]]::new()
foreach ($name in ($durableModels.Keys | Sort-Object)) {
    $model = $durableModels[$name]
    $unannotated = @($model.properties | Where-Object { -not $_.annotated } | ForEach-Object { $_.name })
    $total = @($model.properties).Count
    # Strategic S1639 ADR-2 accepts either form of pinning, so each module is judged against its own rules:
    # the phone annotates its contract models where the watch keeps the whole package by name.
    $keep = Find-KeepRule -Declaration $model.declaration
    if ($model.declaration.kind -eq 'enum class') {
        # Gson writes an enum as its constant name, never as its properties, so annotation coverage says
        # nothing here. The constants are judged by their own check.
        $outcome = 'enum'
        $kind = ''
    } elseif ($total -eq 0) {
        $outcome = 'no-properties'
        $kind = ''
    } elseif ($unannotated.Count -eq 0) {
        $outcome = 'annotated-all'
        $kind = ''
    } elseif ($keep) {
        # Field names kept by rule are stable whatever the annotations say, so a keep rule settles the
        # partial case too rather than leaving a model reported as broken while it is not.
        $outcome = 'keep-rule'
        $kind = ''
    } elseif ($unannotated.Count -lt $total) {
        $outcome = 'annotated-partial'
        $kind = 'partial-annotation'
    } else {
        $outcome = 'annotated-none'
        $kind = 'no-annotation'
    }

    # An enum reached by a durable model is judged separately from that model's own pinning: Gson takes the
    # written value from the constant, so a model that is fully annotated or fully kept still ships a
    # renameable name (measured while closing S1638).
    $enumTargets = [System.Collections.Generic.List[object]]::new()
    # An enum reached only as another model's property is already reported against that property; claiming
    # it is serialized on its own would double-count the same constant list under a wrong name.
    $reachedDirectly = @($model.reached | Where-Object { $_ -match ':\d+$' }).Count -gt 0
    if ($model.declaration.kind -eq 'enum class' -and $reachedDirectly) {
        $enumTargets.Add([pscustomobject]@{ property = '<serialized directly>'; declaration = $model.declaration })
    }
    foreach ($property in $model.properties) {
        foreach ($typeName in (Get-TypeName $property.type)) {
            $enumDeclaration = Get-Declaration -ModuleName $model.declaration.module -Name $typeName
            if ($null -eq $enumDeclaration -or $enumDeclaration.kind -ne 'enum class') { continue }
            $enumTargets.Add([pscustomobject]@{ property = $property.name; declaration = $enumDeclaration })
        }
    }
    $enumIssues = [System.Collections.Generic.List[object]]::new()
    foreach ($target in $enumTargets) {
        if (Find-KeepRule -Declaration $target.declaration) { continue }
        $constants = @(Get-EnumConstant -Declaration $target.declaration)
        if ($constants.Count -eq 0) { continue }
        $bare = @($constants | Where-Object { -not $_.annotated } | ForEach-Object { $_.name })
        if ($bare.Count -eq 0) { continue }
        $enumIssues.Add([pscustomobject]@{
                kind      = 'enum-constants-unpinned'
                property  = $target.property
                enum      = $target.declaration.fqn
                constants = $bare
                message   = $EnumConstantMessage
            })
    }

    $verdicts.Add([pscustomobject]@{
            model       = $model.declaration.name
            fqn         = $model.declaration.fqn
            declaredIn  = $model.declaration.module
            kindOfType  = $model.declaration.kind
            sink        = $model.sink
            reachedFrom = @($model.reached | Sort-Object -Unique)
            properties  = $total
            unannotated = $unannotated
            outcome     = $outcome
            violation   = $kind
            keepRule    = if ($keep) { "$($keep.file):$($keep.line) $($keep.text)" } else { '' }
            enumIssues  = @($enumIssues)
        })
}

$usedKeys = @{}
$suppressedCount = 0
$trackedCount = 0

$violations = @($verdicts | Where-Object { $_.violation -and -not $exemptions.Contains($_.fqn) })
$enumOffenders = @($verdicts | Where-Object { @($_.enumIssues | Where-Object { -not $exemptions.Contains($_.enum) }).Count -gt 0 })
$unresolvedPoints = @($points | Where-Object { $_.durable -and -not $_.resolved -and -not $exemptions.Contains($_.file) })

foreach ($verdict in $verdicts) {
    $keys = [System.Collections.Generic.List[string]]::new()
    if ($verdict.violation) { $keys.Add($verdict.fqn) }
    foreach ($issue in $verdict.enumIssues) { $keys.Add($issue.enum) }
    foreach ($key in ($keys | Sort-Object -Unique)) {
        if (-not $exemptions.Contains($key)) { continue }
        $usedKeys[$key] = $true
        $suppressedCount++
        # A justification that opens with an owning ticket records a live defect instead of excusing it,
        # so a green run has to say how many of those it is still carrying.
        if ($exemptions[$key] -match '^Ticket: S\d{4}') { $trackedCount++ }
    }
}
foreach ($point in ($points | Where-Object { $_.durable -and -not $_.resolved })) {
    if (-not $exemptions.Contains($point.file)) { continue }
    if (-not $usedKeys.ContainsKey($point.file)) {
        $usedKeys[$point.file] = $true
        $suppressedCount++
        if ($exemptions[$point.file] -match '^Ticket: S\d{4}') { $trackedCount++ }
    }
}

# Staleness is only knowable from a full run: a scoped or single-module run legitimately never reaches
# most of the registry, and calling those entries stale would train the reader to ignore the report.
$stale = @()
if ($Module -eq 'all' -and -not $ChangedFiles) {
    $stale = @($exemptions.Keys | Where-Object { -not $usedKeys.ContainsKey($_) })
}

$failed = $violations.Count -gt 0 -or $enumOffenders.Count -gt 0 -or $unresolvedPoints.Count -gt 0

if ($Format -eq 'json') {
    [pscustomobject]@{
        rootsScanned = $rootsScanned
        points       = $points
        models       = $verdicts
        unresolved   = $unresolvedPoints
        suppressed   = $suppressedCount
        tracked      = $trackedCount
        stale        = $stale
    } | ConvertTo-Json -Depth 6
    if ($failed) { exit 1 }
    exit 0
}

$durable = @($points | Where-Object { $_.durable })
$transient = @($points | Where-Object { -not $_.durable })

# In a batch the full inventory is noise around the one line the reader needs, so gate mode prints only
# what the registry does not already cover.
$terse = $Gate -or $Quiet
if (-not $terse) {
    Write-Host "assert-gson-persistence-contract: $($points.Count) serialization point(s) across $rootsScanned source root(s)."
    Write-Host "  durable: $($durable.Count) | transient: $($transient.Count) | durable model(s): $($verdicts.Count)"
}

$listed = if ($terse) { @(@($violations) + @($enumOffenders) | Sort-Object fqn -Unique) } else { $verdicts }
foreach ($verdict in $listed) {
    $suffix = ''
    if ($verdict.outcome -eq 'keep-rule') {
        $suffix = " - kept by $($verdict.keepRule)"
    } elseif ($verdict.unannotated.Count -gt 0) {
        # A settings model runs to a hundred properties; the full list belongs to the json report.
        $shown = @($verdict.unannotated | Select-Object -First 5) -join ', '
        $rest = if ($verdict.unannotated.Count -gt 5) { " (+$($verdict.unannotated.Count - 5) more)" } else { '' }
        $suffix = " - unannotated: $shown$rest"
    }
    $exempt = if ($verdict.violation -and $exemptions.Contains($verdict.fqn)) { ' [exempt]' } else { '' }
    Write-Host "  $($verdict.outcome.PadRight(18)) $($verdict.fqn) via $($verdict.sink)$suffix$exempt"
    foreach ($issue in $verdict.enumIssues) {
        $issueExempt = if ($exemptions.Contains($issue.enum)) { ' [exempt]' } else { '' }
        Write-Host "  $('enum-constants'.PadRight(18)) $($verdict.model).$($issue.property) -> $($issue.enum): $($issue.constants -join ', ')$issueExempt"
    }
}
foreach ($point in $unresolvedPoints) {
    Write-Host "  unresolved-type    $($point.file):$($point.line) via $($point.sink)"
}
foreach ($key in $stale) {
    Write-Host "  stale-exemption    $key - nothing violates any more, remove the registry line"
}

$carried = "suppressed by registry: $suppressedCount ($trackedCount tracked defect(s))"
if ($failed) {
    if ($enumOffenders.Count -gt 0) { Write-Host "  note: $EnumConstantMessage" }
    $msg = "assert-gson-persistence-contract: $($violations.Count) unpinned model(s), $($enumOffenders.Count) model(s) with unpinned enum constants, $($unresolvedPoints.Count) point(s) with an unresolvable type; $carried."
    Write-Error $msg -ErrorAction Continue
    exit 1
}

Write-Host "assert-gson-persistence-contract: every durable model pins its wire names; $carried."
exit 0
