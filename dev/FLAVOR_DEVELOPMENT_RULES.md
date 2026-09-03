# FLAVOR DEVELOPMENT RULES & ISOLATION STRATEGY

## 1. THE PROBLEM (CURRENT STATE)
Currently, the `app_v2/src/main/java` source set is polluted with flavor-specific logic. 
Developers and agents have been using conditional checks like `if (BuildConfig.IS_NO_LEGAL_FLAVOR)` or `if (BuildConfig.SUPPORT_VR_PLAYER)` inside core classes (e.g., `OpenSourceLicensesFragment`, `PlayerActivity`, `BrowseActionBarManager`). 

**Why this is bad:**
- **Code Leakage:** `noLegal` or `VR` specific code/imports get compiled into the `standard` build, inflating the APK size and exposing non-standard logic.
- **Maintainability:** `src/main` becomes a monolith of `if/else` flavor statements.
- **Security/Compliance:** `noLegal` code must remain strictly isolated from Google Play (`standard`) builds.

## 2. THE GOLDEN RULES FOR DEVELOPERS & AGENTS

When developing for a specific build (e.g., `vr`, `noLegal`, `lite`), you **MUST** follow these rules:

### RULE 1: STRICT SOURCE SET ABSTRACTION
Do not write flavor-specific implementations in `src/main/java`. 
- **WRONG:** `if (BuildConfig.SUPPORT_VR_PLAYER) { startVr() }` in `main`.
- **RIGHT:** Delegate the action to a flavor-aware interface injected into the `main` class.

### RULE 2: USE INTERFACES FOR BOUNDARIES
Define the contract (Interface) in `src/main/java`.
- Example: `interface VrNavigationDelegate { fun navigateToVrPlayer(...) }`

### RULE 3: FLAVOR-SPECIFIC IMPLEMENTATIONS
Place the actual implementation inside the flavor's source set.
- For `VR`: Create `class RealVrNavigationDelegate : VrNavigationDelegate` inside `src/vr/java/...`.
- For `noLegal`: Create implementations inside `src/noLegal/java/...`.

### RULE 4: DEFAULT NO-OP IMPLEMENTATIONS
For flavors that do not support the feature, provide a No-Operation (No-Op) or fallback implementation.
- Example: `class NoOpVrNavigationDelegate : VrNavigationDelegate` (placed in `src/streamingEnabled/java` or `src/main/java` and bound appropriately).

