package et.tibeb.examprep.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the device token encrypted with an AES-256-GCM key that lives in the Android
 * Keystore (hardware-backed on most phones). The key never leaves the secure hardware,
 * so a copied prefs file is useless on another device.
 */
open class SecureStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("secure", Context.MODE_PRIVATE)
    private val ks by lazy { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }

    private fun key(): SecretKey {
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    open fun put(name: String, value: String) {
        val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val blob = c.iv + c.doFinal(value.toByteArray())
        prefs.edit().putString(name, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    open fun remove(name: String) { prefs.edit().remove(name).apply() }

    open fun get(name: String): String? = try {
        prefs.getString(name, null)?.let {
            val blob = Base64.decode(it, Base64.NO_WRAP)
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12))
            String(c.doFinal(blob, 12, blob.size - 12))
        }
    } catch (e: Exception) {
        null // key invalidated (e.g. device restore) -> ask the user to re-enter
    }

    companion object {
        private const val ALIAS = "tibeb_token_key"
        private const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
