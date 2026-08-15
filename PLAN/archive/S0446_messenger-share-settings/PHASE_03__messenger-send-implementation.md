# Phase 03 - WhatsApp & Instagram send implementation

**Strategic spec:** [`../S0446_messenger-share-settings.md`](../S0446_messenger-share-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked - see Pre-Implementation Blockers (B1/B2/B3) in INDEX
**Depends on:** Phase 02, **research B1/B2/B3 resolved**
**Blocks:** Phase 04, Phase 05 (their callbacks invoke these send methods)
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Implement "send current/selected file to WhatsApp" and "..to Instagram" on the two share surfaces (player and browse multi-select), mirroring the existing Telegram send. Telegram already works and is not re-implemented. The send routes a file URI through the existing system invoker, targeting the messenger's package, with a system-chooser fallback when the client is unavailable.

This phase is **blocked** until the messenger-API unknowns are answered (see below). The implementation shape below assumes the recommended default ("open in app with attachment"); if research overturns it, revise this phase before coding.

---

## Blocking research (must complete first)

Resolve and record in `research/01__messenger-send-limits.md`:

- **B1** - whether a sanctioned public API allows in-app recipient selection + attachment send (hypothesis: no). Decides "pick recipient" vs "open in app".
- **B2** - Instagram accepted attachment types via share intent; the message shown when a type is rejected.
- **B3** - confirmed WhatsApp client package ids/order (validates Step 02.1 catalogue).

Do not invent capabilities. If B1 is "no" (expected), the send is `ACTION_SEND` + `setPackage` + chooser fallback, identical in shape to Telegram, and no "recipient" UI is built.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (targets registered, package catalogues exist).
- [ ] `research/01__messenger-send-limits.md` written; B1/B2/B3 marked Resolved.
- [ ] Phase 01 send-failure strings exist.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerShareManager.kt` | Modified | ≤ 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseShareOperationsHelper.kt` | Modified | ≤ 380 |

---

## Steps

### Step 03.1 - Player: send current file to WhatsApp / Instagram

**Files:** `ui/player/helpers/PlayerShareManager.kt`
**Depends on:** start of phase (after research)

**Prompt for developer:**

> Add `sendCurrentFileToWhatsapp()` and `sendCurrentFileToInstagram()` mirroring the existing `sendCurrentFileToTelegram()`: resolve `currentFile`; for local files build the URI directly, for network files prepare via `networkFileManager.prepareFileForRead` on `lifecycleScope`; then call `SystemShareInvoker.invokeFiles(context, uris, mime, preferredPackage = <messenger>.firstInstalledPackage(packageManager), chooserTitle = getString(R.string.share_to_<messenger>))`. On `!launched` show the matching `share_to_<messenger>_failed` toast. Factor shared staging logic if it keeps each method small; do not duplicate the whole Telegram body three times if a private helper reads cleaner. For Instagram, set the mime per B2 (image vs `*/*`) and surface the B2 reject message when applicable.

**Verification:**

- `Grep` - `fun sendCurrentFileToWhatsapp` and `fun sendCurrentFileToInstagram` present.
- `Grep` - `WhatsAppShareTargets` and `InstagramShareTargets` referenced.
- `Grep` - `share_to_whatsapp_failed` and `share_to_instagram_failed` referenced.
- `.\a.ps1 fk` - compiles.

**Status:** `[ ]` not done

---

### Step 03.2 - Browse: send selected files to WhatsApp / Instagram

**Files:** `ui/browse/managers/BrowseShareOperationsHelper.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `sendSelectedFilesToWhatsapp(selectedFiles, resource)` and `sendSelectedFilesToInstagram(...)` mirroring `sendSelectedFilesToTelegram`: stage each file (local direct, network via `downloadNetworkFileToCacheWithProgress`), collect URIs, then `SystemShareInvoker.invokeFiles(preferredPackage = <messenger>.firstInstalledPackage(...), chooserTitle = ...)`; on `!launched` show the matching failure string. Keep `CancellationException` handling and the `share_temp` staging exactly as the Telegram method does.

**Verification:**

- `Grep` - `fun sendSelectedFilesToWhatsapp` and `fun sendSelectedFilesToInstagram` present.
- `Grep` - `WhatsAppShareTargets` / `InstagramShareTargets` referenced.
- `.\a.ps1 fk` - compiles.

**Status:** `[ ]` not done

---

### Step 03.3 - Build & smoke the send primitives

**Files:** - (no new edits)
**Depends on:** Step 03.1, Step 03.2

**Prompt for developer:**

> Run `.\a.ps1 fc` (code + resources). The send methods are not yet reachable from UI (callbacks are wired in Phases 04/05); this step only proves they compile and resolve `R.string.*` / catalogue symbols. Do not add a temporary trigger - the methods are exercised end to end after Phase 04/05.

**Verification:**

- `.\a.ps1 fc` - PASS (record exit code).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `research/01__messenger-send-limits.md` exists with B1/B2/B3 Resolved.
- [ ] `.\a.ps1 fc` passes.
- [ ] No `TODO()` / `NotImplementedError` shipped (CLAUDE.md Rule 19) - the send methods are fully implemented, not stubs.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- Player: `PlayerShareManager.sendCurrentFileToWhatsapp()/Instagram()` ready for the Phase 04 callbacks.
- Browse: `BrowseShareOperationsHelper.sendSelectedFilesToWhatsapp()/Instagram()` ready for the Phase 05 callbacks.

---

## Rollback Plan

Remove the four send methods. No data migration; the registry/manifest changes from Phase 02 are independent.
