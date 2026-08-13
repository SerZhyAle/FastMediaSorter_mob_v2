# Research 03 - Behavior when skips exhaust the queue

Strategic §6 item 3.

## Question

What happens if every remaining track is unplayable and the queue ends through skips?

## Findings

- `playAudioPlaylist` sets `Player.REPEAT_MODE_ALL`; `playAudio` (single file) sets `REPEAT_MODE_OFF` with one item.
- With `REPEAT_MODE_ALL`, `seekToNext` from the last item wraps to the first. A naive "skip on error" on an all-bad playlist would loop forever, re-erroring on each item.
- Single-file mode has `mediaItemCount == 1`; there is no real next item (the existing `ForwardingPlayer` only fakes skip availability for the notification).

## Decision

- Maintain a consecutive-skip counter in the service:
  - Increment on each skippable error that triggers a skip.
  - Reset to zero on `STATE_READY` (a track actually started - the queue is making progress).
- Stop condition: when the counter reaches `mediaItemCount` (every item in the queue has failed in a row), stop the service and show one final "nothing to play" message instead of looping. This bounds the loop even under `REPEAT_MODE_ALL`.
- Single file / no next item: keep today's behavior - stop the session (no queue to advance).

This satisfies anti-spam (§3.1): at most the per-window skip toast plus one terminal message.

## Status

Resolved.
