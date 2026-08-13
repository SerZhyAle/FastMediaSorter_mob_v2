# S1596 research 01 - what the plan-file edits actually are

**Resolves:** strategic §6 item 1
**Performed:** 2026-08-12
**Method:** `temp/S1596/classify_index_edits.py` over `C:/Users/serzh/.claude/projects/p--ANDROID-FastMediaSorter-mob-v2`, window from 2026-08-05, dedup by `(requestId, tool_use.id)`. Every `Edit` tool call whose `file_path` is under `PLAN/` is classified by matching `old_string + new_string` against four bookkeeping shapes, everything else counted as authoring.

## Result

Total `Edit` tool calls in window: **7 090**. Of those, **2 358** land in `PLAN/`.

### INDEX.md - 495 edits

| Kind | Count | Share |
| --- | ---: | ---: |
| `index-table-steps-cell` (`Steps` cell `6/6`) | 231 | 46.7% |
| `index-header-counter` (`**Phases:**` / `**Last updated:**`) | 164 | 33.1% |
| `content-authoring` | 70 | 14.1% |
| `gfm-checkbox` (`- [ ]` blockers / gate) | 30 | 6.1% |

**Bookkeeping = 425 of 495 = 85.9%.** Authoring is 14%.

### PHASE_*.md - 1 863 edits

| Kind | Count | Share |
| --- | ---: | ---: |
| `content-authoring` | 851 | 45.7% |
| `step-status-marker` (`**Status:** \`[x]\` done`) | 791 | 42.5% |
| `gfm-checkbox` (prerequisites / done criteria) | 206 | 11.1% |
| `index-table-steps-cell` | 15 | 0.8% |

**Bookkeeping = 1 012 of 1 863 = 54.3%.**

### Combined

**1 437 of 2 358 plan-file edits in one week are state bookkeeping** - about 61%. Each is one turn.

## Consequences for the plan

1. The `231` figure quoted in strategic §0 is only the INDEX `Steps` cell class. The real addressable mass is **six times larger** and split across two files and four distinct shapes.
2. The batch ticker cannot serve only the per-step inline marker. The four shapes it must write, in descending weight: `**Status:** \`[x]\` done` in the phase file (791), INDEX `Steps` cell (231), GFM `- [x]` in phase files (206), INDEX `**Phases:**` / `**Last updated:**` header (164), GFM `- [x]` in INDEX (30).
3. Strategic §3.2 "Две формы отметки" is therefore answered as **both forms**, not one: the inline step marker and the GFM checkbox each carry an order of magnitude of edits.
4. `**Last updated:**` sits inside the 164-count header class, confirming it is routinely hand-maintained today - a batch writer that drops it changes behaviour the drift gate depends on.

## Top INDEX.md files in window

```
31  PLAN/S1433_network-monitor/INDEX.md
22  PLAN/S1420_locale-bulk-translation-remaining-tranches/INDEX.md
19  PLAN/S1428_launcher-shortcut-groups/INDEX.md
19  PLAN/S1359_minigame-restart-level-command/INDEX.md
18  PLAN/S1179_launcher-gps-sensor-widgets/INDEX.md
```

## Caveat

Classification is by regex over the edit payload, so an authoring edit that happens to contain a `N/M` cell is counted as bookkeeping. The bias is small (the `content-authoring` samples inspected are Blockers Log and Change Log prose) and runs against the finding, not for it.
