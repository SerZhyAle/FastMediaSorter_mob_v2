# Agent memory - moved

The agent memory of this repository lives in one place only:

**`.claude/agent-memory/android-rd-specialist/`** - `MEMORY.md` is the index, every pointer in it names a file in that same directory.

Read it there whatever runtime loaded you. It is a plain directory on disk; nothing about it is Claude-specific except the path.

This file used to be a hand-made copy of that index plus 125 copies of its memory files. It was frozen on 2026-06-29 while the live set kept growing, so the two disagreed for three months under one name. S2622 deleted the copies and left this pointer: a copy that has to be repeated after every memory write does not survive in a repository where memory is written in every session, and a pointer cannot drift from what it points at.

The path is kept because `docs/PRODUCT_HISTORY*.md` cite it as the source of a 2026-06-27 row, and because `scripts/quality/document-registry-coverage-baseline.txt` needs `.agents/**` to keep matching a directory that holds a document.
