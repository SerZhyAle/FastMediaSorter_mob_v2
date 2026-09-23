# Subject: scripts/quality/assert-wear-wire-vocabulary-parity.ps1
<#
.SYNOPSIS
    S2642: Regression suite for assert-wear-wire-vocabulary-parity.ps1.

.DESCRIPTION
    Tests the wire vocabulary parity gate against fixture pairs exercising all 3 comparison shapes,
    discovery of undeclared mirrored enums, and LocalOnly safety rules.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-wear-wire-vocabulary-parity.tests/Run-Tests.ps1
#>

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

$gateScript = Join-Path $repoRoot 'scripts/quality/assert-wear-wire-vocabulary-parity.ps1'
if (-not (Test-Path -LiteralPath $gateScript)) {
    Write-Error "assert-wear-wire-vocabulary-parity.tests: could not verify - missing $gateScript" -ErrorAction Continue
    exit 2
}

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

# Helper to build a minimal valid sandbox fixture pair
function New-FixtureSandbox {
    $sandbox = Join-Path ([System.IO.Path]::GetTempPath()) ("s2642-" + [Guid]::NewGuid().ToString('N'))
    $phoneDir = Join-Path $sandbox 'phone'
    $watchDir = Join-Path $sandbox 'watch'

    $phoneModel = Join-Path $phoneDir 'domain/model'
    $phoneSvc = Join-Path $phoneDir 'service'
    $phoneBroadcast = Join-Path $phoneDir 'data/broadcast'
    $watchModel = Join-Path $watchDir 'domain/model'
    $watchSvc = Join-Path $watchDir 'data/wear'
    $watchBroadcast = Join-Path $watchDir 'data/broadcast'
    $watchUseCase = Join-Path $watchDir 'domain/usecase'
    # S3160: the browse mediaType row's two sides live outside domain/model on both modules.
    $phoneUseCase = Join-Path $phoneDir 'domain/usecase'
    $watchBrowse = Join-Path $watchDir 'domain/browse'

    New-Item -ItemType Directory -Force -Path $phoneModel | Out-Null
    New-Item -ItemType Directory -Force -Path $phoneSvc | Out-Null
    New-Item -ItemType Directory -Force -Path $phoneBroadcast | Out-Null
    New-Item -ItemType Directory -Force -Path $watchModel | Out-Null
    New-Item -ItemType Directory -Force -Path $watchSvc | Out-Null
    New-Item -ItemType Directory -Force -Path $watchBroadcast | Out-Null
    New-Item -ItemType Directory -Force -Path $watchUseCase | Out-Null
    New-Item -ItemType Directory -Force -Path $phoneUseCase | Out-Null
    New-Item -ItemType Directory -Force -Path $watchBrowse | Out-Null

    @'
package com.sza.fastmediasorter.data.broadcast
class BroadcastDescriptorParser {
    companion object {
        const val COMPRESSED_PREFIX = "FMSBCAST1:"
    }
}
'@ | Set-Content (Join-Path $phoneBroadcast 'BroadcastDescriptorParser.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.wear.data.broadcast
class BroadcastDescriptorSerializer {
    companion object {
        const val COMPRESSED_PREFIX = "FMSBCAST1:"
    }
}
'@ | Set-Content (Join-Path $watchBroadcast 'BroadcastDescriptorSerializer.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.data.broadcast
data class BroadcastDescriptorDto(
    @SerializedName("url") val url: String
)
'@ | Set-Content (Join-Path $phoneBroadcast 'BroadcastDescriptorDto.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.wear.data.broadcast
data class BroadcastDescriptorDto(
    @SerializedName("url") val url: String
)
'@ | Set-Content (Join-Path $watchBroadcast 'BroadcastDescriptorDto.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.data.broadcast
data class BroadcastEndpointDto(
    @SerializedName("url") val url: String
)
'@ | Set-Content (Join-Path $phoneBroadcast 'BroadcastEndpointDto.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.wear.data.broadcast
data class BroadcastEndpointDto(
    @SerializedName("url") val url: String
)
'@ | Set-Content (Join-Path $watchBroadcast 'BroadcastEndpointDto.kt') -Encoding utf8NoBOM

    # Base valid contents
    @'
package com.sza.fastmediasorter.service
object WearDataLayerPaths {
    const val PATH_SHARED = "/fms/shared"
    const val EVENT_A = "event_a"
    const val EVENT_B = "event_b"
}
'@ | Set-Content (Join-Path $phoneSvc 'WearDataLayerPaths.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.wear.data.wear
object WearDataLayerPaths {
    const val PATH_SHARED = "/fms/shared"
    const val EVENT_A = "event_a"
    const val EVENT_B = "event_b"
}
'@ | Set-Content (Join-Path $watchSvc 'WearDataLayerPaths.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.domain.model
class WearStreamTransferPayload {
    class WearStreamTransferAck {
        companion object {
            const val OUTCOME_OK = "ok"
        }
    }
}
'@ | Set-Content (Join-Path $phoneModel 'WearStreamTransferPayload.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.wear.domain.model
class WearStreamTransferPayload {
    class WearStreamTransferAck {
        companion object {
            const val OUTCOME_OK = "ok"
        }
    }
}
'@ | Set-Content (Join-Path $watchModel 'WearStreamTransferPayload.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.domain.model
class WearFileTransfer {
    class WearFileTransferAck {
        companion object {
            const val OUTCOME_SUCCESS = "success"
            const val OUTCOME_TOO_LARGE = "too_large"
        }
    }
    class WearFileReceiveAck {
        companion object {
            const val OUTCOME_SAVED = "saved"
        }
    }
}
enum class WearFileReceiveOutcome { SAVED, REFUSED, FAILED }
'@ | Set-Content (Join-Path $phoneModel 'WearFileTransfer.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.wear.domain.model
class WearFileTransferMetadata {
    class WearFileTransferAck {
        companion object {
            const val OUTCOME_SUCCESS = "success"
            const val OUTCOME_TOO_LARGE = "too_large"
        }
    }
    class WearFileReceiveAck {
        companion object {
            const val OUTCOME_SAVED = "saved"
        }
    }
}
enum class WearFileReceiveOutcome { SAVED, FAILED }
'@ | Set-Content (Join-Path $watchModel 'WearFileTransferMetadata.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.domain.model
enum class WearPlaybackCommand { PLAY_PAUSE, NEXT, PREVIOUS }
'@ | Set-Content (Join-Path $phoneModel 'WearPlaybackCommand.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.wear.domain.model
enum class WearPlaybackCommand { PLAY_PAUSE, NEXT, PREVIOUS }
'@ | Set-Content (Join-Path $watchModel 'WearPlaybackCommand.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.domain.model
enum class WearOpenOnPhoneOutcome { SUCCESS, NOT_FOUND }
'@ | Set-Content (Join-Path $phoneModel 'WearOpenOnPhonePayload.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.wear.domain.model
enum class WearOpenOnPhoneOutcome { SUCCESS, NOT_FOUND }
'@ | Set-Content (Join-Path $watchModel 'WearOpenOnPhonePayload.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.domain.model
enum class WearCastOrigin { STREAM, NETWORK_SOURCE }
enum class WearCastMediaType { IMAGE, VIDEO, AUDIO }
enum class WearCastOutcome { CASTING, PICKER_NEEDED }
'@ | Set-Content (Join-Path $phoneModel 'WearCastPayload.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.wear.domain.model
enum class WearCastOrigin { STREAM, NETWORK_SOURCE }
enum class WearCastMediaType { IMAGE, VIDEO, AUDIO }
enum class WearCastOutcome { CASTING, PICKER_NEEDED }
'@ | Set-Content (Join-Path $watchModel 'WearCastPayload.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.domain.model
enum class WearPhoneResourceRequestKind { @SerializedName("AUDIO") AUDIO }
enum class WearPhoneResourceResponseStatus { @SerializedName("OK") OK }
enum class WearPhoneResourceDeleteOutcome { @SerializedName("DELETED") DELETED }
'@ | Set-Content (Join-Path $phoneModel 'WearPhoneResourcePayload.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.wear.domain.model
enum class WearPhoneResourceRequestKind { AUDIO }
enum class WearPhoneResourceResponseStatus { OK }
enum class WearPhoneResourceDeleteOutcome { DELETED }
'@ | Set-Content (Join-Path $watchModel 'WearPhoneResourcePayload.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.domain.model
enum class WearSyncLeg { PHONE_TO_WATCH, WATCH_TO_PHONE }
'@ | Set-Content (Join-Path $phoneModel 'WearSyncOutcome.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.wear.domain.model
enum class WearSyncLeg { PHONE_TO_WATCH, WATCH_TO_PHONE }
'@ | Set-Content (Join-Path $watchModel 'WearSyncOutcome.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.domain.model
enum class WearSettingOwnership { PHONE_ONLY, WATCH_ONLY }
'@ | Set-Content (Join-Path $phoneModel 'WearSettingsRegistry.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.wear.domain.model
enum class WearSettingOwnership { PHONE_ONLY, WATCH_ONLY }
'@ | Set-Content (Join-Path $watchModel 'WearSettingsRegistry.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.domain.model
enum class WearSettingsFieldIssue { UNKNOWN_KEY }
'@ | Set-Content (Join-Path $phoneModel 'WearSettingsDecodeResult.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.wear.domain.model
enum class WearSettingsFieldIssue { UNKNOWN_KEY }
'@ | Set-Content (Join-Path $watchModel 'WearSettingsDecodeResult.kt') -Encoding utf8NoBOM

    # S2641: the one pair whose sides are not same-named - a phone-side subset declaration against a
    # watch-side branch list. Both files must exist in every fixture, or the row reports "could not
    # check" (exit 2) and every case below reads as a failure of whatever it was actually testing.
    @'
package com.sza.fastmediasorter.domain.model
enum class ResourceType {
    LOCAL,
    SMB,
    SFTP,
    FTP,
    CLOUD;

    companion object {
        val WATCH_TRANSFERABLE: Set<ResourceType> = setOf(SMB, FTP, SFTP)
    }
}
'@ | Set-Content (Join-Path $phoneModel 'Models.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.wear.domain.usecase
class ImportNetworkSourcesUseCase {
    private fun parseType(raw: String): NetworkSourceType? = when (raw.uppercase()) {
        "SMB" -> NetworkSourceType.SMB
        "FTP" -> NetworkSourceType.FTP
        "SFTP" -> NetworkSourceType.SFTP
        else -> null
    }
}
'@ | Set-Content (Join-Path $watchUseCase 'ImportNetworkSourcesUseCase.kt') -Encoding utf8NoBOM

    # S3160: the browse mediaType row. Both sides are named sets of string constants whose CONSTANT
    # names differ by design, so the fixture keeps that asymmetry - a fixture that named them alike
    # would pass whether the gate compared values or names.
    @'
package com.sza.fastmediasorter.domain.usecase
class ListPhoneResourcePageUseCase {
    companion object {
        private const val FILTER_PHOTOS = "photos"
        private const val FILTER_VIDEOS = "videos"
        private val KNOWN_MEDIA_TYPE_FILTERS: Set<String> = setOf(
            FILTER_PHOTOS,
            FILTER_VIDEOS
        )
    }
}
'@ | Set-Content (Join-Path $phoneUseCase 'ListPhoneResourcePageUseCase.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.wear.domain.browse
object BrowseCategoryCatalog {
    const val TOKEN_VIDEOS = "videos"
    const val TOKEN_PHOTOS = "photos"
    val PHONE_FILTER_TOKENS: Set<String> = setOf(
        TOKEN_PHOTOS,
        TOKEN_VIDEOS
    )
}
'@ | Set-Content (Join-Path $watchBrowse 'BrowseCategoryCatalog.kt') -Encoding utf8NoBOM

    # S3161: the favourite delta's sourceId vocabulary. Both sides are top-level constants outside
    # any object, which is the shape no other row in this fixture exercises.
    @'
package com.sza.fastmediasorter.domain.model
const val SOURCE_ID_LOCAL = "local"
const val SOURCE_ID_NETWORK = "network"
const val SOURCE_ID_STREAM = "stream"
const val SOURCE_ID_VOICE_NOTE = "voice_note"
'@ | Set-Content (Join-Path $phoneModel 'WearFavoritesPayload.kt') -Encoding utf8NoBOM

    @'
package com.sza.fastmediasorter.wear.domain.model
const val SOURCE_ID_LOCAL = "local"
const val SOURCE_ID_NETWORK = "network"
const val SOURCE_ID_STREAM = "stream"
const val SOURCE_ID_VOICE_NOTE = "voice_note"
'@ | Set-Content (Join-Path $watchModel 'WearFavoriteRecord.kt') -Encoding utf8NoBOM

    return @{ Root = $sandbox; Phone = $phoneDir; Watch = $watchDir }
}

