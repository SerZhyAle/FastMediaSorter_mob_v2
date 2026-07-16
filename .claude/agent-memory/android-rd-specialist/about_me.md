---
name: about-me
description: User profile - Serhii, solo owner of FastMediaSorter; data engineer, no Android/Kotlin background (code is vibecoded), SQL/VB.NET, weak English; work style, goals, external systems
metadata:
  type: user
---

# About Me
Сергей Жигуненко / Serhii Zhyhunenko
Сейчас дата-инженер в Мальте в айгейминге. Раньше был 1С-ником в Одессе, украинец. женат, дети. Английский довольно слабый, технический, но стараюсь изучать.

## Role & experience
- Solo owner and sole developer of FastMediaSorter (Android, Kotlin). Wears every hat: architect, implementer, release manager, QA.
- Many tickets touch the same file across time -> working tree is the only source of truth, not git history.
- Have no experience in android java (Kotlin). All code in this project is vibecoded.
- Background outside Android: SQL, VB.NET (data engineer in iGaming, ex-1C developer).

## How to help me (given the above)
- I cannot read/write Kotlin fluently - I drive the project by intent, not by code. Explain Android/Kotlin concepts in plain terms, and when useful map them to SQL / VB.NET / data-engineering analogues.
- Don't assume I know a Kotlin/Android idiom; name the concept before using it. Avoid dumping raw code at me as an "explanation" - say what it does and why.
- English is weak (technical reading OK, improving). Keep chat in RU; keep any English I must read simple and short.
- Because the code is vibecoded, you are the one who must hold architecture discipline (layers, flavor isolation, catalog/spec lifecycle) - I rely on you to catch violations, not the other way around.
- **I think in Windows desktop metaphors, not Android ones.** When I describe UI intent, it comes out as desktop/taskbar/Start menu/tray/shortcut-with-arguments/gadget (native model from my 1C/VB.NET past). S0404 turned on exactly this: "весь интерфейс больше в логике Windows" reframed the whole epic. Mirror those analogies back to me - they carry more signal than Android idioms - but check each one against what Android actually allows: my model breaks where Android has no equivalent (no enumerable "running windows" without special access, no giving away the system status bar without device owner). Name the mismatch explicitly instead of quietly designing around it.

## Work style with me
- Chat in RU; code/docs/logs/commits in EN. Tone: dry, concise. No trailing "what I did" summaries - the diff speaks.
- Style rules are strict: `..` not `...`, plain hyphen never em/en-dash, Ё/ё mandatory in chat/UI/docs/Approved specs.
- Prefer autonomy: run searches, builds, catalog/spec queries, device chores without asking. Flag blockers up front. Background long jobs rather than foreground-waiting. 
- Don't ask owner questions that the architecture/flavor hierarchy already answers; research conventions and recommend instead.
- Surface UI placement/visibility/fallback ambiguity before implementing - don't guess.
- Spec lifecycle and catalog are sacred: always go through the CLI, never hand-edit JSONL.

## Goals & priorities
- Primary focus right now: stability + new features
- Priority #1 on conflict: quality.  Known hard rule: never ship a release that regresses market coverage (countries, age ratings, minSdk/ABI/uses-feature/flavor reach).

## Contacts & external systems
- Email: serzhyale@gmail.com
- Google Play Console: read track/bundle state via androidpublisher API (temp/play_status.py); review verdicts need a screenshot from me.
- Azure: MSAL app registration - each signing keystore needs its BrowserTabActivity hash declared in manifest + Azure.
