---
name: feedback-timestamp-in-chat
description: User wants every chat message prefixed with a wall-clock timestamp [HH:MM:SS] to track where time is spent
metadata:
  type: feedback
---

Always prefix every chat response with a wall-clock timestamp in `[HH:MM:SS]` format so the user can see how long each step takes.

**Why:** User wants to understand where time is being spent during multi-step tasks.

**How to apply:** Add the timestamp at the very start of each chat message, before any other content. Use current local time at the moment of composing the response.
