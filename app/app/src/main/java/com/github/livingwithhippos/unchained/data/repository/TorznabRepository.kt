package com.github.livingwithhippos.unchained.data.repository

import android.content.Context
import androidx.core.net.toUri
import com.github.livingwithhippos.unchained.data.local.CompleteRemoteService
import com.github.livingwithhippos.unchained.data.model.torznab.SearchRSS
import com.github.livingwithhippos.unchained.data.model.torznab.parseSearchRss
import com.github.livingwithhippos.unchained.data.model.torznab.rssToScrapedItems
import com.github.livingwithhippos.unchained.di.ClassicClient
import com.github.livingwithhippos.unchained.plugins.ParserResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Talks to a plain Torznab indexer: the address is used as-is and the standard `t=search` query is
 * appended. Debrid Media Manager exposes exactly this shape at
 * `https://debridmediamanager.com/api/torznab` (its API key comes with a DMM sponsorship), and any
 * self-hosted Torznab endpoint works the same way.
 *
 * Deliberately separate from [JackettRepository]: that one appends Jackett's
 * `/api/v2.0/indexers/<indexer>/results/torznab/api` path and Prowlarr appends `/api/v1/search`.
 */
class TorznabRepository
@Inject
constructor(
    @param:ClassicClient private val client: OkHttpClient,
    @ApplicationContext private val applicationContext: Context,
) {

    fun performSearch(
        service: CompleteRemoteService,
        query: String,
        categories: String = "",
    ) = flow {
        val url = buildUrl(service, query, categories)
        if (url == null) {
            emit(ParserResult.SourceError)
            return@flow
        }

        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(ParserResult.SourceError)
                    return@flow
                }
                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    emit(ParserResult.NetworkBodyError)
                    return@flow
                }
                // A Torznab error is well-formed XML, so parse failures must not be mistaken for
                // "no results".
                if (body.contains("<error")) {
                    Timber.e("Torznab indexer returned an error document")
                    emit(ParserResult.SourceError)
                    return@flow
                }
                val search: SearchRSS = parseSearchRss(body)
                val items = rssToScrapedItems(applicationContext, search)
                emit(ParserResult.Results(items.first))
            }
        } catch (ex: Exception) {
            // JackettRepository lets this escape the flow; here a dead indexer must degrade to a
            // source error instead of killing the caller's coroutine scope.
            Timber.e(ex, "Torznab search failed")
            emit(ParserResult.SourceError)
        }
    }
        .flowOn(Dispatchers.IO)

    /** `t=caps` doubles as the Settings "Test" call: 200 with a `<caps>` body means it answers. */
    fun buildTestUrl(service: CompleteRemoteService): String? =
        buildUrl(service, query = null, categories = "", type = "caps")

    private fun buildUrl(
        service: CompleteRemoteService,
        query: String?,
        categories: String,
        type: String = "search",
    ): String? =
        try {
            val base = service.address.trim().trimEnd('/').toUri()
            if (base.scheme.isNullOrBlank() || base.encodedAuthority.isNullOrBlank()) {
                null
            } else {
                base.buildUpon()
                    .encodedQuery(null)
                    .fragment(null)
                    .appendQueryParameter("apikey", service.apiToken)
                    .appendQueryParameter("t", type)
                    .apply {
                        if (categories.isNotBlank()) appendQueryParameter("cat", categories)
                        if (!query.isNullOrBlank()) appendQueryParameter("q", query)
                    }
                    .build()
                    .toString()
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Error parsing torznab url from $service")
            null
        }
}
