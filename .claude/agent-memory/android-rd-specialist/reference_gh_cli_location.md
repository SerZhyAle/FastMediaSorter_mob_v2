---
name: gh-cli-location
description: GitHub CLI (gh.exe) install path - not on PATH; needed by catalog/release publish scripts
metadata:
  type: reference
---

`gh.exe` is installed at `C:\Program Files\GitHub CLI\gh.exe` but is NOT on PATH in Git Bash or pwsh (`Get-Command gh` / `gh --version` fail).

**Why:** scripts that upload GitHub Release assets (e.g. `scripts/streams/collect-stream-candidates.ps1 -Publish`, which does `gh release upload`) call `Get-Command gh` and throw "gh CLI not found on PATH" even though gh is installed and authenticated.

**How to apply:** before running any publish/release script that shells out to `gh`, prepend the dir to the session PATH:
`$env:PATH = 'C:\Program Files\GitHub CLI;' + $env:PATH`
Auth is already set up (account SerZhyAle, keyring, scopes gist/read:org/repo) - sufficient for `gh release upload`.
