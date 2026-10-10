package dev.schoolgrades

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the session cookie in app-private storage.
 * Android 6+: encrypted with an AES-GCM key held in the Android Keystore.
 * Android 5.x: the Keystore can't hold AES keys, so it is stored unencrypted in private storage.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun saveCookie(value: String) {
        val stored = if (Build.VERSION.SDK_INT >= 23) encrypt(value) else value
        prefs.edit().putString(KEY, stored).apply()
    }

    fun loadCookie(): String? {
        val stored = prefs.getString(KEY, null) ?: return null
        return if (Build.VERSION.SDK_INT >= 23) decrypt(stored) else stored
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    @RequiresApi(23)
    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    @RequiresApi(23)
    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + data, Base64.NO_WRAP)
    }

    @RequiresApi(23)
    private fun decrypt(stored: String): String? = try {
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        String(cipher.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val KEY = "cookie"
        const val ALIAS = "schoolgrades_session"
    }
}
