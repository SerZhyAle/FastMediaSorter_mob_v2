$modulePath = Join-Path $PSScriptRoot '..\streams\modules\StreamPublisher.Probes.ps1'
$ua = 'FastMediaSorter-test/1.0'
$Throttle = 4
$LivenessTimeoutSec = 1
$SignalTimeoutSec = 1
$SignalBytes = 4096
$SignalMinBytes = 512
$SkipCaptureFirst = $true
$PreviewFrameDir = $TestDrive
$PreviewCaptureTimeoutSec = 1

function Get-RegistrableDomain {
    param([string]$hostName)
    if ($hostName -match '^\d{1,3}(\.\d{1,3}){3}$') { return $null }
    $labels = $hostName.Split('.')
    if ($labels.Count -lt 2) { return $null }
    return ($labels[-2..-1] -join '.')
}

. $modulePath

Describe 'StreamPublisher.Probes' {
    It 'refuses unknown statuses for pruning' {
        $threw = $false
        try { Assert-PrunableStatuses -Statuses @('dead', 'unknown') } catch { $threw = $true }
        $threw | Should Be $true
        { Assert-PrunableStatuses -Statuses @('dead', 'geo') } | Should Not Throw
    }

    It 'uses registrable domain keys and host fallback' {
        (Get-ProviderKey -Url 'https://a.example.test/live') | Should Be 'example.test'
        (Get-ProviderKey -Url 'https://127.0.0.1/live') | Should Be '127.0.0.1'
        (Get-ProviderKey -Url 'not a url') | Should Be '<unparsable>'
    }

    It 'interleaves rows by provider without changing row identity' {
        $rows = @(
            [pscustomobject]@{ url = 'https://a.example.test/1'; name = 'one' },
            [pscustomobject]@{ url = 'https://a.example.test/2'; name = 'two' },
            [pscustomobject]@{ url = 'https://b.other.test/1'; name = 'three' },
            [pscustomobject]@{ url = 'https://c.third.test/1'; name = 'four' }
        )
        $ordered = @(Get-ProviderInterleavedRows -Rows $rows)
        $ordered.Count | Should Be 4
        @($ordered.name | Sort-Object) -join ',' | Should Be 'four,one,three,two'
    }

    It 'reports provider loss only above count and share thresholds' {
        $all = @(
            'https://a.example.test/1', 'https://a.example.test/2', 'https://a.example.test/3',
            'https://b.other.test/1', 'https://b.other.test/2'
        )
        $pruned = @('https://a.example.test/1', 'https://a.example.test/2')
        @(Get-ProviderLossOffenders -AllUrls $all -PrunedUrls $pruned -MinShare 0.5 -MinCount 2).Count | Should Be 1
        @(Get-ProviderLossOffenders -AllUrls $all -PrunedUrls $pruned -MinShare 0.9 -MinCount 2).Count | Should Be 0
    }
}
