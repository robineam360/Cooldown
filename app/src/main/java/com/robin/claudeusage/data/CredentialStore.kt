package com.robin.claudeusage.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

data class Credentials(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long, // epoch millis; 0 = unknown
    /** ChatGPT sends this back as a header (CCRM-53 (Provider Model)); Claude never has one. */
    val accountId: String? = null,
)

/** Android Keystore-backed storage for the OAuth tokens, one slot per profile. */
class CredentialStore(context: Context) {

    @Suppress("DEPRECATION")
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "secure_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // v0.5 and earlier stored the single (personal) token without a prefix. Restated as a
    // key comparison for CCRM-6 (Multi-Account), now that Profile is a value type — the
    // legacy key is a storage-format constant, so this exception outlives the enum.
    private fun k(profile: Profile, name: String): String =
        if (profile.key == Profile.LEGACY_KEY) name else "${profile.key}.$name"

    fun load(profile: Profile): Credentials? {
        val access = prefs.getString(k(profile, "accessToken"), null) ?: return null
        val refresh = prefs.getString(k(profile, "refreshToken"), null) ?: return null
        return Credentials(
            access, refresh,
            prefs.getLong(k(profile, "expiresAt"), 0L),
            prefs.getString(k(profile, "accountId"), null),
        )
    }

    /**
     * stampAdded is true only when the user pastes a token; silent rotations
     * during background refresh keep the original added date and tail label.
     */
    fun save(profile: Profile, creds: Credentials, stampAdded: Boolean = false) {
        val e = prefs.edit()
            .putString(k(profile, "accessToken"), creds.accessToken)
            .putString(k(profile, "refreshToken"), creds.refreshToken)
            .putLong(k(profile, "expiresAt"), creds.expiresAt)
        if (creds.accountId != null) e.putString(k(profile, "accountId"), creds.accountId)
        else e.remove(k(profile, "accountId"))
        if (stampAdded) {
            e.putLong(k(profile, "addedAt"), System.currentTimeMillis())
            e.putString(k(profile, "tokenTail"), creds.accessToken.takeLast(4))
        }
        e.apply()
    }

    fun addedAt(profile: Profile): Long = prefs.getLong(k(profile, "addedAt"), 0L)

    fun tokenTail(profile: Profile): String? = prefs.getString(k(profile, "tokenTail"), null)

    fun clear(profile: Profile) {
        prefs.edit()
            .remove(k(profile, "accessToken"))
            .remove(k(profile, "refreshToken"))
            .remove(k(profile, "expiresAt"))
            .remove(k(profile, "accountId"))
            .remove(k(profile, "addedAt"))
            .remove(k(profile, "tokenTail"))
            .apply()
    }

}
