package com.github.livingwithhippos.unchained.repository.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Data shapes for the plugin system. They used to live next to the plugin-repository list UI, which
 * is gone with the Search tab, but the plugin data layer still models what a repository returns.
 */
sealed class RepositoryListItem {
    @Parcelize
    data class Repository(
        // primary id
        val link: String,
        val name: String,
        val version: Double,
        val description: String,
        val author: String,
    ) : RepositoryListItem(),
        Parcelable

    data class Plugin(
        // repo link used as foreign key to differentiate plugins
        val repository: String,
        val name: String,
        val version: Float,
        val link: String,
        val author: String?,
        // see PluginStatus
        var status: String,
        var statusTranslation: String,
        var disabled: Boolean? = false,
    ) : RepositoryListItem()
}

object PluginStatus {
    // installed and ready
    const val updated = "updated"

    // installed with compatible update available
    const val hasUpdate = "has_update"

    // installed with no compatible update available
    const val hasIncompatibleUpdate = "has_incompatible_update"

    // no versions in repository, split between installed and not installed?
    const val unknown = "unknown"

    // new, installable
    const val isNew = "new"

    // new. not installable
    const val incompatible = "incompatible"

    // disabled when it has issues, marked by the repo (website down etc.)
    const val disabled = "disabled"
}
