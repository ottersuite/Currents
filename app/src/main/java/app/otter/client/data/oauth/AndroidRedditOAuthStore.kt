package app.otter.client.data.oauth

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.otter.client.model.RedditOAuthCredential
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android-backed Reddit credential storage.
 *
 * The refresh token is encrypted using a non-exportable Android Keystore AES-256 key and a new
 * GCM IV on every write. Account metadata and scopes are intentionally non-secret.
 *
 * The short-lived access token is kept too, under the same key but a separate AAD domain so one
 * ciphertext can never be replayed as the other. It is the weaker of the two secrets — it expires
 * within the hour and cannot mint anything — while the refresh token it is derived from is
 * already stored here and can mint access tokens indefinitely. Keeping it removes a network
 * round trip from the front of every cold start. Sign-out deletes the Keystore key, which leaves
 * both ciphertexts undecryptable regardless of what is still on disk.
 */
@SuppressLint("ApplySharedPref", "UseKtx") // Sign-out must be durable before the token is gone.
class AndroidRedditOAuthStore(context: Context) : RedditOAuthStore {
    private val applicationContext = context.applicationContext
    private val preferences: SharedPreferences = applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun saveCredential(
        configurationKey: String,
        credential: RedditOAuthCredential,
    ): Boolean =
        synchronized(STORE_LOCK) {
            if (!isValid(configurationKey, credential)) return@synchronized false
            try {
                persistCredential(configurationKey, credential)
            } catch (_: GeneralSecurityException) {
                // A biometric/security-policy change can invalidate a Keystore key. Destroy its
                // unusable record and retry once with a newly generated key.
                clearCredentialLocked()
                deleteKeyQuietly(KEYSTORE_ALIAS)
                try {
                    persistCredential(configurationKey, credential)
                } catch (_: GeneralSecurityException) {
                    false
                } catch (_: IllegalArgumentException) {
                    false
                }
            } catch (_: IllegalArgumentException) {
                false
            }
        }

    override fun loadCredential(configurationKey: String): RedditOAuthCredential? = synchronized(STORE_LOCK) {
        if (configurationKey.isBlank()) return@synchronized null
        try {
            val blob = preferences.getString(KEY_REFRESH_TOKEN_BLOB, null)
            val accountId = preferences.getString(KEY_ACCOUNT_ID, null)
            val username = preferences.getString(KEY_USERNAME, null)
            val scopes = preferences.getStringSet(KEY_SCOPES, null)?.toSet()

            if (blob == null && accountId == null && username == null && scopes == null) {
                return@synchronized null
            }
            if (blob.isNullOrBlank() || accountId.isNullOrBlank() || username.isNullOrBlank() ||
                scopes == null || scopes.any(String::isBlank)
            ) {
                clearCredentialLocked()
                return@synchronized null
            }

            val refreshToken = decrypt(KEYSTORE_ALIAS, configurationKey, blob)
            if (refreshToken.isBlank()) {
                clearCredentialLocked()
                return@synchronized null
            }
            RedditOAuthCredential(
                refreshToken = refreshToken,
                accountId = accountId,
                username = username,
                scopes = scopes,
            )
        } catch (_: GeneralSecurityException) {
            // Authentication failure, key invalidation, or a missing key makes the record unusable.
            clearCredentialLocked()
            deleteKeyQuietly(KEYSTORE_ALIAS)
            null
        } catch (_: IllegalArgumentException) {
            // Includes malformed Base64 and invalid blob fields.
            clearCredentialLocked()
            null
        } catch (_: ClassCastException) {
            // SharedPreferences may contain values written by an incompatible/corrupt schema.
            clearCredentialLocked()
            null
        }
    }

