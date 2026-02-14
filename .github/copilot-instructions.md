# FastMediaSorter v2 - GitHub Copilot Instructions

**Last Updated**: January 29, 2026

---

## 1. COMMUNICATION DIRECTIVES [PRIORITY 0]

- **RESPONSE_LANGUAGE**: RUSSIAN for all chat interaction.
- **CODE_LANGUAGE**: ENGLISH. **MANDATORY** for code, comments, docs (if it is not translation), logs.
- **TONE**: PROFESSIONAL / DRY / CONCISE.
  - **PROHIBITED**: Pleasantries ("please", "thank you", "apologies"), emotive language, basic explanations.
  - **REQUIRED**: Technical accuracy, direct answer to prompt.
  - **REQUIRED**: Less guessing and assumptions. If not sure how to do - ask user!
- **AUDIENCE_PROFILE**: Senior Engineer (30+ years: Java, .NET, Data Engineering).
- **INPUT_HANDLING**:
  - IF input == ENGLISH: EXECUTE task THEN APPEND `Grammar_Corrections_List` to response (LOW priority, skip if minor).
  - IF (file or data) == MISSING: REQUEST file. DO NOT HALUCINATE/ASSUME content.
  - IF (file or data) == MODIFIED: ALWAYS use `latest` version.
- **TROUBLESHOOTING_PROTOCOL**:
  - IF problem not found: ADD debugging/logging -> ASK user to reproduce/restart scenario.
  - IF problem not found: ADD debugging/logging -> ASK user to reproduce/restart scenario.
  - **TRUST_USER**: NEVER disbelieve user report. Assume error exists.
  - **ADVICE_ONLY_PROTOCOL**:
    - IF user asks for "suggestion", "advice", "opinion", or "recommendation":
      - **PROHIBITED**: Writing code, creating files, or modifying existing code.
      - **REQUIRED**: Provide a text-based answer, list of options, or high-level analysis.
      - **EXCEPTION**: Only write code if EXPLICITLY asked to "implement", "fix", "write", or "change".

---

## 1.5. MODEL SELECTION PROTOCOL [INFO]

**OBJECTIVE**: Reference guide for model selection and Smart Router Helper integration.

### Complexity Classification (for reference):

**SIMPLE:**
- Typo fixes, formatting, renaming
- Adding logs, comments, simple refactoring
- File/code navigation, search, explanations
- Quick fixes under 50 lines
- **Recommended:** Haiku 4.5 (0.33x cost)

**MEDIUM:**
- New features: UseCases, Managers, ViewModels
- Bug fixes requiring code analysis
- Multi-file changes, UI implementation
- Network/DB integration
- **Recommended:** Sonnet 4.5 (1x baseline)

**COMPLEX:**
- Architectural changes, major refactoring
- Cross-module modifications
- Performance optimization, complex debugging
- New modules, critical infrastructure
- **Recommended:** Opus 4.6 (3x cost)

### Using Smart Router Helper

For automatic complexity analysis and model recommendation:

```powershell
# Analyze your question with free AI (GPT-4o/Grok)
.\scripts\ask-smart-router.ps1 "Your question here"

# Example
.\scripts\ask-smart-router.ps1 "Посоветуй каких скриншотов не хватает"
```

**How it works:**
1. Sends question to free analyzer (GPT-4o or Grok)
2. Receives complexity analysis + model recommendation
3. Generates detailed enhanced prompt
4. Copies to clipboard for Copilot Chat

**Setup:** See `temp/SMART_ROUTER_HELPER_GUIDE.md`

### Model Capability Tiers:

| Model | Best For | Cost Multiplier |
|-------|----------|----------------|
| Haiku 4.5 | Simple, quick tasks | 0.33x |
| Sonnet 4.5 | Main development | 1x (baseline) |
| Opus 4.6 | Complex architecture | 3x |

---

## 2. PROJECT ARCHITECTURE

