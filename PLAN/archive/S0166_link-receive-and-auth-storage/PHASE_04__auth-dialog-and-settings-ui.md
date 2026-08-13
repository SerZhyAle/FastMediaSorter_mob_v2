# Phase 04 — Rebuild auth dialogs, account picker, and settings UI

## Goal

Restore the user-facing auth surfaces described by S0166 §2 Step 4 and §4: initial/reauth offer,
multi-account picker, and the settings screen for managing stored sessions and rejection records.

## Steps

- [x] Implement the 3-button auth offer with distinct behaviors for `Sign in`, `Cancel`, and `Don't ask`.
  **Verification:** `Cancel` creates no record; `Don't ask` creates a dismissal record for the host.

- [x] Implement explicit account selection when more than one non-dismissed record exists for a host.
  **Verification:** The picker orders records by most recently updated/used first.

- [x] Rebuild the WebView sign-in save flow so `Save authorization` persists or refreshes one record,
  extracts display name when possible, and returns to one retry attempt.
  **Verification:** A successful save updates storage and resumes the pending download flow.

- [x] Rebuild the settings/auth-management screen with the explanatory block, per-record actions, and
  add-account entry point from S0166 §4.
  **Verification:** Dismissed records are visible as such and can be deleted from settings.

## Verification predicate

All user-visible auth decisions from S0166 are available in UI, and the settings screen can explain why
zero-cookie dismissal records exist.

## Status: ✅ Done