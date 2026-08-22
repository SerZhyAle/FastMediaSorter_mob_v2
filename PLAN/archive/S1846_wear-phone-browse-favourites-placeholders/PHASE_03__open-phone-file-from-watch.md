# Phase 03 - Tapping a phone file opens it on the watch

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

A file row in the phone browser stops being a no-op: the tap asks the phone to deliver the file and lands in the matching watch player.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Owner ruling read: strategic §13 answer 6 - this is fixed here, not left to S1697.
- [ ] `research/02__phone-browse-and-favourites-as-is.md` §2 read.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/phone/PhoneResourceScreen.kt` | Modified | ≤ 300 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/phone/PhoneResourceViewModel.kt` | Modified | ≤ 200 |

> `PhoneResourceClient.open()` already exists and already speaks the `OPEN` request kind - research artifact 02 §2 records that it simply has no call site. This phase adds the call site; it does not add a transport.
>
> Both files carry S1697 probes. Carry them through unchanged.

---

## Steps

### Step 03.1 - Give the view model an open action

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/phone/PhoneResourceViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an `openFile(entry)` action beside the existing `openFolder` / `navigateUp` / `retry`. It calls `PhoneResourceClient.open()` with the entry's token and turns the answer into one of three outcomes the screen can render: delivered - with what is needed to reach the player, refused - with the response status, or unreachable. Route the failure states through the same failure state the screen already renders for a failed browse; do not add a second error vocabulary.

**Why:**

Research artifact 02 §2 establishes that `PhoneResourceClient.open()` has no call site at all, so the transport half already exists and only the action is missing; strategic goal 4 requires the tap to stop being a no-op.

**Verification:**

- `Grep` - `open(` on the phone-resource client matches at least once in that file - the call site now exists.
- `Grep` - the response statuses `TRANSFER_REJECTED`, `NOT_FOUND` and `PHONE_UNAVAILABLE` are each handled, not swallowed.
- `Grep` - no empty `catch` block was introduced (CLAUDE.md Rule 19).
- `Grep` - `Timber.d("S1697:` still matches exactly once in that file.
- `.\a.ps1 fw` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - `openFile(entry)` added beside `openFolder`/`navigateUp`/`retry`, plus a one-shot `openOutcome` the screen consumes. Four outcomes, none swallowed: `Opening`, `Ready`, `Unsupported` (delivered but no player renders the kind) and `Failed(status?)`. The delivered file lands in a `phone-files` subdirectory of the CACHE dir - a phone file opened on the watch is a convenience copy, not a download the user has to find and delete later. `Timber.d("S1697:` still 1 hit. `fw` exit 0.

---

### Step 03.2 - Ungate the row and the chip

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/phone/PhoneResourceScreen.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Remove the `entry.isDirectory` condition that currently suppresses the click on both the chip and the row, and route a directory to `openFolder` and a file to the new `openFile`. Keep every row focusable and reachable by rotary and D-pad.

**Why:**

Research artifact 02 §2 records that the click is gated on `entry.isDirectory` in two places, which is what makes a file row inert, and strategic §3.2 requires rotary and D-pad to keep working on a navigational screen.

**Verification:**

- `Grep` - `entry.isDirectory` no longer appears as a click condition in that file; it may still appear to choose the destination.
- `Grep` - both the chip and the row have an enabled click path.
- `.\a.ps1 fw` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Both `entry.isDirectory` click gates removed; the two composables now take one `onEntryClick(entry)` and the screen decides folder-vs-file at the single call site. `isDirectory` still selects the ICON, which is what it was legitimately for. `fw` exit 0.

---

### Step 03.3 - Land in the right player

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/phone/PhoneResourceScreen.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> On a delivered file, navigate to the audio, video or image destination that matches its mime type, reusing `WearRoutes.AUDIO_PLAYER_PATTERN`, `VIDEO_PLAYER_PATTERN` and `IMAGE_VIEWER_PATTERN`. A type with no player says so in words and stays on the list. Add no new route.

**Why:**

Strategic goal 4 requires the file to open, and the three player routes already exist for watch-local media, so a fourth entrance would duplicate them; strategic §1 requires that whatever cannot open says so rather than doing nothing.

**Verification:**

- `Grep` - all three of `AUDIO_PLAYER_PATTERN`, `VIDEO_PLAYER_PATTERN`, `IMAGE_VIEWER_PATTERN` are referenced from the delivered-file branch.
- `Grep` - no new `const val .*_PATTERN` was added to `WearRoutes.kt` by this phase.
- `Grep` - the unsupported-type branch resolves a string resource, not a literal.
- `.\a.ps1 fw` exits 0 and `.\a.ps1 fwu` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Navigation runs in a `LaunchedEffect` keyed to the outcome and the outcome is consumed straight after, so a recomposition cannot push the player twice. The route is chosen from the FILE's mime type, not from the chip: a folder under the `all` entrance mixes kinds, and the general browser already picks its player the same way. No new route was added. The hand-off is `SelectedMediaManager` - the same one the general browser uses for a network file, which a phone file resembles in the way that matters: it has no MediaStore row for a player to look up. `fw` 0, `fwr` 0, `fwu` 0.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `.\a.ps1 fw` exits 0, `.\a.ps1 fwr` exits 0, `.\a.ps1 fwu` exits 0; scoped detekt PASS over the three files.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `Grep` - the `Timber.d("S1697:` count in `wear/src` is unchanged from the phase start.
- [x] Phase-boundary audit run - a new suspending round trip was added, so apply the coroutine and lifecycle lens of `docs/CODE_AUDIT_PROTOCOL.md` Layer 3.

---

## Handoff Notes to Next Phase

The watch can open a phone file. Phase 05 opens a watch-local file from the favourites list; the two paths stay separate because their sources differ.

---

## Rollback Plan

Revert the phase commit. The tap returns to a no-op; no stored state changes.

---

## Beyond the written steps - two things the plan did not name

**The existing view-model test broke and was repaired, not deleted.** Widening the constructor made all six
cases in `PhoneResourceViewModelTest` fail to compile - the plan's Files Touched did not list it. Fixed by
stubbing the two new dependencies; the six cases still pass unchanged (`tests="6" failures="0"` read from the
results XML). A constructor change compiles the tests too, and this is the reminder.

**The failure states needed somewhere to be seen.** Step 03.1 said route them through the existing failure
state and step 03.3 said an unsupported type "says so and stays on the list" - but the existing failure state
REPLACES the list, so obeying both literally was impossible. Resolved by a caption line above the list: the
list keeps the user's position, and a per-file problem does not read as a per-folder one. Two strings added
(`phone_resource_opening`, `phone_resource_open_unsupported`) in en/ru/uk, remaining ten locales via the Rule 30
pre-release path. Without this the tap on an undeliverable file would have looked like nothing happened -
the very dead end this ticket removes.
