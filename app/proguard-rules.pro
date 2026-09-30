# Preserve source line numbers for crash stack trace deobfuscation
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Gson Serialized Data Models & Room Entities
-keepclassmembers class * implements java.io.Serializable { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Cric League Domain Models & Database Classes
-keep class in.nrkmart.cricscore.Match { *; }
-keep class in.nrkmart.cricscore.Tournament { *; }
-keep class in.nrkmart.cricscore.Team { *; }
-keep class in.nrkmart.cricscore.Player { *; }
-keep class in.nrkmart.cricscore.Ball { *; }
-keep class in.nrkmart.cricscore.NearbyPayload { *; }
-keep class in.nrkmart.cricscore.db.** { *; }

# Room Database Keep Rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
