$script:TestStats = @{
    pass = 0
    fail = 0
    failures = @()
}

function script:Register-Pass {
    $script:TestStats.pass++
}

function script:Register-Fail {
    param([Parameter(Mandatory)][string] $Name, [Parameter(Mandatory)][string] $Message)
    $script:TestStats.fail++
    $script:TestStats.failures += "[FAIL] $Name :: $Message"
}

function Describe-Test {
    param(
        [Parameter(Mandatory)][string] $Name,
        [Parameter(Mandatory)][scriptblock] $Body
    )
    Write-Output "[TEST] $Name"
    try {
        & $Body
        Register-Pass
    } catch {
        Register-Fail -Name $Name -Message $_.Exception.Message
        Write-Output ("[FAIL] {0}" -f $_.Exception.Message)
    }
}

function Assert-Equal {
    param(
        [Parameter(Mandatory)] $Expected,
        [Parameter(Mandatory)] $Actual,
        [Parameter(Mandatory)][string] $Message
    )
    if ($Expected -is [array] -or $Actual -is [array]) {
        $expectedJson = ($Expected | ConvertTo-Json -Compress -Depth 10)
        $actualJson = ($Actual | ConvertTo-Json -Compress -Depth 10)
        if ($expectedJson -ne $actualJson) {
            throw "$Message | expected: $expectedJson | actual: $actualJson"
        }
        return
    }
    if ($Expected -ne $Actual) {
        throw "$Message | expected: $Expected | actual: $Actual"
    }
}

function Assert-True {
    param(
        [Parameter(Mandatory)][bool] $Condition,
        [Parameter(Mandatory)][string] $Message
    )
    if (-not $Condition) {
        throw $Message
    }
}

function Assert-Match {
    param(
        [Parameter(Mandatory)][string] $Pattern,
        [Parameter(Mandatory)][AllowEmptyString()][string] $Text,
        [Parameter(Mandatory)][string] $Message
    )
    if ($Text -notmatch $Pattern) {
        throw "$Message | pattern: $Pattern | actual: $Text"
    }
}

function Assert-Throws {
    param(
        [Parameter(Mandatory)][scriptblock] $ScriptBlock,
        [Parameter(Mandatory)][string] $Message
    )
    try {
        & $ScriptBlock
    } catch {
        return $_.Exception
    }
    throw $Message
}
