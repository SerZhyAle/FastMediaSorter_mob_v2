---
name: constructor-change-compile-tests
description: assembleStandardDebug does NOT compile test sources; after changing a class constructor/signature also run testStandardDebugUnitTest or compileStandardDebugUnitTestKotlin or unit tests break silently
type: feedback
---

After changing a class's constructor (adding/removing/reordering an injected param) or any public signature, run the unit-test compilation - `./gradlew.bat testStandardDebugUnitTest` (or at minimum `compileStandardDebugUnitTestKotlin`) - not just `a.ps1 dq`/`fk`/`fc`.

**Why:** `a.ps1 dq` / `assembleStandardDebug` / `fk` (`compileStandardDebugKotlin`) compile only the `main` source set. A unit test that manually constructs the class with the old signature (e.g. `SftpConnectionGate(client, tracker, diagnostics)`) keeps a stale call and fails to compile, but the main build is green - so the break ships silently and surfaces only when something later runs the test task. This actually happened: S0727 added an `@ApplicationScope CoroutineScope` param to `SftpConnectionGate`; the S0727 build gate (assembleStandardDebug + noLegal debug) passed, but `ConnectionGatesTest` (3 constructions) was broken and only caught a ticket later under S0732.

**How to apply:**
- When a change touches a constructor or public signature of a class that has (or might have) a unit test, grep `src/test` for manual constructions (`grep -rn "<ClassName>("`), update them, and run the affected `--tests "*<ClassName>Test*"` filter (the test task compiles the WHOLE test source set first, so it also surfaces unrelated stale call sites).
- Mocking a Room `db.withTransaction { }` in a plain mockk JVM test: `mockkStatic("androidx.room.RoomDatabaseKt")` then make the mock RUN the block. For an extension function mocked via mockkStatic, **arg 0 is the receiver and arg 1 is the block** - `firstArg()` returns the receiver `db` (block never runs -> counters stay 0 -> `expected:<1> but was:<0>`), NOT a ClassCastException when the caller wraps in try/catch. Two working forms (verified 2026-07-03 under both `runBlocking` and `runTest`, mockk in this repo):
  - Index-agnostic + robust: `coEvery { db.withTransaction<Any?>(any()) } coAnswers { @Suppress("UNCHECKED_CAST") (args.first { it is Function<*> } as suspend () -> Any?).invoke() }`. `coAnswers` IS usable as a scope call here (no import needed); `args` picks the block regardless of receiver indexing.
  - Explicit: `secondArg<suspend () -> Any?>().invoke()` inside `coAnswers`.
  - The block-not-running symptom is builder-independent (fails under BOTH `runTest` and `runBlocking`) - it is the `firstArg`/indexing bug, not a dispatcher issue. Don't chase runTest-vs-runBlocking.
- **Apply this per-ticket, immediately** - not at batch end. In a `/spec-all` run I validated several constructor/DI changes (`@ApplicationScope`/`AppDatabase` injected params) with `fk` only; the test-compile breakage stayed silent for 4 tickets until a later ticket's `--tests` run surfaced it. Every constructor/signature change gets its own `testStandardDebugUnitTest --tests "*<Class>Test"` before the ticket is closed.
