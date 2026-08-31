package app.otter.client.data.oauth

import app.otter.client.model.RedditAccount
import app.otter.client.model.RedditAccountState
import app.otter.client.model.RedditAuthenticationException
import app.otter.client.model.RedditOAuthCredential
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import app.otter.client.data.OtterHttp
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class RedditAccessToken(
    val value: String,
)

/** Owns Reddit's installed-app OAuth lifecycle. Access tokens never leave memory. */
class RedditOAuthManager(
    configuration: RedditApiConfiguration,
    private val store: RedditOAuthStore,
    private val httpClient: OkHttpClient = OtterHttp.client,
    private val authBaseUrl: String = "https://www.reddit.com",
    private val apiBaseUrl: String = "https://oauth.reddit.com",
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
    private val monotonicMillis: () -> Long = {
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
    },
) {
    private val configuration = configuration.normalized()

    private val clientId: String
        get() = configuration.clientId

    private val userAgent: String
        get() = configuration.userAgent

    private val credentialStorageKey = configuration.credentialStorageKey()

    private data class CachedToken(val value: String, val expiresAtMillis: Long)

    private data class TokenResponse(
        val accessToken: String,
        val refreshToken: String?,
        val scopes: Set<String>,
        val expiresInSeconds: Long,
    )

    private val tokenMutex = Mutex()
    private val secureRandom = SecureRandom()
    private var userToken: CachedToken? = null

    private val mutableAccountState = MutableStateFlow(
        accountStateFrom(store.loadCredential(credentialStorageKey)),
    )
    val accountState: StateFlow<RedditAccountState> = mutableAccountState.asStateFlow()

    init {
        require(configuration.isUsable) { configuration.validationError().orEmpty() }
    }

    fun beginAuthorization(): Result<String> = runCatching {
        val stateBytes = ByteArray(32).also(secureRandom::nextBytes)
        val state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes)
        check(store.savePendingAuthorization(boundState(state), wallClockMillis())) {
            "Could not securely start Reddit sign-in"
        }
        mutableAccountState.value = RedditAccountState.Authorizing
        authBaseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/v1/authorize.compact")
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("state", state)
            .addQueryParameter("redirect_uri", configuration.redirectUri)
            .addQueryParameter("duration", "permanent")
            .addQueryParameter("scope", REQUESTED_SCOPES.joinToString(" "))
            .build()
            .toString()
    }.onFailure {
        restoreStoredAccountState()
    }

    fun cancelAuthorization() {
        store.clearPendingAuthorization()
        restoreStoredAccountState()
    }

    suspend fun completeAuthorization(callbackUrl: String): Result<RedditAccount> =
        withContext(Dispatchers.IO) {
            runCatching {
                val callback = parseAndValidateCallback(callbackUrl)
                val returnedState = callback["state"]?.singleOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?: throw RedditAuthenticationException("Reddit returned an invalid sign-in state")
                if (!store.consumePendingAuthorization(boundState(returnedState), wallClockMillis())) {
                    throw RedditAuthenticationException("This Reddit sign-in request expired or was already used")
                }
                callback["error"]?.let { returnedErrors ->
                    val error = returnedErrors.singleOrNull()
                        ?.takeIf(String::isNotBlank)
                        ?: throw RedditAuthenticationException("Reddit returned an invalid sign-in error")
                    if (error == "access_denied") {
                        throw RedditAuthenticationException("Reddit sign-in was cancelled")
                    }
                    throw RedditAuthenticationException("Reddit sign-in failed: $error")
                }
                val returnedCodes = callback["code"]
                    ?: throw RedditAuthenticationException("Reddit did not return an authorization code")
                val code = returnedCodes.singleOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?: throw RedditAuthenticationException("Reddit returned an invalid authorization code")

                val token = exchangeCode(code)
                val refreshToken = token.refreshToken
                    ?: throw RedditAuthenticationException("Reddit did not grant permanent account access")
                val identity = fetchIdentity(token.accessToken)
                val scopes = token.scopes.ifEmpty { REQUESTED_SCOPES }
                if ("identity" !in scopes || "read" !in scopes) {
                    throw RedditAuthenticationException("Reddit did not grant the required account permissions")
                }
                val credential = RedditOAuthCredential(
                    refreshToken = refreshToken,
                    accountId = identity.id,
                    username = identity.username,
                    scopes = scopes,
                )
                check(store.saveCredential(credentialStorageKey, credential)) {
                    "Could not securely save the Reddit account"
                }
                cacheToken(token.accessToken, token.expiresInSeconds)
                val account = RedditAccount(identity.id, identity.username, scopes)
                mutableAccountState.value = RedditAccountState.SignedIn(account)
                account
            }.onFailure {
                restoreStoredAccountState()
            }
        }

    suspend fun accessToken(): RedditAccessToken = tokenMutex.withLock {
        RedditAccessToken(validUserTokenLocked())
    }

    suspend fun invalidate(rejected: RedditAccessToken) = tokenMutex.withLock {
        if (userToken?.value == rejected.value) userToken = null
        // Reddit has refused this token, so the persisted copy is worthless too. Dropping it
        // here is what stops the next cold start from confidently replaying a dead token.
        if (store.loadAccessToken(credentialStorageKey)?.value == rejected.value) {
            store.saveAccessToken(credentialStorageKey, null)
        }
    }

    suspend fun disconnect(): Result<Unit> {
        tokenMutex.withLock {
            val currentRefreshToken = store.loadCredential(credentialStorageKey)?.refreshToken
            // Queued before the credential is destroyed. Revocation needs the network, and this
            // is the only copy of the token that can ever retire the grant on Reddit's side.
            if (!currentRefreshToken.isNullOrBlank()) {
                store.savePendingRevocation(credentialStorageKey, currentRefreshToken)
            }
            userToken = null
            store.clearPendingAuthorization()
            store.clearCredential()
            mutableAccountState.value = RedditAccountState.SignedOut
        }
        // Local sign-out has already happened; this only decides whether the grant dies now or
        // on a later run.
        return flushPendingRevocations()
    }

    /**
     * Retires grants that an earlier disconnect could not reach Reddit to revoke.
     *
     * Safe to call on startup: an empty queue is a no-op, and a token that still cannot be
     * revoked stays queued for the next attempt.
     */
    suspend fun flushPendingRevocations(): Result<Unit> =
        // Reading the queue decrypts it, so keep that off whichever thread called in.
        withContext(Dispatchers.IO) {
            val pending = store.loadPendingRevocations(credentialStorageKey)
            if (pending.isEmpty()) return@withContext Result.success(Unit)
            var firstFailure: Throwable? = null
            for (token in pending) {
                val outcome = revokeRefreshToken(token)
                if (outcome.error != null && firstFailure == null) firstFailure = outcome.error
                // Dequeue on success, and also when Reddit rejected the request in a way that
                // repeating it cannot fix -- otherwise the queue never drains.
                if (outcome.dequeue) store.clearPendingRevocation(credentialStorageKey, token)
            }
            val failure = firstFailure
            if (failure == null) Result.success(Unit) else Result.failure(failure)
        }

    /**
     * [dequeue] is deliberately independent of [error]: a token Reddit will never accept has to
     * leave the queue even though the attempt failed, or it is retried on every launch forever.
     */
    private data class RevocationOutcome(val dequeue: Boolean, val error: Throwable?)

    private fun revokeRefreshToken(refreshToken: String): RevocationOutcome {
        val body = FormBody.Builder()
            .add("token", refreshToken)
            .add("token_type_hint", "refresh_token")
            .build()
        val request = Request.Builder()
            .url("${authBaseUrl.trimEnd('/')}/api/v1/revoke_token")
            .header("Authorization", Credentials.basic(clientId, ""))
            .header("User-Agent", userAgent)
            .post(body)
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) return RevocationOutcome(dequeue = true, error = null)
                val retryable = response.code == HTTP_TOO_MANY_REQUESTS || response.code >= 500
                RevocationOutcome(
                    dequeue = !retryable,
                    error = IOException("Reddit token revocation failed (${response.code})"),
                )
            }
        } catch (error: IOException) {
            // Offline, DNS failure, or a dropped connection -- all worth another attempt later.
            RevocationOutcome(dequeue = false, error = error)
        }
    }

    private fun validUserTokenLocked(): String {
        current(userToken)?.let { return it }
        restoredToken()?.let { return it }
        val credential = store.loadCredential(credentialStorageKey)
            ?: throw RedditAuthenticationException("Connect your Reddit account first")
        val token = requestToken(
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", credential.refreshToken)
                .build(),
        )
        val replacement = token.refreshToken?.takeIf(String::isNotBlank) ?: credential.refreshToken
        if (replacement != credential.refreshToken) {
            check(
                store.saveCredential(
                    credentialStorageKey,
                    credential.copy(refreshToken = replacement),
                ),
            ) {
                "Could not securely update the Reddit session"
            }
        }
        cacheToken(token.accessToken, token.expiresInSeconds)
        return token.accessToken
    }

    /**
     * Adopts a token left behind by an earlier process, when it has enough life left to be worth
     * using.
     *
     * The persisted expiry is wall-clock, so it is only as trustworthy as the device's clock. It
     * does not need to be authoritative: a token that turns out to be dead comes back as a 401,
     * and [invalidate] already drops it and forces a refresh. The cost of being wrong is one
     * retried request; the cost of not trying is a round trip on every single cold start.
     */
    private fun restoredToken(): String? {
        val stored = store.loadAccessToken(credentialStorageKey) ?: return null
        val remaining = stored.expiresAtEpochMillis - wallClockMillis()
        if (remaining <= TOKEN_EXPIRY_SKEW_MILLIS) {
            store.saveAccessToken(credentialStorageKey, null)
            return null
        }
        // Re-seat it against the monotonic clock so the rest of this process uses the fast path
        // and never has to trust the wall clock again.
        userToken = CachedToken(stored.value, monotonicMillis() + remaining)
        return stored.value
    }

    private fun cacheToken(accessToken: String, expiresInSeconds: Long) {
        val lifetimeMillis = TimeUnit.SECONDS.toMillis(expiresInSeconds.coerceAtLeast(60L))
        userToken = CachedToken(accessToken, monotonicMillis() + lifetimeMillis)
        store.saveAccessToken(
            credentialStorageKey,
            StoredAccessToken(accessToken, wallClockMillis() + lifetimeMillis),
        )
    }

    private fun exchangeCode(code: String): TokenResponse = requestToken(
        FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", configuration.redirectUri)
            .build(),
    )

    private fun requestToken(body: FormBody): TokenResponse {
        val request = Request.Builder()
            .url("${authBaseUrl.trimEnd('/')}/api/v1/access_token")
            .header("Authorization", Credentials.basic(clientId, ""))
            .header("User-Agent", userAgent)
            .post(body)
            .build()
        httpClient.newCall(request).execute().use { response ->
            val payload = response.body.string()
            val json = payload.takeIf(String::isNotBlank)?.let(::JSONObject) ?: JSONObject()
            if (!response.isSuccessful || json.has("error")) {
                val oauthError = json.optString("error")
                if (oauthError == "invalid_grant") {
                    userToken = null
                    store.clearCredential()
                    mutableAccountState.value = RedditAccountState.SignedOut
                    throw RedditAuthenticationException("Reddit access expired; connect your account again")
                }
                throw IOException("Reddit authorization failed (${response.code})")
            }
            return TokenResponse(
                accessToken = json.optString("access_token").takeIf(String::isNotBlank)
                    ?: throw IOException("Reddit did not return an access token"),
                refreshToken = json.optString("refresh_token").takeIf(String::isNotBlank),
                scopes = json.optString("scope")
                    .split(' ')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toSet(),
                expiresInSeconds = json.optLong("expires_in", 3_600L),
            )
        }
    }

    private fun fetchIdentity(accessToken: String): RedditAccount {
        val request = Request.Builder()
            .url("${apiBaseUrl.trimEnd('/')}/api/v1/me")
            .header("Authorization", "bearer $accessToken")
            .header("User-Agent", userAgent)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            val payload = response.body.string()
            if (!response.isSuccessful) throw IOException("Reddit account verification failed (${response.code})")
            val json = JSONObject(payload)
            val id = json.optString("id").takeIf(String::isNotBlank)
                ?: throw IOException("Reddit account ID was missing")
            val username = json.optString("name").takeIf(String::isNotBlank)
                ?: throw IOException("Reddit username was missing")
            return RedditAccount(id, username, emptySet())
        }
    }

    private fun parseAndValidateCallback(callbackUrl: String): Map<String, List<String>> {
        val uri = runCatching { URI(callbackUrl) }.getOrElse {
            throw RedditAuthenticationException("Reddit returned an invalid callback")
        }
        if (!configuration.matchesCallback(callbackUrl)) {
            throw RedditAuthenticationException("Ignored an unexpected sign-in callback")
        }
        return uri.rawQuery.orEmpty()
            .split('&')
            .filter(String::isNotBlank)
            .map { pair ->
                val parts = pair.split('=', limit = 2)
                decode(parts[0]) to decode(parts.getOrElse(1) { "" })
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun restoreStoredAccountState() {
        mutableAccountState.value = accountStateFrom(store.loadCredential(credentialStorageKey))
    }

    private fun accountStateFrom(credential: RedditOAuthCredential?): RedditAccountState =
        credential?.let {
            RedditAccountState.SignedIn(RedditAccount(it.accountId, it.username, it.scopes))
        } ?: RedditAccountState.SignedOut

    private fun current(token: CachedToken?): String? = token
        ?.takeIf { cached -> cached.expiresAtMillis - monotonicMillis() > TOKEN_EXPIRY_SKEW_MILLIS }
        ?.value

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun boundState(state: String): String = buildString {
        append(state)
        append('\u0000')
        append(configuration.clientId)
        append('\u0000')
        append(configuration.userAgent)
        append('\u0000')
        append(configuration.redirectUri)
    }

    companion object {
        val REQUESTED_SCOPES: Set<String> = linkedSetOf(
            "identity",
            "read",
            "mysubreddits",
            "history",
            "vote",
            "save",
            "submit",
            "edit",
            "report",
            "subscribe",
            "account",
        )
        private const val TOKEN_EXPIRY_SKEW_MILLIS = 90_000L
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
