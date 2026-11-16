package dev.haashemi.virshdroid

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Server(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    val useKey: Boolean = false,
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
    /** Accepted host key, Base64. Null until the user accepts one. */
    val hostKey: String? = null,
)

private val Context.dataStore by preferencesDataStore("servers")
private val SERVERS = stringPreferencesKey("servers")
private val SELECTED = stringPreferencesKey("selected")

/** The saved servers and the one the app connects to (the first one if none was chosen). */
class Saved(val servers: List<Server>, val selected: Server?)

/** Saved servers, stored as one JSON list encrypted with an Android Keystore key. */
class Store(private val context: Context) {
    val saved = context.dataStore.data.map { prefs ->
        val servers = decode(prefs[SERVERS])
        Saved(servers, servers.find { it.id == prefs[SELECTED] } ?: servers.firstOrNull())
    }

    suspend fun select(id: String) {
        context.dataStore.edit { it[SELECTED] = id }
    }

    /** Adds or replaces [server] and selects it. */
    suspend fun save(server: Server) = update(select = server.id) { list ->
        if (list.any { it.id == server.id }) list.map { if (it.id == server.id) server else it } else list + server
    }

    suspend fun remove(id: String) = update { list -> list.filterNot { it.id == id } }

    private suspend fun update(select: String? = null, change: (List<Server>) -> List<Server>) {
        context.dataStore.edit { prefs ->
            prefs[SERVERS] = encrypt(Json.encodeToString(change(decode(prefs[SERVERS]))))
            select?.let { prefs[SELECTED] = it }
        }
    }

    private fun decode(blob: String?): List<Server> = blob?.let { Json.decodeFromString(decrypt(it)) } ?: emptyList()

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as SecretKey?)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(text.encodeToByteArray()))
    }

    private fun decrypt(blob: String): String {
        val bytes = Base64.getDecoder().decode(blob)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        return cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE).decodeToString()
    }

    private companion object {
        const val ALIAS = "servers"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
