---
name: workflow-args-trap
description: Workflow tool args arrive as a JSON string, and resume does not re-supply args - guard and re-pass
metadata:
  type: reference
---

Two Workflow-tool gotchas observed 2026-07-02 (run wf_95bd68a0-c09):

1. An `args` value passed as a JSON array reached the script as a STRING - `args.map` threw. Guard every script with: `const data = typeof args === 'string' ? JSON.parse(args) : args`.
2. Resuming via `{scriptPath, resumeFromRunId}` does NOT re-supply the original args - args is `undefined` on resume. Always pass `args` again in the resume call.

**How to apply:** any Workflow script that consumes args gets the typeof-guard on line one of the body; any resume call repeats the args parameter verbatim.
