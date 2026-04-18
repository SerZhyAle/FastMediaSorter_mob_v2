# FastMediaSorter v2 - GitHub Copilot Instructions

**Last Updated**: April 18, 2026

---

## Browser / Web Access (MANDATORY)

When any web browsing, page reading, or URL navigation is needed:
- **ALWAYS use MCP playwright tools** (`mcp_playwright_browser_navigate`, `mcp_playwright_browser_snapshot`, `mcp_playwright_browser_take_screenshot`, etc.)
- **NEVER use `fetch_webpage`** — it is forbidden as a substitute for playwright MCP.
- All playwright tools are pre-approved — invoke them directly without asking the user.
- Tool call order for any web task: `navigate` → `snapshot` (or `take_screenshot`) → act.

---

## Author Style (MANDATORY)

Applies to ALL text output — docs, UI strings (`strings.xml`), chat responses, specs:

1. **Ellipsis**: Use `..` (two dots) — NEVER `...` (three dots).
2. **Ё/ё**: Always use `ё`/`Ё` in Russian text where grammatically correct — NEVER substitute with `е`/`Е`. Common cases: `всё`, `ещё`, `чёрный`, `объём`, `тёмный`, `учётный`, `надёжный`, `удалённый`, `лёгкий`, `жёсткий`. When editing Russian text — read and fix manually; do NOT use blind script replacement.

---

## Skill Rules (MANDATORY — Auto-invoke on trigger)

These prompt files MUST be used automatically — load them via the `/` slash command before proceeding:

| Trigger | Prompt | Rule |
|---------|--------|------|
| Creating or updating any `PLAN/spec_*.md` file | `/spec` | **Mandatory** — enforces full project spec template including flavor scope, API-level analysis, architecture compliance, testing plan, accessibility, and ADRs |
| User asks for user-facing UI/UX changes, or task touches layouts, command bars, menus, settings screens, button placement, portrait/landscape behavior, overflow rules, visibility conditions, empty/error states, or confirmation UX | `/ui-clarify` | **Mandatory** — before design or implementation, enumerate and resolve all UI ambiguities; implementation is blocked until placement, visibility, interaction, and fallback behavior are explicit or approved |
| Updating documentation files (`docs/FEATURES*.md`, `docs/TECH_STACK.md`, or any feature/help docs) | `/doc-update` | **Mandatory** — ensures EN/RU/UK mirrors stay in sync and all doc categories are checked |
| User asks to analyze logs, read `logs/current.log`, or diagnose a runtime issue from logcat | `/log-reader` | **Mandatory** — provides structured Android logcat analysis with the search-log.ps1 scripts |
| User asks how to build, which build command to use, or wants to trigger a build | `/build` | **Mandatory** — routes to the correct flavor/variant build command |
| User asks about git commits, staging, pushing, diffs, old file versions, or "what should I commit" | `/git` | **Mandatory** — provides project-aware git workflow guidance |

Prompt files are located in `.github/prompts/`. Do NOT handle these tasks ad-hoc — always route through the corresponding prompt.

---

