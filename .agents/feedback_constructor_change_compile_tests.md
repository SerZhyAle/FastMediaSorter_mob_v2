---
name: constructor-change-compile-tests
description: assembleStandardDebug does NOT compile test sources; after changing a class constructor/signature also run testStandardDebugUnitTest or compileStandardDebugUnitTestKotlin or unit tests break silently
type: feedback
---

After changing a class's constructor (adding/removing/reordering an injected param) or any public signature, run the unit-test compilation - `./gradlew.bat testStandardDebugUnitTest` (or at minimum `compileStandardDebugUnitTestKotlin`) - not just `a.ps1 dq`/`fk`/`fc`.

**Why:** `a.ps1 dq` / `assembleStandardDebug` / `fk` (`compileStandardDebugKotlin`) compile only the `main` source set. A unit test that manually constructs the class with the old signature (e.g. `SftpConnectionGate(client, tracker, diagnostics)`) keeps a stale call and fails to compile, but the main build is green - so the break ships silently and surfaces only when something later runs the test task. This actually happened: S0727 added an `@ApplicationScope CoroutineScope` param to `SftpConnectionGate`; the S0727 build gate (assembleStandardDebug + noLegal debug) passed, but `ConnectionGatesTest` (3 constructions) was broken and only caught a ticket later under S0732.

**How to apply:**
- When a change touches a constructor or public signature of a class that has (or might have) a unit test, grep `src/test` for manual constructions (`grep -rn "<ClassName>("`), update them, and run the affected `--tests "*<ClassName>Test*"` filter (the test task compiles the WHOLE test source set first, so it also surfaces unrelated stale call sites).
- Mocking a Room `db.withTransaction { }` in a plain mockk JVM test: `mockkStatic("androidx.room.RoomDatabaseKt")` then `coEvery { db.withTransaction(any<suspend () -> Any?>()) } answers { runBlocking { secondArg<suspend () -> Any?>().invoke() } }` - for an extension function mocked via mockkStatic, **arg 0 is the receiver and arg 1 is the block** (use `secondArg`, not `firstArg`; `firstArg` throws ClassCastException). `coAnswers` is not an importable top-level symbol in mockk 1.13.9 - use `answers { runBlocking { .. } }`.
