# HANDOFF — Pascal The Popcorn

Updated 2026-11-02 ~23:59 CDT (Claude Code). Summary copy for this repo. The **full handoff** lives with the app source as `HANDOFF.md` (plus `PLAN-1.0.1.md` with the 1.0.2 list).

> **Source:** a snapshot of the working code is on this repo's **`source`** branch (one commit, no old history). Day-to-day development stays in the app source folder on Brain, whose own git remote is the original public project and is never pushed. The TMDB key lives in a git-ignored `secrets.properties`.

## Current state

| | |
|---|---|
| App | **Pascal-The-Popcorn 1.0.1**: `versionName 1.0.1`, `versionCode 323`. Internal package ID unchanged. |
| Source | `source` branch of this repo (snapshot of branch `personal-tv-aware` @ handoff commit `b939003` on Brain) |
| APK | Release **v1.0.1** in this repo (v1.0 kept), plus a private copy on Brain: `~/.local/state/new-tv/ptp-builds/Pascal-The-Popcorn-1.0.1.apk`. 129,136,754 B, sha256 `7b8435052bd4f0fce8cc966deb70b0b690826da9c77d4e7c8fb0c837e65969f7` |
| ABIs | arm64-v8a + armeabi-v7a (one universal APK) |
| Signer | Brain debug key, cert SHA-256 `ee3441c2…bfae8` (upgrades 1.0 in place) |
| Installed on | **Hisense 65U6H** (fresh install 2026-11-02 23:10, speed-compiled). Not on the TCL. |
| Tested on the Hisense | TV check says 4K, Dolby Vision P4/5/8/9/10, HDR10/HDR10+/HLG. First Play works first time. Movie page drawn in 0.23 s; Play→first frame 14.7 s (11 s of it Usenet Ultimate's search). |

## What 1.0.1 adds
See [CHANGELOG.md](CHANGELOG.md): TV check with owner corrections and preferred picture format, first-Play fix, 4K detection, lean movie page that loads only what it shows, empty-Home focus, timing/decision log lines (`[TvScan]`, `[BestSource]`, `[PageTiming]`, `[PlaybackDiag]`).

## 1.0.2 list (not started)
1. Play takes over the movie page's background Usenet Ultimate search instead of starting a new one (would cut most of the 11 s wait).
2. Merge *The Great British Bake Off* into one show (IMDb tt1877368, seasons 1–17): the 2017+ listing has no IMDb number; IMDb numbers it seasons 8–17.

## What 1.0 changed (owner's goals)
1. **Startup timeout:** at least 30 s (hard timeout about 42 s, cap 45 s). This fixes first Usenet plays giving up after 14 s.
2. **Removed:** cloud (its endpoints point at a dead `.invalid` host; no sync, realtime or analytics), plugins, Discord, sports, and every mention of them in the UI.
3. **No profiles:** one hidden built-in profile, never "Who's watching?".
4. **Defaults at first launch:** English subtitles on, original-language audio, next-episode autoplay off.
5. **Top bar:** Search, Home, plus the Settings gear. Home shows only Continue Watching. **Settings keeps every other section.** The owner will revisit how Settings is reached later.
6. **TV scan** (`util/TvCapabilities.kt`), once on first launch:
   - Captures chip, RAM, hardware decoders (and 10-bit), display modes, HDR types, Dolby Vision and audio passthrough.
   - Treated as 4K if any display mode is at least 3840 wide, otherwise 1080p.
   - Shown in Settings → Playback → This TV, with Re-scan TV.
7. **Best-source Play** (`util/BestSourcePicker.kt`):
   - Plays the first source in Usenet Ultimate's order that suits the TV.
   - If none suits, it picks by resolution: the TV's own first, then lower, then higher. Usenet Ultimate's order is kept within each resolution.
   - Auto-retries up to 3 sources in total.
   - **Show all sources** (default off) opens the full list instead.
   - Usenet Ultimate's list is never re-sorted or filtered.
   - Owner rulings: HDR10+ counts as suiting HDR10-only TVs; a Dolby Vision-only source on a non-DV TV does not suit.
8. **Rename** to "Pascal The Popcorn" in all visible text, in every language.
9. **PTP art:** TV banner, loading logo and launcher icons, on background #0E0F12.

Details for users: [README](README.md), [How Play picks a source](docs/how-play-picks-a-source.md), [Changelog](CHANGELOG.md).

## Build (run in the app source folder on Brain)
```sh
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew compileSideloadReleaseKotlin -PpersonalBuild=true                     # quick check
./gradlew testSideloadReleaseUnitTest -PpersonalBuild=true --tests 'com.arflix.tv.util.BestSourcePickerTest'
./gradlew assembleSideloadRelease -PpersonalBuild=true -x lintVitalSideloadRelease -x lintVitalAnalyzeSideloadRelease   # ~8-10 min warm
```
- Before any copy or install, check that the build succeeded and that the APK's time, hash and versionName are new.
- Without `-Pabis` the build is universal; `-Pabis=armeabi-v7a` builds 32-bit only.

## Releasing a new version
1. Bump `versionName` and `versionCode` (personal build) in `app/build.gradle.kts`.
2. Build and verify the APK.
3. Update `CHANGELOG.md` and this file.
4. Run `gh release create vX.Y Pascal-The-Popcorn-X.Y.apk --repo ReyesPascal/Pascal-The-Popcorn`.

## Rules for this repo
- **Private only.** The APK embeds the TMDB key and addon URLs; rotate them before ever making the repo public.
- **No mentions of the original app name** anywhere here or in release notes.
- **No license or credit file.** This was the owner's choice.
- No old builds, keys, addon addresses or IPs.

## Standing rules
- **Always follow Usenet Ultimate:** never re-rank, filter or override its streams.
- **Never install on a TV without the owner's explicit OK.** Always `adb -s <verified serial>`, and check the fingerprint first.
- **TCL (`G06_2K_US`):** no launcher or home changes. Tailscale was switched on 2026-11-02 and may not reconnect by itself after a power cycle.
- **Multi-agent builds:** start fresh agents from the latest commit, with strict file ownership as in `PERSONAL_PLAN.md`.

## Open / next
1. Owner keeps testing 1.0.1 on the Hisense; then the 1.0.2 list.
2. Owner decides: install on the TCL.
3. After every install: `cmd package compile -m speed <package>`.
4. Later: change how Settings is reached.
5. Known gaps: the Privacy-policy row still links to the original project's site; Home is empty until something has been watched.
