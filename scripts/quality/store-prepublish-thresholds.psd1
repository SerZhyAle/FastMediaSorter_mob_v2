<#
  store-prepublish-thresholds.psd1 (S3364)

  The single home of every store pre-publication threshold the S3364 checks read.
  The check scripts import this file instead of hardcoding the numbers, so a store
  policy change is a data edit here, not a code edit.

  Every entry has a companion line in SourceNotes naming the store page the value
  was read from and the date that page was verified live. The canonical source of
  all of them is the S3363 digest:
  PLAN/S3363_store-prepublish-testing-requirements/research/01__store-prepublish-tools-and-requirements.md
  (public mirror: docs/STORE_PREPUBLISH_TOOLS_AND_REQUIREMENTS.md).

  Update a value only together with its SourceNotes line - a threshold without a
  dated source is a hardcoded number that rots silently.

  Consumed by: assert-wear-phone-identity-parity.ps1, assert-wear-64bit-abi.ps1,
  assert-meta-packaging-limits.ps1.
#>

@{
    # Google Play, Wear OS: apps must target API 35+ from 2026-08-31.
    WearTargetApi          = 35

    # Google Play, Wear OS: 64-bit support becomes mandatory on this date.
    Wear64BitDeadline      = '2026-09-15'

    # Meta VRC packaging: APK size must stay below 1 GB (1073741824 bytes).
    MetaMaxApkBytes        = 1073741824

    # Meta VRC packaging: OBB expansion size must stay below 4 GB (4294967296 bytes).
    MetaMaxObbBytes        = 4294967296

    # Meta VRC packaging: 64-bit native binaries required - the ABI that must be present.
    MetaRequiredAbi        = 'arm64-v8a'

    # Meta VRC packaging: minimum APK signature scheme the store accepts.
    MetaMinSignatureScheme = 'v2'

    SourceNotes = @{
        WearTargetApi          = 'Wear target API 35+ from 2026-08-31 - Wear OS quality page https://developer.android.com/docs/quality-guidelines/wear-app-quality (page 2026-08-06) and Play target API policy https://support.google.com/googleplay/android-developer/answer/11926878 [Verified 2026-09-21]'
        Wear64BitDeadline      = 'Wear OS 64-bit support mandatory from 2026-09-15 - Wear OS quality page https://developer.android.com/docs/quality-guidelines/wear-app-quality (page 2026-08-06) [Verified 2026-09-21]'
        MetaMaxApkBytes        = 'APK below 1 GB - Meta VRC master page https://developers.meta.com/horizon/resources/publish-quest-req (page 2026-08-19) [Verified 2026-09-21]'
        MetaMaxObbBytes        = 'OBB below 4 GB - Meta VRC master page https://developers.meta.com/horizon/resources/publish-quest-req (page 2026-08-19) [Verified 2026-09-21]'
        MetaRequiredAbi        = '64-bit binaries required - Meta VRC master page https://developers.meta.com/horizon/resources/publish-quest-req (page 2026-08-19) [Verified 2026-09-21]'
        MetaMinSignatureScheme = 'APK signature scheme v2 - Meta VRC master page https://developers.meta.com/horizon/resources/publish-quest-req (page 2026-08-19) [Verified 2026-09-21]'
    }
}
