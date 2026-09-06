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
        (Map-IptvTopic 'unknown-category') | Should Be 'General'
    }

    It 'normalizes each grouping facet while retaining unknown values for review' {
        (Get-CanonicalCategory 'Radio (SomaFM)') | Should Be 'Radio'
        (Get-CanonicalCategory 'Open movies') | Should Be 'On-demand video'
        (Get-CanonicalTopic 'Adult Contemporary') | Should Be 'Pop'
        (Get-CanonicalLanguages 'American English, Gernan') | Should Be 'english,german'
        (Get-CanonicalLanguages 'english german') | Should Be 'english,german'
        (Get-CanonicalCountry 'Germany') | Should Be 'DE'
        (Get-CanonicalCountry 'USA') | Should Be 'US'
        (Get-CanonicalCategory '') | Should Be ''
        (Get-CanonicalLanguages '') | Should Be ''
        (Get-CanonicalCountry '') | Should Be ''
        (Get-CanonicalCategory 'Future provider class') | Should Be 'Future provider class'
        (Get-CanonicalLanguages 'future language') | Should Be 'future language'
        (Get-CanonicalCountry 'Future country') | Should Be 'Future country'
    }

    It 'normalizes comma-separated prune statuses' {
        $statuses = @(Normalize-PruneStatuses @('dead, unknown', 'geo', ''))
        $statuses.Count | Should Be 3
        $statuses[0] | Should Be 'dead'
        $statuses[1] | Should Be 'unknown'
        $statuses[2] | Should Be 'geo'
    }

    It 'decodes HTML entities in a catalog name, including the double-encoded form' {
        (Repair-CatalogName 'Rust &amp; Roses Country') | Should Be 'Rust & Roses Country'
        (Repair-CatalogName '102 FM L&amp;#039;Originale') | Should Be "102 FM L'Originale"
        (Repair-CatalogName '&Rho;&Alpha;&Delta;&Iota;&Omicron; &Alpha;&Rho;&Gamma;&Omega;') |
            Should Be ([char]0x03A1 + [char]0x0391 + [char]0x0394 + [char]0x0399 + [char]0x039F + ' ' +
                       [char]0x0391 + [char]0x03A1 + [char]0x0393 + [char]0x03A9)
    }

    It 'strips the serialised encoder-slot prefix but keeps a real leading dash' {
        (Repair-CatalogName '- 0 N - Blues on Radio') | Should Be 'Blues on Radio'
        (Repair-CatalogName '- 0 N - Deutsch Rap on Radio') | Should Be 'Deutsch Rap on Radio'
        (Repair-CatalogName '- NEUERSCHEINUNGEN - Radio Charts') | Should Be '- NEUERSCHEINUNGEN - Radio Charts'
    }

    It 'leaves leading punctuation that belongs to the station name' {
        (Repair-CatalogName '.977 Country') | Should Be '.977 Country'
        (Repair-CatalogName '#joint radio Blues Rock') | Should Be '#joint radio Blues Rock'
        (Repair-CatalogName '_Funky Corner Radio (USA)') | Should Be '_Funky Corner Radio (USA)'
    }

    It 'collapses whitespace and trims trailing separators' {
        (Repair-CatalogName "  Jazz   Lounge  Radio  -  ") | Should Be 'Jazz Lounge Radio'
        (Repair-CatalogName 'Radio F.M.') | Should Be 'Radio F.M.'
        (Repair-CatalogName '') | Should Be ''
    }

    It 'recognises a name that tells the user nothing' {
        (Test-CatalogNameUninformative '(null)') | Should Be $true
        (Test-CatalogNameUninformative '-') | Should Be $true
        (Test-CatalogNameUninformative '   ') | Should Be $true
        (Test-CatalogNameUninformative 'Online Radio') | Should Be $true
        (Test-CatalogNameUninformative 'ONLINE  RADIO') | Should Be $true
        (Test-CatalogNameUninformative 'no name') | Should Be $true
        (Test-CatalogNameUninformative 'Orban Opticodec-PC Encoder') | Should Be $true
    }

    It 'keeps a real station name informative' {
        (Test-CatalogNameUninformative 'Blues on Radio') | Should Be $false
        (Test-CatalogNameUninformative '.977 Country') | Should Be $false
        (Test-CatalogNameUninformative 'Radio Paradise') | Should Be $false
    }

    It 'discards a name that only names the software or the server' {
        (Test-CatalogNameDiscardable 'Orban Opticodec-PC Encoder') | Should Be $true
        (Test-CatalogNameDiscardable 'MB STUDIO') | Should Be $true
        (Test-CatalogNameDiscardable 'RadioBOSS Stream') | Should Be $true
        (Test-CatalogNameDiscardable 'This is my server name') | Should Be $true
        (Test-CatalogNameDiscardable 'Unspecified name') | Should Be $true
        (Test-CatalogNameDiscardable '(null)') | Should Be $true
    }

    It 'keeps a name that describes the medium, because the bank mixes radio, TV and webcams' {
        (Test-CatalogNameDiscardable 'Online Radio') | Should Be $false
        (Test-CatalogNameDiscardable 'Radio') | Should Be $false
        (Test-CatalogNameDiscardable 'stream') | Should Be $false
        (Test-CatalogNameDiscardable 'Blues on Radio') | Should Be $false
    }

    It 'shapes the final name by what the old one was about' {
        (Resolve-CatalogName -Name 'Online Radio' -Url 'http://a.test:8000/s').Name |
            Should BeExactly 'Online Radio (a.test:8000)'
        (Resolve-CatalogName -Name 'Orban Opticodec-PC Encoder' -Url 'http://a.test:8000/s').Name |
            Should BeExactly 'a.test:8000'
        (Resolve-CatalogName -Name '(null)' -Url 'http://a.test:8000/s').Name |
            Should BeExactly 'a.test:8000'
        (Resolve-CatalogName -Name 'Radio Paradise' -Url 'http://a.test:8000/s').Name |
            Should BeExactly 'Radio Paradise'
        (Resolve-CatalogName -Name 'Rust &amp; Roses' -Url 'http://a.test:8000/s').Rule |
            Should BeExactly 'repair'
    }

    It 'leaves a nameless row alone when its url yields no token, so the publish gate can name it' {
        $r = Resolve-CatalogName -Name '(null)' -Url 'not a url'
        $r.Name | Should BeExactly '(null)'
        $r.Rule | Should BeExactly ''
    }

    It 'derives the distinguishing host token from a catalog url' {
        # The port stays: on a shared streaming host it is the only thing that tells two tenants apart.
        (Get-CatalogNameFromUrl 'http://quincy.torontocast.com:2150/stream') | Should BeExactly 'quincy.torontocast.com:2150'
        # BeExactly: Pester 3's Be is case-insensitive and would pass on a token that was never lowered.
        (Get-CatalogNameFromUrl 'https://Example.Test/live.m3u8') | Should BeExactly 'example.test'
        (Get-CatalogNameFromUrl 'not a url') | Should BeExactly ''
    }

    It 'drops a default port from the token but keeps every other one' {
        (Get-CatalogNameFromUrl 'http://example.test:80/live') | Should BeExactly 'example.test'
        (Get-CatalogNameFromUrl 'https://example.test:443/live') | Should BeExactly 'example.test'
        (Get-CatalogNameFromUrl 'rtsp://example.test:554/live') | Should BeExactly 'example.test'
        (Get-CatalogNameFromUrl 'http://example.test:8000/live') | Should BeExactly 'example.test:8000'
    }

    It 'tells two tenants of one shared streaming host apart' {
        $a = Get-CatalogNameFromUrl 'http://hoth.alonhosting.com:3410/stream'
        $b = Get-CatalogNameFromUrl 'http://hoth.alonhosting.com:3910/stream'
        $a | Should BeExactly 'hoth.alonhosting.com:3410'
        $a | Should Not BeExactly $b
    }
}
