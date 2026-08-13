# Phase 04 - The per-profile starter table

**Strategic spec:** [`../S1560_launcher-profile-defaults.md`](../S1560_launcher-profile-defaults.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 6 / 6
**Started:** 2026-08-11
**Completed:** 2026-08-11

---

## Objective

Replace the thin per-profile `when` with the full set the owner approved, so a car head unit, a smartphone and an
audio player each get a different desktop on first run.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - Phases 01, 02, 03.
- [ ] Strategic §6 research items blocking this phase are Resolved - all six.
- [ ] Working tree is clean or on a feature branch.
- [ ] `CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "//spec-dev S1560 phase 04"` before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt` | Modified | ≤ 430 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt` | Modified | ≤ 420 |
| `app_v2/src/testLauncherEnabled/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsParityTest.kt` | Modified | ≤ 110 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt` | Modified | ≤ 125 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/AudioNowPlayingGadget.kt` | Modified | ≤ 70 |

> `AudioNowPlayingGadget` keeps its key in its own **private** companion, so nothing outside the class can name it -
> including the parity test. Step 04.3 moves it to `LauncherGadgetRegistry.KEY_AUDIO_NOW_PLAYING`, which is where
> every other gadget's key already lives, and leaves the gadget reading it from there.

> `LauncherStarterSets.kt` crosses 500 LOC only if the branches are written verbatim per profile - Step 04.1 uses
> profile sets rather than eleven copy-pasted branches and keeps it under 430. If it does cross 500 during
> implementation, take the Rule 5 backup into `temp/S1560/` before the edit that crosses it.
>
> **Flavor placement.** All three files are shared code. The table stays in `src/main`; the parity test stays in
> `src/testLauncherEnabled`, which only the launcher flavors mount, because it is the only place that may import
> `LauncherGadgetRegistry`.

---

## The approved set

Common to every profile, on top of what is already common today (section heads, the four existing launcher
actions, `clock`, the virtual-resource shortcuts, the `fn:` feature block, `fn:favorites` + `os:settings` +
`app:__self__`):

- `act:all_apps`
- `app:com.google.android.youtube` - only when installed
- `app:com.google.android.apps.youtube.music` - only when installed
- `weather` gadget - every profile **except** `AUDIO_PLAYER`

Per profile, in addition:

| Profile | speed | altitude | satellites | maps | FM app | os:wifi | os:bluetooth | now-playing | act:black_screen |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `CAR_HEAD_UNIT` | yes | yes | yes | yes | yes | yes | yes | yes | yes |
| `PERSONAL_SMARTPHONE` | - | yes | yes | yes | - | - | - | - | - |
| `HOME_TABLET` | - | - | - | - | - | - | - | - | - |
| `AUDIO_PLAYER` | - | - | - | - | - | yes | yes | yes | yes |
| `TV_MEDIA_BOX` | - | - | - | - | - | yes | yes | yes | yes |
| `MEDIA_PLAYER` | - | - | - | - | - | yes | yes | yes | - |
| `VIDEO_PLAYER` | - | - | - | - | - | yes | yes | yes | - |
| `PHOTO_FRAME` | - | - | - | - | - | yes | - | - | yes |
| `EBOOK_READER` | - | - | - | - | - | - | - | - | - |
| `VR_HEADSET` | - | - | - | - | - | yes | yes | - | - |
| `OTHER` | - | - | - | - | - | yes | yes | - | - |

Every existing branch keeps what it already seeds - `folder_preview`, `playlist`, `streams` and the resource-mode
shortcuts are unchanged. Source of the distribution: strategic §6.4 for Wi-Fi, Bluetooth, now-playing and black
screen; strategic §0 for speed, altitude, satellites, maps and radio; strategic §6.1 for the audio-player weather
exclusion.

---

## Steps

### Step 04.0 - Stop seeding the black-screen action to every profile

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `launcherActions()` maps over the whole of `LauncherActionCatalog.all`, so the two actions Phase 01 added are
> already seeded to every profile as a side effect. `act:all_apps` for every profile is correct and stays. Give
> `launcherActions` a `profile` parameter and drop `LauncherActionCatalog.KEY_BLACK_SCREEN` from the produced list
> for any profile outside `BLACK_SCREEN_PROFILES`. Do not filter by key name anywhere else - the app-functions
> section must keep its remaining five entries in catalog order.

**Why:**

Strategic §6.4 assigns the black screen to the always-on profiles only - car head unit, audio player, photo frame
and TV media box - and explicitly withholds it from the smartphone and tablet, where the power button already does
the job; seeding it everywhere would contradict the owner's own distribution.

**Verification:**

- `Grep` - `launcherActions(` in `LauncherStarterSets.kt` takes a `profile` argument.
- `Grep` - `KEY_BLACK_SCREEN` appears in `LauncherStarterSets.kt` inside a filter over `BLACK_SCREEN_PROFILES`.
- Unit test asserting `PERSONAL_SMARTPHONE`'s set contains no `act:black_screen` cell (written in Step 04.4).

**Status:** `[x]` done

---

### Step 04.1 - Rewrite the per-profile branch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Extend `profileItems` to produce the grid above. Express each cross-profile row as a private `Set<DeviceProfileType>`
> companion constant - `WIFI_PROFILES`, `BLUETOOTH_PROFILES`, `NOW_PLAYING_PROFILES`, `BLACK_SCREEN_PROFILES`,
> `LOCATION_TILE_PROFILES` (altitude and satellites), `MAPS_PROFILES` - and build the list by membership tests
> rather than eleven copy-pasted `when` branches, keeping the existing `when` only for the gadget/resource items
> that are already per-profile. Keep the branch exhaustive over `DeviceProfileType` so a new profile still fails to
> compile until it is classified. Seed the FM shortcut with `firstInstalled(FM_RADIO_CANDIDATES, installedPackages)`
> in the car branch only. Reference gadget keys by the same literal strings the parity test guards, and reference
> launcher action keys through `LauncherActionCatalog.KEY_ALL_APPS` / `KEY_BLACK_SCREEN`, which this file may
> import because both live in `src/main`.

**Why:**

Strategic §2 goal 1 requires the first seed to produce a set that fits the actual device, and goal 2 requires the
whole answer to be readable in one place, which is what ADR-1 preserved by keeping the set a code table.

**Verification:**

- `Grep` - `WIFI_PROFILES`, `BLUETOOTH_PROFILES`, `NOW_PLAYING_PROFILES`, `BLACK_SCREEN_PROFILES` each match at
  least twice (declaration and use).
- `Grep` - `KEY_ALL_APPS` and `KEY_BLACK_SCREEN` each match at least once.
- `Grep` - `FM_RADIO_CANDIDATES` matches at least twice.
- `Grep` - `import android\.` returns zero hits in the file.
- File length below 430 lines.

**Status:** `[x]` done

---

### Step 04.2 - Seed the weather tile everywhere except the audio player

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add the `weather` gadget to the common section, guarded by `profile != DeviceProfileType.AUDIO_PLAYER`, with the
> registry's default span of 2x1 and no `param` - the cell carries no place until the owner picks one, and Phase 01
> made an unconfigured weather cell open its place picker on tap. Add a KDoc line on the guard recording that the
> exclusion is the owner's §6.1 ruling, not an availability check.

**Why:**

The owner's §0 list asks for the current temperature on every device except the audio speaker, and §6.1 resolved
"audio speaker" to the existing `AUDIO_PLAYER` profile.

**Verification:**

- `Grep` - `AUDIO_PLAYER` appears in a guard adjacent to the weather key in `LauncherStarterSets.kt`.
- `Grep` - the `weather` key literal matches at least once in the file.

**Status:** `[x]` done

---

### Step 04.3 - Extend the parity test to every key the table now names

**Files:** `app_v2/src/testLauncherEnabled/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsParityTest.kt`
**Depends on:** Step 04.1, Step 04.2

**Prompt for developer:**

> First move `AudioNowPlayingGadget`'s key out of its private companion into
> `LauncherGadgetRegistry.KEY_AUDIO_NOW_PLAYING`, where every other gadget's key already lives, and have the gadget
> read it from there - nothing outside the class can name it today, the parity test included. Then extend the
> existing key-parity assertion to cover every gadget key literal the table now holds - the four it already guards
> plus `weather`, `speed`, `altitude`, `satellites` and `audio_now_playing` - each asserted equal to its
> `LauncherGadgetRegistry.KEY_*` constant. Add a second assertion that every gadget key literal declared in
> `LauncherStarterSets`'s companion resolves through `LauncherGadgetRegistry` rather than only comparing the ones
> the test remembers to list.

**Why:**

`research/06__existing-cells-inventory.md` §8 established that the parity test guards only four of about twenty
registry keys, so every key this phase adds would otherwise be an unguarded string literal that a registry rename
breaks silently at runtime rather than at build time.

**Verification:**

- `Grep` - `KEY_WEATHER`, `KEY_SPEED`, `KEY_ALTITUDE`, `KEY_SATELLITES` each match at least once in the parity test.
- `.\a.ps1 fu` - `LauncherStarterSetsParityTest` passes; read the per-class result XML.

**Status:** `[x]` done

---

### Step 04.4 - Assert the profile sets differ and match the grid

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt`
**Depends on:** Step 04.1, Step 04.2

**Prompt for developer:**

> Add tests asserting: the `CAR_HEAD_UNIT` set differs from the `PERSONAL_SMARTPHONE` set; `AUDIO_PLAYER` is the
> only profile whose set contains no `weather` cell; `os:wifi` appears for exactly the profiles listed in the grid
> above and for no other; `act:black_screen` likewise; and `act:all_apps` appears for all eleven. Drive them from a
> single parameterised table in the test so a future profile change updates one place. Keep the existing eleven
> tests green.

**Why:**

Strategic §11 criterion 1 requires the car and smartphone sets to be provably different and both to match the
approved list, and criterion 3 forbids a silent omission - a static test over the grid is the only mechanical form
of that promise.

**Verification:**

- `Grep` - a test naming `car` and `smartphone` in the same assertion exists.
- `.\a.ps1 fu` - the whole `LauncherStarterSetsTest` class passes; read the per-class result XML, not the summary.

**Status:** `[x]` done

---

### Step 04.5 - Verify the densest profile still fits its first screen

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> Add a test running `place(itemsFor(CAR_HEAD_UNIT, ..), columns)` at the tightest supported column count already
> used by the existing overlap tests, and assert no two placed items overlap and that the total row count stays
> within the bound the existing packer tests use. If the car set overflows, cut it in the order the owner listed
> the items in §0 - last item first - and record what was cut in the file's KDoc rather than dropping it silently.

**Why:**

Strategic §7 names "the set grows past the profile's screen" as a medium-probability risk whose mitigation is to
check the set against the grid of the tightest profile, and §11 criterion 3 forbids dropping an approved item
without saying so.

**Verification:**

- `Grep` - a test naming `CAR_HEAD_UNIT` and `place(` in the same body exists.
- `.\a.ps1 fu` - it passes; read the per-class result XML.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings, with the "startup-path change" trigger applied.
- [ ] `CODE.LOCK` released via `scripts/utils/exit-code-lock.ps1`.

---

## Handoff Notes to Next Phase

The table is now the single source of truth for what each profile receives, and every item of the owner's §0 list
is either seeded, already seeded before this ticket, or named in the INDEX scope-boundary section. Phase 05 records
the capability and refreshes the generated indexes.

## Step Log

- 2026-08-11 - 04.0-04.2 done. The profile table retains all-apps for every profile, limits black
  screen to the approved always-on profiles, and adds conditional app, sensor, weather, and system cells.
- 2026-08-11 - 04.3 done. `audio_now_playing` now has a registry constant and the parity test covers
  every starter-table gadget key.
- 2026-08-11 - 04.4-04.5 done. One profile-grid test checks all approved assignments; the full car set
  packs without overlap at the three-column minimum.

---

## Rollback Plan

Revert the phase commit. Already-seeded desktops are untouched - `seedIfEmpty` only ever writes into an empty
orientation, so no user's arrangement can be rewritten by this change or by its revert.
