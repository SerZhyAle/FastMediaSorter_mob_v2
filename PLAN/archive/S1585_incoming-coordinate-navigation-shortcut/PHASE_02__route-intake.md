# Phase 02 - Route-ready intake and tap

**Strategic spec:** [`../S1585_incoming-coordinate-navigation-shortcut.md`](../S1585_incoming-coordinate-navigation-shortcut.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** -
**Completed:** -

---

## Objective

Make a shared short link produce a shortcut carrying coordinates, and make tapping a route shortcut
open turn-by-turn navigation instead of a Maps search.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - `MapsShortLinkResolver` is injectable.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/share/LauncherPlaceShareParser.kt` | Modified | ≤ 90 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/share/LauncherPlaceShareManager.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt` | Modified | ≤ 260 |
| `app_v2/src/testLauncherEnabled/java/com/sza/fastmediasorter/ui/launcher/share/LauncherPlaceShareParserTest.kt` | Modified | ≤ 200 |

> All four files are flavor-correct as listed: the share intake lives in the `launcherEnabled` source
> set (Rule 14), the command executor is shared `src/main` code already reached by both launcher
> flavors.

---

## Steps

### Step 02.1 - Recognise a Maps link as a resolvable point

**Files:** `.../ui/launcher/share/LauncherPlaceShareParser.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a parse outcome distinguishing "this share carries a Maps short link that must be resolved"
> from the existing coordinate, free-text and bare-URL cases. Reorder the branches so a recognised
> Maps link is preferred over the free text that accompanies it, while an explicit coordinate in the
> text still wins over both. Keep the parser free of network access and of any dependency on the
> resolver - it reports the shape of the text, nothing more. Update the class KDoc: the current text
> states a short link "resolves to nothing a route can start from", which is the defect this ticket
> fixes.

**Why:**

Strategic §1 identifies the inverted priority as the root cause - the short link is the most precise
identifier of the place and is currently discarded in favour of the imprecise caption - and §5.1
requires the parser to keep reporting only the shape of the text so it stays verifiable without a
device.

**Verification:**

- `Grep` - `UrlInTextDetector` still present in the parser.
- `Grep` - no `HttpURLConnection`, `OkHttp`, or `MapsShortLinkResolver` reference in the parser file.
- `Grep` - `resolves to nothing a route can start from` returns zero hits.

**Status:** `[ ]` not done

---

### Step 02.2 - Resolve the link while placing the shortcut

**Files:** `.../ui/launcher/share/LauncherPlaceShareManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Inject `MapsShortLinkResolver` into `LauncherPlaceShareManager`. When the parser reports a Maps
> link, call the resolver and, on a non-null point, build a `Geographic` command whose action is
> `NAVIGATION` and whose query is the `<lat>,<lon>` pair. On `null`, fall back to the existing
> `SHOW_PLACE` command built from the link, so the share is still placed. Keep the outcome mapping
> intact: a navigation command reports `PLACED_ROUTE`, the fallback reports `PLACED_PLACE`.

**Why:**

Strategic ADR-1 puts the single network call at creation time so the tap never waits on the network,
and strategic §6 item 1 records the owner's decision that a failed resolution degrades to a place
shortcut rather than dropping the share.

**Verification:**

- `Grep` - `MapsShortLinkResolver` present in the manager file.
- `Grep` - `LauncherGeographicAction.NAVIGATION` present in the manager file.
- `Grep` - `PLACED_PLACE` still present in the manager file.
- `.\a.ps1 fk` exits 0.

**Status:** `[ ]` not done

---

### Step 02.3 - Keep the place fallback honest

**Files:** `.../ui/launcher/share/LauncherPlaceShareManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Ensure the label carried by the fallback `SHOW_PLACE` command is the shared caption when one exists
> and the link otherwise, never a caption implying a route. Confirm the existing string shown for
> that outcome, `launcher_share_place_added_place`, is the one reported, and leave the three
> `values*/strings.xml` files unchanged.

**Why:**

Strategic §2 goal 4 requires that a shortcut which could not resolve to a point does not present
itself as a route, since the owner accepted degradation only on the condition that it is not
disguised.

**Verification:**

- `Grep` - `launcher_share_place_added_place` present in the manager or activity.
- `Grep` - `launcher_share_place` returns exactly 4 hits in `app_v2/src/main/res/values/strings.xml` (no new keys).

**Status:** `[ ]` not done

---

### Step 02.4 - Confirm the navigation intent shape

**Files:** `.../domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Verify the `NAVIGATION` branch builds `google.navigation:q=<query>` targeted at the Google Maps
> package, and that it is reachable now that the intake emits that action. Leave `DIRECTIONS` and
> `SHOW_PLACE` behaviour unchanged. Add no new branch - this step confirms and, if needed, corrects
> the existing one rather than introducing a fourth action.

**Why:**

Strategic ADR-2 selects the action that opens Maps ready to start moving, which the owner stated as
"приступить к дороге сразу"; the branch already exists in the executor but was never reachable
because no parser path ever produced `NAVIGATION`.

**Verification:**

- `Grep` - `google.navigation:q=` present exactly once in the executor.
- `Grep` - `GOOGLE_MAPS_PACKAGE` present on the navigation branch.
- `Grep` - `maps/dir/?api=1` still present (DIRECTIONS unchanged).

**Status:** `[ ]` not done

---

### Step 02.5 - Extend parser tests for the new priority

**Files:** `.../testLauncherEnabled/.../LauncherPlaceShareParserTest.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Add cases to the existing test class: a share that is only `https://maps.app.goo.gl/Ep9BAYWhvoDUL7BN6?g_st=atm`
> is reported as a resolvable Maps link; the same link with a trailing query string is still
> recognised; a caption plus a Maps link prefers the link over the caption; an explicit coordinate
> pair in the text still wins over an accompanying link; a share with neither link nor text yields
> null. Keep the existing assertions passing or update them to the new outcome type where the shape
> genuinely changed.

**Why:**

Strategic §11 criterion 5 requires the share-shape decisions to be covered by device-free tests, and
the owner-supplied link is the exact payload that produced the reported defect, so it is the case
most worth pinning.

**Verification:**

- `Grep` - `maps.app.goo.gl` present in the test file.
- `Grep` - `g_st=atm` present in the test file.
- `.\a.ps1 fu` reports this test class passing.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] `Grep` - `Log\.d\(` returns zero hits in every modified file.
- [ ] Dev log entry added for the change via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Tapping a shortcut performs no network access: the point is stored in the cell at creation time.
Cells created before this ticket keep their old encoding and their old behaviour - no migration.

---

## Rollback Plan

Revert phase commit(s). No schema or resource change is involved; cells written by the new intake
remain decodable by the pre-change executor because the `geo:` encoding is unchanged.
