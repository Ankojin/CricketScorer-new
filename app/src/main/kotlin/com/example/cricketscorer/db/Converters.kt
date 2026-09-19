package com.example.cricketscorer.db

import androidx.room.TypeConverter
import com.example.cricketscorer.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromBattingStyle(value: BattingStyle?): String? = value?.name

    @TypeConverter
    fun toBattingStyle(value: String?): BattingStyle? = value?.let { BattingStyle.valueOf(it) }

    @TypeConverter
    fun fromExtrasType(value: ExtrasType): String = value.name

    @TypeConverter
    fun toExtrasType(value: String): ExtrasType = ExtrasType.valueOf(value)

    @TypeConverter
    fun fromWicketType(value: WicketType): String = value.name

    @TypeConverter
    fun toWicketType(value: String): WicketType = WicketType.valueOf(value)

    @TypeConverter
    fun fromMatchStatus(value: MatchStatus): String = value.name

    @TypeConverter
    fun toMatchStatus(value: String): MatchStatus = MatchStatus.valueOf(value)

    @TypeConverter
    fun fromPendingAction(value: PendingAction?): String? = value?.name

    @TypeConverter
    fun toPendingAction(value: String?): PendingAction? = value?.let { PendingAction.valueOf(it) }

    @TypeConverter
    fun fromStringList(value: List<String>?): String? = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromInningsSummary(value: InningsSummary?): String? = gson.toJson(value)

    @TypeConverter
    fun toInningsSummary(value: String?): InningsSummary? {
        val type = object : TypeToken<InningsSummary>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromWicketRecordList(value: List<WicketRecord>?): String? = gson.toJson(value)

    @TypeConverter
    fun toWicketRecordList(value: String?): List<WicketRecord>? {
        val type = object : TypeToken<List<WicketRecord>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromTeam(value: Team?): String? = gson.toJson(value)

    @TypeConverter
    fun toTeam(value: String?): Team? {
        val type = object : TypeToken<Team>() {}.type
        return gson.fromJson(value, type)
    }
}
