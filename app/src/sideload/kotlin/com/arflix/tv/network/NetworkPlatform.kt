package com.arflix.tv.network

import android.content.Context
import okhttp3.OkHttp

internal fun initializeNetworkPlatform(context: Context) {
    // Initialize OkHttp before Hilt or DNS can load its Android assets.
    OkHttp.initialize(context)
}
