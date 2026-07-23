package com.github.livingwithhippos.unchained.authentication.viewmodel

import androidx.lifecycle.ViewModel
import com.github.livingwithhippos.unchained.data.local.TorboxKeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Stores only the TorBox key; authentication is validated by MainActivityViewModel. */
@HiltViewModel
class AuthenticationViewModel
@Inject
constructor(private val torboxKeyStore: TorboxKeyStore) : ViewModel() {

    fun saveApiKey(value: String) {
        torboxKeyStore.write(value.trim())
    }
}
