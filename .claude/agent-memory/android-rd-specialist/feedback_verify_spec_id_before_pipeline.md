---
name: feedback-verify-spec-id-before-pipeline
description: Before declaring "Stage 0 / launching /spec-all <Sxxxx>", verify the id resolves and matches the IDE-open file; never announce a pipeline start on an unverified id
metadata:
  type: feedback
---

For any `/spec-*` invocation with an `Sxxxx` argument, do NOT print "Запускаю Stage 0 для Sxxxx" or start any pipeline-y narration until BOTH of these succeed:

1. `"/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -File scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json` returns a non-empty object.
2. If an IDE-opened file matches `PLAN/S\d{4}_*.md`, the id from step 1 matches that file's id. If not, surface the mismatch as a typo candidate before doing anything else.

**Why:** the user observed me announce `/spec-all S2074`, run `select.ps1`, get `[]`, and still narrate progress. The IDE had `S0274_kotlin_hotspots_decomposition.md` open - clear typo. Announcing a long pipeline on an unverified id wastes user attention and risks creating bogus tickets / committing to non-existent ones. Combined with also fumbling the `pwsh` path on the first call (see `feedback_pwsh_path.md` in user-scope memory, outside this corpus), the start of the session looked sloppy.

**How to apply:**
- On receiving `/spec-* Sxxxx`, the very first tool call is `select.ps1 -Id Sxxxx -Format json` with the **full pwsh path**.
- Only after empty/match check, print a one-line confirmation: `Тикет Sxxxx подтверждён - <name>, status=<…>. Запускаю …`.
- On empty result + IDE has a different `Sxxxx` open → ask the user which id is correct via AskUserQuestion BEFORE any further steps. Do not "start" anything.
- On empty result + no IDE hint → ask whether to allocate a new id via `next-id.ps1` or whether it was a typo.
- This rule reinforces `feedback_pwsh_path.md` (user-scope memory: use full path from first call) and [[pwsh-efficiency]] (`-NoProfile` always).
