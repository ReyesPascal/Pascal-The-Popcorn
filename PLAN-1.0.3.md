# PLAN 1.0.3 — Pascal The Popcorn (backlog)

Created 2026-11-07 from the 1.0.2 / 1.0.2.1 testing notes (Brain: `ptp-wt/STATUS.md`).
Status: **backlog only — nothing started.** Owner decides order. Redesign items must be planned with the owner before any code.

## Bugs / fixes
1. **"No sources" after UU unreachable.** StreamRepository caches EMPTY results (`STREAM_RESULT_EMPTY_CACHE_TTL_MS`, 15 s, also persisted), so a quick retry repeats "No sources". Fix: don't cache empty when UU is unreachable/errored, auto-retry once, show "Can't reach Usenet Ultimate".
2. **Stall after seek.** Confirmed 2026-11-06 20:25 (Primetime 4K DV, UU): skip +30 s → app logged nothing after, no new UU stream activity. Also decoder stall / black-video recovery never reconnects. Fix + add player state/seek/buffer diag events so the logger can see why.
3. **Aspect-button mash pauses video.** PlayerScreen auto-hide timer is keyed only on (showControls, isPlaying, seek phase); aspect/rewind/forward presses don't reset it → controls hide after 5 s, next OK re-shows with focus on Play, further OKs toggle pause. Covered by item 5.
4. **Bundled add-ons no longer auto-install.** Restore the `seedBundledAddons()` call; add AIOMetadata.

## Redesigns (plan with owner first)
5. **Player controls rewrite.** One interaction-reset timer for every key/click, hidden-controls D-pad behaviour, focus restore, QuickSkip on hidden controls. Reference: **Infuse** player, incl. an advanced info overlay (bitrate, connection/network, stream details).
6. **Search revamp.** "prime time" must find "Primetime" (space/no-space + fuzzy matching). Search keyboard (in-app grid vs Gboard) + clear behaviour — owner researching keyboard options first; ask before planning.
7. **Scrub thumbnails.** Not showing on Hisense (4K DV Primetime); memory gate passes, cause unverified (2nd HW decoder for 4K DV?). Decide fix vs drop.

## Small UI / cleanup
8. **Loading skeletons show old layout.** SkeletonDetailsPage (`ui/components/SkeletonLoader.kt`, used by DetailsScreen + MediaCard) should match the new stripped pages (Play/Watched/Watchlist, no cast/extras), or just show the backdrop.
9. **Focused season tab** white box + underline clashes with the episode row — less glaring, more gap.
10. **Watchlist label** shows a double symbol.
11. **13 known failing tests:** DetailsWatchlistToggleTest (12) + DetailsStreamCompletionTest (1) — Log not mocked / outdated.
12. **PRIVACY / README** still contain stale upstream text.

## Separate (not app code)
- UU: "Auto-queue failed: fetch failed" on pre-queue of top 3 (UU → ::1:1337 closed) — Usenet Ultimate issue.
