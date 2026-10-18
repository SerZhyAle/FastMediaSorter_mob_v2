#requires -Version 7.0
# CHECK-VERDICT 0.13 counts tests, while check aggregators retain rule 14's grammar.
function Get-TestCountScope {
    param([System.Management.Automation.Language.Ast]$Node)
    while ($null -ne $Node -and $Node -isnot [System.Management.Automation.Language.ScriptBlockAst]) {
        $Node = $Node.Parent
    }
    return $Node
}

function Get-LegacyTestCountWriter {
    [CmdletBinding()]
    param([string[]]$Files, [string]$RepoRoot)

    $counter = '(?:\{\d+(?:[^}]*)\}|\$[\w:.]+|\$\([^\r\n]*?\)|\d+)'
    $legacy = "(?i)(?:$counter\s+(?:tests?\s+)?(?:passed|failed|skipped|tests\b)|\b(?:passed|failed|skipped|tests|total|run)\s*[:=]\s*$counter)"
    foreach ($file in $Files) {
        $tokens = $null
        $parseErrors = $null
        $ast = [System.Management.Automation.Language.Parser]::ParseFile($file, [ref]$tokens, [ref]$parseErrors)
        # A partial AST still exposes output statements; rule A retains its documented line fallback.
        $relative = [IO.Path]::GetRelativePath($RepoRoot, $file).Replace('\', '/')
        if ($relative -match '(?i)/(?:stub|fixtures?)/') { continue }
        $suite = $relative -match '(?i)(?:\.tests/|(?:^|/)[^/]*tests?\.ps1$)'
        $commands = $ast.FindAll({ param($node)
            $node -is [System.Management.Automation.Language.CommandAst] -and
                $node.GetCommandName() -in @('Write-Host', 'Write-Output', 'Write-Information')
        }, $true)
        $assignments = @($ast.FindAll({ param($node)
            $node -is [System.Management.Automation.Language.AssignmentStatementAst]
        }, $true))
        foreach ($command in $commands) {
            $scope = Get-TestCountScope -Node $command
            # Fixture bodies are string values, never output commands in this AST.
            $strings = @($command.FindAll({ param($node)
                $node -is [System.Management.Automation.Language.StringConstantExpressionAst] -or
                    $node -is [System.Management.Automation.Language.ExpandableStringExpressionAst]
            }, $false))
            $resolved = @()
            $references = @($command.FindAll({ param($node)
                $node -is [System.Management.Automation.Language.VariableExpressionAst]
            }, $false))
            foreach ($reference in $references) {
                $assignment = $assignments | Where-Object {
                    $_.Left -is [System.Management.Automation.Language.VariableExpressionAst] -and
                    $_.Left.VariablePath.UserPath -eq $reference.VariablePath.UserPath -and
                    $_.Extent.EndOffset -le $command.Extent.StartOffset -and
                    [object]::ReferenceEquals((Get-TestCountScope -Node $_), $scope)
                } | Sort-Object { $_.Extent.EndOffset } -Descending | Select-Object -First 1
                if ($null -eq $assignment) { continue }
                $assignedStrings = @($assignment.Right.FindAll({ param($node)
                    $node -is [System.Management.Automation.Language.StringConstantExpressionAst] -or
                        $node -is [System.Management.Automation.Language.ExpandableStringExpressionAst]
                }, $false))
                $assignedCounts = @($assignedStrings | Where-Object {
                    $_.StringConstantType -notmatch 'HereString' -and $_.Value -match $legacy
                })
                if ($assignedCounts.Count -eq 0) { continue }
                $strings += $assignedCounts
                $resolved += ($assignment.Extent.Text -replace '\s+', ' ').Trim()
            }
            foreach ($literal in $strings) {
                $value = $literal.Value
                if ($value -notmatch $legacy -and $value -notmatch '(?i)\b(?:run|skipped)=') { continue }
                if (-not $suite -and $value -notmatch ':') { continue }
                if ($value -match '(?i)^Unit tests\s*:|\bfile\(s\)\s*\||\bchild checks?\b') { continue }
                if ($value -match '(?i)\bprobe failed:|\b(?:test tree|test set)\(') { continue }
                if (-not $suite -and $value -notmatch '(?i)\btests\b.*\b(?:PASS|FAIL|passed|failed)\b|\b(?:PASS|FAIL)\b.*\btests\b') { continue }
                if ($value -match '(?i)\b(?:expected|assertion)\b') { continue }
                if ($value -match "(?-i)\brun=$counter skipped=$counter(?:[\s),;]|$)") { continue }
                $text = ($command.Extent.Text -replace '\s+', ' ').Trim()
                [pscustomobject]@{
                    File = $relative
                    Line = $command.Extent.StartLineNumber
                    Text = $text
                    Key = "$relative | $text" + $(if ($resolved.Count) { ' <= ' + (($resolved | Sort-Object -Unique) -join ' ; ') })
                }
                break
            }
        }
    }
}

function Get-TestCountResidue {
    [CmdletBinding()]
    param([object[]]$Writers, [string[]]$Baseline)

    $allowed = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($entry in $Baseline) {
        if ($entry.Trim() -and -not $entry.Trim().StartsWith('#')) { [void]$allowed.Add($entry) }
    }
    $present = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($writer in $Writers) { [void]$present.Add($writer.Key) }
    [pscustomobject]@{
        New = @($Writers | Where-Object { -not $allowed.Contains($_.Key) })
        Stale = @($allowed | Where-Object { -not $present.Contains($_) } | Sort-Object)
        Count = $allowed.Count
    }
}
