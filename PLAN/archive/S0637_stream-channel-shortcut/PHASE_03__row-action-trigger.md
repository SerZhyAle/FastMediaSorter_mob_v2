# Phase 03 - Row action trigger + strings

**Strategic spec:** [`../S0637_stream-channel-shortcut.md`](../S0637_stream-channel-shortcut.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-23
**Completed:** 2026-06-23

> **§6 #4 resolved (2026-06-23):** action placement is an overflow `⋮` button in the row. Menu items: Add to home screen, Remove. Long-press-removes and the pin button stay unchanged.

---

## Objective

Expose the "Add to home screen" action via an overflow `⋮` button on a stream row and wire it to `StreamShortcutPinManager`, with the supported/unsupported result surfaced as a toast. Add the user-visible strings.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`StreamShortcutPinManager.requestPin` available).
- [ ] §6 #4 is Resolved (it is - overflow `⋮` button). The row gains an overflow button next to the existing pin button; tapping it opens a `PopupMenu` with Add to home screen and Remove.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/item_stream_source.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamSourceAdapter.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 450 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> **Landscape parity:** `app_v2/src/main/res/layout-land/item_stream_source.xml` does not exist - this RecyclerView row uses one layout for both orientations (width `match_parent` adapts), so no landscape counterpart is needed. Do not create one.

---

## Steps

### Step 03.1 - Add an overflow button to the row and its menu

**Files:** `app_v2/src/main/res/layout/item_stream_source.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamSourceAdapter.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `item_stream_source.xml`, add an `ImageButton` `@+id/btnOverflow` after `@+id/btnPin` (48dp, `src="@drawable/ic_more_vert"`, `tint="?attr/colorControlNormal"`, `background="?attr/selectableItemBackgroundBorderless"`, `focusable="true"`, `contentDescription="@string/streams_more_actions"`). Do not hardcode colours - use `?attr` (Rule 19). Add an `onAddShortcut: (StreamSourceEntity) -> Unit` constructor callback to `StreamSourceAdapter`; in `bind`, set `binding.btnOverflow.setOnClickListener` to show a `PopupMenu` anchored on `btnOverflow` with two items - Add to home screen → `onAddShortcut(source)`, Remove → `onRemove(source)`. Leave the existing long-press-removes and the pin button as they are. In `StreamsActivity`, pass `onAddShortcut = ::onAddShortcut` when constructing the adapter.

**Verification:**

- `Grep` - `btnOverflow` present in `item_stream_source.xml` and `StreamSourceAdapter.kt`.
- `Grep` - `onAddShortcut` present in `StreamSourceAdapter` constructor and in `StreamsActivity` adapter construction.
- `Grep` - `PopupMenu` present in `StreamSourceAdapter.kt`.
- `Grep -nE "=\"#|Log\.d\(" ` across the touched files returns zero hits (no hardcoded hex, Timber only).

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - Verification 4/4 PASS. Files: res/layout/item_stream_source.xml (btnOverflow), StreamSourceAdapter.kt (onAddShortcut callback + PopupMenu), StreamsActivity.kt (adapter wiring). No hex, Timber-only. Dev log batched to finalization.

---

### Step 03.2 - Wire the action to the pin manager with result toast

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a private `onAddShortcut(source: StreamSourceEntity)` in `StreamsActivity` that calls `StreamShortcutPinManager(this).requestPin(source)` and shows a toast: `R.string.streams_shortcut_created` when it returns true, `R.string.streams_shortcut_unsupported` when false. Construct the manager at the call site (no Hilt). No business logic beyond delegation (Rule 3/5).

**Verification:**

- `Grep` - `StreamShortcutPinManager(this)` (or an injected instance) referenced in `StreamsActivity.kt`.
- `Grep` - both `streams_shortcut_created` and `streams_shortcut_unsupported` referenced in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - Verification 2/2 PASS. Files: ui/streams/StreamsActivity.kt (+7 LOC: onAddShortcut). Dev log batched to finalization.

---

### Step 03.3 - Add the action and result strings (EN/RU/UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add four keys in lockstep via `scripts/utils/set-android-string.ps1 -Action add` (one call per key, `-En -Ru -Uk`): `streams_add_to_home_screen` (EN "Add to home screen" / RU "На домашний экран" / UK "На домашній екран"), `streams_shortcut_created` (EN "Shortcut added to home screen" / RU "Ярлык добавлен на домашний экран" / UK "Ярлик додано на домашній екран"), `streams_shortcut_unsupported` (EN "Your launcher does not support home-screen shortcuts" / RU "Ваш лаунчер не поддерживает ярлыки на домашнем экране" / UK "Ваш лаунчер не підтримує ярлики на домашньому екрані"), `streams_more_actions` (overflow button content description; EN "More actions" / RU "Ещё" / UK "Ще"). All must follow `docs/COMMUNICATION_POLICY.md` §2 and pass the §6 tone checklist.

**Verification:**

- `Grep` - each of the four keys present in all three `strings.xml` files.
- Strings pass COMMUNICATION_POLICY §6 checklist.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix streams_shortcut` exits 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix streams_add_to_home_screen` exits 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix streams_more_actions` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - Verification PASS (4 keys x EN/RU/UK present, 3 parity gates OK, Cyrillic intact). Files: res/values{,-ru,-uk}/strings.xml (+4 keys). Comm-policy §6 OK. Dev log batched to finalization.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- End-to-end path is live: row long-press → Add to home screen → pinned shortcut → tap → `StreamsActivity` plays the channel. Phase 04 only does catalog regen, dev log, and FEATURES/ALL_FEATURES.

---

## Rollback Plan

Revert phase commit(s). The Phase 01/02 backend remains compiled but unreachable (no trigger) - non-breaking, no data change.
