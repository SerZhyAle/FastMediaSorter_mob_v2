---
agent: "agent"
description: "Use when: the task changes user-facing UI/UX, layouts, command bars, menus, settings screens, button placement, portrait/landscape behavior, overflow rules, visibility conditions, empty/error states, confirmation UX, or other interaction details. Triggers on: UI ambiguity, placement, command panel, button visibility, overflow, portrait, landscape, settings UI, tooltip, label, icon, empty state, fallback UX."
---

# UI Clarification Gate

Block implementation until all important UI/UX decisions are explicit.

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

Before design or implementation, identify every meaningful UI ambiguity that could change behavior, placement, discoverability, or user expectations.

If the task touches user-visible wording, labels, help text, errors, empty states, confirmations, or CTAs, treat `docs/COMMUNICATION_POLICY.md` as a mandatory source for tone and message-structure decisions.

The agent must NOT implement while any important item below is unresolved.

---

## Required Checklist

For the requested change, inspect the spec/request and the current code, then produce one decision table in these four passes:

1. Placement and presentation.
   - exact placement in portrait
   - exact placement in landscape
   - tablet / wide-screen if relevant
   - direct button in command bar, overflow menu item, top app bar / toolbar item, or settings row / dialog / bottom sheet

2. Visibility and priority.
   - which media or file types show it
   - feature flags / flavors
   - permissions / read-only / destination availability
   - hidden vs disabled behavior
   - what happens when there is not enough space
   - which actions outrank it
   - whether it may spill to overflow

3. Interaction and wording.
   - label
   - icon
   - tooltip / help text
   - click behavior
   - long-click behavior if applicable
   - if wording changes, apply the message-type formula from `docs/COMMUNICATION_POLICY.md` §2, the next-step rule from §3, and the tone checklist from §6

4. State, failure UX, and accessibility.
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

When this prompt is invoked:

**Step 1 - Read context.**
- Read the user request / spec.
- Read the relevant layouts, controllers, planners, adapters, and strings.
- Read `docs/ARCHITECTURE.md` if the task affects canonical UI patterns.
- Read `docs/COMMUNICATION_POLICY.md` if the task affects any user-visible copy.

**Step 2 - Build the ambiguity list.**
- Separate explicit decisions from implicit assumptions.
- Mark every unresolved item as blocking.

**Step 3 - Ask targeted questions or propose bounded options.**
- Ask only the questions needed to unblock implementation.
- If a decision can be delegated, present 2-3 concrete options with tradeoffs.

**Step 4 - Produce one of two outcomes.**

### Outcome A - BLOCKED
Use this when any important UI decision remains unresolved.

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
Use this only when every important UI decision is explicit or explicitly delegated.

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

If the request/spec uses non-committal wording about two or more UI options, treat that item as unresolved unless one option is explicitly approved or the user explicitly delegates the choice to the agent. Do NOT infer implementation freedom when the choice changes discoverability, placement, or behavior under a different orientation or screen constraint.