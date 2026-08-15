# Research 01 - Flavor-gate field coverage across flavors

Resolves strategic §6 item 1.

## Question

Is a unit test on a single build variant enough to catch a typo'd flavor-gate field name, or must the check run per flavor?

## Findings

- The permission registry lives in `src/main` and is shared by all flavors. Its flavor-gate strings currently in use: `SUPPORT_AUDIO`, `ENABLE_PERSISTENT_AUDIO_PLAYBACK`, `SUPPORT_LOCAL_NETWORK`.
- Every one of those `buildConfigField` names is declared in all 6 flavor blocks (`grep -c` = 6 each in `app_v2/build.gradle.kts`). The field *set* is uniform across flavors; only the boolean *values* differ.
- `BuildConfig` is per-variant, but because the field set is uniform, a typo'd / non-existent field name is absent from EVERY variant's `BuildConfig`, including the test variant (`standard`).
- A Robolectric unit test already exists: `PermissionRegistryRepositoryImplTest.kt`, running against the `standardDebug` variant's `BuildConfig` (`testStandardDebugUnitTest`).

## Resolution

A single-variant unit test (`testStandardDebugUnitTest`) is sufficient to catch a typo or removed field: reflecting each declared flavor-gate string against `BuildConfig` and asserting it is an existing boolean field hard-fails on any name that does not resolve - and a typo resolves on no variant.

Caveat (documented, not blocking): the test only proves the field exists in the *standard* `BuildConfig`. If a future field were declared asymmetrically (present in `standard`, absent in another flavor), a standard-only test would miss the asymmetric gap. The project's convention is uniform per-flavor declaration, so this is a low residual risk; if asymmetric gating is ever introduced, extend the assertion to run per variant.

## Impact on plan

- No per-flavor CI matrix needed. The validation is a unit test in the existing `PermissionRegistryRepositoryImplTest.kt`, run by `testStandardDebugUnitTest`.
- The impl must expose the set of declared flavor-gate field names to the test (a testable accessor on the registry), and narrow the production catch so a missing field is logged at an actionable level while preserving the safe release default (ADR-1).
