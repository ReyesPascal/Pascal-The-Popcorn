# HANDOFF — Pascal The Popcorn (personal build of this ARVIO fork)

Updated 2026-11-03 (Claude Code). **Read this first** when resuming. **Current work: 1.0.2 planning, see [`PLAN-1.0.2.md`](PLAN-1.0.2.md) (in progress, nothing coded; don't start until the owner says the whole plan is finished).** Plan, goals and the shared contract: [`PERSONAL_PLAN.md`](PERSONAL_PLAN.md). **1.0.1 plan + the 1.0.2 list: [`PLAN-1.0.1.md`](PLAN-1.0.1.md).**

> **Never push this repo.** `origin` is the public upstream `ProdigyV21/ARVIO` (someone else's project). Everything here is Brain-local. Keys live in git-ignored `secrets.properties`.

## Current state

| | |
|---|---|
| App | **Pascal-The-Popcorn 1.0.1** (`versionName 1.0.1`, `versionCode 323`, package `com.arvio.tv` unchanged) |
| Branch / commit | `personal-tv-aware` @ `00fedbe` = lead `d0945a3` + Agent A `fb296ff` (first-run TV check) + Agent B `69e305c` (movie page) |
| APK (private copy) | `~/.local/state/new-tv/ptp-builds/Pascal-The-Popcorn-1.0.1.apk`, 129,136,754 B, sha256 `7b8435052bd4f0fce8cc966deb70b0b690826da9c77d4e7c8fb0c837e65969f7` (1.0 APK kept alongside; both in `SHA256SUMS`) |
| ABIs | arm64-v8a + armeabi-v7a (one universal APK) |
| Signer | Brain debug key, cert SHA-256 `ee3441c2…bfae8` |
| Published | Private GitHub repo **ReyesPascal/Pascal-The-Popcorn**: releases **v1.0** and **v1.0.1** (Latest, 2026-11-02). Source snapshot on branch `source` (bc1747f + docs updates). |
| Installed on | **Hisense 65U6H** (fresh install of 1.0.1 on 2026-11-02 23:10, `adb -s <tv-adb-address>`; old app + data removed first by the owner; `cmd package compile -m speed` → status=speed). Also the **TCL** (1.0.1 fresh install 2026-11-02 ~23:37, `adb -s <tv-adb-address>`, **no** speed-compile per owner). TCL scan: 1080p, HDR10+HLG, no DV. If TCL Play says "No streams found", check Tailscale is connected first. |
| Tested on the Hisense | Scan says **4K** (from `vendor.display-size=3840x2160`), DV P4/5/8/9/10, HDR10/HDR10+/HLG. First Play works first time (movie 155: page drawn 230 ms; picked UU #0 4K DV 23.4 GB with preference Dolby Vision; Play→first frame 14.7 s, of which 11.0 s waiting for UU's search). Bake Off 2026 episode won't play (no IMDb ID on TMDB 87012) → 1.0.2 item 2. |
| Unit tests | Picker 29 + size 2 + overrides 11 + details 15 pass. 13 older details tests (watchlist/stream-completion) fail exactly as before 1.0.1 (Log not mocked / DataStore NPE; 3 now reach assertions because the personal build skips the watchlist read on purpose). |

## Session 2026-11-03: 1.0.2 planning (not coded)
- **Crash seen on the Hisense** (QR screen at launch = the leftover crash notice):
  - Cause: Android 11 `SecurityException: Package android does not belong to <uid>`, from `getNetworkCapabilities` in `network/NetworkMonitor.kt`, created when a movie page opens (crash buffer 2026-11-02 23:54).
  - The fix is planned (PLAN-1.0.2 Part 1).
  - Nothing was sent anywhere: `ENABLE_CRASH_REPORTING=false` in the personal release.
- **Owner decisions:** a big clean-up. The full list is in **PLAN-1.0.2.md → "Decisions log"**. In short:
  - Remove Accounts/Trakt, Playback (→ Advanced), Language, Subtitles, AI subtitles (incl. Sync by Hearing), Stream integrations (UU only), Telegram, Live TV, Home server, collections, trailers, translations, and everything previously only hidden (Home rows, movie-page extras, profiles, plugins, cloud, Discord).
  - Top bar → Search · Home · Library · Settings. **+ Watchlist** button. Per-show auto-play. TVmaze air times for the calendar.
- **Open items** (next session): crash QR screen, Cast, self-updater, Firebase, repo-only folders; Settings sections Add-ons / Catalogs / Interface / Network; whether to fold in the old 1.0.2 items (UU lookup reuse, Bake Off merge); the implementation approach.
- **Found while planning:**
  - `secrets.properties` Trakt keys are placeholders, so Trakt public data never worked.
  - Both TVs expose only 60/50 Hz display modes to apps, so there's no 24p switching.
  - `HomeViewModel` loads hidden catalog rows in the background (wasted work).
  - The PTP APK carries ~39 MB TDLib + ~46 MB sherpa-onnx native libraries that 1.0.2 removes.

## 1.0.1 (on top of 1.0)
1. **First-Play fix:** a cancelled stream search (Play closing the movie page) is rethrown, never logged/cached as "no sources" (`StreamRepository.kt`, 4 catch blocks + `ensureActive()` before caching). This was the false "No streams found" on the first Play.
2. **4K detection:** display mode ≥3840 → `vendor.display-size`/`sys.display-size` → Media3 display size → `ro.vt.vcom_display_resolution` → model name. `SCHEMA_VERSION` 2 forces a re-scan.
3. **First-run TV check** (`ui/screens/tvcheck/TvCheckScreen.kt`): Welcome → Scan this TV → Scanning → Results (✓/✗, device line, Preferred picture format) → Looks right / Something's wrong (switches for 4K, DV, HDR10, HDR10+, HLG; "set by you"; Save / Reset my changes / Scan again). Shown once per install (`tv_check_done_v1`); Settings → Playback → This TV opens it. Owner corrections + preference: `util/TvOverrides.kt` (`personal_tv_overrides`).
4. **Preferred picture format** in the picker: walk UU's list in order; with a preference, the first entry that suits the TV and matches it wins — same resolution only on a 4K TV, any resolution on a 1080p TV; hybrids count for both; failover follows it.
5. **Movie page** shows/loads only: backdrop (TMDB w1280), logo, info line, whole-sentence description, big focused ▶ Play, 👁 Watched (+ seasons/episodes on shows). Ratings, budget, trailer, Sources, View Collection, Watchlist, cast, More Like This, Collection, Reviews, streaming services: not shown and never requested. Background UU lookup starts immediately (no 1 s wait).
6. **Empty Home** starts focus on the top bar.
7. **Logs over adb** (System.err, survive release stripping): `[TvScan]`, `[BestSource]`, `[PageTiming]` (page drawn, Play→first frame), `[PlaybackDiag]`.

## What changed vs the old personal build (owner's goals)
1. **Startup timeout:** at least 30 s (hard timeout about 42 s, cap 45 s). This fixed the 14 s give-up seen on the TCL.
2. **Removed** cloud (endpoints point at `https://cloud-disabled.invalid`; no sync, realtime, analytics), plugins, Discord, sports, and every UI mention of them.
3. **No profiles:** one hidden built-in profile, never "Who's watching?", all switch-profile paths do nothing.
4. **Defaults** written at first launch: English subtitles on, original-language audio, next-episode autoplay off.
5. **Top bar:** Search, Home, plus the Settings gear. Home shows only Continue Watching, with no billboard. **Settings keeps every other section.** The owner will revisit how Settings is reached later.
6. **TV scan (`util/TvCapabilities.kt`), once on first launch:**
   - What it captures: chip, RAM, hardware decoders (and 10-bit), display modes, HDR types, Dolby Vision, audio passthrough.
   - Where it's stored: SharedPreferences `personal_tv_capabilities`.
   - How it's shown: Settings → Playback → This TV / Re-scan TV.
   - Screen class: 4K if any mode is ≥3840 wide, else treated as 1080p.
7. **Best-source Play (`util/BestSourcePicker.kt`):**
   - Plays the first source in Usenet Ultimate's order that suits the TV.
   - If none suits, it picks by resolution: the TV's own first, then lower (closest first), then higher. UU order is kept within each resolution.
   - Auto-retry goes to the next candidate, up to 3 total.
   - **Settings → Playback → Show all sources** (`util/PersonalPrefs.kt`, default off) opens the stock list instead.
   - UU's list is never re-sorted or filtered.
   - Owner rulings: HDR10+ counts as suiting HDR10-only TVs. A Dolby Vision-only file on a non-DV TV does not suit.
8. **Rename** to "Pascal The Popcorn" in all user-visible text, all languages. Network identifiers (User-Agent, Plex/Jellyfin client names) are unchanged.
9. **PTP art:** TV banner, loading logo, launcher icons, monochrome icon. Background colour #0E0F12.

## Build
```sh
cd ~/src-untrusted/ARVIO && export ANDROID_HOME=$HOME/Android/Sdk
./gradlew compileSideloadReleaseKotlin -PpersonalBuild=true                     # quick check (~1-4 min warm)
./gradlew testSideloadReleaseUnitTest -PpersonalBuild=true --tests 'com.arflix.tv.util.BestSourcePickerTest'
./gradlew assembleSideloadRelease -PpersonalBuild=true -x lintVitalSideloadRelease -x lintVitalAnalyzeSideloadRelease   # ~8-10 min warm
```
- Output goes to `app/build/outputs/apk/sideload/release/app-sideload-release.apk`.
- Before any copy or install, check that the build succeeded and the APK timestamp, hash and versionName are new (`aapt2 dump badging`).
- Without `-Pabis`, the build is universal. `-Pabis=armeabi-v7a` gives 32-bit only (~74 MB).
- `gradle.properties` heap is 6 GB (Brain: 8 cores, 30 GB RAM).
- Use a warm daemon: don't pass `--no-daemon`.

## Rules for the PTP GitHub repo (owner)
- **Private only.** The APK embeds the TMDB key and addon URLs; rotate them before ever making it public.
- **No ARVIO mentions anywhere** in that repo or its release notes.
- **No license or credit file.** This was the owner's choice; they were told Apache 2.0 asks for one if the app is shared beyond themselves.
- No old builds, keys, addon addresses or IPs.
- New version:
  1. Bump `versionName` / `versionCode` in `app/build.gradle.kts` (personalBuild branch).
  2. Build and verify.
  3. Update `CHANGELOG.md` in the PTP repo.
  4. `gh release create vX.Y Pascal-The-Popcorn-X.Y.apk --repo ReyesPascal/Pascal-The-Popcorn`.

## Rules that still apply
- Pascal The Popcorn must **always follow Usenet Ultimate**: never re-rank, filter or override its streams.
- **Never install on a TV without the owner's explicit OK.** Always `adb -s <verified serial>`; check the fingerprint first.
- TCL = `G06_2K_US`. The owner wants **no launcher or home changes on the TCL**. Tailscale was switched on there on 2026-11-02; it may not reconnect on its own after a power cycle.
- Agents were used for this build (Agent A Settings, Agent B scan/picker). Their worktrees were deleted after merging. For a new update, start fresh agents from the latest commit, with strict file ownership as in `PERSONAL_PLAN.md`.

## Open / next
1. **1.0.2 list** (in `PLAN-1.0.1.md`, not started): (1) player takes over the movie page's background UU lookup instead of searching again; (2) merge The Great British Bake Off into one show (tt1877368, seasons 1–17) so the 2026 episodes play.
2. Owner keeps testing 1.0.1 on the Hisense (episodes, Settings → This TV, description). Log watch: `adb -s <verified Hisense> logcat -v time | grep -E "\[(TvScan|BestSource|PageTiming|PlaybackDiag)\]"`.
3. Owner decides: publish 1.0.1 to the PTP GitHub repo; install on the TCL.
4. After every install: `cmd package compile -m speed com.arvio.tv` (undo: `cmd package compile --reset com.arvio.tv`).
5. Agent worktrees under `.claude/worktrees/` (A and B, already merged) are locked by Claude Code; remove later with `git worktree remove`.
6. Known gaps: Privacy-policy row still links to the original project's site; shows still fetch IMDb episode ratings inside `getSeasonEpisodes` (not shown); the background lookup also asks Plex/Jellyfin (no-op without a home server); the Play label keeps the app's wording ("Continue S1 E3").