<ai_core_directives>
  <routing_rules>
      - Fast research entrypoint (open first): `dev/PROJECT_OPERATIONS_INDEX.md`
     - Project Architecture & Dataflow: ALWAYS READ `docs/ARCHITECTURE.md`
     - PowerShell Scripts, Build/Deploy commands, Feature Flags: ALWAYS READ `docs/DEV_OPS.md`
     - Specific Libraries, DB/Network rules: ALWAYS READ `docs/TECH_STACK.md`
     - Full tech stack, dependencies, constraints, min/recommended requirements: ALWAYS READ `dev/TECH_REQUIREMENTS.md`
     - Tools/Libraries versions: CHECK `gradle/libs.versions.toml`; if missing, USE `app_v2/build.gradle.kts` and `wear/build.gradle.kts` as source of truth
     - Model selection (Haiku/Sonnet/Opus): ALWAYS CHECK `ChoiceModelRules.md` 
  </routing_rules>

  <project_navigation_index>
    <topology>
      - Root modules: `app_v2/` (main Android app), `wear/` (Wear OS companion), `docs/` (knowledge base), `dev/` (workflow/specs/scripts), `scripts/` (automation), `temp/` (scratch/logs/backups only).
      - Read-only zones: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
      - Core app package root: `app_v2/src/main/java/com/sza/fastmediasorter/`.
      - Wear package root: `wear/src/main/java/com/sza/fastmediasorter/wear/`.
    </topology>

    <app_v2_map>
      - `ui/` -> screens/fragments/compose UI + ViewModels (no business logic).
      - `domain/` -> UseCases and domain abstractions.
      - `data/` -> repositories/data sources/db/network adapters.
      - `di/` -> Hilt modules.
      - `core/`, `util/`, `utils/`, `worker/`, `widget/` -> shared infra/background/widget logic.
      - `FastMediaSorterApp.kt` -> application entrypoint.
    </app_v2_map>

    <wear_map>
      - `ui/`, `domain/`, `data/`, `di/` mirrors app layering for Wear.
      - `MainActivity.kt` -> Wear host activity (keep heavy logic out).
      - `FastMediaSorterWearApp.kt` -> Wear application entrypoint.
    </wear_map>

    <build_and_variants>
      - Included modules: `settings.gradle.kts` -> `:app_v2`, `:wear`.
      - Main build config: `app_v2/build.gradle.kts` (SDK 35, Java 17, flavors `standard|lite|photos|legacy`).
      - Wear build config: `wear/build.gradle.kts` (SDK 35, Java 17, Compose + Hilt).
      - Primary commands: `./dev/build-with-version.ps1`, `./build-debug.PS1`, `./gradlew.bat assembleStandardDebug`, `./gradlew.bat testStandardDebugUnitTest`, `./gradlew.bat lintStandardDebug`.
    </build_and_variants>

    <research_shortcuts>
      - Open `dev/PROJECT_OPERATIONS_INDEX.md` first for workspace/module routing.
      - If task is architecture/data-flow -> open `docs/ARCHITECTURE.md` first.
      - If task is build/flags/flavors/scripts -> open `docs/DEV_OPS.md` and `app_v2/build.gradle.kts`.
      - If task is dependency/protocol specifics -> open `docs/TECH_STACK.md` + `dev/TECH_REQUIREMENTS.md` + module `build.gradle.kts`.
      - If task is process/phase compliance -> open `dev/AGENT_WORKFLOW.md`.
      - If task is doc discovery -> open `docs/DOCS_MAP.md`.
    </research_shortcuts>

    <known_repo_realities>
      - `gradle/libs.versions.toml` may be absent in this repository snapshot.
      - Dependency versions are currently pinned directly in module Gradle files.
      - `temp/` is the only allowed location for generated logs, backups, and research artifacts.
    </known_repo_realities>
  </project_navigation_index>

  <strict_constraints>
    <constraint>ROOT_CLEANLINESS: MANDATORY. Never write files to root. Use `temp/`.</constraint>
    <constraint>FILE_SIZE: Max 1000 lines. SPLIT large logic into `helpers/*.kt`.</constraint>
    <constraint>ACTIVITY_LOGIC: PROHIBITED. Delegate complex logic to `helpers/*Manager`.</constraint>
    <constraint>LOGGING: Use `Timber`. `Log.d()` is PROHIBITED. Write physical output to `temp/*.log`.</constraint>
    <constraint>READ_ONLY_ZONES: DO NOT MODIFY `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.</constraint>
    <constraint>SAFETY_BACKUP: IF modifying file > 500 lines, FIRST create backup in `temp/` with timestamp.</constraint>
    <constraint>COMMON_PITFALLS: NO player code in `PlayerActivity.kt`. Use `Dispatchers.IO` for Coroutines. FTP MUST handle timeouts. NO file saves in root.</constraint>
    <constraint>DEV_CHANGELOG: MANDATORY. After EVERY code/config modification, run `.\scripts\add_to_dev_log.ps1 "path" "target" "description"` to log the change to `dev/CHANGELOG.md`. Execute AFTER each modification step, BEFORE moving to next task. Applies to ALL agents.</constraint>
    <constraint>FEATURES_DOCS_UPDATE: MANDATORY. After implementing ANY new user-facing feature, add a bullet entry to ALL THREE files: `docs/FEATURES.md` (EN), `docs/FEATURES_RU.md` (RU), `docs/FEATURES_UK.md` (UK). Do this at end of Step 4, BEFORE marking task complete. Match the bullet style of existing entries. Applies to ALL agents. NO exceptions.</constraint>
    <constraint>COMMENTS_READ_FIRST: MANDATORY. Before editing any file, read ALL existing inline comments and KDoc/Javadoc in the affected area. Treat them as requirements — they encode intent, constraints, and non-obvious decisions. DO NOT ignore or overwrite comments without understanding them first.</constraint>
    <constraint>COMMENTS_WRITE_ON_MODIFY: MANDATORY. When adding or changing logic, add an inline comment explaining WHY (not what) whenever the reason is not immediately obvious from the code. Remove or update stale comments that no longer reflect reality. Applies to ALL agents.</constraint>
    <constraint>UI_TRIGGER_ROW: MANDATORY. All toggle/switch and checkbox rows MUST follow the canonical patterns defined in `docs/ARCHITECTURE.md` § "UI Patterns — Trigger Row". SWITCH rows: title=`toggler_title_text_size` (14sp), help-text=`toggler_desc_text_size` (12sp), help-icon `ic_help_outline_24` always rightmost child. CHECKBOX rows: help-text=`text_size_small` (14sp), indent=`checkbox_subtitle_margin_start`. Help text is ALWAYS 2sp smaller than the trigger label. NEVER hardcode sp values. Applies to ALL agents.</constraint>
    <constraint>UI_AMBIGUITY_GATE: MANDATORY. For ANY user-facing change touching layout, command placement, settings UI, menus, button visibility, portrait/landscape behavior, overflow rules, labels, icons, help text, confirmation/fallback UX, or empty/error states, DO NOT start implementation until all ambiguous UI decisions are explicitly resolved. Minimum checklist: exact placement per orientation, direct button vs overflow vs top menu, visibility predicates by file/media type, priority relative to existing actions, icon/text/tooltip, disabled/hidden behavior, empty/error/loading states, confirmation/overwrite/fallback behavior, and accessibility implications. If the request, spec, or current code leaves any item unclear, stop in Step 0 or Step 2, ask targeted questions, and wait for alignment or explicit delegated assumptions.</constraint>
  </strict_constraints>

  <workflow_stages>
    MANDATORY 5-STEP ENGINEERING WORKFLOW. Strict phase separation. No coding before Step 4.

    <step id="0" name="TASK DEFINITION">
      Ask clarifying questions. Expand task. Output detailed description in RUSSIAN inside `dev/` directory. GATE: Wait for user alignment.
      For any user-facing UI/UX work, produce an explicit ambiguity checklist before proceeding. Implementation is forbidden until every item is answered or the user explicitly delegates decision authority for the remaining items.
    </step>
    
    <step id="1" name="RESEARCH PHASE">
      Analyze "AS-IS". Launch subagents if needed. Collect exact lines, files, paths. Output full context to `temp/`.
    </step>
    
    <step id="2" name="DESIGN PHASE">
      Prepare C4 architecture/solution design (improvements, fixes, data flow, ADR, testing). Output in RUSSIAN to `dev/`. GATE: Wait for human REVIEW.
      For UI/UX tasks, the design must include a decision table for portrait/landscape placement, overflow strategy, visibility rules, fallback/confirmation UX, and accessibility. Missing decisions keep the task blocked.
    </step>
    
    <step id="3" name="PLANNING PHASE">
      Break design into English execution plan (sequence, priority). Large tasks = strategic file + tactical phases files. Add exact prompts per step. Output: Markdown checklists.
    </step>
    
    <step id="4" name="IMPLEMENTATION">
      Execute iteratively AFTER human review. Write code, build, and commit after each step. Mark progress in checklist `[x]`. After implementing any new user-facing feature, update ALL THREE feature inventory files before closing the task: `docs/FEATURES.md` (EN), `docs/FEATURES_RU.md` (RU), `docs/FEATURES_UK.md` (UK).
    </step>
  </workflow_stages>

  <code_review_principles>
    - Review before coding. Highlight 3-4 issues if "BIG change" or focused analysis if "SMALL change".
    - Tradeoffs -> Recommendation -> Wait for input.
    - DRY, Test coverage, Correctness > Speed, Explicit > Clever.
    - Review boundaries: Architecture, Structure, Debt, Memory, N+1.
  </code_review_principles>

  <scripts_reference>
    <!-- USE THESE SCRIPTS DIRECTLY. Do not reinvent ad-hoc Select-String chains. -->

    <log_analysis>
      <!-- Default log: temp/current.log. Android logcat format: DATE TIME PID-TID TAG PKG LEVEL MSG -->
      <script>scripts/utils/search-log.ps1</script>
      <usage>
        # Overview / noise
        .\scripts\utils\search-log.ps1 -Summary
        .\scripts\utils\search-log.ps1 -Spam -Top 20

        # Errors / warnings
        .\scripts\utils\search-log.ps1 -Errors
        .\scripts\utils\search-log.ps1 -Warnings
        .\scripts\utils\search-log.ps1 -Errors -From "01:20:00" -To "01:25:00"
        .\scripts\utils\search-log.ps1 -Errors -OutFile "temp/errors.txt"

        # Pattern / tag search
        .\scripts\utils\search-log.ps1 -Pattern "SORT_DEBUG"
        .\scripts\utils\search-log.ps1 -Tag "BrowseViewModel"
        .\scripts\utils\search-log.ps1 -Tag "ImageLoad" -Level E
        .\scripts\utils\search-log.ps1 -Pattern "Exception|crash" -Context 5

        # Multi-tag flow trace
        .\scripts\utils\search-log.ps1 -Flow "BrowseViewModel","GoogleDrive","MediaFileAdapter"

        # Limit / count
        .\scripts\utils\search-log.ps1 -Pattern "thumbnail" -Top 30
        .\scripts\utils\search-log.ps1 -Errors -Last 20
        .\scripts\utils\search-log.ps1 -Pattern "crash" -Count

        # Filters
        .\scripts\utils\search-log.ps1 -AppOnly -Warnings
        .\scripts\utils\search-log.ps1 -Tag "BrowseViewModel" -Exclude "updateLayout|scrollTo"

        # Custom log file
        .\scripts\utils\search-log.ps1 -LogFile "temp/build_err7.txt" -Errors
      </usage>
    </log_analysis>

    <build_scripts>
      <!-- Debug builds (no version bump) -->
      .\scripts\builders\build-debug.PS1                  # standard flavor
      .\scripts\builders\build-debug.PS1 -SkipZip         # standard, no zip
      .\scripts\builders\build-debug-clean.PS1            # clean + standard debug
      .\scripts\builders\build-lite-debug.ps1             # lite flavor
      .\scripts\builders\build-photos-debug.ps1           # photos flavor
      .\scripts\builders\build-legacy-debug.ps1           # legacy flavor
      .\scripts\builders\clean-gradle-caches.ps1          # stop daemons + clean caches

      <!-- Release builds -->
      .\scripts\builders\build-standard-release.ps1
      .\scripts\builders\build-aab-release.ps1
      .\scripts\builders\build-wear-release.PS1

      <!-- Versioned build (bumps version code/name) -->
      .\dev\build-with-version.ps1
    </build_scripts>

    <device_scripts>
      .\scripts\utils\extract-device-logs.ps1             # pull logcat + prefs from connected device
      .\scripts\utils\Install_release_on_adb_connected_device.ps1  # install APK via ADB
      .\scripts\builders\build-standard-device.ps1        # build + install standard debug on device
      .\scripts\builders\build-lite-device.ps1            # build + install lite debug on device
    </device_scripts>

    <test_scripts>
      .\scripts\utils\run-maestro-smoke.ps1               # smoke tests
      .\scripts\utils\run-maestro-smoke.ps1 -Suite critical
      .\scripts\utils\run-stress.ps1                      # all stress tests
      .\scripts\utils\run-stress.ps1 -Test monkey
      .\scripts\utils\run-maestro-stress.ps1 -Suite all -Monitor -Report
      .\scripts\utils\setup_test_media.ps1                # upload test media to device
      .\scripts\utils\setup-avd-for-tests.ps1             # configure AVD for Maestro
    </test_scripts>

    <utility_scripts>
      .\scripts\utils\commit-push.ps1                     # commit + push current branch
      .\scripts\utils\generate-changelog.ps1              # generate changelog from git log
      .\scripts\utils\generate-quality-report.ps1         # lint + test quality report
      .\scripts\utils\check-typo-lint.ps1                 # spell / typo check
      .\scripts\utils\monitor_git.ps1                     # watch git status in real time
      .\scripts\utils\create-release-candidate.ps1        # tag + prepare RC
      .\scripts\add_to_dev_log.ps1 "path" "target" "desc" # append row to dev/CHANGELOG.md (MANDATORY after every code change)
    </utility_scripts>

    <gradlew_shortcuts>
      .\gradlew.bat assembleStandardDebug
      .\gradlew.bat assembleStandardRelease
      .\gradlew.bat testStandardDebugUnitTest
      .\gradlew.bat lintStandardDebug
      .\gradlew.bat :app_v2:dependencies
    </gradlew_shortcuts>
  </scripts_reference>

</ai_core_directives>
