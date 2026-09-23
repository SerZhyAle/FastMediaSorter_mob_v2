# Pointer - `FDSEC-FORMAT` and `FDSEC-BEHAVIOUR`

| | |
| --- | --- |
| **Id** | `FDSEC-FORMAT`, `FDSEC-BEHAVIOUR` |
| **Version** | 1.0 and 1.0, active; wire carrier: format version byte at head offset 16 (value 1), suite 1. Owner: FileDO |
| **Home** | `secure-container/README.md` in the shared contracts catalog |
| **Role here** | a port - writes and reads `.fd-sec` containers on the phone and the watch |

## What this repository must do to stay conformant

- Reproduce the catalog's conformance vectors byte for byte; they decide correctness, not the prose.
  The copies under `app_v2/src/test/resources/fdsec/` are those vectors and must stay identical to them.
- No dispatch by looking: version and suite are read from the declared field (rule 1).
- Keep the three outcome classes distinct and never report one as another (rule 2).
- Never delete, move or overwrite an original before its container has been reopened and read back to the
  sealed digest; write to a temporary name and rename into place (rules 3, 4).
- Nothing that could be mistaken for ransomware - one file at a time, no bulk or scheduled packing
  (rule 6).
- Keep the credential and the sealed true name out of every log; never describe an empty credential as
  protection and never launch an executable recovered from a container (rules 7, 8).

## Where it lives here

- `app_v2/.../data/security/fdsec/` - format, key schedule, container, outcomes, XChaCha20-Poly1305.
- `wear/.../domain/files/WearFdSecUseCase.kt` and `wear/.../ui/fdsec/`.
- Tests: `app_v2/src/test/java/.../data/security/fdsec/` (`FdSecVectorsTest.kt` runs the vectors).
