# PTP 1.0.2.1 — emergency logging update (DRAFT, not approved)

Why: 2026-11-06 the logger caught a freeze after skip-forward on the Hisense, but PTP logged nothing
after the seek, so the cause can't be seen. This update only adds logs ("the more the better" — owner).
No behaviour changes.

## How logs reach PTP-Logger (nothing lost)
- PTP writes every event as one JSON line, tag `PTPDiag`, to:
  a) the TV's system log (logcat) → PTP-Logger already streams it live from the Hisense;
  b) a rolling file on the TV (`/sdcard/Android/data/com.arvio.tv/files/ptpdiag/`, 50 MB cap,
     oldest deleted first). PTP-Logger pulls new lines over adb when it (re)connects, so events
     survive Brain off, wifi drops and TV reboots. Duplicates skipped by event id.
- No network code added to the TV app. UU token in URLs masked (`<token>`).
- Crash/freeze-safe: crash handler and freeze dumps write the file first.

## Events (all of these)
Playback
1. Player state + play/pause, with position, buffered position, buffer seconds ahead.
2. Seek: from → to, how long it took, what was loaded next. Skip/jump keys too.
3. Every network load the player does: byte range, bytes, time, speed, HTTP code, retries, errors, cancels.
4. Player errors: code + full cause chain + source/host. Failover steps + reason.
5. Stall watchdog: buffering > 10 s or position stuck 10 s → `stall` with buffer, last load,
   bandwidth, decoder state + player thread stack. Repeats every 30 s while stuck.
6. Heartbeat every 10 s while playing: position, buffer, bitrate estimate, dropped frames, decoder.
7. Tracks/format: video codec, resolution, fps, HDR/DV mode, frame-rate match result; audio codec,
   channels, passthrough; subtitles loaded/failed; audio underruns; decoder init/release.
Sources
8. Stream fetch: each addon request (time, status, count), full source list (top 20 with score +
   why), source picked (auto/by hand), "No sources" with reason, cache hits.
App
9. Screens opened, remote key presses (key name only), Play pressed, settings at start (snapshot).
10. Every app HTTP call (TMDB, UU, sync): host, path (masked), status, time, error.
11. Lifecycle: start (version, TV model, Android, cold/warm), resume/pause, network change
    (wifi RSSI, link speed), low memory, app memory + CPU every 30 s.
12. Main-thread freeze watchdog: UI blocked > 2 s → stack dump (catches ANRs/UI hangs).
13. Crash: full stack + last 50 events, written before the app dies.
Logger side
14. PTP-Logger: `PTPDiag` lines → `ptp-<event>` events (source `ptp`), pull the TV file on connect,
    "PTP" filter on the page.

## Cost check
- Heartbeats/timers only while playing; logging on a background thread; rate limit 50 lines/s.
- Measure on the Hisense: startup time and CPU while playing vs 1.0.2 (must be no worse).

## Version / build / install
- versionName 1.0.2.1, versionCode 325, same signing key (installs over 1.0.2, keeps settings).
- Build: small Codex tasks via ptp-wt orchestrator, compile + tests per task, then sideload release APK.
- Install: Hisense only, only after owner OK. Test: Primetime + skip forward, read the logger.

## Owner decisions 2026-11-06
- Logging only (no freeze fix). Version 1.0.2.1 OK.
- Integration with PTP-Logger = item 14 (built in the same run, deployed to Brain before install).
- Estimate given: ~3–4 h unattended (≈13 small app tasks on 2 Codex lanes + logger task + APK build).

## FAST PLAN (proposed 2026-11-06, replaces 13-task build) — target ~1–1.5 h
Why it's fast: logging is add-only, so tasks are mostly NEW files + 1–3 hook lines each -> no merge
conflicts, all run at the same time, one compile at the end.
- 5 tasks in parallel (needs 4 Codex at once instead of 2 + 1 logger task):
  A. Core: PtpDiag helper (logcat + rolling file + token mask + rate limit), crash handler,
     main-thread freeze watchdog, lifecycle/memory/CPU/wifi, settings snapshot. (items 1, 9 part, 11–13)
  B. Player: ONE ExoPlayer AnalyticsListener file (gives state, seeks, every load, errors, formats,
     dropped frames, bandwidth, underruns, decoder) + stall watchdog + heartbeat. Hook = 1 line in
     PlayerViewModel. (items 2–7)
  C. Network + sources: one OkHttp interceptor (every app HTTP call) + stream fetch / source list /
     pick / failover / "No sources" lines in StreamRepository + DetailsViewModel. (items 4 part, 8, 10)
  D. UI: screen opened + remote keys (key name) in one place (MainActivity/nav). (item 9)
  E. PTP-Logger: PTPDiag -> ptp-<event>, pull TV file on adb connect, PTP filter. (item 14)
- Per task: compileKotlin only (warm Gradle daemon), no full test run.
- Merge A–D (expect no conflicts), ONE compile + unit tests, then the sideload release APK.
- Timeline: tasks ~30–40 min (parallel) -> merge + compile ~15 min -> APK ~15 min (+ ~15 min if a fix round).
- Owner 2026-11-06: FAST PLAN OK, 4 Codex at once allowed for this build. If owner says Codex is low ->
  pause queue, plan to finish the rest with Claude agents (ptp-worker, Sonnet medium per agent file).
- Owner trigger phrase: "codex low" -> pause Codex queue (let running jobs finish), save STATUS, propose Claude-agent finish plan.
