---
mode: agent
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

For the requested change, inspect the spec/request and the current code, then produce a decision table covering:

1. Exact placement in each orientation.
   - portrait
   - landscape
   - tablet / wide-screen if relevant

2. Presentation mode.
   - direct button in command bar
   - overflow menu item
   - top app bar / toolbar item
   - settings row / dialog / bottom sheet

3. Visibility rules.
   - which media or file types show it
   - feature flags / flavors
   - permissions / read-only / destination availability
   - hidden vs disabled behavior

4. Priority and degradation.
   - what happens when there is not enough space
   - which actions outrank it
   - whether it may spill to overflow

5. Interaction details.
   - label
   - icon
   - tooltip / help text
   - click behavior
   - long-click behavior if applicable

6. State and failure UX.
   - empty state
   - loading state
   - error state
   - confirmation dialog
   - overwrite / fallback / retry behavior

7. Accessibility.
   - touch target
   - contentDescription / TalkBack
   - discoverability when hidden in overflow

8. Copy and tone rules when wording changes.
   - message-type formula from `docs/COMMUNICATION_POLICY.md` §2
   - whether one contextual next step is allowed or required under §3
   - whether the wording passes the §6 tone checklist

---

## Process

When this prompt is invoked:

**Step 1 — Read context.**
- Read the user request / spec.
- Read the relevant layouts, controllers, planners, adapters, and strings.
- Read `docs/ARCHITECTURE.md` if the task affects canonical UI patterns.
- Read `docs/COMMUNICATION_POLICY.md` if the task affects any user-visible copy.

**Step 2 — Build the ambiguity list.**
- Separate explicit decisions from implicit assumptions.
- Mark every unresolved item as blocking.

**Step 3 — Ask targeted questions or propose bounded options.**
- Ask only the questions needed to unblock implementation.
- If a decision can be delegated, present 2-3 concrete options with tradeoffs.

**Step 4 — Produce one of two outcomes.**

### Outcome A — BLOCKED
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

### Outcome B — READY
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

If the request/spec says something like "acceptable", "preferably", "MVP", or "it can be either", do NOT treat that as implementation freedom when it affects discoverability or behavior in a mode that has different UI constraints. Convert it into an explicit decision first.