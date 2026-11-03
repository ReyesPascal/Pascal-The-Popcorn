# PLAN 1.0.2 — Pascal The Popcorn

**Status (2026-11-03): PLANNING IN PROGRESS. NOTHING IS CODED.**
- **The owner's rule:** do **not** start any code until the owner says the *entire* plan is finished. Plan approval alone is not a go.
- Code is at `personal-tv-aware` @ `b939003` (= 1.0.1), clean tree.
- 1.0.1 is installed on the Hisense and the TCL, and released as v1.0.1.

**How to resume:** read this file, then `HANDOFF.md`. Go through the **Open items** with the owner, then the remaining Settings sections, then wait for "start".

## Open items (not decided yet)
1. **Leftovers still to decide:** delete each of these, or keep it?
   - Crash screen with the QR code (links to the original project's report page)
   - Cast (Chromecast)
   - App self-updater (already off)
   - Firebase Crashlytics + Analytics libraries (already off; the google-services project id is the original developer's)
   - The original project's repo-only folders (`web`, `netlify-*`, `supabase`, `resolver-worker`, `unraid`, `releases`, `screenshots`, `docs`, `distribution`, `templates`, `scripts`)
2. **Settings sections not yet gone through:** Add-ons (`stremio`), Catalogs, Interface (`appearance`), Network.
   - Add-ons note: the bundled add-ons are Usenet Ultimate (streams) and AIOMetadata (catalogs/metadata). After Part 11, Home has no catalog rows, so AIOMetadata's remaining role needs checking (metadata/search?) before deciding.
   - Interface rows already doomed by earlier parts: Netflix home style (49), Watchlist on Home (50), card layout (17), guide rows (47).
3. **Still on the 1.0.2 list from 1.0.1** (`PLAN-1.0.1.md`), to fold in or not:
   - Player reuses the background Usenet Ultimate lookup (Play→frame 14.7 s, of which 11 s is UU search)
   - Bake Off TMDB 34549+87012 → IMDb tt1877368 S1–17 merge
4. **Implementation approach** (when the owner says start):
   - One big change, or several commits per part. Recommended: one commit per part, each compiled and tested.
   - Agents with strict file ownership, or lead-only.
   - Run the touch-point audit (like Parts 7–9) for every part before deleting, including Parts 1, 5, 6 and 11.

## Decisions log (owner, 2026-11-03)
- **Crash on the Hisense:** Android 11 `SecurityException` in `NetworkMonitor`. Fix it (Part 1, step 5). The QR screen was the leftover crash notice; nothing was sent anywhere.
- **No Trakt/Simkl/MDBList at all.** Delete directly with no switch-off stage. Losing watch history is fine (beta).
- **Watchlist + Release calendar:** kept and shown (Library tab), local only. Calendar uses TVmaze in the background for air times.
- **Settings → Playback:** replaced by **Advanced**. Auto-play next becomes per show (default off). No trailers anywhere. Match Frame Rate defaults to Always. Volume Boost stays at 0 dB.
- **Language & audio, Subtitles, AI subtitles, Stream integrations, Live TV, Home server:** sections removed. Values hardcoded or code deleted as in Parts 3–8.
- **Translations:** delete them (English only).
- **Sync by Hearing:** delete it, and recode the player's subtitle setup to standard Media3.
- **Telegram:** delete all of it.
- **Collections:** delete all of them (built-in, imported, YouTube).
- **Top bar:** undo only the "Search/Home/Settings only" restriction → Search · Home · Library · Settings. Add a **+ Watchlist** button on the movie/show page.
- **Hidden extras:** delete everything that was only hidden (Home rows/banner/Netflix home, movie-page ratings/cast/similar/reviews/services/sources button).
- **Original goals:** profiles, plugins, cloud and Discord are deleted, not hidden.
- **Owner preferences:** ask questions in plain text, not pop-ups. Explain the risk of each change. Check everything a removal touches and recode where needed.

## Saved draft from the first (reverted) start
A patch of the 4-file Commit-A draft was saved in the session scratchpad. It isn't needed: everything in it is described in Part 1 (SyncProviderStore local-only, Trakt worker cancel, NetworkMonitor try/catch).

---

## Context
The owner doesn't want Trakt (or Simkl or MDBList) at all. They want to remove the whole **Accounts** section and have watchlist, Continue Watching and watched history always come from the app itself (local), hardcoded.

The owner also hit a crash on the Hisense: the QR screen at launch was the leftover crash notice. The crash itself is an Android 11 bug: `getNetworkCapabilities` throws `SecurityException: Package android does not belong to 10096`. It happens in `NetworkMonitor`, which is created when a movie page opens (logcat crash buffer, 2026-11-02 23:54). Nothing was sent anywhere: `ENABLE_CRASH_REPORTING=false` in the personal release.

Repo `~/src-untrusted/ARVIO`, branch `personal-tv-aware` @ `b939003`. Never push origin.

## What's in Accounts today (personal build rows 1–9, 12–14, 16, 17)
- Trakt, MDBList and Simkl connect/disconnect
- Read source for watchlist, Continue Watching and watched (rows 4–6)
- Write to Trakt/Simkl (rows 7–8)
- Telegram settings (row 9)
- App updates (row 12, already off: `SELF_UPDATE_ENABLED=false`)
- Diagnostics toggle (row 13, already off)
- Privacy policy link (row 14)
- About & Credits (row 16)
- Usage opt-in (row 17)

None of it is needed for Play.

## Key finding: what must NOT be deleted
`data/repository/TraktRepository.kt` (5,446 lines) holds the **local** data despite its name:
- `saveLocalContinueWatching`, `getLocalContinueWatching`
- `savePlaybackProgress`
- `markMovieWatched` / `getWatchedMovies` / `getWatchedEpisodes`
- dismissals

Home, Player and Details all use it. Its local half stays and gets renamed. Its Trakt half is deleted (step 4).

`data/repository/WatchlistRepository.kt` and `WatchHistoryRepository.kt` (local) also stay.

With no account connected, `SyncProviderStore.getTrackingPreferences()` already falls back to `AUTO`, so `readProviders()` and `writeProviders()` return nothing and everything is local. Step 1 just makes that permanent.

## Part 1 — Remove Accounts + all Trakt / Simkl / MDBList; local watchlist / Continue Watching / history; crash fix
1. **Hardcode local** — `data/repository/sync/SyncProviderStore.kt`: when `BuildConfig.PERSONAL_BUILD`:
   - `getTrackingPreferences()` returns all `AUTO`, with write flags `false`.
   - `readProviders()` returns `emptySet()`.
   - `writeProviders()` returns `emptySet()`.
   - `getProvider()` returns `NONE`.

   This is the one place every caller goes through, so stray saved tokens can never switch anything back on.
2. **Stop the Trakt sync jobs**:
   - `ArflixApplication.scheduleTraktSyncIfNeeded()` returns early in the personal build. It also cancels any already-queued unique work (`TraktSyncWorker.WORK_NAME`, `WORK_NAME_ON_OPEN`), which is harmless if none exists.
   - `MainActivity.enqueueFullTraktSync()` does nothing in the personal build.
3. **Remove the Accounts section** — `ui/screens/settings/SettingsScreen.kt`:
   - `sections` list (~line 583): `add("accounts")` only when `!PERSONAL_BUILD`. Settings then opens on Playback.
   - Check anything that opens Settings at `"accounts"`. `indexOf` returns -1, which already falls back to the first section, so nothing should break; verify by grep.
   - Remove the personal-only branches for the accounts row ids and description (`accountsRowIds` personal list, `personal_settings_desc_accounts`).
   **Owner 2026-11-03: no separate switch-off stage.** Steps 1–3 happen as part of the deletion in one go. This is a beta, and losing watch history is fine.

4. **Delete all Trakt, Simkl and MDBList code (owner: "I don't want trakt")** — in the same change, guided by the compiler:
   - **Delete whole files:**
     - `data/api/TraktApi.kt`
     - `data/repository/TraktSyncService.kt`
     - `worker/TraktSyncWorker.kt`
     - `data/repository/simkl/`
     - `sync/TraktRemoteProvider.kt`, `SimklRemoteProvider.kt`, `MdbListRemoteProvider.kt`, `RemoteSyncManager.kt`, `RemoteSyncProvider.kt`
     - The MDBList and Simkl API clients and interceptors, plus their Hilt `@Provides` in `di/`
   - **`TraktRepository.kt`:**
     - Keep only the local parts: local Continue Watching, progress, watched movies/episodes, dismissals, watchlist-local, TMDB enrichment.
     - Remove every `traktApi.*` call (53), the token/auth/device-code code, and the Trakt-vs-local merge. `mergeTraktAndRecentLocalContinueWatching` in HomeViewModel becomes local only.
     - **Rename the file** to `LocalWatchRepository`, so "Trakt" no longer appears in the app's data layer. About 23 files import it.
   - **`SyncProviderStore`** shrinks to a stub that is always local (or is removed, if nothing still needs it). `SyncProvider` and `TrackingReadMode` lose their TRAKT/SIMKL/MDBLIST values.
   - **Callers** (HomeViewModel, PlayerViewModel, DetailsViewModel, SearchViewModel, SettingsViewModel, WatchlistViewModel, MainActivity, ArflixApplication, AuthRepository, PersonalListsRepository, ReleaseCalendarRepository, MediaRepository, LauncherContinueWatchingRepository): drop the Trakt branches.
   - **Strings:** remove the Trakt, Simkl and MDBList strings that are no longer used, in all languages.
   - **Tests:**
     - Delete the tests that cover only Trakt, Simkl or MDBList.
     - Keep the local-data tests (`TraktLocalWatchedSnapshotTest`, `TraktWatchedCacheLoadTest`, `TraktContinueWatchingRefreshTest`) and port them to `LocalWatchRepository`.
   - **Keep the Watchlist and the Release calendar (owner may bring them back), with every account removed from them:**
     - Both stay hidden as they are today. Their code stays complete and compiling, so they can be switched back on later.
     - **Watchlist:**
       - Stays: `WatchlistRepository` (local list) and the Watchlist screen.
       - Removed from `WatchlistViewModel`: the Trakt watchlist/personal lists and the Simkl library. It shows only the local list.
       - `PersonalListsRepository` (Trakt personal lists) is deleted, or reduced to local-only if the Watchlist screen needs it to compile.
     - **Release calendar:**
       - Stays: `ReleaseCalendarRepository`, `ReleaseCalendarViewModel` and the calendar screen.
       - Data comes from the local watchlist (`watchlistRepository.getLocalWatchlistItems()`), with dates from TMDB.
       - `ReleaseCalendarSource` is reduced to the local watchlist only; the "All / Trakt / SIMKL / MDBList" choices go.
       - The Trakt "exact air time" lookup (`authoritativeTimes`) is deleted. It never worked in PTP: `secrets.properties` still has the placeholder Trakt key.
       - **Replaced by TVmaze, running silently in the background** (owner 2026-11-03). TVmaze is free and needs no key and no account.
         - **New** `data/api/TvMazeApi.kt`, a small Retrofit client for `https://api.tvmaze.com/` with 2 calls:
           - `GET lookup/shows?imdb={tt}` → TVmaze show id. Use `thetvdb=` as a fallback.
           - `GET shows/{id}/episodes` → `season`, `number`, `airstamp` (ISO time with zone).
         - Gets the IMDb ID from TMDB `tv/{id}/external_ids` (already in `TmdbApi.kt:189`).
         - Replaces the type `TraktCalendarEpisode` with a neutral `CalendarEpisodeTime(season, number, airstamp)` that `calendarEpisodeReleases` (`ReleaseCalendarMapping.kt:75`) consumes. `firstAired` becomes `airstamp`.
         - Keeps the existing `cached(...)`, `timePermits` (rate limit) and 4 s budget pattern from `authoritativeTimes`. TVmaze allows about 20 calls per 10 s per IP, and its responses are cached.
         - Any failure, or a show TVmaze doesn't have, silently falls back to the TMDB date only. It never blocks or slows the calendar, and the user never sees it.
         - Never on the Play path. It's only used by the calendar, which is hidden today.
         - Unit test: parse a sample TVmaze episodes JSON, map `airstamp` to the right local date and time, and confirm a failed lookup falls back to TMDB.
     - Neither feature keeps any account code.
   - **Leave cloud-sync code alone** (`CloudSyncRepository`, which also references Trakt). It's already off in the personal build; only its Trakt references get trimmed so it still compiles.
   - **Note:** after this, the non-personal build won't compile. PTP only ever builds with `-PpersonalBuild=true`.
   - **Risk:**
     - This is the biggest change in PTP so far: about 70 app files mention Trakt.
     - The danger is breaking local Continue Watching or watched marks. It's checked with the local-data tests and on the TV (step-by-step checks below).
     - **Losing existing watch history is acceptable** (owner: beta). Old DataStore keys and data formats can be dropped or simplified freely; no migration is needed.
5. **Fix the crash** — `network/NetworkMonitor.kt`: wrap both `getNetworkCapabilities(activeNetwork)` calls (`checkNetworkConnectivity`, `getNetworkType`) in `try/catch (SecurityException / RuntimeException)`. On error, assume connected (`true`), so Play is never blocked; `getNetworkType` returns `OTHER`.
6. Bump to **1.0.2** (`versionCode 324`) in `app/build.gradle.kts`. Update HANDOFF.md, which is also stale: 1.0.1 is released and on the TCL.

## Part 2 — Settings: Playback section replaced by "Advanced" (owner 2026-11-03)
The **Playback** section is removed entirely. A new **Advanced** section (`"advanced"`, at the same place in the Settings list) holds only the rows the owner kept.

| Old Playback row | Decision |
|---|---|
| Auto-Play Next | **Removed from Settings.** Becomes **per show** (below). |
| Autoplay (single source) | Removed (PTP ignores it already). |
| Show all sources | **Advanced** |
| This TV | **Advanced** |
| Auto-Play Min / Max Quality, Max File Size | Removed, plus their dead prefs (Play never reads them). |
| Trailer Auto-Play / Sound / Delay / In Cards | Removed. **No trailers anywhere in the app** (below). |
| Quality Regex Filters | Removed, plus the filter step in `StreamRepository.kt:~5151` (`filterStreamsByQualityRegex`) and the `quality_filters` pref. Usenet Ultimate does this itself. |
| Match Frame Rate | **Advanced, default "Always"** (first-launch default like the other PTP defaults; switches to 50 Hz for 25/50 fps UK shows, no change for 24p films on these TVs). |
| Dolby Vision Compatibility | **Advanced**, default **on** (already `?: true` in `PlayerViewModel.kt:963`). |
| Volume Boost | **Advanced**, default 0 dB (as now). |

- **Where the code is:** `ui/screens/settings/SettingsScreen.kt`.
  - In the `sections` list, replace `add("playback")` with `add("advanced")` in the personal build.
  - Add `"advanced" -> listOf(51, 52, 15, 40, 27)` in `tvGeneralRowsForSection`.
  - Add a title and description for `"advanced"`, plus English strings (other languages fall back to English).
  - The click handler `when` around line 1460 already handles these row ids; reuse it.
  - Point the settings deep-link to "This TV" (`initialSection = "playback"`, used by the first-run TV check) at `"advanced"`.
- **Where defaults are written:** the first-launch default write in `SettingsViewModel.kt:~739` (next to `PERSONAL_DEFAULT_AUTO_PLAY_NEXT`). Add `frame_rate_matching_mode = "always"` if unset.

### Auto-play next, per show
- The setting lives on **each show's page**: a toggle button **"Auto-play next episode"** beside 👁 Watched. It only appears on shows, never on movies.
- **Default off** for every show.
- Stored per show: DataStore key `autoplay_next_show_<tmdbId>`, in a small helper in `util/PersonalPrefs.kt`.
- `PlayerViewModel.kt:888` reads the per-show value for the playing show (instead of the global `auto_play_next`). Movies: never auto-play.
- `setAutoPlayNext` (`PlayerViewModel.kt:3694`, the in-player toggle) writes the same per-show key, so both places stay in step.
- Delete the global `auto_play_next` first-launch default (`SettingsViewModel.kt:739–747`).

### No trailers anywhere
- **Delete:**
  - `ui/components/YouTubeTrailerModal.kt`
  - `ui/components/TrailerPlayer.kt`
  - `ui/components/HomeTrailerPreview.kt`
  - `ui/components/TrailerWebViewRecovery.kt`
  - The trailer prefs and code in `HomeScreen`, `NetflixHome`, `HomeViewModel`, `HomeProfilePreferences`, `MediaCard`, `util/HomePresentationMigration.kt` and `DetailsViewModel` (trailer fetch is already skipped in personal)
  - About 31 files mention trailers. Strip each one.
- **Remove the YouTube player library** from `app/build.gradle.kts:519` (`androidyoutubeplayer`), which shrinks the APK.
- **Check:** `grep -ri trailer app/src/main/kotlin` leaves only harmless model fields from TMDB, or none.

## Part 3 — Settings: remove "Language & audio"; hardcode English (owner 2026-11-03)
**The section goes:** in `SettingsScreen.kt`, the `sections` list drops `add("language")` in the personal build. Its 4 rows (ids 0, 3, 1, 2) all disappear:

| Row | Fixed value in PTP |
|---|---|
| App language (0) | **English (en-US)**, always, whatever the TV's language is |
| Default subtitle (3) | **English** |
| Secondary subtitle (1) | **Off** |
| Default audio (2) | **Original** (`"Auto (Original)"` = the title's original audio track) |

**Hardcoded, not just defaulted.** New constants in `util/PersonalPrefs.kt` (`APP_LANGUAGE = "en-US"`, `SUBTITLE = "English"`, `SECONDARY_SUBTITLE = "Off"`, `AUDIO = "Auto (Original)"`). In the personal build these are used instead of reading the saved prefs, so nothing left over can change them:
- **App language:**
  - `MainActivity.attachBaseContext` (~224) and `initialAppLanguage` (~376)
  - `CrashReportActivity.attachBaseContext` (48)
  - The TMDB language interceptor in `di/AppModule.kt:46`. TMDB titles and descriptions are always English.
  - `util/AppLanguage.defaultAppLanguage()`
  - `SettingsViewModel.resolveAppLanguage`
- **Subtitles:** `PlayerViewModel.kt` lines ~896, 898, 2072, 2088–2089. Also `HomeViewModel.kt:1121` and the subtitle read in `MainActivity.kt:354`.
- **Audio:** `PlayerViewModel.resolvePreferredAudioLanguage()` (~2738).
- **Remove** the now-pointless first-launch subtitle write (`SettingsViewModel.kt:724–730`) and the `PERSONAL_DEFAULT_SUBTITLE` constant.

**Stays:**
- The **in-player** audio and subtitle track menu, so you can still switch tracks for the one video you're watching. This was not asked to be removed.
- The **Subtitles** section (style/size etc.) is untouched. Not discussed yet.

**Risk: low.** These are value reads; Play's source order doesn't depend on language.

One thing to check: subtitle add-ons and the source picker may pass the language to Usenet Ultimate or subtitle add-ons. They now always get English or original, which matches what's saved on both TVs today.

### Delete the translations (owner: yes)
- **Delete the 51 language folders** under `app/src/main/res/` (`values-af` … `values-zh-rTW`; each has `strings.xml` and `calendar_strings.xml`). Keep `values/` (English) and any non-language folders.
- **Make the locale config English only:**
  - Trim `res/xml/locale_config.xml` to English, so Android's per-app language picker offers nothing else.
  - Alternatively remove `android:localeConfig` (`AndroidManifest.xml:68`) together with the file.
- **Add `androidResources { localeFilters += listOf("en") }`** in `app/build.gradle.kts` `defaultConfig`. This also strips the AndroidX, Media3 and Material translations from the APK. Use `resourceConfigurations += "en"` if the AGP version is older.
- **Check:** `aapt2 dump badging` shows `locales: '--_--' 'en'` only. The APK is smaller.
- **Benefit:** future text changes touch only `values/strings.xml`.
- **Risk: low.** Every English string already exists in `values/`. Android lint/compile flags any string that was only translated.

## Part 4 — Settings: remove "Subtitles"; hardcode the current values (owner 2026-11-03)
**The section goes:** in `SettingsScreen.kt`, the `sections` list drops `add("subtitles")` in the personal build, along with all its rows (ids 4, 5, 6, 7, 42, 8, 38, 48, 39, 9, 45).

**Hardcoded values.** These are the current defaults, which is what the Hisense is on. They go in `util/PersonalPrefs.kt` as `PersonalSubtitles` constants.

| Setting | Pref key | Fixed value |
|---|---|---|
| Size | `subtitle_size` | **Medium** |
| Colour | `subtitle_color` | **White** |
| Position | `subtitle_offset` | **Bottom** |
| Style | `subtitle_style` | **Bold** |
| Font | `subtitle_font` | **System** (`SubtitleFontOption.DefaultPreference`) |
| Stylized subtitles | `subtitle_stylized` | **On** |
| Auto find best match (AI) | `subtitle_ai_find_best_match` | **Off** |
| Sync by hearing | `subtitle_hearing_sync` | **Off** (fixed, not the RAM-based default) |
| Preload subtitles | `subtitle_preload*` | **On** |
| Filter by language | `filter_subtitles_by_lang` | **On** |
| Forced subtitles | `use_forced_subtitles` | **Off** |

- **The player** (`PlayerViewModel.kt`, the prefs load around lines 880–960 that builds the UI state) uses these constants instead of the prefs in the personal build. Saved values can't override them.
- **Any in-player menu that changes subtitle size, colour or style:** make those write a no-op, or hide them in the personal build, so the fixed look can't drift. Check for these in `PlayerScreen.kt` while implementing.
- **Stays:** picking a subtitle track (or off) for the current video in the player.
- **Note:** "Sync by hearing" is off for good, so its audio-sync code becomes dead in PTP. It's left in place for now and can be removed later, as part of the AI-subtitles / leftover clean-up.
- **Risk: low.** The values are the same as today; only where they come from changes.

## Part 5 — Remove AI subtitles completely: section and all code (owner 2026-11-03)
**Settings:** in `SettingsScreen.kt`, the `sections` list drops `add("ai_subtitles")`, along with rows 28–33:
- AI Subtitle Translation
- AI Model
- Auto-Select AI Translation
- Remove Hearing Impaired Text
- API Key
- Scan QR Code to Set Key

The AI "Auto Find Best Subtitle Match" row (38, from Part 4) and its pref go too.

**Delete these files:**
- `ui/screens/player/subtitles/SubtitleTranslationService.kt`
- `ui/screens/player/subtitles/GeminiLiveTranslationService.kt`
- `ui/screens/player/subtitles/SubtitleTranslationManager.kt`
- `ui/screens/player/subtitles/SubtitleAiModel.kt`
- `ui/screens/player/subtitles/SubtitleAutoSync.kt` (if it's AI-only)
- `ui/screens/player/engine/exoplayer/AiSubtitleRenderersFactory.kt`: the player falls back to the standard Media3 renderers factory. Check that `BufferedCueReader.kt` is only used by it, and delete it too if so.
- `server/AiKeyConfigServer.kt` and `server/AiKeyWebPage.kt`: the local web server and QR code for typing in the AI key. The `server/` folder goes.

**Strip AI references from:**
- `PlayerViewModel`, `PlayerScreen` and `MobilePlayerPanels`: the AI translate menu items, plus the `subtitleAi*` state and prefs
- `SettingsViewModel`
- `MainActivity`
- `CloudSyncRepository`: only the references, so it compiles
- `DetailsViewModel` and `DiscordRpcManager`: references only
- AI strings in `values/strings.xml`, and AI tests

**Sync by Hearing (on-device speech AI): delete too. Owner confirmed 2026-11-03.**
- **Delete:**
  - `ui/screens/player/audiosync/` (about 21 files, plus `asr/`)
  - The `subtitle_hearing_sync` pref
  - Row 48
- **In `app/build.gradle.kts`:**
  - Remove the `downloadSherpaOnnx` task, the `sherpaOnnx*` values and the `sideloadImplementation(files(sherpaOnnxAar))` dependency (~line 688).
  - Remove the packaging excludes (~302).
  - Remove `AUDIO_SYNC_AVAILABLE`.
- **Also delete** the `assets/licenses/sherpa-onnx…`, `silero-vad…` and `onnxruntime…` files.
- **Saves about 46 MB of native libraries** in the universal APK (`libonnxruntime.so` + `libsherpa-onnx-jni.so`, both ABIs).
- Part 4 already hardcodes it off, so nothing visible changes.

**Stays:** normal subtitles (add-on/embedded tracks, picking a track, the hardcoded look from Part 4).

**Recode the player's subtitle setup (owner: "recode it").** Rewrite the ExoPlayer setup in `PlayerScreen`/`PlayerViewModel` (wherever `AiSubtitleRenderersFactory` and the audio-sync tap are built) to use the standard Media3 `DefaultRenderersFactory` with the normal text renderer:
- Keep the Dolby Vision compatibility hooks (`player/dv/DolbyVisionStripExtractorsFactory`) and the existing extension-renderer and decoder settings exactly as they are.
- Remove the `PlaybackAudioTap` / audio processor wiring that only fed Sync by Hearing.
- Subtitle styling comes from the Part 4 constants.

**Risk: low–medium.** This is a player-setup change. Test on the TV that a movie plays with English subtitles showing (embedded track and add-on subtitle).

**Check:** `grep -riE "subtitleAi|AiSubtitle|gemini|sherpa|onnx|AiKey" app/src` returns nothing.

## Part 6 — Remove "Stream integrations"; hardcode Usenet Ultimate as the only source (owner 2026-11-03)
**What the section did:** it ordered and switched on/off 5 kinds of stream source:
- Home server (Plex/Jellyfin)
- **Stremio add-ons**: Usenet Ultimate is one of these
- Plugins
- Telegram
- IPTV VOD

It also had a search-mode switch (parallel / sequential) and per-provider on/off/order.

**Settings:** in `SettingsScreen.kt`, the `sections` list drops `add("stream_integrations")`. Delete `ui/screens/settings/StreamIntegrationsScreen.kt` (883 lines), its SettingsViewModel actions (`moveIntegrationUp/Down`, `toggleIntegration`, `moveProviderItem*`, `toggleProviderItem`, `setSearchMode`, ~2352–2394) and its strings.

**Hardcode, in `data/repository/StreamIntegrationRepository.kt`, personal build:**
- `isIntegrationEnabled(type)` = `type == STREMIO_ADDONS`. Home server, plugins, Telegram and IPTV VOD are always **off**, so `DetailsViewModel` (~2258–2312), `PlayerViewModel` (~1090–1567) and `StreamRepository` (~2538–3369) never search them.
- `observeConfigs()` / `getConfigs()` return just `[STREMIO_ADDONS enabled]`.
- `getUnifiedSourceOrderedIds(Sync)()`: the installed add-ons in install order, so Usenet Ultimate comes first. No saved order or per-provider toggles are read.
- `getSearchMode()` = `PARALLEL` (the current default).
- **Usenet Ultimate's own list order is untouched**, as now ("always follow UU").

**Add-ons note:** the bundled add-ons are Usenet Ultimate (streams) and AIOMetadata (catalogs/metadata). AIOMetadata isn't a stream source, so it isn't affected. Settings → Add-ons is discussed separately.

**Code that becomes dead:** the Home server, IPTV-VOD and Telegram stream paths. Their deletion is decided with their own sections (Home server, Live TV).

### Delete all Telegram code (owner confirmed 2026-11-03)
- **Delete:**
  - `data/telegram/` (8 files, including `TelegramStreamingProxy`, `TelegramSourceResolver` and `TelegramClient`)
  - `ui/screens/settings/telegram/` (screen + ViewModel)
  - `TelegramRepository`
  - The Telegram navigation route (`AppNavigation`, `onNavigateToTelegramSettings`)
- **Remove from:**
  - `StreamRepository` (~2656, 2985, 3026, 3369 + cached-sources code)
  - `StreamSelector`
  - `PlayerViewModel`, `DetailsViewModel`/`DetailsScreen`
  - `SettingsScreen`/`SettingsViewModel`
  - `AuthRepository`, `CloudSyncRepository` (refs only)
  - `util/DataStores.kt` (telegram datastore)
  - `data/model/StreamIntegration.kt` (`TELEGRAM` type + `telegram_native` mapping)
- **Delete the native TDLib library and its Java bindings:**
  - `src/main/jniLibs/*/libtdjni.so`, for all ABIs including x86/x86_64
  - `src/main/tdlib-java/`, plus the `java.srcDir("src/main/tdlib-java")` line (`build.gradle.kts:273`)
  - **Saves about 39 MB** in the universal APK.
- **In `app/build.gradle.kts`, remove:**
  - `TELEGRAM_API_ID`/`HASH` buildConfig fields (90–91) and the secrets checks (573–574, 606–611)
  - **Ktor server** modules (`ktor-server-core/cio/call-logging/host-common`, 478–481), which only the Telegram proxy used (the AI key server goes in Part 5)
  - Keep `ktor-client-android` only if something else still uses it after Trakt and cloud trimming; otherwise remove it too. Grep `io.ktor` to check.
- **Delete Telegram tests:** `TelegramConfigTest`, `TelegramBufferPolicyTest`, `TelegramClientErrorPolicyTest`, `TelegramClientDeviceModelTest`, `TelegramPhraseSearchTest`, `TelegramCachedSourcesTest`.
- **Risk: medium.** Telegram is woven into `StreamRepository` (the Play path). Delete it carefully, guided by the compiler, then run the Play tests on the TV (Part 6 checks).
- **Check:** `grep -ri telegram app/src` returns nothing.

**Risk: low–medium.** This is on the Play path, but it only fixes values that are already like this today. Test on the TV that Play still picks Usenet Ultimate's #0 suitable source and that "Show all sources" still lists UU's streams in UU order.

## Part 7 — Remove Live TV (IPTV) completely: section and all code (owner 2026-11-03)
**Settings:**
- `sections` drops `add("iptv")`, along with its rows:
  - M3U playlists
  - Stalker portals
  - EPG refresh
  - Channel groups
  - Playlist options and actions
- Delete `ui/components/IptvPlaylistModal.kt` and `ui/screens/settings/IptvAccountInfoViews.kt`.
- Remove the IPTV-only rows elsewhere, such as Interface → **Guide rows** (row 47).

**Delete whole files (~45,000 lines):**
- `ui/screens/tv/`: `TvScreen`, `TvViewModel`, `EpgRefreshRequests`, `VisibleGuideData`, and all of `live/` (about 60 files: guide, EPG grid, mini player, HUD, quick zap, catch-up, channel logos, Sports guide…)
- `data/repository/`:
  - `IptvRepository` (12.6k lines), `IptvEpgIndex`, `IptvChannelStore`, `IptvPlaybackUrlResolver`, `IptvGroupOrder`, `IptvUrlNormalizer`, `IptvGuideRequestBudget`, `IptvAccountInfo`, `IptvCloudFields`, `IptvProviderOrder`, `IptvFavoriteChannels`
  - `GuideChannelIdentity`, `GuideBatchTiming`, `ChannelLogoDirectory`
  - `StalkerChannelListLoader`, `StalkerPortalSupport`
- `data/api/StalkerApi.kt`
- `data/model/`: `IptvModels`, `IptvGuideHistory`, `IptvVodSourceIds`, `StalkerVodLink`, `ChannelLogoIndex`
- `network/IptvPlaybackConnections.kt`, `network/IptvProviderRequestGuard.kt`
- `util/IptvGuidePreferences.kt`
- `ui/screens/home/FavoriteTvGuideRefresh.kt`
- `src/main/iptvts-java/` (Java TS extractor, only used by the IPTV HLS extractor) and its `java.srcDir("src/main/iptvts-java")` line in `app/build.gradle.kts`
- **Sports** (already off in PTP; it lives inside Live TV):
  - `SportsRepository`, `SportsAddonRepository`, `SportsMetadataRepository`
  - `data/model/Sports*`
  - The `SPORTS_METADATA_URL` build field
- **All their tests:** about 90 files under `test/…/iptv*`, `tv/`, `tv/live/`, `Stalker*`, `Sports*`, `Guide*`, plus `SettingsIptv*`/`IptvRefreshPolicy`/`IptvPlaylistEpgSettings`.

**Remove the IPTV references from these mixed files:**
- `MainActivity`
- `navigation/AppNavigation` (`Screen.Tv` route + `navigateTopLevel(Screen.Tv…)`)
- `HomeScreen`/`HomeViewModel`/`NetflixHome` (Live TV / favourite channel rows)
- `SettingsScreen`/`SettingsViewModel`
- `ProfileViewModel`
- `StreamRepository` and `StreamIntegrationRepository`: the `IPTV_VOD` integration and provider items. Remove `IPTV_VOD` from `StreamIntegrationType`, which links to Part 6.
- `PlayerViewModel` (~1104, 1281 IPTV VOD lookups)
- `DetailsViewModel` (~2265 VOD)
- `StreamSelector`
- `CloudSyncRepository` (refs only)
- `AuthRepository`
- IPTV/EPG/Stalker/Sports strings in `values/strings.xml`

### Touch-point audit (done 2026-11-03): every non-Live-TV file that references it, and what happens
42 mixed files reference IPTV, Stalker, EPG, Sports or Live TV code. Each is handled as below. **"Recode"** means a replacement is written, not just a deletion.

**⚠ Critical: Continue Watching and watch progress (recode).**
`SportsAddonCapabilities.isLiveStreamOrSportsItem(...)` decides whether playback is **saved to Continue Watching / history**. It's used in:
- `PlayerViewModel` ~7869–8028: progress saving, watched-at-threshold, scrobble
- `TraktRepository` (→ `LocalWatchRepository`) 2776, 2987, 3060
- `WatchHistoryRepository:312`
- `ContinueWatchingMerge:63–64`
- `LauncherContinueWatchingRepository:141, 159`

Deleting Sports would delete it. **Recode:** a small neutral guard, `data/model/ResumableGuard.kt` → `isNotResumable(id, isLiveStream, isAddonNative)`. It keeps only the general safety rules: an explicit live stream, or an invalid id (`id <= 0 && !isAddonNative`). All the call sites switch to it. Without this, Continue Watching could silently stop saving, or save junk entries.

**Play path (recode or trim, behaviour unchanged for Usenet Ultimate):**
- **`StreamRepository`:**
  - Remove `iptvRepository` injection (349); the Xtream-VOD "recover playback" fallback (1936); `hasIptvVodProviders`, `findMovieVodSources` and `findEpisodeVodSources`; VOD prefetch (3065–3762); the Stalker `stalker_vod://` resolve (4487–4493); the `"iptv_vod"` label (2078).
  - Remove the `isSportsOnlyLiveTvAddon` add-on filter (1761, 1802). Neither Usenet Ultimate nor AIOMetadata is a sports add-on, so the filter is a no-op today.
- **`PlayerViewModel`:**
  - Remove: the IPTV-VOD lookups (1104, 1281, 1421), `hasIptvVodProviders` (1555, 1632), `isIptvVodAddonId` checks (113, 667, 6323, 6336), the sports filter (118).
  - Where `hasIptvVodProviders` fed the "no sources configured" message (1632), simplify it to add-ons (+ home server, until Home server is decided).
- **`DetailsViewModel`:**
  - Remove the VOD-enabled flag and `hasIptvVodProviders` (2265–2508, 3636–3637), the `isIptvVodAddonId` check (228) and the sports filter (244).
  - The helper at 3636 becomes `streamingAddonCount > 0 || hasHomeServerConnections`.
- **`AutoPlaySourcePlanner:119`:** remove the Stalker-VOD placeholder branch.
- **`StreamSelector`** (1347, 1473, 1551–1559) and **`StreamSourceAttribution:24`:** remove the IPTV-VOD label/branch.
- **`player/preview/SeekPreviewRequest:12`:** the condition becomes just `memoryClassMb >= 192`.

**Must NOT be removed (generic, not IPTV):**
- `.m3u8` / HLS handling in `PlayerScreen` (7062, 7564), `StreamApi:378`, `FrameRateUtils:79` and `HttpLocalScraperRuntime`. HLS is a normal video format that add-on streams can use.
  - Only the **sports-playlist** heuristic in `PlayerScreen` 7082–7087 goes.
- `androidx.tvprovider` `ChannelLogoUtils` in `LauncherContinueWatchingRepository:253`. That's the Android TV launcher "Play Next" row, not IPTV.
- `TvCheck` (the "This TV" scan).

So the final grep check **excludes** `m3u8` and `tvprovider`.

**App shell and navigation (recode):**
- **`MainActivity`:**
  - Remove the `iptvRepository` warm-up/injection (205–210, 435, 474, 706, 1023).
  - Remove the `iptvFullscreen` state (791–1030) and simplify the fullscreen checks to `isPlayerScreen || overlayFullscreen`. Check that the player still hides the top bar and system bars.
- **`AppNavigation`:**
  - Remove the `Screen.Tv` route (95–97, 368), every `onNavigateToTv` callback (326, 354, 439, 535, 631), `onNavigateToIptvSettings` (390), and the kids-profile tv guard (208).
  - Remove the **launcher deep link into a channel** (291). Check that an old launcher intent with a `channelId` just opens Home and doesn't crash.

**Home, cards, catalogs, collections (trim):**
- `HomeViewModel` (157 refs) / `HomeScreen` (84) / `NetflixHome` (8) / `HomeProfilePreferences` (2): remove the Live TV, favourite-channel and sports rows and their loading. None of these refs is in the Continue Watching pipeline (checked).
- `MediaCard` (136–271): remove the `iptv:` channel-logo and sports-collection tile modes; normal posters stay.
- `CatalogRepository` (490, 654, 1548): remove the sports-catalog filters and the sports/popular-live row ids.
- `CollectionDetailsScreen` (24 refs): remove the `SportsRepository` injection, sports collection catalog and sports item playback. Normal collections stay (hidden in PTP anyway).
- `CategoryViewAllScreen:160`: drop the `isSportsCollection` arg.

**Settings, profile, cloud, misc (trim):**
- `SettingsScreen` (683 refs) / `SettingsViewModel` (378): the whole IPTV section and actions.
- `ProfileViewModel` (7), `AuthRepository` (7), `CloudSyncRepository` (47): refs only.
- `util/DataStores.kt`: the IPTV datastore.
- `util/CrashReportFilter` (54–56, 206): the IPTV error strings.
- `util/AppLanguage` (549, 613): the IPTV label mappings.
- `AboutCreditsDialog`, `OkHttpProvider:320` (comment only), `MediaRepository:36` (unused import), `HomeServerRepository` (1 ref), `AudioSubtitleSyncController` (deleted in Part 5).

**One-time clean-up (recode):** `AccountLocalData.kt:13–33` lists the IPTV cache files and databases (`iptv_cache`, `xtream_vod_disk_cache`, `arvio_iptv_prefs`, `arvio_iptv_channels.db`, `arvio_iptv_epg_index.db`…). Keep those names in a **legacy clean-up list** that deletes them once at startup, so the TVs get their storage back. After that, nothing reads them.

**Not affected:** `TvCheck` (the "This TV" scan) is a different thing despite the name; it stays.

**Risk: medium.** It's a huge deletion, but Live TV is its own screen. The mixed files are the Play path (`StreamRepository`/`PlayerViewModel` IPTV-VOD bits) and Home. Delete guided by the compiler, then run the full Play + Continue Watching checks on the TV.

**Check:** `grep -riE "iptv|stalker|epg|xtream|sports" app/src` returns only the legacy clean-up list. `m3u8` (HLS) and `tvprovider` are expected to stay. The unit tests for Continue Watching / watched (`TraktLocalWatchedSnapshotTest` etc., ported) pass. **On the TV:** play a movie 5 minutes, back out, and it's in Continue Watching. Finish an episode and it's marked watched. Old launcher Play Next entries still open the right title.

## Part 8 — Remove Home server (Plex / Jellyfin / Emby) completely: section and all code (owner 2026-11-03)
**Settings:** in `SettingsScreen.kt`, the `sections` list drops `add("home_server")`, along with its rows and dialogs (connect, sign-in, libraries, catalogs). Remove the matching `SettingsViewModel` actions (114 refs).

**Delete whole files:**
- `data/repository/HomeServerRepository.kt` (3,008 lines), `HomeServerUrl.kt`, `HomeServerLoginFailure.kt`
- **Tests:** `HomeServerMatcherTest`, `HomeServerConnectionRecoveryTest`, `HomeServerSavedLibrariesTest`, `HomeServerPaginationTest`, `HomeServerSourceDiscoveryTest`, `HomeServerLoginFailureTest`, `HomeServerConnectionTest`, `HomeServerImdbRatingTest`, and the Watchlist home-library test (`WatchlistHomeLibraryTest`)

### Touch-point audit (done 2026-11-03)
31 mixed files reference Home server code.

**Play path (recode, behaviour unchanged for Usenet Ultimate):**
- **`PlayerViewModel` (43 refs):**
  - Remove `homeServerAppendJob` and `appendHomeServerSourcesInBackground` (789, 1089–1100, 1268–1271), the `HOME_SERVER` branch (1338–1350) and the `"home_server"` label (666).
  - `hasHomeServerConnections`, the **850 ms home-server autoplay wait** (`HOME_SERVER_AUTOPLAY_WAIT_MS`, 1556) and `homeServerReadyForAutoplay` (1697–1698) go. Today they're all no-ops, since there are no connections. After the change, autoplay never waits on a home server.
  - The "no sources" message (1632–1634, 1648, 1686) becomes add-ons only. Combined with Part 7, its only case is "no add-on streams found". Delete the `player_error_no_streams_media_servers` string.
  - `stream.addonId == HomeServerRepository.ADDON_ID` (113) goes.
- **`DetailsViewModel` (25 refs):**
  - The home-server append job and resolves go (334, 508, 1979–2003, 2147–2149, 2258–2271, 3456–3592), plus the `hasHomeServerConnections` flag in the four Play/state calls (2406–2507).
  - The helper (3635–3637) becomes simply `streamingAddonCount > 0`.
- **`StreamRepository` (11 refs):**
  - Remove `homeServerRepository` injection (351), `hasHomeServerConnections` (3060), `resolveMovieHomeServerSources` (3076–3090), `resolveEpisodeHomeServerSources` (3667–3685) and the `"home_server"` label (2077).
- **`StreamIntegrationRepository`, `StreamIntegration.kt`, `StreamIntegrationsScreen`:** `HOME_SERVER` type and provider items go. After Parts 6, 7 and 8, `StreamIntegrationType` has only `STREMIO_ADDONS`. Simplify the repository to the hardcoded add-on order, or remove it and inline `getUnifiedSourceOrderedIds` into `StreamRepository`; whichever is smaller.
- **`StreamSelector:1302`:** remove the home-server name branch.
- **Seek preview (`ui/screens/player/preview/`):**
  - Delete `HomeServerPreviewParser` (`NativePreviewIndexes.kt:52–115`) and the `JELLYFIN`/`PLEX`/`EMBY` branches in `NativeSeekPreviewAdapter.discover()` (113–127+). Remove those three values from `StreamPreviewKind` (`Models.kt:254`).
  - **Keep** `WEBVTT`, `IMAGE_HLS`, `BIF` and the frame-extraction seek preview. That's what Usenet Ultimate streams use. Update `NATIVE_PREVIEWS.md`.
  - **Test on the TV:** seek preview thumbnails still appear when scrubbing a UU movie.

**Data model (recode):**
- **`Models.kt:71–75`:** remove `isHomeServer`, `homeServerItemId`, `homeServerSourceRef`, `homeServerProvider` and `homeServerImdbId` from `MediaItem`.
- **Fix every reader,** for example:
  - `HomeViewModel:960`: the Continue Watching artwork-repair guard keeps `id <= 0 || isAddonNative`
  - `CollectionDetailsScreen:462`
  - `MediaRepository:505–510`
- Old saved Continue Watching JSON with these fields still loads; Gson ignores unknown fields. History loss is OK anyway.
- **`CatalogModels.kt`:** remove `CatalogSourceType.HOME_SERVER` and its labels (15, 145, 157, 169).

**Catalogs, metadata, Home (trim):**
- **`MediaRepository` (45):** `HomeServerLibraryIdentity` (96), the injection (115), the logo-ref cache (159–182, 505–510) and home-server catalog pages (1003–1018) all go.
- **`CatalogRepository` (28):** the hidden home-server catalog keys and functions (99–334) and `syncHomeServerCatalogs` go.
- **`HomeViewModel` (4):** the injection (384) and `syncHomeServerCatalogs` call (2983) go.
- **`CatalogCloudFields`**, **`CollectionSpotlight`**, **`CollectionDetailsScreen`:** refs only.

**Watchlist (kept for later, so recode):**
- `WatchlistViewModel` (55), `WatchlistScreen` (14), `LibrarySort` (7) and `LibraryPreview` (1) have a "home library" mode (browse Plex/Jellyfin libraries).
- Remove that mode, its provider/library pickers, and `HomeServerLibrarySort` usage.
- The Watchlist keeps only the **local list** (Part 1/step 4 rule). It must still compile and work, so it can be switched back on later.

**Other refs only:**
- `CloudSyncRepository` (11: connection export/import, hidden catalogs)
- `AuthRepository`, `IptvRepository` (deleted in Part 7)
- `AiSubtitleRenderersFactory` and `audiosync/Fft.kt` (deleted in Part 5)
- `OkHttpProvider:260–261`: drop the `X-Plex-Token`/`X-Emby-Token` redaction (harmless either way; drop it for cleanliness)
- Comment-only mentions in `TmdbImageSizing:14` and `Constants:92`
- Home-server strings in `values/strings.xml`

**One-time clean-up:** add the home-server DataStore/prefs file names (saved connections and tokens) to the Part 7 **legacy clean-up list**, so any stored server tokens are deleted from the TVs.

**Risk: medium.** It's woven into Play start-up (the autoplay wait and append jobs) and the seek preview, but all of it is inactive today. Check on the TV:
- Play picks UU's first suitable source, and time-to-first-frame is the same or better (`[PageTiming]`).
- "Show all sources" lists UU in order.
- Seek preview works.
- Continue Watching saves.

**Check:** `grep -riE "homeserver|home_server|plex|jellyfin|emby" app/src` returns only the legacy clean-up list.

## Part 9 — Remove collections completely: built-in, imported and YouTube (owner 2026-11-03)
**What they are:** curated tiles and rails built from TMDB queries, defined in `ArvioCollections.kt`:
- Services (Netflix, Disney+…)
- Genres
- Franchises

They're stored as catalogs of `CatalogKind.COLLECTION` with `status = "collection:<id>"`. Import-a-pack (`CollectionTemplateManifest`), user `CustomCollections` and `YouTubeCollection` use the same machinery and the same screen, so they all go.

**Not the same thing:** the TMDB "belongs to collection" data on a movie (the "View Collection" button). That was already removed from the movie page in 1.0.1. Its leftover code in `DetailsViewModel`/`DetailsScreen` (`collectionItems`, `hasCollectionButton`) is deleted here too, as dead code.

**Delete whole files (~2,350 lines + tests):**
- `data/repository/`: `ArvioCollections.kt`, `CollectionTemplateManifest.kt`, `CustomCollections.kt`, `YouTubeCollection.kt`
- `ui/screens/collections/`: `CollectionDetailsScreen`, `CollectionSpotlight`, `VideoHero`, `CollectionGridLayout`. **But first move the shared grid out (recode, see below).**
- **Tests:** `CollectionCardLoadingTest`, `CustomCollectionsTest`, `CollectionProviderCacheTest`, `ArvioCollectionsTest`, `CollectionLoadingBenchmarkTest`, `CollectionTabPagingTest`, `CollectionSourceLoadingTest`, `YouTubeCollectionTest`, `CollectionPresentationTest`, `CollectionDetailsLoadingTest`
- **Repo-only:** `collections/kaptain-arvio-trimmed.json` (1 MB, not part of the app)

### Touch-point audit (done 2026-11-03)
**⚠ Recode: shared grid.** `ui/screens/home/CategoryViewAllScreen.kt` (the "View all" page for a Home row) imports `CollectionItemsGrid` and `CollectionTab` from `CollectionDetailsScreen.kt` (1005, 111).
- Move both, plus anything they need from `CollectionGridLayout`, into a new neutral file, `ui/screens/home/CategoryItemsGrid.kt`, before deleting the collections folder.
- Update the comments that reference it: `SearchScreen:454`, `DiscoverGridLayout:10`, `TvFocusCoordinator:9`.
- **Test:** "View all" on Continue Watching / Search still opens and scrolls.

**Navigation:** `AppNavigation`: remove the `Screen.CollectionDetails` route (85, 506–514) and both `navigate(CollectionDetails…)` calls (272, 622).

**Home:**
- **`HomeViewModel` (14):** remove `isCollectionItem` (570), `isCollectionTileConfig`/`isCollectionRailConfig` and their filters (1304, 3112, 3164, 3239, 3283–3294), and collection tile building (2745).
- **`HomeScreen` (9):** remove `onNavigateToCollection` and the `collection:` click/long-press branches (518, 1555, 1568, 2247, 3186–3194, 3282).
- **`CategoryViewAllScreen:160`:** remove the `isSportsCollection` arg (also in Part 7).

**Cards and filters:**
- **`MediaCard` (10):** remove the collection-tile mode (`isCollectionTile`, the title overlay, logo scaling). Normal posters stay.
- **`KidsContentFilter` (65, 75, 238):** drop the `isCollectionTile` exemption.

**Catalogs:**
- **`CatalogRepository`:**
  - `ArvioCollections::preserveDisabledDefaults` (150) and the built-in collections toggle (883–889) go.
  - **Recode** the catalog list load so that, without collections, it returns only add-on catalogs.
- **`CatalogModels.kt`:** remove `CatalogKind.COLLECTION`, the collection group fields (127) and `CollectionGroupKind`.
- **`SettingsScreen:10245`:** remove the collection catalog ids from the Catalogs section, plus any "Add collection" / "Import pack" rows. The Catalogs section itself is decided separately.
- **Saved catalog configs** of kind `collection` are dropped on load. History/settings loss is OK.

**Unrelated, kept:**
- `PlayerViewModel` 536 / 6985 "reference collection": a subtitle-cue term, not this feature.
- `SettingsScreen` 8456 / 8527 `connection.collections`: Home server libraries, removed in Part 8.
- `HomeServerRepository`, `SportsRepository`, `LiveTvScreen`, `SportsAddonCapabilities`: deleted in Parts 7 and 8.

**Risk: low–medium.** Collections are hidden in PTP today, since Home only shows Continue Watching. The only shared piece is the "View all" grid, which is moved rather than deleted.

**Check:** `grep -rniE "ArvioCollections|CollectionDetails|CustomCollection|YouTubeCollection|CatalogKind.COLLECTION|\"collection:" app/src` returns nothing.

## Part 10 — Undo "top bar shows only Search, Home, Settings" (owner 2026-11-03)
**Owner:** undo the earlier top-bar restriction, and show every tab that still exists after Parts 1–9.

**Top bar after the change:** **Search · Home · Library · Settings ⚙**
- **Library** (`SidebarItem.WATCHLIST`, label `nav_library`) comes back.
- **Live TV** (`SidebarItem.TV`) is gone, because the code is deleted in Part 7. Remove `TV` from `SidebarItem` (`ui/components/Sidebar.kt:60–65`).
- **Profile slot** stays hidden (one hidden built-in profile, unchanged).
- **Where the code is:**
  - `ui/components/AppTopBar.kt:70` drops the `PERSONAL_BUILD` filter, so every `SidebarItem` is shown.
  - Remove `topBarMaxIndex`/focus-index special cases for the personal build in `SettingsScreen`, `DetailsScreen`, `HomeScreen` and `SearchScreen` so D-pad focus covers the 4 items. Check `hasProfile` logic stays false.

**Library contents after Parts 1, 4 and 8** (`ui/screens/watchlist/WatchlistScreen.kt:68`, `LibrarySection`):
- **Watchlist**: the local list only. Rename the label "Watchlists" → **"Watchlist"**.
- **Calendar**: release calendar from the local watchlist; TMDB dates + TVmaze air times (Part 1/step 4).
- **Removed:** "My lists" (Trakt/MDBList personal lists) and "Homeserver" (Part 8). Delete them from `LibrarySection`.
- Turn the **Watchlist + Calendar code from "kept hidden" into shown and working.** Test:
  - Library opens.
  - The list shows the items, and opening one goes to its page.
  - Calendar loads a month and shows TVmaze times for a known show.

**Scope (owner):** reverse **only** the top-bar restriction. Nothing else hidden earlier comes back; it gets deleted instead (Part 11).

### + Watchlist button on the movie/show page (owner: yes)
- **Button row:** `[ ▶ Play ] [ 👁 Watched ] [ + Watchlist ]`. Shows also get `[ ⏭ Auto-play: Off ]` (Part 2).
- **Behaviour:**
  - The button toggles **+ Watchlist** ↔ **✓ In Watchlist**.
  - Storage is local only: `WatchlistRepository` add/remove/check (`checkInWatchlist`-style local call, no account code).
- **Where the code is:**
  - **`DetailsViewModel.kt:641`:** the personal build currently skips the watchlist read (*"no Watchlist button, so the watchlist is never asked"*). Change it to a **local** check only: one DataStore read, no network. This keeps the 1.0.1 "load only what's shown" rule.
  - **`DetailsScreen.kt`**, personal button row (~2415–2435): add the button.
  - **`personalNextButtonIndex`:** extend it for the new focus order (Play → Watched → Watchlist → Auto-play).
- **Check on the TV:**
  - Add a movie, and it appears in Library → Watchlist.
  - Remove it, and it's gone.
  - A show on the watchlist with an upcoming episode appears in Calendar.

## Part 11 — Delete the things that were only hidden before (owner 2026-11-03: "i wanted that stuff deleted")
Delete these with the same touch-point check as Parts 7–9. Each one is hidden in the personal build today.

**Home** (`HomeScreen:901` filters to `continue_watching`, `:913` hero, `:957` Netflix style):
- **What goes:**
  - Every catalog row: Trending, Popular, add-on catalog rows, Watchlist-on-Home row
  - The hero/billboard
  - `ui/screens/home/NetflixHome.kt` (1,317 lines)
  - Their loading code in `HomeViewModel` (catalog observe/load at 528, 1776, 2387, 2560, 2916–2942)
- **Home then loads only Continue Watching.**
- **Today `HomeViewModel` still loads the hidden catalog rows in the background,** so this also makes Home start faster and stops pointless AIOMetadata/TMDB requests.
- **Keep:**
  - The Continue Watching row and its pipeline (`ContinueWatchingRefreshState`, `ContinueWatchingRowReducer`, the local repo)
  - "View all" (`CategoryViewAllScreen`, shared grid from Part 9)
  - The empty-Home focus-on-top-bar behaviour (1.0.1)
- Remove the `PERSONAL_BUILD` filters once the code is gone.
- The related **Interface** rows (Netflix home style 49, Watchlist on Home 50, card layout 17, guide rows 47) are removed when the Interface section is discussed.

**Movie/show page** (all already never requested in the personal build; `DetailsViewModel` 861–1031, 1862):
- **What goes:**
  - MDBList/external ratings (861), IMDb rating (899), episode ratings, budget (808)
  - Cast + photos (943), `ui/components/PersonModal.kt` + `closePersonModal`
  - More Like This (955), Reviews (985), TMDB Collection (999, also Part 9), streaming services (1031)
  - Trailer (926, also Part 2)
  - The hidden **Sources** button (covered by Advanced → Show all sources)
- **Delete:** their UI sections in `DetailsScreen`, their `DetailsSectionContent` counts and `FocusSection` entries (RATINGS, CAST, REVIEWS, COLLECTION, SIMILAR) in the TV focus coordinator, and their `MediaRepository`/`TmdbApi` calls when nothing else uses them.
- **Keep:**
  - **Search's person search** (`SearchViewModel:897` `getPersonDetails`): Search still finds titles by actor.
  - The personal season-loading behaviour (749/779: load a season when it's picked, warm adjacent seasons).
- **Risk: low–medium.** `DetailsScreen` focus navigation has to be rebuilt for the shorter page: buttons → seasons → episodes. Test the D-pad on a movie and on a show.

**Already decided by the owner** (`PERSONAL_PLAN.md` goals 2–3: "Remove cloud, plugins, Discord, sports" and "Remove profiles entirely"). These were hidden then; now they're **deleted**, with the same touch-point check:
- **Profiles:**
  - Delete the Profiles section, `ui/screens/profile/`, "Who's watching?", the profile slot and kids profiles.
  - Keep exactly one fixed built-in profile id, so the profile-scoped DataStore keys keep working. Recode `ProfileManager` to a constant profile.
- **Plugins:** delete the Plugins section, `core/plugin/`, `ui/screens/plugin/`, `data/local/PluginDataStore`, the `PLUGINS` integration and `FEATURE_PLUGINS_ENABLED`.
- **Cloud sync:**
  - Delete `CloudSyncRepository` (2,159 lines), `CloudSyncCoordinator`, `CloudSyncInvalidationBus`, `worker/CloudSyncWorker`, Supabase (`SupabaseClient`, gotrue/supabase deps), Netlify sync, `AuthRepository` cloud login, the `ENABLE_*CLOUD*` flags, and the cloud URL fields (`cloud-disabled.invalid`).
  - This also removes all the "refs only" trims noted in earlier parts.
- **Discord:** delete `ui/screens/details/discord/` (`DiscordRpcManager`), its init in `MainActivity`, the `arvio://discord` deep link, and `DISCORD_RICH_PRESENCE_AVAILABLE` / the Discord SDK.
- **Sports:** already deleted in Part 7.

**Still open, not decided by the owner yet:** crash QR screen, Cast (Chromecast), self-updater, Firebase (Crashlytics/Analytics libraries), old project folders in the repo.

## Not in this change / notes
- **Losing About & Credits** (it was in Accounts) means there's no on-screen app version any more. It could be added to Settings → Advanced → This TV later if wanted.
- **Still to decide** (see "Open items" at the top).

## Verification
```sh
cd ~/src-untrusted/ARVIO && export ANDROID_HOME=$HOME/Android/Sdk
./gradlew compileSideloadReleaseKotlin -PpersonalBuild=true
./gradlew testSideloadReleaseUnitTest -PpersonalBuild=true   # picker/size/overrides/details must still pass; the 13 pre-existing failures stay as before
./gradlew assembleSideloadRelease -PpersonalBuild=true -x lintVitalSideloadRelease -x lintVitalAnalyzeSideloadRelease
```
- Check the APK is new with `aapt2 dump badging`: versionName 1.0.2, new hash.
- Grep: no `"accounts"` section in personal Settings, and no WorkManager Trakt enqueue reachable.
- On the TV, **only with the owner's OK to install**:
  - Settings shows only the sections left after all parts; Advanced holds Show all sources, This TV, Match Frame Rate, Dolby Vision Compatibility, Volume Boost.
  - Top bar: Search · Home · Library · Settings.
  - Play picks Usenet Ultimate's first suitable source. "Show all sources" lists UU in UU order. Time-to-first-frame (`[PageTiming]`) is the same or better.
  - Play a movie partway, back out: it shows in Continue Watching. Finish an episode: it's marked watched. Mark Watched persists after a restart.
  - + Watchlist adds the title to Library → Watchlist; Calendar shows an upcoming episode with a TVmaze time.
  - Per-show Auto-play: on = next episode starts, off = it doesn't.
  - English subtitles show (embedded and add-on). Seek preview thumbnails show.
  - Opening many movie pages doesn't crash (the Android 11 fix).
  - D-pad on a movie page and a show page: buttons → seasons → episodes.
  - Old launcher Play Next entries open the right title.
- **APK size:** expect roughly −85 MB or more (Telegram TDLib ~39 MB, sherpa-onnx ~46 MB), plus the YouTube player, translations, Ktor server and Firebase if removed.
