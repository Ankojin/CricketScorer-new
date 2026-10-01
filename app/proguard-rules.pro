# Preserve source line numbers & reflection attributes for crash stack trace deobfuscation
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,Enums
-renamesourcefileattribute SourceFile

# Keep Gson Serialized Data Models & Room Entities
-keepclassmembers class * implements java.io.Serializable { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep ALL Cric League Domain Models, Enums, Stats & Database Classes
-keep class in.nrkmart.cricscore.** { *; }
-keepclassmembers class in.nrkmart.cricscore.** { *; }
-keep class in.nrkmart.cricscore.db.** { *; }
-keepclassmembers class in.nrkmart.cricscore.db.** { *; }

# Room Database Keep Rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
