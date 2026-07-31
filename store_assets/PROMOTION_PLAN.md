# Free Promotion Plan

Where the prepared posts go, in what order, and what each community will not tolerate. Every channel
here is free. Ticket: **S1268**. Post texts live beside this file as `post_*.md`.

---

## 1. Before posting anything

- Search registration first (`docs/SEARCH_ENGINE_REGISTRATION.md`). Traffic that arrives before the
  site is indexable is traffic spent once.
- Check the claims in the post you are about to publish against `app_v2/build.gradle.kts`. Flavor
  capability claims drift: two posts stated "Legacy - full features minus cloud" when Legacy has
  cloud, network shares and documents, and differs from Standard only in `SUPPORT_VR_PLAYER` and
  `minSdk`. Both were corrected on 2026-07-28; the same trap applies to every future edit.
- Never state a rating, download count or award the app does not have.

---

## 2. Order of publication

Spread these out. Posting everywhere in one day looks like a campaign and gets treated like one.

| Order | Channel | File | Why this slot |
|---|---|---|---|
| 1 | XDA Forums | `post.md` | Already published - the thread is the reference link others can cite |
| 2 | r/androidapps | `post_reddit_androidapps.md` | The flagship Android audience; the broadest single post |
| 3 | r/selfhosted | `post_reddit_selfhosted.md` | NAS and SMB angle lands hardest here |
| 4 | r/datahoarder | `post_reddit_datahoarder.md` | Bulk transfer, duplicate finder, scheduled operations |
| 5 | r/homelab | `post_reddit_homelab.md` | Overlaps r/selfhosted - leave at least a week between them |
| 6 | r/unraid | `post_reddit_unraid.md` | Narrow but highly relevant |
| 7 | Habr | `post_habr.md` | Long-form, Russian-speaking; the biggest single writing investment |
| 8 | 4PDA | `post_4pda.md` | Russian-speaking Android community, expects an APK link |
| 9 | Hacker News | `post_hackernews.md` | Show HN - one shot, timing matters more than wording |
| 10 | Product Hunt | `post_producthunt.md` | One launch only, so do it after the listing and site are polished |
| 11 | AlternativeTo | `post_alternativeto.md` | Evergreen catalogue entry, keeps paying off quietly |
| 12 | IndieHackers | `post_indiehackers.md` | Build-story framing rather than feature list |

---

## 3. Rules per community, in one line each

- **Reddit.** Every listed subreddit requires a developer disclosure - the `[DEV]` tag is not
  decoration. Read each subreddit's self-promotion rule before posting; several allow one
  self-promotional post per month and count links across the account. Answer every comment; a dead
  thread reads worse than no thread.
- **Hacker News.** Title format is `Show HN: <what it is>`. No marketing adjectives, no emoji. Post
  on a weekday morning US time. Expect blunt technical criticism and answer it plainly.
- **Product Hunt.** A launch is once per product. Do it when the store listing, screenshots and site
  are all current - which, as of 2026-07-28, they are.
- **Habr.** Long-form only; a feature list gets downvoted. The prepared text leads with the
  engineering problem, which is the right shape.
- **4PDA.** Expects a direct APK link alongside the Play link, and an active thread afterwards.
- **AlternativeTo.** Add the app as an alternative to the tools it actually replaces - Solid
  Explorer, VLC, Moon+ Reader, AndFTP. Do not list unrelated popular apps to farm visibility.

---

## 4. What not to do

- No vote manipulation, no asking friends to upvote, no multiple accounts. Reddit and Hacker News
  both detect and punish this, and it is unrecoverable.
- No posting the same text to several subreddits on the same day.
- No paid placements, paid reviews, or link directories - this project ships without paid services
  by policy, and directory links actively harm search ranking.
- No fabricated user quotes or invented ratings.

---

## 5. After each post

- Add the resulting URL to the top of the corresponding `post_*.md`, the way `post.md` records its
  XDA thread. That is how the next person knows what has already been published.
- Feed the new inbound link to the search engines: `pwsh -NoProfile -File scripts/site/ping-indexnow.ps1`
  only covers our own pages, so nothing to do for external links - but do check Search Console
  afterwards to see which queries start bringing traffic.
