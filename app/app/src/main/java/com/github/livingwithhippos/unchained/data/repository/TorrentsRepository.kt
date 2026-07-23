package com.github.livingwithhippos.unchained.data.repository

import com.github.livingwithhippos.unchained.data.model.AvailableHost
import com.github.livingwithhippos.unchained.data.model.DownloadItem
import com.github.livingwithhippos.unchained.data.model.NetworkError
import com.github.livingwithhippos.unchained.data.model.TorrentItem
import com.github.livingwithhippos.unchained.data.model.UnchainedNetworkException
import com.github.livingwithhippos.unchained.data.model.UploadedTorrent
import com.github.livingwithhippos.unchained.data.remote.TorboxClient
import com.github.livingwithhippos.unchained.utilities.EitherResult
import javax.inject.Inject

/** Repository facade used by the existing UI, backed exclusively by TorBox. */
class TorrentsRepository
@Inject
constructor(private val torboxClient: TorboxClient) {

    suspend fun getAvailableHosts(): List<AvailableHost> =
        listOf(AvailableHost(TorboxClient.TORBOX_HOST, Int.MAX_VALUE))

    suspend fun getTorrentInfo(id: String): TorrentItem? = torboxClient.getTorrent(id)

    suspend fun addTorrent(
        binaryTorrent: ByteArray,
        host: String,
    ): EitherResult<UnchainedNetworkException, UploadedTorrent> =
        torboxClient.createTorrentFile(binaryTorrent).asUploadedTorrent("Error uploading torrent")

    suspend fun addMagnet(
        magnet: String,
        host: String,
    ): EitherResult<UnchainedNetworkException, UploadedTorrent> =
        torboxClient.createMagnet(magnet).asUploadedTorrent("Error uploading torrent from magnet")

    suspend fun getTorrentsList(
        offset: Int? = null,
        page: Int? = 1,
        limit: Int? = 50,
        filter: String? = null,
    ): List<TorrentItem> = torboxClient.listTorrents()

    suspend fun selectFiles(
        id: String,
        files: String = "all",
    ): EitherResult<UnchainedNetworkException, Unit> =
        // TorBox downloads all files; the app filters which files to download locally.
        EitherResult.Success(Unit)

    suspend fun deleteTorrent(id: String): EitherResult<UnchainedNetworkException, Unit> =
        if (torboxClient.deleteTorrent(id)) EitherResult.Success(Unit)
        else EitherResult.Failure(NetworkError(-1, "Error deleting torrent"))

    suspend fun getDownloadItems(torrent: TorrentItem): List<DownloadItem> =
        torboxClient.downloadItems(torrent)
}

private fun Result<Int>.asUploadedTorrent(
    errorMessage: String,
): EitherResult<UnchainedNetworkException, UploadedTorrent> =
    fold(
        onSuccess = { id -> EitherResult.Success(UploadedTorrent(id.toString(), "")) },
        onFailure = {
            EitherResult.Failure(NetworkError(-1, errorMessage))
        },
    )
