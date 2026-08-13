# Phase 13 - Upgrade State Reconciliation (force-OFF migration)

**Strategic spec:** [`../S0386_ondemand-ocr-translation-delivery.md`](../S0386_ondemand-ocr-translation-delivery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (device-verified on emulator API 33)
**Depends on:** Phase 03
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-06-10
**Completed:** 2026-06-10

> **Device-verified 2026-06-10:** after installing the de-bundle build over an install that had Translation ON (set not installed) and OCR ON (set installed), the run-once migration flipped Enable Translation -> OFF while leaving Enable OCR -> ON. Re-enabling Translation showed the prompt titled "Translation Module". `S0386UpgradeReconciliation` wired in `FastMediaSorterApp.onCreate` mirroring the `S0200AuthStateWipe` run-once pattern.
> **Minor note (13.2):** the prompt size is shown for sets that contribute a descriptor (OCR/FFmpeg/audio-viz); for store TRANSLATION (a Play dynamic-feature, no mirror descriptor) the message falls back to "a small extra download" without a number. Cosmetic; the feature name is shown.

---

## Objective

Reconcile the toggle-vs-installed mismatch that appears when an existing user updates from a build that shipped the code in the base to the de-bundle build (strategic ADR-2, owner decision 2026-06-10 = variant 1.1). On that upgrade an active user keeps `enableOcr`/`enableTranslation` = ON, but the payload is gone from the base and not yet downloaded. Force the toggle OFF for any code-download set that is ON but not installed, so the state is honest and re-enabling runs the normal size-prompt.

Already-working safety net (do NOT remove): the enable-intercept already fires at use time (`TranslationButtonManager`, `CameraOcrFlowManager`, settings toggles) - this phase only adds honest toggle state on top.

Rejected: variant 1.2 (auto-download on upgrade) - silent network/storage use without consent, contradicts §2.3-2.5.

---

## Prerequisites

- [ ] Phase 03 ✅ Done (default-OFF for new installs).
- [ ] Working tree clean or on a feature branch.

---

## Steps

### Step 13.1 - One-time reconciliation migration

**Files:** the settings migration path (DataStore migration / first-run-after-upgrade hook keyed by versionCode), `DeliverableCapabilityRepository`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a one-time, version-gated migration that runs once after the de-bundle upgrade. For each code-download set (OCR_ENGINES -> `enableOcr`, TRANSLATION -> `enableTranslation`; extend if more toggles trigger downloads): if the toggle is ON and `DeliverableCapabilityRepository.isInstalledBlocking(set)` is false, set the toggle OFF. Run exactly once (guard with a persisted flag / versionCode marker), never re-run, never touch a set that is installed or already OFF. Bundled flavors (sideload/VR where TRANSLATION stays bundled) report installed -> not flipped.

**Verification:**

- `Grep` - the migration checks `isInstalledBlocking` before flipping and is guarded to run once.
- Manual: install an old build with OCR ON, update to the de-bundle build -> OCR toggle reads OFF; re-enabling shows the size prompt; on a build where the set is still bundled the toggle is untouched.

**Status:** `[x]` done

---

### Step 13.2 - Name the feature in the download prompt

**Files:** `DeliveryPromptDialogFragment` / `delivery_offer_message` strings
**Depends on:** - start of phase

**Prompt for developer:**

> The offer dialog already shows the estimated size ("This feature needs an extra download (about N MB)"). Make it name the specific capability being enabled (e.g. "OCR", "Translation", "DTS audio") and what it is for, so the user understands the purpose - not just the size. Keep EN/RU/UK parity.

**Verification:**

- On-device: enabling OCR shows a prompt naming OCR + size; enabling translation names translation + size.
- `check_strings_localized.ps1` passes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Both steps `[x] done`.
- [ ] Upgrade path verified: old build with OCR ON -> de-bundle build -> toggle OFF + re-enable prompts download with size + feature name.
- [ ] No silent downloads on upgrade.
- [ ] `standardDebug` + `noLegalDebug` build; dev log + catalog synced.

---

## Rollback Plan

Revert the phase commit - behavior returns to 1.0 (toggle stays ON; download offered at first use). No data migration beyond the one-time flag.
