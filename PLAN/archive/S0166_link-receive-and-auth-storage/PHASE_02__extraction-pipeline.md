# Phase 02 — Rebuild the social-aware extraction pipeline

## Goal

Restore link extraction around S0166 §2 Steps 0–2a so known social hosts can apply stored sessions,
distinguish real media from preview-only results, and escalate to auth only when the page analysis truly
requires it.

## Steps

- [x] Rebuild the strategy contract/registry for link extraction after the Phase 00 deletions.
  **Verification:** `ReceiveShareActivity` and the worker resolve a concrete coordinator path without
  depending on deleted strategy types.

- [x] Implement known-host routing for Instagram/Threads and a standard pipeline path for unknown hosts.
  **Verification:** Known hosts are recognized by an explicit in-app list; unknown hosts bypass the
  social-specific branch.

- [x] Reintroduce cookie/session injection for the invisible page load path and keep the chosen account
  isolated to the active download.
  **Verification:** One download can use one selected account's cookies without mutating global browser state.

- [x] Distinguish real media, preview-only, login-wall, empty-content, and network-error outcomes.
  **Verification:** `og:image` alone is never treated as success.

## Verification predicate

The extraction layer produces enough result detail for S0166 to choose among download, re-auth,
honest failure, and network error without heuristics leaking through the UI layer.

## Status: ✅ Complete