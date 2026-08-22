# Phase 03 - Fetch command

**Strategic spec:** [`../S1695_release-deobfuscation-artifact-retention.md`](../S1695_release-deobfuscation-artifact-retention.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Introduce `scripts/release/fetch-deobfuscation.ps1`, which returns a retained payload by version in one command and can verify a stored payload without extracting it.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.
- [x] At least one payload is retained in the archive, from the Phase 01 measurement or a real release.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/release/fetch-deobfuscation.ps1` | New | ≤ 300 |

> Budget corrected at audit, 2026-08-15: actual 290 lines, of which 169 are executable - inside the original `≤ 180` estimate. The overrun is the help block and rationale comments only.

---

## Steps

### Step 03.1 - Create the script and its listing mode

**Files:** `scripts/release/fetch-deobfuscation.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/release/fetch-deobfuscation.ps1` in the shape of the neighbouring release scripts. Parameters: `-VersionCode <int>`, `-VersionName <string>`, `-Latest`, `-Variant <string>` defaulting to `standard`, `-Destination <path>` defaulting to `temp/deobfuscation`, `-List`, `-Verify`, `-Json`, `-ArchiveRoot <path>` defaulting to the same root Phase 01 used, `-Help`.
>
> `-List` reads every `<ArchiveRoot>\<versionCode>\manifest.json` and prints one line per retained release: version code, version name, the variants stored, and the payload size. With `-Json`, emit the same as a JSON array. `-List` needs no version argument and exits 0 even when the archive holds nothing, printing that it is empty.
>
> Document the exit codes: 0 = request answered, 1 = the requested release or variant is not retained, 2 = archive root unreachable or unreadable. Honour Rule 7 for every non-1 exit.

**Why:**

Strategic §3.1 requires that recovering an artifact by version number be a single command, and strategic §11 criterion 1 is stated in exactly those terms. Listing exists because the operator arriving from a Play Console crash report knows a version string, not what the archive happens to hold, and an empty archive is a legitimate answer rather than a failure.

**Verification:**

- `Glob` - `scripts/release/fetch-deobfuscation.ps1` exists.
- `Grep` - the `param(` block contains `$Latest`, `$List`, `$Verify`, `$ArchiveRoot`.
- Run with `-List`; exit code equals 0 and the retained versionCode from Phase 01 appears in the output.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Skeleton + -List implemented. Archive root absent = exit 2 (not 1). -List against the phase-01 archive prints 260812204 / 2.60.8122.040 / 21.0 MB [standard], exit 0; empty archive prints a message and still exits 0. assert-exit-contract PASS.

---

### Step 03.2 - Resolve a version and extract the payload

**Files:** `scripts/release/fetch-deobfuscation.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Resolve the target release from whichever of `-VersionCode`, `-VersionName` or `-Latest` was given; more than one supplied at once is an invalid invocation and exits 2. `-VersionName` matches the `versionName` recorded in the manifests, `-Latest` picks the highest retained `versionCode`.
>
> Extract the resolved variant's payload into `<Destination>\<versionCode>\<variant>\`, producing `mapping.txt` and `symbols/<abi>/`. Print the absolute path of the extracted `mapping.txt` as the last line, so it can be passed straight to a retrace tool or to `scripts/quality/assert-enum-persistence-contract.ps1 -Mapping`.
>
> A version that is retained but lacks the requested variant exits 1 and names the variants that release does hold.

**Why:**

Strategic §2 goal 1 requires decoding a stack trace from any released version regardless of how many builds followed, which is only true if the retrieval accepts the identifier the operator actually has in hand. Strategic §3.3 names S1674 as a second consumer of the mapping - its gate compares enum constant names against one - so printing the path in a directly consumable form serves both the crash-triage and the gate case.

**Verification:**

- Run with `-Latest`; exit code equals 0 and the extracted `mapping.txt` exists at the printed path.
- Run with `-VersionCode` naming a code absent from the archive; exit code equals 1.
- Run with both `-Latest` and `-VersionCode`; exit code equals 2.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Resolution + extraction done. -Latest exit 0, extracted mapping.txt 178901251 bytes and 12 symbol files, absolute path printed as the last line. Absent versionCode exit 1; -Latest plus -VersionCode together exit 2. Duplicate versionName refuses with exit 2 rather than picking one.

---

### Step 03.3 - Add the read-back verification mode

**Files:** `scripts/release/fetch-deobfuscation.ps1`
**Depends on:** Step 03.2

**Prompt for developer:**

> `-Verify` resolves the release the same way but extracts nothing. It opens the payload zip, streams the `mapping.txt` entry, recomputes its SHA-256 and compares it with the `mappingSha256` recorded in the manifest. A mismatch, an unreadable zip, or a manifest record with no matching payload file all exit 1 and say which of the three happened.
>
> Read the entry through the archive rather than trusting the file size, because a cloud folder that has not finished syncing typically presents a correctly sized placeholder.

**Why:**

Strategic §7 lists an unsynchronised or unavailable cloud folder as a medium-probability risk whose consequence is that retention "succeeded into the void", and mitigates it by requiring the pre-release check to read the stored artifact back rather than merely confirm a path exists. Phase 04 consumes this mode instead of reimplementing the read.

**Verification:**

- `Grep` - `SHA256` present in the file.
- Run with `-Verify -Latest`; exit code equals 0.
- Corrupt a copy of a payload zip in a scratch archive root and run `-Verify` against it; exit code equals 1 and the message names the failure kind.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Verify mode implemented: streams mapping.txt through the archive and recomputes SHA-256, never trusting file size. Good payload exit 0 with matching hash 7afc7e01..66d2; a payload corrupted at byte 1000 inflated without error and was caught as hash-mismatch, exit 1. Three failure kinds named separately: payload-has-no-mapping, payload-unreadable, hash-mismatch.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - not applicable, this phase touches no compiled source.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable, no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Invariant established: `-Verify` answers "is this release's payload really there and really readable" with an exit code, which is the whole contract Phase 04 needs.

---

## Rollback Plan

Delete `scripts/release/fetch-deobfuscation.ps1`. Retention keeps working; only retrieval reverts to opening the archive by hand.
