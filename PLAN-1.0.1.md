# Pascal The Popcorn 1.0.1 — plan (agreed with owner 2026-11-02)

Status: **planned, not started** (only the 4K detection draft in `TvCapabilities.kt` + `TvCapabilitiesSizeTest.kt` exists, uncommitted, not compiled).
Rules that still apply: always follow Usenet Ultimate (never re-rank/filter); never install on a TV without the owner's OK; never push this repo.

## 1. First-Play "No streams found" bug (upstream bug)
Cause (confirmed from log + code): the movie page's background Usenet Ultimate lookup is cancelled when Play closes the page
→ `fetchMovieStreamsFromAddon` catches the cancellation as a normal failure → "0 sources" saved as the final answer
(15 s, also persisted) → player reads that cache 88 ms later → "No streams found". Retry works once the cache expires.
Fix:
- Rethrow `CancellationException` in the movie and episode addon fetch paths (not swallowed as failure).
- Never cache/persist a result from a cancelled search (check `isActive` before caching).
- Test: open a movie, press Play immediately → plays first time.

## 2. 4K detection (draft exists)
Hisense reports only 1920x1080 display modes (UI layer); panel is 4K. Order: display mode ≥3840 → `vendor.display-size` /
`sys.display-size` → Media3 display size → `ro.vt.vcom_display_resolution=4k` → model name 4K/UHD. Decoder alone is not proof.
`SCHEMA_VERSION` 2 forces a re-scan after update. `[TvScan]` line on System.err for adb checks.

## 3. First-run TV check (new screens)
Neutral wording, plain dark style, no "?" items.
1. **Welcome:** PTP logo + name, "First, let's check what this TV can play, so Play always picks the best source for it." Button: **Scan this TV** (no skip).
2. **Scanning:** ~2 s minimum, items tick off: Screen → HDR → Video → Audio.
3. **Results:** "Here's what this TV can play" — Screen (4K/1080p), HDR (Dolby Vision, HDR10, HDR10+, HLG), Video (H.264, HEVC 10-bit, AV1, VP9), Audio (only what the TV reports), small device line (name, chip, RAM, Android). Buttons: **Looks right** → Home, **Something's wrong** → edit.
4. **Edit:** switches only for 4K/1080p, each HDR type, Dolby Vision. Saved separately from the scan as owner overrides; Play uses the override; marked "set by you"; re-scans never overwrite them. Buttons: **Save**, **Reset my changes**, **Scan again**.
4b. **Preferred picture format** row on Results/edit: "Best for this TV" (default) + only the formats this TV supports
   (Hisense: Dolby Vision, HDR10+, HDR10). Saved with the owner overrides.
   **Play rule (Usenet Ultimate list is the only pool, order never changed, list never filtered for display):**
   walk UU's list top to bottom; default pick = first entry that suits this TV (unchanged). If a preferred format is set and
   the default pick isn't in it, play the first entry that suits the TV **and** matches the format —
   on a **4K TV only at the same resolution as the default pick** (resolution beats format);
   on a **1080p TV any resolution the TV suits** (format may win). None found → default pick.
   "DV + HDR10" hybrids match both DV and HDR10; HDR10+ matches an HDR10 preference. Failover (3 tries) follows the same rule.
   Picker unit tests for all of the above.
5. **Settings → Playback → This TV** opens the same Results screen (edit, Scan again, Reset).
- Shown once per install; existing installs see it once after the update.

## 4. Movie page (Details) — show and load ONLY:
Shown: backdrop (smaller ~1280 wide image), title logo, info line (genre | date | runtime | age rating), description
(whole sentences that fit, always ends on a full stop, measured on screen — never "…"), **big labeled ▶ Play** (focused by default), **👁 Watched**.
TV shows also keep seasons + episodes.
Loaded but not shown (needed): IMDb ID (UU sources), resume point, watched state, background UU lookup (owner chose to keep, safe after fix 1).
**Removed and never requested:** IMDb rating, MDBList ratings, budget, trailer, Sources button, View Collection, Watchlist button,
cast + photos, More Like This, Collection, Reviews, watchlist check, streaming services, and any images for these.
Manual source pick = Settings → Playback → Show all sources (owner OK).

