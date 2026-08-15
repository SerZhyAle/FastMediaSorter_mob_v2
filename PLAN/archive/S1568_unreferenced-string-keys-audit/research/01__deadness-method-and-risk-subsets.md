# S1568 research 01 - is the dead-key count real, and what could still be alive

**Question (strategic §6):** the capture claims 396 of 3224 keys in `app_v2/src/main/res/values/strings.xml`
have no static reference. Does the number hold, what can make a "dead" key secretly alive, and what does
deleting one actually touch?

**Method:** independent re-measurement by a research sub-agent, then the load-bearing claims re-verified by
hand. The two classes of confidence are kept apart, because a spec that deletes shipped resources must not
rest on a number nobody re-ran.

---

## Verified by hand

**1. The denominator is 3228, not 3224. The dead count of 396 holds.**

`app_v2/src/main/res/values/strings.xml` declares 3221 `<string>`, 6 `<plurals>` and 1 `<string-array>` =
3228 names. The file is uncommitted and was being edited on the capture date, which is the likely reason for
the drift of four. The dead count re-measured independently to exactly 396, so the finding is not sensitive
to the denominator.

**2. A cross-module name collision inflates the "alive" set. The real figure is 397.**

`test_connection` has **zero** references in `app_v2/src` and exactly **one** in `wear/src`. A scan that
searches both module trees at once therefore counts it as referenced and drops it from the dead list. It is
dead in `app_v2`.

This matters beyond one key: the two modules are separate resource namespaces - `app_v2` is
`com.sza.fastmediasorter`, `wear` is `com.sza.fastmediasorter.wear`, each with its own generated `R`, and
neither `build.gradle.kts` declares a dependency on the other. A key declared in `app_v2` cannot be reached
from `wear` at all. **Any measurement for this ticket must scan `app_v2/src` alone**; 15 names exist in both
modules and every one of them is a chance to repeat this error.

**3. The safety check inside the removal tool does not see flavor source sets.**

`scripts/utils/set-android-string.ps1`, `Report-References` at line 267: `$srcRoot = Join-Path $resDir '..'`,
which resolves to `<module>/src/main`. The three patterns it greps are `R.string.<key>` in `.kt`/`.java` and
`@string/<key>` in `.xml`.

Two consequences, both load-bearing for this ticket:

- The 39 source-set directories under `app_v2/src` are never scanned. Asked about a key that lives only in
  `launcherEnabled`, `castEnabled`, `screenCapture`, `noLegal` or `vr`, the check prints `none` - which reads
  as "safe to remove".
- `R.plurals.` / `R.array.` / `@plurals/` / `@array/` are not matched at all, so the 7 non-`<string>` names
  have no safety check whatsoever.

Parked separately as its own ticket: the defect misleads every caller of `-Action remove` today, not only
this ticket.

---

## Reported by the research pass, not re-verified by hand

Credible and evidenced, but re-run before a removal list is finalised.

- **216 keys are alive only through a flavor source set.** Restricting the scan to `src/main` alone raises
  the dead count from 396 to 612; the difference is `launcher_*`, `cast_*`, `screen_capture_*`,
  `screen_recording_*`, `screenshot_*` and `tile_screenshot_label`. This is the single largest trap in the
  audit and the exact blind spot of the tool check above.
- **No key is alive only through a test source set.** Excluding all nine test directories leaves the count at
  396 - every test reference duplicates a production one.
- **The array and plurals children are not an escape hatch in this file.** The one `<string-array>`
  (`color_theme_options`) and all six `<plurals>` hold literal text, no nested `@string/` reference, so the
  parent name carries liveness.
- **The dynamic lookup cannot reach this file.** All three `getIdentifier(` call sites in the tree resolve
  either `keybinding_label_*` / `keybinding_group_*`, which live entirely in `strings_input.xml`, or the
  platform `status_bar_height` dimen. Zero keys of `strings.xml` are reachable dynamically, so the capture's
  caveat about `KeybindingRowLabelFormatter` turns out not to shrink the list at all.
- **Non-source surfaces do not name string keys.** `docs/settings/settings-manifest.json` keys off view ids
  and carries duplicated literal titles, not `R.string` identifiers. The keep rule
  `-keep class **.R$string { *; }` preserves generated int fields for R8 and says nothing about source
  liveness.
- **Locale blast radius.** Of the 396, `values-ru` and `values-uk` carry 394 each, and each of the ten
  best-effort locales carries 122. Removal therefore touches roughly 2000 lines today. `-Action remove`
  already sweeps every locale present on disk in one call, so cross-locale atomicity is not a problem.
- **Overlap with S1550 is substantial.** 75 of the 396 match the layout-attribute-literal families that
  S1550 is about (`_lineSpacingMultiplier`, `_boxBackgroundMode`, `_endIconMode`, `_layoutManager`,
  `_resize_mode`). Deleting a key resolves S1550's problem for that key; the two tickets should not run
  against the same lines in parallel.
- **No gate notices an orphan.** `scripts/check_strings_localized.ps1` builds its key universe from the
  strict locales only and has no surplus check, so a key hand-deleted from `values/` while left in a locale
  survives silently. Going through `-Action remove` avoids creating one; a hand edit does not.

---

## What this does not answer

Whether the remedy is deletion, quarantine, or leaving the keys in place, and how the work orders against
S1420's in-flight locale seeding and S1550. Those are the owner's calls - see strategic §6.
