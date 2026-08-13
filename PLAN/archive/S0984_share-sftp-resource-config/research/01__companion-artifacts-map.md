# Research 01 - S0421 companion artifacts + S0422 share plumbing (S0984 planning input)

**Source:** codebase read (2026-07-11), corroborated by `android-solution-researcher` sweep.
**Status:** Resolved - all integration points located; no open blockers.

## Reused contract (frozen, S0421)

- `CompanionConfigDto` (+ `CompanionAccessPathDto`, `CompanionRootDto`, `CompanionConfigException`) - `app_v2/src/main/java/com/sza/fastmediasorter/data/companion/CompanionConfigDto.kt`. Gson DTO, `schemaVersion=1`, plain-JSON or `FMSCFG1:`+base64(gzip) transport. Fields: schemaVersion, resourceName?, protocol?, accessPaths?, username?, password?, hostKeyFingerprintSha256?, roots?, createdAt?.
- `CompanionConfigParser.validate()` - `.../data/companion/CompanionConfigParser.kt:61-90`. Currently HARD-requires `password` (`:82`) and `hostKeyFingerprintSha256` (`:83`). These two lines are the relaxation target.
- `ImportCompanionConfigUseCase.import()` - `.../domain/usecase/companion/ImportCompanionConfigUseCase.kt:77-136`. GOTCHA (High): even after parser relaxation, `:83-89` hard-fails when `SshFingerprintNormalizer.canonical("")` returns null. Must fall through to `hostKeyFingerprint = null` (no pinning) for blank fingerprint. `import(dto)` is public and reused by the QR path -> the new import trampoline calls it directly with `dto.copy(password = entered)` for passwordless configs.
- Credentials: `SmbOperationsUseCase.saveSftpCredentials(host,port,username,password)` -> dedup by (SFTP,host,port), `.../domain/usecase/SmbOperationsUseCase.kt:345`. Export reads via `getSftpCredentials(credentialsId): Result<NetworkCredentialsEntity>` (`:452`); decrypted password from `NetworkCredentialsEntity.password` getter (`:50-85`).
- `SftpPathUtils.parseSftpPath(path): SftpPathInfo(host,port,remotePath)` - `.../utils/SftpPathUtils.kt:26`. Export uses it to recover host/port/root from `resource.path`.

## Export template (S0422 sibling `.fmsr`, structurally identical)

- Menu: `ResourceAdapter` builds `btnMoreActions` PopupMenu from `R.menu.resource_item_actions` in TWO viewholders - grid `:446-477`, list `:804-864`; visibility gated per item (`action_export_resource?.isVisible = !isPredefinedVirtualResource`, `:450`). A third path `layoutInlineActions` (`:786-797`) has no export button today - out of scope.
- Host: `MainActivity` constructs adapter `:884`, provides `onExportClick` `:927` (warning dialog -> `viewModel.exportResourceForShare`).
- VM: `MainViewModel.exportResourceForShare` `:334-351` writes cache file, emits `MainEvent.ShareResourceFile(path)` (`MainEvent` sealed `:75`, case `:107`).
- Share launch: `MainEventHandler.shareResourceFile(filePath)` `:154-170` - `FileProvider.getUriForFile(activity, "${packageName}.fileprovider", file)` + `ACTION_SEND` + vendor MIME + `createChooser`. Dispatch at `:110`.
- Receiver template: `ResourceImportActivity` (`ui/resourceimport/ResourceImportActivity.kt`) - transparent host, `resolveUri()` (SEND EXTRA_STREAM / VIEW data), preview -> confirm -> import. Manifest `:589-612` registers VIEW (vendor MIME + `file` pathPattern `.*\.fmsr`) + SEND (vendor MIME). Comment `:587` records that bare `application/octet-stream` is intentionally NOT registered (chooser-noise).
- FileProvider: authority `${applicationId}.fileprovider`, paths `res/xml/file_provider_paths.xml` (`cache-path name="cache"` covers `cacheDir`).

## Constraints established

- Flavor: `lite` sets `SUPPORT_LOCAL_NETWORK=false` (`build.gradle.kts:419`) - no SFTP resources exist there, so the export menu (gated on `resource.type == SFTP`) never shows. Import activity lives in `src/main` matching `ResourceImportActivity`'s existing flavor posture (the same lite "Open with" presence already ships for `.fmsr`). No flavor source-set split; no `BuildConfig.IS_*` in `src/main` (Rule 14).
- Contract frozen cross-repo (companion in FastMediaSorter LITE, Go). Export emits the schema 1:1 - no new/renamed fields. Validation relaxation is Android-only (empty password + empty fingerprint), no schemaVersion bump (LITE always sends both).
- Tests: `CompanionConfigParserTest` fixture is frozen "on both ends" - relaxed-validation tests use NEW inline JSON, never mutate the canonical vector.
- Key-auth-only SFTP resources (no stored password): export degrades to passwordless `.fmscfg` (receiver enters password at import) - reuses the passwordless machinery, no key material shared.
- MIME: register vendor `application/vnd.fms.companion-config+json` for reliable SEND; octet-stream only on VIEW (the tap-from-messenger path, owner-accepted 23:37).

## Decisions folded into the plan (from research open questions)

1. Export schema = `.fmscfg` `CompanionConfigDto` 1:1, single resource, one root.
2. One exported file = one `MediaResource` (multi-select out of scope, strategic §3.3).
3. `accessPaths` = single `kind=lan` entry (host/port from `resource.path`); multi-path fallback stays the S0421 follow-up.
4. Separate `CompanionConfigImportActivity` (not extending `ResourceImportActivity`) - keeps `.fmscfg`/`.fmsr` formats decoupled.
5. Key-auth resources -> passwordless export (see above).
6. Relaxation covers BOTH parser (import side, Lite no-auth shares) and export (mobile source with no password/fingerprint).