**Framework**: Android Native (Kotlin 1.9+, Java 17).
**Pattern**: Clean Architecture + MVVM + Hilt DI.

### 2.1 Module Structure

- `root/`
  - `app_v2/` [Main App]: Kotlin, View System + Material3, `compileSdk 35`.
  - `wear/` [Companion]: Wear OS, Compose.
  - `dev/`: Development scripts, specs.
  - `dev/archive/`: Archived old requests/todo and deprecated code (do not modify).
  - `docs/`: Documentation (MD files).
  - `downloads/`: Last build results (apk) and journal.
  - `scripts/`: Implementation scripts.
  - `store_assets/`: Store assets (texts, icons, screenshots).
  - `temp/`: **SCRATCHPAD**. All logs/debug outputs GO HERE. (`.gitignore` active).
  - `web/`: HTML Documentation.
  - `test_media/`: Test assets.
  - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/` - **CRITICAL**: All Player logic extracted from Activity.

### 2.2 Data Flow Blueprint

`UI` → `ViewModel` → `UseCase` → `Repository` → `DataSource` (Local/Network)

### 2.3 Three-Layer Structure

- **UI (`ui/`)**: Activities/Fragments observe `StateFlow`/`SharedFlow` from ViewModels. Zero business logic.
- **Domain (`domain/`)**: UseCases encapsulate single operations. Only depends on repository interfaces.
- **Data (`data/`)**: Repository implementations, Room entities, network clients (SMB/SFTP/FTP/Cloud).

**Dependency Rule**: `UI` → `Domain` (via UseCases) → `Data` (via Repository interfaces).

### 2.4 Key Patterns

1. **ViewModels**: `@HiltViewModel`. Expose `StateFlow` for state, `SharedFlow` for one-time events.
2. **UseCases**: Single-responsibility `VerbNounUseCase` (e.g., `GetMediaFilesUseCase`).
3. **Manager Pattern (UI)**: Delegate complex Activity logic to "Managers". **Mandatory for massive Activities.**
4. **Strategy Pattern**: File operations use `FileOperationStrategy` (e.g., `SmbOperationStrategy`).
5. **Connection Pooling**: Network clients must use pooling managers (e.g., `SmbConnectionManager`).

---

## 3. DEVELOPMENT OPERATIONS

### 3.1 Build Commands (PowerShell)

```powershell
# PRIMARY DEBUG BUILD (Auto-versioning)
.\dev\build-with-version.ps1

# FAST DEBUG (No version bump)
.\build-debug.PS1

# FLAVOR BUILDS
.\gradlew.bat assembleStandardDebug
.\gradlew.bat assembleLiteDebug
.\gradlew.bat assemblePhotosDebug
.\gradlew.bat assembleLegacyDebug

# WEAR OS
.\gradlew.bat :wear:assembleDebug

# RELEASE
.\gradlew.bat assembleStandardRelease
```

### 3.2 Test & Verify

```powershell
# UNIT TESTS
.\gradlew.bat testStandardDebugUnitTest

