# Research 01 - Capture-to-curated-send seam

**Ticket:** S0472
**Status:** informational (shaped §5 and §6.1)
**Method:** codebase research via class catalog + grep (android-solution-researcher), 2026-06-17.

## Question

Screenshot-gesture capture currently offers a `SHARE` post-capture action that fires the
plain OS `ACTION_SEND` chooser (every installed app). The owner wants an additional option
that instead opens the app's own curated "Send to.." dialog, populated from the allowed
recipients configured in Player settings ("Команды отправить файл в" / "Send file to").
Where does a new option hook in, and what makes it non-trivial?

## Two existing subsystems

### A. Screenshot-gesture post-capture dispatch (noLegal-scoped feature)

- Post-capture actions are an enum assigned per gesture direction:
  `DO_NOT_USE`, `SILENT_SCREENSHOT`, `OPEN_IN_PLAYER`, `OPEN_IN_DRAW`, `OCR_TRANSLATE`, `SHARE`.
- Per-direction values stored in app settings (down/right/up).
- Dispatch entry point: a shared (`src/main`) post-save dispatcher that receives a bare
  `Context` (the capture `Service` `applicationContext`, or the `AccessibilityService`
  instance on API 30+). It maps the resolved action to a route.
- The existing `SHARE` route calls the system-share invoker with `FLAG_ACTIVITY_NEW_TASK`,
  which already works from a non-Activity `Context`. It produces the unfiltered OS chooser.
- Settings UI: the gesture-action picker dialog renders the enum labels; the whole gesture
  group is shown only when the injected capture-controller set is non-empty (noLegal only).

### B. Curated "Send file to" recipients (shared, all flavors)

- Recipients are declared as immutable share-target descriptors (id, title, target
  packages, applicable media types, enable state) and collected via a Hilt multibinding
  registry.
- A pure-domain use case builds the ordered recipient list with a 3-gate filter:
  enabled (from settings) + available (resolver) + applies-to-media-type.
- The player presents them through a curated send manager: 1 recipient -> direct send,
  >1 -> a bottom-sheet dialog. This manager **requires a `FragmentActivity`** because the
  dialog is a `BottomSheetDialogFragment`.

## The seam and why it is non-trivial

- The natural hook is a **new enum value** in the shared post-capture action set (additive;
  do not reroute `SHARE`). The dispatcher gains one new route.
- That route must invoke subsystem B's curated send path with the freshly saved screenshot
  URI + `MediaType.IMAGE`. Image-applicable enabled recipients are selected automatically by
  the existing 3-gate filter.
- **Hard part:** the curated dialog needs a `FragmentActivity`; capture dispatch has only a
  `Context`. Candidate mechanisms (tactical decision, see §6.1):
  1. Transparent trampoline Activity (same pattern as the existing MediaProjection consent
     Activity in the screenCapture source set) that hosts the bottom sheet.
  2. A notification action whose `PendingIntent` opens the dialog on tap (safe from
     background-activity-launch limits, adds one user tap).
  3. Route through the existing standalone media Activity with an auto-action extra that
     triggers the curated send in `onCreate`.

## Risks that make it non-PRIMITIVE

- **Background-activity-launch denial (API 29+):** dispatch fires after the capture FGS has
  stopped (or, on API 30+, from an `AccessibilityService` with no FGS). Launching a new
  Activity then may be denied. The existing `OPEN_IN_PLAYER` route has the same exposure and
  was accepted in device test, but a curated bottom-sheet host is a new path to verify.
- **Null saved URI:** when the screenshot destination is a network resource, the saved URI
  can be null. The new route must degrade gracefully, exactly like the existing `SHARE` route.
- **No applicable/enabled recipients:** the curated list can be empty. Needs a defined
  fallback (hint per communication policy, or fall back to the OS chooser) - product decision.
- **Settings fragment size:** the fragments rendering the gesture group and the recipients
  group are already near the 1500-LOC ceiling; adding a label may require checking for helper
  extraction first.

## Flavor isolation

- The new enum value + dispatch route live in shared `src/main` (reachable in every flavor
  by code, but only *exposed* where the noLegal capture controllers are injected).
- Any new capture-side UI-host glue lands in the noLegal / screenCapture source sets,
  following `dev/FLAVOR_DEVELOPMENT_RULES.md`. No `BuildConfig.IS_*` guard in `src/main`.
