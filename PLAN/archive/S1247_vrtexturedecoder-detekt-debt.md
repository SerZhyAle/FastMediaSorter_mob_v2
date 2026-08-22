# S1247 - VrTextureDecoder carries detekt debt that is absent from the baseline

**Status:** Archived
**Priority:** 40
**Date:** 2026-07-28 (captured), 2026-08-21 (premise re-verified)
**Tier:** 1 - Quick Win (ad-hoc)

<!-- auto-approved by /spec-do - 2026-08-21 -->

## 0. Raw capture

Found 2026-07-28 while closing S1221 (VR stale-frame fix). Out of scope of that ticket, parked per CLAUDE.md 3.1.

The diff-scoped detekt gate flags `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/VrTextureDecoder.kt` for eight findings, none of which appear in `config/detekt/baseline-app_v2.xml` - the file has **zero** baseline entries:

- `ExplicitGarbageCollectionCall` x2 - `System.gc()` and `System.runFinalization()` at lines 40-41.
- `MagicNumber` x5 - `1024` x2 in the budget log line, `4L` and `4` in the byte arithmetic, `8` as the sample-size cap.
- `StringTemplate` x2 - redundant braces in `${sampledH}` at lines 109 and 161.

## 1. The debt is not from S1221

S1221 added one private method (`retryWithoutPool`) plus two log lines, all below line 200. Every flagged line is older code:

- 38-41 is the OOM fallback inside `getReusableDirectBuffer`.
- 109 is the S0960 bundled-asset preflight log.
- 160-161 is the decode-budget log inside `decodeFilePooled`.
- 223-229 is `pickSampleSizeForBudget`.

The S1221 edit shifted their line numbers, which is why the gate noticed them now.

## 2. Two of the eight need a decision, not a rename

Most are mechanical - name the constants, drop the redundant braces. The `System.gc()` / `System.runFinalization()` pair is not:

```kotlin
} catch (oom: OutOfMemoryError) {
    Timber.w(oom, "getReusableDirectBuffer: OOM allocating direct buffer of size $size, trying GC...")
    System.gc()
    System.runFinalization()
    ByteBuffer.allocateDirect(size).also { reusableDirectBuffer = it }
```

This is a deliberate last-ditch reclaim before retrying a large direct allocation on a headset, not carelessness. detekt is right that the call is a smell and probably wrong that it should be removed here. The ticket must decide between suppressing it with the rationale in the annotation and finding a non-`gc()` way to bound the allocation - and must not silently delete a working OOM fallback to turn a bar green.

Note when suppressing: the file has no baseline entries at all, so adding a `@Suppress` here cannot shift a baselined signature (the hazard recorded in S0826).

## 3. This is the second instance of one mechanism

**S1198** is the same finding on `StreamsActivity.kt`, captured two days earlier, from the same cause: a file accumulated findings, the baseline was never refreshed, and the next person to touch the file inherits a red gate for code they did not write.

Whoever picks up either ticket should look at whether the answer is per-file at all. A sweep that re-freezes the baseline, or a report of every file whose current findings are absent from it, would close both and prevent the third - and would be worth more than two hand-fixed files. That call belongs to whoever schedules this, not to this capture.

## 4. Related

- **S1198** - `streamsactivity-detekt-debt`, same mechanism, different file.
- **S1186** - `detekt-importordering-homewidgetcatalog`, another baseline-drift symptom.
- **S1221** - the ticket whose closure surfaced this.
## 5. What changed since the capture (verified 2026-08-21)

The premise decayed, and it decayed in the direction that matters.

- **The file is no longer red.** `detekt-scoped.ps1` over it: `PASS - 1 file(s), no new finding under the full configured rule set`.
- **It is green because the findings were baselined, not fixed.** `config/detekt/baseline-app_v2.xml` now carries **seven** entries for this file where §0 recorded zero: `ExplicitGarbageCollectionCall$gc()`, `ExplicitGarbageCollectionCall$runFinalization()`, `MagicNumber$1024`, `MagicNumber$4`, `MagicNumber$4L`, `MagicNumber$8`, `StringTemplate$${sampledH}`.