function Invoke-GateOnSandbox($sandbox, [switch]$NoGate) {
    $callArgs = @('-NoProfile', '-File', $gateScript, '-PhoneRoot', $sandbox.Phone, '-WatchRoot', $sandbox.Watch, '-Quiet')
    if (-not $NoGate) { $callArgs += '-Gate' }
    & $pwshExe @callArgs 2>&1 | Out-Null
    return $LASTEXITCODE
}

# --- Case 1: Baseline green ---
$sb = New-FixtureSandbox
try {
    $code = Invoke-GateOnSandbox $sb
    Assert-That "1. Baseline clean fixture" ($code -eq 0) "expected 0, got $code"
} finally { Remove-Item -Recurse -Force $sb.Root -ErrorAction SilentlyContinue }

# --- Case 2: constMap value diverges ---
$sb = New-FixtureSandbox
try {
    @'
package com.sza.fastmediasorter.wear.data.wear
object WearDataLayerPaths {
    const val PATH_SHARED = "/fms/shared"
    const val EVENT_A = "event_a_DIVERGED"
    const val EVENT_B = "event_b"
}
'@ | Set-Content (Join-Path $sb.Watch 'data/wear/WearDataLayerPaths.kt') -Encoding utf8NoBOM
    $code = Invoke-GateOnSandbox $sb
    Assert-That "2. constMap value diverges" ($code -eq 1) "expected 1, got $code"
} finally { Remove-Item -Recurse -Force $sb.Root -ErrorAction SilentlyContinue }

