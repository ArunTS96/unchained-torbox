package com.github.livingwithhippos.unchained.data.remote

import android.net.Uri
import com.github.livingwithhippos.unchained.data.local.TorboxKeyStore
import com.github.livingwithhippos.unchained.data.model.DownloadItem
import com.github.livingwithhippos.unchained.data.model.InnerTorrentFile
import com.github.livingwithhippos.unchained.data.model.TorrentItem
import com.github.livingwithhippos.unchained.di.ClassicClient
import com.github.livingwithhippos.unchained.utilities.BASE_URL
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Small TorBox-only HTTP client. */
class TorboxClient
@Inject
constructor(
    @ClassicClient private val httpClient: OkHttpClient,
    private val keyStore: TorboxKeyStore,
) {

    suspend fun validateKey(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
            authenticatedGet("torrents/mylist") {
                addQueryParameter("limit", "1")
                addQueryParameter("offset", "0")
            }.use { it.isSuccessful }
            }.getOrDefault(false)
        }

    suspend fun createMagnet(magnet: String): Result<Int> =
        createTorrent(magnet = magnet, torrentFile = null)

    suspend fun createTorrentFile(torrentFile: ByteArray): Result<Int> =
        createTorrent(magnet = null, torrentFile = torrentFile)

    suspend fun listTorrents(): List<TorrentItem> =
        withContext(Dispatchers.IO) {
            runCatching {
            authenticatedGet("torrents/mylist") {
                addQueryParameter("limit", "1000")
                addQueryParameter("offset", "0")
            }.use { response ->
                if (!response.isSuccessful) emptyList()
                else parseTorrentCollection(response.body?.string().orEmpty())
            }
            }.getOrDefault(emptyList())
        }

    suspend fun getTorrent(id: String): TorrentItem? =
        withContext(Dispatchers.IO) {
            runCatching {
            authenticatedGet("torrents/mylist") {
                addQueryParameter("id", id)
                addQueryParameter("bypass_cache", "true")
            }.use { response ->
                if (!response.isSuccessful) null
                else parseTorrentCollection(response.body?.string().orEmpty()).firstOrNull()
            }
            }.getOrNull()
        }

    suspend fun deleteTorrent(id: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
            val numericId = id.toIntOrNull() ?: return@runCatching false
            authenticatedPost(
                "torrents/controltorrent",
                """{"operation":"delete","torrent_id":$numericId}""".toRequestBody(JSON),
            ).use { it.isSuccessful }
            }.getOrDefault(false)
        }

    /**
     * Resolves a non-shareable TorBox reference into a short-lived CDN URL. The API-key query
     * parameter is created only inside this method and is never returned to the app UI.
     */
    suspend fun resolveDownloadReference(reference: String): String? =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(reference)
            if (uri.scheme != TORBOX_SCHEME || uri.host != DOWNLOAD_HOST) return@withContext null
            val torrentId =
                uri.getQueryParameter("torrent_id")?.toIntOrNull()
                    ?: return@withContext null
            val fileId =
                uri.getQueryParameter("file_id")?.toIntOrNull()
                    ?: return@withContext null
            requestDownloadUrl(torrentId, fileId)
        }

    fun downloadItems(torrent: TorrentItem): List<DownloadItem> {
        val torrentId = torrent.id.toIntOrNull() ?: return emptyList()
        val files = torrent.files.orEmpty()
        return files
            .filter { it.selected == 1 || files.size == 1 }
            .map { file ->
                val name = file.path.substringAfterLast('/').ifBlank { torrent.filename }
                DownloadItem(
                    id = torrentId.toString() + "-" + file.id,
                    filename = name,
                    mimeType = null,
                    fileSize = file.bytes,
                    link = torboxReference(torrentId, file.id),
                    host = TORBOX_HOST,
                    hostIcon = null,
                    chunks = 1,
                    crc = null,
                    download = torboxReference(torrentId, file.id),
                    streamable = 0,
                    generated = null,
                    type = null,
                    alternative = null,
                )
            }
    }

    private suspend fun createTorrent(magnet: String?, torrentFile: ByteArray?): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val key = requireKey()
                val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                if (magnet != null) bodyBuilder.addFormDataPart("magnet", magnet)
                if (torrentFile != null) {
                    bodyBuilder.addFormDataPart(
                        "file",
                        "upload.torrent",
                        torrentFile.toRequestBody("application/octet-stream".toMediaType()),
                    )
                }
                val request =
                    Request.Builder()
                        .url(BASE_URL + "torrents/createtorrent")
                        .header("Authorization", "Bearer " + key)
                        .post(bodyBuilder.build())
                        .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("TorBox rejected torrent creation")
                    }
                    extractId(response.body?.string().orEmpty())
                        ?: throw IOException("TorBox did not return a torrent id")
                }
            }
        }

    private suspend fun requestDownloadUrl(torrentId: Int, fileId: Int): String? {
        val key = requireKey()
        val url =
            (BASE_URL + "torrents/requestdl")
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("token", key)
                .addQueryParameter("torrent_id", torrentId.toString())
                .addQueryParameter("file_id", fileId.toString())
                .addQueryParameter("redirect", "true")
                .addQueryParameter("append_name", "true")
                .build()
        val request = Request.Builder().url(url).get().build()

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val finalUrl = response.request.url.toString()
            if (finalUrl.contains("api.torbox.app")) {
                extractString(response.body?.string().orEmpty())
            } else {
                finalUrl
            }
        }
    }

    private fun authenticatedGet(
        path: String,
        configure: okhttp3.HttpUrl.Builder.() -> Unit = {},
    ): okhttp3.Response {
        val url = BASE_URL.toHttpUrl().newBuilder().addPathSegments(path).apply(configure).build()
        val request =
            Request.Builder().url(url).header("Authorization", "Bearer " + requireKey()).get().build()
        return httpClient.newCall(request).execute()
    }

    private fun authenticatedPost(path: String, body: okhttp3.RequestBody): okhttp3.Response {
        val url = BASE_URL.toHttpUrl().newBuilder().addPathSegments(path).build()
        val request =
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + requireKey())
                .post(body)
                .build()
        return httpClient.newCall(request).execute()
    }

    private fun requireKey(): String =
        keyStore.read()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("TorBox API key is not configured")

    private fun parseTorrentCollection(raw: String): List<TorrentItem> {
        if (raw.isBlank()) return emptyList()
        val root = JSONObject(raw)
        val data = root.opt("data") ?: root
        val values =
            when (data) {
                is JSONArray -> (0 until data.length()).mapNotNull { data.optJSONObject(it) }
                is JSONObject -> {
                    val nested = data.opt("torrents") ?: data.opt("items")
                    if (nested is JSONArray) {
                        (0 until nested.length()).mapNotNull { nested.optJSONObject(it) }
                    } else listOf(data)
                }
                else -> emptyList()
            }
        return values.mapNotNull(::parseTorrent)
    }

    private fun parseTorrent(json: JSONObject): TorrentItem? {
        val id = json.optIntOrNull("id") ?: json.optIntOrNull("torrent_id") ?: return null
        val rawState =
            json.optStringAny("download_state", "status", "state").ifBlank { "queued" }
        // Unchained only lists torrents whose status is "downloaded" or "ready", and it
        // expects progress as a 0..100 percentage. TorBox sends download_state plus a
        // 0..1 ratio, so an unmapped torrent renders as "queued" at 1% and never shows.
        val status = torboxStatus(rawState)
        val size = json.optLongAny("size", "original_bytes", "bytes")
        val progress =
            json.optDoubleAny("progress")
                ?.let { raw ->
                    (if (raw <= 1.0) raw * 100.0 else raw).coerceIn(0.0, 100.0).toFloat()
                }
                ?: if (status == "downloaded") 100f else 0f
        val files = parseFiles(json.optJSONArray("files"), id)
        val links = files.map { torboxReference(id, it.id) }
        val filename =
            json.optStringAny("name", "filename", "short_name")
                .ifBlank { "TorBox torrent " + id }
        return TorrentItem(
            id = id.toString(),
            filename = filename,
            originalFilename = json.optStringAny("original_filename", "name").ifBlank { null },
            hash = json.optStringAny("hash", "infohash"),
            bytes = json.optLongAny("total_downloaded", "downloaded", "downloaded_bytes", "bytes"),
            originalBytes = size.takeIf { it > 0 },
            host = TORBOX_HOST,
            split = 0,
            progress = progress,
            status = status,
            added = json.optStringAny("added", "created_at", "createdAt"),
            files = files,
            links = links,
            ended = json.optStringAny("ended", "completed_at", "completedAt").ifBlank { null },
            speed = json.optIntOrNull("download_speed") ?: json.optIntOrNull("speed"),
            seeders = json.optIntOrNull("seeds") ?: json.optIntOrNull("seeders"),
        )
    }

    /** Maps TorBox download_state onto the statuses Unchained's UI understands. */
    private fun torboxStatus(state: String): String =
        when (state.lowercase()) {
            "cached",
            "completed",
            "download finished" -> "downloaded"
            "downloading",
            "incomplete",
            "uploading",
            "metadl",
            "checking" -> "downloading"
            "expired",
            "reported missing",
            "error" -> "error"
            else -> "queued"
        }

    private fun parseFiles(array: JSONArray?, torrentId: Int): List<InnerTorrentFile> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optIntOrNull("id") ?: item.optIntOrNull("file_id") ?: index
            val path =
                item.optStringAny("path", "name", "short_name", "s3_path")
                    .ifBlank { "torrent-" + torrentId + "-file-" + id }
            val bytes = item.optLongAny("bytes", "size")
            val selected =
                when (val rawSelected = item.opt("selected")) {
                    is Number -> rawSelected.toInt()
                    is Boolean -> if (rawSelected) 1 else 0
                    else -> 1
                }
            InnerTorrentFile(id, path, bytes, selected)
        }
    }

    private fun extractId(raw: String): Int? {
        if (raw.isBlank()) return null
        val root = JSONObject(raw)
        val data = root.opt("data")
        return when (data) {
            is Number -> data.toInt()
            is JSONObject -> data.optIntOrNull("id") ?: data.optIntOrNull("torrent_id")
            else -> root.optIntOrNull("id") ?: root.optIntOrNull("torrent_id")
        }
    }

    private fun extractString(raw: String): String? {
        if (raw.isBlank()) return null
        val root = JSONObject(raw)
        val value = root.opt("data")
        return when (value) {
            is String -> value
            is JSONObject -> value.optStringAny("url", "download_url", "link").ifBlank { null }
            else -> root.optStringAny("url", "download_url", "link").ifBlank { null }
        }
    }

    private fun torboxReference(torrentId: Int, fileId: Int): String =
        TORBOX_SCHEME + "://" + DOWNLOAD_HOST + "?torrent_id=" + torrentId + "&file_id=" + fileId

    private fun JSONObject.optStringAny(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key ->
            optString(key).takeIf { it.isNotBlank() && it != "null" }
        } ?: ""

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) {
            optInt(key).takeIf { it != 0 || opt(key).toString() == "0" }
        } else null

    private fun JSONObject.optLongAny(vararg keys: String): Long =
        keys.firstNotNullOfOrNull { key ->
            if (has(key) && !isNull(key)) optLong(key) else null
        } ?: 0L

    private fun JSONObject.optDoubleAny(vararg keys: String): Double? =
        keys.firstNotNullOfOrNull { key ->
            if (has(key) && !isNull(key)) optDouble(key) else null
        }

    companion object {
        const val TORBOX_SCHEME = "torbox"
        const val DOWNLOAD_HOST = "download"
        const val TORBOX_HOST = "TorBox"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
