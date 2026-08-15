# FastMediaSorter - UI Communication Policy

**Canonical source:** This document. Mirrors: `COMMUNICATION_POLICY_RU.md`, `COMMUNICATION_POLICY_UK.md`.
**Origin:** S0118 (friendly-ui-copy-revision). Update this document when new message formulas or feedback channels are added.
**Scope:** All user-visible strings in all six flavors (`standard`, `noLegal`, `lite`, `photos`, `legacy`, `vr`), EN/RU/UK. Excludes: legal texts, Terms of Service, machine-readable technical artifacts (manifests, metadata).

---

## 1. Voice

- Tone: friendly, clear, brief. Light irony is allowed only as self-deprecating app humor, never aimed at the user.
- No bureaucratic constructions: avoid "operation completed successfully", "please be advised", "an error has occurred".
- No jargon without explanation.
- No emoji images; rare text smileys like "))" or ":-)" are allowed only where they genuinely fit and do not obscure meaning.
- When ambiguous, prioritize clarity over wit.
- When a user action is required, state it directly in the message.

---

## 2. Message Formulas by Type

### 2.1 Toast (short notification)

- One short thought, readable in a single glance.
- No next steps, no extra context.
- Examples:
  - ✓ "Saved."
  - ✓ "Connection lost."
  - ✗ "The operation has been completed successfully. Your changes have been saved."

### 2.2 Error (inline or snackbar)

- Human explanation of what happened + one actionable next step if available.
- Do not expose raw exception text as the primary message.
- Technical details (stack trace, error codes) go to a secondary "Details" section, not the headline.
- Examples:
  - ✓ "Couldn't connect - check the server address and try again."
  - ✗ "java.net.SocketTimeoutException: timeout"

### 2.3 Error dialog (expanded)

- May be longer if it helps the user understand what happened and what to try.
- Structure: [What happened] → [What to try] → [optional: one next step].
- Bare error statements without a useful path forward are not acceptable.

### 2.4 Empty state

- Explain why there is no content + natural invitation to act.
- Do not leave empty states as dead ends.
- Examples:
  - ✓ "No files here yet. Add a resource - a device folder or a network source - to start browsing."
  - ✗ "No items found."

### 2.5 Progress / loading

- Short, calm status. No bureaucratic "Processing.." if a more specific phrase fits.
- Examples:
  - ✓ "Loading files.."
  - ✓ "Scanning.."
  - ✗ "Please wait while the operation is being performed."

### 2.6 Success confirmation

- Brief confirmation of result, without heavy formality.
- Examples:
  - ✓ "Done." / "Moved." / "Deleted."
  - ✗ "The operation has been completed successfully."

### 2.7 Destructive-action confirmation dialog

- Direct, polite, no jokes if there is a risk of data loss.
- State what will happen, not just "Are you sure?".
- Examples:
  - ✓ "This will remove all saved connections. You can add them again later."
  - ✗ "Are you sure you want to delete?"

### 2.8 Network / resource / access error

- Friendly explanation + a corrective step the user can actually take.
- Examples:
  - ✓ "Can't reach the server - check your connection or try again later."
  - ✗ "SMB STATUS_BAD_NETWORK_NAME"

---

## 3. Helpful Next Step (dead-end rule)

Show at most one contextual next step in dead-end or problem states. Never in short toasts. Never as a required action blocking the main flow.

| Situation | Preferred channel |
|---|---|
| Setup / access / configuration error the user can fix | Documentation (app website) |
| Repeated failure or suspected app defect | Bug report: `sza@ukr.net` |
| UX dead-end, help surface, or About screen | Improvement suggestion / review: Google Play |

Rules:
- Pick the most relevant single channel - do not show all three at once.
- The next-step CTA is secondary to the main message and never replaces the corrective advice.
- Do not show feedback/help CTAs after every success action - only in genuine dead ends and problem states.

---

## 4. Feedback Channels

| Channel | Purpose |
|---|---|
| App website / documentation | How-to guidance, setup instructions |
| `sza@ukr.net` | Bug reports, reproducible problems |
| Google Play reviews | Product improvement suggestions, general feedback |

---

## 5. Localization Rules

- Every new or updated user-visible string must be present in EN, RU, and UK in one commit.
- Verify parity with `scripts/check_strings_localized.ps1` - exit code 1 is a blocker.
- EN is the source locale; RU and UK must match in structure and intent, adapted naturally (not word-for-word translated).
- `..` (two dots) as ellipsis in all locales; never `…` (Unicode) or `...`.
- A plain hyphen `-` in all locales; never the long dashes `–` (en), `—` (em) or `―` (horizontal bar).

---

## 6. Tone Checklist (pre-integration)

Before any batch of new or updated strings is merged:

- [ ] No raw exception text or error codes as the primary user-facing message.
- [ ] No "Are you sure?" without stating what will happen.
- [ ] No "operation completed successfully" phrasing.
- [ ] Every error message has a next step or at least a human explanation.
- [ ] Every empty state has an invitation to act.
- [ ] Strings fit on a smartphone screen without truncation (spot-check at 360 dp width).
- [ ] EN/RU/UK parity confirmed (`check_strings_localized.ps1` exit 0).
- [ ] No emoji images; text smileys used sparingly and appropriately.
- [ ] Legal texts and machine-readable artifacts are untouched.

---

## 7. Glossary (canonical terms)

- The Browse window (the screen that lists files and folders) is called the **file browser**.
- Qualified variants are allowed when the media type is relevant: "video file browser", "image browser", "document browser".
- The word "explorer" is forbidden for this window in user-facing text.
- Exception: strings about a **web browser** (Chrome Custom Tabs, Google sign-in) keep "browser" in the web sense and are out of scope of this rule.

### 7.1 Resource vs Folder (S0799)

- **Resource** is an internal FastMediaSorter entity: a registered address (local, network, or cloud) the user added by hand or imported. A resource can be a browse source, a Quick Sort destination, a media library, or a virtual aggregate (All Music, All Video, favorites, streams). A resource points to one folder or a branch of folders. Use "resource" whenever the text is about the app's registered entry: add / edit / copy / remove / select / refresh a resource, its name / type / path / PIN, the resource list.
- **Folder** (directory) is a filesystem directory that exists independently of the app: a local Android folder, or a folder on a remote SMB / (S)FTP / cloud server. Use "folder" only for genuine directory work: the system or manual folder picker, the folder a resource points to, subfolder scanning, creating a folder on disk, cloud-provider folder IDs, current / parent-directory navigation.
- Never call a resource a "folder": e.g. the "Add resource" action must not open a dialog titled "Add folder".
- Two distinct icons back this split: `ic_resource` (a stacked collection) marks the resource concept; `ic_folder` marks a genuine folder. Never swap them (S0842).
