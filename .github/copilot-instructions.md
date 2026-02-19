# FastMediaSorter v2 - GitHub Copilot Instructions

**Last Updated**: February 19, 2026

---

## COMMUNICATION DIRECTIVES [PRIORITY 0]

- **RESPONSE_LANGUAGE**: RUSSIAN.
- **CODE_LANGUAGE**: ENGLISH. **MANDATORY** for code, comments, docs, logs.
- **TONE**: PROFESSIONAL / DRY / CONCISE.
  - **PROHIBITED**: Pleasantries, emotive language, basic explanations.
  - **REQUIRED**: Technical accuracy, direct answer.
  - **REQUIRED**: No assumptions. Ask user if unsure.
- **USER_PROFILE**: Senior Engineer (30+ years: Java, .NET, Data Engineering).
- **INPUT_HANDLING**:
  - IF input == ENGLISH: EXECUTE task. APPEND `Grammar_Corrections_List` (Low priority).
  - IF (file/data) == MISSING: REQUEST file. DO NOT ASSUME.
  - IF (file/data) == MODIFIED: USE `latest`.
- **TROUBLESHOOTING**:
  - IF problem not found: ADD logging -> ASK reproduce.
  - **TRUST_USER**: Assume error exists. Verify.
  - **ADVICE_PROTOCOL**:
    - IF user asks "suggestion", "advice", "opinion":
      - **PROHIBITED**: Writing/modifying code.
      - **REQUIRED**: Text answer, options, analysis.
      - **EXCEPTION**: Code ONLY if EXPLICITLY asked ("implement", "fix", "write").

---

## MODEL SELECTION PROTOCOL [INFO]

**OBJECTIVE**: Model selection / Smart Router Helper guide.

### Complexity Classification

**SIMPLE** (suggest Haiku 4.5):
- Typos, formatting, renaming.
- Logs, comments, simple refactors.
- Navigation, search, explanations.
- Fixes < 50 lines.

**MEDIUM** (suggest Sonnet 4.5):
- New features (UseCases, ViewModels).
- Analysis-heavy bug fixes.
- Multi-file changes, UI.
- Network/DB.

**COMPLEX** (suggest Opus 4.6):
- Architecture, major refactors.
- Cross-module.
- Optimization, complex debugging.
- New modules, critical infra.

### Smart Router Helper

**Command**:
```powershell
.\scripts\ask-smart-router.ps1 "Question"
```

**Function**:
1. Sends to free analyzer.
2. Gets complexity/model.
3. Generates enhanced prompt.
4. Copies to clipboard.

**Setup**: `temp/SMART_ROUTER_HELPER_GUIDE.md`

### Model Tiers

| Model | Best For | Cost |
|-------|----------|------|
| Haiku 4.5 | Simple/Quick | 0.33x |
| Sonnet 4.5 | Main Dev | 1x |
| Opus 4.6 | Complex | 3x |

---

## PROJECT ARCHITECTURE

**Framework**: Android Native (Kotlin 1.9+, Java 17).
**Pattern**: Clean Architecture + MVVM + Hilt DI.

### Module Structure

- `root/`
  - `app_v2/`: Kotlin, View System + Material3, `compileSdk 35`.
  - `wear/`: Wear OS, Compose.
  - `dev/`: Scripts, specs.
  - `dev/archive/`: READ-ONLY archive.
  - `docs/`: Documentation (MD).
  - `downloads/`: Build results.
  - `scripts/`: Implementation scripts.
  - `store_assets/`: Store assets.
  - `temp/`: **SCRATCHPAD**. Logs/debugs.
  - `web/`: HTML Docs.
  - `test_media/`: Test assets.
  - `app_v2/.../helpers/` - **CRITICAL**: Extracted Player logic.

### Data Flow

`UI` → `ViewModel` → `UseCase` → `Repository` → `DataSource`

### Three-Layer Structure

- **UI (`ui/`)**: Observe `StateFlow`. Zero business logic.
- **Domain (`domain/`)**: UseCases. Repository interfaces only.
- **Data (`data/`)**: Repositories, DB, Network.

**Dependency Rule**: `UI` → `Domain` → `Data`.

### Key Patterns

- **ViewModels**: `@HiltViewModel`. `StateFlow` (state), `SharedFlow` (events).
- **UseCases**: Single-responsibility `VerbNounUseCase`.
- **Manager Pattern**: Delegate complex Activity logic to "Managers". **Mandatory**.
- **Strategy Pattern**: File operations (`FileOperationStrategy`).
- **Connection Pooling**: Network clients (`SmbConnectionManager`).

---

## DEVELOPMENT OPERATIONS

### Build Commands (PowerShell)

```powershell
# PRIMARY DEBUG
.\dev\build-with-version.ps1

# FAST DEBUG
.\build-debug.PS1

# FLAVORS
.\gradlew.bat assembleStandardDebug
.\gradlew.bat assembleLiteDebug
.\gradlew.bat assemblePhotosDebug
.\gradlew.bat assembleLegacyDebug

# WEAR OS
.\gradlew.bat :wear:assembleDebug

# RELEASE
.\gradlew.bat assembleStandardRelease
```

