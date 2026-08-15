# Compact specification: S0959 - Debug StrictMode policy hardcoded off

**Ticket:** S0959
**Status:** Archived
**Priority:** 30
**Date:** 2026-07-05
**Tier:** 2 - Small
**Related tickets:** S0905

<!-- auto-approved by /spec-all - 2026-07-11 -->
<!-- parked by S0905 audit sweep (Layer 5) - 2026-07-05 -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-05, из S0905 Layer 5 startup sweep.

Symptom: `FastMediaSorterApp.setupDebugStrictMode()` defines `StrictMode.ThreadPolicy` and
`StrictMode.VmPolicy`, but a hardcoded `ENABLE_DEBUG_STRICT_MODE = false` keeps the hook
unreachable in debug builds.

Evidence:
- `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` declares
  `ENABLE_DEBUG_STRICT_MODE = false`.
- `setupDebugStrictMode()` still installs `penaltyLog()` ThreadPolicy/VmPolicy when the guard
  allows it.
- `core/debug/StrictModeHelper.kt` already wraps intentional main-thread disk access with
  temporary permit-restore helpers.

Severity: P3 - dead debug safety hook, no user-facing runtime defect.

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - implementation through `/spec-all`.
- **Goal / expected outcome:** Provided by user - make the existing DEBUG StrictMode hook run so
  main-thread disk/network regressions surface again during development.
- **Local anchor:** Delegated by user - /spec-all auto-approval - `FastMediaSorterApp.setupDebugStrictMode()`
  and companion helper `core/debug/StrictModeHelper.kt`.
- **Scope boundaries / forbidden areas:** Delegated by user - /spec-all auto-approval - limit the
  change to the existing app startup StrictMode guard and this ticket documentation; do not redesign
  startup, add new policies, or touch release behavior.
- **Done / success signal:** Delegated by user - /spec-all auto-approval - DEBUG builds no longer
  short-circuit `setupDebugStrictMode()` via a hardcoded false flag, and the ticket records the
  residual manual runtime triage note.
- **Autonomy rule:** Delegated by user - /spec-all auto-approval - agent may decide with explicit
  assumptions.
- **UI decisions / delegation:** Delegated by user - /spec-all auto-approval - no UI impact.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0905
- **Policy choice:** Keep the StrictMode hook and flip it on for DEBUG builds; do not remove the
  policy code.

## Goal

Вернуть работающий DEBUG StrictMode-хук в `FastMediaSorterApp`, чтобы существующие
`penaltyLog()`-политики снова ловили main-thread disk/network нарушения в дев-сборках.
Релизное поведение и пользовательский runtime не меняются, потому что защита остаётся под
`BuildConfig.DEBUG`.

## Root cause

The codebase already contains a non-fatal StrictMode policy and a whitelist helper for intentional
startup disk I/O, but the top-level feature flag was hardcoded to `false`. That turned the entire
debug detector into dead code and hid future regressions from the intended runtime signal.

## Phase 01 - Re-enable the existing hook

- Change `ENABLE_DEBUG_STRICT_MODE` in
  `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` from `false` to `true`.
- Preserve the existing `BuildConfig.DEBUG` gate and `penaltyLog()` behavior.
- Verification: `.\a.ps1 fk`

## Phase 02 - Confirm the guardrail contract

- Re-check that intentional startup disk I/O remains wrapped by `StrictModeHelper`.
- Record that fresh runtime log triage is a follow-up observation, not a blocker for enabling the
  hook.
- Verification: static audit of `FastMediaSorterApp.kt` and `core/debug/StrictModeHelper.kt`

## Validation

- PASS - `.\a.ps1 fk` compiled the touched Kotlin source successfully on 2026-07-11.
- PASS - `FastMediaSorterApp.kt` now sets `ENABLE_DEBUG_STRICT_MODE = true`.
- PASS - `setupDebugStrictMode()` still remains behind `BuildConfig.DEBUG`, so release behavior is
  unchanged.
- PASS - `core/debug/StrictModeHelper.kt` still provides the documented permit-restore wrappers for
  intentional startup disk I/O.
- MANUAL - a future debug run should observe the surfaced StrictMode backlog and wrap any newly
  intentional main-thread disk access instead of re-disabling the hook.

## Last Audit

**Date:** 2026-07-11
**Mode:** compact full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 - WARN 0 - FAIL 0 - MANUAL 1 - EXEMPT 0

### Manual / follow-up

- [ ] On the next emulator/device debug session, review StrictMode `penaltyLog()` output and ticket
  any newly surfaced intentional accesses that still need `StrictModeHelper` wrapping.
