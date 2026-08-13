# Tactical Plan: S0243 - cloud-auth-result-channel

**Strategic spec:** [`../S0243_cloud-auth-result-channel.md`](../S0243_cloud-auth-result-channel.md)
**Feature:** Unified async result channel for interactive cloud authentication
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 90
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-19

> **Scope:** tactical, English, developer handoff. Every step has a Glob/Grep verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | interactive-auth-contract | - | ✅ Done | 5/5 | [PHASE_01__interactive-auth-contract.md](PHASE_01__interactive-auth-contract.md) |
| 02 | orchestrator-unification | 01 | ✅ Done | 4/4 | [PHASE_02__orchestrator-unification.md](PHASE_02__orchestrator-unification.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All four strategic §6 open research items are resolved by this tactical plan. The decisions below are baked into the phase prompts.

- [x] **Research §6.1 - Channel semantics.** Decision: per-plugin `MutableSharedFlow<AuthResult>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)`. Orchestrator consumes the next emission via `first()` under a per-attempt subscription `Job`. Rationale: works uniformly for sync-callback (OneDrive MSAL), async-coroutine (Google Credential Manager), and lifecycle-driven (Dropbox onResume) producers; a new attempt simply cancels the prior subscription job (handles strategic §6.1's "4 clicks in a row" case). One-shot `Channel<AuthResult>` was rejected because it forces per-attempt construction inside plugins and makes prior-job cancellation harder to reason about - `SharedFlow` + `Job.cancel()` is the idiomatic primitive in this codebase.
- [x] **Research §6.2 - MSAL/Dropbox compatibility.** Decision: keep poll/intent/resume as private detail of each plugin. The contract surface becomes `startInteractiveSignIn(Activity)` + `results: SharedFlow<AuthResult>` + lifecycle hooks `onIntentResult(Intent?)` and `onResume()` that return `Unit` (each plugin emits to its own `results` flow when its SDK signals completion). Rationale: Dropbox legitimately requires `onResume()` to call `finishAuthentication()` (no callback API); OneDrive's MSAL callback becomes a direct `tryEmit`; Google Credential Manager emits from its launched coroutine. Removing the lifecycle hooks would force a Dropbox rewrite that is out of scope.
- [x] **Research §6.3 - Cancellation / Activity lifecycle.** Decision: subscription job lives on `appScope` (Singleton orchestrator). Each `startInteractiveSignIn` calls `subscriptionJob?.cancel()` before launching a new one. Single timeout `INTERACTIVE_AUTH_TIMEOUT_MS = 300_000L` (5 min) lives in the orchestrator; `withTimeoutOrNull` returns `null` → orchestrator routes a synthesized `AuthResult.Error("timeout")` to `processPluginResult`. If the Activity is not in foreground when the result arrives, `MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)` already buffers one event for the next collector - preserves current UX (continue at folder picker on return).
- [x] **Research §6.4 - identity-domain compatibility.** Decision: the new `results` channel emits **one final result per attempt** (terminal). `identityRepository.state` remains the ambient observable for "who is currently signed in" (consumed by the Settings card). The orchestrator subscribes only to the new channel; identity state is updated independently inside the Credential Manager plugin's success branch (existing behavior - unchanged). The split is documented in a KDoc paragraph on `InteractiveCloudAuthenticator.results`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - **SKIPPED**. Strategic §8: "Без изменений в `docs/FEATURES`" - internal refactor with identical user-visible behavior.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - public API of `InteractiveCloudAuthenticator` changed.
- [ ] `/spec-check S0243` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/3 done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0243`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-05-19 - Initial tactical plan authored by `/spec-tech`.