# LINT
.\gradlew.bat lintStandardDebug
```

### 3.3 Database Migrations

Room DB version 6. Migrations in `AppDatabase.kt`. Always increment version on schema change.

---

## 4. CODING STANDARDS [STRICT]

### 4.1 Constraints

- **ROOT_CLEANLINESS**: **MANDATORY**. Keep root folder clean.
  - **ACTION**: ALL temporary files, logs, debug outputs MUST be created in `temp/`.
- **FILE_SIZE**: Max 1000 lines (soft limit).
  - **ACTION**: Use `helpers/*.kt` classes to split logic during development.
  - **AIM**: Keep files concise and readable.
- **SAFETY_BACKUP**:
  - **CONDITION**: File > 500 lines.
  - **ACTION**: Create backup in `temp/` with timestamp BEFORE modification.
- **ACTIVITY_LOGIC**: **PROHIBITED**. Complex logic MUST reside in `helpers/*Manager`.
  - _Example_: `PlayerActivity.kt` delegates to `VideoPlayerManager.kt`.
- **NAMING**:
  - UseCase: `VerbNounUseCase` (`GetFileUseCase`)
  - Repository: `NounRepository` (`MediaRepository`)
  - ViewModel: `NounViewModel` (`PlayerViewModel`)
  - Manager: `NounVerbManager` (`PlayerGestureSetupManager`, `VideoPlayerManager`)

### 4.2 Logging Protocol

- **LIBRARY**: `Timber`.
- **DIRECT_LOG**: `Log.d()` is **PROHIBITED**.
- **OUTPUT**: Write extensive logs to `temp/*.log` for debugging.

### 4.3 Coroutines

- **IO**: File/Network operations.
- **Main**: UI interactions.
- **Scope**: `viewModelScope` preferred. Check `Job.isActive` for cancellation.

### 4.4 Lint and Canonical Style Compliance

- **LINT_COMPLIANCE**: **MANDATORY**. Always follow lint recommendations and project static-analysis rules.
  - **ACTION**: Before finalizing changes, remove lint warnings in touched files where feasible and do not introduce new avoidable warnings.
- **CANONICAL_NAMING**: **MANDATORY**. Use naming patterns from this document and existing project conventions.
  - **ACTION**: Prefer canonical/classic naming and structure accepted by Kotlin/Android best practices and current project architecture.
- **NO_STYLE_DRIFT**: **MANDATORY**. Do not invent ad-hoc naming or style patterns when a canonical project pattern exists.

---

## 5. FEATURE FLAGS (BuildConfig)

| FLAVOR       | VIDEO | AUDIO | IMAGES | CLOUD | DOCS | ANIM |
| :----------- | :---: | :---: | :----: | :---: | :--: | :--: |
| **standard** |  [+]  |  [+]  |  [+]   |  [+]  | [+]  | [+]  |
| **lite**     |  [+]  |  [-]  |  [+]   |  [-]  | [-]  | [-]  |
| **photos**   |  [-]  |  [-]  |  [+]   |  [-]  | [-]  | [+]  |
| **legacy**   |  [+]  |  [+]  |  [+]   |  [-]  | [-]  | [+]  |

---

## 6. DEPENDENCY STACK

_See `gradle/libs.versions.toml` for exact versions._

- **Core**: Hilt, Room
- **Media**: ExoPlayer (Media3)
- **Image**: Glide (App), Coil (Wear)
- **Network**: SMBJ (SMB), SSHJ (SFTP), Commons Net (FTP), OkHttp/Retrofit
- **Cloud**: Google Drive, OneDrive (MSAL), Dropbox
- **OCR/AI**: ML Kit + Tesseract4Android

### 6.1 Network Protocol Notes

- **SMB**: Use `SmbConnectionManager` for connection pooling.
- **FTP**: Apache Commons Net. Use active mode fallback for PASV timeouts.
- **SFTP**: SSHJ + EdDSA. Check `Job.isActive` for cancellation.

---

## 7. CRITICAL BLACKZONES [NO-WRITE AREAS]

- `V1/` - Legacy version 1 (reference only)
- `v2_6/` - Unsuccessful version attempt (reference only)
- `spec_v2/` - v2.x specification documents (reference only)
- `dev/archive/` - Archived deprecated code

**ACTION**: READ-ONLY. DO NOT modify.

---

## 8. COMMON PITFALLS

1. **Player Logic**: Do NOT add code to `PlayerActivity.kt`. Add to relevant Manager in `ui/player/helpers/`.
2. **Coroutines**: Use `Dispatchers.IO` for file/network ops. Check `Job.isActive`.
3. **FTP**: Handle timeouts diligently; do not rely on `completePendingCommand` after error.
4. **Images**: Network editing requires download/edit/upload cycle (`NetworkImageEditUseCase`).
5. **File Storage**: Do not save anything in the root folder. Use `temp/` for logs and temporary files.
