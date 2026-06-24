---
model: sonnet
---

# Arc - Archive Tickets

Alias for `/spec-arc`. Same semantics: accept 1+ ids, loop `archive.ps1` per id, emit single batch summary.

Full process: [.claude/commands/spec-arc.md](.claude/commands/spec-arc.md).

## Usage

```text
/arc <Sxxxx>
/arc <Sxxxx> <Syyyy> <Szzzz>
/arc <Sxxxx> <Syyyy> --removes-functionality
```

Execute as `/spec-arc` with the same arguments.
