package com.github.livingwithhippos.unchained.lists.view

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.paging.PagingData
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.github.livingwithhippos.unchained.R
import com.github.livingwithhippos.unchained.base.UnchainedFragment
import com.github.livingwithhippos.unchained.data.model.APIError
import com.github.livingwithhippos.unchained.data.remote.TorboxClient
import com.github.livingwithhippos.unchained.data.model.ApiConversionError
import com.github.livingwithhippos.unchained.data.model.DownloadItem
import com.github.livingwithhippos.unchained.data.model.EmptyBodyError
import com.github.livingwithhippos.unchained.data.model.NetworkError
import com.github.livingwithhippos.unchained.data.model.TorrentItem
import com.github.livingwithhippos.unchained.databinding.FragmentDownloadsListBinding
import com.github.livingwithhippos.unchained.databinding.FragmentTabListsBinding
import com.github.livingwithhippos.unchained.databinding.FragmentTorrentsListBinding
import com.github.livingwithhippos.unchained.lists.viewmodel.ListEvent
import com.github.livingwithhippos.unchained.lists.viewmodel.ListTabsViewModel
import com.github.livingwithhippos.unchained.lists.viewmodel.ListTabsViewModel.Companion.DOWNLOADS_DELETED
import com.github.livingwithhippos.unchained.lists.viewmodel.ListTabsViewModel.Companion.DOWNLOADS_DELETED_ALL
import com.github.livingwithhippos.unchained.lists.viewmodel.ListTabsViewModel.Companion.DOWNLOAD_DELETED
import com.github.livingwithhippos.unchained.lists.viewmodel.ListTabsViewModel.Companion.DOWNLOAD_NOT_DELETED
import com.github.livingwithhippos.unchained.lists.viewmodel.ListTabsViewModel.Companion.TORRENTS_DELETED
import com.github.livingwithhippos.unchained.lists.viewmodel.ListTabsViewModel.Companion.TORRENTS_DELETED_ALL
import com.github.livingwithhippos.unchained.lists.viewmodel.ListTabsViewModel.Companion.TORRENT_DELETED
import com.github.livingwithhippos.unchained.lists.viewmodel.ListTabsViewModel.Companion.TORRENT_NOT_DELETED
import com.github.livingwithhippos.unchained.statemachine.authentication.FSMAuthenticationEvent
import com.github.livingwithhippos.unchained.statemachine.authentication.FSMAuthenticationState
import com.github.livingwithhippos.unchained.utilities.DOWNLOADS_TAB
import com.github.livingwithhippos.unchained.utilities.EventObserver
import com.github.livingwithhippos.unchained.utilities.TORRENTS_TAB
import com.github.livingwithhippos.unchained.utilities.beforeSelectionStatusList
import com.github.livingwithhippos.unchained.utilities.extension.delayedScrolling
import com.github.livingwithhippos.unchained.utilities.extension.getApiErrorMessage
import com.github.livingwithhippos.unchained.utilities.extension.getDownloadedFileUri
import com.github.livingwithhippos.unchained.utilities.extension.getThemeColor
import com.github.livingwithhippos.unchained.utilities.extension.getThemedDrawable
import com.github.livingwithhippos.unchained.utilities.extension.showToast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

/**
 * A simple [UnchainedFragment] subclass. It is capable of showing a list of both [DownloadItem] and
 * [TorrentItem] switched with a tab layout.
 */
@AndroidEntryPoint
class ListsTabFragment : UnchainedFragment() {

    private val viewModel: ListTabsViewModel by activityViewModels()

    // used to simulate a debounce effect while typing on the search bar
    var queryJob: Job? = null