# --- Case 3: constMap values swapped (ADR-3 test) ---
$sb = New-FixtureSandbox
try {
    @'
package com.sza.fastmediasorter.wear.data.wear
object WearDataLayerPaths {
    const val PATH_SHARED = "/fms/shared"
    const val EVENT_A = "event_b"
    const val EVENT_B = "event_a"
}
'@ | Set-Content (Join-Path $sb.Watch 'data/wear/WearDataLayerPaths.kt') -Encoding utf8NoBOM
    $code = Invoke-GateOnSandbox $sb
    Assert-That "3. constMap values swapped (ADR-3)" ($code -eq 1) "expected 1, got $code"
} finally { Remove-Item -Recurse -Force $sb.Root -ErrorAction SilentlyContinue }

# --- Case 4: Enum member missing on watch ---
$sb = New-FixtureSandbox
try {
    @'
package com.sza.fastmediasorter.wear.domain.model
enum class WearOpenOnPhoneOutcome { SUCCESS }
'@ | Set-Content (Join-Path $sb.Watch 'domain/model/WearOpenOnPhonePayload.kt') -Encoding utf8NoBOM
    $code = Invoke-GateOnSandbox $sb
    Assert-That "4. Enum member missing on watch" ($code -eq 1) "expected 1, got $code"
} finally { Remove-Item -Recurse -Force $sb.Root -ErrorAction SilentlyContinue }

