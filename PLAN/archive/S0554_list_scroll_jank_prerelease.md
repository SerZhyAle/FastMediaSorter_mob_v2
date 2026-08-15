**Status:** Archived

# S0554 - List-scroll jank flagged by pre-release sweep

## 0. Capture (raw, unverified)

Auto-parked by `/spec-prerelease` sweep (device `emulator-5556`, Pixel 6 AVD, Android 13), run `20260620_015505`.

Symptom
- The `list-scroll` perf checkpoint failed the verdict gate: `79.35%` janky frames vs the `20%` configured limit.
- Verdict aggregator returned content FAIL (exit 1) solely on this metric; log dimension clean (0 actionable errors, no crash), screenshots clean.

How it was measured
- Resource: `virtual://all_docs`, 77-file mixed-type document list.
- `dumpsys gfxinfo <pkg> reset`, then three mobile-mcp (UiAutomator) swipes (up, up, down), then `prerelease-measure.ps1 -Checkpoint list-scroll` reading `gfxinfo` janky %.
- Result: `{"checkpoint":"list-scroll","measured":79.35,"limit":20,"pass":false}`.

Evidence
- Metrics: `temp/s0484_metrics_20260620_015505.json`.
- Run log: `temp/s0484_run_20260620_015505.log`.
- Report: `temp/s0484_prerelease_20260620_015505.md`.

## 1. Root cause

- This is a harness/threshold artifact, not an app scroll regression.
- `dumpsys gfxinfo` janky% is structurally inflated on an emulator because rendering goes through software / host-GPU paths that miss far more frame deadlines than real device hardware.
- The mobile-mcp swipes are driven by UiAutomator, which injects its own frame work into the measured window.
- The `20%` limit was a self-labeled placeholder (`prerelease.config.psd1`: "emulator-aware starter set; refine after baseline"), with no real-device baseline to anchor it.
- All independent signals were clean: 0 actionable log errors, no crash, screenshots clean. Only this one emulator-bound metric tripped the gate.

## 2. Decision

- Owner decision (2026-06-20): make `list-scroll` advisory on emulators - measured and reported, but not release-gating. It still gates on physical devices.
- Rejected alternatives: recalibrating the emulator threshold (no baseline, the number would be a guess) and deferring to a physical-device profiling pass (treats a structurally invalid emulator metric as possibly real).

## 3. Fix applied

- `scripts/devtest/prerelease-measure.ps1`: added `Test-IsEmulator` (qemu / ranchu / goldfish / SDK-image detection via `getprop`). For `list-scroll` on an emulator the record now carries `advisory: true` and the call exits `0` regardless of the raw janky% (the aggregator owns gating). The raw `measured` / `pass` are kept for transparency.
- `scripts/devtest/prerelease-verdict.ps1`: the perf fold skips `advisory` records from the FAIL set and lists them under `breakdown.perf.advisory`. Records without the field gate exactly as before.
- `scripts/devtest/prerelease.config.psd1`: documented the advisory behavior on the `ListScroll` threshold and removed the dead `MaxFrameMs = 700` field (never read by any script).
- `.claude/commands/spec-prerelease.md` and `.github/prompts/spec-prerelease.prompt.md`: documented that the list-scroll record is advisory on emulator.

## 4. Validation

- Parse: `prerelease-measure.ps1`, `prerelease-verdict.ps1`, `prerelease.config.psd1` all parse clean; config exposes `ListScroll` keys `Metric,Limit` (dead key gone).
- Verdict, advisory record (emulator, over-threshold): perf PASS, list-scroll under `breakdown.perf.advisory`, verdict PASS, exit `0`.
- Verdict, non-advisory record (physical, over-threshold): perf FAIL, list-scroll under `failures`, verdict FAIL, exit `1`.
- Verdict, original run metrics (no `advisory` key): still FAIL, exit `1` - legacy gating behavior preserved.
- Live `prerelease-measure.ps1 -DeviceId emulator-5556 -Checkpoint list-scroll`: `advisory:true`, exit `0` (emulator detected via model `sdk_gphone64_x86_64`).

## 5. Deferred follow-up (optional, non-blocking)

- A real-device scroll-performance baseline for the 77-item mixed document list (`gfxinfo`, ideally Perfetto, over a deterministic scroll) would let the `ListScroll` threshold gate meaningfully on physical hardware.
- Not required for this ticket: the emulator FAIL is resolved as an artifact, and the metric already gates on physical devices once a device runs the sweep.
