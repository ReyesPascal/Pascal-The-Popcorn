# HANDOFF — Pascal The Popcorn

Updated 2026-11-02 ~21:55 CDT (Claude Code). Summary copy for this repo. The **full handoff** (with source paths) lives in the app source on Brain, as `HANDOFF.md` next to `PERSONAL_PLAN.md`, on branch `personal-tv-aware`.

> The app source is **Brain-local only and never pushed**: its git remote is the original public project, which belongs to someone else. Keys live in a git-ignored `secrets.properties` there.

## Current state

| | |
|---|---|
| App | **Pascal-The-Popcorn 1.0**: `versionName 1.0`, `versionCode 322`. The internal package ID is unchanged from the previous personal build. |
| Source | Branch `personal-tv-aware` @ `132881a` (handoff commit `cf10660`) |
| APK | Release **v1.0** in this repo, plus a private copy on Brain: `~/.local/state/new-tv/ptp-builds/Pascal-The-Popcorn-1.0.apk`. 129,091,946 B, sha256 `9836c4ec296ee0794ab9bf53a606a7ab927993af855f4034ac6589950c9444a0` |
| ABIs | arm64-v8a + armeabi-v7a (one universal APK) |
| Signer | Brain debug key, cert SHA-256 `ee3441c2…bfae8`. It's the same key as the previous personal build, so it upgrades that install in place. |
| Installed on | **No TV yet.** The TCL's previous build was uninstalled on 2026-11-02; the Hisense still runs the previous personal build. |
| Tested | Compiles. BestSourcePicker unit tests pass (20/20). **Not yet tested on a real TV.** |

## What 1.0 changes (owner's goals)
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
1. The owner decides installs: TCL, Hisense, or both. The Hisense can upgrade in place and keep its history.
2. On-TV test: the This TV summary, Play starting directly, the retry path, the first-play wait of at least 30 s, and Continue Watching resume.
3. Later: change how Settings is reached.
4. Known gaps:
   - The "This TV" summary refreshes only when Settings is reopened.
   - The Privacy-policy row still links to the original project's site.
   - Home is empty until something has been watched.
