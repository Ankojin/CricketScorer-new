package com.example.cricketscorer

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object GullyRulesRepository {
    private val _gullyRules = MutableStateFlow(GullyRules())
    val gullyRules: StateFlow<GullyRules> = _gullyRules.asStateFlow()

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private const val PREFS_NAME = "gully_rules_prefs"
    private const val RULES_KEY = "gully_rules_data"

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromDisk()
    }

    private fun loadFromDisk() {
        val json = prefs.getString(RULES_KEY, null)
        if (json != null) {
            try {
                val rules = gson.fromJson(json, GullyRules::class.java)
                if (rules != null) {
                    _gullyRules.value = rules
                }
            } catch (e: Exception) {
                _gullyRules.value = GullyRules()
            }
        }
    }

    private fun saveToDisk(rules: GullyRules) {
        val json = gson.toJson(rules)
        prefs.edit().putString(RULES_KEY, json).apply()
    }

    fun updateRules(newRules: GullyRules) {
        _gullyRules.value = newRules
        saveToDisk(newRules)
    }

    fun updateRule(transform: (GullyRules) -> GullyRules) {
        _gullyRules.update { current ->
            val updated = transform(current)
            saveToDisk(updated)
            updated
        }
    }
}
