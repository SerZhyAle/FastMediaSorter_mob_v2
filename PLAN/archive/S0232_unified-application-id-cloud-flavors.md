# Strategic Specification: S0232 — Unified `applicationId` for cloud-enabled flavors

**Ticket:** S0232
**Status:** Verified
**Priority:** 85
**Date:** 2026-05-17

---

## 1. Problem

Cloud account creation for OneDrive, Google Drive and Dropbox is broken or degraded in `noLegal` and `vr`/`vrUnlicensed` builds. Symptoms:

- `noLegal-DEBUG` OneDrive: `MsalClientException: Intent filter for: BrowserTabActivity is missing` for redirect path `/iRMe/7fhUe3Plj8y2z5NIOOXsZ8=` — the `debugCustom` keystore hash was not in the manifest at the time of the failing run (see `logs/fastmediasorter_20260517_003023.log:257..328`). The missing intent-filter has since been added to `app_v2/src/main/AndroidManifest.xml:161..167` (uncommitted working-tree fix). OneDrive will work once the unified configuration is in place, but the underlying architectural problem remains: each new keystore × each flavor creates yet another (`packageName`, `signature_hash`) pair the cloud consoles must learn about.
- Google Drive (S0233) and Dropbox (S0235) hit a parallel class of regressions because Google Cloud and Dropbox bind their OAuth client identity to a specific `applicationId`. `com.sza.fastmediasorter.nolegal` and `com.sza.fastmediasorter.vr` are seen as **different apps** from the published `com.sza.fastmediasorter`, so existing OAuth clients reject the redirect.

**Owner directive (2026-05-17):** `noLegal` and VR builds must represent themselves to Google, Microsoft and Dropbox identically to `standard`. They are not separately distributed first-class apps — they are alternate builds of the same product. Per-flavor cloud registration is rejected as a strategy.

`vr` and `vrUnlicensed` are not yet published on Meta Horizon Store, so changing their `applicationId` is safe right now; this window will close once a Store listing exists.

---

## 2. Goals

1. `noLegal`, `vr`, `vrUnlicensed` builds share `applicationId = com.sza.fastmediasorter` with `standard` (debug variants share `com.sza.fastmediasorter.debug`).
2. A single set of cloud-provider OAuth registrations (one OneDrive Azure App, one Google Cloud OAuth client per build type, one Dropbox app) covers every cloud-enabled flavor.
3. The `debugCustom` keystore signature hash (`iRMe/7fhUe3Plj8y2z5NIOOXsZ8=`) is permanent in the MSAL manifest entry — it covers every shared-key developer build.
4. The "future signing-config addition" failure mode is documented inline so a new keystore obviously requires both a manifest entry and a cloud-console redirect URI.

**Non-goals:**

- Modifications to `lite`, `photos`, `legacy` flavors. `lite` has no cloud. `photos` and `legacy` are published flavors with their own `applicationId` for Store identity; they stay as-is.
- Migrating off MSAL, Credential Manager, or the Dropbox SDK.
- Refactor of the cloud auth plugins.
- The Google Drive / Dropbox specific fixes (S0233/S0235) — they are unblocked by this spec but tracked separately.
- Touching `wear/` companion app — no cloud surface there.

---

## 3. Constraints

- **Side-by-side install:** `standard` and `noLegal`/`vr`/`vrUnlicensed` will no longer coexist on the same device. Owner accepted this trade-off explicitly. Devices that currently have both will need a re-install of whichever build is being kept.
- **Meta Horizon Store:** `vr` is not yet published. If a Store listing is created later under `com.sza.fastmediasorter.vr`, the `vr` flavor's `applicationId` must be re-suffixed at that point and a dedicated Azure / Google / Dropbox app registered. The `vrUnlicensed` (sideload-only) flavor stays at `com.sza.fastmediasorter` permanently.
- **Debug build variant:** `debug` buildType keeps its `.debug` suffix → all cloud-enabled flavors' debug variants land on `com.sza.fastmediasorter.debug`. This matches the debug-specific Azure App / Dropbox app key already in use.
- **Signing configs:** all flavors continue to sign via the project-wide `debugCustom` (debug) and `release` (release) configs — already buildType-level, no per-flavor signing exists.
- **Manifest merge:** the four `<intent-filter>` paths in `BrowserTabActivity` cover all four currently-known signing certificates. Adding a new keystore in the future requires extending this list.
- **Hilt / generated code:** `${applicationId}` placeholders and `BuildConfig.APPLICATION_ID` references regenerate automatically. Hilt-generated code uses the new package and stays valid.
- **No user-visible strings:** entirely internal change.