# --- Case 5: SerializedName changes, member names untouched ---
$sb = New-FixtureSandbox
try {
    @'
package com.sza.fastmediasorter.domain.model
enum class WearPhoneResourceRequestKind { @SerializedName("CHANGED") AUDIO }
enum class WearPhoneResourceResponseStatus { @SerializedName("OK") OK }
enum class WearPhoneResourceDeleteOutcome { @SerializedName("DELETED") DELETED }
'@ | Set-Content (Join-Path $sb.Phone 'domain/model/WearPhoneResourcePayload.kt') -Encoding utf8NoBOM
    $code = Invoke-GateOnSandbox $sb
    Assert-That "5. SerializedName changes, member names untouched" ($code -eq 1) "expected 1, got $code"
} finally { Remove-Item -Recurse -Force $sb.Root -ErrorAction SilentlyContinue }

# --- Case 6: Constant moved between two companions in one file ---
$sb = New-FixtureSandbox
try {
    @'
package com.sza.fastmediasorter.domain.model
class WearFileTransfer {
    class WearFileTransferAck {
        companion object {
            const val OUTCOME_SUCCESS = "success"
        }
    }
    class WearFileReceiveAck {
        companion object {
            const val OUTCOME_TOO_LARGE = "too_large"
            const val OUTCOME_SAVED = "saved"
        }
    }
}
enum class WearFileReceiveOutcome { SAVED, REFUSED, FAILED }
'@ | Set-Content (Join-Path $sb.Phone 'domain/model/WearFileTransfer.kt') -Encoding utf8NoBOM
    $code = Invoke-GateOnSandbox $sb
    Assert-That "6. Constant moved between companions in one file" ($code -eq 1) "expected 1, got $code"
} finally { Remove-Item -Recurse -Force $sb.Root -ErrorAction SilentlyContinue }

