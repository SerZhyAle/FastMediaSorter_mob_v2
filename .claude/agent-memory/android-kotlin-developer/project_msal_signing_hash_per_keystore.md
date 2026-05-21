---
name: project_msal_signing_hash_per_keystore
description: Each signing config (debug/release/debugCustom/etc) produces a distinct MSAL BrowserTabActivity signature hash - manifest must register all of them
metadata:
  type: project
---

MSAL BrowserTabActivity intent-filter requires one `<data android:path="/<Base64-SHA1-of-cert>=" />` entry **per signing config** used to build a flavor.

**Why:** MSAL's `PublicClientApplicationConfiguration.checkIntentFilterAddedToAppManifestForBrokerFlow` validates that the running APK's signature hash matches at least one declared redirect URI. A signing config whose hash is not declared raises `MsalClientException: Intent filter for: BrowserTabActivity is missing` and OneDrive sign-in dies before reaching the browser. Discovered in S0232 (2026-05-17) on noLegal-DEBUG where `debugCustom` config's hash `iRMe/7fhUe3Plj8y2z5NIOOXsZ8=` was missing from `app_v2/src/main/AndroidManifest.xml:145..167` despite three other hashes being registered.

**How to apply:** When implementing a change that adds or modifies a `signingConfigs { create("xxx") { ... } }` block in `app_v2/build.gradle.kts` (or wires an existing signing config to a new flavor variant):
1. Compute the new hash: `keytool -exportcert -keystore <file> -alias <alias> -storepass <pass> | openssl sha1 -binary | openssl base64`.
2. Add a new `<intent-filter>` to the `BrowserTabActivity` declaration in `src/main/AndroidManifest.xml` with the computed hash as `android:path="/<hash>"` (look near the existing MSAL redirect filters).
3. Register the new redirect URI in the OneDrive Azure App Registration (Microsoft Entra portal) - manifest alone is not sufficient; the signed-in user will still hit `MsalClientException` if Azure doesn't accept the callback. This step is not automatable from the build.
4. Build the variant and confirm `BrowserTabActivity` carries the new hash in the merged manifest before testing OneDrive sign-in.

Related: [[s0232]] tracks the active fix; the same pattern must be considered for any future signing config addition.
