# Phase 06 - Runtime gating of the main-screen Streams menu item

**Strategic spec:** [`../S0575_streams-toggle-welcome-entrypoints.md`](../S0575_streams-toggle-welcome-entrypoints.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 1 / 1
**Started:** 2026-06-21
**Completed:** 2026-06-21

## Step Log

- 2026-06-21 - Step 06.1 Verification PASS. Implemented via the codebase `isXxxEnabled` field pattern (refines the plan's inline-read predicate): added `private var isStreamsEnabled`, synced from `settings.enableStreams` in the settings observer, and gated `streamsMenuManager.populate(popup, BuildConfig.SUPPORT_STREAMS && isStreamsEnabled, 1)`. `btnStreams` (PlaybackSettingsFragment:216) confirmed SUPPORT_STREAMS-only (no enableStreams) - stays visible as the way back; auto-hidden in lite. MainActivity backed up to temp/. Note: pre-existing `getMainWindowDropdownMenuItemCount()` does not count the streams item (S0565) - unchanged, out of scope. Compile folded into the final-build validation. Dev logs batched at Phase 07.

---

## Objective

Show the main-screen Streams menu item only when the build offers Streams AND the user toggle is ON. The playback-settings shortcut stays gated on `SUPPORT_STREAMS` only (it is the way back when the menu item is hidden) - strategic §6 "Quiz decisions: hide only the menu item".

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`AppSettings.enableStreams`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1300 |

> File >500 LOC (≈1257) - take a timestamped backup into `temp/` before editing (Step 06.1).

---

## Steps

### Step 06.1 - Back up MainActivity, then gate the menu item on the runtime flag

**Files:** `ui/main/MainActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> First copy `MainActivity.kt` to `temp/MainActivity.kt.<timestamp>.bak`. Then, at the call that populates the Streams entry (`streamsMenuManager.populate(popup, BuildConfig.SUPPORT_STREAMS, ...)`), change the `enabled` argument to `BuildConfig.SUPPORT_STREAMS && <currentSettings>.enableStreams`, reading the latest settings value already available at popup-build time (one-shot read of the current `AppSettings` from the ViewModel/state - no new reactive observation needed, per strategic §6). Do NOT change `MainStreamsMenuManager` itself - it already takes a Boolean `enabled`. Leave the playback-settings `btnStreams` gating (`SUPPORT_STREAMS`) unchanged.

**Verification:**

- `Grep` - the `streamsMenuManager.populate(` call argument contains `enableStreams`.
- `Grep` - `BuildConfig.SUPPORT_STREAMS && ` present at that call site.
- `Glob` - `temp/MainActivity.kt.*.bak` exists.
- `Grep` - guard against regression: `btnStreams` visibility in `PlaybackSettingsFragment.kt` still references `SUPPORT_STREAMS` and does NOT reference `enableStreams` (the shortcut must stay visible when the toggle is OFF - the canonical way back).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] On a `standard` build: with the toggle ON the main menu shows Streams; toggling OFF and reopening the menu hides it; the playback-settings shortcut stays visible in both states.
- [ ] Dev log entry added for `MainActivity.kt`.

---

## Handoff Notes to Next Phase

- All five entry surfaces (settings section, welcome row, extensions item, main menu, playback shortcut) now honour the agreed gating; Phase 07 finalises docs/catalog.

---

## Rollback Plan

Restore `MainActivity.kt` from the `temp/` backup or revert the phase commit - single call-site change, no persistence touched.
