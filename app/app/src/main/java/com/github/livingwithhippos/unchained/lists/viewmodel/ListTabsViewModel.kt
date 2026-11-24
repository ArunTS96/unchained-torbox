package com.github.livingwithhippos.unchained.lists.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.liveData
import com.github.livingwithhippos.unchained.data.model.DownloadItem
import com.github.livingwithhippos.unchained.data.model.TorrentItem
import com.github.livingwithhippos.unchained.data.model.UnchainedNetworkException
import com.github.livingwithhippos.unchained.data.remote.TorboxClient
import com.github.livingwithhippos.unchained.data.repository.DownloadRepository
import com.github.livingwithhippos.unchained.data.repository.TorrentsRepository
import com.github.livingwithhippos.unchained.data.repository.UnrestrictRepository
import com.github.livingwithhippos.unchained.lists.model.TorrentPagingSource
import com.github.livingwithhippos.unchained.utilities.DOWNLOADS_TAB
import com.github.livingwithhippos.unchained.utilities.EitherResult
import com.github.livingwithhippos.unchained.utilities.Event
import com.github.livingwithhippos.unchained.utilities.postEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

/**
 * A [ViewModel] subclass. It offers LiveData to be observed to populate lists with paging support
 */
@HiltViewModel
class ListTabsViewModel
@Inject
constructor(
    private val savedStateHandle: SavedStateHandle,
    private val preferences: SharedPreferences,
    private val downloadRepository: DownloadRepository,
    private val torrentsRepository: TorrentsRepository,
    private val unrestrictRepository: UnrestrictRepository,
) : ViewModel() {

    // stores the last query value
    // Seeded with a blank query: switchMap only emits once this has a value, and nothing calls
    // setListFilter until the user touches the search box, so an unseeded LiveData left the
    // list empty on every cold start.
    private val queryLiveData = MutableLiveData<String>("")

    val torrentsLiveData: LiveData<PagingData<TorrentItem>> =
        queryLiveData.switchMap { query: String ->
            val size = getPagingSize()
            val initialSize = max(size, INITIAL_LOAD)
            Pager(PagingConfig(pageSize = size, initialLoadSize = initialSize)) {
                    TorrentPagingSource(torrentsRepository, query)
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val errorsLiveData = MutableLiveData<Event<List<UnchainedNetworkException>>>()

    val deletedTorrentLiveData = MutableLiveData<Event<Int>>()

    val eventLiveData = MutableLiveData<Event<ListEvent>>()

    // tab index of the list to be scrolled to the top, see ListsTabFragment fabScrollToTop
    val scrollToTopLiveData = MutableLiveData<Event<Int>>()

    private fun getPagingSize(): Int {
        return min(preferences.getInt("paging_size", 50), MAX_PAGE_SIZE)
    }

    fun setSelectedTab(tabID: Int) {
        savedStateHandle[KEY_SELECTED_TAB] = tabID
    }

    fun getSelectedTab(): Int {
        return savedStateHandle[KEY_SELECTED_TAB] ?: DOWNLOADS_TAB
    }

    fun setListFilter(query: String?) {
        // Avoid updating the lists if the query hasn't changed. We don't check for cases but we
        // could
        if (queryLiveData.value != query) queryLiveData.postValue(query?.trim() ?: "")
    }

    fun deleteAllTorrents() {
        viewModelScope.launch {
            do {
                val torrents = torrentsRepository.getTorrentsList(0, 1, 50)
                torrents.forEach { torrentsRepository.deleteTorrent(it.id) }
            } while (torrents.size >= 50)

            deletedTorrentLiveData.postEvent(TORRENTS_DELETED_ALL)
        }
    }

    fun deleteTorrents(torrents: List<TorrentItem>) {
        viewModelScope.launch {
            torrents.forEach { torrentsRepository.deleteTorrent(it.id) }
            if (torrents.size > 1) deletedTorrentLiveData.postEvent(TORRENTS_DELETED)
            else deletedTorrentLiveData.postEvent(TORRENT_DELETED)
        }
    }

    fun postEventNotice(event: ListEvent) {
        eventLiveData.postEvent(event)
    }

    fun postScrollToTop(tab: Int) {
        scrollToTopLiveData.postEvent(tab)
    }

    companion object {
        const val KEY_SELECTED_TAB = "selected_tab_key"
        const val TORRENT_DELETED = -1
        const val TORRENTS_DELETED = -2
        const val TORRENTS_DELETED_ALL = -3
        const val TORRENT_NOT_DELETED = -4
        const val DOWNLOAD_DELETED = -1
        const val DOWNLOADS_DELETED = -2
        const val DOWNLOADS_DELETED_ALL = -3
        const val DOWNLOAD_NOT_DELETED = -4

        private const val MAX_PAGE_SIZE = 2500

        private const val INITIAL_LOAD = 100
    }
}

sealed class ListEvent {
    data class DownloadItemClick(val item: DownloadItem) : ListEvent()

    data class TorrentItemClick(val item: TorrentItem) : ListEvent()

    data class OpenTorrent(val item: TorrentItem) : ListEvent()


    data object NewDownload : ListEvent()
}
