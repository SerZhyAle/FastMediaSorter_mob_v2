---
name: wear-play-publishing-gaps
description: What Play requires to publish the Wear app and the four repo-side gaps blocking it - dedicated Wear track, AAB not APK, WO-P6 on-watch password entry, listing must say "Wear OS"
metadata:
  type: project
---

**The Wear module has never been published to Play, and four concrete things block it.** Established
2026-08-16 from the official Play/Wear docs plus a read-only Play API probe (production `260815194`,
bundles only, `wearScreenshots` for en-US empty, no wear artifact ever uploaded).

**Why:** the owner asked to make the watch app installable from the store. The repo ships the wear
APK only as a GitHub sideload asset (S0394, `:wear:assembleRelease` in `build-release-spectrum.ps1`),
and nothing in `scripts/` ever builds `:wear:bundleRelease`. There is also no publish ticket - VR has
S0555/S0556, Wear has none, and `PLAN/RELEASE_QUEUE.md` package 34 states outright that "the Wear
module ships in no phone release".

**The Play-side rules that differ from a phone release:**
- The wear artifact uploads to a **dedicated Wear OS track**, not the mobile track. An artifact left
  on a mobile track still serves but **can never be updated**.
- Order of operations: closed-testing release first -> only then Advanced settings -> Form factors ->
  add Wear OS -> opt in and agree to the review policy -> rollout. Opting in triggers an **extra
  Wear-specific review** (Pending/Approved/Not approved) on top of normal policy review.
- Same `applicationId` and same signing key as the phone app is **required** (WO-G7) - which is what
  S1681 already established, see [[wear-data-layer-applicationid-mismatch]].
- versionCodes need only be **unique**; no official ordering rule exists between phone and watch.
  Our 8-digit wear code vs 9-digit phone code cannot collide, so this is already compliant - treat
  any "watch must be higher" advice as folklore.
- Wear targetSdk floor is API **35** from 2026-08-31 (lower than phone's 36); wear already targets 36.
- 2026-09-15: all Wear apps must support 64-bit devices.

**The four repo-side gaps:**
1. **No AAB path.** Play takes bundles for this app (20 bundles, 0 APKs in the account). Need a
   `:wear:bundleRelease` path in the release scripts.
2. **WO-P6 is violated by existing UI, and this is the expensive one.** The guideline is absolute:
   the app *must not ask the user to input a username or password directly on the Wear OS device*.
   `wear/.../ui/network/AddNetworkSourceScreen.kt` does exactly that - an editable password chip with
   `PasswordVisualTransformation` and a visibility toggle. The compliant route already half-exists:
   the phone pushes sources over the Data Layer (`ImportNetworkSourcesUseCase`), so the fix is to
   make the push the only credential path and drop or gate on-watch entry. This reshapes a feature,
   it is not a checklist tick.
3. **The store listing never says "Wear OS".** Grepping `play/listing/**` for it returns nothing.
   That literal string is the #1 documented rejection reason (WO-G2); "Android Wear" is separately
   forbidden wording, and the listing currently contains neither.
4. **No Wear screenshots anywhere.** `play/listing/*/images/` has only `phoneScreenshots` and
   `tenInchScreenshots`. Wear needs >=1 shot, 1:1, min 384x384, no device frame, no transparency.

**Open tickets that map straight onto review-failure items:** S1678 round-display clipping is WO-V16
verbatim (Approved, unfixed); S1705 image viewer has no dismiss, which is WO-V3 swipe-to-dismiss;
S1687 and S1688 are both priority 90 and still `BlockNeedUserTest`; S1683 is In Progress; S1555
accepts any SFTP host key; S1628 leaves wear strings at en/ru/uk against 13 declared locales.

**The ticket now exists: S1707** (`publish-wear-app-play-store`, Approved, tier 4), scheduled **last in
release package 33** by owner ruling 2026-08-16 - "if release 33 ships the Wear work, it must also ship
the Wear app to the store", so 33 is not done until the watch app is in the store. Its research folder
carries the full Play rule set, the review checklist and the readiness measurement; read those before
re-researching anything above. Two owner rulings worth not re-litigating: the credential-entry screen is
**hidden from non-debug builds, not deleted** (it is wanted later for S1697's phone-hosted resources),
and there is **no closed-testing phase** - the closed track is a one-tester technical step only, because
the console will not open the Wear form factor without a release in it.

**How to apply:** treat "publish the watch app" as a project the size of S0555, not a release step.
Nothing can be uploaded until the AAB path exists, and nothing should be submitted until WO-P6 is
resolved - a rejection costs a full re-review cycle. The console half (form factor, opt-in, review
submission) is owner-only; the Play API cannot see or do it, see [[play-console-api-access]].
