# ENGINEERING WORKFLOW (5-STEP PROCESS)

**Rule**: Strict phase separation. No coding before Step 4.

### 8.0 TASK DEFINITION (Постановка задачи)
- **Action**: Ask clarifying questions. Expand and refine the task.
- **Output**: Detailed task description in **RUSSIAN** inside a file in the `dev/` directory.
- **Gate**: DO NOT proceed until the task is perfectly clarified and aligned with the user.

### 8.1 RESEARCH PHASE (Исследование)
- **Action**: Analyze current "AS-IS" state. Launch multiple subagents if needed.
- **Focus**: Collect files, classes, current solutions, and exact line numbers.
- **Output**: A comprehensive temporary file in `temp/` with the full context.

### 8.2 DESIGN PHASE (Дизайн решения)
- **Action**: Prepare an architecture and solution design using C4 model approach.
- **Focus**: What to improve, fix, and add. Data flow, ADRs, testing requirements, API contracts.
- **Output**: Design document in `dev/` directory in **RUSSIAN**.
- **Gate**: Wait for human REVIEW and confirmation.

### 8.3 PLANNING PHASE (Планирование)
- **Action**: Break down the approved design into an execution plan (sequence, priorities). All planning artifacts MUST be in **ENGLISH**.
- **Focus**: 
  - For large tasks: Create a strategic plan + separate tactical files (phases) for specific steps.
  - For each step: Include clear instructions and detailed prompts.
- **Output**: Work plan files (Markdown) with checkboxes.

### 8.4 IMPLEMENTATION PHASE (Имплементация)
- **Action**: Execute the plan step-by-step AFTER human review and adjustments.
- **Workflow**: 
  - Write code and other objects iteratively.
  - Build and commit after each non-trivial step.
  - Mark progress directly in the planning files (`[x]`).
  - **FEATURES UPDATE (MANDATORY)**: After implementing any new user-facing feature, add a description entry to ALL THREE files: `docs/FEATURES.md` (EN), `docs/FEATURES_RU.md` (RU), `docs/FEATURES_UK.md` (UK). Do this before marking the step complete. Use consistent bullet style matching existing entries.
