<sub class="doc-stamp">26.11.20 16:26</sub>

# Pointer - `REMOTE-CONTROL`

| | |
| --- | --- |
| **Id** | `REMOTE-CONTROL` |
| **Version** | 0.6, draft. The phone implementation is built against 0.4. |
| **Home** | `remote-control/README.md` in the shared contracts catalog |
| **Role here** | target - the phone app answers commands from the account's other devices; the watch app is not a target |

## What this repository owes it as a target

- Declare `command-v1` at enrollment only together with `e2e-v1`, and send frames only when `welcome` lists `command` together with `sealed`.
- Keep the listening switch off by default, after install and after a reset, changed only by the person in the app's own settings (rule 8).
- Grant per controller and per risk class; a new controller gets `safe` only (rule 9).
- Consent per class, "ask" or "always"; a verb with on-device confirmation always asks; a prompt that cannot be shown is a refusal (rule 10).
- Keep a command log on the device, arguments hidden, nothing sent to the server (rule 11).
- Keep a replay ring on disk until `issuedAt + ttlSeconds + 300` seconds on the target's clock, answering a repeat from it, and refuse a request outside the time window (rule 12).
- Publish the remote verbs in APP-COMMANDS shape with a `remote` member at `docs/cli-commands.json`; no verb takes a path, file name, shell command, script or secret (rules 6 and 7).
- Keep the switch, the grants, the ring and the log out of Android backup, the settings export and import, and any server record.

## Where it lives here

- Domain: `app_v2/src/main/java/com/sza/fastmediasorter/domain/remotecontrol/`.
- Storage: `app_v2/src/main/java/com/sza/fastmediasorter/data/remotecontrol/`.
- Remote verb manifest: `docs/cli-commands.json`.

## Ticket

- S4701 - remote-control target on the phone.
