# Changelog

## 1.0.1 — 2026-11-02

### New
- **TV check on first launch.** A welcome screen, then **Scan this TV**: the app shows what your TV can play — screen (4K / 1080p), HDR (Dolby Vision, HDR10, HDR10+, HLG), video formats and sound — and asks if it looks right.
- **Fix it yourself.** If something is wrong, **Something's wrong** lets you switch 4K/1080p, each HDR type and Dolby Vision. Your changes are marked "set by you", Play uses them, and re-scans keep them. **Reset my changes** and **Scan again** are there too. Open it any time from **Settings → Playback → This TV**.
- **Preferred picture format** (Best for this TV, Dolby Vision, HDR10+, HDR10 — only what your TV supports). Play still walks Usenet Ultimate's list in its own order and plays the first source that suits your TV **and** matches your format; on a 4K TV only at the same resolution, so it never drops to 1080p just for a format ([details](docs/how-play-picks-a-source.md#3-what-play-starts)).

### Fixed
- **First Play no longer says "No streams found".** Pressing Play while the movie page was still fetching sources in the background made the app remember "no sources" for a few seconds; the first Play always failed and Retry worked.
- **4K TVs detected correctly.** Many 4K TVs report only a 1080p menu mode; the scan now reads the real panel size, so 4K sources are chosen on 4K TVs. Existing installs re-scan once after updating.
- **Empty Home:** the top bar has focus, so Left/Right reach Search and Settings straight away.

### Changed
- **A simpler, faster movie page.** It shows only the backdrop, title logo, genre/date/runtime/rating, a short description that always ends on a full sentence (never "…"), a big **▶ Play** button (focused when the page opens) and **Watched**. Shows keep their seasons and episodes.
- The page no longer loads ratings, budget, trailer, cast and photos, More Like This, collections, reviews, watchlist or streaming services — nothing is fetched for things you don't see. The backdrop uses a smaller image.
- The source search for Play starts as soon as the page opens.

### Removed
- Movie page: **Sources**, **Trailer**, **View Collection** and **Watchlist** buttons; cast, More Like This, collection and reviews sections; IMDb rating and budget. To pick a source yourself, turn on **Settings → Playback → Show all sources**.

### Known issues
- *The Great British Bake Off* episodes from 2017 on don't play yet (the show's listing for those years has no IMDb number). Planned for 1.0.2.

## 1.0 — 2026-11-02

First release of Pascal The Popcorn.

### New
- **Play starts the best source for your TV** straight away, following Usenet Ultimate's order ([details](docs/how-play-picks-a-source.md)).
- **One-time TV scan** of chip, video formats, screen, HDR, Dolby Vision and sound; shown in Settings → Playback → This TV, with Re-scan.
- **Automatic retry** of the next suitable source, up to 3 in total.
- **When nothing suits the TV**, picks by resolution: the TV's own first, then lower, then higher.
- **Settings → Playback → Show all sources** to get the full source list instead.
- New name, icon, TV banner and loading screen.

### Changed
- Waits **at least 30 seconds** for a source to start (first plays from Usenet need it).
- Opens straight to Home — **no profiles**, no "Who's watching?".
- **Top bar:** Search and Home only, plus the Settings gear. Home shows only **Continue Watching**.
- **Defaults:** English subtitles on, original-language audio, next-episode autoplay off.

### Removed
- Cloud accounts and sync, plugins, Discord status, sports, profiles.
- Usage analytics, crash reporting and self-update.

### Requirements
- Android TV / Google TV, 32-bit or 64-bit ARM (one APK).
- Tailscale connected on the TV so Usenet Ultimate is reachable.
