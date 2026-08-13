# S0420 - Maestro flows for the repeatable device-test sweep core

**Status:** Archived
**Priority:** 40

## 0. Inbox (raw capture)

Origin: research request (2026-06-14) - "/spec-sweep device testing via mobile-mcp is slow and burns a lot of tokens, mostly when the agent tries to aim for a tap. Research how to improve."

### Diagnosis (from research)

Token cost in `/spec-test-device` (looped by `/spec-sweep`) is dominated by screenshots, not coordinate aiming:

- `mobile_take_screenshot` returns the image **into the LLM context**; each inline image (~1-1.6k tokens, can exceed 25k on large screens) is **re-sent on every subsequent turn**. Across a multi-step scenario this compounds.
- The old `/spec-test-device` step 6 took a screenshot **before and after every step** plus a rule "never click without a screenshot first" -> 2-3N inline images per N-step scenario.
- Coordinates were already coming from the text accessibility tree (`mobile_list_elements_on_screen`), so aiming itself was cheap; the screenshots were the leak.
- mobile-mcp exposes no flag to disable/resize screenshots; the only lever is to not call `take_screenshot`.

Evidence:
- mobile_take_screenshot tool description: "if you need to press an element that is available through view hierarchy then you must list elements on screen instead".
- mobile_save_screenshot writes to a file (off-context); mobile_take_screenshot returns the image (in-context).
- Claude Code issue #27869 - screenshots accumulate in context, re-sent each turn (18 shots -> 17% of Max plan in 5 calls). Issue #9152 - image responses > 25000 tokens.
- mobile-mcp / Mobilewright (mobile-next): accessibility tree is deterministic, token-efficient, no vision model.

### Tier 0 - DONE in this same session (not part of this ticket)

Rewrote `/spec-test-device` (both `.claude/commands/spec-test-device.md` and `.github/prompts/spec-test-device.prompt.md`):
- `mobile_list_elements_on_screen` is the single source of truth for targeting AND verification.
- evidence via `mobile_save_screenshot` (file, zero context) instead of `take_screenshot`.
- `mobile_take_screenshot` reserved for fallback only (empty/non-semantic a11y tree, purely visual assertion, FAIL diagnosis).
- screen-recording suggested over screenshot bursts for many visual states.

This ticket covers only the Tier 2 structural option below.

### Tier 2 proposal - Maestro for the repeatable regression core (this ticket)

For the stable, repeatable subset of sweep tickets, drive per-tap automation out of the LLM loop entirely:

- Author a Maestro YAML flow once per ticket (`tapOn: { id/text }`, `assertVisible`, `inputText`, ..). Element matching by id/text is more reliable than coordinates.
- Run via Maestro CLI; the LLM only reads the compact pass/fail output. No LLM round-trip per tap -> ~0 tokens per run, native speed.
- mobile-mcp stays for exploratory / unknown screens and one-off tickets; Maestro handles the regression core.

Reference URLs:
- https://github.com/mobile-next/mobile-mcp
- https://github.com/mobile-next/mobilewright
- https://maestro.dev/blog/maestro-mcp-an-introduction
- https://deepwiki.com/mobile-dev-inc/Maestro/6.5-ai-features-and-mcp-server
- https://github.com/anthropics/claude-code/issues/27869
- https://github.com/anthropics/claude-code/issues/9152

## 1. Problem

Repeatable device-test sweeps (`/spec-sweep` -> `/spec-test-device`) keep an LLM in the per-tap loop. Even after Tier 0 screenshot discipline, every tap/assert is still an LLM turn against the mobile-mcp server: slower than native automation and non-zero tokens per step. For tickets re-tested often, this recurring cost has no payoff over a scripted flow.

## 2. Goal

Adopt Maestro YAML flows as the execution engine for the repeatable regression subset of the device-test sweep, keeping mobile-mcp for exploratory and one-off runs. Prove the engine on a single real flow (pilot) before wiring it into the sweep classifier.

## 3. Scope

