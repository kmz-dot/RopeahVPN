# Keep VpnService entry points
-keep class com.ropeah.vpn.vpn.RopeahVpnService { *; }

# Keep JNI class — methods are called from libhev-socks5-tunnel.so by name; R8 must not rename them
-keep class com.ropeah.vpn.vpn.Tun2SocksNative { *; }

# Keep gomobile-generated bindings
-keep class go.** { *; }
-keep class libv2ray.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.ropeah.vpn.**$$serializer { *; }
-keepclassmembers class com.ropeah.vpn.** {
    *** Companion;
}
-keepclasseswithmembers class com.ropeah.vpn.** {
    kotlinx.serialization.KSerializer serializer(...);
}