# --- Case 7: Undeclared mirrored enum ---
$sb = New-FixtureSandbox
try {
    @'
package com.sza.fastmediasorter.domain.model
enum class WearUndeclaredEnum { FOO, BAR }
'@ | Set-Content (Join-Path $sb.Phone 'domain/model/WearUndeclaredPayload.kt') -Encoding utf8NoBOM
    @'
package com.sza.fastmediasorter.wear.domain.model
enum class WearUndeclaredEnum { FOO, BAR }
'@ | Set-Content (Join-Path $sb.Watch 'domain/model/WearUndeclaredPayload.kt') -Encoding utf8NoBOM
    $code = Invoke-GateOnSandbox $sb
    Assert-That "7. Undeclared mirrored enum discovered" ($code -eq 1) "expected 1, got $code"
} finally { Remove-Item -Recurse -Force $sb.Root -ErrorAction SilentlyContinue }

# --- Case 8: LocalOnly type gains SerializedName ---
$sb = New-FixtureSandbox
try {
    @'
package com.sza.fastmediasorter.wear.domain.model
class WearFileTransferMetadata {
    class WearFileTransferAck {
        companion object {
            const val OUTCOME_SUCCESS = "success"
            const val OUTCOME_TOO_LARGE = "too_large"
        }
    }
    class WearFileReceiveAck {
        companion object {
            const val OUTCOME_SAVED = "saved"
        }
    }
}
enum class WearFileReceiveOutcome { @SerializedName("SAVED") SAVED, FAILED }
'@ | Set-Content (Join-Path $sb.Watch 'domain/model/WearFileTransferMetadata.kt') -Encoding utf8NoBOM
    $code = Invoke-GateOnSandbox $sb
    Assert-That "8. LocalOnly type gains SerializedName" ($code -eq 1) "expected 1, got $code"
} finally { Remove-Item -Recurse -Force $sb.Root -ErrorAction SilentlyContinue }

