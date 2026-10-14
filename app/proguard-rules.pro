# kotlinx.serialization: keep generated serializers of @Serializable classes
# (DTOs in :core:network and the navigation routes).
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }

# Retrofit: keep the service interfaces' generic signatures for suspend funs.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface com.azza.core.network.api.** { *; }