    override fun loadAccessToken(configurationKey: String): StoredAccessToken? =
        synchronized(STORE_LOCK) {
            if (configurationKey.isBlank()) return@synchronized null
            val blob = preferences.getString(KEY_ACCESS_TOKEN_BLOB, null)
            if (blob.isNullOrBlank()) return@synchronized null
            val expiresAt = preferences.getLong(KEY_ACCESS_TOKEN_EXPIRES_AT, 0L)
            if (expiresAt <= 0L) {
                clearAccessTokenLocked()
                return@synchronized null
            }
            try {
                val value = decrypt(KEYSTORE_ALIAS, configurationKey, blob, ACCESS_TOKEN_DOMAIN)
                if (value.isBlank()) {
                    clearAccessTokenLocked()
                    null
                } else {
                    StoredAccessToken(value, expiresAt)
                }
            } catch (_: GeneralSecurityException) {
                // Unreadable for any reason -- a rotated key, a tampered blob -- is simply a
                // cache miss. The refresh token is still there to mint a new one.
                clearAccessTokenLocked()
                null
            } catch (_: IllegalArgumentException) {
                clearAccessTokenLocked()
                null
            }
        }

    override fun saveAccessToken(configurationKey: String, token: StoredAccessToken?): Boolean =
        synchronized(STORE_LOCK) {
            if (configurationKey.isBlank()) return@synchronized false
            if (token == null || token.value.isBlank() || token.expiresAtEpochMillis <= 0L) {
                return@synchronized clearAccessTokenLocked()
            }
            try {
                val blob = encrypt(KEYSTORE_ALIAS, configurationKey, token.value, ACCESS_TOKEN_DOMAIN)
                preferences.edit()
                    .putString(KEY_ACCESS_TOKEN_BLOB, blob)
                    .putLong(KEY_ACCESS_TOKEN_EXPIRES_AT, token.expiresAtEpochMillis)
                    .commit()
            } catch (_: GeneralSecurityException) {
                // Failing to cache a token is not a failure worth propagating: the caller can
                // always refresh. Make sure a stale blob is not left behind, though.
                clearAccessTokenLocked()
                false
            } catch (_: IllegalArgumentException) {
                clearAccessTokenLocked()
                false
            }
        }

    override fun clearCredential(): Boolean = synchronized(STORE_LOCK) {
        val preferencesCleared = clearCredentialLocked()
        // Logout destroys both the ciphertext and the key that could decrypt any stale copy.
        deleteKeyQuietly(KEYSTORE_ALIAS)
        preferencesCleared
    }

    override fun savePendingAuthorization(state: String, issuedAtEpochMillis: Long): Boolean =
        synchronized(STORE_LOCK) {
            if (state.isBlank() || issuedAtEpochMillis < 0L) return@synchronized false
            val digest = Base64.encodeToString(OAuthStateVerifier.digest(state), Base64.NO_WRAP)
            preferences.edit()
                .putString(KEY_PENDING_STATE_DIGEST, digest)
                .putLong(KEY_PENDING_ISSUED_AT, issuedAtEpochMillis)
                .commit()
        }

    override fun consumePendingAuthorization(
        returnedState: String,
        nowEpochMillis: Long,
    ): Boolean = synchronized(STORE_LOCK) {
        try {
            val encodedDigest = preferences.getString(KEY_PENDING_STATE_DIGEST, null)
            val hasTimestamp = preferences.contains(KEY_PENDING_ISSUED_AT)
            val issuedAt = if (hasTimestamp) preferences.getLong(KEY_PENDING_ISSUED_AT, -1L) else -1L

            if (encodedDigest.isNullOrBlank() || !hasTimestamp) {
                clearPendingAuthorizationLocked()
                return@synchronized false
            }

            val expectedDigest = Base64.decode(encodedDigest, Base64.NO_WRAP)
            val stateMatches = OAuthStateVerifier.matches(expectedDigest, returnedState)
            val isFresh = OAuthStateVerifier.isFresh(issuedAt, nowEpochMillis)
            if (!isFresh) {
                clearPendingAuthorizationLocked()
                return@synchronized false
            }
            if (!stateMatches) return@synchronized false
            clearPendingAuthorizationLocked()
        } catch (_: IllegalArgumentException) {
            clearPendingAuthorizationLocked()
            false
        } catch (_: ClassCastException) {
            clearPendingAuthorizationLocked()
            false
        }
    }

    override fun clearPendingAuthorization(): Boolean = synchronized(STORE_LOCK) {
        clearPendingAuthorizationLocked()
    }

