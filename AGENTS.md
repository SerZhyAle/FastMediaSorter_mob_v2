# FastMediaSorter v2 PROTOCOLS

## 1. COMMUNICATION
- **LANG**: RUSSIAN (chat), ENGLISH (code, docs, logs).
- **TONE**: PROFESSIONAL / DRY / CONCISE. NO guessing, ASK if ambiguous.
- **MISSING INPUT**: REQUEST file/data. DO NOT HALLUCINATE.

## 1a. AUTHOR STYLE (MANDATORY — applies to ALL text output)
- **ELLIPSIS**: Use `..` (two dots) — NEVER `...` (three dots). Applies everywhere: docs, UI strings, chat, specs.
- **Ё/ё**: Always use `ё` (and `Ё`) in Russian text where grammatically correct — NEVER substitute with `е`/`Е`. Examples: `всё`, `ещё`, `чёрный`, `объём`, `тёмный`, `учётный`, `надёжный`, `удалённый`. When editing `.md` files or `strings.xml` — read text and fix manually; don't rely on blind script replacement.

## 1b. CAVEMAN MODE (OPTIONAL)
- **TRIGGER**: ONLY when the user explicitly asks for `caveman`, `less tokens`, `be brief`, `ultra-terse`, or `/caveman`.
- **BEHAVIOR**: Keep RUSSIAN in chat and ENGLISH in code/docs/logs. Drop filler, pleasantries, and hedging. Keep technical terms exact.
- **WORKFLOW**: Repo workflow rules still win. `/spec*`, `/ui-clarify`, `/build`, `/git`, `/doc-update`, and `/log-reader` routing remains mandatory.
- **LIMITS**: Do NOT use caveman compression for security warnings, destructive/irreversible confirmations, or ordered multi-step instructions where compression can create ambiguity.
- **STOP WORDS**: `stop caveman`, `normal mode`.

## 2. PROJECT
- **Stack**: Android (Kotlin 1.9+, Java 17), MVVM+Clean, Hilt, Room, Media3, Timber (NO `Log.d()`).
- **Modules**: `app_v2/` (main), `wear/` (companion), `dev/` (scripts), `docs/`, `temp/` (scratchpad).
- **FAST ENTRYPOINT**: Start research from `dev/PROJECT_OPERATIONS_INDEX.md`.
- **FEATURE MAP**: Use section `Feature-to-Path Map` in `dev/PROJECT_OPERATIONS_INDEX.md` before global search.
- **Package Structure** (`app_v2/src/main/java/com/sza/fastmediasorter/`):
  - `ui/`: MVVM UI layers by feature (`player`, `browse`, `settings`, `main`, etc.).
  - `data/`: Data sources and impls (`local`, `network`, `cloud`, `transfer`, `repository`).
  - `domain/`: Business logic (`usecase`, `repository` interfaces, `model`).
  - `core/`, `di/`, `util/`, `worker/`, `widget/`.
- **Wear Package**: `wear/src/main/java/com/sza/fastmediasorter/wear/`.
- **NO-WRITE ZONES**: `V1/`, `v2_6/`, `spec_v2/`.

