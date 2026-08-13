# Phase 01 - Stream play deep-link entry point

**Strategic spec:** [`../S0637_stream-channel-shortcut.md`](../S0637_stream-channel-shortcut.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Let `StreamsActivity` accept an external "play this stream URL" intent and route it through the existing `onPlay` flow; expose the intent contract (action + extra + factory) that the shortcut helper will target. No shortcut creation and no UI trigger yet.

---

## Prerequisites

- [ ] Strategic §6 #2 and #3 are Resolved (they are - see `research/`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` | Modified | ≤ 320 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | n/a |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> No `layout/` edits in this phase - no landscape parity concern.

---

## Steps

### Step 01.1 - Add the play-stream intent contract to StreamsActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In the `StreamsActivity` companion, add `const val ACTION_PLAY_STREAM = "com.sza.fastmediasorter.action.PLAY_STREAM"` and `const val EXTRA_STREAM_URL = "extra_stream_url"`. Add a factory `fun createPlayShortcutIntent(context: Context, url: String): Intent` that targets `StreamsActivity` explicitly, sets the action, puts the URL extra, and sets `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP`. This is the single intent the home-screen shortcut will carry. Keep it data-only - no playback logic here.

**Verification:**

- `Grep` - `ACTION_PLAY_STREAM` and `EXTRA_STREAM_URL` each match once in the file.
- `Grep` - `fun createPlayShortcutIntent` present with a `Context` and `String` parameter.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - Verification 2/2 PASS. Files: ui/streams/StreamsActivity.kt (+15 LOC: companion with ACTION_PLAY_STREAM, EXTRA_STREAM_URL, createPlayShortcutIntent). Dev log batched to finalization.

---

### Step 01.2 - Resolve URL to a source in the ViewModel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Inject `GetStreamSourceByUrlUseCase` into the `StreamsViewModel` constructor (it already exists under `domain/usecase/streams/`; no new Hilt module - it is constructor-injectable). Add `fun playByUrl(url: String)` that launches in `viewModelScope`, resolves the source via the use case, and emits a new event: extend the existing `sealed interface StreamsEvent` with `data class PlayRequested(val source: StreamSourceEntity) : StreamsEvent` when found, or reuse `StreamsEvent.Message(R.string.streams_shortcut_channel_missing)` when the use case returns null. Do not start playback in the ViewModel - the Activity owns `onPlay`.

**Verification:**

- `Grep` - `getStreamSourceByUrl` (or the injected property name) present in the constructor parameter list.
- `Grep` - `fun playByUrl` present.
- `Grep` - `data class PlayRequested` present in the `StreamsEvent` interface.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - Verification 3/3 PASS. Files: ui/streams/StreamsViewModel.kt (+10 LOC: inject GetStreamSourceByUrlUseCase, playByUrl, StreamsEvent.PlayRequested). Dev log batched to finalization.

---

### Step 01.3 - Consume the incoming intent in StreamsActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`, `app_v2/src/main/AndroidManifest.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `android:launchMode="singleTop"` to the `StreamsActivity` entry in the manifest so an already-open screen receives the shortcut intent via `onNewIntent`. In the Activity, add a private `handlePlayIntent(intent: Intent?)` that reads `EXTRA_STREAM_URL` when the action is `ACTION_PLAY_STREAM` and calls `viewModel.playByUrl(url)`; invoke it from `onCreate` (after view setup) and from an `onNewIntent` override (call `setIntent(intent)` first). In `observeData`, handle `StreamsEvent.PlayRequested` by calling the existing `onPlay(event.source)`. The `Message` path already shows a toast, satisfying the removed-channel case (strategic §6 #5).

**Verification:**

- `Grep` - `launchMode="singleTop"` present on the `StreamsActivity` manifest line.
- `Grep` - `override fun onNewIntent` present in `StreamsActivity.kt`.
- `Grep` - `is StreamsViewModel.StreamsEvent.PlayRequested` handled in `observeData`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - Verification 3/3 PASS. Files: ui/streams/StreamsActivity.kt (+12 LOC: onNewIntent, handlePlayIntent, PlayRequested branch, setupViews call), AndroidManifest.xml (launchMode=singleTop). Dev log batched to finalization.

---

### Step 01.4 - Add the removed-channel message string (EN/RU/UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add one string key `streams_shortcut_channel_missing` across EN/RU/UK in one lockstep call: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key streams_shortcut_channel_missing -En "This stream is no longer in your list" -Ru "Этой трансляции больше нет в списке" -Uk "Цієї трансляції більше немає в списку"`. The message must follow `docs/COMMUNICATION_POLICY.md` §2 (state + plain next step implied by the open list) and pass the §6 tone checklist - no blame, no jargon.

**Verification:**

- `Grep` - `streams_shortcut_channel_missing` present in all three `strings.xml` files.
- Strings pass COMMUNICATION_POLICY §6 checklist.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix streams_shortcut_channel_missing` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - Verification 3/3 PASS (EN/RU/UK present, parity gate exit 0, Cyrillic intact). Files: res/values{,-ru,-uk}/strings.xml (+1 key streams_shortcut_channel_missing). Dev log batched to finalization.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- `StreamsActivity.createPlayShortcutIntent(context, url)` is the canonical intent for the shortcut - Phase 02 must use it, not hand-build an intent.
- `EXTRA_STREAM_URL` carries the channel URL (stable, unique-indexed) - chosen over the row id so a re-imported channel keeps working and the existing `GetStreamSourceByUrlUseCase` is reused.

---

## Rollback Plan

Revert phase commit(s) - no data migration, no user-facing surface changed (the new intent path is unreachable until Phase 03 wires a trigger).
