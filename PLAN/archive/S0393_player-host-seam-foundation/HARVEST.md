# S0393 - Legacy `StandalonePlayerActivity` harvest map

**Phase 04 deliverable.** What the legacy host has that the specialized hosts do NOT, so nothing is lost before deprecating it. Source: read-only harvest diff, 2026-06-10.

## Routing (deprecation pre-condition) — CLEAR

All external `VIEW`/`SEND` intent-filters belong to activity-aliases targeting the specialized hosts + dispatcher (`AndroidManifest.xml:438-581`). `StandalonePlayerDispatcherActivity` forwards only to the 4 specialized hosts. `StandalonePlayerActivity` has no intent-filter and is reached only by explicit/fallback launch. → safe to `@Deprecated` once U1-U8 (minus dropped items) are ported.

## Unique capabilities to port

| # | Capability | Legacy impl | Destination | Level | Effort | Decision |
|---|---|---|---|:--:|:--:|---|
| U1 | Picture-in-Picture (auto-enter, PiP button, remote play/pause) | `StandalonePlayerActivity:241-249,407-411,842-847` | PhotoVideo | Activity | L | **Port** - make `PictureInPictureManager` binding-agnostic (Q2). |
| U2 | Playback-control dialog (volume/track/subtitle/stereo/hue/brightness/**speed**) | `:394-405,670-679,798-802` | PhotoVideo (video) ✅; Audio (volume+speed) **deferred** | Activity | S-M | **PhotoVideo done** (wired via PlayerHostCapabilities+VideoPlayerHandle, no dialog change, Q4). **Audio deferred:** the audio lane has no controls surface to trigger the dialog (`videoPlayerHandle=null`); needs a UI entry point first - follow-up, not a legacy-loss (legacy showed it only because it shared the unified controls). |
| U3 | WebView selection ActionMode (Translate / Search-in-Google) | `:160-170` | Document | Activity | M | **Port** - add `startActionMode` override aggregating the active doc-viewer's callback. |
| U4 | Keyboard layer: pdf/epub page keys, text-scroll PAGE_UP/DOWN/HOME/END, seek, document-search | `:265-336` | Audio, Document, Text | Activity | M | **Port** - new per-host keyboard managers (pattern: `PhotoVideoStandaloneKeyboardManager`). |
| U5 | Keyboard: help / mute / volume / fullscreen for all types | `:307-320` | Audio/Document/Text (+ help into PhotoVideo) | Activity | S | **Port** (folded into U4). |
| U6 | Inline find-panel (keyboard find/next/prev/counter) over PDF/Text/EPUB | `:556-573` | Document, Text | Activity | L | **DROP from harvest** (Q1): Document already offers thumbnail-nav (PDF) + cross-chapter search (EPUB) as the accepted replacement; Text inline-find is a separate optional follow-up. Dropping removes the 2 `SearchControlsManager` High risks. |
| U7 | EPUB translator-button orientation guard (hide in portrait) | `:827-831` | Document | Activity | S | **Port**. |
| U8 | Cached translation-settings observer for the EPUB button | `:834-840` | Document | Activity | S | **Port** (folded into U7). |

## Not unique — do NOT port

Intent parse, window/insets, file ops (delete/share/rename/info/favourite/open-in-FMS), delete-permission launchers, video controls + `VideoPlayerHandle`, **`updateAudioMediaItem`** (already in Audio host), PDF/EPUB nav, stereo plumbing, paging/slideshow (specialized hosts are newer here).

## Open-question decisions (2026-06-10)

- **Q1 (U6):** drop inline find-panel from the harvest - Document's thumbnail-nav + cross-chapter search are the accepted replacement; Text inline-find → optional follow-up ticket. Removes the `SearchControlsManager` binding-refactor risk.
- **Q2 (U1 PiP):** make `PictureInPictureManager` binding-agnostic (consume a narrow seam over `playerView` + the command bar) rather than duplicate it - aligns with S0393's single-seam thesis. Effort stays L; sequence PiP last.
- **Q4:** `PlaybackControlDialogFragment` casts `requireActivity() as PlayerHostCapabilities`; PhotoVideo/Audio already implement it → U2 binds with zero dialog change.

## Port order (value × risk)

1. **U2** playback-control dialog (PhotoVideo video) - cheapest high-value.
2. **U7/U8** EPUB translator guard - small.
3. **U4/U5** keyboard for Audio/Document/Text - accessibility, medium.
4. **U3** WebView ActionMode - medium.
5. **U1** PiP - large (binding-agnostic refactor), last.

Legacy `@Deprecated` after U1-U5, U7/U8 land (U6 dropped). No unit-test coverage exists for any standalone host → validate on-device.
