# Pointer - `USER-PLAYLIST`

| | |
| --- | --- |
| **Id** | `USER-PLAYLIST` |
| **Version** | 0.9, draft. Owner: StreamsPlayer |
| **Home** | `user-playlist/README.md` in the shared contracts catalog |
| **Role here** | consumer (imports user-curated streams and playlists) |

## What this repository must do to stay conformant

- Parse `.m3u` / `.m3u8` playlists and extract stream entries (`url`, `title`, `#EXTINF` tags).
- Preserve local user customizations during import - never delete existing channels.
- Classify imported stream URLs into audio / video using `StreamMediaKindClassifier`.
- Deduplicate on insert by normalized stream URL (`addAllIgnoringDuplicates`).

## Where it lives here

- `app_v2/.../data/repository/M3uPlaylistParser.kt`.
- `app_v2/.../domain/usecase/streams/ImportStreamPlaylistUseCase.kt`.
- `app_v2/.../domain/usecase/streams/StreamMediaKindClassifier.kt`.
