# 03 - Reaching the engine that is already playing the channel

Resolves strategic §6 item 3 ("Чтение у играющего радио").

## Findings in the current code

- Video: `PlayerActivity` owns `_videoPlayerManager`, and `VideoPlayerManager.getPlayer(): ExoPlayer?` is already used from a dialog-facing helper (`PlayerDialogAndUiStateManager.showFileInfo()`). The video case needs no new plumbing.
- Radio: `StreamInlineAudioManager` keeps its own `ExoPlayer` for the in-app path and exposes only `activeServicePlayer: Player?`, and only while the background service path is in use (`StreamInlineAudioManager.kt:166-172`). The in-app player is private, so the currently playing radio channel is unreachable from outside today.

## Decision

Add a single read-only accessor on the inline audio manager that returns the engine currently playing, whichever path owns it (in-app player or service player), and `null` when nothing is playing. Nothing else about that class changes: no lifecycle, no ownership, no new state.

The accessor is the whole radio-side change. Strategic §2 goal 5 and §11 criterion 6 require that a channel already playing is read rather than reopened, and without this accessor the card menu would open a second connection to a station that is playing two centimetres above the menu.

## Rejected alternative

A shared "currently playing stream" state holder readable by both players. It is the cleaner long-term shape, but it moves ownership of playback state out of two working classes for a feature that only reads, which is scope this ticket's non-goals exclude. The accessor keeps the change proportional; the state holder belongs to S1143, which rebuilds the radio path anyway.
