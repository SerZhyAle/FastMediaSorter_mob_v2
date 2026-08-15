# Research 01 - Crash-vs-info gate

Spec: S0483. §6 item 1. Source: codebase audit (read-only) 2026-06-17.

## Question

How does the error dialog tell a real crash (show the report button) from a benign
"X unavailable / not supported" message (hide it)?

## Findings

- The error dialog is a single unified surface in `ui/dialog/` (`ScrollableTextDialog`),
  inflating `res/layout/dialog_error_detail.xml` (+ `res/layout-land/` counterpart).
- Its public `show(..)` entry takes `message: String` and optional `details: String?`.
  A `Throwable` convenience overload exists but only reads `throwable.message` and
  discards the object - no exception identity reaches the dialog.
- There is an `ErrorSeverity` enum (`CRITICAL`, `DEBUG_ONLY`) but it controls display
  verbosity, not crash-vs-info classification.
- The originating exception IS available upstream at most call sites. The browse path
  carries it end-to-end via the error event (`ShowError(message, details?, exception?)`)
  and the browse error display manager receives `exception: Throwable?` - but does not
  forward it into the dialog (it is used only to filter non-critical network image
  errors). The player path calls the dialog with a bare `message: String`.

## Gap

No signal at the dialog layer distinguishes exception-backed failures from purely
informational unavailability strings. A gate must be introduced.

## Recommended direction (strategic)

- Button appears only for **unexpected/exception-backed failures** (real bugs the author
  would want to fix), never for expected, handled conditions surfaced to the user
  (file/format unavailable, offline, permission denied, etc.).
- Mechanism is a tactical decision for `/spec-tech`. Candidate predicates:
  - forward a nullable `Throwable` into the dialog; show the button iff non-null;
  - OR an explicit `isCrashReport: Boolean` flag set by call sites;
  - OR reserve the existing optional action slot for crash-originated calls only.
- `/spec-tech` must audit all dialog call sites and decide which classes of error are
  "crash" vs "info". Known call sites to audit: browse error display manager, player
  event handler, add-resource connection manager, the UI message projector, browse
  dialog helper, file-operation destination dialog, main activity, plus standalone
  activities that call the dialog directly.

## Layout note

`dialog_error_detail.xml` already exposes an optional `btnExtra` action slot wired
through the dialog's `ExtraAction` parameter - lowest-friction insertion point, no new
button row required. Action row already holds several buttons; check overflow on narrow
portrait. Portrait + landscape layouts must change in lockstep.