## 3. STRICT CODING RULES
- **CLEAN ROOT**: ALL tool outputs, logs, and pre-modification backups MUST go to `temp/`.
- **FILE SIZE**: Max 1000 lines. Extract to `helpers/*Manager`.
- **ARCHITECTURE**: Activity logic PROHIBITED. Delegate to NounVerbManagers.
- **NAMING**: VerbNounUseCase, NounRepository, NounViewModel.
- **LINT**: ALWAYS resolve warnings in touched files. Canonical naming only.
- **COMMENTS — READ FIRST**: Before editing any file, read ALL existing inline comments and KDoc/Javadoc in the affected area. Treat them as requirements — they encode intent, constraints, and non-obvious decisions.
- **COMMENTS — WRITE ON MODIFY**: When adding or changing logic, add an inline comment explaining WHY (not what) whenever the reason is not immediately obvious. Remove or update stale comments that no longer reflect reality.
- **SCRIPT OWNERSHIP**: Do NOT work around broken or weak internal repo scripts when the task depends on them. If a project script has bugs, misses the needed behavior, or can be materially improved to solve the task safely, improve the script itself and then use it.
- **STRING RESOURCE TOOLING**: For single-key updates in `values*/strings.xml`, prefer `pwsh -File scripts/utils/set-android-string.ps1 -Module app_v2|wear -Locale en|ru|uk -Key "<key>" -Value "<text>"`. Use manual XML edits only for `plurals`, `string-array`, comments, regrouping, or bulk rewrites.
- **UI AMBIGUITY GATE**: For ANY user-facing change touching layout, command placement, menus, settings UI, portrait/landscape behavior, overflow rules, visibility conditions, labels/icons/tooltips, empty/error states, or confirmation/fallback UX, DO NOT implement until all ambiguous decisions are resolved. Minimum checklist: exact placement per orientation, direct button vs overflow vs top menu, visibility by media/file type, action priority, hidden vs disabled behavior, empty/error/loading states, overwrite/confirmation/fallback behavior, and accessibility implications.
- **LAYOUT_ORIENTATION**: MANDATORY. Any edit to `res/layout/*.xml` MUST be followed by a check of the corresponding `res/layout-land/*.xml`. If the landscape variant exists → apply the equivalent change in the same commit/step. If it does not exist but the screen supports landscape → either create the file or add an explicit blocker. **Never leave portrait-only edits in a layout that has a landscape counterpart.** Applies to ALL agents. NO exceptions.

## 4. COMMANDS
- Build: `.\dev\build-with-version.ps1`, `.\build-debug.PS1`.
- Gradle: `assembleStandardRelease`, `testStandardDebugUnitTest`, `lintStandardDebug`.
- Flavors: `standard` (all), `lite` (no audio/cloud/docs/anim), `photos` (images/anim only), `legacy` (no cloud/docs).
- Strings: `pwsh -File scripts/utils/set-android-string.ps1 -Module app_v2 -Locale ru -Key "cloud_check_failed" -Value "..." [-ExpectedOldValue "..."] [-CreateIfMissing]`.

## 5. WORKFLOW
- **CRITICAL**: For ANY task larger than a single file fix, you MUST read `dev/AGENT_WORKFLOW.md` BEFORE execution to follow the 5-step engineering process. Network specific notes are in `dev/NETWORK_SPECS.md`.
- **RESEARCH ORDER**: `dev/PROJECT_OPERATIONS_INDEX.md` -> domain-specific doc (`docs/ARCHITECTURE.md` / `docs/DEV_OPS.md` / `docs/TECH_STACK.md` / `dev/TECH_REQUIREMENTS.md`) -> implementation files.
- **TECH REQUIREMENTS**: For full dependency inventory, platform constraints, and min/recommended requirements always check `dev/TECH_REQUIREMENTS.md`.
- **HYGIENE**: Ignore `*.backup` files in primary analysis unless user explicitly requests historical comparison.
- **UI TASKS**: If the task changes user-facing behavior, first enumerate UI/UX ambiguities and get alignment. No implementation while any important UI decision remains implicit, contradictory, or "acceptable either way".

## 6. DEV CHANGELOG (MANDATORY)
- **RULE**: After EVERY code/config file modification, log the change to `dev/CHANGELOG.md` via script.
- **COMMAND**: `.\scripts\add_to_dev_log.ps1 "<relative_path>" "<class_or_target>" "<short_description>"`
- **EXAMPLE**: `.\scripts\add_to_dev_log.ps1 "app_v2/src/.../GlideAppModule.kt" "GlideAppModule" "Fixed memory cache formula to heap×10%"`
- **WHEN**: At the end of each implementation step, BEFORE moving to next task.
- **FORMAT**: The script auto-appends a timestamped row to `dev/CHANGELOG.md` (Markdown table).
- **SCOPE**: Applies to ALL agents (Copilot, Cursor, Windsurf, CLI agents). NO exceptions.
## 7. FEATURES DOCS UPDATE (MANDATORY)
- **RULE**: After implementing ANY new user-facing feature, update the feature inventory in ALL THREE language variants.
- **FILES**:
  - `docs/FEATURES.md` — English (canonical)
  - `docs/FEATURES_RU.md` — Russian
  - `docs/FEATURES_UK.md` — Ukrainian
- **WHEN**: At the end of Step 4 (Implementation), BEFORE marking the task complete.
- **WHAT**: Add a concise bullet under the relevant section (or create a new section if needed). Keep consistent style with existing entries.
- **SCOPE**: Applies to ALL agents. NO exceptions.