## 5. Speed
- Timer in log (System.err): page opened → page drawn; Play pressed → first video frame. Compare 1.0 vs 1.0.1 on the Hisense.
- Smaller backdrop image (see 4).
- Background UU lookup starts immediately (no 1 s wait) once fix 1 is in.
- After owner installs: `adb -s <verified Hisense> shell cmd package compile -m speed com.arvio.tv` (re-run after every install; undo: `cmd package compile --reset com.arvio.tv`).
- Later (not 1.0.1): preload page data when a Continue Watching card is focused.

## 6. Home focus
Empty Home → focus starts on the top bar (Left/Right reach Search / Settings).

## 7. Build and hand over
versionName 1.0.1 / versionCode 323. Compile, unit tests (BestSourcePicker, TvCapabilities), build universal APK, verify new
(timestamp, sha256, `aapt2 dump badging`). **Stop** — owner fresh-installs. GitHub release only when owner says.

## Contract and file ownership (agents)
Base commit: the commit that adds this section. Shared types/signatures: `app/src/main/kotlin/com/arflix/tv/util/TvOverrides.kt`
(`PictureFormat`, `TvOverrides`, `TvOverridesStore`) — signatures fixed; Agent A writes the bodies.
- **Lead:** `StreamRepository.kt` (fix 1), `BestSourcePicker.kt` + `BestSourcePickerTest.kt` (preference rule 4b),
  `PlayerViewModel.kt` (use `TvOverridesStore.effective` + `overrides().preferredFormat`, failover, Play timer),
  `PlayerScreen.kt` (first-frame timer if needed), `HomeScreen.kt` (fix 6), version bump, merge, final build.
- **Agent A (first-run TV check):** `TvCapabilities.kt`, `TvOverrides.kt` bodies, new files under
  `ui/screens/tvcheck/`, `navigation/AppNavigation.kt`, `MainActivity.kt`, `SettingsScreen.kt` (This TV row only),
  `res/values/strings.xml` (new strings only, appended), new unit tests under `app/src/test/.../util/`.
- **Agent B (movie page + speed):** files under `ui/screens/details/` only (DetailsScreen.kt, DetailsViewModel.kt and
  other details-only files). No strings.xml changes (reuse existing strings). No shared repository/model changes —
  stop calls from the details side; resize backdrop URLs inside details code.
- Nobody else edits another owner's files. Nobody pushes. Nobody installs on a TV.

# 1.0.2 list (owner-approved items, not started)
1. **Player takes over the movie page's background Usenet Ultimate lookup** instead of cancelling it and searching
   again. Evidence (Hisense, 2026-11-02 23:13, movie 155): Play pressed 2 s after the page opened; the background
   lookup was cancelled, the player searched from zero → UU answered after 11.0 s (658 results), resolve 1.9 s,
   first frame 1.7 s = 14.7 s Play→video. Idea: share the in-flight search (same title/cache key) between the details
   prefetch and the player so Play waits only for the remaining time; never cancel it on page close when Play is the
   reason. Must still follow UU order and never cache cancelled/partial results.
2. **Merge The Great British Bake Off into one show (IMDb tt1877368, seasons 1–17).** TMDB splits it: 34549 (BBC,
   seasons 1–7, IMDb tt1877368) and 87012 (Channel 4, "seasons" 1–10, **no IMDb ID** → Play could never find sources).
   Verified 2026-11-02 by air date vs Cinemeta: 87012 S1–S10 = IMDb S8–S17, same episode numbers, all 100 episodes;
   34549 S1–S7 = IMDb S1–S7 for regular episodes; the trailing Masterclass/Revisited extras in 34549 S2–S7 are IMDb
   season 0 (match those by air date). Usenet Ultimate check: tt1877368:17:6 = 35 sources (the 2026 Pastry Week);
   tt1877368:10:6 = the 2019 episode (so never reuse TMDB numbers with the IMDb ID).
   Design: small labelled merge table (only Bake Off for now); opening either entry shows one show with seasons 1–17
   (1–7 from 34549, 8–17 from 87012 S1–10); Play asks UU for tt1877368 with IMDb numbers; extras by air date;
   87012 hidden from search / opens the merged show. Trade-off accepted: history from the old 87012 listing doesn't carry over.
   Lead does it alone (one focused change).
