# Phase 01 - Schema v2 DTO + parser + profile preset extraction

**Goal:** carry per-root resource params in the DTO, accept schemaVersion 1..2, soft-validate new tokens, expose one reusable profile->media preset.

## Steps

1. [ ] Extract a reusable profile preset in `domain/model/Models.kt` (next to `ResourceProfile`):
   - Add `data class ProfileMediaPreset(val supportedMediaTypes: Set<MediaType>?, val allFiles: Boolean, val rememberFileList: Boolean)`.
   - Add `fun ResourceProfile.mediaPreset(): ProfileMediaPreset` returning exactly the sets used today in `ResourceFormData.applyProfile` (AUDIO_LIBRARY -> {AUDIO}, rememberFileList=true; VIDEO_LIBRARY -> {VIDEO,AUDIO}; PHOTO_STORAGE -> {IMAGE,GIF}; DOCUMENTS -> {TEXT,PDF,EPUB,OFFICE_DOCUMENT}; ALL_FILES -> supportedMediaTypes=null, allFiles=true; NONE -> supportedMediaTypes=null, allFiles=false). `supportedMediaTypes=null` means "leave caller default".
   - Verification: `Grep` shows `fun ResourceProfile.mediaPreset` in Models.kt.
2. [ ] Refactor `domain/model/ResourceFormData.kt` `applyProfile` to delegate to `mediaPreset()` so the mapping has one source of truth (behavior byte-identical: NONE stays no-op returning `this`).
   - Verification: `.\a.ps1 fk` compiles; the four non-NONE/ALL branches produce the same sets.
3. [ ] Extend `data/companion/CompanionConfigDto.kt` `CompanionRootDto` with optional fields AFTER `label` (all nullable default null): `profile: String?`, `mediaTypes: List<String>?`, `scanSubdirectories: Boolean?`, `showSubfoldersAsItems: Boolean?`, `showHiddenFiles: Boolean?`, `allFiles: Boolean?`, `isDestination: Boolean?`, `destinationColor: Int?`, `comment: String?`, `accessPin: String?`, `slideshowInterval: Int?`. Each with `@SerializedName`.
   - Verification: positional `CompanionRootDto("/Photos", "Photos")` in tests still compiles.
4. [ ] Add token<->enum maps in `data/companion` (new small file `CompanionResourceTokens.kt` or companion object): profile tokens `none|audio_library|video_library|photo_storage|documents|all_files` <-> `ResourceProfile`; mediaType tokens `image|video|audio|gif|text|pdf|epub|office` <-> `MediaType`. Unknown token -> null (soft).
   - Verification: `Grep` shows both maps; unknown token returns null.
5. [ ] `CompanionConfigParser`: raise `SUPPORTED_SCHEMA_VERSION = 2`; `validate()` accepts `schemaVersion in 1..2` (keep the `> SUPPORTED` reject for 3+, keep `< 1` invalid). Do NOT hard-reject unknown profile/mediaType tokens (soft: import maps them to defaults). Keep all v1 checks (protocol, accessPaths, username, roots.virtualPath).
   - Verification: existing `rejects unknown higher schemaVersion` test still passes when bumped to 99 (adjust test in Phase 04 to use 3).

## Notes

- Keep log/probe lines <=120 chars, no bare numeric literals beyond -1/0/1/2 (detekt S0826). `MAX_TCP_PORT` const already exists; add named consts for any new magic numbers.
- No Room change (all target fields already in `ResourceEntity`).