So the symptom §0 was written about - "the next person to touch the file inherits a red gate for code they did not write" - is gone. What is left is the thing §2 warned against, arrived at by the other route: **the `System.gc()` decision was never made. It was suppressed.** §2 said the ticket must not silently delete a working OOM fallback to turn a bar green; baselining turns the same bar green while leaving no trace at the call site of why the call is deliberate.

That also means the S0826 hazard §2 dismissed is now live: the file has baseline entries, so edits here can shift baselined signatures.

---

## 6. Goal

Мелкие находки чинятся по-настоящему, а не прячутся, и единственное решение, которое здесь есть, становится видимым там, где его прочитают - у самого вызова. Записей в baseline для этого файла становится меньше, а не больше.

---

## 7. Phases

### Phase 01 - Fix the mechanical findings and make the one decision visible

**Objective:** name the three unnamed numbers, drop the redundant braces, move the garbage-collection rationale from the baseline into the code, and delete the baseline entries that stop applying.

**Files touched:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/VrTextureDecoder.kt`, `config/detekt/baseline-app_v2.xml`.

#### Step 01.1 - Name the numbers in `pickSampleSizeForBudget`

**Prompt for developer:**

> In `pickSampleSizeForBudget`, replace the literal `4L` with the existing `RGBA_BYTES_PER_PIXEL` constant widened to `Long`, give the loop's `bytes /= 4` its own named constant - it is the area reduction per doubling of the sample size, four, and is a different four from the bytes-per-pixel one - and replace the `sample < 8` cap with a named `MAX_SAMPLE_SIZE`. The KDoc above the function already explains why the cap is 8; point it at the constant instead of repeating the number.

**Why:**

Two of the three literals are the digit `4` meaning two unrelated things four lines apart, which is the case `MagicNumber` exists to catch, and the capture recorded them as mechanical - a rename, not a decision.

**Verification:**

- `Grep` - `4L` and `/= 4` no longer appear in the function.
- `Grep` - `MAX_SAMPLE_SIZE` is declared once in the companion object and used once in the loop condition.
- The vr flavor compiles.

#### Step 01.2 - Name the megabyte divisor and drop the redundant braces

**Prompt for developer:**

> Replace `MAX_DECODE_BYTES / (1024 * 1024)` in the decode-budget log with a named `BYTES_PER_MB` constant, and change `${sampledH}` to `$sampledH` where the braces enclose a bare identifier.

**Why:**

`1024 * 1024` in a log line is the same unnamed quantity the existing `MAX_DECODE_BYTES` comment already spells out in megabytes, and a brace around a bare name is what `StringTemplate` reports.

**Verification:**

- `Grep` - `1024` does not appear outside the companion object.
- `Grep` - `${sampledH}` returns zero hits; `$sampledH` is present.
- The vr flavor compiles.

#### Step 01.3 - Move the garbage-collection rationale to the call site

**Prompt for developer:**

> Annotate the OOM fallback in `getReusableDirectBuffer` with `@Suppress("ExplicitGarbageCollectionCall")` and put the reason next to it: this is a last-ditch reclaim before retrying a large direct allocation on a headset, taken only after the allocation has already thrown, and the alternative is failing the frame. Do not remove the fallback. Then delete the two `ExplicitGarbageCollectionCall` entries for this file from the detekt baseline.

**Why:**

The capture's §2 required this to be a decision rather than a silent green bar, and a baseline entry is silent by construction - it lives in a generated XML nobody reads at the call site, whereas a suppression carries its reason to the next person who reads the fallback.

**Verification:**

- `Grep` - `@Suppress("ExplicitGarbageCollectionCall")` appears once in the file, with a comment stating the reason.
- `Grep` - `ExplicitGarbageCollectionCall:VrTextureDecoder` returns zero hits in `config/detekt/baseline-app_v2.xml`.
- `System.gc()` and `System.runFinalization()` are both still present - the fallback was not deleted.

#### Step 01.4 - Remove the baseline entries that stopped applying, and close

**Prompt for developer:**

> After the renames, the `MagicNumber` and `StringTemplate` entries for this file no longer match anything. Delete exactly those entries and re-run the scoped gate; it must still pass without them. Then close through `scripts/post-change.ps1` with `-ScopeToFile -ChangeType Mixed`.

**Why:**

A baseline entry whose subject no longer exists is indistinguishable from one that is still suppressing something real, and leaving it behind means the next reader cannot tell which of the seven were ever decided.

**Verification:**

- `Grep` - `VrTextureDecoder` returns zero hits in `config/detekt/baseline-app_v2.xml`.
- `detekt-scoped.ps1 -ChangedFiles <the file>` passes.
- `post-change.ps1` exits 0.

---

## 8. Done criteria

1. The scoped detekt gate passes on the file with **no** baseline entries for it.
2. The OOM fallback still calls `System.gc()` and `System.runFinalization()`, and the reason is readable at the call site rather than in a generated XML.
3. Every number the gate flagged is either named or gone; none is suppressed.

---

## 9. What this ticket deliberately does not do

- It does not sweep the baseline repository-wide. §3 of the capture proposed that, and it is still the better answer at scale - but it is a different ticket with a different risk profile, and doing it here would hide this file's one real decision inside a bulk regeneration.
- It does not touch **S1198** (`streamsactivity-detekt-debt`), the same mechanism on another file. Whether that one decayed the same way is unchecked here.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1198 - same mechanism on `StreamsActivity.kt`, deliberately untouched here; S1221 - the ticket whose closure surfaced this; S1186 - another baseline-drift symptom. None is a blocker.
- **Validation level:** run. The scoped detekt gate must pass with zero baseline entries for the file, and the vr flavor must compile.
- **Owner sign-off:** n/a - debt cleanup with no user-visible surface and no owner decision; the one judgement call (keep the OOM fallback) was already made in the capture.

---

## 10. Outcome (2026-08-21)

All four steps applied in one change. Evidence:

- **Seven baseline entries removed**, all of them: both `ExplicitGarbageCollectionCall`, four `MagicNumber`, one `StringTemplate`. `Grep` for `VrTextureDecoder` in `config/detekt/baseline-app_v2.xml` now returns **0**.
- **The scoped gate passes without them:** `detekt-scoped.ps1` over the file -> `PASS - 1 file(s), no new finding under the full configured rule set (3.3s)`, exit 0. This is the difference that matters: before, the file was green *because* seven findings were suppressed; now it is green because there is nothing to suppress except one documented decision.
- **The OOM fallback is intact.** `System.gc()` and `System.runFinalization()` are still at lines 45-46, now under `@Suppress("ExplicitGarbageCollectionCall")` with four lines of reason immediately above: the calls run only after `allocateDirect` has already thrown, as the last reclaim before one retry on a headset where the alternative is failing the frame.
- **The two fours are named apart.** `RGBA_BYTES_PER_PIXEL` (bytes per pixel) and the new `SAMPLE_STEP_AREA_DIVISOR` (area reduction per doubling of `inSampleSize`) sat four lines apart as the same digit - which is precisely why `MagicNumber` flagged them and why naming was the right fix rather than a suppression. Also added: `MAX_SAMPLE_SIZE`, `BYTES_PER_MB`.
- **Compiles on the flavor that owns the file:** `check-standard-fast.ps1 -Mode Code -Flavor Vr` -> `BUILD SUCCESSFUL in 3m 10s`, exit 0. `fk` would not have covered it - this file lives in `src/vr`.
- `post-change.ps1 -ScopeToFile -ChangeType Mixed` -> `post-change: PASS`, exit 0.

### On the premise that decayed

§0 was captured 2026-07-28 against a red gate and zero baseline entries. By 2026-08-21 the gate was green and the entries were seven: someone re-baselined the file in between. Had the ticket been executed on its written premise it would have found nothing to do and closed as stale. The substance survived the decay only because §2 had named the one thing that was not mechanical - and that thing had been silently suppressed rather than decided. A debt ticket is worth re-reading against the tree before it is worth closing.
