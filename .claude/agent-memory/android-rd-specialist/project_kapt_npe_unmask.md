---
name: kapt-npe-unmask
description: How to unmask a stackless kapt NullPointerException ("Cannot read field tree") on this AGP9/legacy-kapt toolchain
metadata:
  type: project
---

A kapt failure that shows only `Caused by: java.lang.NullPointerException` (no `at ...` frames) from `AnnotationProcessingKt.doAnnotationProcessing` is a MASKED error, not a toolchain/daemon bug. Cleaning caches, resetting daemons, `kapt.use.worker.api`, `kapt.incremental.apt`, and JDK swaps will NOT fix it - they did nothing in the S0566 incident.

**Why:** `kapt { correctErrorTypes = true }` (app_v2/build.gradle.kts) converts real javac stub-processing errors into a stackless NPE, and the JVM's `OmitStackTraceInFastThrow` optimization strips the stack after the NPE is thrown a few times. The real cause is usually a malformed kapt stub - e.g. a genuine Kotlin error that the error-tolerant stub generator emits anyway, then javac chokes on it (cascades into bogus `duplicate class` errors across the whole stub set).

**How to apply:** two-step unmask, both reversible:
1. Append `-XX:-OmitStackTraceInFastThrow` to `org.gradle.jvmargs` in gradle.properties (kapt runs in the Gradle daemon JVM because `kapt.use.worker.api=false`) -> reveals the NPE message (e.g. `Cannot read field "tree"`).
2. Temporarily set `correctErrorTypes = false` and rerun the kapt task -> javac now prints the real `error:` lines pointing at the offending stub `.java` under `app_v2/build/intermediates/built_in_kapt_stubs/<variant>/...`. Look for the line `Running non-incrementally because analyzing <X>.java failed` - `<X>` is the trigger class. Read its generated stub for the anomaly (S0566: two `companion object`s in one class produced `Companion` + `Companion$1` static fields).

kapt runs BEFORE compileKotlin, so a kapt NPE can hide ordinary Kotlin compile errors; once kapt is fixed, compileKotlin may surface a separate batch of real errors.
