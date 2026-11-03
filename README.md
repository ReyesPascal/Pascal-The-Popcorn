# Pascal The Popcorn — source

Source code for **Pascal The Popcorn**, a personal Android TV streaming app. This `source` branch is a snapshot of the
working code; the app's description, changelog and APK releases live on `main` and in Releases.

> **Private repo.** The code contains the owner's personal addon addresses and their access tokens
> (`app/src/main/kotlin/com/arflix/tv/data/repository/BundledAddons.kt`). Rotate them before this repo is ever shared
> or made public.

## What the app does
- **Play follows Usenet Ultimate:** Play walks Usenet Ultimate's source list in its own order and starts the first
  source that suits this TV. The list is never re-sorted or filtered.
- **TV check on first launch:** scans the screen (4K / 1080p), HDR formats, Dolby Vision, video decoders and audio;
  the owner can correct results ("set by you") and pick a preferred picture format.
- **Preferred picture format:** with a preference set, Play picks the first suitable source in that format — on a 4K
  TV only at the same resolution as the default pick.
- **Lean movie page:** only backdrop, logo, info line, a whole-sentence description, Play and Watched are shown or loaded.
- Startup waits at least 30 s for a source, retries up to 3 sources, no profiles, no cloud.

Details: [`HANDOFF.md`](HANDOFF.md) (current state, build, rules), [`PLAN-1.0.1.md`](PLAN-1.0.1.md) (1.0.1 plan and the
1.0.2 list), [`PERSONAL_PLAN.md`](PERSONAL_PLAN.md) (original goals).

## Build
Needs the Android SDK (API 36) and JDK 17+.

1. Create `secrets.properties` in the repo root (git-ignored) with your TMDB key — see `secrets.defaults.properties`
   for the key names.
2. Build:
   ```sh
   export ANDROID_HOME=$HOME/Android/Sdk
   ./gradlew assembleSideloadRelease -PpersonalBuild=true -x lintVitalSideloadRelease -x lintVitalAnalyzeSideloadRelease
   ```
   Output: `app/build/outputs/apk/sideload/release/app-sideload-release.apk` (universal: arm64-v8a + armeabi-v7a).
   `-Pabis=armeabi-v7a` builds 32-bit only.
3. Quick checks:
   ```sh
   ./gradlew compileSideloadReleaseKotlin -PpersonalBuild=true
   ./gradlew testSideloadReleaseUnitTest -PpersonalBuild=true --tests 'com.arflix.tv.util.*'
   ```

## On a TV
- Install: `adb -s <tv> install Pascal-The-Popcorn-X.Y.apk`, then `adb -s <tv> shell cmd package compile -m speed <package>` (the package is `applicationId` in `app/build.gradle.kts`).
- Useful log lines: `adb -s <tv> logcat -v time | grep -E "\[(TvScan|BestSource|PageTiming|PlaybackDiag)\]"`.

Package and code names inside the source are unchanged from the codebase it started from, so installed builds keep
upgrading in place.