- **In:** a Maestro runner script with stable exit codes and off-context evidence; a flow-file home keyed by ticket id; an authoring template and one real example flow; install/version documentation; a deferred design for `/spec-sweep` auto-routing.
- **Out:** replacing mobile-mcp wholesale; VR / 3D / OpenXR / Quest-only tickets; release signing; the Maestro MCP server (hand-authored YAML only); authoring flows for the whole regression backlog (Phase 2).

### 3.3 Owner inputs (Approval gate)

Owner decisions (2026-06-15) closing the §0 open questions:

- **Increment shape - pilot first.** Build the engine + one example flow + docs now; defer sweep auto-routing and bulk flow authoring to Phase 2, gated on the pilot proving out.
- **Flow-file location - central, keyed by ticket id:** `scripts/devtest/maestro/<Sxxxx>.yaml`. A deterministic id-keyed path is what the future sweep classifier matches on; `scripts/devtest/` is already the home of `device-ready.ps1`.
- **Sweep integration - auto-route by flow presence.** The existing `/spec-sweep` classification step gains a `maestro` class: if `scripts/devtest/maestro/<Sxxxx>.yaml` exists, the ticket runs through the Maestro runner; otherwise the current mobile-mcp subagent path. No new top-level skill. (Phase 2.)
- **Authoring - hand-written YAML.** `tapOn:{id/text}`, `assertVisible`, `inputText`. No extra MCP dependency, deterministic, reviewable in git. Element id/text is sourced from `mobile_list_elements_on_screen` during first authoring.
- **Install / version - documented pin, local-only.** Maestro CLI pinned to a known version, documented in `scripts/devtest/maestro/README.md`. Local developer/emulator use; no CI wiring in this ticket.
- **Related tickets:** S0307 (the `/spec-sweep` strategic spec this engine plugs into); S0398 (welcome onboarding ticket used as the pilot example flow).

## 4. Plan

### Phase 1 - Maestro engine pilot (this increment)

- `scripts/devtest/maestro-run.ps1` thin runner: discover the Maestro binary, run a flow against the selected device, write full output to `temp/<id>_maestro_<TS>.log` (off-context), emit a compact verdict line (or `-Json`), stable exit codes mirroring `device-ready.ps1`.
- `scripts/devtest/maestro/` home: `_template.yaml` (annotated authoring convention) + one real example flow against a deterministic existing screen.
- `scripts/devtest/maestro/README.md`: install + pinned version + Java requirement + usage + flow-authoring convention + the id-keyed naming rule.

### Phase 2 - /spec-sweep auto-routing (deferred, separate increment)

- `/spec-sweep` §4 classification gains class `maestro`: presence of `scripts/devtest/maestro/<Sxxxx>.yaml` routes the ticket to `maestro-run.ps1` in place of the per-ticket mobile-mcp subagent; absence keeps the current path.
- Runner output feeds the ticket's `## Last Audit` Manual block + logcat harvest the same way `/spec-test-device` does, so `/spec-check` finalizes identically.
- Author flows for the proven-repeatable subset only (regression core), not one-shot tickets.
- Gate: adopt only after Phase 1 is verified on a real device (cost/benefit threshold met).

## 5. Verification

- **Static (Phase 1, done):** `maestro-run.ps1` parses; it exits `1` on a missing flow file and `2` on an absent Maestro binary cleanly (verified); the example flow and template are tab-free, single-separator YAML.
- **Device proof (owner gate before Phase 2):** install the pinned Maestro CLI, boot an emulator, run `pwsh -NoProfile -File scripts/devtest/maestro-run.ps1 -Flow scripts/devtest/maestro/S0398.yaml`; the flow runs green and the full log lands under `temp/`. This confirms the engine end-to-end; only then start Phase 2.

## 6. Notes

- Tier 0 (screenshot discipline) already landed and is independent of this ticket; do not re-do it here.
- No app `.kt` flow changes - this is dev-test tooling. No `Timber.d("S0420:` debug tags apply.
