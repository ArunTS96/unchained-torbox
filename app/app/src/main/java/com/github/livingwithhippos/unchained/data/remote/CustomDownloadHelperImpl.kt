package com.github.livingwithhippos.unchained.data.remote

import javax.inject.Inject
import okhttp3.ResponseBody
import retrofit2.Response

class CustomDownloadHelperImpl @Inject constructor(private val customDownload: CustomDownload) :
    CustomDownloadHelper {
    override suspend fun getFile(url: String): Response<ResponseBody> = customDownload.getFile(url)
}
