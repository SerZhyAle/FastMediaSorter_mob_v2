# Pointer - `INSTALL-TRUST`

| | |
| --- | --- |
| **Id** | `INSTALL-TRUST` |
| **Version** | 1.0, active. Owner: shared (amendments through the domain page) |
| **Home** | `install-trust/README.md` in the shared contracts catalog |
| **Role here** | producer - the page a user reads after Android warns about a sideloaded APK |

## What this repository must do to stay conformant

- Four sections in order: what the warning is, why it appears, exactly what to tap, what the app never
  does (rule 1); quote the dialogs as Android words them (rule 2).
- State the real reason, including the cost, without implying the user should have been spared the
  warning (rule 3).
- Never tell a user to weaken a protection (rule 4).
- "What the app never does" says what the privacy page and the permission list say (rule 6).
- Link the page from every surface that hands out an APK (rule 7); when the builds become recognized,
  update the page rather than delete it (rule 8).

## Where it lives here

- `docs/INSTALL_TRUST.md`, `docs/INSTALL_TRUST-ru.md`, `docs/INSTALL_TRUST-uk.md`, registered in
  `docs/DOCUMENT_REGISTRY.jsonl`.
- Linked from `docs/DOWNLOADS*.md`, `docs/README*.md`, `index*.html`, `nolegal*.html` and `README.md`.
- Every hand-out surface, the four rule-1 headings per locale and the "never does" facts are declared
  in `scripts/quality/install-trust.psd1`; a new page handing out the APK gets a row there.
- Rules 1, 6 and 7 are held by `scripts/quality/assert-install-trust.ps1`, run at release scope: it
  fails on a section missing or out of order, a surface without its locale link, an undeclared page
  carrying a download link, a claim the same-locale privacy policy does not make, and an advertising
  id or an ad/analytics SDK in the phone module. Rules 2-4 stay a reading: a pattern cannot tell the
  advice the page forbids from the sentence saying it never gives it.

## Rule 8 - when the builds become recognized

- The page stays in all three locales; deleting it fails the gate.
- "Why it appears" is rewritten to say the warning was for builds before recognition.
- "When the warning goes away" is rewritten to say it has gone for current builds and still meets
  older ones.
- The sections, the surfaces and the links are unchanged.
