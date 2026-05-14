# ENGINEERING WORKFLOW (5-STEP PROCESS)

**Rule**: Strict phase separation. No coding before Step 4.

### 8.0 TASK DEFINITION (Постановка задачи)
- **Action**: Ask clarifying questions. Expand and refine the task.
- **Output**: Detailed task description in **RUSSIAN** inside a file in the `dev/` directory.
- **Gate**: DO NOT proceed until the task is perfectly clarified and aligned with the user.
- **Branch check**: Run `git branch --show-current`. Confirm the session is on the expected branch before any file edit. If on `main`, proceed only for hotfixes or pre-branch tooling setup; all other development belongs on a `DEBUG-v00N` branch.
- **UI/UX Gate**: For any user-facing change, explicitly enumerate all ambiguous UI decisions before continuing. Minimum checklist: placement per orientation, direct button vs overflow vs top menu, visibility predicates by media/file type, action priority, hidden vs disabled behavior, labels/icons/tooltips/help text, empty/error/loading states, confirmation/overwrite/fallback behavior, and accessibility. If any item is unresolved, implementation is blocked.

### 8.1 RESEARCH PHASE (Исследование)
- **Action**: Analyze current "AS-IS" state. Launch multiple subagents if needed.
- **Focus**: Collect files, classes, current solutions, and exact line numbers.
- **Output**: A comprehensive temporary file in `temp/` with the full context.

### 8.2 DESIGN PHASE (Дизайн решения)
- **Action**: Prepare an architecture and solution design using C4 model approach.
- **Focus**: What to improve, fix, and add. Data flow, ADRs, testing requirements, API contracts.
- **Output**: Design document in `dev/` directory in **RUSSIAN**.
- **Gate**: Wait for human REVIEW and confirmation.
- **UI/UX Deliverable**: For UI tasks, include a decision table with approved behavior for portrait, landscape, overflow, visibility, fallback, and accessibility. Missing decisions keep the task blocked.

### 8.3 PLANNING PHASE (Планирование)
- **Action**: Break down the approved design into an execution plan (sequence, priorities). All planning artifacts MUST be in **ENGLISH**.
- **Focus**: 
  - For large tasks: Create a strategic plan + separate tactical files (phases) for specific steps.
  - For each step: Include clear instructions and detailed prompts.
- **Output**: Work plan files (Markdown) with checkboxes.
- **Branch note**: If this task includes work not intended for the upcoming release, identify those steps explicitly and flag them for the "future" DEBUG branch.

### 8.4 IMPLEMENTATION PHASE (Имплементация)
- **Action**: Execute the plan step-by-step AFTER human review and adjustments.
- **Workflow**: 
  - Write code and other objects iteratively.
  - Build and commit after each non-trivial step.
  - Mark progress directly in the planning files (`[x]`).
  - **FLAVOR RULES**: If the task involves a specific flavor (e.g., `noLegal`, `vr`), strictly follow the isolation rules in `dev/FLAVOR_DEVELOPMENT_RULES.md`. DO NOT use `BuildConfig` checks in `src/main`.
  - **FEATURES UPDATE (MANDATORY)**: After implementing any new user-facing feature, add a description entry to ALL THREE files: `docs/FEATURES.md` (EN), `docs/FEATURES_RU.md` (RU), `docs/FEATURES_UK.md` (UK). Do this before marking the step complete. Use consistent bullet style matching existing entries.
