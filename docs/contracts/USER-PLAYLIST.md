<sub class="doc-stamp">26.11.25 02:51</sub>

# Pointer - `USER-PLAYLIST`

| | |
| --- | --- |
| **Id** | `USER-PLAYLIST` |
| **Version** | 0.14, draft. Owner: StreamsPlayer |
| **Home** | `user-playlist/README.md` in the shared contracts catalog |
| **Role here** | consumer (imports user-curated streams and playlists) |

## What this repository must do to stay conformant

- Parse `.m3u` / `.m3u8` playlists and extract stream entries (`url`, `title`, `#EXTINF` tags); a title
  is the text after the first comma outside double quotes, trimmed (section 8 item 2).
- A `.m3u8` URL inside a playlist is an ordinary entry; a file is an HLS manifest only when a line of it
  begins with `#EXT-X-` (rule 3, section 9).
- Bound the input: an oversized playlist is refused whole and imports nothing (section 8 item 1).
- A network import refuses, before reading the body, a response carrying `icy-*` headers or a
  non-playlist `audio/*` / `video/*` content type (section 8 item 3).
- Refuse the JSON carrier whole (section 6 item A): a `.json` URL or a body opening with `{` / `[` imports nothing and shows its own message (`ImportResult.UnsupportedFormat`). That is the one refusal of 0.13 item 2, which the contract names `JsonCarrierUnread`: it is decided from the name before the body is read as a playlist, imports 0 entries and writes nothing.
- Preserve local user customizations during import - never delete existing channels.
- Classify imported stream URLs into audio / video using `StreamMediaKindClassifier`.
- Deduplicate on insert by normalized stream URL (`addAllIgnoringDuplicates`). The key is the exact, case-sensitive match of 0.13 item 1: `StreamUrlNormalizer` folds only scheme and host case, the default port and a trailing slash, never `http` against `https`, so `duplicate` counts exact matches and this product reports no `folded` count.

## Where it lives here

- `app_v2/.../data/repository/M3uPlaylistParser.kt`.
- `app_v2/.../domain/usecase/streams/ImportStreamPlaylistUseCase.kt`.
- `app_v2/.../domain/usecase/streams/StreamMediaKindClassifier.kt`.
- Conformance: `M3uPlaylistParserTest` on the vendored `.m3u8` vectors (`app_v2/src/test/resources/user-playlist/`, sha256 in `PROVENANCE.txt`; re-vendor on a catalog vector change).
