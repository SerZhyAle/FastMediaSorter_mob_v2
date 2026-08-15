# S1516 Research 01 - Current versus historical Room schema references

**Date:** 2026-08-14
**Question:** Which Room schema references must be kept in sync with the live database declaration?

## Evidence

- The live database declaration reports schema version 50.
- `dev/TECH_REQUIREMENTS.md` has two current-schema rows, both at version 50.
- `docs/DEV_OPS.md` calls its Room schema value current but still reports version 49.
- `dev/PRODUCT_COMPLEXITY_ASSESSMENT.md` describes version 41 as the current database value in a dated assessment.
- `dev/handoff/streams-source-spec/02_data_model.md` identifies version 41 as the current database version while linking to the version-41 exported schema snapshot.
- The existing `room-schema-version` pin checks only `dev/TECH_REQUIREMENTS.md`; its test suite only copies that document into the sandbox.

## Decision

Treat `dev/TECH_REQUIREMENTS.md` and `docs/DEV_OPS.md` as current operational documents. Both must be required entries of the existing Room schema pin and both must be covered by mismatch regression scenarios.

Treat the product-complexity assessment and Streams handoff as historical snapshots. Preserve their version-41 references, but rewrite them as a baseline instead of a claim about the current database version. They must not be included in the live-version pin.

## Consequences

The checker remains a single manifest-driven mechanism. A schema change requires documentation updates only for the two current references; historical documents retain reproducible context without causing false failures.
