# Build & Test Fast Path

This playbook defines the cheapest safe validation path for common change types in `app_v2/` and `wear/`.

Goal: reduce local feedback time without lowering confidence or skipping quality gates.

## Core rule

Pick the cheapest proof that matches the risk of the change.

- Do not default to full APK builds for every edit.
- Do not default to `clean`.
- Do not default to the full unit suite when one targeted test proves the change.
- Escalate only when the previous proof level is not sufficient for the changed surface.

## Foreground or background: the 120 s threshold (S1338)

Backgrounding is not free. The harness re-invokes the agent when a background job finishes, so
every backgrounded command costs one extra turn - and the agent that cannot wait quietly ends up
hand-polling with `cat` and `sleep`, which is where roughly 1,297 polling turns and 81 minutes of
literal `sleep` went in one month. Backgrounding a 15 s check is strictly worse than waiting for it.

**The threshold is 120 s** - the Bash tool's foreground timeout, so it is a real boundary rather
than a round number. Above it a command must be backgrounded, because a foreground call would be
force-migrated to the background anyway and lose its clean output capture. Below it backgrounding
is forbidden: wait for the command and read its verdict in the same turn.

**The forbidden half is enforced (2026-08-08, CLAUDE.md Rule 26).** It had been prose since S1338,
and prose is the 1-8% tier - the same reason the small-task rung nudge became a hook. The global
`PreToolUse` guard `~/.claude/hooks/guard-fire-and-forget.ps1` blocks a `run_in_background` call
whose command is a gate, a closure facade or a catalog mutator, and waves through everything else,
including a long job chained with a gate on the same line. It is deny-list by literal command shape
rather than by heuristic, because a guard that over-blocks gets switched off and then nothing is
enforced. Canon home: `rules/AI_USAGE.md` section 1; smoke-tested from both sides in the canon's
`hooks/tests/smoke-hooks.ps1`.

Measured on this host, 2026-08-01, warm daemon, configuration cache reused:

| Target | Wall clock | Verdict |
| --- | ---: | --- |
| `a.ps1 fg` (fast static gates) | 18.9 s | foreground |
| `assert-detekt.ps1 -Module app_v2` | 20.3 s | foreground |
| `detekt-scoped.ps1 -ChangedFiles <1 file>` | 2.1 s | foreground |
| `detekt-scoped.ps1 -ChangedFiles <2 files>` | 3.3 s | foreground |
| `detekt-preflight` step inside `post-change.ps1` | 3.1 s | foreground |
| `assert-doc-icons-sync.ps1 -Gate` | 0.36 s | foreground |
| `a.ps1 fk` | 14.1 s | foreground |
| `a.ps1 fc` | 18.6 s | foreground |
| `a.ps1 dq` | 18.4 s | foreground |
| `a.ps1 d` / `dav` / `r` / `fu` | not measured | background |

The `resource-link-gate` rows were measured on 2026-08-21 (S1915), warm daemon, `app_v2`:

| Gate run | Wall clock | Verdict |
| --- | ---: | --- |
| one flavor, nothing to relink | 1.9 s | foreground |
| one flavor, cold configuration cache | 10.6 s | foreground |
| one flavor, red - aapt rejects a layout | 15.9 s | foreground |
| one flavor, full relink after a resource change | 41.8 s | foreground |

A two-flavor set costs the sum of two such runs, because the gate invokes the helper once per flavor -
so the worst case observed here doubles to roughly 84 s and still clears the 120 s threshold. The
1.9 s row is the one that matters for everyday cost: a closure whose resource set is already linked
pays almost nothing, and the 41.8 s row is what an actual resource edit costs.

The OCR overlay bench rows were measured on 2026-08-26 (S1782), warm daemon, `app_v2`. Both benches live
in the test source set, so nothing here ships in an APK:

| Bench | Command | Wall clock | Verdict |
| --- | --- | ---: | --- |
| rectangle axes (S1716) | `scripts/ocrbench/run-corpus.ps1` | not measured | foreground |
| concealment axis (S1782) | `check-standard-fast.ps1 -Mode Unit -Tests "*ConcealmentMetricTest*"` | 24-42 s | foreground |

