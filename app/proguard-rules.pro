# Keep line numbers so release crash traces map back to source.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization. Type-safe navigation routes are @Serializable objects
# whose serializer is looked up by reflection at runtime; R8 full mode strips
# that without these rules.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Room. The generated <Database>_Impl class is instantiated by name.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# MediaPipe Tasks: JNI calls back into these classes by name and parses protobuf options.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn javax.annotation.**
-dontwarn com.google.auto.value.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**

# Play In-App Review references an annotation that is not on the classpath.
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite

# MediaPipe logs through Flogger, which finds its caller by class name on the stack; renaming it
# breaks Graph's static initialiser.
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.common.flogger.**
