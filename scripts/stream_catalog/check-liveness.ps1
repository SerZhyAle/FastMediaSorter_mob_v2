#requires -Version 7.0
<#
.SYNOPSIS
    Liveness probe for the curated stream catalog (delivery/stream-catalog/streams.csv).

.DESCRIPTION
    Reads the catalog CSV and probes each stream URL to classify it as alive / dead / unknown.
    This is the maintenance "mechanism" for the catalog: run it periodically to find dead
    entries to drop and to confirm new ones before publishing.

    Probe strategy (http/https):
      - Uses .NET HttpClient with HttpCompletionOption.ResponseHeadersRead so the (often
        endless) Icecast/Shoutcast body is NEVER downloaded - only the response headers are read.
        This is critical: a naive GET that reads the body hangs forever on a live radio stream.
      - HEAD first, falling back to GET (HEAD is frequently rejected by media/relay servers).
      - 2xx/3xx => alive. A bare "ICY 200 OK" non-HTTP status line surfaces as a client
        exception and is treated as alive. 404/410 / DNS failure / connection refused => dead.
        401/403/429 / 5xx / timeouts / other 4xx => unknown (auth/geo/rate/transient, NOT proof
        of death - kept conservatively).
      - rtsp:// : a TCP connect probe to host:port (default 554). Connect => alive; refused =>
        dead; timeout => unknown (RTSP liveness over TCP is weak, never auto-dead on timeout).

    Probes run from THIS machine, so geo-restricted streams may read dead/unknown here yet work
    elsewhere. Treat "dead" conservatively before deleting catalog rows.

.EXAMPLE
    pwsh -NoProfile -File scripts/stream_catalog/check-liveness.ps1
    pwsh -NoProfile -File scripts/stream_catalog/check-liveness.ps1 -TimeoutSec 10 -Throttle 32
#>
[CmdletBinding()]
param(
    [string] $CsvPath = "delivery/stream-catalog/streams.csv",
    [string] $OutReport = "temp/stream-catalog-liveness.csv",
    [int]    $TimeoutSec = 7,
    [int]    $Throttle = 24
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $CsvPath)) { throw "Catalog CSV not found: $CsvPath" }
$rows = Import-Csv -Path $CsvPath
if (-not $rows -or $rows.Count -eq 0) { throw "No rows in $CsvPath" }
Write-Host "Probing $($rows.Count) streams (timeout=${TimeoutSec}s, throttle=$Throttle).." -ForegroundColor Cyan

$ua = "FastMediaSorter/2.60 (Android) catalog-liveness"

$results = $rows | ForEach-Object -ThrottleLimit $Throttle -Parallel {
    $r = $_
    $url = [string]$r.url
    $timeout = $using:TimeoutSec
    $ua = $using:ua
    $status = 'unknown'; $code = ''; $note = ''

    if ($url -like 'rtsp://*') {
        try {
            $u = [Uri]$url
            $port = if ($u.Port -gt 0) { $u.Port } else { 554 }
            $tcp = [System.Net.Sockets.TcpClient]::new()
            $iar = $tcp.BeginConnect($u.Host, $port, $null, $null)
            if ($iar.AsyncWaitHandle.WaitOne([TimeSpan]::FromSeconds($timeout))) {
                $tcp.EndConnect($iar); $status = 'alive'; $note = 'rtsp tcp-connect'
            } else {
                $status = 'unknown'; $note = 'rtsp tcp-timeout'
            }
            $tcp.Close()
        } catch {
            $m = $_.Exception.Message
            if ($m -match 'refused|actively refused') { $status = 'dead'; $note = 'rtsp conn-refused' }
            else { $status = 'unknown'; $note = "rtsp $m" }
        }
    } else {
        $handler = [System.Net.Http.HttpClientHandler]::new()
        $handler.AllowAutoRedirect = $true
        $handler.MaxAutomaticRedirections = 6
        try { $handler.AutomaticDecompression = [System.Net.DecompressionMethods]::All } catch {}
        $client = [System.Net.Http.HttpClient]::new($handler)
        $client.Timeout = [TimeSpan]::FromSeconds($timeout)
        $client.DefaultRequestHeaders.TryAddWithoutValidation('User-Agent', $ua) | Out-Null
        $client.DefaultRequestHeaders.TryAddWithoutValidation('Icy-MetaData', '1') | Out-Null
        $rhr = [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead

        $probe = {
            param($method)
            $req = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::$method, $url)
            $resp = $client.SendAsync($req, $rhr).GetAwaiter().GetResult()
            $c = [int]$resp.StatusCode
            $resp.Dispose()
            return $c
        }

        try {
            try { $code = & $probe 'Head' } catch { $code = & $probe 'Get' }
            if (-not ($code -ge 200 -and $code -lt 400)) {
                # HEAD often blocked by media servers -> confirm with GET (headers only)
                try { $g = & $probe 'Get'; if ($g -ge 200 -and $g -lt 400) { $code = $g } else { $code = $g } } catch { }
            }
            if ($code -ge 200 -and $code -lt 400) { $status = 'alive' }
            elseif ($code -in 404, 410) { $status = 'dead'; $note = "http $code" }
            else { $status = 'unknown'; $note = "http $code" }
        } catch {
            $m = $_.Exception.Message
            $inner = if ($_.Exception.InnerException) { $_.Exception.InnerException.Message } else { '' }
            $full = "$m $inner"
            if ($full -match 'ICY|status line|invalid response|unrecognized|ended prematurely') { $status = 'alive'; $note = 'icy/non-http' }
            elseif ($full -match 'No such host|not known|name resolution|NameResolution|known host') { $status = 'dead'; $note = 'dns-fail' }
            elseif ($full -match 'refused|actively refused') { $status = 'dead'; $note = 'conn-refused' }
            elseif ($full -match 'canceled|cancelled|timed out|timeout|task was canceled') { $status = 'unknown'; $note = 'timeout' }
            else { $status = 'unknown'; $note = ($full.Trim() -replace '\s+', ' ') }
        } finally {
            $client.Dispose(); $handler.Dispose()
        }
    }

    [pscustomobject]@{
        status     = $status
        http       = $code
        media_kind = $r.media_kind
        category   = $r.category
        name       = $r.name
        url        = $url
        note       = $note
    }
}

$outDir = Split-Path -Parent $OutReport
if ($outDir -and -not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
$results | Sort-Object status, category, name | Export-Csv -Path $OutReport -NoTypeInformation -Encoding utf8

Write-Host "`n=== Liveness summary ===" -ForegroundColor Green
$results | Group-Object status | Sort-Object Name | ForEach-Object { "{0,-8} {1}" -f $_.Name, $_.Count }
Write-Host "`nDead (drop candidates):" -ForegroundColor Yellow
$results | Where-Object status -eq 'dead' | Sort-Object category, name | ForEach-Object { " - [{0}] {1}  ({2})  {3}" -f $_.category, $_.name, $_.note, $_.url }
Write-Host "`nReport written: $OutReport"
