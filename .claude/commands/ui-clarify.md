# UI Clarification Gate

Block implementation until all important UI/UX decisions explicit.

## Usage

```
/ui-clarify <short task description>
```

Examples:
- `/ui-clarify add Save Frame to player UI`
- `/ui-clarify change settings row layout for video options`
- `/ui-clarify move player action between command bar and overflow`

---

## Goal

Before design/impl, identify every meaningful UI ambiguity affecting behavior, placement, discoverability, or user expectations.

Task touches user-visible wording / labels / help text / errors / empty states / confirmations / CTAs → `docs/COMMUNICATION_POLICY.md` mandatory source for tone + message-structure.

Do NOT implement while any important item below unresolved.

---

## Required Checklist

Inspect spec/request + current code, produce one decision table in four passes:

1. Placement and presentation.
   - exact placement in portrait
   - exact placement in landscape
   - tablet / wide-screen if relevant
   - direct button in command bar, overflow menu item, top app bar / toolbar item, or settings row / dialog / bottom sheet

2. Visibility and priority.
   - which media/file types show it
   - feature flags / flavors
   - permissions / read-only / destination availability
   - hidden vs disabled behavior
   - behavior when not enough space
   - which actions outrank it
   - may spill to overflow?

3. Interaction and wording.
   - label
   - icon
   - tooltip / help text
   - click behavior
   - long-click behavior if applicable
   - wording changes → apply message-type formula `docs/COMMUNICATION_POLICY.md` §2, next-step rule §3, tone checklist §6

4. State, failure UX, accessibility.
   - empty state
   - loading state
   - error state
   - confirmation dialog
   - overwrite / fallback / retry behavior
   - touch target
   - contentDescription / TalkBack
   - discoverability when hidden in overflow

---

## Process

On invocation:

**Step 1 - Read context.**
- Read user request / spec.
- Read relevant layouts, controllers, planners, adapters, strings.
- Read `docs/ARCHITECTURE.md` if task affects canonical UI patterns.
- Read `docs/COMMUNICATION_POLICY.md` if task affects user-visible copy.

**Step 2 - Build ambiguity list.**
- Separate explicit decisions from implicit assumptions.
- Mark every unresolved item blocking.

**Step 3 - Ask targeted questions or propose bounded options.**
- Ask only questions needed to unblock impl.
- Delegable decision → present 2-3 concrete options with tradeoffs.

**Step 4 - Produce one of two outcomes.**

### Outcome A - BLOCKED
Use when any important UI decision unresolved.

```markdown
## UI Clarification Status
Status: BLOCKED

### Confirmed
- <explicitly confirmed items>

### Unresolved
1. <question>
2. <question>

### Why implementation is blocked
<1-3 sentences>
```

### Outcome B - READY
Use only when every important UI decision explicit or explicitly delegated.

```markdown
## UI Clarification Status
Status: READY

### Approved Decisions
- <portrait behavior>
- <landscape behavior>
- <overflow behavior>
- <visibility rules>
- <fallback/error behavior>

### Delegated Assumptions
- <only if user explicitly allowed agent choice>
```

---

## Hard Rule

Request/spec uses non-committal wording about 2+ UI options → treat item as unresolved unless one option explicitly approved or user explicitly delegates choice to agent. Do NOT infer implementation freedom when choice changes discoverability, placement, or behavior under different orientation / screen constraint.
