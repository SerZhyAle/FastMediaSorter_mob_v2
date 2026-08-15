# Phase 03 - Send to Telegram via Bot API (optional)

**Strategic spec:** [`../S0303_telegram-integration.md`](../S0303_telegram-integration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⏭️ Skipped
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

> **⏭️ SKIPPED (2026-05-30, owner decision §6.3).** First iteration is share-intent only; the Bot API upload path is deferred. This phase is kept on record for a future extension. Re-open by setting status back to ⛔ Blocked and resolving §6.4 (Bot API config UX) when the owner decides to add it.

---

## Objective

Introduce a "share target" abstraction so a file can be uploaded to a Telegram chat through the official Bot API with a user-supplied token, without an installed Telegram client. The contract and a No-Op default live in shared code; the real Bot API implementation lives in `noLegal`; the token lives in the existing encrypted secret store.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §6.3 and §6.4 decisions Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/share/TelegramUploadTarget.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/NoOpTelegramUploadTarget.kt` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/TelegramUploadModule.kt` | New | ≤ 50 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/share/nolegal/BotApiTelegramUploadTarget.kt` | New | ≤ 250 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalTelegramUploadModule.kt` | New | ≤ 50 |

> Flavor isolation per `dev/FLAVOR_DEVELOPMENT_RULES.md`: interface + No-Op default + default Hilt binding in `src/main`; the real `BotApiTelegramUploadTarget` and its overriding Hilt module in `src/noLegal/java/`. No `BuildConfig` gate anywhere. Settings token-entry UI is scoped after §6.4 is resolved and added as steps then.

---

## Steps

### Step 03.1 - Define the upload-target contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/share/TelegramUploadTarget.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Declare an interface for uploading a local file to a Telegram chat, returning a result that distinguishes success, no-configuration, transport failure, and limit-exceeded. No Android imports beyond what the contract needs.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `interface TelegramUploadTarget` matches exactly once.

**Status:** `[ ]` not done

---

### Step 03.2 - Ship a No-Op default and default binding in shared code

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/share/NoOpTelegramUploadTarget.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/di/TelegramUploadModule.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Implement a No-Op `TelegramUploadTarget` that always returns the no-configuration result, and bind it as the default in a shared Hilt module. Flavors without a Bot API implementation get this binding.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `class NoOpTelegramUploadTarget` matches exactly once.
- `Grep` - `@Binds` present in `TelegramUploadModule.kt`.

**Status:** `[ ]` not done

---

### Step 03.3 - Implement the Bot API upload in noLegal

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/share/nolegal/BotApiTelegramUploadTarget.kt`, `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalTelegramUploadModule.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Implement `TelegramUploadTarget` against the official Bot API `sendDocument` / `sendMedia` endpoints using the user-supplied token read from the existing encrypted secret store. Report progress, map Bot API errors and size limits to the result type. Bind it in a `noLegal` Hilt module that overrides the shared default. Never log the token. `Timber` only.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `class BotApiTelegramUploadTarget` matches exactly once.
- `Grep -n "Log\.d\("` - zero hits in both files.
- `/build` - `assembleNoLegalDebug` assembles.

**Status:** `[ ]` not done

---

### Step 03.4 - Route the player/Browse send action through the upload target when configured

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerShareManager.kt` (and/or Browse share helper)
**Depends on:** Step 03.3

**Prompt for developer:**

> When a Bot API token is configured, offer the Bot API upload alongside the share-intent path from Phase 01; otherwise only the share-intent path is shown. Surface progress/result via the existing communication style (`docs/COMMUNICATION_POLICY.md`).

**Verification:**

- `Grep` - `TelegramUploadTarget` injected/referenced in the touched UI helper.
- `/build` - `standardDebug` and `noLegalDebug` assemble (No-Op on standard, real on noLegal).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done` (or the phase is `⏭️ Skipped` per §6.3).
- [ ] Project compiles - run `/build` (`standardDebug` + `noLegalDebug`).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`BotApiTelegramUploadTarget` and `NoLegalTelegramUploadModule` are `noLegal`-only - flag via `set.ps1 -NoFlavors` in Phase 04.

---

## Rollback Plan

Revert phase commit(s) - the share-intent path from Phase 01 remains intact; the No-Op default means no behavior change on any flavor after revert.
