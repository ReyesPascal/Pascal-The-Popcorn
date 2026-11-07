package com.arflix.tv.data.repository

/**
 * Stremio addons bundled into this personal build (local fork, not for distribution: the URLs carry
 * personal configuration IDs). [StreamRepository.seedBundledAddons] installs each one once on first
 * start; an addon the user later removes stays removed.
 */
internal object BundledAddons {
    val MANIFEST_URLS: List<String> = listOf(
        // Usenet Ultimate (streams), self-hosted on brain-1, reached over Tailscale.
        "https://brain-1.tail1986dd.ts.net:8443/stremio/afc23524-989d-4cfb-910e-30ff1dbec163/manifest.json",
        // AIOMetadata on ElfHosted (catalogs and metadata).
        "https://aiometadata.elfhosted.com/stremio/9229d15d-68c5-4c66-bdf1-22d5bc1506ae/manifest.json",
    )
}
