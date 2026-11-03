# How Play picks a source

Pascal The Popcorn always follows **Usenet Ultimate**: its sources are used in the order it returns them, and the app never re-ranks or filters that list. What the app adds is a choice of *which one to start* when you press Play, based on what your TV can actually show.

## 1. The TV scan (once)

On first launch the app checks the TV once and saves the result:

- **Chip** and processor type, **RAM**
- **Video decoders built into the chip:** H.264, HEVC (and 10-bit), AV1, VP9, Dolby Vision
- **Screen:** 4K if the TV offers a 3840-wide mode, otherwise treated as 1080p (many TVs run a 720p menu on a Full HD panel)
- **HDR:** HDR10, HDR10+, HLG, Dolby Vision
- **Sound passthrough:** Dolby Digital, Dolby Digital Plus, Atmos, TrueHD, DTS

See it, or run it again, in **Settings → Playback → This TV**.

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
2. **If none suits**, it still plays one, chosen by resolution:
   1. the TV's own resolution (1080p on a Full HD TV, 4K on a 4K TV),
   2. then lower — 720p before 480p,
   3. then higher — closest first.
   Within each resolution, Usenet Ultimate's order decides.

## 4. If a source doesn't start

- The app waits **at least 30 seconds** (up to about 42) for the first picture — a first play from Usenet takes time while the server fetches the file.
- If it still doesn't start, it moves on to the **next suitable source** (or, when none suits, the next by the resolution order above).
- **Up to 3 sources in total**, then it shows the error with **Retry**.

## 5. Show all sources

**Settings → Playback → Show all sources** turns this off. Play then opens the full list in Usenet Ultimate's order, and whatever you pick plays exactly as chosen.
