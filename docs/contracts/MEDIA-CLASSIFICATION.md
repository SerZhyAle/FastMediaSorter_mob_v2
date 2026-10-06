<sub class="doc-stamp">26.10.02 21:53</sub>

# Pointer - `MEDIA-CLASSIFICATION`

| | |
| --- | --- |
| **Id** | `MEDIA-CLASSIFICATION` |
| **Version** | 0.10, draft. Owner: this product |
| **Home** | `media-classification/README.md` in the shared contracts catalog |
| **Role here** | owner, producer and consumer (primary file and stream classifier) |

## What this repository must do to stay conformant

- Resolve every file or URL into canonical primary categories: `IMAGE`, `VIDEO`, `AUDIO`, `BOOK`, `STREAM`, `CONTAINER`, `DOCUMENT`, `UNKNOWN`.
- Keep extension tables case-insensitive and lowercase ASCII, stripping trailing dots/spaces/temporary suffixes (`.temp_copy`).
- Recognize sidecar patterns (subtitles, lyrics, covers, metadata) without reclassifying the parent media item.
- Exclude system junk (dot-files, `Thumbs.db`, `desktop.ini`, `*.tmp`, `*.~*`, `*.swp`) before classification; it receives no category, and the enum has no `SYSTEM_IGNORED` value (rule 5).
- Classify by file name or MIME only, never by what the product does with the file (rule 1).
- List `.tiff .ico .wmf .emf` as `IMAGE` and `.3g2 .asf` as `VIDEO`; `.ts` stays `VIDEO` (rule 1, 0.10).
- MIME-first resolution for SAF/cloud providers, falling back to extension matching when MIME is absent or `application/octet-stream`.

## Where it lives here

- `app_v2/.../data/common/MediaCategoryClassifier.kt` (reference classifier: the canonical categories and table, suffix stripping, junk, MIME-first).
- `app_v2/.../data/common/MediaTypeUtils.kt` (gallery routing sets, MIME mappings; drops junk before classification for every scanner).
- `app_v2/.../util/BinaryFileTypeDetector.kt` (archive and disk image extension mappings).
- `app_v2/.../domain/model/Models.kt` (`MediaType` enum).
- `wear/.../wear/domain/model/WearMediaFile.kt` (`MediaType` on watch).
