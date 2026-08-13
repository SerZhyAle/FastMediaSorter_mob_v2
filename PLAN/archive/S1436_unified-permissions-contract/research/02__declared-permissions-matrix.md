# 02 - Declared permissions per flavor and build type

Research performed 2026-08-06 for S1436, read-only sweep of all 16 manifests plus the flavor wiring in
`app_v2/build.gradle.kts`. Flavor capability values are read from `docs/FLAVOR_MATRIX.md`, never restated from
memory (S1392).

## Why this matters to the spec

The registry filters its entries on two axes - SDK range and four `BuildConfig` booleans. The merged manifest
varies on at least five. Everything the registry cannot express is a permission it cannot list.

## The five axes the manifest actually varies on

1. **Flavor overlay** - `src/<flavor>/AndroidManifest.xml`, auto-detected.
2. **Build type overlay** - `src/release/AndroidManifest.xml:16-21` removes three permissions from every
   flavor's release variant: RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE_DATA_SYNC and
   REQUEST_IGNORE_BATTERY_OPTIMIZATIONS ("scheduled ops not yet submitted for Play review").
3. **Gradle property switches** - `fms.screenCapture`, `fms.edgeGestureOverlay`, `fms.edgeGestureTile`
   (`gradle.properties:18-27`) gate whole manifest fragments for `standard`. They are not `BuildConfig` fields,
   so the registry has no channel to read them.
4. **Static manifest injection** - `androidComponents.onVariants` + `addStaticManifestFile`
   (`app_v2/build.gradle.kts:1010-1094`) layers seven supplementary source sets onto flavors.
5. **Flavor manifest substitution** - `productFlavors.noLegal` sets `manifest.srcFile("src/vr/AndroidManifest.xml")`
   (`build.gradle.kts:616`), which *replaces* rather than merges the auto-detected `src/noLegal` slot. This is the
   S0183 regression; the shipped fix re-injects `src/noLegal/AndroidManifest.xml` as an additional merger input
   (`build.gradle.kts:1036-1038`). noLegal is whole today, but the pattern stays fragile for any future flavor.

## Universal permissions

Declared by `app_v2/src/main/AndroidManifest.xml` for all six flavors: READ_EXTERNAL_STORAGE (`:6`),
WRITE_EXTERNAL_STORAGE maxSdk 28 (`:7`), READ_MEDIA_IMAGES/VIDEO/AUDIO (`:8-10`), MANAGE_EXTERNAL_STORAGE (`:14`),
MANAGE_MEDIA (`:17`), CAMERA (`:18`), ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION (`:20-21`),
READ_CONTACTS (`:22`), INTERNET (`:26`), ACCESS_LOCAL_NETWORK minSdk 37 (`:27`), ACCESS_NETWORK_STATE +
ACCESS_WIFI_STATE (`:28-29`), CHANGE_WIFI_MULTICAST_STATE (`:31`), WAKE_LOCK (`:65`), VIBRATE (`:68`),
FOREGROUND_SERVICE (`:71`), FOREGROUND_SERVICE_MEDIA_PLAYBACK (`:72`), POST_NOTIFICATIONS (`:73`),
RECORD_AUDIO (`:75`), RECEIVE_BOOT_COMPLETED (`:78`), FOREGROUND_SERVICE_DATA_SYNC (`:79`),
FOREGROUND_SERVICE_MICROPHONE (`:81`), REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (`:82`).

Exceptions: ACCESS_LOCAL_NETWORK is removed in `lite` (`src/lite/AndroidManifest.xml:13-15`, `tools:node="remove"`);
the last three of that list are removed in every release build (axis 2 above).

No `<uses-permission-sdk-23>` and no app-defined `<permission>` exist in any of the 16 manifests. All 17
`<uses-feature>` declarations are `required="false"`, so device reach is not restricted.

## Differentiated permissions

| Permission | standard | noLegal | lite | photos | legacy | vr | Source |
|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| ACCESS_LOCAL_NETWORK | + | + | - | + | + | + | `main:27` minus the lite removal; inert everywhere (minSdk 37 vs targetSdk 36) |
| FOREGROUND_SERVICE_MEDIA_PROJECTION | +¹ | + | - | - | - | - | `screenCapture/AndroidManifest.xml:12`, injected `build.gradle.kts:1073-1077` |
| SYSTEM_ALERT_WINDOW | +² | + | - | - | - | - | `standardScreenCapture/AndroidManifest.xml:14`, `noLegal/AndroidManifest.xml:21` |
| FOREGROUND_SERVICE_SPECIAL_USE | +² | + | - | - | - | - | same two files |
| REQUEST_INSTALL_PACKAGES | - | + | - | - | - | - | `noLegal/AndroidManifest.xml:20`, sideload only (S0183) |
| com.oculus.permission.HAND_TRACKING | - | + | - | - | - | + | `vr/AndroidManifest.xml:12`; reaches noLegal through the manifest substitution |

