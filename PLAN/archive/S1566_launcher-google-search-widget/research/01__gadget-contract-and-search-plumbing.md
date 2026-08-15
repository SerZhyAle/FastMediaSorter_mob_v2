# Research 01 - Gadget contract and existing search plumbing

**Spec:** S1566 - launcher-google-search-widget
**Date:** 2026-08-11
**Mode:** read-only codebase research
**Question:** what does it take to ship a Google-search gadget, and what already exists to launch a search?

---

## 1. What one new gadget costs

The two gadgets added by S1560 (`AltitudeGadget`, `SatellitesGadget`) are the worked template. A gadget is:

- One class in `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/`, implementing
  `LauncherGadget` and returning its own `LauncherGadgetView` subclass from `createView`.
- One stable `KEY_*` constant in `LauncherGadgetRegistry` - the key is the stored `target` string, so it is
  never renamed once shipped.
- One entry in a qualified Hilt list multibinding module (`di/SensorGadgetModule.kt` and its two siblings),
  binding into `List<@JvmSuppressWildcards LauncherGadget>` behind a `@Qualifier`.
- One layout `res/layout/gadget_launcher_*.xml` under `src/launcherEnabled`. No `-land` counterpart: every
  existing gadget layout is orientation-neutral, because the desktop grid measures the cell.
- One drawable in `src/main/res/drawable/`.
- String keys in `src/main/res/values{,-ru,-uk}/strings.xml`. Strings stay centralised in `src/main` even for a
  class that lives in a flavor source set.

## 2. ADR-5 - the constraint that reshapes the request

`docs/ARCHITECTURE.md:304`, verbatim:

> A gadget is an interactive block the user places on the desktop, and it is always **our own view - never a
> third-party `AppWidget`** (ADR-5)

Consequence for this ticket: the real Google Search `AppWidget` cannot be embedded. What ships is our own view
that *looks like* a search field and hands the query off to something else.

## 3. Search / web launch that already exists

- `WeatherGadget.openWeatherApp` (`WeatherGadget.kt:106-120`) is the closest precedent and is directly
  reusable in shape: try a hardcoded package list via `getLaunchIntentForPackage`, else fall back to
  `Intent(Intent.ACTION_WEB_SEARCH)` carrying `SearchManager.QUERY`, check `resolveActivityCompat` before
  firing, wrap `startActivity` in `runCatching`, log and no-op on total failure.
- `ClockGadget.openSystemClock` / `openCalendar` (`ClockGadget.kt:66-89`) - same resolve-then-launch-then-catch
  shape, and shows a gadget attaching its own click listeners.
- `GoogleDomainBrowserLauncher` + `CctAvailabilityChecker` (`data/browser/`) already open a Google URL in a
  Chrome Custom Tab, but are scoped to OAuth/auth-url routing. Reusable as a pattern, not currently wired to
  search queries.
- `ACTION_ASSIST` is already declared in `<queries>` (`AndroidManifest.xml:199-201`).

No existing `LauncherCellCommand` variant opens a generic search query.

## 4. Package visibility - nothing to add

`AndroidManifest.xml:102-105` already declares a `MAIN`/`LAUNCHER` `<queries>` intent, which grants visibility
of every launchable app on API 30+. This is the same mechanism `WeatherGadget` and
`ResolveInstalledPackagesUseCase` rely on. A "is a Google search app installed" check needs no manifest change.
The Custom Tabs probe is likewise already covered (`AndroidManifest.xml:165`).

## 5. Text input inside a desktop cell

- No existing gadget hosts an editable field. The only launcher search box lives in
  `fragment_launcher_all_apps.xml:24-40`, a Fragment outside the drag/D-pad grid.
- The cell card is deliberately non-focusable so that "the gadget's inner controls are the D-pad stops"
  (`item_launcher_cell_gadget.xml:2-5`). An editable field inside a gadget is therefore architecturally
  sanctioned rather than a deviation.
- Edit mode overlays a transparent clickable+long-clickable scrim as the cell's last child
  (`LauncherCellViewBinder.kt:228-272`). It swallows every touch and starts the drag, so a field inside a
  gadget is automatically inert while the desktop is being rearranged. Outside edit mode the gadget owns its
  touches uncontested.

## 6. Lifecycle and spans

`LauncherGadgetView.onActive()` runs under `repeatOnLifecycle(STARTED)` bound to the view, cancelled on
detach; there is no `onViewRecycled` because the grid is not a `RecyclerView` (ADR-9). `defaultSpanW` is a
**column count**, not a width fraction, and the column count itself resolves from available width and a user
density factor - so "full width" is not directly expressible. The widest existing gadget is `ClockGadget` at
4x2.

## 7. Flavor scope

`docs/FLAVOR_MATRIX.md:25` - `SUPPORT_LAUNCHER` is `[+]` for `standard` and `noLegal`, `[-]` for `lite`,
`photos`, `legacy`, `vr`. A gadget never branches on the flag; living in `src/launcherEnabled` is the gate.
minSdk floor is 26 - `legacy`'s 23 is moot because `legacy` never compiles the launcher tree.

## 8. Known inconsistency worth a decision, not a separate ticket

`WeatherGadget` and `ClockGadget` build and fire their own Intents, bypassing the `LauncherGadgetHost.run`
funnel that `docs/ARCHITECTURE.md:306` describes as the single guarded execution path. Every other gadget goes
through the host. A search gadget has to pick a side, which is why this is recorded as a spec decision rather
than parked as an unrelated defect.

## 9. Test baseline

No unit tests exist for any `*Gadget.kt` or for `LauncherGadgetRegistry`. The nearest tested piece is
`LauncherCellCommandTest`, covering the command codec - relevant only if this ticket adds a command variant.
