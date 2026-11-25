package com.github.livingwithhippos.unchained.data.remote

import okhttp3.ResponseBody
import retrofit2.Response

interface CustomDownloadHelper {

    suspend fun getFile(url: String): Response<ResponseBody>
}
