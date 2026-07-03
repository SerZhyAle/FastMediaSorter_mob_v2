---
name: workflow-journal-result-recovery
description: recover completed Workflow agent outputs from the run's journal.jsonl when a later stage crashes the whole workflow
metadata:
  type: reference
---

When a dynamic `Workflow` fails at a late stage (e.g. a synthesis `agent({schema})` hits "StructuredOutput retry cap (5) exceeded"), the EARLIER parallel agents' results are NOT lost - they are cached in the run's journal.

- Transcript dir: `C:\Users\serzh\.claude\projects\<project>\<session>\subagents\workflows\wf_<id>\`
- `journal.jsonl` holds one `{"type":"result","key":"<hash>","result": <agent return value>}` line per completed `agent()` call (plus `started` lines).
- Extract with Python: read `journal.jsonl`, collect `o["type"]=="result"`, dump each `o["result"]` to a file. The `key` is an opaque hash, not the label - index by order of appearance instead.

**Gotcha:** write the dump files with an explicit Windows path (`r'P:\...\temp\...'`) - the bundled Python is Windows Python, so Git-Bash-style `/p/...` paths land in the wrong place silently.

**Avoidance:** keep workflow synthesis schemas SMALL (the 5-researcher synthesis with a deep nested schema is what blew the retry cap). Prefer returning plain text from the final synthesis agent and parse it yourself, or split the schema. Lightweight per-agent text returns (no schema) were robust for the wiring fan-out.
