$modulePath = Join-Path $PSScriptRoot '..\streams\modules\StreamPublisher.Common.ps1'
$Schema = @(
    'category', 'topic', 'name', 'url', 'media_kind', 'protocol', 'format', 'bitrate',
    'is_live', 'https', 'language', 'country', 'homepage', 'source_kind',
    'license_note', 'notes', 'confidence', 'favicon_index', 'access'
)
$ua = 'FastMediaSorter-test/1.0'
. $modulePath

Describe 'StreamPublisher.Common' {
    It 'preserves the published CSV schema order' {
        $Schema.Count | Should Be 19
        $Schema[0] | Should Be 'category'
        $Schema[17] | Should Be 'favicon_index'
        $Schema[18] | Should Be 'access'
    }

    It 'classifies URL formats without network access' {
        (Get-FormatFromUrl 'https://example.test/live.m3u8?token=1') | Should Be 'm3u8'
        (Get-FormatFromUrl 'https://example.test/live.mpd') | Should Be 'mpd'
        (Get-FormatFromUrl 'rtsp://example.test/live') | Should Be 'rtsp'
        (Get-FormatFromUrl 'https://example.test/live') | Should Be ''
    }

    It 'maps URL formats to the existing protocol contract' {
        (Get-ProtocolFromUrl 'rtsp://example.test/live' '') | Should Be 'RTSP'
        (Get-ProtocolFromUrl 'https://example.test/live.m3u8' 'm3u8') | Should Be 'HLS'
        (Get-ProtocolFromUrl 'https://example.test/live.mpd' 'mpd') | Should Be 'DASH'
        (Get-ProtocolFromUrl 'https://example.test/live' '') | Should Be 'ICECAST'
    }

    It 'folds unknown topics into the closed rubric set' {
        (Get-CanonicalTopic 'Adult Contemporary') | Should Be 'Pop'
        (Get-CanonicalTopic '') | Should Be 'General'
        (Map-IptvTopic 'sports') | Should Be 'Sports'
        (Map-IptvTopic 'unknown-category') | Should Be 'Unknown-Category'
    }

    It 'normalizes comma-separated prune statuses' {
        $statuses = @(Normalize-PruneStatuses @('dead, unknown', 'geo', ''))
        $statuses.Count | Should Be 3
        $statuses[0] | Should Be 'dead'
        $statuses[1] | Should Be 'unknown'
        $statuses[2] | Should Be 'geo'
    }
}
