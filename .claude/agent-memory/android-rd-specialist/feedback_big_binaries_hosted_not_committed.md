---
name: big-binaries-hosted-not-committed
description: Owner's ruling - host large build/runtime binaries as GitHub Release assets, never commit them to the repo
metadata:
  type: feedback
---

When a build or runtime needs a large binary the repo does not carry, publish it as a GitHub Release
asset and fetch it, rather than committing it or weakening the build to skip it.

**Why:** offered four ways to fix four months of red CI (commit the 11.5 MB AAR / host + download /
make the dependency optional / turn CI off), the owner picked host + download on 2026-08-09. Committing
would add the binary to history on every rebuild; making the dependency optional would stop CI proving
the real product. The repo already had the pattern - the permanent `delivery-so-v1` release.

**How to apply:** reach for the existing delivery release first, check `delivery/INVENTORY.md`, and add
a fetch step rather than a `.gitignore` exception. Do not pin the asset's SHA-256 in a workflow when a
rebuild would then need two synchronised edits - a forgotten pin turns CI red, which is the failure
mode being cured. See [[gh-cli-location]] for the gh path this needs.