### RULE 5: HILT / DI BINDING PER FLAVOR
Use Dependency Injection (Hilt) to provide the correct implementation at compile time.
- Create a Dagger `@Module` in `src/main/java` that binds the `NoOp` implementation.
- Create an overriding or mutually exclusive Dagger `@Module` in `src/vr/java` that binds the `Real` implementation. *(Note: Android source sets allow overriding files with the exact same package and name if they don't exist in `main`, or you can use flavor-specific components).*
- **Preferred approach for Android:** Place the abstract DI Module in the flavor folders (e.g., `src/standard/java/di/FlavorModule.kt` vs `src/vr/java/di/FlavorModule.kt`) so Hilt resolves the correct one at compile-time.

### RULE 6: CLOUD-ENABLED FLAVOR `applicationId` POLICY (origin: S0232)
For flavors that talk to cloud OAuth providers (OneDrive / MSAL, Google Drive, Dropbox):

- **Non-Store-published cloud-enabled flavors** (`noLegal`) MUST NOT carry an `applicationIdSuffix`. They share `applicationId = com.sza.fastmediasorter` with `standard` and reuse the same OAuth registrations (one Azure App / one Google OAuth client per build type / one Dropbox app). They are alternate builds of the same product, not separately distributed apps. (S0250: a former `vrUnlicensed` flavor followed this same rule; it was archived 2026-05-19 and merged into `noLegal`.)
- **Store-published flavors** (`photos`, `legacy`, and a future Meta Horizon Store `vr`) keep their `applicationIdSuffix` because the Store binds listing identity to it. Each Store-published flavor must register its own OAuth client per provider.
- **`lite`** has no cloud surface - its `applicationIdSuffix` is unaffected by this rule.
- **Adding a new signing keystore** additionally requires both of the following - neither alone is sufficient:
  - A new `<data android:path="…"/>` line under `BrowserTabActivity` in `src/main/AndroidManifest.xml` carrying the new signing-hash.
  - A matching redirect URI registered in the Azure App / Google Cloud OAuth / Dropbox consoles.
- Source of truth for the active matrix: `app_v2/build.gradle.kts` § `productFlavors` (S0232 policy comment).

### RULE 7: A TEST LIVES IN THE SAME SCOPE AS ITS SUBJECT (origin: S1450)

`app_v2/src/test` is compiled for **every** flavor. A test placed there for a class that lives in a flavor-scoped source set does not merely fail - it breaks unit-test **compilation** on every flavor mounting the disabled counterpart, so no test runs there at all.

- Subject in `src/main/java` -> test goes in `src/test/java`, as usual.
- Subject in a single flavor's set (`src/vr/java`, `src/noLegal/java`) -> test goes in that flavor's unit-test set, `src/testVr/java` / `src/testNoLegal/java`. AGP picks up `src/test<Flavor>/java` by convention, so no Gradle change is needed.
- Subject in a capability set shared by several flavors (`src/streamingEnabled/java`, `src/cloudEnabled/java`) -> test goes in the matching shared test set (`src/testStreamingEnabled/java`, `src/testCloudEnabled/java`), which `app_v2/build.gradle.kts` mounts into exactly the same flavors as the main set. Duplicating the file into each `src/test<Flavor>` is the wrong fix.
- Adding a new capability source set means adding its test counterpart and mirroring the mount list. A mount map that drifts from the main one reintroduces this defect silently.

Why it stays silent: nobody routinely runs unit tests on the reduced flavors, so a break there surfaces only when a release gate asks for them. `docs/RELEASE_READINESS_STANDARD.md` requires `PermissionRegistryManifestParityTest` on `lite` and `noLegal` as a release blocker; while `lite` unit tests did not compile, that blocker could not run on the very flavor whose permission set differs most.

Enforced by `scripts/quality/assert-shared-test-flavor-scope.ps1` (S1453), which runs in the fast-gates batch. It refuses both halves of this rule: a shared test referencing a flavor-scoped type, and a capability test set whose mount list drifted from its main counterpart's.

### RULE 8: THE WEAR MODULE PLAYS BY THE SAME RULES (origin: S2090)

Everything above was written for `app_v2` and applies unchanged to `wear`. Since S2090 the watch
carries its own `version` dimension with two flavors - `standard` (the one Play accepts, the default)
and `noLegal` (sideload). Read `wear/build.gradle.kts` for the watch, `app_v2/build.gradle.kts` for
the phone; the two dimensions are independent and only happen to share two names.

Rules 1-5 map directly: the contract and its no-op live in `wear/src/main/java/`, the real
implementation in `wear/src/noLegal/java/`, and the binding in a flavor-local Hilt module under
`wear/src/noLegal/java/.../di/`. Substitute `wear/` for `app_v2/` and nothing else changes.

Two deltas are specific to the watch, and both are hard:

- **No `applicationIdSuffix`, under any circumstances.** Play Services routes Wear Data Layer traffic
  by an AppKey of (package name, signing certificate) and drops a mismatch inside `WearableService`,
  below the application - so a suffix stops delivery in both directions while the watch goes on
  logging successful sends (S1681). S1951 records what a suffix costs when a flavor does take one:
  the phone's `legacy` flavor got `.legacy` and the resolution was to switch the Wear companion off in
  that flavor entirely, not to find a way to pair two package names.
- **No `manifest.srcFile` on a wear flavor.** On the phone that substitution REPLACED the
  auto-detected flavor manifest and silently dropped its other entries, which then had to be re-added
  through `androidComponents.onVariants` (S0174/S0183). Leaving it unset means a
  `wear/src/noLegal/AndroidManifest.xml` is picked up by convention and merged normally - which is why
  no such file needs to exist until something has a permission to declare.

**`wear/src/noLegal/` was empty from S2090 until S2165 filled it, and the ban that kept it empty still
stands.** The dimension existed so that the next capability Play refuses on a watch had somewhere to go
instead of being deleted, which is what happened to `ACCESS_FINE_LOCATION` in S2013. It now holds
exactly two files, and they are the shape to copy:

- `wear/src/noLegal/java/com/sza/fastmediasorter/wear/diagnostics/NoLegalWearInfoContributor.kt` - the
  capability itself, adding the signing-certificate fingerprint to the system-information report.
- `wear/src/noLegal/java/com/sza/fastmediasorter/wear/di/NoLegalWearInfoModule.kt` - its `@Binds
  @IntoSet` module, contributing into a set that `wear/src/main` declares with `@Multibinds` so the set
  stays injectable in `standard`, where nothing occupies that slot.

There is still **no `wear/src/noLegal/AndroidManifest.xml`**, because this capability declares no
permission and an empty manifest overlay is dead weight under Rule 20 - the second half of S2090 §9
ADR-5 is unspent, and the first capability that needs a permission creates that file.

Do not fill the directory with a placeholder class to make it look occupied; that half of ADR-5 is
permanent, and it is the reason the directory stayed empty for as long as it did rather than acquiring
a stub nobody could later distinguish from real content.

## 3. AGENT BEHAVIOR & SKILLS

When an AI Agent is tasked with creating a feature for a non-STANDARD build:

1. **Verify Target Flavor:** Read `app_v2/build.gradle.kts` - or `wear/build.gradle.kts` for the watch - to understand the target flavor and its assigned `sourceSets`.
2. **Never Hardcode BuildConfigs in Main:** If you are about to type `BuildConfig.IS_...` in `src/main/java`, **STOP**. You are doing it wrong.
3. **Check for Existing Interfaces:** Look for existing delegates or managers (e.g., `VrFeatureManager`) that you can extend.
4. **Isolate UI Changes:** If a specific flavor requires a new UI element (like the NewPipe license in `noLegal`), do not hardcode its visibility in `main`'s XML. Instead:
   - Provide a dynamic UI population method via an injected interface.
   - Or, override the XML layout entirely in `src/noLegal/res/layout/`.
5. **Log Correctly:** When updating `CHANGELOG.md`, clearly specify which source set (`src/noLegal`, `src/vr`) was modified.

## 4. EXAMPLE REFACTORING (Mental Model)

**AS-IS (Bad):**
```kotlin
// In src/main/java/.../OpenSourceLicensesFragment.kt
binding.cardNewpipeLicense.visibility = if (BuildConfig.IS_NO_LEGAL_FLAVOR) View.VISIBLE else View.GONE
```

**TO-BE (Good):**
*Approach A: Layout Overriding*
- Keep `cardNewpipeLicense` completely out of `src/main/res/layout/fragment_open_source_licenses.xml`.
- Create `src/noLegal/res/layout/fragment_open_source_licenses.xml` containing the extra card.

*Approach B: Injected Delegate*
- `src/main/java`: `interface LicenseCardProvider { fun addExtraCards(container: ViewGroup) }`
- `src/main/java`: `class DefaultLicenseCardProvider : LicenseCardProvider { /* does nothing */ }`
- `src/noLegal/java`: `class NoLegalLicenseCardProvider : LicenseCardProvider { /* inflates and adds NewPipe card */ }`
- Bind the correct provider via flavor-specific Hilt modules.
