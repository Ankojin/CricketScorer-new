package `in`.nrkmart.cricscore

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object CloudSyncManager {
    private const val PREFS_NAME = "cric_cloud_sync"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_NAME = "name"
    private const val KEY_API_BASE = "api_base"
    private const val KEY_PENDING_MATCH_OPS = "pending_match_ops"

    private const val DEFAULT_API_BASE = "https://cricleagueapi.nrkmart.in"
    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 20000

    data class Session(
        val token: String,
        val userId: String,
        val email: String,
        val name: String,
        val apiBase: String
    )

    data class ShareTokenResult(
        val matchId: String,
        val spectatorToken: String,
        val tokenVersion: Int?,
        val expiresInSeconds: Int,
        val active: Boolean,
        val issuedAt: String?
    )

    data class RevokeShareResult(
        val matchId: String,
        val revoked: Boolean,
        val active: Boolean,
        val revokedAt: String?
    )

    private data class PendingMatchOp(
        val matchId: String,
        val action: String, // UPSERT | DELETE
        val payload: String? = null,
        val updatedAt: Long = System.currentTimeMillis(),
        val attempts: Int = 0,
        val lastError: String? = null
    )

    private class CloudApiException(val statusCode: Int, message: String) : IllegalStateException(message)

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingUpsertJobs = ConcurrentHashMap<String, Job>()
    private val pendingDeleteJobs = ConcurrentHashMap<String, Job>()

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _session.value = loadSessionFromPrefs()
        if (isSignedIn()) {
            syncScope.launch { processPendingQueue() }
        }
    }

    fun isSignedIn(): Boolean = _session.value != null

    fun signOut() {
        if (!::prefs.isInitialized) return
        pendingUpsertJobs.values.forEach { it.cancel() }
        pendingDeleteJobs.values.forEach { it.cancel() }
        pendingUpsertJobs.clear()
        pendingDeleteJobs.clear()
        clearPendingOps()
        prefs.edit().clear().apply()
        _session.value = null
    }

    suspend fun register(email: String, password: String, name: String?): Session = withContext(Dispatchers.IO) {
        val payload = JsonObject().apply {
            addProperty("email", email.trim())
            addProperty("password", password)
            if (!name.isNullOrBlank()) addProperty("name", name.trim())
        }
        val body = post("/auth/register", payload)
        val session = parseSessionFromAuthBody(body)
        persistSession(session)
        session
    }

    suspend fun login(email: String, password: String): Session = withContext(Dispatchers.IO) {
        val payload = JsonObject().apply {
            addProperty("email", email.trim())
            addProperty("password", password)
        }
        val body = post("/auth/login", payload)
        val session = parseSessionFromAuthBody(body)
        persistSession(session)
        session
    }

    suspend fun pullCloudMatches(): List<Match> = withContext(Dispatchers.IO) {
        val currentSession = _session.value ?: throw IllegalStateException("Please sign in first")
        val body = get("/matches", currentSession)
        parseMatchList(body)
    }

    suspend fun upsertMatchToCloud(match: Match): Match = withContext(Dispatchers.IO) {
        val currentSession = _session.value ?: throw IllegalStateException("Please sign in first")
        val payload = gson.toJsonTree(match.safeCopy())
        val body = try {
            putAuthorized("/matches/${match.id}", payload, currentSession)
        } catch (e: CloudApiException) {
            if (e.statusCode == 403 || e.statusCode == 404) {
                postAuthorized("/matches", payload, currentSession)
            } else {
                throw e
            }
        }
        gson.fromJson(body, Match::class.java)?.safeCopy() ?: match.safeCopy()
    }

    suspend fun deleteMatchFromCloud(matchId: String) = withContext(Dispatchers.IO) {
        val currentSession = _session.value ?: throw IllegalStateException("Please sign in first")
        deleteAuthorized("/matches/$matchId", currentSession)
    }

    suspend fun createSpectatorShareToken(matchId: String, ttlMinutes: Int = 360): ShareTokenResult = withContext(Dispatchers.IO) {
        val currentSession = _session.value ?: throw IllegalStateException("Please sign in first")
        val payload = JsonObject().apply { addProperty("ttlMinutes", ttlMinutes) }
        val body = postAuthorized("/matches/$matchId/share-token", payload, currentSession)
        val root = gson.fromJson(body, JsonObject::class.java)
            ?: throw IllegalStateException("Invalid share token response")

        val token = root.get("spectatorToken")?.asString?.trim().orEmpty()
        if (token.isBlank()) throw IllegalStateException("Missing spectator token in response")

        val shareStatus = root.getAsJsonObject("shareStatus")
        ShareTokenResult(
            matchId = root.get("matchId")?.asString?.trim().orEmpty().ifBlank { matchId },
            spectatorToken = token,
            tokenVersion = root.get("tokenVersion")?.asInt,
            expiresInSeconds = root.get("expiresInSeconds")?.asInt ?: ttlMinutes * 60,
            active = shareStatus?.get("active")?.asBoolean ?: true,
            issuedAt = shareStatus?.get("issuedAt")?.asString
        )
    }

    suspend fun revokeSpectatorShareToken(matchId: String): RevokeShareResult = withContext(Dispatchers.IO) {
        val currentSession = _session.value ?: throw IllegalStateException("Please sign in first")
        val body = postAuthorized("/matches/$matchId/revoke-share", JsonObject(), currentSession)
        val root = gson.fromJson(body, JsonObject::class.java)
            ?: throw IllegalStateException("Invalid revoke response")

        val shareStatus = root.getAsJsonObject("shareStatus")
        RevokeShareResult(
            matchId = root.get("matchId")?.asString?.trim().orEmpty().ifBlank { matchId },
            revoked = root.get("revoked")?.asBoolean ?: true,
            active = shareStatus?.get("active")?.asBoolean ?: false,
            revokedAt = shareStatus?.get("revokedAt")?.asString
        )
    }

    fun enqueueMatchUpsert(match: Match) {
        if (!isSignedIn()) return
        val matchCopy = match.safeCopy()
        val matchId = matchCopy.id
        savePendingOp(PendingMatchOp(matchId = matchId, action = "UPSERT", payload = gson.toJson(matchCopy)))
        pendingDeleteJobs.remove(matchId)?.cancel()
        pendingUpsertJobs[matchId]?.cancel()
        pendingUpsertJobs[matchId] = syncScope.launch {
            delay(1500)
            processPendingOp(matchId)
            pendingUpsertJobs.remove(matchId)
        }
    }

    fun enqueueMatchDelete(matchId: String) {
        if (!isSignedIn()) return
        savePendingOp(PendingMatchOp(matchId = matchId, action = "DELETE"))
        pendingUpsertJobs.remove(matchId)?.cancel()
        pendingDeleteJobs[matchId]?.cancel()
        pendingDeleteJobs[matchId] = syncScope.launch {
            delay(300)
            processPendingOp(matchId)
            pendingDeleteJobs.remove(matchId)
        }
    }

    suspend fun processPendingQueue() = withContext(Dispatchers.IO) {
        val currentSession = _session.value ?: return@withContext
        val pending = loadPendingOps()
            .values
            .sortedBy { it.updatedAt }

        for (op in pending) {
            if (_session.value?.token != currentSession.token) break
            processPendingOp(op.matchId)
        }
    }

    fun importCloudMatchesToLocal(matches: List<Match>): Int {
        var imported = 0
        matches.forEach { match ->
            val wrapper = JsonObject().apply {
                addProperty("type", "MATCH_BACKUP")
                add("match", gson.toJsonTree(match.safeCopy()))
            }
            if (TournamentRepository.importTournament(gson.toJson(wrapper))) {
                imported++
            }
        }
        return imported
    }

    private fun parseMatchList(body: String): List<Match> {
        val root = gson.fromJson(body, JsonElement::class.java)
        val listElement: JsonElement = when {
            root == null || root.isJsonNull -> return emptyList()
            root.isJsonArray -> root
            root.isJsonObject && root.asJsonObject.has("items") -> root.asJsonObject.get("items")
            root.isJsonObject && root.asJsonObject.has("data") -> root.asJsonObject.get("data")
            root.isJsonObject && root.asJsonObject.has("results") -> root.asJsonObject.get("results")
            else -> return emptyList()
        }

        val type = object : TypeToken<List<Match>>() {}.type
        return gson.fromJson<List<Match>>(listElement, type)
            ?.orEmpty()
            ?.map { it.safeCopy() }
            ?: emptyList()
    }

    private fun parseSessionFromAuthBody(body: String): Session {
        val root = gson.fromJson(body, JsonObject::class.java)
            ?: throw IllegalStateException("Invalid auth response")

        val token = root.get("token")?.asString?.trim().orEmpty()
        val user = root.getAsJsonObject("user")
        val userId = user?.get("userId")?.asString?.trim().orEmpty()
        val email = user?.get("email")?.asString?.trim().orEmpty()
        val name = user?.get("name")?.asString?.trim().orEmpty()

        if (token.isBlank() || userId.isBlank()) {
            throw IllegalStateException("Missing token or user information")
        }

        return Session(
            token = token,
            userId = userId,
            email = email,
            name = name,
            apiBase = DEFAULT_API_BASE
        )
    }

    private fun persistSession(session: Session) {
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_NAME, session.name)
            .putString(KEY_API_BASE, session.apiBase)
            .apply()
        _session.value = session
        syncScope.launch { processPendingQueue() }
    }

    private suspend fun processPendingOp(matchId: String) {
        val op = loadPendingOps()[matchId] ?: return

        val result = runCatching {
            when (op.action) {
                "UPSERT" -> {
                    val payload = op.payload ?: throw IllegalStateException("Missing pending match payload")
                    val match = gson.fromJson(payload, Match::class.java)?.safeCopy()
                        ?: throw IllegalStateException("Invalid pending match payload")
                    upsertMatchToCloud(match)
                }
                "DELETE" -> deleteMatchFromCloud(matchId)
                else -> throw IllegalStateException("Unknown pending action")
            }
        }

        if (result.isSuccess) {
            removePendingOp(matchId)
            return
        }

        val err = result.exceptionOrNull()
        val status = (err as? CloudApiException)?.statusCode
        if (status in 400..499 && status != 429) {
            removePendingOp(matchId)
            return
        }

        val attempts = op.attempts + 1
        savePendingOp(
            op.copy(
                attempts = attempts,
                lastError = err?.message,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun loadPendingOps(): MutableMap<String, PendingMatchOp> {
        if (!::prefs.isInitialized) return mutableMapOf()
        val raw = prefs.getString(KEY_PENDING_MATCH_OPS, null)?.trim().orEmpty()
        if (raw.isBlank()) return mutableMapOf()

        return try {
            val type = object : TypeToken<MutableMap<String, PendingMatchOp>>() {}.type
            gson.fromJson<MutableMap<String, PendingMatchOp>>(raw, type) ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun savePendingMap(map: MutableMap<String, PendingMatchOp>) {
        if (!::prefs.isInitialized) return
        prefs.edit().putString(KEY_PENDING_MATCH_OPS, gson.toJson(map)).apply()
    }

    private fun savePendingOp(op: PendingMatchOp) {
        val map = loadPendingOps()
        map[op.matchId] = op
        savePendingMap(map)
    }

    private fun removePendingOp(matchId: String) {
        val map = loadPendingOps()
        if (map.remove(matchId) != null) {
            savePendingMap(map)
        }
    }

    private fun clearPendingOps() {
        if (!::prefs.isInitialized) return
        prefs.edit().remove(KEY_PENDING_MATCH_OPS).apply()
    }

    private fun loadSessionFromPrefs(): Session? {
        if (!::prefs.isInitialized) return null
        val token = prefs.getString(KEY_TOKEN, null)?.trim().orEmpty()
        val userId = prefs.getString(KEY_USER_ID, null)?.trim().orEmpty()
        if (token.isBlank() || userId.isBlank()) return null

        return Session(
            token = token,
            userId = userId,
            email = prefs.getString(KEY_EMAIL, "")?.trim().orEmpty(),
            name = prefs.getString(KEY_NAME, "")?.trim().orEmpty(),
            apiBase = prefs.getString(KEY_API_BASE, DEFAULT_API_BASE)?.trim().orEmpty().ifBlank { DEFAULT_API_BASE }
        )
    }

    private fun get(path: String, session: Session): String {
        val url = URL("${session.apiBase.trimEnd('/')}$path")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${session.token}")
        }
        return execute(connection)
    }

    private fun post(path: String, payload: JsonObject): String {
        val url = URL("${DEFAULT_API_BASE.trimEnd('/')}$path")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        connection.outputStream.use { os ->
            os.write(gson.toJson(payload).toByteArray(Charsets.UTF_8))
        }

        return execute(connection)
    }

    private fun postAuthorized(path: String, payload: JsonElement, session: Session): String {
        val url = URL("${session.apiBase.trimEnd('/')}$path")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${session.token}")
        }

        connection.outputStream.use { os ->
            os.write(gson.toJson(payload).toByteArray(Charsets.UTF_8))
        }

        return execute(connection)
    }

    private fun putAuthorized(path: String, payload: JsonElement, session: Session): String {
        val url = URL("${session.apiBase.trimEnd('/')}$path")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${session.token}")
        }

        connection.outputStream.use { os ->
            os.write(gson.toJson(payload).toByteArray(Charsets.UTF_8))
        }

        return execute(connection)
    }

    private fun deleteAuthorized(path: String, session: Session): String {
        val url = URL("${session.apiBase.trimEnd('/')}$path")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${session.token}")
        }
        return execute(connection)
    }

    private fun execute(connection: HttpURLConnection): String {
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()

            if (status !in 200..299) {
                val message = try {
                    gson.fromJson(body, JsonObject::class.java)?.get("error")?.asString
                } catch (_: Exception) {
                    null
                }
                throw CloudApiException(status, message ?: "Cloud API error (HTTP $status)")
            }
            body
        } finally {
            connection.disconnect()
        }
    }
}