The 24 s figure is an incremental re-run and the 42 s one includes recompiling the unit test source set;
a run that also has to recompile `app_v2` itself was measured at 1 m 33 s and still clears the threshold.
Each concealment run writes `temp/ocrbench/<date>/overlay-concealment-report.md` and records that path in
`temp/ocrbench/last-concealment-report.txt`. The acceptance bound is **not** in the metric code - it is in
`app_v2/src/test/resources/ocrbench/concealment-bounds.json`, which names the dated report it came from.

The three `detekt-scoped` rows were measured on 2026-08-12 (S1595). They are the only detekt rows here that do NOT take `BUILD.LOCK`: the scoped runner drives detekt's CLI directly rather than through gradle, so it never queues behind a sibling session's build - which is why its number stays honest under contention while the `assert-detekt` row above does not.

`assert-doc-icons-sync.ps1 -Gate` was measured on 2026-08-14 (S1545) as a completed foreground run with no lock wait. The 0.36 s figure is the gate's own wall-clock duration, not time spent queued for a repository lock.

Since that measurement `fg` gained one gate: `assert-shared-test-flavor-scope` (S1453) at 1.4-1.9 s, which puts the batch around 20 s and still an order of magnitude below the 120 s threshold. The row above is left at its 2026-08-01 value rather than restated, because the only re-run available on 2026-08-09 read 46.1 s wall with two sibling sessions holding `BUILD.LOCK` for gradle - a measurement of contention, not of the batch.

Two caveats, recorded rather than smoothed over:

- The `fk` / `fc` / `dq` figures are runs that stopped at a **kapt failure** from another ticket's
  in-flight Kotlin, so they time the configuration and compile-graph phases but not packaging. A
  green run of the same targets is longer; the audit measured the compile chain at ~44 s.
- `d`, `dav`, `r` and `fu` could not be measured in that window for the same reason. They stay
  background-required on the standing observation that a cold gradle daemon routinely exceeds the
  120 s foreground timeout, and because `fu` is the full unit suite.
- S1375 (2026-08-03) turned KSP's incremental bookkeeping off - it crashes on this host's cross-drive
  layout, see `docs/DEV_OPS.md` "KSP incremental is off on purpose". Every Kotlin source change now
  pays a full KSP pass. Re-measured green on that date: a no-change run stays `UP-TO-DATE` at ~2 s, a
  one-file edit runs `fk` in **22-24 s**. Still foreground by a wide margin, and inside the ~44 s
  green compile-chain figure above, but the 14.1 s row is not reachable for an edit any more.

Re-measure on a green tree by timing the targets themselves - `Measure-Command { pwsh -NoProfile -File ./a.ps1 fk }`
and the same for `fc` and `fu`, once with no change and once after touching a single file. The harness this
line used to name lived under `temp/`, which CLAUDE.md Rule 1 makes disposable; it is gone, and a document
may not send a reader to a path that scratch cleanup is entitled to delete (S1850).

## Default command set

Use these commands as the standard local toolbox:

```powershell
.\a.ps1 fk
.\a.ps1 fr
.\a.ps1 fc
.\a.ps1 fu
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.SomeClassTest"
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Assemble
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Code -BuildType Release
.\a.ps1 d
.\a.ps1 db
.\a.ps1 dq
.\a.ps1 fw
.\a.ps1 fwr
.\a.ps1 fwu
.\gradlew.bat :wear:assembleDebug   # wear packaging proof only - fw/fwr/fwu cover the rest
.\a.ps1 adb install -Flavor standard
.\a.ps1 adb launch
.\a.ps1 adb log -Tail 400 -Grep "FATAL|ANR|Sxxxx"
pwsh -NoProfile -File scripts/utils/recover-kapt-stall.ps1 -Task ":app_v2:testStandardDebugUnitTest"
```

### Proving a release variant compiles - and why `nl` is not that proof (S1988)

Every fast target above compiles a **debug** variant, which puts `src/debug` on the classpath beside
`src/main`. That is the wrong instrument for one specific question: does `src/main` still build once a
debug-only class is gone? A test seam reached reflectively (`CameraTestHooksBridge` -> `CameraTestHooks`)
raises exactly that question, and a debug compile answers it "yes" whether it is true or not.