    private var _binding: FragmentTabListsBinding? = null
    private val binding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTabListsBinding.inflate(inflater, container, false)

        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.lists_bar, menu)

                    val searchItem = menu.findItem(R.id.search)
                    val searchView = searchItem.actionView as SearchView
                    // listens to the user typing in the search bar

                    searchView.setOnQueryTextListener(
                        object : SearchView.OnQueryTextListener {
                            // since there is a 500ms delay on new queries, this will help if the
                            // user types
                            // something and press search in less than half sec. May be unnecessary.
                            // The value
                            // is checked anyway in the ViewModel to avoid reloading with the same
                            // query as
                            // the last one.
                            override fun onQueryTextSubmit(query: String?): Boolean {
                                viewModel.setListFilter(query)
                                return true
                            }

                            override fun onQueryTextChange(newText: String?): Boolean {
                                // simulate debounce
                                queryJob?.cancel()

                                queryJob = lifecycleScope.launch {
                                    delay(500.milliseconds)
                                    if (isActive) viewModel.setListFilter(newText)
                                }
                                return true
                            }
                        }
                    )
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.search -> {
                            true
                        }

                        R.id.delete_all_torrents -> {
                            showDeleteAllDialog()
                            true
                        }

                        else -> false
                    }
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )

        binding.fabNewDownload.setOnClickListener {
            val action = ListsTabFragmentDirections.actionListTabsDestToNewDownloadFragment()
            findNavController().navigate(action)
        }

        binding.fabScrollToTop.setOnClickListener {
            viewModel.postScrollToTop(TORRENTS_TAB)
        }

        // an external link has been shared with the app
        activityViewModel.externalLinkLiveData.observe(
            viewLifecycleOwner,
            EventObserver { uri ->
                val action =
                    ListsTabFragmentDirections.actionListTabsDestToNewDownloadFragment(
                        externalUri = uri
                    )
                findNavController().navigate(action)
            },
        )

        // a file has been downloaded, usually a torrent, and needs to be unrestricted
        activityViewModel.downloadedFileLiveData.observe(
            viewLifecycleOwner,
            EventObserver { fileID ->
                val uri = requireContext().getDownloadedFileUri(fileID)
                // no need to recheck the extension since it was checked on download
                // if (uri?.path?.endsWith(".torrent") == true)
                if (uri?.path != null) {
                    val action =
                        ListsTabFragmentDirections.actionListTabsDestToNewDownloadFragment(
                            externalUri = uri
                        )
                    findNavController().navigate(action)
                }
            },
        )

        // a notification has been clicked
        activityViewModel.notificationTorrentLiveData.observe(
            viewLifecycleOwner,
            EventObserver { torrentID ->
                val action = ListsTabFragmentDirections.actionListsTabToTorrentDetails(torrentID)
                findNavController().navigate(action)
            },
        )

        viewModel.eventLiveData.observe(
            viewLifecycleOwner,
            EventObserver { event ->
                when (event) {
                    is ListEvent.DownloadItemClick -> {
                        val action =
                            ListsTabFragmentDirections.actionListsTabToDownloadDetails(event.item)
                        var loop = 0
                        val controller = findNavController()
                        lifecycleScope.launch {
                            while (
                                loop++ < 20 &&
                                    controller.currentDestination?.id != R.id.list_tabs_dest
                            ) {
                                delay(100.milliseconds)
                            }
                            if (controller.currentDestination?.id == R.id.list_tabs_dest)
                                controller.navigate(action)
                        }
                    }

                    is ListEvent.TorrentItemClick -> {
                        when (event.item.status) {
                            "downloaded",
                            "ready" -> {
                                // A TorBox torrent is always browsed in the folder screen: it is
                                // the only list that renders TorBox files, and the Downloads tab is
                                // backed by Put.io and never fills. Same for the second call site.
                                if (
                                    event.item.host == TorboxClient.TORBOX_HOST ||
                                        event.item.links.size > 1
                                ) {
                                    val action =
                                        ListsTabFragmentDirections
                                            .actionListTabsDestToFolderListFragment2(
                                                folder = null,
                                                torrent = event.item,
                                                linkList = null,
                                            )
                                    findNavController().navigate(action)
                                } else context?.showToast(R.string.error)
                            }
                            // open the torrent details fragment
                            else -> {
                                val action =
                                    ListsTabFragmentDirections.actionListsTabToTorrentDetails(
                                        event.item
                                    )
                                var loop = 0

                                val controller = findNavController()
                                lifecycleScope.launch {
                                    while (
                                        loop++ < 20 &&
                                            controller.currentDestination?.id != R.id.list_tabs_dest
                                    ) {
                                        delay(100.milliseconds)
                                    }
                                    if (controller.currentDestination?.id == R.id.list_tabs_dest)
                                        controller.navigate(action)
                                }
                            }
                        }
                    }

                    is ListEvent.OpenTorrent -> {
                        val action =
                            ListsTabFragmentDirections.actionListsTabToTorrentDetails(event.item)

                        // workaround to avoid issues when the dialog still hasn't been popped from
                        // the
                        // navigation stack
                        val controller = findNavController()
                        var loop = 0
                        lifecycleScope.launch {
                            while (
                                loop++ < 20 &&
                                    controller.currentDestination?.id != R.id.list_tabs_dest
                            ) {
                                delay(100.milliseconds)
                            }
                            if (controller.currentDestination?.id == R.id.list_tabs_dest)
                                controller.navigate(action)
                        }
                    }

                    ListEvent.NewDownload -> {
                        val action =
                            ListsTabFragmentDirections.actionListTabsDestToNewDownloadFragment()
                        findNavController().navigate(action)
                    }
                }
            },
        )

        viewModel.errorsLiveData.observe(
            viewLifecycleOwner,
            EventObserver {
                for (error in it) {
                    when (error) {
                        is APIError -> {
                            context?.let { c -> c.showToast(c.getApiErrorMessage(error.errorCode)) }
                            when (error.errorCode) {
                                8 -> {
                                    // bad token, try refreshing it
                                    if (
                                        activityViewModel.getAuthenticationMachineState()
                                            is FSMAuthenticationState.AuthenticatedOpenToken
                                    )
                                        activityViewModel.transitionAuthenticationMachine(
                                            FSMAuthenticationEvent.OnExpiredOpenToken
                                        )
                                    context?.showToast(R.string.refreshing_token)
                                }
                            }
                        }

                        is EmptyBodyError -> {}
                        is NetworkError -> {
                            context?.showToast(R.string.network_error)
                        }

                        is ApiConversionError -> {
                            context?.showToast(R.string.parsing_error)
                        }
                    }
                }
            },
        )

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val viewPager: ViewPager2 = view.findViewById(R.id.listPager)

        // A single page now: the tab strip that used to switch between Torrents and the removed
        // Downloads tab is gone, so the list starts at the top of the fragment.
        viewPager.adapter = ListsAdapter(this)

        super.onViewCreated(view, savedInstanceState)
    }

    private fun showDeleteAllDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_all))
            .setMessage(getString(R.string.delete_all_torrents_message))
            .setNegativeButton(getString(R.string.decline)) { _, _ -> }
            .setPositiveButton(getString(R.string.accept)) { _, _ ->
                viewModel.deleteAllTorrents()
            }
            .show()
    }
}

class ListsAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 1

    override fun createFragment(position: Int): Fragment = TorrentsListFragment()
}

@AndroidEntryPoint
class TorrentsListFragment : UnchainedFragment(), TorrentListListener {

    private val viewModel: ListTabsViewModel by activityViewModels()
    private var _binding: FragmentTorrentsListBinding? = null
    private val binding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTorrentsListBinding.inflate(inflater, container, false)

        val torrentAdapter = TorrentListPagingAdapter(this)
        binding.rvTorrentList.adapter = torrentAdapter

        // torrent list selection  tracker
        val torrentTracker: SelectionTracker<TorrentItem> =
            SelectionTracker.Builder(
                    "torrentListSelection",
                    binding.rvTorrentList,
                    TorrentKeyProvider(torrentAdapter),
                    TorrentDetailsLookup(binding.rvTorrentList),
                    StorageStrategy.createParcelableStorage(TorrentItem::class.java),
                )
                .withSelectionPredicate(SelectionPredicates.createSelectAnything())
                .build()

        torrentAdapter.tracker = torrentTracker

        torrentTracker.addObserver(
            object : SelectionTracker.SelectionObserver<TorrentItem>() {
                override fun onSelectionChanged() {
                    super.onSelectionChanged()
                    if (_binding == null) return
                    binding.cbSelectAll.text = torrentTracker.selection.size().toString()
                    if (torrentTracker.selection.size() == 1) {
                        binding.bDetailsSelected.visibility = View.VISIBLE
                    } else {
                        binding.bDetailsSelected.visibility = View.GONE
                    }
                }
            }
        )
        context?.let {
            // theme the swipe refresh circle and arrow
            val primaryColor = getThemeColor(it, android.R.attr.colorPrimary)
            val surfaceColor = getThemeColor(it, com.google.android.material.R.attr.colorSurface)
            binding.srLayout.setColorSchemeColors(primaryColor)
            binding.srLayout.setProgressBackgroundColorSchemeColor(surfaceColor)
        }

