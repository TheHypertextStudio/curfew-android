# R8 runs with isMinifyEnabled and isShrinkResources on the release build.
#
# Curfew resolves serializers reflectively through Json.encodeToString and
# serializer() across the sync, account, security, and callback layers, and the
# protocol DTOs it serializes are @Serializable classes supplied by the
# curfew-protocols artifact rather than declared here. R8 has no way to see
# those lookups, so without the rules below it renames the synthetic Companion
# and $serializer members and every wire message fails to encode at runtime.
#
# That failure mode is invisible to CI: the workflow assembles only
# assemble<Flavor>Debug, so the minified build is never exercised. Treat any
# change to serialization or to the protocol dependency as a reason to run
# assemblePlainRelease locally before shipping.

# --- kotlinx.serialization -------------------------------------------------
# Serializer resolution reads these annotations at runtime.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,Signature

# Keep the Companion of every @Serializable class so Companion.serializer()
# resolves.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep the synthetic $serializer object and its serializer() factory.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Object declarations that are @Serializable expose INSTANCE instead.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# The generated protocol bindings are the DTOs actually crossing the wire.
-keep,includedescriptorclasses class studio.hypertext.curfew.protocols.** { *; }
-keepclassmembers class studio.hypertext.curfew.protocols.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Enum entries are matched by name during deserialization.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# kotlinx.serialization's own internals reference these reflectively.
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

# --- Room ------------------------------------------------------------------
# Entities are instantiated by generated code that R8 keeps, but the column
# field names are read from the compiled schema.
-keep class studio.hypertext.curfew.persistence.** { *; }

# --- Android components ----------------------------------------------------
# Instantiated by the framework from the manifest, never referenced in code.
-keep class studio.hypertext.curfew.platform.PerpetualAlarmService { *; }
-keep class studio.hypertext.curfew.platform.AlarmTransitionReceiver { *; }
-keep class studio.hypertext.curfew.platform.AlarmRecoveryReceiver { *; }
-keep class studio.hypertext.curfew.MainActivity { *; }

# WorkManager instantiates workers reflectively by class name.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
