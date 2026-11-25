package com.github.livingwithhippos.unchained.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

/** Fetches remote files that are not part of the TorBox API — e.g. a .torrent behind a URL. */
interface CustomDownload {

    @Streaming @GET suspend fun getFile(@Url url: String): Response<ResponseBody>
}
