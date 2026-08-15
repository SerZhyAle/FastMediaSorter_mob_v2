# 05 - Running the real analyser on the changed files only

**Strategic §6 item:** 5 (feeds it; the divergence question itself stays Open)
**Date:** 2026-08-12
**Verdict:** VIABLE and measured. 1.3-2.5 s against the gate's ~87 s, with real findings proven.

## Mechanism

detekt's CLI accepts `--input` with an explicit file list, so the same analyser can judge only
the changed files, outside gradle entirely. Invocation shape, all flags confirmed working:

```text
java -cp <jars> io.gitlab.arturbosch.detekt.cli.Main
  --input <comma-separated files>
  --config config/detekt/detekt.yml
  --build-upon-default-config
  --plugins <detekt-formatting jar>
  --baseline config/detekt/baseline-<module>.xml
  --report xml:<path>
```

The classpath is assembled from the gradle dependency cache. Precedent exists in the repository -
`temp/archive/20260807_235658/tickets/S1129/S1129/run-detekt-file.ps1` - but it pins versions as
literals and was never promoted out of scratch, so it is a proof of mechanism, not reusable code.
The probe resolves the same jars by wildcard instead and picked up 65 of them plus the formatting
plugin.

Probe: `temp/S1595/probe-cli-cost.ps1`.

## Cost

| input | outcome | wall clock |
|---|---|---|
| 1 changed file (real, baselined) | exit 0 | 2.0-2.2 s |
| 2 changed files (real, baselined) | exit 0 | 2.4-2.5 s |
| 1 deliberately violating file | exit 2 | 1.3-1.4 s |

Against a gradle gate measured at ~87 s per run in the audit window, and 20.3 s in the fast-path
document for a single module on a warm daemon. Cold and warm rounds differed by under 0.5 s -
there is no daemon to warm, which is the point.

## Proof that it detects, not just exits

`exit 0` on two real files is not evidence of working detection - it is also what "analysed
nothing" looks like. Probe therefore ran a file authored to violate several rules at once:

```text
BadSample.kt:3:1: Imports must be ordered .. [ImportOrdering]
BadSample.kt:8:1: Exceeded max line length (120) [MaximumLineLength]
BadSample.kt:12:9: Function tooManyReturns has 4 return statements which exceeds the limit of 2. [ReturnCount]
BadSample.kt:8:1: Line detected, which is longer than the defined maximum .. [MaxLineLength]
BadSample.kt:10:51: This expression contains a magic number .. [MagicNumber]
Analysis failed with 10 weighted issues.
```

Findings span four rule families - formatting, style, complexity, potential-bugs - so coverage is
the whole configured rule set, not a subset. The configured threshold (120) is honoured, which
proves `--config` and `--build-upon-default-config` are both in effect. Exit code is 2 on
findings, 0 on none.

The two real files exiting 0 while carrying baselined findings is the evidence that `--baseline`
suppresses correctly in this mode.

## Constraints this imposes on the implementation

1. **Report path.** The probe wrote to `temp/S1595/`, deliberately. `assert-detekt.ps1` narrows a
   project-wide failure against `app_v2/build/reports/detekt/detekt.xml` and judges that file's
   staleness by mtime. Writing there would corrupt an unrelated gate's verdict.
2. **Baseline per module.** A `wear` file judged against the `app_v2` baseline would report the
   whole of `wear`'s known debt as new.
3. **Classpath is the fragile part.** It resolves from a cache whose contents follow the version
   pins. This must fail loudly as "could not verify", never quietly as "clean" - the reason
   ADR-3 requires a third outcome.
4. **No build lock.** No gradle process is involved, so the step must not take `BUILD.LOCK`.

## Still open

Whether a single-file run reports the same set as a whole-module run. Rules that depend on
seeing the whole module could differ. Divergence toward extra findings makes the step cry wolf
and must block the advisory-to-blocking flip; divergence toward misses is harmless, because the
expensive gate still runs behind it. Measured in Phase 02.
