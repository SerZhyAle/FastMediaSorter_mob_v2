---
model: sonnet
---

# Arc - Archive Tickets

Alias for `/spec-arc`. Identical semantics: accepts one or several ids, loops `archive.ps1` per id, emits a single batch summary.

See [.claude/commands/spec-arc.md](.claude/commands/spec-arc.md) for the full process.

## Usage

```text
/arc <Sxxxx>
/arc <Sxxxx> <Syyyy> <Szzzz>
/arc <Sxxxx> <Syyyy> --removes-functionality
```

Execute as `/spec-arc` with the same arguments.