---

## 4. Current Architecture Context

`app_v2/build.gradle.kts` (current state):

| Flavor | `applicationIdSuffix` | Effective `applicationId` (release) | Cloud impact |
|--------|------------------------|------------------------------------|--------------|
| `standard` | — | `com.sza.fastmediasorter` | works |
| `noLegal` | `.nolegal` | `com.sza.fastmediasorter.nolegal` | OAuth rejects |
| `vr` | `.vr` | `com.sza.fastmediasorter.vr` | OAuth rejects |
| `vrUnlicensed` | `.vr` | `com.sza.fastmediasorter.vr` | OAuth rejects |
| `lite` | `.lite` | `com.sza.fastmediasorter.lite` | no cloud (skip) |
| `photos` | `.photos` | `com.sza.fastmediasorter.photos` | published; keep |
| `legacy` | `.legacy` | `com.sza.fastmediasorter.legacy` | published; keep |

MSAL redirect-URI host (`com.sza.fastmediasorter`) is hardcoded in `app_v2/src/main/res/raw/msal_config.json`, `app_v2/src/debug/res/raw/msal_config.json`, `app_v2/src/release/res/raw/msal_config.json` and in `app_v2/src/main/AndroidManifest.xml:144..175`. The host does not match the runtime package for `noLegal`/`vr` builds — MSAL accepts this only because it matches the redirect URI configured in MSAL's own config, not the calling app's package. Google Drive and Dropbox SDKs are stricter and reject mismatched applicationIds.

`debugCustom` signing config (`build.gradle.kts:438..488`) is applied via `buildTypes.debug.signingConfig` and therefore covers every `*Debug` variant.

---

## 5. Proposed Approach

1. In `app_v2/build.gradle.kts` `productFlavors` block:
   - `noLegal`: remove `applicationIdSuffix = ".nolegal"` (line 144).
   - `vr`: remove `applicationIdSuffix = ".vr"` (line 279).
   - `vrUnlicensed`: remove `applicationIdSuffix = ".vr"` (line 342).
   - Add a comment block at the head of `productFlavors` documenting the policy: cloud-enabled non-Store flavors share `applicationId` with `standard`; only Store-published flavors carry a suffix.
2. Keep the existing `iRMe/7fhUe3Plj8y2z5NIOOXsZ8=` `<intent-filter>` in `app_v2/src/main/AndroidManifest.xml` (already in working tree). Tighten the surrounding comment so the rule is explicit: "every signing-config keystore needs its hash listed here AND a matching redirect URI in the cloud-provider consoles".
3. Document the cloud-console invariant in `dev/FLAVOR_DEVELOPMENT_RULES.md` (or wherever flavor policy lives): cloud-enabled non-Store flavors **must not** carry an `applicationIdSuffix`; Store-published flavors **must** register their own OAuth client.
4. After the build flip, OneDrive should work without further changes (manifest hash is already there; MSAL config already points at the standard host). Google Drive and Dropbox are unblocked but their specific failures (S0233 missing OAuth client; S0235 missing scope) are out of scope for this ticket.

---

## 6. Open Questions

