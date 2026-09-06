package com.example.cricketscorer

import android.content.Context
import android.content.SharedPreferences
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

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private const val PREFS_NAME = "global_players_prefs"
    private const val PLAYERS_KEY = "global_players_data"

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromDisk()
    }

    private fun loadFromDisk() {
        val json = prefs.getString(PLAYERS_KEY, null)
        if (json != null) {
            val type = object : TypeToken<List<Player>>() {}.type
            val data: List<Player> = gson.fromJson(json, type)
            _players.value = data
        }
    }

    private fun saveToDisk(data: List<Player>) {
        val json = gson.toJson(data)
        prefs.edit().putString(PLAYERS_KEY, json).apply()
    }

    fun addPlayer(name: String, style: BattingStyle): Player {
        val trimmed = name.trim()
        val existing = _players.value.find { it.name.equals(trimmed, ignoreCase = true) }
        if (existing != null) return existing

        val newPlayer = Player(id = UUID.randomUUID().toString(), name = trimmed, battingStyle = style)
        _players.update { (it + newPlayer).sortedBy { p -> p.name } }
        saveToDisk(_players.value)
        return newPlayer
    }

    fun removePlayer(id: String) {
        _players.update { list -> list.filter { it.id != id } }
        saveToDisk(_players.value)
    }
}
