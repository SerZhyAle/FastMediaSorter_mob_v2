# Pointer - `MEDIA-CLASSIFICATION`

| | |
| --- | --- |
| **Id** | `MEDIA-CLASSIFICATION` |
| **Version** | 0.9, draft. Owner: this product |
| **Home** | `media-classification/README.md` in the shared contracts catalog |
| **Role here** | owner, producer and consumer (primary file and stream classifier) |

## What this repository must do to stay conformant

- Resolve every file or URL into canonical primary categories: `IMAGE`, `VIDEO`, `AUDIO`, `BOOK`, `STREAM`, `CONTAINER`, `DOCUMENT`, `UNKNOWN`.
- Keep extension tables case-insensitive and lowercase ASCII, stripping trailing dots/spaces/temporary suffixes (`.temp_copy`).
- Recognize sidecar patterns (subtitles, lyrics, covers, metadata) without reclassifying the parent media item.
- Exclude system junk (`.DS_Store`, `Thumbs.db`, `desktop.ini`, `*.tmp`, `*.~*`) as `SYSTEM_IGNORED`.
- MIME-first resolution for SAF/cloud providers, falling back to extension matching when MIME is absent or `application/octet-stream`.

## Where it lives here

- `app_v2/.../data/common/MediaTypeUtils.kt` (extension sets, MIME mappings, category resolver).
- `app_v2/.../util/BinaryFileTypeDetector.kt` (archive and disk image extension mappings).
- `app_v2/.../domain/model/Models.kt` (`MediaType` enum).
- `wear/.../wear/domain/model/WearMediaFile.kt` (`MediaType` on watch).
