package com.arflix.tv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TvOverridesTest {
    private val hisense = TvProfile(
        chip = "mt9638",
        screenClass = 1080,
        avc = CodecSupport(hardware = true),
        hevc = CodecSupport(hardware = true, tenBit = true),
        dolbyVisionDecoder = CodecSupport(hardware = true, tenBit = true),
        hdrTypes = listOf(
            TvProfile.HDR_TYPE_DOLBY_VISION,
            TvProfile.HDR_TYPE_HDR10,
            TvProfile.HDR_TYPE_HLG,
            TvProfile.HDR_TYPE_HDR10_PLUS
        )
    ).let { it.copy(summary = TvCapabilities.describe(it)) }

    private val plain1080 = TvProfile(
        screenClass = 1080,
        avc = CodecSupport(hardware = true),
        hdrTypes = listOf(TvProfile.HDR_TYPE_HDR10)
    ).let { it.copy(summary = TvCapabilities.describe(it)) }

    private fun apply(p: TvProfile, o: TvOverrides) = TvOverridesStore.applyOverrides(p, o)

    @Test fun noCorrectionsReturnsScanUnchanged() {
        assertSame(hisense, apply(hisense, TvOverrides()))
        assertSame(hisense, apply(hisense, TvOverrides(preferredFormat = PictureFormat.DOLBY_VISION)))
    }

    @Test fun screenClassOverrideReplacesScan() {
        val p = apply(hisense, TvOverrides(screenClass = 2160))
        assertEquals(2160, p.screenClass)
        assertTrue(p.summary.contains("4K"))
        assertEquals(1080, apply(p, TvOverrides(screenClass = 1080)).screenClass)
    }

    @Test fun hdrOverridesAddAndRemoveTypes() {
        val p = apply(hisense, TvOverrides(hdr10Plus = false, hlg = false))
        assertFalse(p.displayHdr10Plus)
        assertFalse(p.displayHlg)
        assertTrue(p.displayHdr10)
        assertTrue(p.hasDolbyVision)

        val q = apply(plain1080, TvOverrides(hdr10 = false, hdr10Plus = true, hlg = true))
        assertFalse(q.displayHdr10)
        assertTrue(q.displayHdr10Plus)
        assertTrue(q.displayHlg)
        assertEquals(q.hdrTypes.sorted(), q.hdrTypes)
        assertTrue(q.summary.contains("HDR10+"))
    }

    @Test fun dolbyVisionOnAddsDisplayTypeAndDecoder() {
        assertFalse(plain1080.hasDolbyVision)
        val p = apply(plain1080, TvOverrides(dolbyVision = true))
        assertTrue(p.displayDolbyVision)
        assertTrue(p.dolbyVisionDecoder.hardware)
        assertTrue(p.hasDolbyVision)
        assertTrue(p.summary.contains("Dolby Vision"))
        assertFalse(p.summary.contains("no Dolby Vision"))
    }

    @Test fun dolbyVisionOffRemovesDisplayType() {
        val p = apply(hisense, TvOverrides(dolbyVision = false))
        assertFalse(p.displayDolbyVision)
        assertFalse(p.hasDolbyVision)
        assertTrue(p.summary.contains("no Dolby Vision"))
        // Other HDR types are untouched.
        assertTrue(p.displayHdr10 && p.displayHdr10Plus && p.displayHlg)
    }

    @Test fun overridesNeverTouchOtherFields() {
        val p = apply(hisense, TvOverrides(screenClass = 2160, dolbyVision = false))
        assertEquals(hisense.hevc, p.hevc)
        assertEquals(hisense.chip, p.chip)
        assertEquals(hisense.dolbyVisionProfiles, p.dolbyVisionProfiles)
    }

    @Test fun hasCorrectionsIgnoresPreferredFormat() {
        assertFalse(TvOverrides(preferredFormat = PictureFormat.HDR10).hasCorrections)
        assertTrue(TvOverrides(hlg = false).hasCorrections)
    }

    @Test fun supportedFormatsHisenseOrder() {
        assertEquals(
            listOf(PictureFormat.BEST_FOR_TV, PictureFormat.DOLBY_VISION, PictureFormat.HDR10_PLUS, PictureFormat.HDR10),
            TvOverridesStore.supportedFormats(hisense)
        )
    }

    @Test fun supportedFormatsOnlyWhatTheTvSupports() {
        assertEquals(listOf(PictureFormat.BEST_FOR_TV, PictureFormat.HDR10), TvOverridesStore.supportedFormats(plain1080))
        assertEquals(listOf(PictureFormat.BEST_FOR_TV), TvOverridesStore.supportedFormats(TvProfile()))
    }

    @Test fun dolbyVisionFormatNeedsDecoderToo() {
        // A DV display path without a hardware DV decoder is not real Dolby Vision.
        val displayOnly = hisense.copy(dolbyVisionDecoder = CodecSupport())
        assertFalse(PictureFormat.DOLBY_VISION in TvOverridesStore.supportedFormats(displayOnly))
    }

    @Test fun supportedFormatsFollowCorrections() {
        val p = apply(plain1080, TvOverrides(dolbyVision = true, hdr10 = false))
        assertEquals(listOf(PictureFormat.BEST_FOR_TV, PictureFormat.DOLBY_VISION), TvOverridesStore.supportedFormats(p))
    }
}
