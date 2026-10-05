# Release build rules (R8). Settings are stored as Gson JSON and enum names, so the
# classes and enum constants that are (de)serialized must keep their names and fields.

# Gson: generic type info for TypeToken<List<...>> / TypeToken<Map<...>>
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken

# Serialized models: KeyboardConfig / RowConfig / KeyConfig, EdgeSlot, KeyShape, ...
-keep class com.example.antarakeyboard.model.** { *; }
-keep class com.example.antarakeyboard.data.SavedLayoutStorage$SavedLayout { *; }
-keep class com.example.antarakeyboard.data.EdgePos { *; }
-keep class com.example.antarakeyboard.data.EdgePos$Side { *; }

# Enum constants are stored by name (valueOf) in SharedPreferences
-keepclassmembers enum com.example.antarakeyboard.** { *; }

# Keep line numbers for readable crash traces, hide original file names
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
