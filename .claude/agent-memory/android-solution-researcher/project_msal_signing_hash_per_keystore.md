---
name: msal-signing-hash-per-keystore
description: Each signing config (debug/release/debugCustom/etc) produces a distinct MSAL BrowserTabActivity hash - research must surface this multiplicity, not assume a single hash
metadata:
  type: project
---

MSAL BrowserTabActivity intent-filter requires one `<data android:path="/<Base64-SHA1-of-cert>=" />` entry **per signing config** used to build a flavor. A research report on MSAL/Azure auth that assumes a single hash will mislead the spec author.

**Why:** MSAL's `PublicClientApplicationConfiguration.checkIntentFilterAddedToAppManifestForBrokerFlow` validates that the running APK's signature hash matches at least one declared redirect URI. A signing config whose hash is not declared raises `MsalClientException: Intent filter for: BrowserTabActivity is missing` and OneDrive sign-in dies before reaching the browser. Discovered in S0232 (2026-05-17) on noLegal-DEBUG where `debugCustom` config's hash `iRMe/7fhUe3Plj8y2z5NIOOXsZ8=` was missing from `app_v2/src/main/AndroidManifest.xml:145..167` despite three other hashes being registered.

**How to apply:** When research touches OneDrive, Azure, MSAL, or any auth flow gated by `BrowserTabActivity`:

- Read `app_v2/build.gradle.kts` and list EVERY `signingConfigs { create("..") { .. } }` block - cite them in the Current Architecture section.
- Read `app_v2/src/main/AndroidManifest.xml` lines around the `BrowserTabActivity` `<intent-filter>` blocks and list EVERY declared `<data android:path="/<hash>" />` entry.
- Cross-reference: a signing config without a matching hash entry is a runtime auth failure waiting to happen - flag under "Risks Identified" with severity Med-High depending on which flavor uses that config.
- Do NOT recommend a fix in the report (writer-agent territory). Do cite that the fix has two parts: (a) add `<intent-filter>` entry, (b) register the new redirect URI in the Azure App Registration (Microsoft Entra portal) - manifest alone is insufficient.
- The hash format is the `Base64(SHA1(cert))` of the signing cert; you do not compute it during research, only count declared vs configured.