### Test & Verify

```powershell
# UNIT TESTS
.\gradlew.bat testStandardDebugUnitTest

# LINT
.\gradlew.bat lintStandardDebug
```

### Database Migrations

Room Config: Version 6.
Migrations: `AppDatabase.kt`.
**Rule**: Increment version on schema change.

---

## CODING STANDARDS [STRICT]

### Constraints

- **ROOT_CLEANLINESS**: **MANDATORY**.
  - **ACTION**: `temp/` for temp files/logs.
- **FILE_SIZE**: Max 1000 lines.
  - **ACTION**: Split to `helpers/*.kt`.
- **SAFETY_BACKUP**:
  - **CONDITION**: File > 500 lines.
  - **ACTION**: Backup to `temp/` with timestamp BEFORE mod.
- **ACTIVITY_LOGIC**: **PROHIBITED**.
  - **ACTION**: Delegate to `helpers/*Manager`.
- **NAMING**:
  - UseCase: `VerbNounUseCase`
  - Repository: `NounRepository`
  - ViewModel: `NounViewModel`
  - Manager: `NounVerbManager`

### Logging Protocol

- **LIBRARY**: `Timber`.
- **PROHIBITED**: `Log.d()`.
- **OUTPUT**: `temp/*.log`.

### Coroutines

- **IO**: File/Network.
- **Main**: UI.
- **Scope**: `viewModelScope`. CHECK `Job.isActive`.

### Lint / Style

- **LINT_COMPLIANCE**: **MANDATORY**. Follow rules. Remove warnings.
- **CANONICAL_NAMING**: **MANDATORY**.
- **NO_STYLE_DRIFT**: **MANDATORY**.

---

## FEATURE FLAGS (BuildConfig)

| FLAVOR | VIDEO | AUDIO | IMAGES | CLOUD | DOCS | ANIM |
| :--- | :---: | :---: | :---: | :---: | :--: | :--: |
| **standard** | [+] | [+] | [+] | [+] | [+] | [+] |
| **lite** | [+] | [-] | [+] | [-] | [-] | [-] |
| **photos** | [-] | [-] | [+] | [-] | [-] | [+] |
| **legacy** | [+] | [+] | [+] | [-] | [-] | [+] |

---

## DEPENDENCY STACK

Ref: `gradle/libs.versions.toml`

- **Core**: Hilt, Room
- **Media**: ExoPlayer
- **Image**: Glide (App), Coil (Wear)
- **Network**: SMBJ, SSHJ, Commons Net, OkHttp
- **Cloud**: Drive, OneDrive, Dropbox
- **OCR/AI**: ML Kit, Tesseract4Android

### Network Protocols

- **SMB**: `SmbConnectionManager`.
- **FTP**: Apache Commons Net. Active mode fallback.
- **SFTP**: SSHJ + EdDSA. Check `Job.isActive`.

---

## CRITICAL BLACKZONES [READ-ONLY]

- `V1/`
- `v2_6/`
- `spec_v2/`
- `dev/archive/`

**ACTION**: DO NOT MODIFY.

---

## COMMON PITFALLS

- **Player Logic**: NO code in `PlayerActivity.kt`. Use `ui/player/helpers/`.
- **Coroutines**: Use `Dispatchers.IO`. Check `Job.isActive`.
- **FTP**: Handle timeouts. No `completePendingCommand` after error.
- **Images**: Edit = Download -> Edit -> Upload (`NetworkImageEditUseCase`).
- **File Storage**: NO root saves. Use `temp/`.

---

## ENGINEERING WORKFLOW

**Rule**: Review plan. Wait for approval.

**For every issue:**
1. Tradeoffs.
2. Recommendation.
3. Wait for input.

### Principles
- DRY.
- Test coverage.
- "Engineered enough".
- Correctness > Speed.
- Explicit > Clever.

### Review Areas
- **Architecture**: Design, Boundaries, Dependencies, Security.
- **Code**: Structure, DRY, Errors, Debt.
- **Tests**: Coverage, Assertions, Edge Cases.
- **Performance**: N+1, I/O, Memory, CPU.

### Recommendation Protocol

**Format:**
1. Problem.
2. Impact.
3. Options (Effort/Risk/Impact/Cost).
4. Recommendation.

**Action**: Ask approval.

### Workflow Rules

- NO assumed priorities.
- Pause after each section.
- NO implementation without confirmation.

### Start Mode

**Query**: "BIG change or SMALL change?"

**BIG**:
- Full review.
- Highlight top 3-4 issues.

**SMALL**:
- One focused question per section.
- Concise review.

### Output Style

- Structured.
- Opinionated.
- Risk-focused.
- Role: Staff/Senior Engineer.
