package `in`.nrkmart.cricscore

import android.content.Context
import android.content.SharedPreferences
import `in`.nrkmart.cricscore.db.CricketDatabase
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

object GlobalPlayerRepository {
    private val _players = MutableStateFlow<List<Player>>(emptyList())
    val players: StateFlow<List<Player>> = _players.asStateFlow()

    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    private val gson = Gson()
    private const val PREFS_NAME = "global_players_prefs"
    private const val PLAYERS_KEY = "global_players_data"
    private const val TAG = "GlobalPlayerRepo"

    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
        switchProfile(null)
    }

    @Synchronized
    fun switchProfile(userId: String?) {
        val context = appContext ?: return
        val profileSuffix = userId?.takeIf { it.isNotBlank() }?.let {
            CricketDatabase.databaseNameForProfile(it).substringAfterLast('_')
        }
        val scopedPrefsName = profileSuffix?.let { "${PREFS_NAME}_$it" } ?: PREFS_NAME
        prefs = context.getSharedPreferences(scopedPrefsName, Context.MODE_PRIVATE)
        _players.value = emptyList()
        loadFromDisk()
    }

    private fun loadFromDisk() {
        val p = prefs ?: return
        val json = p.getString(PLAYERS_KEY, null) ?: run {
            _players.value = emptyList()
            return
        }
        try {
            val type = object : TypeToken<List<Player>>() {}.type
            val data: List<Player>? = gson.fromJson(json, type)
            if (data != null) {
                _players.value = data.sortedBy { it.name.lowercase() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse global playlist from disk", e)
        }
    }

    private fun saveToDisk(data: List<Player>) {
        val p = prefs ?: return
        try {
            val json = gson.toJson(data)
            p.edit().putString(PLAYERS_KEY, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save global playlist to disk", e)
        }
    }

    @Synchronized
    fun addPlayer(
        name: String,
        style: BattingStyle,
        bowlingStyle: BowlingStyle = BowlingStyle.NONE,
        role: PlayerRole = PlayerRole.BATTER
    ): Player {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return Player(id = UUID.randomUUID().toString(), name = "Player", battingStyle = style, bowlingStyle = bowlingStyle, role = role)

        val existing = _players.value.find { it.name.equals(trimmed, ignoreCase = true) }
        if (existing != null) {
            val updated = existing.copy(
                battingStyle = style,
                bowlingStyle = bowlingStyle,
                role = role
            )
            if (updated != existing) {
                updatePlayer(existing.id, updated.name, updated.battingStyle ?: style, updated.bowlingStyle ?: bowlingStyle, updated.role)
            }
            return updated
        }

        val newPlayer = Player(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            battingStyle = style,
            bowlingStyle = bowlingStyle,
            role = role
        )
        _players.update { (it + newPlayer).sortedBy { p -> p.name.lowercase() } }
        saveToDisk(_players.value)
        return newPlayer
    }

    @Synchronized
    fun importPlayers(importedList: List<Player>) {
        if (importedList.isEmpty()) return
        _players.update { currentList ->
            val currentMap = currentList.associateBy { it.name.trim().lowercase() }.toMutableMap()
            importedList.forEach { p ->
                val nameTrimmed = p.name.trim()
                if (nameTrimmed.isNotBlank()) {
                    val key = nameTrimmed.lowercase()
                    val existing = currentMap[key]
                    if (existing != null) {
                        currentMap[key] = existing.copy(
                            battingStyle = p.battingStyle ?: existing.battingStyle,
                            bowlingStyle = p.bowlingStyle ?: existing.bowlingStyle,
                            role = p.role,
                            isCaptain = false,
                            isViceCaptain = false,
                            isJoker = p.isJoker || existing.isJoker
                        )
                    } else {
                        currentMap[key] = p.copy(isCaptain = false, isViceCaptain = false)
                    }
                }
            }
            currentMap.values.sortedBy { it.name.lowercase() }
        }
        saveToDisk(_players.value)
    }

    @Synchronized
    fun removePlayer(id: String) {
        _players.update { list -> list.filter { it.id != id } }
        saveToDisk(_players.value)
    }

    @Synchronized
    fun updatePlayer(id: String, newName: String, style: BattingStyle, bowlingStyle: BowlingStyle, role: PlayerRole) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return

        _players.update { list ->
            list.map { player ->
                if (player.id == id) {
                    player.copy(
                        name = trimmed,
                        battingStyle = style,
                        bowlingStyle = bowlingStyle,
                        role = role
                    )
                } else player
            }.sortedBy { it.name.lowercase() }
        }
        saveToDisk(_players.value)
    }
}