`.\a.ps1 nl` looks like the missing proof and is not. `r`, `nl` and `vr` all delegate to the git worktree
at `../FastMediaSorter_release`, pull `main`, and build **there** - so a BUILD SUCCESSFUL from any of them
describes committed `main`, not the working tree, and says nothing about uncommitted work. This was
quoted as evidence once before it was caught.

Use `-BuildType Release` instead. It compiles the working tree:

```powershell
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Code -BuildType Release
```

Measured 3m 4s cold on `app_v2/Standard`, so background it per the 120 s rule above. It is refused with
`-Mode Assemble` (exit 2): packaging a release artifact needs the signing config and the release version
stamp, both of which belong to the release worktree, and an unsigned artifact stamped like a real one is
the S1873 failure.

## Fast-path routing

### 1. Docs, comments, or non-runtime text only

Use grep-only proof.

```powershell
rg -n "expected text" docs dev <path>
```

Do not run Gradle for doc-only edits.

### 2. Kotlin or Java symbol edit, no resources touched

Use:

```powershell
.\a.ps1 fk
```

Examples:
- helper extraction
- ViewModel logic change
- repository/use-case edit
- DI wiring that does not need packaging proof

### 3. XML, manifest, navigation, or resources only

Use:

```powershell
.\a.ps1 fr
```

Examples:
- layout edits
- manifest flags
- drawables
- string/resource reshaping

### 4. Small mixed code + resource change

Use:

```powershell
.\a.ps1 fc
```

This is the default proof for small UI changes that touch both Kotlin and XML.

### 5. Focused logic fix with known tests

Run the narrowest relevant unit test first.

```powershell
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.SomeClassTest"
```

Prefer one class, one package, or one failure-focused test pattern before the full suite.

**`-Tests` is the default, not the exception (S1338).** 163 of 344 fast-check unit runs in one
month used the full suite while a single-class filter was available - the suite is minutes, one
class is seconds, and the suite has been observed to OOM part-way through and report a truncated
pass (S1244). Reach for `-Tests` unless the change is in the list under section 6 below.

```powershell
# one class
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.SomeClassTest"
# one package - the wildcard is a gradle --tests pattern
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.ui.*"
# one failing method
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.SomeClassTest.theOneCase"
```

### 6. Broad logic change or shared infrastructure change

Escalate to the full local unit suite.

```powershell
.\a.ps1 fu
```

Examples:
- shared utility used across features
- settings serialization model
- data-layer contract changes
- script changes that alter generated values consumed by tests

### 7. Need installable artifact or packaging proof

Use the fast reusable debug path.

```powershell
.\a.ps1 d
```

If ZIP output is not needed, prefer:

```powershell
.\a.ps1 db
```

If you want less console noise during repeated local loops, prefer:

```powershell
.\a.ps1 dq
```

### 8. Need assemble proof without the full wrapper path

Use:

```powershell
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Assemble
```

This is useful when compile/resources are not enough, but you still do not need the richer artifact handling of `.\a.ps1 d`.

### 9. Wear-only change

Use:

```powershell
.\a.ps1 fw                          # Kotlin under wear/
.\a.ps1 fwr                         # resources/manifest under wear/
.\a.ps1 fwu                         # unit tests under wear/src/test
.\gradlew.bat :wear:assembleDebug   # only when packaging proof is the point
```

**Never prove a wear change with `fk`/`fr`/`fc`/`fu` (S1807).** Those four check `app_v2` and exit 0 without compiling a single watch file, so the green they print is a verdict about the other module. Every fast check prints the module it checked in its own banner - read that line before quoting the exit code as proof.

Escalate only if the change also affects shared code used by `app_v2/`.

### 10. Device smoke after a local build

Use:

```powershell
.\a.ps1 adb install -Flavor standard
.\a.ps1 adb launch
.\a.ps1 adb log -Tail 400 -Grep "FATAL|ANR|Sxxxx"
```

Prefer this for:
- startup sanity
- crash confirmation
- quick verification of user-visible behavior on a connected device

### 11. noLegal / Chaquopy path

