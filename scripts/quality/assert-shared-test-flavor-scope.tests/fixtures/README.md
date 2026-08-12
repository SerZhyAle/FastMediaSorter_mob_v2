# Fixtures for assert-shared-test-flavor-scope

Nothing in this folder is a real source file, and nothing here is checked in as one.

`Run-Tests.ps1` generates its fixtures per run: a synthetic repository under `temp/scratch/`, holding
a build file with two flavors and the source-set folders each case needs. The folder is removed in a
`finally` block, so a crash mid-run leaves no stray tree behind.

The fixtures live outside `app_v2/src` on purpose. The gate under test refuses a shared unit test that
references a flavor-scoped type, so a fixture placed in the real source tree would either trip the gate
it is meant to prove or, worse, be mistaken for production test code.