        // listener for selection buttons
        binding.bDeleteSelected.setOnClickListener {
            if (torrentTracker.selection.toList().isNotEmpty())
                viewModel.deleteTorrents(torrentTracker.selection.toList())
            else context?.showToast(R.string.select_one_item)
        }
        binding.bDownloadSelected.setOnClickListener {
            // A TorBox torrent's files live in the folder screen; enqueueing here would only resolve
            // a torbox:// reference and needs the key, so hand the user the real file list instead.
            if (torrentTracker.selection.size() != 1) {
                context?.showToast(R.string.select_one_item)
                return@setOnClickListener
            }
            val torrent: TorrentItem = torrentTracker.selection.toList().first()
            findNavController()
                .navigate(
                    ListsTabFragmentDirections.actionListTabsDestToFolderListFragment2(
                        folder = null,
                        torrent = torrent,
                        linkList = null,
                    )
                )
        }
        binding.bDetailsSelected.setOnClickListener {
            if (torrentTracker.selection.size() != 1) {
                // we can only open a single torrent at a time
                return@setOnClickListener
            }

            val item: TorrentItem = torrentTracker.selection.toList().first()
            val action =
                if (beforeSelectionStatusList.contains(item.status))
                    ListsTabFragmentDirections.actionListTabsDestToTorrentProcessingFragment(
                        torrentID = item.id
                    )
                else ListsTabFragmentDirections.actionListsTabToTorrentDetails(item)
            findNavController().navigate(action)
        }
        binding.bAddNew?.setOnClickListener {
            // landscape only
            viewModel.postEventNotice(ListEvent.NewDownload)
        }
        binding.bRefresh?.setOnClickListener {
            if (!binding.srLayout.isRefreshing) {
                binding.srLayout.isRefreshing = true
                torrentAdapter.refresh()
            }
        }

