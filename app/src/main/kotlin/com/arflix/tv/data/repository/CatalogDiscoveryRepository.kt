package com.arflix.tv.data.repository

import com.arflix.tv.data.model.CatalogDiscoveryResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogDiscoveryRepository @Inject constructor() {
    suspend fun searchCatalogLists(query: String): Result<List<CatalogDiscoveryResult>> = Result.success(emptyList())
}
