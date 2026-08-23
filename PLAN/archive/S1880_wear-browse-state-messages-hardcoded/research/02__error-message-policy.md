# Browse error-message policy

**Ticket:** S1880
**Status:** Resolved

## Evidence

`BrowseViewModel` currently places exception messages into its UI state. The UI communication
policy requires a human explanation and a usable next step, and forbids raw exception text as the
primary error message.

## Decision

Keep the original exception in `Timber` for diagnostics. The screen receives a localized,
resource-backed generic message instead. Connection errors tell the user to check the connection
and retry; unexpected load errors offer retry without exposing a technical cause.
