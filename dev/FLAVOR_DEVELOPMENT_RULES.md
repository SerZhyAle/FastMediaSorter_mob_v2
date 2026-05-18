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

- **Non-Store-published cloud-enabled flavors** (`noLegal`, future `vrUnlicensed`) MUST NOT carry an `applicationIdSuffix`. They share `applicationId = com.sza.fastmediasorter` with `standard` and reuse the same OAuth registrations (one Azure App / one Google OAuth client per build type / one Dropbox app). They are alternate builds of the same product, not separately distributed apps.
- **Store-published flavors** (`photos`, `legacy`, and a future Meta Horizon Store `vr`) keep their `applicationIdSuffix` because the Store binds listing identity to it. Each Store-published flavor must register its own OAuth client per provider.
- **`lite`** has no cloud surface - its `applicationIdSuffix` is unaffected by this rule.
- **Adding a new signing keystore** additionally requires both of the following - neither alone is sufficient:
  - A new `<data android:path="…"/>` line under `BrowserTabActivity` in `src/main/AndroidManifest.xml` carrying the new signing-hash.
  - A matching redirect URI registered in the Azure App / Google Cloud OAuth / Dropbox consoles.
- Source of truth for the active matrix: `app_v2/build.gradle.kts` § `productFlavors` (S0232 policy comment).

## 3. AGENT BEHAVIOR & SKILLS

When an AI Agent is tasked with creating a feature for a non-STANDARD build:

1. **Verify Target Flavor:** Read `app_v2/build.gradle.kts` to understand the target flavor and its assigned `sourceSets`.
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
