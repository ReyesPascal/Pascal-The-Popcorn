package com.arflix.tv.data.repository

/**
 * External rating shown on the movie page. Nothing fills it any more (its source was
 * removed in 1.0.2); it only remains so the movie-page ratings UI still compiles until
 * that UI is deleted too. Delete this file together with that UI.
 */
data class MdbExternalRating(
    val source: String,
    val label: String,
    val value: String
)
