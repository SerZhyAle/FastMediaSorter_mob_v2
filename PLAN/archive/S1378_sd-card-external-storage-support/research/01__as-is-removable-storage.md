# S1378 / Research 01 - AS-IS: removable volumes in app_v2

**Date:** 2026-08-03
**Method:** catalog query + targeted grep over `app_v2/src` (read-only research pass).
**Scope of the question (§6 item 1):** what part of "SD card / external drive support" already exists, and where exactly the app refuses.

---

## 1. Source discovery

- Auto-discovery goes `ScanLocalFoldersUseCase` -> `MediaStoreRepositoryImpl`, which queries `MediaStore.Files.getContentUri("external")`. Since API 29 that collection is a merged view across every mounted external volume, so SD-card folders that the platform has already indexed **can** surface as ordinary `LOCAL` resources with a plain filesystem path.
- Manual add goes through `AddResourceScanManager.showFolderSelectionDialog()`. Its quick-path shortcuts are hardcoded to `/storage/emulated/0/..`; the only way to a removable volume is the "Browse with SAF" button (`ActivityResultContracts.OpenDocumentTree`).
- Nothing anywhere enumerates `StorageManager.storageVolumes` to *offer* volumes to the user.

**Conclusion:** removable media is reachable but never advertised. Discovery is the first gap.

## 2. Existing removable-aware code

- `core/util/UriPathResolver.kt` already resolves a SAF document id back to a real mount path through `StorageManager.storageVolumes`, with an API-30 `StorageVolume.getDirectory()` branch and a pre-30 reflection fallback on the hidden `getPath()`. This is the reusable primitive - but it runs only reactively, after the user picked a tree.
- No unit test covers `UriPathResolver`. The reflection fallback is an OEM-variance risk with no regression guard.

## 3. File-access model

- Mixed, and the branch is per-call, not per-layer: `LocalMediaScanner`, `LocalTransferProvider` and `SafHelper` each test `path.startsWith("content://")` and pick a `DocumentFile`/`ContentResolver` route or a `java.io.File` route.
- There is no single abstraction over "a place where files live" on the local side. The protocol-level abstractions that do exist - `domain/strategy/ResourceStrategy` (form/validation) and `data/transfer/FileOperationStrategy` (copy/move/delete/rename/list) - dispatch by URL scheme (`smb://`, `sftp://`, `ftp://`, `cloud://`, else "local"), so a removable volume falls into the single "local" bucket together with internal storage.

## 4. The hard refusal

- `UnifiedFileOperationHandler` refuses whole-directory copy/move whenever the destination is a document-tree URI (`isDocumentTreeDestination`, around lines 578-598), returning `DirectoryOperationRefusal.Reason.DESTINATION_NOT_SUPPORTED`; the user-facing wording lives in `ui/browse/helpers/DirectoryRefusalMessages.kt`.
- Single-file copy/move/delete/rename to and from a `content://` path **is** implemented.

**Conclusion:** "move this whole folder to the SD card" is architecturally blocked today, not merely untested. This is the heaviest part of the ticket.

## 5. Free space

- `GetDeviceStorageUseCase` measures `Environment.getExternalStorageDirectory()` only - primary storage. Any "will it fit" decision about a removable destination is computed from the wrong volume.

## 6. Permissions and manifest

- Declared: `MANAGE_EXTERNAL_STORAGE` (with `tools:ignore="ScopedStorage"`), `READ_MEDIA_IMAGES` / `_VIDEO` / `_AUDIO`, plus the legacy read/write pair for older API levels.
- No `requestLegacyExternalStorage` anywhere - the app is scoped-storage compliant from Android 10 onward.
- No `android.hardware.usb.host` feature and no `USB_DEVICE_ATTACHED` intent filter in any of the manifest fragments. Raw USB-OTG has no hook at all - which matches the owner's scope decision to exclude it.
- `ChromeOsCompat.needsSafFolderPicker()` forces the SAF picker on ARC++ regardless of `MANAGE_EXTERNAL_STORAGE`, and `MainStoragePermissionsHelper` skips the all-files request there entirely. So a SAF-first design is already mandatory on one supported platform.

## 7. Flavor and API constraints

- All four flavors share the same local-storage stack; the only difference is `legacy` at minSdk 23.
- Flavor variance in storage targeting is expressed through the injected `RestrictedTreeTargetPolicy` interface (`core/storage/`, per-flavor Hilt module), never a `BuildConfig.IS_*` guard - the pattern to follow if a variant-specific rule appears.
- API boundaries that matter: 23-28 runtime read/write pair; 29 write permission goes inert; 30+ all-files access behind `MANAGE_EXTERNAL_STORAGE`; 33+ granular media permissions.

## 8. Shipped-capability check

`docs/ALL_FEATURES.jsonl` contains no record about SD card, removable, USB or external-volume support. Nothing to deduplicate against.

---

## Risks carried into the spec

1. Whole-directory operations onto a SAF tree are refused by design - a real implementation (recursive `DocumentFile` walk with progress and cancel) is required, not a relaxed guard.
2. Free-space accounting is single-volume and will silently mis-measure removable destinations.
3. `UriPathResolver`'s volume resolution is untested and partly reflection-based.

## Related tickets

- `S1354` capture-destination-missing-saf-support (Draft) - same access layer, different entry point.
