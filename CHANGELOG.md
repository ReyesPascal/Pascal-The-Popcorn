# Changelog

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