# --- Case 9: LocalOnly type becomes a field of a serialized class ---
$sb = New-FixtureSandbox
try {
    @'
package com.sza.fastmediasorter.domain.model
class WearFileTransfer {
    class WearFileTransferAck {
        companion object {
            const val OUTCOME_SUCCESS = "success"
            const val OUTCOME_TOO_LARGE = "too_large"
        }
    }
    class WearFileReceiveAck {
        companion object {
            const val OUTCOME_SAVED = "saved"
        }
    }
}
enum class WearFileReceiveOutcome { SAVED, REFUSED, FAILED }
data class WearLeakedPayload(
    @SerializedName("outcome") val outcome: WearFileReceiveOutcome
)
'@ | Set-Content (Join-Path $sb.Phone 'domain/model/WearFileTransfer.kt') -Encoding utf8NoBOM
    $code = Invoke-GateOnSandbox $sb
    Assert-That "9. LocalOnly type becomes field in serialized class" ($code -eq 1) "expected 1, got $code"
} finally { Remove-Item -Recurse -Force $sb.Root -ErrorAction SilentlyContinue }

# --- Case 10: Declared file missing returns exit 2 ---
$sb = New-FixtureSandbox
try {
    Remove-Item (Join-Path $sb.Phone 'service/WearDataLayerPaths.kt') -Force
    $code = Invoke-GateOnSandbox $sb
    Assert-That "10. Declared file missing returns exit 2" ($code -eq 2) "expected 2, got $code"
} finally { Remove-Item -Recurse -Force $sb.Root -ErrorAction SilentlyContinue }

# --- Case 11: phone widens WATCH_TRANSFERABLE alone (S2641) ---
$sb = New-FixtureSandbox
try {
    @'
package com.sza.fastmediasorter.domain.model
enum class ResourceType {
    LOCAL,
    SMB,
    SFTP,
    FTP,
    CLOUD;

    companion object {
        val WATCH_TRANSFERABLE: Set<ResourceType> = setOf(SMB, FTP, SFTP, CLOUD)
    }
}
'@ | Set-Content (Join-Path $sb.Phone 'domain/model/Models.kt') -Encoding utf8NoBOM
    $code = Invoke-GateOnSandbox $sb
    Assert-That "11. Phone widens the watch-transferable set alone" ($code -eq 1) "expected 1, got $code"
} finally { Remove-Item -Recurse -Force $sb.Root -ErrorAction SilentlyContinue }

# --- Case 12: watch drops a parseType branch alone (S2641, the same row from the other side) ---
$sb = New-FixtureSandbox
try {
    @'
package com.sza.fastmediasorter.wear.domain.usecase
class ImportNetworkSourcesUseCase {
    private fun parseType(raw: String): NetworkSourceType? = when (raw.uppercase()) {
        "SMB" -> NetworkSourceType.SMB
        "FTP" -> NetworkSourceType.FTP
        else -> null
    }
}
'@ | Set-Content (Join-Path $sb.Watch 'domain/usecase/ImportNetworkSourcesUseCase.kt') -Encoding utf8NoBOM
    $code = Invoke-GateOnSandbox $sb
    Assert-That "12. Watch drops a parseType branch alone" ($code -eq 1) "expected 1, got $code"
} finally { Remove-Item -Recurse -Force $sb.Root -ErrorAction SilentlyContinue }

