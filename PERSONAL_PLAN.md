# Personal build v2 — plan and shared contract

Branch `personal-tv-aware` (from `personal-bundled-addons` 2a0d40c). Build: ONE universal 32+64-bit APK, warm daemon, no lint: `./gradlew assembleSideloadRelease -PpersonalBuild=true -x lintVitalSideloadRelease -x lintVitalAnalyzeSideloadRelease` (no `-Pabis`). Lead runs compile check first in the same daemon. No TV installs.

## Owner goals (strict)
1. Usenet Ultimate (UU) startup: at least 30 s before ARVIO gives up on a source.
2. Remove **cloud, plugins, Discord, sports** and every user-visible mention of them.
3. Remove **profiles** entirely: one hidden built-in profile, never "Who's watching?", no profile UI/text.
4. Defaults: audio = original language, subtitles **on**, subtitle language **English**, next-episode autoplay **off**.
5. Top bar: Search, Home, Settings only. Home shows only the Continue Watching row. **Settings keeps every remaining section** (only the removed features disappear).
6. One-time TV capability scan on first launch (chip/decoders, 8/10-bit, max resolution, display modes, HDR types, Dolby Vision, audio passthrough Atmos/DD+, RAM). Saved. "Re-scan TV" in Settings.
7. Play = start the **first source in UU's order that suits this TV**; if none suit, play UU's first source anyway; if a source fails to start, auto-try the next suitable one (max 3). Setting "Show all sources" (default off) → Play opens the full list instead.
8. Never re-rank or filter UU's list itself. The list (when shown) stays in UU order.
9. Rename to **Pascal The Popcorn** in all user-visible text (all languages). Art/logos (TV banner, launcher icon, loading logo, text logo) use a **PTP** monogram with a popcorn motif, clean on dark. Internal package id `com.arvio.tv` unchanged. **Lead does this as a final pass after merging; agents must not rename anything.**

## Shared contract (both agents must use these names exactly)
- `com.arflix.tv.util.PersonalPrefs` (exists, lead-owned): `SHOW_ALL_SOURCES`, `showAllSourcesFlow(context)`, `showAllSources(context)`, `setShowAllSources(context, value)`.
- `BuildConfig.PERSONAL_BUILD: Boolean` (lead adds in build.gradle.kts).
- Agent B provides, in `com.arflix.tv.util.TvCapabilities.kt`:
  ```kotlin
  data class TvProfile(/* fields */ val summary: String /* one short human line, e.g. "MT5867 · 1080p · H.264/HEVC 10-bit · HDR10/HLG · no Dolby Vision" */)
  object TvCapabilities {
      fun cached(context: Context): TvProfile?          // null until first scan
      suspend fun ensureScanned(context: Context): TvProfile   // scans once, then returns cache
      suspend fun rescan(context: Context): TvProfile          // forced re-scan, overwrites cache
  }
  ```
- Agent A's Settings rows call only `TvCapabilities.cached/rescan` and `PersonalPrefs.*`.

## File ownership (do not edit files you don't own)
| Owner | Files |
|---|---|
| Lead | `app/build.gradle.kts`, `util/Constants.kt`, `util/PersonalPrefs.kt`, `MainActivity.kt`, `navigation/AppNavigation.kt`, `ui/components/AppTopBar.kt`, `ui/screens/home/**`, `ui/screens/profile/**`, `ui/screens/login/**`, `ArflixApplication.kt`, `data/repository/ProfileRepository.kt`, `util/DeviceType.kt` |
| Agent A | `ui/screens/settings/SettingsScreen.kt`, `ui/screens/settings/SettingsViewModel.kt`, `ui/screens/settings/SettingsPreferenceWriter.kt`, `res/values/strings.xml` (may ADD strings; don't delete strings used elsewhere) |
| Agent B | NEW `util/TvCapabilities.kt`, NEW `util/BestSourcePicker.kt`, `ui/screens/player/PlayerScreen.kt`, `ui/screens/player/PlayerViewModel.kt`, `ui/screens/player/PlayerSourceDiscovery.kt`, `ui/screens/details/DetailsViewModel.kt`, `ui/screens/details/AutoPlaySourcePlanner.kt`, `data/model/UsenetUltimateFirst.kt`, `ui/components/StreamSelector.kt` |

Paths relative to `app/src/main/kotlin/com/arflix/tv/` unless shown.
