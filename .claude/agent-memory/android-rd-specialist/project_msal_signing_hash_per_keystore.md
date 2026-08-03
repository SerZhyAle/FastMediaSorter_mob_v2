---
name: msal-signing-hash-per-keystore
description: Each signing config (debug/release/debugCustom/etc) produces a distinct MSAL BrowserTabActivity signature hash - manifest must register all of them
metadata:
  type: project
---

MSAL BrowserTabActivity intent-filter requires one `<data android:path="/<Base64-SHA1-of-cert>=" />` entry **per signing config** used to build a flavor.

**Why:** MSAL's `PublicClientApplicationConfiguration.checkIntentFilterAddedToAppManifestForBrokerFlow` validates that the running APK's signature hash matches at least one declared redirect URI. A signing config whose hash is not declared raises `MsalClientException: Intent filter for: BrowserTabActivity is missing` and OneDrive sign-in dies before reaching the browser. Discovered in S0232 (2026-05-17) on noLegal-DEBUG where `debugCustom` config's hash `iRMe/7fhUe3Plj8y2z5NIOOXsZ8=` was missing from `app_v2/src/main/AndroidManifest.xml:145..167` despite three other hashes being registered.

**How to apply:** When adding any new `signingConfigs { create("xxx") { ... } }` block in `app_v2/build.gradle.kts`, immediately also:
1. Compute the new hash: `keytool -exportcert -keystore <file> -alias <alias> -storepass <pass> | openssl sha1 -binary | openssl base64`.
2. Add a new `<intent-filter>` to `BrowserTabActivity` in `src/main/AndroidManifest.xml` with the computed hash as `android:path="/<hash>"`.
3. Register the new redirect URI in the OneDrive Azure App Registration (Microsoft Entra portal) - manifest alone is not sufficient; Azure must accept the callback too.

Related: S0232 (unified-application-id-cloud-flavors, Archived) tracked the fix; the same pattern must be considered for any future signing config addition.
