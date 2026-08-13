# S1381 research - current drift gate

## Evidence

- The baseline gate exits 0 with 18 PASS and 72 SKIP records.
- The manifest has no required documentation matcher for targetSdk and no canonical source or matcher for the Room schema version.
- The current Room schema row is 44 while a history row still records 42. The live source contract is 44.

## Decision

Model compileSdk, targetSdk and Room schema as explicit pins. Require only live documentation rows and exclude the version-history section from the schema matcher. Regression coverage must prove both mismatch detection and history exclusion.

## Source material

`temp/S1381/research-01-current-drift-gate.md`
