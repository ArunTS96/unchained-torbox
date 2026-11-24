package com.github.livingwithhippos.unchained.lists.view

import com.github.livingwithhippos.unchained.data.model.DownloadItem

/**
 * Implemented by the folder screen: the download list adapter that used to live here is gone with the
 * Downloads tab, but the folder list still reports item clicks through this contract.
 */
interface DownloadListListener {
    fun onClick(item: DownloadItem)
}
