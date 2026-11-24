package com.github.livingwithhippos.unchained.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * The five fields of TorBox's `user/me` this app is willing to show.
 *
 * Deliberately excludes every account identifier and the email: the panel is a glanceable summary,
 * not an identity card, and anything shown here can end up in a screenshot. The API key never enters
 * this model.
 */
@Parcelize
data class TorboxAccount(
    val isSubscribed: Boolean,
    val expiresAt: String?,
    val createdAt: String?,
    val torrentsDownloaded: Int,
) : Parcelable
