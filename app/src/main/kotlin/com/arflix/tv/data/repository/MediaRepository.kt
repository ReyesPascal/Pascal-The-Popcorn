package com.arflix.tv.data.repository

import com.arflix.tv.data.merge.ShowMergeTable
import com.arflix.tv.data.merge.getMergedTvDetails
import com.arflix.tv.data.merge.getMergedTvSeason
import android.content.Context
import com.arflix.tv.R
import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.api.TmdbEpisode
import com.arflix.tv.data.api.TmdbExternalIds
import com.arflix.tv.data.api.TmdbImage
import com.arflix.tv.data.api.TmdbListResponse
import com.arflix.tv.data.api.TmdbMediaItem
import com.arflix.tv.data.api.TmdbMovieDetails
import com.arflix.tv.data.api.TmdbPersonDetails
import com.arflix.tv.data.api.TmdbSeasonDetails
import com.arflix.tv.data.api.TmdbTvDetails
import com.arflix.tv.data.api.TmdbWatchProviderRegion
import com.arflix.tv.data.api.StremioMetaPreview
import com.arflix.tv.data.model.Addon
import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.CatalogKind
import com.arflix.tv.data.model.CatalogSourceType
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.CatalogQuerySourceConfig
import com.arflix.tv.data.model.CatalogQuerySourceKind
import com.arflix.tv.data.model.Episode
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.PersonDetails
import com.arflix.tv.util.CatalogUrlParser
import com.arflix.tv.util.Constants
import com.arflix.tv.util.ContentRating
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.arflix.tv.network.OkHttpProvider
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import com.arflix.tv.util.ParsedCatalogUrl
import javax.inject.Inject
import javax.inject.Singleton

data class StreamingServiceInfo(
    val id: Int,
    val name: String,
    val logoUrl: String? = null
)

data class StreamingServicesResult(
    val region: String,
    val services: List<StreamingServiceInfo>
)

data class PersonMediaSearchResult(
    val personId: Int,
    val name: String,
    val items: List<MediaItem>
)

data class MediaSearchResults(
    val items: List<MediaItem>,
    val people: List<PersonMediaSearchResult>
)