Use the dedicated noLegal wrappers, not the standard fast-check assumptions.

```powershell
.\a.ps1 nd
.\a.ps1 nl
```

Reason: the noLegal graph intentionally avoids configuration-cache reuse.

### 12. Change touching flavor-visible resources or flavor source sets

Prove each affected flavor with the same fast check, selected by `-Flavor`. No dedicated letter exists or is needed.

```powershell
.\a.ps1 fc -Flavor Lite      # Standard | NoLegal | Lite | Photos | Legacy | Vr
.\a.ps1 fc -Flavor Legacy    # the only path that compiles against minSdk 23
.\a.ps1 fc -Flavor Vr        # the only path that compiles src/vr
```

`-Flavor` works on `fk` and `fr` too, and every call takes `BUILD.LOCK`, so a spec demanding proof on "every affected variant" is satisfiable without a direct `gradlew` call. Run them one at a time - Rule 23 allows a single gradle invocation at once. Measured on a warm daemon: roughly 1-2.5 min per flavor, since each one owns its own configuration-cache entry.

Reason this is spelled out: the flag existed long before it was documented, and S1568 deferred its per-flavor validation on the assumption that no such command was available (S1589).

### 13. KAPT stall recovery

If a targeted Kotlin/unit task hangs around `kaptGenerateStubs...` or `kapt...Kotlin`, recover instead of wiping all caches.

```powershell
pwsh -NoProfile -File scripts/utils/recover-kapt-stall.ps1 -Task ":app_v2:testStandardDebugUnitTest"
```

Use full cache cleanup only as a last resort.

## Escalation ladder

Move upward only when needed:

1. grep-only
2. `.\a.ps1 fk`
3. `.\a.ps1 fr`
4. `.\a.ps1 fc`
5. targeted `-Mode Unit -Tests "..."`
6. `.\a.ps1 fu`
7. `check-standard-fast.ps1 -Mode Assemble`
8. `.\a.ps1 d`
9. device smoke

## Recommended defaults by change type

| Change type | Default proof | Escalate when |
| --- | --- | --- |
| Docs only | grep | never, unless a script/doc generator changed |
| Kotlin-only symbol edits | `.\a.ps1 fk` | packaging or behavior needs proof |
| Resource/layout/manifest only | `.\a.ps1 fr` | mixed code also changed |
| Small UI fix | `.\a.ps1 fc` | behavior depends on runtime flow or packaging |
| Focused logic bug fix | targeted unit test | shared area or many tests affected |
| Shared model / serializer / infra | `.\a.ps1 fu` | install/runtime behavior also changed |
| Packaging/install concern | `.\a.ps1 d` | device-specific behavior matters |
| Wear-only Kotlin edit | `.\a.ps1 fw` | resources or tests also changed |
| Wear-only resource/manifest edit | `.\a.ps1 fwr` | Kotlin also changed |
| Wear-only logic change with tests | `.\a.ps1 fwu` | packaging proof needed - then `:wear:assembleDebug` |
| Flavor-visible resources / flavor source sets | `.\a.ps1 fc -Flavor <name>` per affected flavor | packaging proof needed on that flavor |

## Anti-patterns

Avoid these habits:

- running `.\a.ps1 d` for every Kotlin change
- running `clean` to "be safe" in normal loops
- using `dav` during normal development
- jumping to the full unit suite before a targeted test
- using standard fast checks for `noLegal` tasks
- proving a `wear/` change with `fk`/`fr`/`fc`/`fu` - they check `app_v2` and pass without touching the watch module (S1807)
- wiping all Gradle caches before trying targeted KAPT recovery

## Quick examples

### Change a ViewModel function

```powershell
.\a.ps1 fk
```

### Change a layout and its fragment code

```powershell
.\a.ps1 fc
```

### Fix one repository bug with existing tests

```powershell
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.data.SomeRepositoryTest"
```

### Verify the app still installs and launches

```powershell
.\a.ps1 db
.\a.ps1 adb install -Flavor standard
.\a.ps1 adb launch
```

## Decision summary

Use targeted checks by default, full builds only when the changed surface demands packaging or runtime proof, and full suites only when the blast radius is broad.