        binding.cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                torrentTracker.setItemsSelected(torrentAdapter.snapshot().items, true)
            } else {
                torrentTracker.clearSelection()
            }
        }

        binding.srLayout.setOnRefreshListener {
            if (_binding == null) return@setOnRefreshListener
            torrentAdapter.refresh()
        }

        val torrentObserver =
            Observer<PagingData<TorrentItem>> {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (_binding == null) return@launch

                    torrentAdapter.submitData(it)
                    if (binding.srLayout.isRefreshing) {
                        binding.srLayout.isRefreshing = false
                        lifecycleScope.launch {
                            binding.rvTorrentList.delayedScrolling(requireContext())
                        }
                    }
                    delay(300.milliseconds)
                    torrentAdapter.notifyDataSetChanged()
                }
            }

        // checks the authentication state. Needed to avoid automatic API calls before the
        // authentication process is finished
        activityViewModel.fsmAuthenticationState.observe(viewLifecycleOwner) {
            when (it?.peekContent()) {
                FSMAuthenticationState.AuthenticatedOpenToken,
                FSMAuthenticationState.AuthenticatedPrivateToken -> {
                    // register observers if not already registered
                    if (!viewModel.torrentsLiveData.hasActiveObservers())
                        viewModel.torrentsLiveData.observe(viewLifecycleOwner, torrentObserver)
                }

                else -> {}
            }
        }

        viewModel.deletedTorrentLiveData.observe(
            viewLifecycleOwner,
            EventObserver {
                when (it) {
                    TORRENT_NOT_DELETED -> {}
                    TORRENT_DELETED -> {
                        context?.showToast(R.string.torrent_removed)
                        torrentAdapter.refresh()
                    }

                    TORRENTS_DELETED_ALL -> {
                        context?.showToast(R.string.torrents_removed)
                        lifecycleScope.launch {
                            // if we don't refresh the cached copy of the last result will be
                            // restored on the
                            // first list redraw
                            torrentAdapter.refresh()
                            torrentAdapter.submitData(PagingData.empty())
                        }
                    }

                    TORRENTS_DELETED -> {
                        context?.showToast(R.string.torrents_removed)
                        torrentAdapter.refresh()
                    }

                    0 -> {
                        context?.showToast(R.string.removing_torrents)
                    }

                    else -> {
                        torrentAdapter.refresh()
                    }
                }
            },
        )

        activityViewModel.listStateLiveData.observe(
            viewLifecycleOwner,
            EventObserver {
                if (_binding == null) return@EventObserver
                when (it) {
                    ListState.UpdateTorrent -> {
                        lifecycleScope.launch {
                            delay(300.milliseconds)
                            torrentAdapter.refresh()
                            lifecycleScope.launch {
                                binding.rvTorrentList.delayedScrolling(requireContext())
                            }
                        }
                    }

                    else -> {}
                }
            },
        )

        // scroll the list to the top when the parent fragment fab is clicked on this tab
        viewModel.scrollToTopLiveData.observe(viewLifecycleOwner) { event ->
            // a single list page now, so any scroll-to-top request belongs to it
            event?.getContentIfNotHandled()?.let {
                if (_binding != null) binding.rvTorrentList.layoutManager?.scrollToPosition(0)
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onClick(item: TorrentItem) {

        // this was used to wait for some loading, can't remember the use case anymore
        // maybe a link share or a notification click?
        var loop = 0

        lifecycleScope.launch {
            val controller = findNavController()
            while (loop++ < 20 && controller.currentDestination?.id != R.id.list_tabs_dest) {
                delay(100.milliseconds)
            }

            if (item.status == "downloaded" || item.status == "ready") {
                // unrestrict and move to download tab
                if (item.host == TorboxClient.TORBOX_HOST || item.links.size > 1) {
                    val action =
                        ListsTabFragmentDirections.actionListTabsDestToFolderListFragment2(
                            folder = null,
                            torrent = item,
                            linkList = null,
                        )
                    if (controller.currentDestination?.id == R.id.list_tabs_dest)
                        controller.navigate(action)
                    else
                        Timber.e(
                            "Correct tab was not ready within 2 seconds after clicking torrent $item"
                        )
                } else context?.showToast(R.string.error)
            } else if (beforeSelectionStatusList.contains(item.status)) {
                // go to torrent processing since it is still loading
                val action =
                    ListsTabFragmentDirections.actionListTabsDestToTorrentProcessingFragment(
                        torrentID = item.id
                    )
                if (controller.currentDestination?.id == R.id.list_tabs_dest)
                    controller.navigate(action)
                else
                    Timber.e(
                        "Correct tab was not ready within 2 seconds after clicking torrent $item"
                    )
            } else {
                // go to torrent details
                val action = ListsTabFragmentDirections.actionListsTabToTorrentDetails(item)
                if (controller.currentDestination?.id == R.id.list_tabs_dest)
                    controller.navigate(action)
                else
                    Timber.e(
                        "Correct tab was not ready within 2 seconds after clicking torrent $item"
                    )
            }
        }
    }
}

sealed class ListState {
    data object UpdateTorrent : ListState()

    data object UpdateDownload : ListState()
}
