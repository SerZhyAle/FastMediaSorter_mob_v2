# Phase 02 - Import mapping (apply per-root params)

**Goal:** `ImportCompanionConfigUseCase` builds each `MediaResource` from the root's v2 fields, falling back to v1 defaults when absent.

## Steps

1. [ ] In `ImportCompanionConfigUseCase.import()`, extract a `private fun buildResource(root, host, port, credentialsId, canonicalFingerprint, configName): MediaResource` to keep `import()` readable (function length / detekt).
2. [ ] Resolve media types + allFiles:
   - `profileEnum = root.profile?.let { profileFromToken(it) }` (soft null on unknown).
   - `explicitTypes = root.mediaTypes?.mapNotNull { mediaTypeFromToken(it) }?.toSet()?.ifEmpty { null }`.
   - Precedence: `explicitTypes` if present; else if `profileEnum != null` use `profileEnum.mediaPreset()` (its `supportedMediaTypes` or, when null, keep `DEFAULT_MEDIA_TYPES`); else `DEFAULT_MEDIA_TYPES` (v1 default).
   - `allFiles`: `root.allFiles ?: profileEnum?.mediaPreset()?.allFiles ?: false`.
3. [ ] Map remaining fields with v1 fallbacks:
   - `scanSubdirectories = root.scanSubdirectories ?: true` (v1 default true).
   - `showSubfoldersAsItems = root.showSubfoldersAsItems ?: false`, `showHiddenFiles = root.showHiddenFiles ?: false`.
   - `comment = root.comment?.ifBlank { null } ?: configName?.let { "Companion: $it" }` (explicit overrides default).
   - `accessPin = root.accessPin?.ifBlank { null }`.
   - `slideshowInterval = root.slideshowInterval?.takeIf { it > 0 } ?: DEFAULT_SLIDESHOW` (reuse `MediaResource` default 10 via a named const).
   - `profile = profileEnum ?: ResourceProfile.NONE`.
   - `rememberFileList = profileEnum?.mediaPreset()?.rememberFileList ?: false`.
4. [ ] Destination + read-only:
   - `isDestination = root.isDestination ?: false`.
   - `isReadOnly = if (isDestination) false else true` (destination implies writable; v1 default read-only otherwise).
   - Pass `destinationColor` when `root.destinationColor != null` (else let `addMultiple` assign). `MediaResource.destinationColor` has a non-null default, so only override when provided.
5. [ ] Keep `addResourceUseCase.addMultiple(resources)` unchanged - it still allocates destination slots/order/color.
   - Verification: `.\a.ps1 fk` compiles; a v1 config (no new fields) yields the same `MediaResource` as before (ALL types, scan=true, readOnly=true, comment "Companion: ..").

## Notes

- `@Suppress("TooGenericExceptionCaught")` on the existing import-boundary catch stays; do not add new broad catches.
- Do not log pin/comment values (PII/secret) - only counts.
