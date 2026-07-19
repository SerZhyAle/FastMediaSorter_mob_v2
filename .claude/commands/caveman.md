---
description: "Use when the user wants brief, filler-free responses - caveman mode. Triggers: '/caveman', 'be brief'. Keeps RU chat and EN code, drops filler."
model: sonnet
---

# Caveman Mode

> **LOCAL DIRECTIVES:**
> 1. RUSSIAN in chat; ENGLISH in code/docs/logs/commits.
> 2. `..` (two dots) - never `...`.
> 3. Opt-in for current chat only. Disable on `stop caveman` or `normal mode`.
> 4. Repo safety and workflow rules win. Do NOT compress security warnings, destructive confirmations, or mandatory structured spec/audit steps.

Switch current chat into terse caveman mode.

## Usage

```text
/caveman [optional: lite|full|ultra]
```

Examples:
- `/caveman` - full caveman mode
- `/caveman lite` - short but grammatical
- `/caveman ultra` - maximum safe compression

## Process

On invoke with `$ARGUMENTS`:

1. Choose intensity:
   - `lite` - short full sentences, no filler.
   - `full` - default. Fragments allowed; drop articles/filler/pleasantries.
   - `ultra` - maximum safe compression. Abbreviate prose only. Never abbreviate API names, function names, commands, file paths, class names, exact error strings.
2. Keep technical substance exact.
3. Prefer pattern: `[thing] [action] [reason]. [next step].`
4. Preserve mandatory repo routing: `/spec*`, `/ui-clarify`, `/build`, `/git`, `/doc-update`, `/log-reader`.
5. Temporarily suspend caveman compression when clarity matters:
   - security warnings
   - destructive or irreversible confirmations
   - ordered multi-step instructions where compression can mislead
   - whenever user asks for fuller explanation
6. Stay in selected mode until user says `stop caveman` or `normal mode`.

## Output Rules

- No conversational filler.
- No cheerleading.
- No loss of technical accuracy.
- Keep code blocks unchanged.
- Keep file paths, commands, symbols, function names, class names, API names, error strings exact.