1. Should `vrUnlicensed` retain its `versionNameSuffix = "-VR-Unlicensed"` (yes — it's a label, not an identity)? Confirmed.
2. The "Security alert" warning when debug+release share a Dropbox scheme: the current `manifestPlaceholders["dropboxAppKey"]` in the debug buildType already uses a separate `u43ocp6pqvwaiu1` app key. After unification this separation continues to be useful (debug builds across flavors still share one debug-only Dropbox app). No action needed in this ticket.
3. Does any feature in `noLegal` rely on the `BuildConfig.APPLICATION_ID` literal string `com.sza.fastmediasorter.nolegal`? Grep of `app_v2/src` finds none; the `.nolegal` token appears only in source-set folder names (Kotlin subpackages), never as a runtime applicationId compare. Tactical spec re-confirms.

---

## 7. Risks

- **Side-by-side install regression** (accepted): users with both `standard` and `noLegal` installed on the same device will lose one of them on next install. Mitigation: callout in `dev/CHANGELOG.md`; on-device, Android prompts "replace existing application".
- **Future Store publication of vr**: when `vr` is added to Meta Horizon Store, the `applicationId` must be re-suffixed and a dedicated cloud-app registration created. Mitigation: §5.3 policy doc captures the rule.
- **Hilt regeneration**: `applicationId` change triggers full re-generation of Hilt + ViewBinding + R class. Low risk — same flow happens on every flavor build.
- **Stale APK on developer device**: testers must uninstall the old `*.nolegal` / `*.vr` APK before installing the unified build, or `adb install` will reject signature mismatch. Mitigation: `adb uninstall com.sza.fastmediasorter.nolegal` etc. before first install of unified build.

---

## 8. User Impact

Internal infrastructure change. OneDrive starts working again in `noLegal` builds as a side effect.

- **EN:** Cloud sign-in works the same in sideload builds as in the regular app.
- **RU:** Вход в облачные сервисы в сборках для прямой установки работает так же, как в обычной версии.
- **UK:** Вхід у хмарні сервіси у збірках для прямої установки працює так само, як у звичайному застосунку.

Bug fix and architectural cleanup. `docs/FEATURES.md` not touched; `dev/FUNCTIONALITY.log` records a FIX entry.

---

## 9. Related Specs

- **S0156** — noLegal capability surface audit (defines noLegal as standard+VR+sideload). This spec confirms noLegal's identity-aligned-with-standard interpretation.
- **S0183** — noLegal APK install manifest injection (Archived). Confirms `src/main/AndroidManifest.xml` IS merged into noLegal builds.
- **S0200** — Google Drive central binding via Credential Manager (parallel infrastructure work in progress).
- **S0233** — Google Drive `PlayServicesOutdated` fallback. Unblocked by this spec.
- **S0234** — Google Account card error UI. Unblocked by this spec.
- **S0235** — Dropbox `files.metadata.read` scope regression. Unblocked by this spec; remaining work is the scope/PKCE call fix.

---

## Last Audit

**Date:** 2026-05-18
**Mode:** strategic
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 10 · WARN 0 · FAIL 0 · MANUAL 4 · EXEMPT 2

### Manual / on-device

- [ ] OneDrive sign-in completes end-to-end in a freshly-installed `noLegalDebug` APK (`debugCustom` keystore) with no `BrowserTabActivity is missing` error.
- [ ] Google Drive and Dropbox sign-in unblock in `noLegalDebug` (separate fixes in S0233 / S0235; this spec just removes the applicationId barrier). The `vrDebug` / `vrUnlicensedDebug` parts of the original §1 are moot — S0241 has since removed both flavors.
- [ ] Logcat shows `S0232: unified applicationId at runtime = com.sza.fastmediasorter.debug (flavor=noLegal)` — proves runtime package is the unified id (probe will be re-inserted on next `BlockNeedUserTest` cycle if needed).
- [ ] `adb install` on a device that has a prior `com.sza.fastmediasorter.nolegal` APK either prompts for replace-existing or instructs `adb uninstall` first.

### Notes

- §1, §2 and §3 reference `vr` / `vrUnlicensed` flavors which have since been removed by S0241. The spec's intent — cloud-auth applicationId unification for non-Store cloud-enabled flavors — is satisfied for the surviving such flavor (`noLegal`). Run `/spec-update S0232` later if the scope text should be tightened in writing.
- All four action items from the previous audit (`Partial`, 2026-05-17) are resolved this round:
  1. `[FIXED]` Added RULE 6 (cloud-enabled flavor `applicationId` policy, origin: S0232) to `dev/FLAVOR_DEVELOPMENT_RULES.md`.
  2. `[FIXED]` Tightened `BrowserTabActivity` manifest comment with the explicit (a) intent-filter + (b) console-redirect requirement; called out the symmetric MsalClientException / provider-rejection failure modes.
  3. `[PRE-RESOLVED]` Catalog scan + render confirmed fresh; `lastTouched` is git-commit date and is expected to lag the working tree.
  4. `[FIXED]` Disabled the dead `vrPackage` Play Store CTA in `PlayerEventHandler.showVrInstallCtaDialog`; replaced with TODO referencing S0232 §3 «vr re-suffix on Store publication» so the wiring is restored on the right store (Meta Horizon Store for Quest, Play Store for Android XR) when the VR edition actually ships.