¹ For standard only when `fms.screenCapture` is not `off` - currently `on` (`gradle.properties:23`). Unconditional
for noLegal (`build.gradle.kts:1074`).
² For standard only when `fms.edgeGestureOverlay` is not `off` - currently `on` (`gradle.properties:24`).
A build invoked with both switches `off` drops all three special permissions from standard.

`castEnabled`, `wearGms` and `launcherEnabled` overlays declare zero permissions - verified by full reads.

## Runtime subset and how each is granted

- Ordinary runtime dialog: READ_EXTERNAL_STORAGE (23+), WRITE_EXTERNAL_STORAGE (23-28),
  READ_MEDIA_IMAGES/VIDEO/AUDIO (33+), CAMERA (23+), ACCESS_FINE/COARSE_LOCATION (23+), READ_CONTACTS (23+),
  POST_NOTIFICATIONS (33+), RECORD_AUDIO (23+), ACCESS_LOCAL_NETWORK (37+, inert today).
- Dedicated system screen: MANAGE_EXTERNAL_STORAGE (30+), MANAGE_MEDIA (31+), SYSTEM_ALERT_WINDOW (23+),
  REQUEST_INSTALL_PACKAGES (26+ per-app).
- Direct consent dialog: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (23+) - stripped from release manifests.
- Separate runtime consent, not a manifest permission: the MediaProjection capture dialog.
- Install-time, never shown: the foreground-service type declarations, WAKE_LOCK, VIBRATE, INTERNET,
  ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE, RECEIVE_BOOT_COMPLETED, HAND_TRACKING.

## Registry gates and what they miss

Four `BuildConfig` fields are read through the gate map (`PermissionRegistryRepositoryImpl.kt:234-242`):
`SUPPORT_AUDIO` (gates `record_audio`), `SUPPORT_LOCAL_NETWORK` (gates `access_local_network`),
`SUPPORT_LAUNCHER` (gates `read_contacts`), `ENABLE_PERSISTENT_AUDIO_PLAYBACK` (gates `post_notifications` in the
settings list only). `IS_NO_LEGAL_FLAVOR` exists and would be the natural gate for REQUEST_INSTALL_PACKAGES and
HAND_TRACKING if those were ever folded in.

Nothing reaches the three `fms.*` gradle properties. Gate resolution is a compile-time `when`, not reflection -
deliberately, after S0970 showed R8 constant-folding silently disabling a permission through the reflective form.
Any new gate kind must keep that property.

## Consequences the spec must answer

1. **No build-type axis.** The `battery_optimization` entry is unconditional
   (`PermissionRegistryRepositoryImpl.kt:144-151`) and its grant button fires the settings Intent unconditionally
   (`PermissionsManagementFragment.kt:238-241`), while the release manifest no longer declares the permission
   (`release/AndroidManifest.xml:20-21`). Needs on-device confirmation on a real release build; the design answer
   is the same either way - do not show a row for a permission this build does not declare.
2. **Five permissions have no registry row at all**: SYSTEM_ALERT_WINDOW, REQUEST_INSTALL_PACKAGES,
   FOREGROUND_SERVICE_MEDIA_PROJECTION consent, FOREGROUND_SERVICE_SPECIAL_USE, HAND_TRACKING. The overlay one
   is worse than absent - it is duplicated independently in Welcome (`WelcomeGesturesManager.kt:15-21`, whose KDoc
   states it mirrors the settings copy) and in Settings (`OperationsGesturesManager`).
3. **RECORD_AUDIO is declared in every flavor** but hidden from the settings screen in `photos`
   (`SUPPORT_AUDIO=false`), leaving that flavor with a declared permission and no explanation surface.
4. **POST_NOTIFICATIONS is hidden from the settings screen** for lite and photos while onboarding always asks for
   it and the manifest always declares it - no post-onboarding review surface in those builds.
5. **`docs/PRIVACY_POLICY.md:124-146`** explains roughly 11 of the ~31 distinct declared identifiers. Missing:
   the whole screen-capture/overlay/install group, plus CAMERA, POST_NOTIFICATIONS, RECORD_AUDIO, location,
   MANAGE_MEDIA, battery optimization, ACCESS_LOCAL_NETWORK and HAND_TRACKING.
6. **No mechanical gate exists.** `Glob` over `scripts/quality/assert-*permission*.ps1` and
   `assert-*manifest*.ps1` returns nothing. `docs/RELEASE_READINESS_STANDARD.md:79,135` already names a manual
   "permission audit" in the release checklist - the hook to mechanise rather than invent.

## Document registry

No `permissions` product area or trigger exists in `docs/DOCUMENT_REGISTRY.jsonl` - confirmed by
`scripts/document_registry/query.ps1` on both axes. Registered records that this work touches:
`legal-downloads` (privacy policy, materially incomplete), `settings-reference` (correct and in sync for the
"Permissions & Access" row), `feature-inventory`, `flavor-capability-matrix`.
`dev/FLAVOR_DEVELOPMENT_RULES.md` contains no permission or manifest-wiring guidance despite the S0183 history.
