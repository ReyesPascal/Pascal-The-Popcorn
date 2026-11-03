# How Play picks a source

Pascal The Popcorn always follows **Usenet Ultimate**: its sources are used in the order it returns them, and the app never re-ranks or filters that list. What the app adds is a choice of *which one to start* when you press Play, based on what your TV can actually show.

## 1. The TV check (first launch)

On first launch the app scans the TV, shows the result and asks if it looks right:

- **Chip** and processor type, **RAM**
- **Video decoders built into the chip:** H.264, HEVC (and 10-bit), AV1, VP9, Dolby Vision
- **Screen:** 4K or 1080p. Many 4K TVs only report a 1080p menu mode, so the app also reads the real panel size the TV publishes; anything below 4K is treated as 1080p (many TVs run a 720p menu on a Full HD panel)
- **HDR:** HDR10, HDR10+, HLG, Dolby Vision
- **Sound passthrough:** Dolby Digital, Dolby Digital Plus, Atmos, TrueHD, DTS

If something is wrong you can correct it yourself (**Something's wrong**): 4K/1080p, each HDR type and Dolby Vision. Corrections are marked "set by you", Play uses them instead of the scan, and **Scan again** never overwrites them (**Reset my changes** does). See or change it any time in **Settings → Playback → This TV**.

You can also choose a **preferred picture format** there — see step 3.

## 2. Does a source suit this TV?

A source **suits** the TV when all of these hold:

| Check | Rule |
|---|---|
| Resolution | Not higher than the screen (no 4K on a 1080p TV — it only wastes bandwidth). |
| Video format | The chip has a hardware decoder for it (HEVC 10-bit needs 10-bit support; AV1 needs AV1). Unknown format → treated as H.264. |
| Dolby Vision | Suits a Dolby Vision TV. On other TVs only if the file also carries an HDR10 layer; Dolby Vision-only files are skipped (they show purple/green). |
| HDR10+ | Suits any HDR10 TV (HDR10+ includes a plain HDR10 picture). |
| HDR10 / HLG | Only if the TV supports that type. |
| Sound | Never disqualifies a source. |

## 3. What Play starts

1. **The first source in Usenet Ultimate's order that suits the TV.**
2. **With a preferred picture format** (Dolby Vision, HDR10+ or HDR10): if that first source isn't in your format, Play keeps walking Usenet Ultimate's list and starts the first source that suits the TV **and** is in your format —
   - on a **4K TV only at the same resolution** as the source from step 1 (resolution beats format: 4K HDR10 wins over 1080p Dolby Vision);
   - on a **1080p TV** at any resolution the TV suits.
   If there's none, the source from step 1 plays. "Dolby Vision + HDR10" files count for both formats; HDR10+ files count for HDR10. **Best for this TV** (the default) skips this step.
3. **If none suits**, it still plays one, chosen by resolution:
   1. the TV's own resolution (1080p on a Full HD TV, 4K on a 4K TV),
   2. then lower — 720p before 480p,
   3. then higher — closest first.
   Within each resolution, Usenet Ultimate's order decides.

## 4. If a source doesn't start

- The app waits **at least 30 seconds** (up to about 42) for the first picture — a first play from Usenet takes time while the server fetches the file.
- If it still doesn't start, it moves on to the **next suitable source** (sources in your preferred format first, same rule as above) (or, when none suits, the next by the resolution order above).
- **Up to 3 sources in total**, then it shows the error with **Retry**.

## 5. Show all sources

**Settings → Playback → Show all sources** turns this off. Play then opens the full list in Usenet Ultimate's order, and whatever you pick plays exactly as chosen.
