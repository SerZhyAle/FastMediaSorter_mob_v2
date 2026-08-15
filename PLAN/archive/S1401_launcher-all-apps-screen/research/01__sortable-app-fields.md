# 01 - Sortable fields available for an installed app

Research for S1401 §6 item 1. Performed 2026-08-05 against the current working tree and the platform
APIs already used by the project.

## Question

Which characteristics of an installed launchable app can the all-apps screen sort by, what does each
cost, and which of them need a permission the owner would have to grant by hand?

## Current state in the tree

`QueryLaunchableAppsUseCase` (shared layer, used by both the launcher Start menu and the app-launch
panel picker) resolves launchable activities and keeps exactly three fields: package name, label,
icon. Nothing else is read, nothing is cached, and the icon of every app is decoded on every call.
That is both the source of the multi-second wait and the reason no other sort order is possible today.

`AppShortcutDataSource` is a separate seam onto the published shortcuts of an app and is unrelated to
sorting - it only matters for the long-press menu.

The launcher already keeps its own launch journal (`LauncherJournalRepository`,
`QueryRecentLauncherCommandsUseCase`). It records commands run through our launcher, needs no
permission, and is the only usage signal available for free.

## Findings

Free - no permission, cheap enough to read for every app during a cache rebuild:

- **Label** - the display name. Locale-dependent, so a system language change invalidates it.
- **Package name** - stable identity; also the natural cache key.
- **First install time** - supports "newest first".
- **Last update time** - supports "recently updated first".
- **Category** - the declared app category (Games, Audio, Social, ..). Declared by the publisher and
  frequently left undeclared, so a real device will have an "uncategorised" bucket of unknown size.
  Measuring that share is S1401 §6 item 4.
- **System vs user app flag** - cheap, but it is a filter axis rather than a sort axis; also decides
  whether the "Uninstall" entry of the long-press menu is shown at all.

Free but partial:

- **Launch frequency and recency** from our own launcher journal. No permission. Only counts launches
  made through our launcher, so it is empty for a freshly installed launcher and blind to launches
  made from elsewhere. Needs a fallback ordering while empty.

Permission-gated - rejected for this ticket:

- **App size** - requires the special "Usage access" permission that the user grants by hand in
  Android settings, and computing it across every installed app is slow.
- **System-wide usage statistics** (true launch counts and last-used timestamps across all apps) -
  same special permission.

## Decision taken (owner, 2026-08-05)

Ship: name (always), install date, update date, launch frequency/recency from our own journal,
category. Do not ship: app size, system-wide usage statistics. Recorded in S1401 §2 Non-goals and
ADR-3.
