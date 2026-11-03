package com.arflix.tv.util

import com.arflix.tv.data.model.StreamBehaviorHints
import com.arflix.tv.data.model.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BestSourcePickerTest {

    private val logged = mutableListOf<String>()

    @Before
    fun setUp() {
        logged.clear()
        BestSourcePicker.log = { logged += it }
    }

    /** TCL Google TV: MT5867, 720p UI mode on a Full HD panel, HDR10 + HLG, no Dolby Vision, no AV1. */
    private val tcl = TvProfile(
        chip = "mt5867",
        avc = CodecSupport(hardware = true, uhd = true),
        hevc = CodecSupport(hardware = true, tenBit = true, uhd = true),
        vp9 = CodecSupport(hardware = true, tenBit = true, uhd = true),
        displayModes = listOf("1280x720@60", "1280x720@50"),
        hdrTypes = listOf(TvProfile.HDR_TYPE_HDR10, TvProfile.HDR_TYPE_HLG),
        screenClass = 1080,
        summary = "MT5867 · 1080p · H.264/HEVC 10-bit · HDR10/HLG · no Dolby Vision"
    )

    /** Hisense 65U6H: 4K, Dolby Vision, HDR10/HDR10+/HLG. */
    private val hisense = TvProfile(
        avc = CodecSupport(hardware = true, uhd = true),
        hevc = CodecSupport(hardware = true, tenBit = true, uhd = true),
        av1 = CodecSupport(hardware = true, tenBit = true, uhd = true),
        vp9 = CodecSupport(hardware = true, tenBit = true, uhd = true),
        dolbyVisionDecoder = CodecSupport(hardware = true, tenBit = true, uhd = true),
        dolbyVisionProfiles = listOf(5, 8),
        displayModes = listOf("3840x2160@60", "1920x1080@60"),
        hdrTypes = listOf(1, 2, 3, 4),
        screenClass = 2160
    )

    /** A 4K TV with HDR10 but no Dolby Vision. */
    private val hdr10Only4k = hisense.copy(
        dolbyVisionDecoder = CodecSupport(),
        dolbyVisionProfiles = emptyList(),
        hdrTypes = listOf(TvProfile.HDR_TYPE_HDR10, TvProfile.HDR_TYPE_HLG)
    )

    private fun uu(quality: String, filename: String, size: String = "") = StreamSource(
        source = filename,
        addonName = "Usenet Ultimate",
        addonId = "com.usenetultimate",
        quality = quality,
        size = size,
        url = "http://nzbdav.local/${filename.hashCode()}",
        behaviorHints = StreamBehaviorHints(filename = filename)
    )

    // ------------------------------------------------------------ parser

    @Test
    fun parse_4kRemuxDvHdr10() {
        val t = BestSourcePicker.parse(uu("4K", "Movie.2023.2160p.UHD.BluRay.REMUX.DV.HDR10.HEVC.TrueHD.Atmos-GRP.mkv", "58.3 GB"))
        assertEquals(2160, t.resolution)
        assertEquals(VideoCodec.HEVC, t.codec)
        assertTrue(t.dolbyVision)
        assertTrue(t.hdr10)
        assertFalse(t.hdr10Plus)
        assertTrue(t.tenBit)
        assertTrue(t.sizeBytes > 58L * 1024 * 1024 * 1024)
    }

    @Test
    fun parse_1080pX264IsPlainAvc() {
        val t = BestSourcePicker.parse(uu("1080p", "Movie.2023.1080p.BluRay.x264.DTS-GRP.mkv", "12 GB"))
        assertEquals(1080, t.resolution)
        assertEquals(VideoCodec.AVC, t.codec)
        assertFalse(t.isHdr)
        assertFalse(t.tenBit)
    }

    @Test
    fun parse_hdr10PlusIsNotPlainHdr10() {
        val t = BestSourcePicker.parseText("2160p", "Show.S01E01.2160p.WEB-DL.DDP5.1.HDR10+.H.265-GRP")
        assertTrue(t.hdr10Plus)
        assertFalse(t.hdr10)
        assertEquals(VideoCodec.HEVC, t.codec)
    }

    @Test
    fun parse_dolbyVisionSpellingsAndCodecs() {
        assertTrue(BestSourcePicker.parseText("", "Movie 2160p DoVi x265").dolbyVision)
        assertTrue(BestSourcePicker.parseText("", "Movie 2160p Dolby Vision HEVC").dolbyVision)
        assertFalse(BestSourcePicker.parseText("", "Movie.1080p.DVDRip.x264").dolbyVision)
        assertEquals(VideoCodec.AV1, BestSourcePicker.parseText("", "Movie.2160p.WEB.AV1.10bit").codec)
        assertEquals(VideoCodec.UNKNOWN, BestSourcePicker.parseText("1080p", "Movie 1080p").codec)
        assertEquals(720, BestSourcePicker.parseText("", "Movie.720p.HDTV").resolution)
        assertTrue(BestSourcePicker.parseText("", "Movie.1080p.x265.10bit").tenBit)
        assertTrue(BestSourcePicker.parseText("", "Movie.2160p.HLG.HEVC").hlg)
    }

    @Test
    fun parse_qualityFieldWinsOverFreeText() {
        // The structured quality label decides the resolution even if a description mentions 4K.
        assertEquals(1080, BestSourcePicker.parseText("1080p", "Encoded from the 4K master").resolution)
    }

    @Test
    fun parse_sizeFromSizeString() {
        assertEquals((15.5 * 1024 * 1024 * 1024).toLong(), BestSourcePicker.parseSize("15,5 GB"))
        assertEquals(700L * 1024 * 1024, BestSourcePicker.parseSize("700 MB"))
        assertEquals(0L, BestSourcePicker.parseSize("unknown"))
    }

    // ------------------------------------------------------------ verdicts

    @Test
    fun dolbyVisionOnly_doesNotSuitNonDvTv() {
        val dvOnly = uu("4K", "Movie.2160p.WEB-DL.DV.H.265-GRP.mkv")
        assertFalse(BestSourcePicker.suits(dvOnly, hdr10Only4k))
        assertTrue(BestSourcePicker.suits(dvOnly, hisense))
    }

    @Test
    fun dolbyVisionWithHdr10_suitsHdr10Tv() {
        val dvHdr10 = uu("1080p", "Movie.1080p.BluRay.DV.HDR10.x265-GRP.mkv")
        assertTrue(BestSourcePicker.suits(dvHdr10, tcl))
        assertTrue(BestSourcePicker.suits(uu("4K", "Movie.2160p.DV.HDR10.HEVC.mkv"), hdr10Only4k))
    }

    @Test
    fun hdrTypeMissingOnTv_doesNotSuit() {
        val noHlg = tcl.copy(hdrTypes = listOf(TvProfile.HDR_TYPE_HDR10))
        assertFalse(BestSourcePicker.suits(uu("1080p", "Show.1080p.HLG.HEVC.mkv"), noHlg))
        val sdrTv = tcl.copy(hdrTypes = emptyList())
        assertFalse(BestSourcePicker.suits(uu("1080p", "Movie.1080p.HDR10.x265.mkv"), sdrTv))
        // HDR10+ falls back to its HDR10 base layer on an HDR10 TV.
        assertTrue(BestSourcePicker.suits(uu("1080p", "Movie.1080p.HDR10+.x265.mkv"), tcl))
    }

    @Test
    fun codecWithoutHardwareDecoder_doesNotSuit() {
        assertFalse(BestSourcePicker.suits(uu("1080p", "Movie.1080p.WEB.AV1.mkv"), tcl))
        val no10Bit = tcl.copy(hevc = CodecSupport(hardware = true, tenBit = false))
        assertFalse(BestSourcePicker.suits(uu("1080p", "Movie.1080p.x265.10bit.mkv"), no10Bit))
        assertTrue(BestSourcePicker.suits(uu("1080p", "Movie.1080p.x265.mkv"), no10Bit))
        // No codec tag: assumed H.264, which suits.
        assertTrue(BestSourcePicker.suits(uu("1080p", "Movie 1080p"), tcl))
    }

    // ------------------------------------------------------------ picking

    private val fourKFirstList = listOf(
        uu("4K", "Movie.2160p.UHD.BluRay.REMUX.DV.HDR10.HEVC.TrueHD.Atmos.mkv", "60 GB"),
        uu("4K", "Movie.2160p.WEB-DL.DV.HEVC.DDP5.1.Atmos.mkv", "20 GB"),
        uu("4K", "Movie.2160p.BluRay.HDR10.x265.mkv", "30 GB"),
        uu("1080p", "Movie.1080p.BluRay.x264.DTS.mkv", "12 GB"),
        uu("1080p", "Movie.1080p.BluRay.x265.10bit.mkv", "6 GB")
    )

    @Test
    fun tcl_picksFirst1080pH264AfterFourKDvSources() {
        assertEquals(3, BestSourcePicker.pickIndex(fourKFirstList, tcl))
        assertTrue(logged.single().startsWith("pick #3/5"))
    }

    @Test
    fun dolbyVisionOnly_isSkippedOnNonDvTv() {
        val list = listOf(
            uu("4K", "Movie.2160p.WEB-DL.DV.HEVC.mkv"),
            uu("4K", "Movie.2160p.WEB-DL.DV.HDR10.HEVC.mkv")
        )
        assertEquals(1, BestSourcePicker.pickIndex(list, hdr10Only4k))
    }

    @Test
    fun nothingSuits_allAboveScreen_playsUusFirstSource() {
        val list = fourKFirstList.take(3)
        assertEquals(0, BestSourcePicker.pickIndex(list, tcl))
        assertTrue(logged.single().contains("nothing suits"))
    }

    @Test
    fun fourKTv_picksFourKFirst() {
        assertEquals(0, BestSourcePicker.pickIndex(fourKFirstList, hisense))
    }

    @Test
    fun emptyList_returnsMinusOne() {
        assertEquals(-1, BestSourcePicker.pickIndex(emptyList(), tcl))
    }

    // ------------------------------------------------------------ failover

    @Test
    fun nextCandidates_areTheFollowingSuitingSourcesInUuOrder() {
        val list = fourKFirstList + uu("720p", "Movie.720p.WEB.x264.mkv")
        assertEquals(listOf(4, 5), BestSourcePicker.nextCandidates(list, tcl, failedIndex = 3))
        assertEquals(listOf(5), BestSourcePicker.nextCandidates(list, tcl, failedIndex = 3, tried = setOf(4)))
        assertEquals(emptyList<Int>(), BestSourcePicker.nextCandidates(list, tcl, failedIndex = 5, tried = setOf(3, 4)))
    }

    @Test
    fun nextCandidates_whenNothingSuits_allSameResolution_followUuOrder() {
        val list = fourKFirstList.take(3)
        assertEquals(listOf(1, 2), BestSourcePicker.nextCandidates(list, tcl, failedIndex = 0))
    }

    // Owner rule (2026-11-02): nothing suits -> TV's own resolution, then lower, then higher.
    private val dvOnlyMixed = listOf(
        uu("4K", "Movie.2160p.WEB-DL.DV.HEVC.mkv"),
        uu("720p", "Movie.720p.WEB-DL.DV.HEVC.mkv"),
        uu("1080p", "Movie.1080p.WEB-DL.DV.HEVC.mkv"),
        uu("480p", "Movie.480p.WEB-DL.DV.HEVC.mkv")
    )

    @Test
    fun nothingSuits_picksTvResolutionFirst() {
        assertEquals(2, BestSourcePicker.pickIndex(dvOnlyMixed, tcl))
        assertEquals(listOf(2, 1, 3, 0), BestSourcePicker.fallbackOrder(dvOnlyMixed, tcl))
        assertEquals(listOf(1, 3, 0), BestSourcePicker.nextCandidates(dvOnlyMixed, tcl, failedIndex = 2))
    }

    @Test
    fun nothingSuits_noTvResolution_goesLowerThenHigher() {
        val list = dvOnlyMixed.filterIndexed { i, _ -> i != 2 } // 4K, 720p, 480p
        assertEquals(1, BestSourcePicker.pickIndex(list, tcl))
        assertEquals(listOf(2, 0), BestSourcePicker.nextCandidates(list, tcl, failedIndex = 1))
    }

    @Test
    fun summary_describesTheTv() {
        val described = TvCapabilities.describe(tcl.copy(summary = ""))
        assertEquals("MT5867 · 1080p · H.264/HEVC 10-bit/VP9 · HDR10/HLG · no Dolby Vision", described)
    }

    // ------------------------------------------------------------ preferred picture format (1.0.1)

    private val hdr10_4k = uu("4K", "Movie.2160p.WEB-DL.HDR10.HEVC-GRP.mkv")
    private val dv_4k = uu("4K", "Movie.2160p.WEB-DL.DV.HEVC-GRP.mkv")
    private val dvHdr10_4k = uu("4K", "Movie.2160p.BluRay.REMUX.DV.HDR10.HEVC-GRP.mkv")
    private val hdr10Plus_4k = uu("4K", "Movie.2160p.WEB-DL.HDR10+.HEVC-GRP.mkv")
    private val dv_1080 = uu("1080p", "Movie.1080p.WEB-DL.DV.HEVC-GRP.mkv")
    private val sdr_1080 = uu("1080p", "Movie.1080p.WEB-DL.x264-GRP.mkv")

    @Test
    fun preference_bestForTv_isTheDefaultPick() {
        val list = listOf(hdr10_4k, dv_4k)
        assertEquals(0, BestSourcePicker.pickIndex(list, hisense))
        assertEquals(0, BestSourcePicker.pickIndex(list, hisense, PictureFormat.BEST_FOR_TV))
    }

    @Test
    fun preference_dv_picksFirstDvAtSameResolution_inUuOrder() {
        val list = listOf(hdr10_4k, sdr_1080, dv_4k, dvHdr10_4k)
        assertEquals(2, BestSourcePicker.pickIndex(list, hisense, PictureFormat.DOLBY_VISION))
    }

    @Test
    fun preference_onA4kTv_resolutionBeatsFormat() {
        // UU: 4K HDR10 first, Dolby Vision only at 1080p -> 4K HDR10 plays.
        val list = listOf(hdr10_4k, dv_1080)
        assertEquals(0, BestSourcePicker.pickIndex(list, hisense, PictureFormat.DOLBY_VISION))
    }

    @Test
    fun preference_onA1080pTv_formatMayWinAcrossResolutions() {
        // 1080p DV-capable TV: 4K is above its screen, default is the 1080p SDR; the DV 1080p matches.
        val dv1080Tv = hisense.copy(screenClass = 1080, displayModes = listOf("1920x1080@60"))
        val list = listOf(sdr_1080, dv_1080)
        assertEquals(1, BestSourcePicker.pickIndex(list, dv1080Tv, PictureFormat.DOLBY_VISION))
    }

    @Test
    fun preference_hybridDvHdr10_countsForBoth() {
        val list = listOf(dv_4k, dvHdr10_4k)
        // HDR10 preference: default (#0, DV only) isn't HDR10; the DV+HDR10 hybrid matches.
        assertEquals(1, BestSourcePicker.pickIndex(list, hisense, PictureFormat.HDR10))
        assertTrue(BestSourcePicker.matchesFormat(BestSourcePicker.parse(dvHdr10_4k), PictureFormat.DOLBY_VISION))
    }

    @Test
    fun preference_hdr10PlusMatchesHdr10Preference() {
        val list = listOf(dv_4k, hdr10Plus_4k)
        assertEquals(1, BestSourcePicker.pickIndex(list, hisense, PictureFormat.HDR10))
    }

    @Test
    fun preference_noMatch_keepsDefault() {
        val list = listOf(hdr10_4k, sdr_1080)
        assertEquals(0, BestSourcePicker.pickIndex(list, hisense, PictureFormat.DOLBY_VISION))
    }

    @Test
    fun preference_neverPicksASourceThatDoesNotSuit() {
        // HDR10-only 4K TV with a DV preference (e.g. stale): DV-only file doesn't suit -> default stays.
        val list = listOf(hdr10_4k, dv_4k)
        assertEquals(0, BestSourcePicker.pickIndex(list, hdr10Only4k, PictureFormat.DOLBY_VISION))
    }

    @Test
    fun preference_failoverTriesPreferredFirst_thenRest_inUuOrder() {
        val list = listOf(dv_4k, hdr10_4k, sdr_1080, dvHdr10_4k)
        val next = BestSourcePicker.nextCandidates(list, hisense, failedIndex = 0, preferred = PictureFormat.DOLBY_VISION)
        assertEquals(listOf(3, 1, 2), next)
    }
}