# --- Case 13: route value diverges while event vocabulary stays equal ---
$sb = New-FixtureSandbox
try {
    @'
package com.sza.fastmediasorter.wear.data.wear
object WearDataLayerPaths {
    const val PATH_SHARED = "/fms/diverged"
    const val EVENT_A = "event_a"
    const val EVENT_B = "event_b"
}
'@ | Set-Content (Join-Path $sb.Watch 'data/wear/WearDataLayerPaths.kt') -Encoding utf8NoBOM
    $code = Invoke-GateOnSandbox $sb
    Assert-That "13. Route value diverges while event values match" ($code -eq 1) "expected 1, got $code"
} finally { Remove-Item -Recurse -Force $sb.Root -ErrorAction SilentlyContinue }

# --- Case 14: watch drops a browse mediaType token alone (S3160) ---
$sb = New-FixtureSandbox
try {
    @'
package com.sza.fastmediasorter.wear.domain.browse
object BrowseCategoryCatalog {
    const val TOKEN_VIDEOS = "videos"
    const val TOKEN_PHOTOS = "photos"
    val PHONE_FILTER_TOKENS: Set<String> = setOf(
        TOKEN_PHOTOS
    )
}
'@ | Set-Content (Join-Path $sb.Watch 'domain/browse/BrowseCategoryCatalog.kt') -Encoding utf8NoBOM
    $code = Invoke-GateOnSandbox $sb
    Assert-That "14. Watch drops a browse mediaType token alone" ($code -eq 1) "expected 1, got $code"
} finally { Remove-Item -Recurse -Force $sb.Root -ErrorAction SilentlyContinue }

# --- Case 15: a browse token is renamed on one side only (S3160) ---
#
# The value is what travels, so this is the case that decides whether the row compares values or
# constant names: the two sides' CONSTANT names differ in the clean fixture already, and a row
# comparing names would have failed case 1 instead of this one.
$sb = New-FixtureSandbox
try {
    @'
package com.sza.fastmediasorter.wear.domain.browse
object BrowseCategoryCatalog {
    const val TOKEN_VIDEOS = "clips"
    const val TOKEN_PHOTOS = "photos"
    val PHONE_FILTER_TOKENS: Set<String> = setOf(
        TOKEN_PHOTOS,
        TOKEN_VIDEOS
    )
}
'@ | Set-Content (Join-Path $sb.Watch 'domain/browse/BrowseCategoryCatalog.kt') -Encoding utf8NoBOM
    $code = Invoke-GateOnSandbox $sb
    Assert-That "15. Browse token value renamed on one side" ($code -eq 1) "expected 1, got $code"
} finally { Remove-Item -Recurse -Force $sb.Root -ErrorAction SilentlyContinue }

# --- Case 16: the watch respells a favourite source id alone (S3161) ---
#
# The phone decides whether to apply a delta item by comparing this value, so a respelling on one
# side silently restores the bug the row exists to prevent: the item stops matching and its watch
# MediaStore address reaches the phone's favorites table again.
$sb = New-FixtureSandbox
try {
    @'
package com.sza.fastmediasorter.wear.domain.model
const val SOURCE_ID_LOCAL = "watch_local"
const val SOURCE_ID_NETWORK = "network"
const val SOURCE_ID_STREAM = "stream"
const val SOURCE_ID_VOICE_NOTE = "voice_note"
'@ | Set-Content (Join-Path $sb.Watch 'domain/model/WearFavoriteRecord.kt') -Encoding utf8NoBOM
    $code = Invoke-GateOnSandbox $sb
    Assert-That "16. Favourite source id respelled on the watch alone" ($code -eq 1) "expected 1, got $code"
} finally { Remove-Item -Recurse -Force $sb.Root -ErrorAction SilentlyContinue }

Write-Host ''
if ($script:fail -gt 0) {
    Write-Host "assert-wear-wire-vocabulary-parity.tests: FAIL - $($script:fail) case(s) failed, $($script:pass) passed." -ForegroundColor Red
    exit 1
}
Write-Host "assert-wear-wire-vocabulary-parity.tests: PASS - $($script:pass) case(s)." -ForegroundColor Green
exit 0
