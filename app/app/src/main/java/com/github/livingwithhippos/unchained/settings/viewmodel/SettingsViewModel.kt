package com.github.livingwithhippos.unchained.settings.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.livingwithhippos.unchained.R
import com.github.livingwithhippos.unchained.data.local.ProtoStore
import com.github.livingwithhippos.unchained.data.local.TorboxKeyStore
import com.github.livingwithhippos.unchained.data.repository.HostsRepository
import com.github.livingwithhippos.unchained.data.repository.KodiRepository
import com.github.livingwithhippos.unchained.data.repository.PluginRepository
import com.github.livingwithhippos.unchained.settings.view.SettingsFragment.Companion.KEY_THEME_NEW
import com.github.livingwithhippos.unchained.settings.view.ThemeItem
import com.github.livingwithhippos.unchained.start.viewmodel.MainActivityViewModel.Companion.KEY_DOWNLOAD_FOLDER
import com.github.livingwithhippos.unchained.utilities.Event
import com.github.livingwithhippos.unchained.utilities.postEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val hostsRepository: HostsRepository,
    private val pluginRepository: PluginRepository,
    private val kodiRepository: KodiRepository,
    private val protoStore: ProtoStore,
    private val preferences: SharedPreferences,
    private val torboxKeyStore: TorboxKeyStore,
) : ViewModel() {

    val kodiLiveData = MutableLiveData<Event<Boolean>>()

    val eventLiveData = MutableLiveData<Event<SettingEvent>>()

    val themeLiveData = MutableLiveData<Event<ThemeItem>>()

    fun updateRegexps() {
        viewModelScope.launch {
            hostsRepository.updateHostsRegex()
            hostsRepository.updateFoldersRegex()
        }
    }

    fun removeAllPlugins(context: Context): Int = pluginRepository.removeInstalledPlugins(context)

    fun testKodi(ip: String, port: Int, username: String?, password: String?) {
        viewModelScope.launch {
            val response = kodiRepository.getVolume(ip, port, username, password)
            kodiLiveData.postEvent(response != null)
        }
    }

    fun selectTheme(theme: ThemeItem) {
        themeLiveData.postEvent(theme)
    }

    fun setDownloadFolder(uri: Uri) {
        uri.describeContents()
        preferences.edit { putString(KEY_DOWNLOAD_FOLDER, uri.toString()) }
    }

    fun userLogout() {
        viewModelScope.launch {
            val hadTorboxKey = !torboxKeyStore.read().isNullOrBlank()
            val credentials = protoStore.getCredentials()
            val hadLegacyCredentials =
                credentials.accessToken.isNotBlank() || credentials.clientId.isNotBlank()
            if (!hadTorboxKey && !hadLegacyCredentials) {
                eventLiveData.postEvent(SettingEvent.LogoutNoCredentials)
            } else {
                // TorBox keeps the API key in the Keystore-backed store, not in ProtoStore, so
                // clearing only the legacy credentials left the account still signed in.
                if (hadLegacyCredentials) protoStore.deleteCredentials()
                if (hadTorboxKey) torboxKeyStore.clear()
                eventLiveData.postEvent(SettingEvent.Logout)
            }
        }
    }

    fun applyTheme() {
        val selectedTheme: ThemeItem? = themeLiveData.value?.peekContent()
        selectedTheme?.let { preferences.edit { putInt(KEY_THEME_NEW, it.themeID) } }
    }

    fun getCurrentTheme(): Int {
        return preferences.getInt(KEY_THEME_NEW, R.style.Theme_Unchained_Material3_Green_One)
    }
}

sealed class SettingEvent {
    data object Logout : SettingEvent()

    data object LogoutNoCredentials : SettingEvent()
}
