# Phase 05 — Restore DI wiring and integration boundaries

## Goal

Finish the rewrite by restoring the Hilt bindings and object graph required for the rebuilt auth-storage,
extraction, coordinator, worker, and UI surfaces.

## Steps

- [x] Audit all injected S0166 surfaces for deleted bindings or stale constructor contracts.
  **Verification:** No remaining injection site references a class deleted in Phase 00.

- [x] Add or repair the Hilt module bindings/providers required by the rebuilt implementation.
  **Verification:** The app module can resolve repository, coordinator, cookie/session, and auth UI dependencies.

- [x] Remove stale module/provider code that exists only for the deleted S0151/S0155 stack.
  **Verification:** The DI graph exposes only live classes.

## Verification predicate

The project reaches a clean compile for the S0166 slice without generated-code failures or missing bindings.

## Status: ✅ Complete