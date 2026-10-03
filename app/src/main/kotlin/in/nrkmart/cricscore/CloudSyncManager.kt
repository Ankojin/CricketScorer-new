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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

object CloudSyncManager {
    private const val PREFS_NAME = "cric_cloud_sync"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_NAME = "name"
    private const val KEY_API_BASE = "api_base"
    private const val KEY_PENDING_MATCH_OPS = "pending_match_ops"
    private const val KEY_PENDING_TOURNAMENT_OPS = "pending_tournament_ops"
    private const val KEY_MATCH_OWNER_CLAIMS = "match_owner_claims"
    private const val KEY_TOURNAMENT_OWNER_CLAIMS = "tournament_owner_claims"

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
        val lastError: String? = null,
        val ownerUserId: String? = null,
        val operationId: String? = null
    )

    private data class PendingTournamentOp(
        val tournamentId: String,
        val action: String,
        val payload: String? = null,
        val updatedAt: Long = System.currentTimeMillis(),
        val attempts: Int = 0,
        val lastError: String? = null,
        val ownerUserId: String? = null,
        val operationId: String? = null
    )

    private class CloudApiException(val statusCode: Int, message: String) : IllegalStateException(message)
    class StaleRevisionException(message: String) : IllegalStateException(message)
    class StaleTournamentException(message: String) : IllegalStateException(message)

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingUpsertJobs = ConcurrentHashMap<String, Job>()
    private val pendingDeleteJobs = ConcurrentHashMap<String, Job>()
    private val pendingTournamentJobs = ConcurrentHashMap<String, Job>()
    private val pendingOperationMutexes = ConcurrentHashMap<String, Mutex>()
    private val pendingTournamentMutexes = ConcurrentHashMap<String, Mutex>()
    private val matchWriteMutexes = ConcurrentHashMap<String, Mutex>()
    private val tournamentWriteMutexes = ConcurrentHashMap<String, Mutex>()
    private val knownServerRevisions = ConcurrentHashMap<String, Int>()
    private val pendingOpsLock = Any()

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _session.value = loadSessionFromPrefs()
        if (isSignedIn()) {
            syncScope.launch { processPendingQueue() }
            syncScope.launch { processPendingTournamentQueue() }
        }
    }

    fun isSignedIn(): Boolean = _session.value != null

    fun apiBaseUrl(): String = _session.value?.apiBase ?: DEFAULT_API_BASE

    fun signOut() {
        if (!::prefs.isInitialized) return
        pendingUpsertJobs.values.forEach { it.cancel() }
        pendingDeleteJobs.values.forEach { it.cancel() }
        pendingTournamentJobs.values.forEach { it.cancel() }
        pendingUpsertJobs.clear()
        pendingDeleteJobs.clear()
        pendingTournamentJobs.clear()
        // Pending operations remain tagged to their account for the next sign-in.
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .remove(KEY_NAME)
            .remove(KEY_API_BASE)
            .apply()
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
        if (_session.value?.userId != currentSession.userId) {
            throw IllegalStateException("Account changed during cloud sync")
        }
        parseMatchList(body).also(::rememberServerRevisions)
    }

    suspend fun pullCloudTournaments(): List<Tournament> = withContext(Dispatchers.IO) {
        val currentSession = _session.value ?: throw IllegalStateException("Please sign in first")
        val root = gson.fromJson(get("/tournaments", currentSession), JsonElement::class.java)
        if (_session.value?.userId != currentSession.userId) {
            throw IllegalStateException("Account changed during cloud sync")
        }
        val list = when {
            root == null || root.isJsonNull -> return@withContext emptyList()
            root.isJsonArray -> root
            root.isJsonObject && root.asJsonObject.has("items") -> root.asJsonObject.get("items")
            root.isJsonObject && root.asJsonObject.has("data") -> root.asJsonObject.get("data")
            else -> return@withContext emptyList()
        }
        val type = object : TypeToken<List<Tournament>>() {}.type
        gson.fromJson<List<Tournament>>(list, type).orEmpty().map { it.safeCopy(matches = emptyList()) }
    }

    suspend fun upsertTournamentToCloud(tournament: Tournament): Tournament = withContext(Dispatchers.IO) {
        val currentSession = _session.value ?: throw IllegalStateException("Please sign in first")
        if (!canUploadTournament(tournament.id)) {
            throw IllegalStateException("Series is not claimed by the signed-in account")
        }
        tournamentWriteMutexes.computeIfAbsent(tournament.id) { Mutex() }.withLock {
            val snapshot = tournament.safeCopy(matches = emptyList())
            val body = postAuthorized("/tournaments", gson.toJsonTree(snapshot), currentSession)
            val synced = gson.fromJson(body, Tournament::class.java)?.safeCopy(matches = emptyList()) ?: snapshot
            acknowledgePendingTournamentUpsert(snapshot, currentSession.userId)
            synced
        }
    }

    suspend fun deleteTournamentFromCloud(tournamentId: String) = withContext(Dispatchers.IO) {
        val currentSession = _session.value ?: throw IllegalStateException("Please sign in first")
        deleteAuthorized("/tournaments/$tournamentId", currentSession)
    }

    suspend fun upsertMatchToCloud(match: Match): Match = withContext(Dispatchers.IO) {
        val currentSession = _session.value ?: throw IllegalStateException("Please sign in first")
        if (!canUploadMatch(match.id)) {
            throw IllegalStateException("Match is not claimed by the signed-in account")
        }
        matchWriteMutexes.computeIfAbsent(match.id) { Mutex() }.withLock {
            val latestKnownRevision = maxOf(match.revision ?: 0, knownServerRevisions[match.id] ?: 0)
            val outboundMatch = match.safeCopy().copy(revision = latestKnownRevision + 1)
            val payload = gson.toJsonTree(outboundMatch)
            val body = try {
                putAuthorized("/matches/${match.id}", payload, currentSession)
            } catch (e: CloudApiException) {
                if (e.statusCode == 403 || e.statusCode == 404) {
                    postAuthorized("/matches", payload, currentSession)
                } else {
                    throw e
                }
            }
            val synced = gson.fromJson(body, Match::class.java)?.safeCopy() ?: outboundMatch
            val appliedRevision = synced.revision ?: outboundMatch.revision ?: latestKnownRevision + 1
            knownServerRevisions[match.id] = appliedRevision
            TournamentRepository.updateMatchSyncMetadata(synced.id, appliedRevision, synced.updatedAt)
            acknowledgePendingUpsert(match, currentSession.userId)
            synced.copy(revision = appliedRevision)
        }
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
        val currentUserId = _session.value?.userId ?: return
        val matchCopy = match.safeCopy()
        val matchId = matchCopy.id
        if (!canUploadMatch(matchId)) return
        savePendingOp(PendingMatchOp(
            matchId = matchId,
            action = "UPSERT",
            payload = gson.toJson(matchCopy),
            ownerUserId = currentUserId,
            operationId = UUID.randomUUID().toString()
        ))
        pendingDeleteJobs.remove(matchId)?.cancel()
        pendingUpsertJobs[matchId]?.cancel()
        pendingUpsertJobs[matchId] = syncScope.launch {
            delay(1500)
            processPendingOp(matchId)
            pendingUpsertJobs.remove(matchId)
        }
    }

    fun enqueueMatchDelete(matchId: String) {
        val currentUserId = _session.value?.userId ?: return
        if (!canUploadMatch(matchId)) return
        savePendingOp(PendingMatchOp(
            matchId = matchId,
            action = "DELETE",
            ownerUserId = currentUserId,
            operationId = UUID.randomUUID().toString()
        ))
        pendingUpsertJobs.remove(matchId)?.cancel()
        pendingDeleteJobs[matchId]?.cancel()
        pendingDeleteJobs[matchId] = syncScope.launch {
            delay(300)
            processPendingOp(matchId)
            pendingDeleteJobs.remove(matchId)
        }
    }

    fun enqueueTournamentUpsert(tournament: Tournament) {
        val currentUserId = _session.value?.userId ?: return
        if (!canUploadTournament(tournament.id)) return
        val snapshot = tournament.safeCopy(matches = emptyList())
        savePendingTournamentOp(PendingTournamentOp(
            tournamentId = snapshot.id,
            action = "UPSERT",
            payload = gson.toJson(snapshot),
            ownerUserId = currentUserId,
            operationId = UUID.randomUUID().toString()
        ))
        pendingTournamentJobs[snapshot.id]?.cancel()
        pendingTournamentJobs[snapshot.id] = syncScope.launch {
            delay(1500)
            processPendingTournamentOp(snapshot.id)
            pendingTournamentJobs.remove(snapshot.id)
        }
    }

    fun enqueueTournamentDelete(tournamentId: String) {
        val currentUserId = _session.value?.userId ?: return
        if (!canUploadTournament(tournamentId)) return
        savePendingTournamentOp(PendingTournamentOp(
            tournamentId = tournamentId,
            action = "DELETE",
            ownerUserId = currentUserId,
            operationId = UUID.randomUUID().toString()
        ))
        pendingTournamentJobs[tournamentId]?.cancel()
        pendingTournamentJobs[tournamentId] = syncScope.launch {
            delay(300)
            processPendingTournamentOp(tournamentId)
            pendingTournamentJobs.remove(tournamentId)
        }
    }

    suspend fun processPendingQueue() = withContext(Dispatchers.IO) {
        val currentSession = _session.value ?: return@withContext
        val pending = loadPendingOps()
            .values
            .filter { it.ownerUserId == currentSession.userId }
            .filterNot { it.lastError?.startsWith("STALE_REVISION:") == true }
            .sortedBy { it.updatedAt }

        for (op in pending) {
            if (_session.value?.token != currentSession.token) break
            processPendingOp(op.matchId)
        }
    }

    suspend fun processPendingTournamentQueue() = withContext(Dispatchers.IO) {
        val currentSession = _session.value ?: return@withContext
        val pending = loadPendingTournamentOps().values
            .filter { it.ownerUserId == currentSession.userId }
            .filterNot { it.lastError?.startsWith("STALE_TOURNAMENT:") == true }
            .sortedBy { it.updatedAt }
        for (op in pending) {
            if (_session.value?.token != currentSession.token) break
            processPendingTournamentOp(op.tournamentId)
        }
    }

    fun canUploadMatch(matchId: String): Boolean {
        val currentUserId = _session.value?.userId ?: return false
        val ownerClaims = loadMatchOwnerClaims()
        return ownerClaims[matchId] == currentUserId
    }

    fun canUploadTournament(tournamentId: String): Boolean {
        val currentUserId = _session.value?.userId ?: return false
        return loadTournamentOwnerClaims()[tournamentId] == currentUserId
    }

    fun claimMatchForCurrentUser(matchId: String): Boolean {
        val currentUserId = _session.value?.userId ?: return false
        if (matchId.isBlank()) return false

        synchronized(pendingOpsLock) {
            val ownerClaims = loadMatchOwnerClaims()
            val existingOwner = ownerClaims[matchId]
            if (existingOwner == currentUserId) return true
            if (!existingOwner.isNullOrBlank()) return false
            ownerClaims[matchId] = currentUserId
            saveMatchOwnerClaims(ownerClaims)
            return true
        }
    }

    fun claimTournamentForCurrentUser(tournamentId: String): Boolean {
        val currentUserId = _session.value?.userId ?: return false
        if (tournamentId.isBlank()) return false
        synchronized(pendingOpsLock) {
            val claims = loadTournamentOwnerClaims()
            val existingOwner = claims[tournamentId]
            if (existingOwner == currentUserId) return true
            if (!existingOwner.isNullOrBlank()) return false
            claims[tournamentId] = currentUserId
            saveTournamentOwnerClaims(claims)
            return true
        }
    }

    fun claimMatchesForCurrentUser(matchIds: Collection<String>, expectedUserId: String? = _session.value?.userId) {
        val currentUserId = expectedUserId ?: return
        if (_session.value?.userId != currentUserId) return
        if (matchIds.isEmpty()) return

        val ownerClaims = loadMatchOwnerClaims()
        var changed = false
        matchIds.forEach { matchId ->
            if (matchId.isBlank()) return@forEach
            if (ownerClaims[matchId].isNullOrBlank()) {
                ownerClaims[matchId] = currentUserId
                changed = true
            }
        }
        if (changed) {
            saveMatchOwnerClaims(ownerClaims)
        }
    }

    fun claimTournamentsForCurrentUser(
        tournamentIds: Collection<String>,
        expectedUserId: String? = _session.value?.userId
    ) {
        val currentUserId = expectedUserId ?: return
        if (_session.value?.userId != currentUserId) return
        synchronized(pendingOpsLock) {
            val claims = loadTournamentOwnerClaims()
            var changed = false
            tournamentIds.forEach { tournamentId ->
                if (tournamentId.isBlank()) return@forEach
                if (claims[tournamentId].isNullOrBlank()) {
                    claims[tournamentId] = currentUserId
                    changed = true
                }
            }
            if (changed) saveTournamentOwnerClaims(claims)
        }
    }

    fun claimUnclaimedMatchesForCurrentUser(matchIds: Collection<String>): Int {
        val currentUserId = _session.value?.userId ?: return 0
        if (matchIds.isEmpty()) return 0

        val ownerClaims = loadMatchOwnerClaims()
        var claimed = 0
        matchIds.forEach { matchId ->
            if (matchId.isBlank()) return@forEach
            val existingOwner = ownerClaims[matchId]
            if (existingOwner.isNullOrBlank()) {
                ownerClaims[matchId] = currentUserId
                claimed++
            }
        }
        if (claimed > 0) {
            saveMatchOwnerClaims(ownerClaims)
        }
        return claimed
    }

    fun claimUnclaimedTournamentsForCurrentUser(tournamentIds: Collection<String>): Int {
        val currentUserId = _session.value?.userId ?: return 0
        synchronized(pendingOpsLock) {
            val claims = loadTournamentOwnerClaims()
            var claimed = 0
            tournamentIds.forEach { tournamentId ->
                if (tournamentId.isBlank()) return@forEach
                if (claims[tournamentId].isNullOrBlank()) {
                    claims[tournamentId] = currentUserId
                    claimed++
                }
            }
            if (claimed > 0) saveTournamentOwnerClaims(claims)
            return claimed
        }
    }

    fun canClaimTournamentForCurrentUser(tournamentId: String, matchIds: Collection<String>): Boolean {
        val currentUserId = _session.value?.userId ?: return false
        val tournamentOwner = loadTournamentOwnerClaims()[tournamentId]
        if (!tournamentOwner.isNullOrBlank() && tournamentOwner != currentUserId) return false
        val matchOwners = loadMatchOwnerClaims()
        return matchIds.all { matchId ->
            matchOwners[matchId].let { it.isNullOrBlank() || it == currentUserId }
        }
    }

    fun countUnclaimedTournamentsForCurrentUser(tournamentIds: Collection<String>): Int {
        val currentUserId = _session.value?.userId ?: return 0
        val claims = loadTournamentOwnerClaims()
        return tournamentIds.count { id -> id.isNotBlank() && claims[id].isNullOrBlank() && currentUserId.isNotBlank() }
    }

    fun countUnclaimedMatchesForCurrentUser(matchIds: Collection<String>): Int {
        val currentUserId = _session.value?.userId ?: return 0
        if (matchIds.isEmpty()) return 0

        val ownerClaims = loadMatchOwnerClaims()
        return matchIds.count { matchId ->
            matchId.isNotBlank() && ownerClaims[matchId].isNullOrBlank() && currentUserId.isNotBlank()
        }
    }

    fun importCloudMatchesToLocal(matches: List<Match>, expectedUserId: String? = _session.value?.userId): Int {
        if (expectedUserId.isNullOrBlank() || _session.value?.userId != expectedUserId) return 0
        claimMatchesForCurrentUser(matches.map { it.id }, expectedUserId)
        var imported = 0
        matches.forEach { match ->
            if (_session.value?.userId != expectedUserId) return imported
            val wrapper = JsonObject().apply {
                addProperty("type", "MATCH_BACKUP")
                add("match", gson.toJsonTree(match.safeCopy()))
            }
            if (TournamentRepository.importTournament(gson.toJson(wrapper), expectedUserId)) {
                imported++
            }
        }
        return imported
    }

    fun importCloudTournamentsToLocal(
        tournaments: List<Tournament>,
        excludedTournamentIds: Set<String> = emptySet(),
        expectedUserId: String? = _session.value?.userId
    ): Int {
        if (expectedUserId.isNullOrBlank() || _session.value?.userId != expectedUserId) return 0
        val importable = tournaments.filterNot { it.id in excludedTournamentIds }
        claimTournamentsForCurrentUser(importable.map { it.id }, expectedUserId)
        var imported = 0
        importable.forEach { tournament ->
            if (_session.value?.userId != expectedUserId) return imported
            if (TournamentRepository.importCloudTournament(tournament.safeCopy(matches = emptyList()), expectedUserId)) imported++
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

    private fun rememberServerRevisions(matches: List<Match>) {
        matches.forEach { match ->
            val revision = match.revision ?: return@forEach
            knownServerRevisions.compute(match.id) { _, current -> maxOf(current ?: 0, revision) }
        }
    }

    private fun acknowledgePendingUpsert(match: Match, ownerUserId: String) {
        synchronized(pendingOpsLock) {
            val map = loadPendingOps()
            val key = pendingOpKey(ownerUserId, match.id)
            val pending = map[key] ?: return
            if (pending.action != "UPSERT") return
            val pendingMatch = pending.payload?.let { gson.fromJson(it, Match::class.java)?.safeCopy() } ?: return
            val syncedSnapshot = match.safeCopy().copy(revision = null, updatedAt = null, lastWriterPlatform = null)
            val pendingSnapshot = pendingMatch.copy(revision = null, updatedAt = null, lastWriterPlatform = null)
            if (syncedSnapshot == pendingSnapshot) {
                map.remove(key)
                savePendingMap(map)
            }
        }
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
        if (_session.value?.userId != session.userId) knownServerRevisions.clear()
        prefs.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_NAME, session.name)
            .putString(KEY_API_BASE, session.apiBase)
            .apply()
        _session.value = session
        syncScope.launch { processPendingQueue() }
        syncScope.launch { processPendingTournamentQueue() }
    }

    private suspend fun processPendingOp(matchId: String) {
        val ownerUserId = _session.value?.userId ?: return
        val opLockKey = "$ownerUserId:$matchId"
        pendingOperationMutexes.computeIfAbsent(opLockKey) { Mutex() }.withLock {
            if (_session.value?.userId != ownerUserId) return@withLock
            val op = loadPendingOps()[pendingOpKey(ownerUserId, matchId)] ?: return@withLock
            if (op.lastError?.startsWith("STALE_REVISION:") == true) return@withLock

            val result = runCatching {
                when (op.action) {
                    "UPSERT" -> {
                        val payload = op.payload ?: throw IllegalStateException("Missing pending match payload")
                        val match = gson.fromJson(payload, Match::class.java)?.safeCopy()
                            ?: throw IllegalStateException("Invalid pending match payload")
                        if (op.ownerUserId != ownerUserId || !canUploadMatch(match.id)) {
                            throw IllegalStateException("Pending match belongs to a different account")
                        }
                        upsertMatchToCloud(match)
                    }
                    "DELETE" -> {
                        if (op.ownerUserId != ownerUserId || !canUploadMatch(matchId)) {
                            throw IllegalStateException("Pending match belongs to a different account")
                        }
                        deleteMatchFromCloud(matchId)
                    }
                    else -> throw IllegalStateException("Unknown pending action")
                }
            }

            if (_session.value?.userId != ownerUserId) return@withLock
            if (result.isSuccess) {
                removePendingOp(ownerUserId, matchId, op.operationId)
                return@withLock
            }

            val err = result.exceptionOrNull()
            val status = (err as? CloudApiException)?.statusCode
            if (status in 400..499 && status !in setOf(401, 409, 429)) {
                removePendingOp(ownerUserId, matchId, op.operationId)
                return@withLock
            }

            val message = err?.message.orEmpty()
            val lastError = if (err is StaleRevisionException) "STALE_REVISION:$message" else message
            replacePendingOpIfCurrent(
                op,
                op.copy(
                    attempts = op.attempts + 1,
                    lastError = lastError,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun processPendingTournamentOp(tournamentId: String) {
        val ownerUserId = _session.value?.userId ?: return
        pendingTournamentMutexes.computeIfAbsent("$ownerUserId:$tournamentId") { Mutex() }.withLock {
            if (_session.value?.userId != ownerUserId) return@withLock
            val op = loadPendingTournamentOps()[pendingTournamentKey(ownerUserId, tournamentId)] ?: return@withLock
            if (op.lastError?.startsWith("STALE_TOURNAMENT:") == true) return@withLock

            val result = runCatching {
                when (op.action) {
                    "UPSERT" -> {
                        val payload = op.payload ?: throw IllegalStateException("Missing pending series payload")
                        val tournament = gson.fromJson(payload, Tournament::class.java)?.safeCopy(matches = emptyList())
                            ?: throw IllegalStateException("Invalid pending series payload")
                        if (op.ownerUserId != ownerUserId || !canUploadTournament(tournament.id)) {
                            throw IllegalStateException("Pending series belongs to a different account")
                        }
                        upsertTournamentToCloud(tournament)
                    }
                    "DELETE" -> {
                        if (op.ownerUserId != ownerUserId || !canUploadTournament(tournamentId)) {
                            throw IllegalStateException("Pending series belongs to a different account")
                        }
                        deleteTournamentFromCloud(tournamentId)
                    }
                    else -> throw IllegalStateException("Unknown pending series action")
                }
            }

            if (_session.value?.userId != ownerUserId) return@withLock
            if (result.isSuccess) {
                removePendingTournamentOp(ownerUserId, tournamentId, op.operationId)
                return@withLock
            }

            val err = result.exceptionOrNull()
            val status = (err as? CloudApiException)?.statusCode
            if (status in 400..499 && status !in setOf(401, 409, 429)) {
                removePendingTournamentOp(ownerUserId, tournamentId, op.operationId)
                return@withLock
            }

            val message = err?.message.orEmpty()
            val lastError = if (err is StaleTournamentException || status == 409) "STALE_TOURNAMENT:$message" else message
            replacePendingTournamentOpIfCurrent(
                op,
                op.copy(
                    attempts = op.attempts + 1,
                    lastError = lastError,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun pendingOpKey(ownerUserId: String, matchId: String): String = "$ownerUserId:$matchId"

    private fun pendingTournamentKey(ownerUserId: String, tournamentId: String): String = "$ownerUserId:$tournamentId"

    private fun loadPendingOps(): MutableMap<String, PendingMatchOp> {
        if (!::prefs.isInitialized) return mutableMapOf()
        val raw = prefs.getString(KEY_PENDING_MATCH_OPS, null)?.trim().orEmpty()
        if (raw.isBlank()) return mutableMapOf()

        return try {
            val type = object : TypeToken<MutableMap<String, PendingMatchOp>>() {}.type
            val stored = gson.fromJson<MutableMap<String, PendingMatchOp>>(raw, type) ?: mutableMapOf()
            val ownerClaims = loadMatchOwnerClaims()
            stored.values.associate { op ->
                val ownerUserId = op.ownerUserId ?: ownerClaims[op.matchId]
                val normalized = op.copy(
                    ownerUserId = ownerUserId,
                    operationId = op.operationId ?: "legacy:${op.matchId}:${op.updatedAt}"
                )
                pendingOpKey(ownerUserId.orEmpty(), op.matchId) to normalized
            }.toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun savePendingMap(map: MutableMap<String, PendingMatchOp>) {
        if (!::prefs.isInitialized) return
        prefs.edit().putString(KEY_PENDING_MATCH_OPS, gson.toJson(map)).apply()
    }

    private fun savePendingOp(op: PendingMatchOp) {
        val ownerUserId = op.ownerUserId ?: return
        synchronized(pendingOpsLock) {
            val map = loadPendingOps()
            map[pendingOpKey(ownerUserId, op.matchId)] = op
            savePendingMap(map)
        }
    }

    private fun removePendingOp(ownerUserId: String, matchId: String, operationId: String?) {
        synchronized(pendingOpsLock) {
            val map = loadPendingOps()
            val key = pendingOpKey(ownerUserId, matchId)
            if (map[key]?.operationId == operationId && map.remove(key) != null) {
                savePendingMap(map)
            }
        }
    }

    private fun replacePendingOpIfCurrent(current: PendingMatchOp, replacement: PendingMatchOp) {
        val ownerUserId = current.ownerUserId ?: return
        synchronized(pendingOpsLock) {
            val map = loadPendingOps()
            val key = pendingOpKey(ownerUserId, current.matchId)
            if (map[key]?.operationId == current.operationId) {
                map[key] = replacement
                savePendingMap(map)
            }
        }
    }

    private fun loadPendingTournamentOps(): MutableMap<String, PendingTournamentOp> {
        if (!::prefs.isInitialized) return mutableMapOf()
        val raw = prefs.getString(KEY_PENDING_TOURNAMENT_OPS, null)?.trim().orEmpty()
        if (raw.isBlank()) return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, PendingTournamentOp>>() {}.type
            val stored = gson.fromJson<MutableMap<String, PendingTournamentOp>>(raw, type) ?: mutableMapOf()
            val ownerClaims = loadTournamentOwnerClaims()
            stored.values.associate { op ->
                val ownerUserId = op.ownerUserId ?: ownerClaims[op.tournamentId]
                val normalized = op.copy(
                    ownerUserId = ownerUserId,
                    operationId = op.operationId ?: "legacy:${op.tournamentId}:${op.updatedAt}"
                )
                pendingTournamentKey(ownerUserId.orEmpty(), op.tournamentId) to normalized
            }.toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun savePendingTournamentMap(map: MutableMap<String, PendingTournamentOp>) {
        if (!::prefs.isInitialized) return
        prefs.edit().putString(KEY_PENDING_TOURNAMENT_OPS, gson.toJson(map)).apply()
    }

    private fun savePendingTournamentOp(op: PendingTournamentOp) {
        val ownerUserId = op.ownerUserId ?: return
        synchronized(pendingOpsLock) {
            val map = loadPendingTournamentOps()
            map[pendingTournamentKey(ownerUserId, op.tournamentId)] = op
            savePendingTournamentMap(map)
        }
    }

    private fun removePendingTournamentOp(ownerUserId: String, tournamentId: String, operationId: String?) {
        synchronized(pendingOpsLock) {
            val map = loadPendingTournamentOps()
            val key = pendingTournamentKey(ownerUserId, tournamentId)
            if (map[key]?.operationId == operationId && map.remove(key) != null) {
                savePendingTournamentMap(map)
            }
        }
    }

    private fun replacePendingTournamentOpIfCurrent(current: PendingTournamentOp, replacement: PendingTournamentOp) {
        val ownerUserId = current.ownerUserId ?: return
        synchronized(pendingOpsLock) {
            val map = loadPendingTournamentOps()
            val key = pendingTournamentKey(ownerUserId, current.tournamentId)
            if (map[key]?.operationId == current.operationId) {
                map[key] = replacement
                savePendingTournamentMap(map)
            }
        }
    }

    private fun acknowledgePendingTournamentUpsert(tournament: Tournament, ownerUserId: String) {
        synchronized(pendingOpsLock) {
            val map = loadPendingTournamentOps()
            val key = pendingTournamentKey(ownerUserId, tournament.id)
            val pending = map[key] ?: return
            if (pending.action != "UPSERT") return
            val pendingTournament = pending.payload?.let { gson.fromJson(it, Tournament::class.java)?.safeCopy(matches = emptyList()) } ?: return
            if (pendingTournament == tournament.safeCopy(matches = emptyList())) {
                map.remove(key)
                savePendingTournamentMap(map)
            }
        }
    }

    private fun loadMatchOwnerClaims(): MutableMap<String, String> {
        if (!::prefs.isInitialized) return mutableMapOf()
        val raw = prefs.getString(KEY_MATCH_OWNER_CLAIMS, null)?.trim().orEmpty()
        if (raw.isBlank()) return mutableMapOf()

        return try {
            val type = object : TypeToken<MutableMap<String, String>>() {}.type
            gson.fromJson<MutableMap<String, String>>(raw, type) ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveMatchOwnerClaims(map: MutableMap<String, String>) {
        if (!::prefs.isInitialized) return
        prefs.edit().putString(KEY_MATCH_OWNER_CLAIMS, gson.toJson(map)).apply()
    }

    private fun loadTournamentOwnerClaims(): MutableMap<String, String> {
        if (!::prefs.isInitialized) return mutableMapOf()
        val raw = prefs.getString(KEY_TOURNAMENT_OWNER_CLAIMS, null)?.trim().orEmpty()
        if (raw.isBlank()) return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, String>>() {}.type
            gson.fromJson<MutableMap<String, String>>(raw, type) ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveTournamentOwnerClaims(map: MutableMap<String, String>) {
        if (!::prefs.isInitialized) return
        prefs.edit().putString(KEY_TOURNAMENT_OWNER_CLAIMS, gson.toJson(map)).apply()
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
            setRequestProperty("X-Client-Platform", "android")
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
            setRequestProperty("X-Client-Platform", "android")
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
            setRequestProperty("X-Client-Platform", "android")
        }
        return execute(connection)
    }

    private fun execute(connection: HttpURLConnection): String {
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()

            if (status !in 200..299) {
                val parsedError = try {
                    gson.fromJson(body, JsonObject::class.java)
                } catch (_: Exception) {
                    null
                }
                val message = parsedError?.get("error")?.asString
                val code = parsedError?.get("code")?.asString?.trim().orEmpty()
                if (status == 409 && code == "STALE_REVISION") {
                    throw StaleRevisionException(message ?: "Stale match update rejected")
                }
                if (status == 409 && code == "STALE_TOURNAMENT") {
                    throw StaleTournamentException(message ?: "Stale series update rejected")
                }
                throw CloudApiException(status, message ?: "Cloud API error (HTTP $status)")
            }
            body
        } finally {
            connection.disconnect()
        }
    }
}