    /**
     * Pending revocations live under their own Keystore alias. Logout deletes the credential key,
     * so sharing one alias would shred the very token the retry needs.
     */
    override fun savePendingRevocation(configurationKey: String, refreshToken: String): Boolean =
        synchronized(STORE_LOCK) {
            if (configurationKey.isBlank() || refreshToken.isBlank()) return@synchronized false
            if (refreshToken.contains(RECORD_SEPARATOR)) return@synchronized false
            val queued = readPendingRevocationsLocked(configurationKey)
            if (refreshToken in queued) return@synchronized true
            writePendingRevocationsLocked(configurationKey, queued + refreshToken)
        }

    override fun loadPendingRevocations(configurationKey: String): List<String> =
        synchronized(STORE_LOCK) {
            if (configurationKey.isBlank()) return@synchronized emptyList()
            readPendingRevocationsLocked(configurationKey)
        }

    override fun clearPendingRevocation(configurationKey: String, refreshToken: String): Boolean =
        synchronized(STORE_LOCK) {
            if (configurationKey.isBlank()) return@synchronized false
            val queued = readPendingRevocationsLocked(configurationKey)
            if (refreshToken !in queued) return@synchronized false
            writePendingRevocationsLocked(configurationKey, queued - refreshToken)
        }

    private fun readPendingRevocationsLocked(configurationKey: String): List<String> = try {
        val blob = preferences.getString(KEY_PENDING_REVOCATIONS_BLOB, null)
        if (blob.isNullOrBlank()) {
            emptyList()
        } else {
            decrypt(REVOCATION_KEYSTORE_ALIAS, configurationKey, blob, REVOCATION_DOMAIN)
                .split(RECORD_SEPARATOR)
                .filter(String::isNotBlank)
        }
    } catch (_: GeneralSecurityException) {
        // A rotated key or a changed API configuration makes the queue unreadable, and an
        // undecryptable token can never be revoked -- drop it rather than retry it forever.
        clearPendingRevocationsLocked()
        emptyList()
    } catch (_: IllegalArgumentException) {
        clearPendingRevocationsLocked()
        emptyList()
    } catch (_: ClassCastException) {
        clearPendingRevocationsLocked()
        emptyList()
    }

    private fun writePendingRevocationsLocked(
        configurationKey: String,
        tokens: List<String>,
    ): Boolean = try {
        if (tokens.isEmpty()) {
            val cleared = clearPendingRevocationsLocked()
            deleteKeyQuietly(REVOCATION_KEYSTORE_ALIAS)
            cleared
        } else {
            val blob = encrypt(
                alias = REVOCATION_KEYSTORE_ALIAS,
                configurationKey = configurationKey,
                plaintext = tokens.joinToString(RECORD_SEPARATOR),
                domain = REVOCATION_DOMAIN,
            )
            preferences.edit().putString(KEY_PENDING_REVOCATIONS_BLOB, blob).commit()
        }
    } catch (_: GeneralSecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun clearPendingRevocationsLocked(): Boolean =
        preferences.edit().remove(KEY_PENDING_REVOCATIONS_BLOB).commit()

    private fun encrypt(
        alias: String,
        configurationKey: String,
        plaintext: String,
        domain: String? = null,
    ): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(alias))
        cipher.updateAAD(additionalAuthenticatedData(configurationKey, domain))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv
        require(iv.isNotEmpty() && iv.size <= UByte.MAX_VALUE.toInt())

        val blob = ByteBuffer.allocate(2 + iv.size + ciphertext.size)
            .put(BLOB_SCHEMA_VERSION.toByte())
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    private fun persistCredential(
        configurationKey: String,
        credential: RedditOAuthCredential,
    ): Boolean {
        val encryptedRefreshToken = encrypt(KEYSTORE_ALIAS, configurationKey, credential.refreshToken)
        return preferences.edit()
            .putString(KEY_REFRESH_TOKEN_BLOB, encryptedRefreshToken)
            .putString(KEY_ACCOUNT_ID, credential.accountId)
            .putString(KEY_USERNAME, credential.username)
            .putStringSet(KEY_SCOPES, credential.scopes.toSet())
            .commit()
    }

