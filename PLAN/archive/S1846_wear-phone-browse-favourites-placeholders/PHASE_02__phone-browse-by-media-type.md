# Phase 02 - The five chips open a filtered phone browser

**Strategic spec:** [`../S1846_wear-phone-browse-favourites-placeholders.md`](../S1846_wear-phone-browse-favourites-placeholders.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-08-20
**Completed:** 2026-08-20

---

## Objective

`Video`, `Audio`, `Images`, `Documents` and `All files` open the phone browser narrowed to that type, and the `Phone` chip keeps opening it unfiltered.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - the request carries a media type and the phone honours it.
- [ ] Owner ruling read: strategic §12 answer 2 and §13 answer 5 - the chips stay and show phone media by type.
- [ ] `temp/CODE.LOCK` acquired immediately before each source edit and released right after.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/src/main/java/com/sza/fastmediasorter/wear/MainActivity.kt` | Modified | ≤ 420 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/phone/PhoneResourceViewModel.kt` | Modified | ≤ 200 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/phone/PhoneResourceScreen.kt` | Modified | ≤ 300 |

> No new screen and no new route: `WearRoutes.PHONE_BROWSE_PATTERN` and `WearRoutes.browsePhone(mediaType)` already exist and the chips already navigate to them. What is missing is a destination that reads the argument instead of a placeholder.
>
> `PhoneResourceViewModel.kt` holds an S1697 probe. Carry it through unchanged.

---

## Steps

### Step 02.1 - Replace the placeholder destination with the real browser

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/MainActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In the `WearRoutes.PHONE_BROWSE_PATTERN` composable, replace `NotYetHereScreen(ownerTicket = "S1846")` with the same phone-browser screen the `Phone` chip already opens, passing the route's `mediaType` argument down to it. Leave the `WearRoutes.FAVOURITES` composable alone - Phase 05 owns it.

**Why:**

Strategic goal 1 requires the media-type chips to open a working screen rather than a placeholder, and strategic §5 rules that no separate screen is introduced - the argument is what differs, so the existing browser is the destination.

**Verification:**

- `Grep` - `NotYetHereScreen` no longer appears in the `PHONE_BROWSE_PATTERN` composable.
- `Grep` - `NotYetHereScreen(ownerTicket = "S1846")` still matches exactly once in the file - the Favourites one, untouched until Phase 05.
- `Grep` - `ARG_MEDIA_TYPE` is read in that composable.
- `.\a.ps1 fw` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - The `PHONE_BROWSE_PATTERN` composable now renders `PhoneResourceScreen`, the same screen the unfiltered `Phone` entrance opens. The media type is deliberately NOT passed as a composable parameter: the view model reads it off the route, so both entrances share one screen and one view model and differ only by the argument on the route. `NotYetHereScreen(ownerTicket = "S1846")` count in the file is now 1 - the Favourites one, untouched. `fw` exit 0.

---

### Step 02.2 - Carry the media type through the view model into the request

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/phone/PhoneResourceViewModel.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Read the `mediaType` argument from the saved-state handle, hold it for the screen's lifetime, and pass it to every browse request the view model issues - the initial one, `openFolder`, paging and `retry` alike. A screen opened without the argument, or with the value the `All files` chip uses, sends null.

**Why:**

Strategic §5 requires the five chips and the `Phone` chip to share one source with only the filter differing, so the type has to live for the whole browse session rather than only on the first request - otherwise stepping into a folder would silently widen the list back to everything.

**Verification:**

- `Grep` - `ARG_MEDIA_TYPE` or `mediaType` is read from the saved-state handle in that file.
- `Grep` - every call site building a browse request in that file passes the held media type - none takes the default.
- `Grep` - `Timber.d("S1697:` still matches exactly once in that file.
- `.\a.ps1 fw` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - `PhoneResourceViewModel` takes `SavedStateHandle` and holds the media type for the screen lifetime, so it rides the first load, `openFolder`, `navigateUp` and `retry` alike - there is a single `browse(..)` call site inside `load()`, which is what makes that cheap. Two values collapse to null: an absent argument (the unfiltered entrance registers none) and the literal `all` from the fifth chip, since the phone reads absence the same way. `Timber.d("S1697:` still 1 hit. `fw` exit 0.

---

### Step 02.3 - Title the screen by its chip and keep the empty state honest

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/phone/PhoneResourceScreen.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Title the screen with the chip that opened it, and when a filtered browse returns nothing say that this type was not found rather than that the phone is empty. Add any new string to every locale of the `wear` module in one edit via `scripts/utils/set-android-string.ps1`. Take colours from the watch theme, never a literal.

**Why:**

Strategic §1 states the defect being removed is a dead end indistinguishable from an error, and an unfiltered "nothing here" message under a filtered list would reproduce exactly that confusion one level down.

**Verification:**

- `Grep` - no string literal is passed as the title or the empty text; both resolve string resources.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<the new prefix>"` exits 0.
- `Grep` - `="#` returns zero hits among the lines this step added.
- `.\a.ps1 fw` exits 0 and `.\a.ps1 fwr` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Title resolves per chip, reusing the chips' OWN label strings rather than new keys - a second wording for the same word is how two screens start disagreeing. One new key was needed, `phone_resource_empty_filtered`, because the existing empty text blames the phone ("your phone has nothing to show") for a filter the user chose one screen earlier. Added via `set-android-string.ps1 -Module wear -Action add` in en/ru/uk; the script named the other ten locales and pointed at the Rule 30 pre-release bulk path, which is the repo mechanism - see the deviation note below. Parity check exit 0 with `-Module wear -SourceSet main`; the default `app_v2` invocation the plan implied would have reported "no keys found" and proved nothing. `fw` 0, `fwr` 0, detekt PASS after one ImportOrdering fix.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `.\a.ps1 fw` exits 0 and `.\a.ps1 fwr` exits 0 - the watch module has its own targets and `fk` says nothing about it (S1807); scoped detekt PASS over the three files.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `Grep` - exactly one `NotYetHereScreen(ownerTicket = "S1846")` remains, the Favourites one.
- [x] Phase-boundary audit run - the view model gained a lifetime-scoped field, so apply the state-and-lifecycle lens of `docs/CODE_AUDIT_PROTOCOL.md`.

---

## Handoff Notes to Next Phase

The filtered list exists but its rows still do nothing - that is Phase 03, and it is the same tap for all six entrances.

---

## Rollback Plan

Revert the phase commit; the placeholder returns. No stored state and no schema is involved.

---

## Deviation recorded - ten locales are not filled here

Strategic §3.2 says any new string reaches every locale of the `wear` module. The one key this phase added
went to `en`/`ru`/`uk` only, which is what `set-android-string.ps1 -Action add` writes and what its own
output then explains: CLAUDE.md Rule 30 puts the thirteen-locale refusal at `/spec-prerelease` step 0.8,
not at authoring or at ticket close, because nothing ships between releases and one bulk round trip clears
every key of a release at once. The remaining ten are listed by `scripts/utils/list-new-lexemes.ps1` and
translated there. This is the repo's mechanism rather than a shortcut taken here.