@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tmdbApi: TmdbApi,
    private val localWatchRepository: LocalWatchRepository,
    private val okHttpClient: OkHttpClient,
    private val streamRepository: StreamRepository,
) {

    data class CategoryPageResult(
        val items: List<MediaItem>,
        val hasMore: Boolean,
        val nextOffset: Int? = null,
        val totalCount: Int? = null
    )

    private val apiKey = Constants.TMDB_API_KEY
    private val gson = Gson()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Lazy so constructing the repository (tests do, with mocks) doesn't touch disk.
    private val addonNative by lazy { AddonNativeCatalog(context, streamRepository, gson) }

    /** TMDB content language (e.g. "en-US", "fr-FR", "nl-NL"). */
    @Volatile
    var contentLanguage: String = "en-US"
        set(value) {
            val normalized = value.ifBlank { "en-US" }.replace("iw", "he").replace('_', '-')
            if (field == normalized) return
            field = normalized
            clearMediaCache()
        }

    // === IN-MEMORY CACHE FOR PERFORMANCE ===
    private data class CacheEntry<T>(val data: T, val timestamp: Long)
    private val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

    fun clearMediaCache() {
        synchronized(detailsCache) { detailsCache.clear() }

        synchronized(similarCache) { similarCache.clear() }
        synchronized(logoCache) { logoCache.clear() }
        synchronized(seasonEpisodesCache) { seasonEpisodesCache.clear() }
        tmdbCatalogRefsCache.clear()
    }

    private data class MediaCacheEntry(val item: MediaItem, val timestamp: Long, val complete: Boolean)
    private val detailsCache = ConcurrentHashMap<String, MediaCacheEntry>()
    private val similarCache = mutableMapOf<String, CacheEntry<List<MediaItem>>>()
    private val logoCache = ConcurrentHashMap<String, CacheEntry<String?>>()
    private val watchProvidersCache = mutableMapOf<String, CacheEntry<StreamingServicesResult?>>()
    private val seasonEpisodesCache = mutableMapOf<String, CacheEntry<List<Episode>>>()
    private val imdbRatingCache = ConcurrentHashMap<String, CacheEntry<String>>()
    private val imdbRatingsByIdCache = ConcurrentHashMap<String, CacheEntry<String>>()
    private val imdbIdCache = ConcurrentHashMap<String, String>()
    private val addonImdbToTmdbCache = ConcurrentHashMap<String, CacheEntry<Pair<MediaType, Int>?>>()
    private val addonTitleToTmdbCache = ConcurrentHashMap<String, CacheEntry<Pair<MediaType, Int>?>>()
    private val tmdbCatalogRefsCache = ConcurrentHashMap<String, CacheEntry<List<Pair<MediaType, Int>>>>()

    private fun <T> getFromCache(cache: Map<String, CacheEntry<T>>, key: String): T? {
        val entry = cache[key] ?: return null
        return if (System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) entry.data else null
    }

    private fun detailsCacheKey(mediaType: MediaType, mediaId: Int): String {
        return if (mediaType == MediaType.MOVIE) "movie_$mediaId" else "tv_$mediaId"
    }

    private fun getAddonImdbLookupEntry(imdbId: String): CacheEntry<Pair<MediaType, Int>?>? {
        val entry = addonImdbToTmdbCache[imdbId] ?: return null
        return if (System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
            entry
        } else {
            addonImdbToTmdbCache.remove(imdbId)
            null
        }
    }

    private fun getAddonImdbLookup(imdbId: String): Pair<MediaType, Int>? {
        return getAddonImdbLookupEntry(imdbId)?.data
    }

    private fun cacheAddonImdbLookup(imdbId: String, value: Pair<MediaType, Int>?) {
        addonImdbToTmdbCache[imdbId] = CacheEntry(value, System.currentTimeMillis())
    }

    private fun getAddonTitleLookupEntry(key: String): CacheEntry<Pair<MediaType, Int>?>? {
        val entry = addonTitleToTmdbCache[key] ?: return null
        return if (System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
            entry
        } else {
            addonTitleToTmdbCache.remove(key)
            null
        }
    }

    private fun cacheAddonTitleLookup(key: String, value: Pair<MediaType, Int>?) {
        addonTitleToTmdbCache[key] = CacheEntry(value, System.currentTimeMillis())
    }

    private data class CatalogQuerySourceRefs(
        val refs: List<Pair<MediaType, Int>>,
        val failed: Boolean = false
    )

    private class CatalogQuerySourceLoadException(
        val partialRefs: List<Pair<MediaType, Int>>,
        cause: Exception
    ) : Exception(cause)


    fun getCachedItem(mediaType: MediaType, mediaId: Int): MediaItem? {
        return cachedMediaEntry(mediaType, mediaId)?.item ?: addonNative.card(mediaType, mediaId)
    }

    private fun cachedMediaEntry(mediaType: MediaType, mediaId: Int): MediaCacheEntry? =
        detailsCache[detailsCacheKey(mediaType, mediaId)]?.takeIf {
            System.currentTimeMillis() - it.timestamp < CACHE_TTL_MS
        }

    suspend fun getCachedItemFromDisk(mediaType: MediaType, mediaId: Int): MediaItem? =
        withContext(Dispatchers.IO) {
            peekItemFromDiskCache(mediaType, mediaId)
        }

    private fun peekItemFromDiskCache(mediaType: MediaType, mediaId: Int): MediaItem? = try {
            val cacheFiles = mutableListOf<java.io.File>()
            context.cacheDir.listFiles { _, name -> name.startsWith("home_categories_cache_") && name.endsWith(".json") }
                ?.let { cacheFiles.addAll(it) }
            context.filesDir.listFiles { _, name -> name.startsWith("home_continue_watching_") && name.endsWith(".json") }
                ?.let { cacheFiles.addAll(it) }

            for (file in cacheFiles) {
                if (!file.exists() || file.length() > 12_000_000L) continue
                val json = file.readText()
                if (json.isBlank()) continue
                if (!json.contains("\"id\":$mediaId") && !json.contains("\"id\": $mediaId")) continue

                val type = com.google.gson.reflect.TypeToken
                    .getParameterized(MutableList::class.java, Category::class.java)
                    .type
                val categories: List<Category>? = runCatching { gson.fromJson<List<Category>>(json, type) }.getOrNull()
                if (categories != null) {
                    for (cat in categories) {
                        for (item in cat.items) {
                            if (item.id == mediaId && item.mediaType == mediaType) {
                                cacheItem(item)
                                return item
                            }
                        }
                    }
                }

                val cwItems = decodeContinueWatchingCache(json, gson)
                if (cwItems != null) {
                    for (cw in cwItems) {
                        if (cw.id == mediaId && cw.mediaType == mediaType) {
                            val item = cw.toMediaItem()
                            cacheItem(item)
                            return item
                        }
                    }
                }
            }
            null
        } catch (_: Throwable) {
            null
        }

    fun getCachedFullItem(mediaType: MediaType, mediaId: Int): MediaItem? {
        return cachedMediaEntry(mediaType, mediaId)?.takeIf { it.complete }?.item
    }

    fun cacheImdbId(mediaType: MediaType, mediaId: Int, imdbId: String) {
        if (imdbId.isBlank()) return
        val cacheKey = detailsCacheKey(mediaType, mediaId)
        imdbIdCache[cacheKey] = imdbId
    }

    fun getCachedImdbId(mediaType: MediaType, mediaId: Int): String? {
        if (mediaType == MediaType.TV) ShowMergeTable.entry(mediaId)?.let { return it.imdbId }
        val cacheKey = detailsCacheKey(mediaType, mediaId)
        // A native addon item has no IMDb id; its addon's own id takes that place, which
        // routes stream requests (details, player, next episode) back to that addon.
        return imdbIdCache[cacheKey] ?: if (mediaId < 0) addonNative.streamId(mediaId) else null
    }

    /** True for a native addon item (see [AddonNativeCatalog]). */
    fun isAddonNative(mediaId: Int): Boolean = mediaId < 0 && addonNative.isNative(mediaId)

    /** A native addon item's own catalog card (with its artwork), never a copy cached from elsewhere. */
    fun addonNativeCard(type: MediaType, mediaId: Int): MediaItem? =
        if (mediaId < 0) addonNative.card(type, mediaId) else null

    /** A native addon item's own type, for stream lookup; null for TMDB items. */
    fun addonNativeType(mediaId: Int): String? = if (mediaId < 0) addonNative.addonType(mediaId) else null

    /** An addon's live channel, which must be played as live and kept out of Continue Watching. */
    fun isAddonNativeLiveChannel(mediaId: Int): Boolean = mediaId < 0 && addonNative.isLiveChannel(mediaId)

    // Catalog rows show native items as registered cards: loading full addon metadata per
    // card would cost one request per item, and the details screen loads it anyway.
    private suspend fun catalogItemFor(type: MediaType, mediaId: Int): MediaItem? = when {
        mediaId < 0 -> addonNative.card(type, mediaId)
        type == MediaType.MOVIE -> getMovieDetails(mediaId)
        else -> getTvDetails(mediaId)
    }

    suspend fun getImdbRating(item: MediaItem): String? {
        item.imdbRating.takeIf { value ->
            value.toDoubleOrNull()?.let { it.isFinite() && it > 0 && it <= 10 } == true
        }?.let { return it }
        return if (item.id > 0) getImdbRating(item.mediaType, item.id) else null
    }

    suspend fun getImdbRating(mediaType: MediaType, mediaId: Int, imdbId: String? = null): String? {
        if (mediaId <= 0 && imdbId?.startsWith("tt", ignoreCase = true) != true) return null
        val cacheKey = detailsCacheKey(mediaType, mediaId)
        getFromCache(imdbRatingCache, cacheKey)?.let { return it }

        val resolvedImdbId = resolveImdbId(mediaType, mediaId, imdbId)

        val rating = resolvedImdbId
            ?.let { imdbIdValue ->
                fetchCinemetaImdbRating(mediaType, imdbIdValue)
                    ?: getAgregarrImdbRatings(listOf(imdbIdValue))[imdbIdValue]
            }
            ?.let { normalizeRating(it) }
            ?: return null

        imdbRatingCache[cacheKey] = CacheEntry(rating, System.currentTimeMillis())
        return rating
    }

    private suspend fun resolveImdbId(mediaType: MediaType, mediaId: Int, imdbId: String? = null): String? {
        val direct = imdbId
            ?.trim()
            ?.takeIf { it.startsWith("tt", ignoreCase = true) }
        if (!direct.isNullOrBlank()) {
            cacheImdbId(mediaType, mediaId, direct)
            return direct
        }
        return getCachedImdbId(mediaType, mediaId)
            ?: resolveExternalIds(mediaType, mediaId)?.imdbId?.also { cacheImdbId(mediaType, mediaId, it) }
    }

    private suspend fun resolveExternalIds(mediaType: MediaType, mediaId: Int): TmdbExternalIds? {
        return try {
            when (mediaType) {
                MediaType.MOVIE -> tmdbApi.getMovieExternalIds(mediaId, apiKey)
                MediaType.TV -> tmdbApi.getTvExternalIds(mediaId, apiKey)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            com.arflix.tv.util.AppLogger.recordException(e, mapOf("error_area" to "MediaRepository", "action" to "resolveExternalIds"))
            null
        }
    }

    private suspend fun fetchCinemetaImdbRating(mediaType: MediaType, imdbId: String): String? = withContext(Dispatchers.IO) {
        val typePath = if (mediaType == MediaType.TV) "series" else "movie"
        val request = Request.Builder()
            .url("https://v3-cinemeta.strem.io/meta/$typePath/$imdbId.json")
            .header("Accept", "application/json")
            .header("User-Agent", OkHttpProvider.userAgentOr("Mozilla/5.0 (Android TV; ARVIO)"))
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                val meta = JSONObject(body).optJSONObject("meta") ?: return@use null
                parseCinemetaMetaRating(meta)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private fun parseCinemetaMetaRating(meta: JSONObject): String? {
        listOf(
            meta.optString("imdbRating"),
            meta.optString("rating")
        ).firstOrNull { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }?.let { return it }

        val links = meta.optJSONArray("links") ?: return null
        for (index in 0 until links.length()) {
            val link = links.optJSONObject(index) ?: continue
            if (link.optString("category").equals("imdb", ignoreCase = true)) {
                return link.optString("name").takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }
            }
        }
        return null
    }







    private suspend fun getAgregarrImdbRatings(imdbIds: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        val uniqueIds = imdbIds
            .map { it.trim() }
            .filter { it.startsWith("tt", ignoreCase = true) }
            .distinct()
        if (uniqueIds.isEmpty()) return@withContext emptyMap()

        val now = System.currentTimeMillis()
        val result = mutableMapOf<String, String>()
        val idsToFetch = uniqueIds.filter { imdbId ->
            val cached = getFromCache(imdbRatingsByIdCache, imdbId)
            if (!cached.isNullOrBlank()) {
                result[imdbId] = cached
                false
            } else {
                true
            }
        }
        if (idsToFetch.isEmpty()) return@withContext result

        idsToFetch.chunked(100).forEach { chunk ->
            val url = "https://api.agregarr.org/api/ratings".toHttpUrl().newBuilder().apply {
                chunk.forEach { addQueryParameter("id", it) }
            }.build()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", OkHttpProvider.userAgentOr("Mozilla/5.0 (Android TV; ARVIO)"))
                .build()

            val fetched = runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyMap<String, String>()
                    val body = response.body?.string().orEmpty()
                    val array = JSONArray(body)
                    buildMap {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            val imdbId = item.optString("imdbId").trim()
                            if (imdbId.isBlank() || item.isNull("rating")) continue
                            val rating = normalizeRating(item.optDouble("rating", 0.0).toString())
                            if (!rating.isNullOrBlank()) put(imdbId, rating)
                        }
                    }
                }
            }.getOrDefault(emptyMap())

            fetched.forEach { (imdbId, rating) ->
                imdbRatingsByIdCache[imdbId] = CacheEntry(rating, now)
                result[imdbId] = rating
            }
        }
        result
    }



    fun cacheItem(item: MediaItem) {
        if (item.isAddonNative) addonNative.restore(item)
        val cacheKey = detailsCacheKey(item.mediaType, item.id)
        val now = System.currentTimeMillis()
        detailsCache.compute(cacheKey) { _, existing ->
            if (existing != null && existing.complete && now - existing.timestamp < CACHE_TTL_MS) existing
            else MediaCacheEntry(item, now, complete = false)
        }
    }

    private fun cacheFullDetailsItem(item: MediaItem) {
        val cacheKey = detailsCacheKey(item.mediaType, item.id)
        detailsCache[cacheKey] = MediaCacheEntry(item, System.currentTimeMillis(), complete = true)
    }

    private fun cacheItems(items: List<MediaItem>) {
        items.forEach { cacheItem(it) }
    }

    fun getDefaultCatalogConfigs(): List<CatalogConfig> = buildPreinstalledDefaults()

    companion object {
        internal fun buildPreinstalledDefaults(): List<CatalogConfig> = emptyList()


        /** Upper bound for a TMDB page used as a catalog row (discover pages are endless). */
        private const val TMDB_CATALOG_MAX_ITEMS = 200


    }

    suspend fun loadCustomCatalog(catalog: CatalogConfig, maxItems: Int = 40): Category? = coroutineScope {

        val effectiveMaxItems = if (catalog.isTop10Catalog()) maxItems.coerceAtMost(10) else maxItems
        val mediaRefs = when (catalog.sourceType) {
            CatalogSourceType.TMDB -> loadTmdbCatalogRefs(catalog.sourceUrl, catalog.sourceRef)
            CatalogSourceType.ADDON -> loadAddonCatalogRefsPage(catalog, offset = 0, limit = effectiveMaxItems).refs
            CatalogSourceType.PREINSTALLED -> emptyList()
        }
        if (mediaRefs.isEmpty()) return@coroutineScope null

        val semaphore = Semaphore(6)
        val jobs = mediaRefs.distinct().take(effectiveMaxItems).map { (type, tmdbId) ->
            async {
                semaphore.withPermit {
                    runCatching { catalogItemFor(type, tmdbId) }.getOrNull()
                }
            }
        }
        val items = jobs.mapNotNull { it.await() }
        if (items.isEmpty()) return@coroutineScope null
        Category(
            id = catalog.id,
            title = catalog.title,
            items = items
        )
    }

    suspend fun loadCustomCatalogPage(
        catalog: CatalogConfig,
        offset: Int,
        limit: Int
    ): CategoryPageResult = coroutineScope {

        if (limit <= 0 || offset < 0) return@coroutineScope CategoryPageResult(emptyList(), hasMore = false)
        val rankedCatalogLimit = if (catalog.isTop10Catalog()) 10 else Int.MAX_VALUE
        if (offset >= rankedCatalogLimit) {
            return@coroutineScope CategoryPageResult(emptyList(), hasMore = false)
        }
        val effectiveLimit = limit.coerceAtMost(rankedCatalogLimit - offset)

        val pageRefs: List<Pair<MediaType, Int>>
        val hasMore: Boolean
        var sourceNextOffset: Int? = null
        if (catalog.sourceType == CatalogSourceType.ADDON) {
            val page = loadAddonCatalogRefsPage(catalog, offset, effectiveLimit)
            pageRefs = page.refs
            sourceNextOffset = page.nextOffset
            hasMore = page.hasMore && page.nextOffset < rankedCatalogLimit
        } else {
            val mediaRefs = when (catalog.sourceType) {
                CatalogSourceType.TMDB -> loadTmdbCatalogRefs(catalog.sourceUrl, catalog.sourceRef)
                CatalogSourceType.ADDON -> emptyList()
                CatalogSourceType.PREINSTALLED -> emptyList()
            }.distinct()

            if (mediaRefs.isEmpty()) return@coroutineScope CategoryPageResult(emptyList(), hasMore = false)

            val cappedRefs = mediaRefs.take(rankedCatalogLimit)
            pageRefs = cappedRefs.drop(offset).take(effectiveLimit)
            if (pageRefs.isEmpty()) {
                return@coroutineScope CategoryPageResult(emptyList(), hasMore = false)
            }
            hasMore = offset + pageRefs.size < cappedRefs.size
        }

        val semaphore = Semaphore(6)
        val jobs = pageRefs.map { (type, tmdbId) ->
            async {
                semaphore.withPermit {
                    runCatching { catalogItemFor(type, tmdbId) }.getOrNull()
                }
            }
        }
        val items = jobs.mapNotNull { it.await() }
        if (items.isNotEmpty()) {
            cacheItems(items)
        }
        CategoryPageResult(
            items = items,
            hasMore = hasMore,
            nextOffset = sourceNextOffset ?: (offset + pageRefs.size)
        )
    }



    private suspend fun resolveCatalogQuerySourceRefs(
        source: CatalogQuerySourceConfig,
        limit: Int,
        mediaType: MediaType?
    ): CatalogQuerySourceRefs {
        // A failed TMDB page must not prevent other catalog sources from loading.
        return try {
            val refs = when (source.kind) {
                CatalogQuerySourceKind.TMDB_COLLECTION -> loadTmdbCollectionRefs(source, limit)
                CatalogQuerySourceKind.TMDB_DISCOVER -> loadTmdbDiscoverRefs(source, limit)
                CatalogQuerySourceKind.TMDB_LIST -> loadTmdbListRefs(source, limit, mediaType)
            }
            CatalogQuerySourceRefs(refs.filter { mediaType == null || it.first == mediaType })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: CatalogQuerySourceLoadException) {
            CatalogQuerySourceRefs(e.partialRefs.filter { mediaType == null || it.first == mediaType }, failed = true)
        } catch (e: Exception) {
            CatalogQuerySourceRefs(emptyList(), failed = true)
        }
    }

    /** Generic TMDB discovery used by user-supplied TMDB page URLs. */
    private suspend fun loadTmdbDiscoverRefs(
        source: CatalogQuerySourceConfig,
        limit: Int
    ): List<Pair<MediaType, Int>> {
        val isTv = when (source.mediaType?.lowercase(Locale.US)) {
            "series", "tv", "show" -> true
            else -> false
        }
        val params = source.discoverParams.orEmpty()
        // Nuvio stores one date sort for both media types; TMDB names them differently.
        val sortBy = (source.sortBy ?: "popularity.desc").let { sort ->
            when {
                isTv && sort.startsWith("primary_release_date") -> sort.replace("primary_release_date", "first_air_date")
                !isTv && sort.startsWith("first_air_date") -> sort.replace("first_air_date", "primary_release_date")
                else -> sort
            }
        }
        return loadPagedTmdbDiscoverRefs(
            mediaType = if (isTv) MediaType.TV else MediaType.MOVIE,
            limit = limit
        ) { page ->
            tmdbApi.discoverWithParams(
                mediaType = if (isTv) "tv" else "movie",
                apiKey = apiKey,
                params = params,
                sortBy = sortBy,
                language = contentLanguage,
                page = page
            )
        }
    }

    /** Public TMDB list, kept in list order; mixed movie/TV entries. */
    private suspend fun loadTmdbListRefs(
        source: CatalogQuerySourceConfig,
        limit: Int,
        mediaType: MediaType?
    ): List<Pair<MediaType, Int>> {
        val requestLanguage = contentLanguage
        val listId = source.tmdbListId ?: return emptyList()
        val refs = LinkedHashSet<Pair<MediaType, Int>>()
        var page = 1
        var totalPages = 1
        while (refs.size < limit && page <= totalPages) {
            val response = try {
                tmdbApi.getPublicList(listId, apiKey, language = requestLanguage, page = page)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                throw CatalogQuerySourceLoadException(refs.toList(), e)
            }
            response.items.forEach { item ->
                if (item.id <= 0) return@forEach
                val type = if (item.mediaType.equals("tv", ignoreCase = true)) MediaType.TV else MediaType.MOVIE
                if (mediaType == null || type == mediaType) {
                    if (refs.size < limit) cacheCatalogPreview(item.asMediaItem(), type, requestLanguage)
                    refs.add(type to item.id)
                }
            }
            totalPages = response.totalPages.coerceAtLeast(1)
            if (response.items.isEmpty()) break
            page += 1
        }
        return refs.take(limit)
    }

    private suspend fun loadTmdbCollectionRefs(
        source: CatalogQuerySourceConfig,
        limit: Int
    ): List<Pair<MediaType, Int>> {
        val requestLanguage = contentLanguage
        val id = source.tmdbCollectionId ?: return emptyList()
        val response = tmdbApi.getTmdbCollection(id, apiKey, language = requestLanguage)
        return response.parts
            .sortedBy { it.releaseDate.orEmpty() }
            .take(limit)
            .onEach { cacheCatalogPreview(it, MediaType.MOVIE, requestLanguage) }
            .map { MediaType.MOVIE to it.id }
    }

    private suspend fun loadPagedTmdbDiscoverRefs(
        mediaType: MediaType,
        limit: Int,
        fetchPage: suspend (Int) -> TmdbListResponse
    ): List<Pair<MediaType, Int>> {
        val requestLanguage = contentLanguage
        if (limit <= 0) return emptyList()
        val refs = LinkedHashSet<Pair<MediaType, Int>>()
        var page = 1
        var totalPages = 1
        while (refs.size < limit && page <= totalPages) {
            val response = try {
                fetchPage(page)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw CatalogQuerySourceLoadException(refs.toList(), e)
            }
            response.results.forEach {
                cacheCatalogPreview(it, mediaType, requestLanguage)
                refs.add(mediaType to it.id)
            }
            totalPages = response.totalPages.coerceAtLeast(1)
            if (response.results.isEmpty()) break
            page += 1
        }
        return refs.take(limit)
    }

    private fun cacheCatalogPreview(item: TmdbMediaItem, type: MediaType, requestLanguage: String) {
        // Some list providers expose IDs only. Keep their lightweight detail fallback.
        if (item.id <= 0 || listOf(item.title, item.name, item.originalTitle, item.originalName).all { it.isNullOrBlank() }) return
        synchronized(detailsCache) {
            if (requestLanguage != contentLanguage || getCachedItem(type, item.id) != null) return
            cacheItem(item.toMediaItem(type))
        }
    }

    private data class AddonCatalogDescriptor(
        val addonId: String,
        val catalogType: String,
        val catalogId: String
    )

    private data class UnresolvedAddonMeta(
        val id: String,
        val typeHint: MediaType?
    )

    private data class AddonCatalogRefsPage(
        val refs: List<Pair<MediaType, Int>>,
        val hasMore: Boolean,
        val nextOffset: Int
    )

    private suspend fun loadAddonCatalogRefsPage(
        catalog: CatalogConfig,
        offset: Int,
        limit: Int
    ): AddonCatalogRefsPage = coroutineScope {
        val descriptor = resolveAddonCatalogDescriptor(catalog)
            ?: return@coroutineScope AddonCatalogRefsPage(emptyList(), hasMore = false, nextOffset = offset)

        val accumulated = LinkedHashSet<Pair<MediaType, Int>>()
        var probeOffset = offset.coerceAtLeast(0)
        var hasMore = false
        var probes = 0
        val maxProbes = 3

        while (probes < maxProbes && accumulated.size < limit) {
            val response = try {
                streamRepository.getAddonCatalogPage(
                    addonId = descriptor.addonId,
                    catalogType = descriptor.catalogType,
                    catalogId = descriptor.catalogId,
                    skip = probeOffset
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // A failed request is retryable, not an exhausted catalogue.
                throw e
            }

            val metas = response.metas ?: response.items ?: emptyList()
            if (metas.isEmpty()) {
                hasMore = false
                break
            }

            // Resolve only what this row needs, not a provider's entire 100-item page.
            // Advance by source entries consumed, including entries that cannot be matched.
            val consumed = metas.take(limit - accumulated.size)
            parseAddonPageRefs(
                metas = consumed,
                descriptor = descriptor
            ).forEach { accumulated.add(it) }

            hasMore = true // Providers choose their own page size; only empty means exhausted.
            probeOffset += consumed.size
            probes += 1
        }

        AddonCatalogRefsPage(
            refs = accumulated.take(limit),
            hasMore = hasMore,
            nextOffset = probeOffset
        )
    }

    private suspend fun parseAddonPageRefs(
        metas: List<StremioMetaPreview>,
        descriptor: AddonCatalogDescriptor
    ): List<Pair<MediaType, Int>> = coroutineScope {
        val typeHint = addonCatalogTypeToMediaType(descriptor.catalogType)
        val directRefs = mutableListOf<Pair<MediaType, Int>>()
        val imdbCandidates = mutableListOf<Pair<String, MediaType?>>()
        val titleCandidates = mutableListOf<Pair<String, MediaType?>>()
        val unresolvedMetaCandidates = mutableListOf<UnresolvedAddonMeta>()
        val seenImdb = HashSet<String>()
        val seenTitle = HashSet<String>()
        val seenMetaId = HashSet<String>()

        // Items whose addon serves its own metadata are shown as they are instead of being
        // matched to TMDB (a match would drop everything TMDB doesn't list, and streams for
        // a TMDB match would be requested by IMDb id, which that addon doesn't answer).
        val nativeRefs = mutableListOf<Pair<MediaType, Int>>()
        val nativeMetaIds = HashSet<String>()
        val nativeItems = mutableListOf<MediaItem>()
        // Read once per page: each read parses every installed manifest, and per item that
        // starved the heap on a 500-item catalog (ANR).
        val installed = installedAddonsSnapshot()
        val catalogAddon = installed.firstOrNull { it.id == descriptor.addonId }
        metas.forEach { meta ->
            if (parseTmdbRefFromAddonMeta(meta, typeHint) != null || extractImdbId(meta) != null) return@forEach
            val metaId = meta.id?.trim().orEmpty()
            // The addon's own type ("Podcasts", "tv", ...) is what its /meta answers to.
            val addonType = meta.type?.trim()?.takeIf { it.isNotBlank() } ?: descriptor.catalogType
            val mediaType = nativeAddonMediaType(addonType, typeHint)
            if (metaId.isBlank()) return@forEach
            // A metadata addon (AIOMetadata) can re-list another addon's catalog; that addon
            // owns the ids and serves their details and streams.
            val owner = if (catalogAddon != null && addonServesOwnMeta(catalogAddon, addonType, metaId)) {
                descriptor.addonId
            } else {
                findAddonServingOwnMeta(installed, addonType, metaId, excludeAddonId = descriptor.addonId)
            } ?: return@forEach
            val item = addonNative.register(owner, meta, mediaType, addonType) ?: return@forEach
            if (nativeMetaIds.add(metaId)) {
                nativeItems += item
                nativeRefs += mediaType to item.id
            }
        }
        if (nativeItems.isNotEmpty()) {
            cacheItems(nativeItems)
            addonNative.flush()
        }

        metas.forEach { meta ->
            if (meta.id?.trim() in nativeMetaIds) return@forEach
            val direct = parseTmdbRefFromAddonMeta(meta, typeHint)
            if (direct != null) {
                directRefs += direct
                return@forEach
            }
            val inferredHint = typeHint ?: addonCatalogTypeToMediaType(meta.type)
            val imdb = extractImdbId(meta)
            if (!imdb.isNullOrBlank() && seenImdb.add(imdb)) {
                imdbCandidates += imdb to inferredHint
                return@forEach
            }
            val metaId = meta.id?.trim().orEmpty()
            if (metaId.isNotBlank() && seenMetaId.add(metaId)) {
                unresolvedMetaCandidates += UnresolvedAddonMeta(
                    id = metaId,
                    typeHint = inferredHint
                )
            }
        }

        metas.forEach { meta ->
            if (meta.id?.trim() in nativeMetaIds) return@forEach
            if (parseTmdbRefFromAddonMeta(meta, typeHint) != null) return@forEach
            if (extractImdbId(meta) != null) return@forEach
            val title = meta.name?.trim().orEmpty()
            if (title.isBlank()) return@forEach
            // A title alone, with no movie/series type to narrow it, matches unrelated TMDB titles.
            val metaHint = typeHint ?: addonCatalogTypeToMediaType(meta.type) ?: return@forEach
            val titleKey = "${metaHint.name}|${title.lowercase(Locale.US)}"
            if (seenTitle.add(titleKey)) {
                titleCandidates += title to metaHint
            }
        }

        val metaSemaphore = Semaphore(2)
        // Don't ask an addon for metadata its manifest says it can't serve (a metadata addon for
        // ids it re-lists from another addon): each miss is several requests, and a page of them
        // gets us rate limited.
        val resolvedFromMeta = unresolvedMetaCandidates
            .filter { addonMayServeMeta(catalogAddon, it.id) }
            .take(8).map { unresolved ->
            async {
                metaSemaphore.withPermit {
                    resolveAddonMetaToTmdbRef(
                        descriptor = descriptor,
                        unresolved = unresolved
                    )
                }
            }
        }.mapNotNull { it.await() }

        val imdbSemaphore = Semaphore(4)
        val resolvedImdbRefs = imdbCandidates.map { (imdbId, hint) ->
            async {
                imdbSemaphore.withPermit {
                    resolveImdbToTmdbRef(imdbId, hint)
                }
            }
        }.mapNotNull { it.await() }

        val titleSemaphore = Semaphore(2)
        val resolvedTitleRefs = titleCandidates.take(12).map { (title, hint) ->
            async {
                titleSemaphore.withPermit {
                    resolveTitleToTmdbRef(title, hint)
                }
            }
        }.mapNotNull { it.await() }

        (nativeRefs + directRefs + resolvedFromMeta + resolvedImdbRefs + resolvedTitleRefs).distinct()
    }

    private suspend fun installedAddonsSnapshot(): List<Addon> = try {
        streamRepository.installedAddons.first()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Unknown means "match it to TMDB as before", never a failed row.
        emptyList()
    }

    private suspend fun resolveAddonMetaToTmdbRef(
        descriptor: AddonCatalogDescriptor,
        unresolved: UnresolvedAddonMeta
    ): Pair<MediaType, Int>? {
        val mediaType = unresolved.typeHint ?: addonCatalogTypeToMediaType(descriptor.catalogType) ?: return null
        val requestedType = when (mediaType) {
            MediaType.MOVIE -> "movie"
            MediaType.TV -> "series"
        }
        val meta = runCatching {
            streamRepository.getAddonMeta(
                addonId = descriptor.addonId,
                mediaType = requestedType,
                mediaId = unresolved.id
            )
        }.getOrNull() ?: return null

        parseTmdbRefFromAddonMeta(meta, mediaType)?.let { return it }
        val imdbId = extractImdbId(meta) ?: return null
        return resolveImdbToTmdbRef(imdbId, mediaType)
    }

    private suspend fun resolveImdbToTmdbRef(
        imdbId: String,
        mediaTypeHint: MediaType?
    ): Pair<MediaType, Int>? {
        val normalizedImdb = imdbId.trim()
        if (normalizedImdb.isBlank()) return null

        getAddonImdbLookupEntry(normalizedImdb)?.let { cached ->
            return cached.data
        }

        val findResponse = runCatching {
            tmdbApi.findByExternalId(
                externalId = normalizedImdb,
                apiKey = apiKey,
                externalSource = "imdb_id"
            )
        }.getOrNull()

        val resolved = findResponse?.let { response ->
            val movies = response.movieResults
            val series = response.tvResults
            when (mediaTypeHint) {
                MediaType.MOVIE -> movies.maxByOrNull { it.popularity }?.id?.let { MediaType.MOVIE to it }
                MediaType.TV -> series.maxByOrNull { it.popularity }?.id?.let { MediaType.TV to it }
                else -> {
                    val movie = movies.maxByOrNull { it.popularity }
                    val tv = series.maxByOrNull { it.popularity }
                    when {
                        movie == null && tv == null -> null
                        movie != null && tv == null -> MediaType.MOVIE to movie.id
                        movie == null && tv != null -> MediaType.TV to tv.id
                        else -> {
                            if ((movie?.popularity ?: 0f) >= (tv?.popularity ?: 0f)) {
                                MediaType.MOVIE to movie!!.id
                            } else {
                                MediaType.TV to tv!!.id
                            }
                        }
                    }
                }
            }
        }

        cacheAddonImdbLookup(normalizedImdb, resolved)
        return resolved
    }

    private suspend fun resolveTitleToTmdbRef(
        rawTitle: String,
        mediaTypeHint: MediaType?
    ): Pair<MediaType, Int>? {
        val title = rawTitle.trim()
        if (title.isBlank()) return null

        val cleanedTitle = title
            .replace(MediaRegexes.YEAR_SUFFIX_REGEX, "")
            .trim()
            .ifBlank { title }
        val cacheKey = "${mediaTypeHint?.name ?: "ANY"}|${cleanedTitle.lowercase(Locale.US)}"
        getAddonTitleLookupEntry(cacheKey)?.let { cached ->
            return cached.data
        }

        val response = runCatching {
            tmdbApi.searchMulti(
                apiKey = apiKey,
                query = cleanedTitle,
                language = contentLanguage,
                page = 1
            )
        }.getOrNull()

        val candidates = response?.results
            ?.mapNotNull { item ->
                val type = when (item.mediaType?.lowercase(Locale.US)) {
                    "movie" -> MediaType.MOVIE
                    "tv" -> MediaType.TV
                    else -> null
                } ?: return@mapNotNull null
                Triple(type, item.id, item.popularity)
            }
            .orEmpty()

        val scoped = if (mediaTypeHint != null) {
            candidates.filter { it.first == mediaTypeHint }
        } else {
            candidates
        }
        val best = (if (scoped.isNotEmpty()) scoped else candidates)
            .maxByOrNull { it.third }
            ?.let { it.first to it.second }

        cacheAddonTitleLookup(cacheKey, best)
        return best
    }

    private fun resolveAddonCatalogDescriptor(catalog: CatalogConfig): AddonCatalogDescriptor? {
        val addonId = catalog.addonId?.trim().takeUnless { it.isNullOrBlank() }
        val catalogType = normalizeAddonCatalogType(catalog.addonCatalogType)
        val catalogId = catalog.addonCatalogId?.trim().takeUnless { it.isNullOrBlank() }
        if (addonId != null && catalogType != null && catalogId != null) {
            return AddonCatalogDescriptor(addonId, catalogType, catalogId)
        }

        val sourceRef = catalog.sourceRef?.trim().orEmpty()
        if (!sourceRef.startsWith("addon_catalog|")) return null
        val parts = sourceRef.removePrefix("addon_catalog|").split("|")
        if (parts.size != 3) return null

        val parsedAddonId = decodeCatalogRefPart(parts[0]).trim()
        val parsedType = normalizeAddonCatalogType(decodeCatalogRefPart(parts[1]))
        val parsedCatalogId = decodeCatalogRefPart(parts[2]).trim()
        if (parsedAddonId.isBlank() || parsedType == null || parsedCatalogId.isBlank()) return null

        return AddonCatalogDescriptor(parsedAddonId, parsedType, parsedCatalogId)
    }

    private fun decodeCatalogRefPart(value: String): String {
        return try { URLDecoder.decode(value, "UTF-8") } catch (_: Exception) { value }
    }

    /** Known types are canonicalised; any other addon type keeps its spelling (see CatalogRepository). */
    private fun normalizeAddonCatalogType(rawType: String?): String? {
        val trimmed = rawType?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when (trimmed.lowercase()) {
            "movie" -> "movie"
            "series" -> "series"
            "tv" -> "tv"
            "show" -> "show"
            "shows" -> "shows"
            else -> trimmed
        }
    }

    /**
     * How a native item is shown. Stremio's "tv" type is a live channel: one stream, no
     * episodes, so it opens like a movie and plays straight away. Other unknown types
     * (podcasts, debrid libraries) are containers of videos, which the series page shows.
     */
    private fun nativeAddonMediaType(rawType: String, typeHint: MediaType?): MediaType {
        if (rawType.equals("tv", ignoreCase = true)) return MediaType.MOVIE
        return typeHint ?: addonCatalogTypeToMediaType(rawType) ?: MediaType.TV
    }

    private fun addonCatalogTypeToMediaType(rawType: String?): MediaType? {
        return when (normalizeAddonCatalogType(rawType)) {
            "movie" -> MediaType.MOVIE
            "series", "tv", "show", "shows" -> MediaType.TV
            else -> null
        }
    }

    private fun parseTmdbRefFromAddonMeta(
        meta: StremioMetaPreview,
        typeHint: MediaType?
    ): Pair<MediaType, Int>? {
        val normalizedHint = typeHint ?: addonCatalogTypeToMediaType(meta.type)
        // `moviedbId` would be a useful addon fallback field but it's added
        // by a separate pending change to StremioMetaPreview — stick to the
        // existing `tmdbId` alias until that lands to keep this branch
        // self-contained.
        val rawTmdb = meta.tmdbId?.trim().orEmpty()
        val tmdbFromField = rawTmdb.toIntOrNull()
            ?: MediaRegexes.DIGITS_REGEX.find(rawTmdb)?.value?.toIntOrNull()
        if (tmdbFromField != null && normalizedHint != null) {
            return normalizedHint to tmdbFromField
        }

        val rawId = meta.id?.trim().orEmpty()
        if (rawId.isBlank()) return null

        // Note: do NOT treat plain-numeric `meta.id` as a TMDB id. Some

        // those produce phantom TMDB 404s that showed up as empty collection
        // grids. Rely on explicit tmdb_id / moviedb_id or IMDB resolution.

        // IDs like movie:12345 or series:12345
        val typedIdMatch = MediaRegexes.TYPED_ID_REGEX.find(rawId)
        if (typedIdMatch != null) {
            val token = typedIdMatch.groupValues[1].lowercase()
            val tmdbId = typedIdMatch.groupValues[2].toIntOrNull() ?: return null
            val mediaType = when (token) {
                "movie" -> MediaType.MOVIE
                else -> MediaType.TV
            }
            return mediaType to tmdbId
        }

        if (!rawId.startsWith("tmdb", ignoreCase = true)) return null

        val parts = rawId.split(":").filter { it.isNotBlank() }
        if (parts.isEmpty()) return null

        if (parts.size == 2) {
            val tmdbId = parts[1].toIntOrNull() ?: return null
            return normalizedHint?.let { mediaType -> mediaType to tmdbId }
        }

        val candidateTokens = parts.drop(1)
        val tmdbId = candidateTokens
            .firstOrNull { token -> token.toIntOrNull() != null }
            ?.toIntOrNull()
            ?: return null
        val mediaType = candidateTokens
            .firstOrNull { token ->
                token.equals("movie", ignoreCase = true) ||
                    token.equals("series", ignoreCase = true) ||
                    token.equals("tv", ignoreCase = true) ||
                    token.equals("show", ignoreCase = true) ||
                    token.equals("shows", ignoreCase = true)
            }
            ?.let { token ->
                when (token.lowercase()) {
                    "movie" -> MediaType.MOVIE
                    "series", "tv", "show", "shows" -> MediaType.TV
                    else -> normalizedHint
                }
            }
            ?: normalizedHint
            ?: return null

        return mediaType to tmdbId
    }

    private fun extractImdbId(meta: StremioMetaPreview): String? {
        val direct = meta.imdbId?.trim().takeUnless { it.isNullOrBlank() }
        if (!direct.isNullOrBlank() && direct.startsWith("tt")) {
            return direct
        }

        val fromId = meta.id?.trim().orEmpty()
        if (fromId.startsWith("tt", ignoreCase = true)) return fromId
        if (fromId.startsWith("imdb:", ignoreCase = true)) {
            val candidate = fromId.substringAfter(':').trim()
            if (candidate.startsWith("tt", ignoreCase = true)) return candidate
        }

        val match = MediaRegexes.IMDB_ID_REGEX.find(fromId)
        return match?.value
    }

    /**
     * Get movie details (cached)
     */
    suspend fun getMovieDetails(movieId: Int): MediaItem {
        if (movieId < 0 && addonNative.isNative(movieId)) {
            return addonNative.details(MediaType.MOVIE, movieId)?.also { cacheItem(it) }
                ?: throw IllegalStateException("Native addon item $movieId has no details")
        }
        getCachedFullItem(MediaType.MOVIE, movieId)?.let { return it }

        val item = coroutineScope {
            val detailsDeferred = async { tmdbApi.getMovieDetails(movieId, apiKey, language = contentLanguage) }
            val externalIdsDeferred = async { resolveExternalIds(MediaType.MOVIE, movieId) }

            val details = detailsDeferred.await()
            externalIdsDeferred.await()?.imdbId?.also { cacheImdbId(MediaType.MOVIE, movieId, it) }
            details.toMediaItem().copy(
                contentRating = ContentRating.forMovie(details.releaseDates, contentLanguage)
            )
        }
        cacheFullDetailsItem(item)
        return item
    }

    /**
     * Get TV show details (cached)
     */
    suspend fun getTvDetails(tvId: Int): MediaItem {
        if (ShowMergeTable.isSecondary(tvId)) return getTvDetails(ShowMergeTable.canonicalId(tvId))
        if (tvId < 0 && addonNative.isNative(tvId)) {
            return addonNative.details(MediaType.TV, tvId)?.also { cacheItem(it) }
                ?: throw IllegalStateException("Native addon item $tvId has no details")
        }
        getCachedFullItem(MediaType.TV, tvId)?.let { return it }

        val item = coroutineScope {
            val detailsDeferred = async { tmdbApi.getMergedTvDetails(tvId, apiKey, language = contentLanguage) }
            val externalIdsDeferred = async { resolveExternalIds(MediaType.TV, tvId) }

            val details = detailsDeferred.await()
            externalIdsDeferred.await()?.imdbId?.also { cacheImdbId(MediaType.TV, tvId, it) }
            details.toMediaItem().copy(
                contentRating = ContentRating.forTv(details.contentRatings, contentLanguage)
            )
        }
        cacheFullDetailsItem(item)
        return item
    }

    /**
     * Lightweight calls for LauncherContinueWatchingRepository to avoid heavy IMDb rating/caching tasks.
     */
    suspend fun getLightweightMovieTitle(movieId: Int, language: String = contentLanguage): String? {
        return runCatching {
            tmdbApi.getMovieDetails(movieId, apiKey, language = language).title
        }.getOrNull()
    }

    suspend fun getLightweightTvTitle(tvId: Int, language: String = contentLanguage): String? {
        return runCatching {
            // TMDB uses 'name' for series instead of 'title'
            tmdbApi.getMergedTvDetails(tvId, apiKey, language = language).name
        }.getOrNull()
    }

    suspend fun getLightweightEpisodeTitle(
        tvId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        language: String = contentLanguage
    ): String? {
        return getLightweightEpisode(tvId, seasonNumber, episodeNumber, language)?.name
    }

    internal suspend fun getLightweightEpisode(
        tvId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        language: String = contentLanguage
    ): TmdbEpisode? {
        return runCatching {
            tmdbApi.getMergedTvSeason(
                tvId = tvId,
                seasonNumber = seasonNumber,
                apiKey = apiKey,
                language = language
            ).episodes.firstOrNull { it.episodeNumber == episodeNumber }
        }.getOrNull()
    }

    /**
     * Get season episodes with local watched status.
     * Uses the TMDB season structure and the local watched cache.
     */
    suspend fun getSeasonEpisodes(tvId: Int, seasonNumber: Int): List<Episode> {
        if (tvId < 0 && addonNative.isNative(tvId)) return addonNative.episodes(tvId, seasonNumber)
        val cacheKey = "tv_${tvId}_season_$seasonNumber"
        val cachedEpisodes = getFromCache(seasonEpisodesCache, cacheKey)

        // First ensure the global watched cache is initialized.
        localWatchRepository.initializeWatchedCache()

        // Get watched episodes - try global cache first (faster, more reliable).
        val watchedEpisodes = if (localWatchRepository.hasWatchedEpisodes(tvId)) {
            localWatchRepository.getWatchedEpisodesFromCache()
        } else {
            try {
                localWatchRepository.getWatchedEpisodesForShow(tvId)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                emptySet<String>()
            }
        }
        val hasShowWatchedData = watchedEpisodes.any { it.startsWith("show_tmdb:$tvId:") }

        // Fast-path: return cached episodes immediately
        if (cachedEpisodes != null) {
            val episodes = cachedEpisodes.map { episode ->
                val episodeKey = "show_tmdb:$tvId:${episode.seasonNumber}:${episode.episodeNumber}"
                episode.copy(
                    isWatched = if (hasShowWatchedData) episodeKey in watchedEpisodes else episode.isWatched
                )
            }
            return episodes
        }

        val season = tmdbApi.getMergedTvSeason(tvId, seasonNumber, apiKey, language = contentLanguage)
        val episodes = season.episodes.map { episode ->
            val episodeKey = "show_tmdb:$tvId:${episode.seasonNumber}:${episode.episodeNumber}"
            episode.toEpisode().copy(
                isWatched = episodeKey in watchedEpisodes
            )
        }
        seasonEpisodesCache[cacheKey] = CacheEntry(episodes, System.currentTimeMillis())


        return episodes
    }



    /**
     * Get recommended content (cached)
     * Falls back to similar if recommendations are empty
     */
    suspend fun getSimilar(mediaType: MediaType, mediaId: Int): List<MediaItem> {
        val cacheKey = "${mediaType}_similar_$mediaId"
        getFromCache(similarCache, cacheKey)?.let { return it }

        val type = if (mediaType == MediaType.TV) "tv" else "movie"
        val recommendations = try {
            tmdbApi.getRecommendations(type, mediaId, apiKey, language = contentLanguage)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e

            null
        }

        val result = if (recommendations != null && recommendations.results.isNotEmpty()) {
            recommendations.results
                .map { it.toMediaItem(mediaType) }
                .distinctBy { it.id }
                .take(12)
        } else {
            val similar = tmdbApi.getSimilar(type, mediaId, apiKey, language = contentLanguage)
            similar.results
                .map { it.toMediaItem(mediaType) }
                .distinctBy { it.id }
                .take(12)
        }
        similarCache[cacheKey] = CacheEntry(result, System.currentTimeMillis())
        cacheItems(result)
        return result
    }

    /**
     * Get logo URL for a media item (cached)
     */
    suspend fun getLogoUrl(mediaType: MediaType, mediaId: Int): String? {
        if (mediaId <= 0) return null
        val cacheKey = "${mediaType}_logo_$mediaId"
        logoCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                return cached.data
            }
            logoCache.remove(cacheKey)
        }

        val type = if (mediaType == MediaType.TV) "tv" else "movie"
        return try {
            val images = tmdbApi.getImages(type, mediaId, apiKey)
            // Quality ranking for clearlogos: prefer PNG over SVG (the app has
            // no SVG decoder), English over other locales, and among the
            // survivors pick the highest community-rated logo (vote_average
            // breaks ties, width acts as a secondary for untouched images).
            val logo = images.logos
                .asSequence()
                .filter { !it.filePath.isNullOrBlank() }
                .filterNot { it.filePath!!.endsWith(".svg", ignoreCase = true) }
                .sortedWith(
                    compareByDescending<TmdbImage> { it.iso6391 == "en" }
                        .thenByDescending { it.voteAverage }
                        .thenByDescending { it.width }
                )
                .firstOrNull()
                ?: images.logos.firstOrNull()
            val url = logo?.filePath?.let { "${Constants.LOGO_BASE}$it" }
            logoCache[cacheKey] = CacheEntry(url, System.currentTimeMillis())
            url
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e

            null
        }
    }

    suspend fun getLogoUrl(item: MediaItem): String? {
        item.id.takeIf { it > 0 }?.let { return getLogoUrl(item.mediaType, it) }
        return null
    }



    /** Instant synchronous peek into the in-memory or persisted logo cache. */
    fun peekCachedLogoUrl(mediaType: MediaType, mediaId: Int): String? {
        val keys = listOf(
            "${mediaType}_logo_$mediaId",
            "${mediaType}_$mediaId",
            "${mediaType.name.lowercase()}_logo_$mediaId",
            "${mediaType.name.lowercase()}_$mediaId",
            "${mediaType.name.uppercase()}_logo_$mediaId",
            "${mediaType.name.uppercase()}_$mediaId"
        )
        for (k in keys) {
            if (logoCache.containsKey(k)) {
                val cached = getFromCache(logoCache, k)
                if (!cached.isNullOrBlank()) return cached
            }
        }
        try {
            val json = context.getSharedPreferences("logo_cache", Context.MODE_PRIVATE).getString("urls", null)
            if (!json.isNullOrBlank()) {
                val jsonObject = org.json.JSONObject(json)
                for (k in keys) {
                    if (jsonObject.has(k)) {
                        val url = jsonObject.optString(k)
                        if (!url.isNullOrBlank()) {
                            logoCache["${mediaType}_logo_$mediaId"] = CacheEntry(url, System.currentTimeMillis())
                            return url
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
        return null
    }

    fun cacheLogoUrl(mediaType: MediaType, mediaId: Int, logoUrl: String) {
        if (logoUrl.isBlank()) return
        val cacheKey = "${mediaType}_logo_$mediaId"
        logoCache[cacheKey] = CacheEntry(logoUrl, System.currentTimeMillis())
    }

    /** Instant synchronous peek into the in-memory season episodes cache. */
    fun peekCachedSeasonEpisodes(tvId: Int, seasonNumber: Int): List<Episode>? {
        val cacheKey = "tv_${tvId}_season_$seasonNumber"
        return getFromCache(seasonEpisodesCache, cacheKey)
    }

    /**
     * Media metadata
     */


    /**
     * Get person details
     */
    suspend fun getPersonDetails(personId: Int): PersonDetails {
        val person = tmdbApi.getPersonDetails(personId, apiKey, language = contentLanguage)
        return person.toPersonDetails()
    }

    /**
     * Search media
     */
    suspend fun search(query: String): List<MediaItem> {
        val results = tmdbApi.searchMulti(apiKey, query, language = contentLanguage)
        val items = results.results
            .filter { it.mediaType == "movie" || (it.mediaType == "tv" && !ShowMergeTable.isSecondary(it.id)) }
            .map {
                it.toMediaItem(
                    if (it.mediaType == "tv") MediaType.TV else MediaType.MOVIE
                )
            }
        cacheItems(items)
        return items
    }

    /** Titles and known-for rows share one request; optional artwork must not delay them. */
    suspend fun searchWithPeople(query: String, maxPeople: Int = 3): MediaSearchResults = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@coroutineScope MediaSearchResults(emptyList(), emptyList())

        val response = tmdbApi.searchMulti(apiKey, trimmed, language = contentLanguage)
        val items = response.results
            .filter { it.mediaType == "movie" || (it.mediaType == "tv" && !ShowMergeTable.isSecondary(it.id)) }
            .map { it.toMediaItem(if (it.mediaType == "tv") MediaType.TV else MediaType.MOVIE) }
            .distinctBy { it.mediaType to it.id }
        val people = response.results
            .asSequence()
            .filter { it.mediaType == "person" && it.id > 0 && !it.name.isNullOrBlank() }
            .distinctBy { it.id }
            .sortedByDescending { it.popularity }
            .take(maxPeople)
            .toList()

        val rows = people.map { person ->
            val knownForItems = person.knownFor
                .asSequence()
                .filter { it.posterPath != null && (it.mediaType == "movie" || (it.mediaType == "tv" && !ShowMergeTable.isSecondary(it.id))) }
                .sortedWith(
                    compareByDescending<TmdbMediaItem> { it.voteCount }
                        .thenByDescending { it.popularity }
                )
                .map {
                    it.toMediaItem(
                        if (it.mediaType == "tv") MediaType.TV else MediaType.MOVIE
                    )
                }
                .distinctBy { "${it.mediaType}_${it.id}" }
                .take(20)
                .toList()

            PersonMediaSearchResult(personId = person.id, name = person.name.orEmpty(), items = knownForItems)
        }

        cacheItems(items + rows.flatMap { it.items })
        MediaSearchResults(items, rows)
    }

    /** Native addon items matching [query] (see [StreamRepository.searchNativeAddonCatalogs]). */
    suspend fun searchNativeAddonItems(query: String): List<MediaItem> = try {
        streamRepository.searchNativeAddonCatalogs(query).flatMap { page ->
            val descriptor = AddonCatalogDescriptor(page.addonId, page.catalogType, page.catalogId)
            parseAddonPageRefs(page.metas, descriptor)
                .mapNotNull { (type, id) -> if (id < 0) addonNative.card(type, id) else null }
        }.distinctBy { it.mediaType to it.id }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Discover movies via TMDB discover API with optional genre/sort/vote filters.
     *
     * Two date windows, deliberately kept apart: `releaseDate*` accepts a film with ANY release
     * in the window (a re-run counts), `primaryReleaseDate*` only one whose FIRST release falls
     * in it — the date the card prints. A filter the user reads off the poster wants the second.
     */
    suspend fun discoverMovies(
        genres: String? = null,
        sortBy: String = "popularity.desc",
        minVoteCount: Int? = null,
        page: Int = 1,
        language: String? = null,
        year: Int? = null,
        keywords: String? = null,
        releaseDateLte: String? = null,
        releaseDateGte: String? = null,
        primaryReleaseDateLte: String? = null,
        primaryReleaseDateGte: String? = null,
        minVoteAverage: Double? = null,
        maxVoteAverage: Double? = null,
        certificationCountry: String? = null,
        certificationLte: String? = null
    ): List<MediaItem> {
        val response = tmdbApi.discoverMovies(apiKey, genres = genres, sortBy = sortBy, minVoteCount = minVoteCount, page = page, originalLanguage = language, year = year, keywords = keywords, language = contentLanguage, releaseDateLte = releaseDateLte, releaseDateGte = releaseDateGte, primaryReleaseDateLte = primaryReleaseDateLte, primaryReleaseDateGte = primaryReleaseDateGte, minVoteAverage = minVoteAverage, maxVoteAverage = maxVoteAverage, certificationCountry = certificationCountry, certificationLte = certificationLte)
        val items = response.results.map { it.toMediaItem(MediaType.MOVIE) }
        cacheItems(items)
        return items
    }

    /**
     * Discover TV shows via TMDB discover API with optional genre/sort/vote/language/year filters.
     *
     * Same split as [discoverMovies]: `airDate*` asks when an EPISODE aired (what the home
     * screen's anime rows need), `firstAirDate*` when the show STARTED — the date on the card.
     */
    suspend fun discoverTv(
        genres: String? = null,
        sortBy: String = "popularity.desc",
        minVoteCount: Int? = null,
        page: Int = 1,
        language: String? = null,
        year: Int? = null,
        keywords: String? = null,
        airDateLte: String? = null,
        airDateGte: String? = null,
        firstAirDateLte: String? = null,
        firstAirDateGte: String? = null,
        minVoteAverage: Double? = null,
        maxVoteAverage: Double? = null
    ): List<MediaItem> {
        val response = tmdbApi.discoverTv(apiKey, genres = genres, sortBy = sortBy, minVoteCount = minVoteCount, page = page, originalLanguage = language, year = year, keywords = keywords, language = contentLanguage, airDateLte = airDateLte, airDateGte = airDateGte, firstAirDateLte = firstAirDateLte, firstAirDateGte = firstAirDateGte, minVoteAverage = minVoteAverage, maxVoteAverage = maxVoteAverage)
        val items = response.results.map { it.toMediaItem(MediaType.TV) }
        cacheItems(items)
        return items
    }

    /**
     * Load a single discover category for the search/discover page.
     */
    suspend fun loadDiscoverCategory(categoryId: String, title: String): Category? {
        return try {
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val items = when (categoryId) {
                "trending_movies" -> {
                    val r1 = tmdbApi.getTrendingMovies(apiKey, language = contentLanguage, page = 1)
                    r1.results.map { it.toMediaItem(MediaType.MOVIE) }
                }
                "popular_movies" -> {
                    val r1 = tmdbApi.discoverMovies(apiKey, sortBy = "popularity.desc", language = contentLanguage, page = 1)
                    r1.results.map { it.toMediaItem(MediaType.MOVIE) }
                }
                "popular_tv" -> {
                    val r1 = tmdbApi.discoverTv(apiKey, sortBy = "popularity.desc", language = contentLanguage, page = 1)
                    r1.results.map { it.toMediaItem(MediaType.TV) }
                }
                "top_rated_movies" -> {
                    val r1 = tmdbApi.discoverMovies(apiKey, sortBy = "vote_average.desc", minVoteCount = 1000, language = contentLanguage, page = 1)
                    r1.results.map { it.toMediaItem(MediaType.MOVIE) }
                }
                "new_releases" -> {
                    val cal = java.util.Calendar.getInstance()
                    val today = dateFormat.format(cal.time)
                    cal.add(java.util.Calendar.DAY_OF_YEAR, -30)
                    val thirtyDaysAgo = dateFormat.format(cal.time)
                    val r1 = tmdbApi.discoverMovies(apiKey, sortBy = "popularity.desc", language = contentLanguage, releaseDateGte = thirtyDaysAgo, releaseDateLte = today, page = 1)
                    r1.results.map { it.toMediaItem(MediaType.MOVIE) }
                }
                else -> emptyList()
            }
            if (items.isEmpty()) null
            else {
                cacheItems(items)
                Category(id = categoryId, title = title, items = items.take(20))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e

            null
        }
    }



    suspend fun getStreamingServices(
        mediaType: MediaType,
        mediaId: Int,
        preferredRegion: String? = null
    ): StreamingServicesResult? {
        val region = normalizeWatchRegion(preferredRegion)
        val cacheKey = "${mediaType}_watch_providers_${mediaId}_$region"
        val cachedEntry = watchProvidersCache[cacheKey]
        if (cachedEntry != null) {
            if (System.currentTimeMillis() - cachedEntry.timestamp < CACHE_TTL_MS) {
                return cachedEntry.data
            }
            watchProvidersCache.remove(cacheKey)
        }

        val response = try {
            when (mediaType) {
                MediaType.MOVIE -> tmdbApi.getMovieWatchProviders(mediaId, apiKey)
                MediaType.TV -> tmdbApi.getTvWatchProviders(mediaId, apiKey)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A failed lookup is not a successful empty response and must not poison the cache.
            return null
        }

        val results = response?.results.orEmpty()
        if (results.isEmpty()) {
            watchProvidersCache[cacheKey] = CacheEntry(null, System.currentTimeMillis())
            return null
        }

        val requestedRegion = normalizeWatchRegion(preferredRegion)
        val localeRegion = normalizeWatchRegion(Locale.getDefault().country)
        val candidateRegions = listOf(requestedRegion, localeRegion, "US")
            .distinct()

        val resolvedFromPreferred = candidateRegions.firstNotNullOfOrNull { regionKey ->
            val regionData = findRegionProviders(results, regionKey) ?: return@firstNotNullOfOrNull null
            val services = toStreamingServiceList(regionData)
            if (services.isEmpty()) null else StreamingServicesResult(region = regionKey, services = services)
        }

        val resolved = resolvedFromPreferred ?: results.entries.firstNotNullOfOrNull { (regionKey, regionData) ->
            val services = toStreamingServiceList(regionData)
            if (services.isEmpty()) null else StreamingServicesResult(region = normalizeWatchRegion(regionKey), services = services)
        }

        watchProvidersCache[cacheKey] = CacheEntry(resolved, System.currentTimeMillis())
        return resolved
    }

    private fun findRegionProviders(
        allRegions: Map<String, TmdbWatchProviderRegion>,
        region: String
    ): TmdbWatchProviderRegion? {
        return allRegions.entries.firstOrNull { (key, _) ->
            key.equals(region, ignoreCase = true)
        }?.value
    }

    private fun toStreamingServiceList(regionData: TmdbWatchProviderRegion): List<StreamingServiceInfo> {
        val prioritizedLists = listOf(
            regionData.flatrate,
            regionData.free,
            regionData.ads,
            regionData.rent,
            regionData.buy
        )

        val deduped = LinkedHashMap<String, StreamingServiceInfo>()
        prioritizedLists.forEach { providers ->
            providers
                .sortedBy { it.displayPriority }
                .forEach providerLoop@{ provider ->
                    val canonicalName = canonicalStreamingServiceName(provider.providerName)
                    if (canonicalName.isBlank()) return@providerLoop
                    val key = canonicalName.lowercase(Locale.US)
                    if (deduped.containsKey(key)) return@providerLoop

                    val stableId = provider.providerId.takeIf { it > 0 } ?: canonicalName.hashCode()
                    val logoUrl = bundledStreamingLogoUri(canonicalName)
                        ?: provider.logoPath?.let { path ->
                            "https://image.tmdb.org/t/p/w92$path"
                        }
                    deduped[key] = StreamingServiceInfo(
                        id = stableId,
                        name = canonicalName,
                        logoUrl = logoUrl
                    )
                }
        }

        return deduped.values.take(10)
    }

    private fun CatalogConfig.isTop10Catalog(): Boolean {
        return id.contains("top10", ignoreCase = true) ||
            title.contains("Top 10", ignoreCase = true) ||
            sourceUrl?.contains("top-10", ignoreCase = true) == true ||
            sourceRef?.contains("top-10", ignoreCase = true) == true
    }

    private fun canonicalStreamingServiceName(raw: String?): String {
        val name = raw?.trim().orEmpty()
        if (name.isBlank()) return ""

        val normalized = name.lowercase(Locale.US)
        return when {
            normalized == "max" || normalized.contains("hbo") -> "HBO Max"
            normalized.contains("netflix") -> "Netflix"
            normalized.contains("prime") || normalized.contains("amazon") -> "Prime Video"
            normalized.contains("disney") -> "Disney+"
            normalized.contains("apple tv") -> "Apple TV+"
            normalized.contains("paramount") -> "Paramount+"
            normalized.contains("hulu") -> "Hulu"
            normalized.contains("peacock") -> "Peacock"
            normalized.contains("crunchyroll") -> "Crunchyroll"
            normalized.contains("discovery") -> "Discovery+"
            normalized.contains("mgm") -> "MGM+"
            normalized.contains("shudder") -> "Shudder"
            normalized.contains("starz") -> "Starz"
            normalized.contains("youtube") -> "YouTube"
            else -> name
        }
    }

    private fun bundledStreamingLogoUri(canonicalName: String): String? {
        val resId = when (canonicalName.lowercase(Locale.US)) {
            "netflix" -> R.raw.logo_netflix
            "hbo max" -> R.raw.logo_hbo_max
            "hulu" -> R.raw.logo_hulu
            "prime video" -> R.raw.logo_prime_video
            "disney+" -> R.raw.logo_disney_plus
            "paramount+" -> R.raw.logo_paramount_plus
            "peacock" -> R.raw.logo_peacock
            "crunchyroll" -> R.raw.logo_crunchyroll
            "discovery+" -> R.raw.logo_discovery_plus
            "mgm+" -> R.raw.logo_mgm_plus
            "shudder" -> R.raw.logo_shudder
            "starz" -> R.raw.logo_starz
            "apple tv+" -> R.drawable.apple_tv_plus_logo
            else -> null
        } ?: return null
        return "android.resource://${context.packageName}/$resId"
    }

    private fun normalizeWatchRegion(region: String?): String {
        val value = region?.trim()?.uppercase(Locale.US).orEmpty()
        return value.takeIf { it.length == 2 } ?: "US"
    }

    /**
     * Items of a TMDB page added as a catalog (list / collection / company /
     * network / person / keyword / genre). Each kind maps onto the TMDB query
     * source resolvers, so paging, sorting and caching stay in one place.
     * Pages without an explicit movie/tv scope return both, interleaved.
     */
    private suspend fun loadTmdbCatalogRefs(
        sourceUrl: String?,
        sourceRef: String?
    ): List<Pair<MediaType, Int>> = coroutineScope {
        val parsed = parseTmdbCatalogRef(sourceRef)
            ?: sourceUrl?.let { CatalogUrlParser.parseTmdb(it) }
            ?: return@coroutineScope emptyList()
        val requestLanguage = contentLanguage
        val cacheKey = "$requestLanguage:${parsed.kind}:${parsed.id}:${parsed.mediaType.orEmpty()}"
        getFromCache(tmdbCatalogRefsCache, cacheKey)?.let { return@coroutineScope it }
        val limit = TMDB_CATALOG_MAX_ITEMS
        if (parsed.kind == "person") {
            val credits = try {
                tmdbApi.getPersonDetails(parsed.id, apiKey, language = requestLanguage).combinedCredits
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return@coroutineScope emptyList()
            }
            val refs = (credits?.cast.orEmpty() + credits?.crew.orEmpty())
                .filter { it.id > 0 && !it.adult && it.mediaType in tmdbMediaScopes(parsed.mediaType) }
                .sortedByDescending { it.popularity }
                .distinctBy { it.mediaType to it.id }
                .take(limit)
                .map { item ->
                    val type = if (item.mediaType == "tv") MediaType.TV else MediaType.MOVIE
                    cacheCatalogPreview(item, type, requestLanguage)
                    type to item.id
                }
            if (requestLanguage == contentLanguage) {
                tmdbCatalogRefsCache[cacheKey] = CacheEntry(refs, System.currentTimeMillis())
            }
            return@coroutineScope refs
        }
        fun discover(mediaType: String, param: String) = CatalogQuerySourceConfig(
            kind = CatalogQuerySourceKind.TMDB_DISCOVER,
            mediaType = mediaType,
            sortBy = "popularity.desc",
            discoverParams = mapOf(param to parsed.id.toString())
        )
        val sources: List<CatalogQuerySourceConfig> = when (parsed.kind) {
            "list" -> listOf(CatalogQuerySourceConfig(kind = CatalogQuerySourceKind.TMDB_LIST, tmdbListId = parsed.id))
            "collection" -> listOf(
                CatalogQuerySourceConfig(kind = CatalogQuerySourceKind.TMDB_COLLECTION, tmdbCollectionId = parsed.id)
            )
            // TMDB has no with_networks for movies, so a network page is series only.
            "network" -> listOf(discover("tv", "with_networks"))
            "company" -> tmdbMediaScopes(parsed.mediaType).map { discover(it, "with_companies") }
            "keyword" -> tmdbMediaScopes(parsed.mediaType).map { discover(it, "with_keywords") }
            "genre" -> tmdbMediaScopes(parsed.mediaType).map { discover(it, "with_genres") }
            else -> emptyList()
        }
        if (sources.isEmpty()) return@coroutineScope emptyList()
        val perSource = sources.map { source ->
            async {
                val type = when (parsed.mediaType) {
                    "movie" -> MediaType.MOVIE
                    "tv" -> MediaType.TV
                    else -> null
                }
                resolveCatalogQuerySourceRefs(source, limit = limit, mediaType = type)
            }
        }.map { it.await() }
        // Interleave movies and series so a mixed page doesn't start with 100 movies.
        val queues = perSource.map { ArrayDeque(it.refs) }
        val refs = LinkedHashSet<Pair<MediaType, Int>>()
        while (queues.any { it.isNotEmpty() } && refs.size < limit) {
            queues.forEach { queue -> if (queue.isNotEmpty() && refs.size < limit) refs.add(queue.removeFirst()) }
        }
        refs.toList().also { result ->
            if (perSource.none { it.failed } && requestLanguage == contentLanguage) {
                tmdbCatalogRefsCache[cacheKey] = CacheEntry(result, System.currentTimeMillis())
            }
        }
    }

    private fun tmdbMediaScopes(mediaType: String?): List<String> = when (mediaType) {
        "movie" -> listOf("movie")
        "tv" -> listOf("tv")
        else -> listOf("movie", "tv")
    }

    /** Parses the stored "tmdb:{kind}:{id}:{mediaType}" reference. */
    private fun parseTmdbCatalogRef(sourceRef: String?): ParsedCatalogUrl.Tmdb? {
        val raw = sourceRef?.trim()?.takeIf { it.startsWith("tmdb:", ignoreCase = true) } ?: return null
        val parts = raw.split(':')
        if (parts.size < 3) return null
        val kind = parts[1].lowercase(Locale.US).takeIf { it in CatalogUrlParser.TMDB_KINDS } ?: return null
        val id = parts[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val mediaType = parts.getOrNull(3)?.takeIf { it.isNotBlank() }?.lowercase(Locale.US)
        if (mediaType != null && mediaType !in setOf("movie", "tv")) return null
        return ParsedCatalogUrl.Tmdb(kind = kind, id = id, mediaType = mediaType, slug = null)
    }













    private fun fetchUrl(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", OkHttpProvider.userAgentOr("Mozilla/5.0 (Android TV; ARVIO)"))
            .build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }
}
private fun Any?.toIntSafe(): Int? {
    return when (this) {
        is Number -> this.toInt()
        is String -> this.toIntOrNull()
        else -> null
    }
}

private fun normalizeRating(raw: String): String? {
    val value = raw.trim().replace(',', '.').toFloatOrNull() ?: return null
    if (value <= 0f || value > 10f) return null
    return String.format(Locale.US, "%.1f", value)
}

private fun formatTmdbRating(voteAverage: Float): String =
    normalizeRating(voteAverage.toString()).orEmpty()

// Extension functions to convert API responses to domain models

private fun TmdbMediaItem.toMediaItem(defaultType: MediaType): MediaItem {
    val type = when (mediaType) {
        "tv" -> MediaType.TV
        "movie" -> MediaType.MOVIE
        else -> defaultType
    }

    val dateStr = releaseDate ?: firstAirDate ?: ""
    val year = dateStr.take(4)

    return MediaItem(
        id = id,
        title = title?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: originalTitle?.takeIf { it.isNotBlank() }
            ?: originalName?.takeIf { it.isNotBlank() }
            ?: "Unknown",
        subtitle = if (type == MediaType.MOVIE) "Movie" else "TV Series",
        overview = overview ?: "",
        year = year,
        releaseDate = formatDate(dateStr),
        imdbRating = "",
        tmdbRating = formatTmdbRating(voteAverage),
        mediaType = type,
        image = posterPath?.let { "${Constants.IMAGE_BASE}$it" }
            ?: backdropPath?.let { "${Constants.BACKDROP_BASE}$it" }
            ?: "",
        backdrop = backdropPath?.let { "${Constants.BACKDROP_BASE_LARGE}$it" },
        genreIds = genreIds,
        originalLanguage = originalLanguage,
        originalTitle = originalTitle?.takeIf { it.isNotBlank() }
            ?: originalName?.takeIf { it.isNotBlank() },
        character = character ?: "",
        popularity = popularity
    )
}

private fun TmdbMovieDetails.toMediaItem(): MediaItem {
    val year = releaseDate?.take(4) ?: ""
    val hours = (runtime ?: 0) / 60
    val minutes = (runtime ?: 0) % 60
    val duration = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    return MediaItem(
        id = id,
        title = title.takeIf { it.isNotBlank() }
            ?: originalTitle?.takeIf { it.isNotBlank() }
            ?: "Unknown",
        subtitle = "Movie",
        overview = overview ?: "",
        year = year,
        releaseDate = formatDate(releaseDate ?: ""),
        duration = duration,
        rating = if (adult) "R" else "PG-13",
        imdbRating = "",
        tmdbRating = formatTmdbRating(voteAverage),
        mediaType = MediaType.MOVIE,
        image = posterPath?.let { "${Constants.IMAGE_BASE}$it" }
            ?: backdropPath?.let { "${Constants.BACKDROP_BASE}$it" }
            ?: "",
        backdrop = backdropPath?.let { "${Constants.BACKDROP_BASE_LARGE}$it" },
        originalLanguage = originalLanguage,
        originalTitle = originalTitle?.takeIf { it.isNotBlank() },
        budget = budget,
        genreIds = genres.map { it.id }
    )
}

private fun TmdbTvDetails.toMediaItem(): MediaItem {
    val year = firstAirDate?.take(4) ?: ""
    val runtime = episodeRunTime.firstOrNull() ?: 45
    val duration = "${runtime}m"
    val actualSeasonCount = seasons
        .asSequence()
        .filter { it.seasonNumber > 0 && it.episodeCount > 0 }
        .map { it.seasonNumber }
        .distinct()
        .count()
        .takeIf { it > 0 }
        ?: numberOfSeasons.coerceAtLeast(1)

    return MediaItem(
        id = id,
        title = name.takeIf { it.isNotBlank() }
            ?: originalName?.takeIf { it.isNotBlank() }
            ?: "Unknown",
        subtitle = "TV Series",
        overview = overview ?: "",
        year = year,
        releaseDate = formatDate(firstAirDate ?: ""),
        duration = duration,
        imdbRating = "",
        tmdbRating = formatTmdbRating(voteAverage),
        mediaType = MediaType.TV,
        image = posterPath?.let { "${Constants.IMAGE_BASE}$it" }
            ?: backdropPath?.let { "${Constants.BACKDROP_BASE}$it" }
            ?: "",
        backdrop = backdropPath?.let { "${Constants.BACKDROP_BASE_LARGE}$it" },
        originalLanguage = originalLanguage,
        originalTitle = originalName?.takeIf { it.isNotBlank() },
        isOngoing = status == "Returning Series",
        totalEpisodes = actualSeasonCount,
        status = status,
        genreIds = genres.map { it.id }
    )
}

private fun TmdbEpisode.toEpisode(): Episode {
    return Episode(
        id = id,
        episodeNumber = episodeNumber,
        seasonNumber = seasonNumber,
        name = name,
        overview = overview ?: "",
        stillPath = stillPath?.let { "${Constants.IMAGE_BASE}$it" },
        voteAverage = voteAverage,
        runtime = runtime ?: 0,
        airDate = airDate ?: ""
    )
}



private fun TmdbPersonDetails.toPersonDetails(): PersonDetails {
    val knownFor = combinedCredits?.cast
        ?.filter { it.posterPath != null && (it.mediaType == "movie" || (it.mediaType == "tv" && !ShowMergeTable.isSecondary(it.id))) }
        ?.sortedByDescending { it.voteCount }
        ?.take(20)
        ?.map {
            it.toMediaItem(
                if (it.mediaType == "tv") MediaType.TV else MediaType.MOVIE
            )
        } ?: emptyList()

    return PersonDetails(
        id = id,
        name = name,
        biography = biography ?: "",
        placeOfBirth = placeOfBirth,
        birthday = birthday,
        profilePath = profilePath?.let { "${Constants.IMAGE_BASE}$it" },
        knownFor = knownFor
    )
}

private fun formatDate(dateStr: String): String {
    if (dateStr.isEmpty()) return ""
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outputFormat = SimpleDateFormat("d MMM yyyy", Locale.US)  // "12 Jan 2025" format
        val date = inputFormat.parse(dateStr)
        date?.let { outputFormat.format(it) } ?: dateStr
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e

        dateStr
    }
}

private object MediaRegexes {
    val YEAR_SUFFIX_REGEX = Regex("""\s+\(\d{4}\)$""")
    val DIGITS_REGEX = Regex("""\d+""")
    val TYPED_ID_REGEX = Regex("""^(movie|series|tv|show|shows):(\d+)$""", RegexOption.IGNORE_CASE)
    val IMDB_ID_REGEX = Regex("""tt\d{5,}""", RegexOption.IGNORE_CASE)
}
