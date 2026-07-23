package com.github.livingwithhippos.unchained.data.local

import android.content.Context
import android.util.Base64
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Stores the TorBox API key encrypted with an Android Keystore AES key. */
@Singleton
class TorboxKeyStore
@Inject
constructor(@ApplicationContext context: Context) {

    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): String? {
        val cipherText = preferences.getString(CIPHERTEXT_KEY, null) ?: return null
        val iv = preferences.getString(IV_KEY, null) ?: return null

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(TAG_LENGTH, decode(iv)),
            )
            String(cipher.doFinal(decode(cipherText)), Charsets.UTF_8)
        }.getOrNull()
    }

    fun write(apiKey: String) {
        require(apiKey.isNotBlank()) { "TorBox API key must not be empty" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        preferences.edit()
            .putString(CIPHERTEXT_KEY, encode(cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))))
            .putString(IV_KEY, encode(cipher.iv))
            .apply()
    }

    fun clear() {
        preferences.edit().remove(CIPHERTEXT_KEY).remove(IV_KEY).apply()
    }

    @VisibleForTesting
    internal fun hasStoredValue(): Boolean =
        preferences.contains(CIPHERTEXT_KEY) && preferences.contains(IV_KEY)

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing

        return KeyGenerator.getInstance(ALGORITHM, ANDROID_KEYSTORE).apply {
            init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                        android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setKeySize(KEY_SIZE)
                    .build()
            )
        }.generateKey()
    }

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val PREFERENCES_NAME = "torbox_secure_credentials"
        const val CIPHERTEXT_KEY = "api_key_ciphertext"
        const val IV_KEY = "api_key_iv"
        const val KEY_ALIAS = "torbox_api_key_aes_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALGORITHM = "AES"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val BLOCK_MODE = "GCM"
        const val PADDING = "NoPadding"
        const val KEY_SIZE = 256
        const val TAG_LENGTH = 128
    }
}
