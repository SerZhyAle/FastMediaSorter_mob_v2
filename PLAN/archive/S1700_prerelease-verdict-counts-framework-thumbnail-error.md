# S1700 - prerelease verdict counts framework thumbnail error

**Status:** Archived

## 0. Raw capture

Sweep `/spec-prerelease` on 2026-08-15 (emulator-5554, API 35, standard-debug `v2.60.8151.612-DEBUG`) returned
`pass=false` with `actionableErrors=1`, while Maestro was 21/21 green, no crash, no ANR, no error toast.

The single counted error is framework-emitted, from a handled remote-thumbnail timeout:

```
19:18:29.363 W NetworkVideoFrameDecoder: [scope=thumbnail failureClass=timeout] Extraction TIMEOUT after 10000ms for 20260629_182628.mp4 - cancelling
19:18:29.364 D NetworkVideoFrameDecoder: MediaMetadataRetriever force-released after timeout
19:18:29.365 W NetworkVideoFrameDecoder: [scope=thumbnail protocol=sftp resource=sftp://193.178.50.43:22 failureClass=timeout playbackActive=false] Extraction failed: 20260629_182628.mp4
19:18:29.366 E FrameDecoder: failed to get video frame (err -1004)          <- pid 519, mediaserver
19:18:29.515 E StagefrightMetadataRetriever: all codecs failed to extract frame.
19:18:29.516 E MetadataRetrieverClient: failed to capture a video frame
19:18:29.517 E MediaMetadataRetrieverJNI: getFrameAtTime: videoFrame is a NULL pointer   <- app pid, framework JNI
19:18:29.518 W NetworkVideoFrameDecoder: getFrameAtTime returned null for 20260629_182628.mp4, skipping fallback
```

App-side handling is correct: the decoder cancels on its own 10 s budget, force-releases the retriever,
logs the outcome as `W` with a structured scope, and skips the fallback. Only the platform layer logs `E`,
and `MediaMetadataRetrieverJNI` does so inside the app process, so `search-log.ps1 -AppOnly` attributes it
to the app.

Evidence: `temp/S0484/run_20260815_184559.log`, `temp/S0484/log_audit_20260815_184559.json`,
`temp/S0484/prerelease_20260815_184559.md`.

## 1. Problem

`scripts/devtest/prerelease-verdict.ps1` has no `expectedFallbacks` entry for the framework thumbnail-failure
chain, so any sweep whose media set contains one slow remote video ends in a red verdict that names nothing
the app can fix. That is the S1391 class of noise the list already exists to absorb.

## 2. Decision

Suppress the chain **conditionally**, never by tag alone. An unconditional allowlist entry would also hide a
real regression in local thumbnail decoding, which logs the identical framework chain.

- The guard is the app's own handled-timeout marker: `NetworkVideoFrameDecoder` + `Extraction TIMEOUT` or
  `getFrameAtTime returned null`, matched anywhere in the same capture.
- With the marker present, the four framework markers join the expected set; without it they keep failing the gate.
- The same guard classifies the chain as benign in `prerelease-log-audit.ps1`, so it stays visible in the
  benign list rather than being dropped from the report.
- The guard reads the log with `Select-String -List` inside the script, not another `search-log.ps1` pass -
  a ~300k-line capture costs minutes per pass and the gate already runs four.

Not in scope: whether a 10 s thumbnail budget over SFTP is the right value, and whether the remote path should
degrade to the placeholder sooner. The sweep's own report records it and nothing depends on it.

## 3. Owner inputs

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1391 (the emulator-noise entries this list already carries), S0484 (the sweep and its verdict aggregator), S0976 (the audit's benign allowlist).
- **Owner ruling 2026-08-15:** fix the pre-release blocker now rather than park it for a later round ("раз ты разобрался - почини"). No further owner decision was requested or given.
- **UI scope:** none - repository tooling only, no app runtime code, no user-visible surface.
- **Flavor scope:** none - the sweep runs standard-debug and the change is flavor-independent.
- **Data scope:** none - no schema, no persisted state.
- **Executable scope:** `scripts/devtest/prerelease-verdict.ps1`, `scripts/devtest/prerelease-log-audit.ps1`, `.claude/reference/spec-prerelease.md`.

## 4. Implementation

- `scripts/devtest/prerelease-verdict.ps1`: added `$handledThumbnailTimeoutPattern` +
  `$guardedThumbnailFallbacks`, applied them to the expected-error pattern only when the marker is present,
  and reported `thumbnailTimeoutHandled` in the log breakdown so a suppressed run says so out loud.
- `scripts/devtest/prerelease-log-audit.ps1`: same guard, folded into the cluster benign test.
- `.claude/reference/spec-prerelease.md` §4.1: documented that this suppression is conditional and why.

## 5. Validation

- `prerelease-verdict.ps1` re-run over the unchanged 2026-08-15 capture: expected exit 0 / `pass=true`,
  `actionableErrors=0`, replacing exit 1 / `actionableErrors=1`.
- `prerelease-log-audit.ps1` re-run over the same capture: actionable clusters 11 -> 8, benign 7 -> 10,
  toasts 0. The three that moved are `FrameDecoder`, `MetadataRetrieverClient`, `MediaMetadataRetrieverJNI`.
- Negative case: a capture without the `NetworkVideoFrameDecoder` marker leaves the chain actionable, which
  is what the guard exists for.