    private fun decrypt(
        alias: String,
        configurationKey: String,
        encodedBlob: String,
        domain: String? = null,
    ): String {
        val blob = Base64.decode(encodedBlob, Base64.NO_WRAP)
        require(blob.size >= MINIMUM_BLOB_SIZE)
        val buffer = ByteBuffer.wrap(blob)
        val schemaVersion = buffer.get().toInt() and 0xff
        require(schemaVersion == BLOB_SCHEMA_VERSION)
        val ivLength = buffer.get().toInt() and 0xff
        require(ivLength in MINIMUM_IV_BYTES..buffer.remaining() - GCM_TAG_BYTES)

        val iv = ByteArray(ivLength).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getExistingKey(alias), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(additionalAuthenticatedData(configurationKey, domain))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun additionalAuthenticatedData(
        configurationKey: String,
        domain: String? = null,
    ): ByteArray {
        val base =
            "package=${applicationContext.packageName}\u0000configuration=$configurationKey\u0000schema=$BLOB_SCHEMA_VERSION"
        val scoped = if (domain == null) base else "$base\u0000domain=$domain"
        return scoped.toByteArray(StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        getExistingKeyOrNull(alias)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val specification = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(specification)
        return generator.generateKey()
    }

    private fun getExistingKey(alias: String): SecretKey =
        getExistingKeyOrNull(alias)
            ?: throw GeneralSecurityException("OAuth encryption key is missing")

    private fun getExistingKeyOrNull(alias: String): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return keyStore.getKey(alias, null) as? SecretKey
    }

    private fun deleteKeyQuietly(alias: String) {
        runCatching {
            KeyStore.getInstance(ANDROID_KEY_STORE).apply {
                load(null)
                deleteEntry(alias)
            }
        }
    }

    private fun clearCredentialLocked(): Boolean = preferences.edit()
        .remove(KEY_REFRESH_TOKEN_BLOB)
        .remove(KEY_ACCOUNT_ID)
        .remove(KEY_USERNAME)
        .remove(KEY_SCOPES)
        // The access token goes with the credential it came from. Leaving it would let a
        // signed-out session keep making authorized requests until it expired.
        .remove(KEY_ACCESS_TOKEN_BLOB)
        .remove(KEY_ACCESS_TOKEN_EXPIRES_AT)
        .commit()

    private fun clearAccessTokenLocked(): Boolean = preferences.edit()
        .remove(KEY_ACCESS_TOKEN_BLOB)
        .remove(KEY_ACCESS_TOKEN_EXPIRES_AT)
        .commit()

    private fun clearPendingAuthorizationLocked(): Boolean = preferences.edit()
        .remove(KEY_PENDING_STATE_DIGEST)
        .remove(KEY_PENDING_ISSUED_AT)
        .commit()

    private fun isValid(configurationKey: String, credential: RedditOAuthCredential): Boolean =
        configurationKey.isNotBlank() &&
            credential.refreshToken.isNotBlank() &&
            credential.accountId.isNotBlank() &&
            credential.username.isNotBlank() &&
            credential.scopes.none(String::isBlank)

    companion object {
        const val PREFERENCES_NAME = "reddit_oauth"

        private const val BLOB_SCHEMA_VERSION = 1
        // Stable aliases preserve access to credentials created by the Orca-branded build.
        private const val KEYSTORE_ALIAS = "orca_reddit_oauth_refresh_v1"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_SIZE_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val MINIMUM_IV_BYTES = 12
        private const val MINIMUM_BLOB_SIZE = 2 + MINIMUM_IV_BYTES + GCM_TAG_BYTES

        private const val KEY_REFRESH_TOKEN_BLOB = "refresh_token_blob"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_SCOPES = "scopes"
        private const val KEY_ACCESS_TOKEN_BLOB = "access_token_blob"
        private const val KEY_ACCESS_TOKEN_EXPIRES_AT = "access_token_expires_at"
        private const val ACCESS_TOKEN_DOMAIN = "access_token"

        private const val KEY_PENDING_STATE_DIGEST = "pending_state_digest"
        private const val KEY_PENDING_ISSUED_AT = "pending_issued_at"
        private const val KEY_PENDING_REVOCATIONS_BLOB = "pending_revocations_blob"

        private const val REVOCATION_KEYSTORE_ALIAS = "orca_reddit_oauth_revocation_v1"
        private const val REVOCATION_DOMAIN = "pending_revocation"
        private const val RECORD_SEPARATOR = "\n"

        private val STORE_LOCK = Any()
    }
}
