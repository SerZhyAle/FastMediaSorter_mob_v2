# S1246 - CameraCaptureSaver reports Success where its test expects a Generic failure

**Status:** Archived
**Priority:** 60

## 0. Raw capture

Found on 2026-07-28 during the S1220 verification run of `.\a.ps1 fu`. Unrelated to that ticket (streams atlas slicers), parked per CLAUDE.md 3.1.

`app_v2/src/test/java/com/sza/fastmediasorter/data/capture/CameraCaptureSaverTest.kt` - 1 of 6 cases fails:

```
tests="6" skipped="0" failures="1" errors="0"

failed upload yields Generic failure and still deletes temp
java.lang.AssertionError:
  expected:<Generic>
  but was:<Success(savedPath=..\DCIM\Camera\fail.jpg, copiedToClipboard=false, fallbackReason=ResourceWriteFailed)>
  at CameraCaptureSaverTest.kt:166
```

## 1. What the mismatch says

The test asserts that a failed upload surfaces as a `Generic` failure. The saver instead returns `Success` carrying `fallbackReason = ResourceWriteFailed` - i.e. the upload failed, the saver fell back to a local write, the local write succeeded, and the overall verdict became success-with-a-reason.

That is a coherent design; it is also a different contract from the one the test was written against. So this is not obviously a bug in either direction, and that ambiguity is the whole ticket.

## 2. The question research has to settle

Which contract is the intended one, and what does the user see?

- If the fallback is intended, the test is stale and should assert `Success` + `fallbackReason = ResourceWriteFailed`, and the *user-visible* behaviour needs checking: a person whose upload failed should not be told the capture simply succeeded with no hint that it never left the device.
- If the fallback is a regression - a failure path that silently degraded into a success path - then the assertion is the last thing still holding the old contract, and "fixing" the test would delete the only remaining evidence of the regression.

Answer this from the saver's own code and from whatever surfaces `fallbackReason` in the UI before touching the test. Do not adjust the assertion to match observed behaviour first; that ordering decides the outcome by accident.

## 3. Why it is worth a ticket

It is a red test in the suite, so it also feeds the "the build is always a bit red" habit. More importantly, `fallbackReason` implies someone already thought about degraded saves - the open question is only whether the user is told. That is a communication-policy question (`docs/COMMUNICATION_POLICY*.md`), not just a test fix.

## 4. Related

- S1244 - the OOM truncation of the full suite. This failure is one of the few that happens to sit alphabetically early enough (`data.capture`) to be reached before the worker dies, which is why it is visible at all.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0522 (the fallback contract), S0369/S0465 (the era the test was written in), S1244

## 5. Resolution (2026-07-28, spec-next loop)

The §2 question is settled from the code: the fallback is the intended contract, the test is stale.

- The fallback is S0522 by design, not drift. `CameraCaptureSaver.save` marks the branch with an
  S0522 comment ("Save to the local default folder .. instead of dropping the capture; the caller
  surfaces the redirect to the user"), and `SaveResult.Success.fallbackReason` KDoc says "the
  caller turns this into a user notification".
- The user IS told. The only caller that can reach a network target is
  `BrowseCameraCaptureManager` (BrowseActivity supplies FTP/SMB/SFTP/CLOUD upload strategies);
  its Success branch calls `saveFallbackNotifier.notify(reason, folderLabel, resourceName, ..)`
  whenever `fallbackReason != null` or the pre-check found the network unreachable - the §2
  concern ("told the capture simply succeeded") does not materialize.
- The other callers cannot reach the fallback: `MainCameraCaptureManager` and
  `SaveCapturedMediaUseCase` (camera quick-capture widget path) write only to public folders
  (DCIM/Camera, Movies) with the upload hook explicitly unused - their KDoc states it.
- The exception path stays a Failure: FTP/SMB/SFTP strategies swallow their errors into `false`
  (verified on `LocalToFtpStrategy` - credentials, connect, open and upload errors all
  `return false`; the CLOUD branch maps through `Result.isSuccess`), so the saver's
  `catch` -> `Failure.Io`/`Generic` branches are a defensive belt, not a reachable upload path.
- The test predates S0522: its KDoc cites only S0369 + S0465, and the red assertion is the old
  pre-fallback contract. Per §2's warning this was checked against the saver and UI first, and
  only then was the assertion updated.

## 6. Fix

- `CameraCaptureSaverTest`: the failed-upload case now asserts the S0522 contract - `Success`
  with `fallbackReason = ResourceWriteFailed`, a `DCIM/Camera` fallback path that exists on
  disk, and the temp file deleted. Renamed to
  `failed upload falls back to local save with fallbackReason and deletes temp`.
- Class KDoc extended with S0522 so the next reader sees the fallback is part of the tested
  contract.
- No production code changed - the saver and its callers already implement the intended
  behaviour.

## 7. Verification

- Targeted run `check-standard-fast.ps1 -Mode Unit -Tests ..CameraCaptureSaverTest`:
  BUILD SUCCESSFUL, `tests=6 failures=0 errors=0 skipped=0` (was 5/6 with the stale assertion).
- expected: the class is fully green under the S0522 contract | actual: 6/6 green - PASS.

## Last Audit

**Date:** 2026-07-28. **Verdict:** Verified.

- §2's contract question resolved from code, not by adjusting the assertion first: fallback is
  S0522 by design; the only network-capable caller (Browse) surfaces the redirect via
  `SaveFallbackNotifier`; Main/widget paths cannot reach the fallback (public-folder targets,
  upload hook unused).
- Fix is test-only: stale pre-S0522 assertion replaced with the full fallback contract
  (Success + ResourceWriteFailed + DCIM/Camera path on disk + temp deleted).
- One of the suite's known reds removed from the "always a bit red" set.
