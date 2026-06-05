# ──────────────────────────────────────────────
# MCP SDK (protocol types are serialized via kotlinx.serialization)
# ──────────────────────────────────────────────
-keep class io.modelcontextprotocol.** { *; }

# ──────────────────────────────────────────────
# Ktor (CIO engine, server pipeline, content negotiation)
# ──────────────────────────────────────────────
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ──────────────────────────────────────────────
# kotlinx.serialization
# Generated serializers are accessed reflectively.
# ──────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.alessandroannini.mcpdroid.**$$serializer { *; }
-keepclassmembers class com.alessandroannini.mcpdroid.** {
    *** Companion;
}
-keepclasseswithmembers class com.alessandroannini.mcpdroid.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ──────────────────────────────────────────────
# kotlinx.coroutines
# ──────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ──────────────────────────────────────────────
# Kotlin metadata (needed for reflection used by Ktor routing)
# ──────────────────────────────────────────────
-keepattributes RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# ──────────────────────────────────────────────
# SLF4J / Logback (Ktor's logging facade)
# ──────────────────────────────────────────────
-dontwarn org.slf4j.**
-dontwarn ch.qos.logback.**

# ──────────────────────────────────────────────
# CameraX (uses reflection for initialization)
# ──────────────────────────────────────────────
-keep class androidx.camera.** { *; }

# ──────────────────────────────────────────────
# Netty (transitive dependency from Ktor/MCP SDK)
# ──────────────────────────────────────────────
-dontwarn io.netty.**
-dontwarn reactor.netty.**

# ──────────────────────────────────────────────
# OkHttp/OkIO (may be pulled in transitively)
# ──────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**

# ──────────────────────────────────────────────
# Java/Kotlin stdlib bits that R8 warns about
# ──────────────────────────────────────────────
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
