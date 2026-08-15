---
name: r8-mapping-is-174mb
description: The standard-release R8 mapping.txt is ~174 MB - any script that inspects it must stream, never load it into an array
metadata:
  type: project
---

`app_v2/build/outputs/mapping/standardRelease/mapping.txt` measured **174 MB** on 2026-08-15 (S1674). A gate that inspects it must read it with a streaming `[System.IO.StreamReader]` in one pass.

**Why:** the obvious PowerShell form, `@(Get-Content -LiteralPath $Mapping)`, materialises millions of lines into an array - minutes of wall clock and gigabytes of RAM for a check that only ever needs the line in front of it. Rewritten as a single streaming pass, the same check over the same file runs in **7.2 s**. Written after the array version was authored and caught before it ever ran against the real artifact.

**How to apply:** whenever a script reads the mapping (verifying keep rules actually held, hunting a renamed member, measuring optimization), stream it and track the current class-block as you go. The same caution applies to any other build output that scales with the whole program: dex dumps, full lint XML, `problems-report.html`.

Related: producing the file at all needs a real minified build (`scripts/builders/build-standard-release.ps1`), which is a *build*, not a release publish - see [[verify-platform-api-with-javap]] for the same "measure the artifact rather than trust the declaration" habit.
