# FastMediaSorter v2 - GitHub Copilot Instructions

**Last Updated**: February 28, 2026

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
  </strict_constraints>

  <workflow_stages>
    MANDATORY 5-STEP ENGINEERING WORKFLOW. Strict phase separation. No coding before Step 4.

    <step id="0" name="TASK DEFINITION">
      Ask clarifying questions. Expand task. Output detailed description in RUSSIAN inside `dev/` directory. GATE: Wait for user alignment.
    </step>
    
    <step id="1" name="RESEARCH PHASE">
      Analyze "AS-IS". Launch subagents if needed. Collect exact lines, files, paths. Output full context to `temp/`.
    </step>
    
    <step id="2" name="DESIGN PHASE">
      Prepare C4 architecture/solution design (improvements, fixes, data flow, ADR, testing). Output in RUSSIAN to `dev/`. GATE: Wait for human REVIEW.
    </step>
    
    <step id="3" name="PLANNING PHASE">
      Break design into English execution plan (sequence, priority). Large tasks = strategic file + tactical phases files. Add exact prompts per step. Output: Markdown checklists.
    </step>
    
    <step id="4" name="IMPLEMENTATION">
      Execute iteratively AFTER human review. Write code, build, and commit after each step. Mark progress in checklist `[x]`. 
    </step>
  </workflow_stages>

  <code_review_principles>
    - Review before coding. Highlight 3-4 issues if "BIG change" or focused analysis if "SMALL change".
    - Tradeoffs -> Recommendation -> Wait for input.
    - DRY, Test coverage, Correctness > Speed, Explicit > Clever.
    - Review boundaries: Architecture, Structure, Debt, Memory, N+1.
  </code_review_principles>
</ai_core_directives>
