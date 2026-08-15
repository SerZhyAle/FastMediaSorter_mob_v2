# Research 01 - Reuse of default-player registration from Settings

Date: 2026-06-15
Ticket: S0435
Method: codebase mapping via catalog + grep (android-solution-researcher).

## Area map

- Playback settings page is rendered by a Settings fragment that hosts several collapsible
  groups; section expand/collapse state is persisted to SharedPreferences. Both
  `res/layout/` and `res/layout-land/` variants of the playback settings layout exist and
  must be edited in lockstep.
- Default-player registration logic already lives in the Settings helpers layer:
  - A fragment-scoped entry point shows an instruction dialog, applies the primary-player
    component state, then opens the OS "Open with / Always" sheet via a bare ACTION_VIEW
    intent, falling back to the system default-apps settings when no sample/handler is
    available.
  - A document-specific entry point first lets the user pick PDF vs Office, then delegates
    to the per-MIME flow.
  - A button-state helper hides a button once the app is already the default.
- The Welcome onboarding screen is currently the ONLY surface that exposes these
  registration buttons (a dedicated pager page with 4 type buttons + helper text). The
  Settings page presently contains only enable/disable toggles for the primary-player and
  share-receiver activity-aliases, with no path into the OS registration sheet.

## Key findings shaping the strategic approach

1. The registration entry point is fragment-compatible: Settings fragments are `Fragment`
   subclasses, so the existing per-MIME flow can be invoked directly from the new Settings
   group with standard `isAdded` lifecycle guarding. No duplication of registration logic
   is required.
2. Type visibility (images / audio / video / documents) is governed by per-feature
   capability flags. A DI capability surface already exposes these flags and is the correct
   read point; the current Welcome adapter and Playback fragment read `BuildConfig.SUPPORT_*`
   directly inside `src/main`, which violates flavor isolation (CLAUDE.md Rule 14). New code
   must read the capability surface instead.
3. Whole-group gating: the "default player" capability is false on the lite flavor, so the
   entire new group must be hidden there. photos supports only images, so only the image
   button shows.
4. The instructional helper text used on Welcome is the reference wording for the Settings
   text; it should come from a shared string source so phrasing stays in sync.

## Out-of-scope problems noted (parked)

- Direct `BuildConfig.SUPPORT_*` reads inside `src/main` (Welcome pager adapter + Playback
  settings fragment) - Rule 14 isolation violation → parked.
- Synchronous MediaStore cursor query on the UI thread when locating a sample file for the
  registration intent (StrictMode / freeze risk) → parked.
- Welcome default-player page is suppressed once onboarding-shown and already-default are
  both true, so a user who de-registers in system settings may not see it again. This is the
  motivation for the Settings entry point and is intentionally addressed indirectly, not by
  changing the Welcome re-entry guard.
